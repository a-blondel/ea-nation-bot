package com.ea.services;

import com.ea.model.Event;
import com.ea.entities.GameEntity;
import com.ea.entities.GameReportEntity;
import com.ea.entities.PersonaConnectionEntity;
import com.ea.entities.discord.ParamEntity;
import com.ea.enums.Params;
import com.ea.enums.SubscriptionType;
import com.ea.repositories.GameReportRepository;
import com.ea.repositories.GameRepository;
import com.ea.repositories.PersonaConnectionRepository;
import com.ea.repositories.discord.ParamRepository;
import com.ea.entities.discord.ChannelSubscriptionEntity;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class PollingService {
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSSSSS";
    private static final List<String> vers = List.of("PSP/MOHGPS071", "PSP/MOH07");

    @Value("${dns.name}")
    private String dnsName;

    private boolean enablePlayerEventsProcess = false;

    @Value("${services.events-enabled}")
    private boolean eventsEnabled;

    @Value("${services.bot-activity-enabled}")
    private boolean botActivityEnabled;

    private final ParamRepository paramRepository;
    private final GameRepository gameRepository;
    private final GameReportRepository gameReportRepository;
    private final PersonaConnectionRepository personaConnectionRepository;
    private final ScoreboardService scoreboardService;
    private final DiscordBotService discordBotService;
    private final ChannelSubscriptionService channelSubscriptionService;

    @PostConstruct
    @Scheduled(fixedDelay = 20000)
    public void updateBotActivity() {
        if (!botActivityEnabled) {
            log.debug("Bot activity updates are disabled");
            return;
        }
        ParamEntity lastKnownIpEntity = paramRepository.findById(Params.LAST_KNOWN_IP.name()).orElse(null);
        String currentIp = lastKnownIpEntity != null ? lastKnownIpEntity.getParamValue() : "UNKNOWN";
        int currentPlayersOnline = personaConnectionRepository.countByIsHostIsFalseAndEndTimeIsNull();
        int currentPlayersInGame = gameReportRepository.countByIsHostIsFalseAndEndTimeIsNull();
        String activity = "🌐 " + currentPlayersOnline + " 🎮 " + currentPlayersInGame + " 💻 " + currentIp;
        discordBotService.updateActivity(activity);
    }

    @Scheduled(fixedDelay = 20000)
    public void processDataSinceLastFetchTime() {
        if (!eventsEnabled) {
            log.debug("Events service is disabled");
            return;
        }
        ParamEntity lastFetchTimeEntity = paramRepository.findById(Params.LAST_FETCH_TIME.name()).orElse(null);
        if(lastFetchTimeEntity != null) {
            LocalDateTime lastFetchTime = LocalDateTime.parse(lastFetchTimeEntity.getParamValue(), DateTimeFormatter.ofPattern(DATETIME_FORMAT));
            LocalDateTime currentFetchTime = LocalDateTime.now();

            processScoreboard(lastFetchTime, currentFetchTime);
            if(enablePlayerEventsProcess) {
                processPlayerEvents(lastFetchTime, currentFetchTime);
            } else {
                enablePlayerEventsProcess = true;
            }

            lastFetchTimeEntity.setParamValue(currentFetchTime.format(DateTimeFormatter.ofPattern(DATETIME_FORMAT)));
            paramRepository.save(lastFetchTimeEntity);
        }
    }

    private void processScoreboard(LocalDateTime lastFetchTime, LocalDateTime currentFetchTime) {
        List<GameEntity> games = gameRepository.findByVersInAndEndTimeBetweenOrderByEndTimeAsc(vers, lastFetchTime, currentFetchTime);

        for (GameEntity game : games) {
            scoreboardService.generateScoreboard(game);
        }
    }

    private void processPlayerEvents(LocalDateTime lastFetchTime, LocalDateTime currentFetchTime) {
        List<PersonaConnectionEntity> personaLogins = personaConnectionRepository.findByIsHostIsFalseAndStartTimeBetweenOrderByStartTime(lastFetchTime, currentFetchTime);
        List<PersonaConnectionEntity> personaLogouts = personaConnectionRepository.findByIsHostIsFalseAndEndTimeBetweenOrderByEndTime(lastFetchTime, currentFetchTime);

        List<GameReportEntity> gameJoining = gameReportRepository.findByIsHostIsFalseAndStartTimeBetweenOrderByStartTime(lastFetchTime, currentFetchTime);
        List<GameReportEntity> gameLeaving = gameReportRepository.findByIsHostIsFalseAndEndTimeBetweenOrderByEndTime(lastFetchTime, currentFetchTime);

        List<Event> events = new ArrayList<>();

        for (PersonaConnectionEntity login : personaLogins) {
            events.add(new Event(login.getId(), login.getStartTime(),
                    login.getPersona().getPers().replaceAll("\"", "") + " connected"));
        }
        for (PersonaConnectionEntity logout : personaLogouts) {
            events.add(new Event(logout.getId(), logout.getEndTime(),
                    logout.getPersona().getPers().replaceAll("\"", "") + " disconnected"));
        }
        for (GameReportEntity join : gameJoining) {
            events.add(new Event(join.getId(), join.getStartTime(),
                    join.getPersonaConnection().getPersona().getPers().replaceAll("\"", "") +
                            " joined game " + join.getGame().getName().replaceAll("\"", "")));
        }
        for (GameReportEntity leave : gameLeaving) {
            events.add(new Event(leave.getId(), leave.getEndTime(),
                    leave.getPersonaConnection().getPersona().getPers().replaceAll("\"", "") +
                            " left game " + leave.getGame().getName().replaceAll("\"", "")));
        }

        Collections.sort(events); // use comparator of Event class

        String message = String.join("\n", events.stream().map(Event::getMessage).toList());

        List<ChannelSubscriptionEntity> eventSubs = channelSubscriptionService.getAllByType(SubscriptionType.EVENTS);
        List<String> channelIds = eventSubs.stream().map(ChannelSubscriptionEntity::getChannelId).collect(Collectors.toList());
        discordBotService.sendMessage(channelIds, message);
    }

    @Scheduled(cron = "0 0 0,12 * * ?")
    public void processIpChange() throws UnknownHostException {
        if (!botActivityEnabled) {
            log.debug("Bot activity is disabled, skipping IP change verification");
            return;
        }
        ParamEntity lastKnownIpEntity = paramRepository.findById(Params.LAST_KNOWN_IP.name()).orElse(null);
        if (lastKnownIpEntity != null) {
            String lastKnownIp = lastKnownIpEntity.getParamValue();
            String currentIp = InetAddress.getByName(dnsName).getHostAddress();
            if(!lastKnownIp.equals(currentIp)) {
                log.info("NEW DNS ADDRESS: {}", currentIp);
                lastKnownIpEntity.setParamValue(currentIp);
                paramRepository.save(lastKnownIpEntity);

                List<ChannelSubscriptionEntity> ipSubs = channelSubscriptionService.getAllByType(SubscriptionType.IP_UPDATE);
                List<String> channelIds = ipSubs.stream().map(ChannelSubscriptionEntity::getChannelId).collect(Collectors.toList());
                discordBotService.sendMessage(channelIds, "New DNS address: " + currentIp);
            }
        }
    }

}
