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
import com.ea.utils.GameVersUtils;
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
    @Scheduled(fixedDelay = 10000)
    public void updateBotActivity() {
        if (!botActivityEnabled) {
            log.debug("Bot activity updates are disabled");
            return;
        }
        ParamEntity lastKnownIpEntity = paramRepository.findById(Params.LAST_KNOWN_IP.name()).orElse(null);
        String currentIp = lastKnownIpEntity != null ? lastKnownIpEntity.getParamValue() : "UNKNOWN";
        int currentPlayersOnline = personaConnectionRepository.countPlayersOnline();
        int currentPlayersInGame = gameReportRepository.countPlayersInGame();
        String activity = "🌐 " + currentPlayersOnline + " 🎮 " + currentPlayersInGame + " 💻 " + currentIp;
        discordBotService.updateActivity(activity);
    }

    @Scheduled(fixedDelay = 10000)
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

        List<PersonaConnectionEntity> personaLogins = personaConnectionRepository.findPersonaLogins(lastFetchTime, currentFetchTime);
        List<PersonaConnectionEntity> personaLogouts = personaConnectionRepository.findPersonaLogouts(lastFetchTime, currentFetchTime);

        List<GameReportEntity> rawGameJoining = gameReportRepository.findPlayerJoins(lastFetchTime, currentFetchTime);
        List<GameReportEntity> gameLeaving = gameReportRepository.findPlayerLeaves(lastFetchTime, currentFetchTime);

        // Filter out map rotation joins: if there is a previous GameReportEntity for the same personaConnection
        // where previousReport.endTime == previousReport.game.endTime and previousReport.endTime == this.startTime, skip
        List<GameReportEntity> gameJoining = new ArrayList<>();
        for (GameReportEntity join : rawGameJoining) {
            PersonaConnectionEntity personaConn = join.getPersonaConnection();
            LocalDateTime joinStart = join.getStartTime();
            // Find previous report for this personaConnection that ends exactly at this join's start time
            GameReportEntity prev = gameReportRepository
                .findFirstByPersonaConnectionAndEndTimeOrderByEndTimeDesc(personaConn, joinStart);
            if (prev != null && prev.getEndTime() != null && prev.getGame() != null && prev.getEndTime().equals(prev.getGame().getEndTime())) {
                // This is a map rotation, skip
                continue;
            }
            gameJoining.add(join);
        }

        List<Event> events = new ArrayList<>();

        for (PersonaConnectionEntity login : personaLogins) {
            String persona = login.getPersona().getPers().replace("\"", "");
            String version = GameVersUtils.getGameNameByVersion(login.getVers());
            events.add(new Event(
                    login.getId(),
                    login.getStartTime(),
                    "🟢 **" + persona + "** connected to `" + version + "`"
            ));
        }
        for (PersonaConnectionEntity logout : personaLogouts) {
            String persona = logout.getPersona().getPers().replace("\"", "");
            String version = GameVersUtils.getGameNameByVersion(logout.getVers());
            events.add(new Event(
                    logout.getId(),
                    logout.getEndTime(),
                    "🔴 **" + persona + "** disconnected from `" + version + "`"
            ));
        }
        for (GameReportEntity join : gameJoining) {
            String persona = join.getPersonaConnection().getPersona().getPers().replace("\"", "");
            String gameName = join.getGame().getName().replace("\"", "");
            events.add(new Event(
                    join.getId(),
                    join.getStartTime(),
                    "➡️ **" + persona + "** joined game `" + gameName + "`"
            ));
        }
        for (GameReportEntity leave : gameLeaving) {
            String persona = leave.getPersonaConnection().getPersona().getPers().replace("\"", "");
            String gameName = leave.getGame().getName().replace("\"", "");
            events.add(new Event(
                    leave.getId(),
                    leave.getEndTime(),
                    "⬅️ **" + persona + "** left game `" + gameName + "`"
            ));
        }

        Collections.sort(events); // use comparator of Event class

        String message = String.join("\n", events.stream().map(Event::getMessage).toList());

        List<ChannelSubscriptionEntity> logSubs = channelSubscriptionService.getAllByType(SubscriptionType.LOGS);
        List<String> channelIds = logSubs.stream().map(ChannelSubscriptionEntity::getChannelId).collect(Collectors.toList());
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

                List<ChannelSubscriptionEntity> alertSubs = channelSubscriptionService.getAllByType(SubscriptionType.ALERTS);
                List<String> channelIds = alertSubs.stream().map(ChannelSubscriptionEntity::getChannelId).collect(Collectors.toList());
                discordBotService.sendMessage(channelIds, String.format("⚠️ New DNS address: `%s`", currentIp));
            }
        }
    }

}
