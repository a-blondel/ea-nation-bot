package com.ea.services.core;

import com.ea.entities.core.GameConnectionEntity;
import com.ea.entities.core.GameEntity;
import com.ea.entities.core.PersonaConnectionEntity;
import com.ea.entities.discord.ChannelSubscriptionEntity;
import com.ea.entities.discord.ParamEntity;
import com.ea.enums.Params;
import com.ea.enums.SubscriptionType;
import com.ea.model.Event;
import com.ea.repositories.core.GameConnectionRepository;
import com.ea.repositories.core.GameRepository;
import com.ea.repositories.core.PersonaConnectionRepository;
import com.ea.repositories.discord.ParamRepository;
import com.ea.services.discord.ChannelSubscriptionService;
import com.ea.services.discord.DiscordBotService;
import com.ea.services.stats.MohhScoreboardService;
import com.ea.services.stats.NhlScoreboardService;
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
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Service
public class PollingService {
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSSSSS";
    public static final String PSP_MOH_07_UHS = "PSP/MOHGPS071";
    public static final String PSP_MOH_07 = "PSP/MOH07";
    public static final String PSP_MOH_08 = "PSP/MOH08";
    public static final String WII_MOH_08 = "WII/MOH08";
    public static final String PSP_NHL_07 = "PSP/NHL07";
    public static final List<String> MOH07_OR_MOH08 = List.of(PSP_MOH_07, PSP_MOH_08, WII_MOH_08);
    private final ParamRepository paramRepository;
    private final GameRepository gameRepository;
    private final GameConnectionRepository gameConnectionRepository;
    private final PersonaConnectionRepository personaConnectionRepository;
    private final MohhScoreboardService mohhScoreboardService;
    private final NhlScoreboardService nhlScoreboardService;
    private final DiscordBotService discordBotService;
    private final ChannelSubscriptionService channelSubscriptionService;
    @Value("${dns.name}")
    private String dnsName;
    private boolean enablePlayerEventsProcess = false;
    @Value("${services.events-enabled}")
    private boolean eventsEnabled;
    @Value("${services.bot-activity-enabled}")
    private boolean botActivityEnabled;

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
        int currentPlayersInGame = gameConnectionRepository.countPlayersInGame();
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
        if (lastFetchTimeEntity != null) {
            LocalDateTime lastFetchTime = LocalDateTime.parse(lastFetchTimeEntity.getParamValue(), DateTimeFormatter.ofPattern(DATETIME_FORMAT));
            LocalDateTime currentFetchTime = LocalDateTime.now();

            processScoreboard(lastFetchTime, currentFetchTime);
            if (enablePlayerEventsProcess) {
                processPlayerEvents(lastFetchTime, currentFetchTime);
            } else {
                enablePlayerEventsProcess = true;
            }

            lastFetchTimeEntity.setParamValue(currentFetchTime.format(DateTimeFormatter.ofPattern(DATETIME_FORMAT)));
            paramRepository.save(lastFetchTimeEntity);
        }
    }

    private void processScoreboard(LocalDateTime lastFetchTime, LocalDateTime currentFetchTime) {
        // Process Mohh scoreboards
        List<GameEntity> games = gameRepository.findByVersInAndEndTimeBetweenOrderByEndTimeAsc(List.of(PSP_MOH_07_UHS), lastFetchTime, currentFetchTime);
        for (GameEntity game : games) {
            mohhScoreboardService.generateScoreboard(game);
        }

        // Process Nhl scoreboards
        games = gameRepository.findByVersInAndEndTimeBetweenOrderByEndTimeAsc(List.of(PSP_NHL_07), lastFetchTime, currentFetchTime);
        for (GameEntity game : games) {
            nhlScoreboardService.generateScoreboard(game);
        }
    }

    private void processPlayerEvents(LocalDateTime lastFetchTime, LocalDateTime currentFetchTime) {

        List<PersonaConnectionEntity> personaLogins = personaConnectionRepository.findPersonaLogins(lastFetchTime, currentFetchTime);
        List<PersonaConnectionEntity> personaLogouts = personaConnectionRepository.findPersonaLogouts(lastFetchTime, currentFetchTime);

        List<GameConnectionEntity> rawMohhGameJoining = gameConnectionRepository.findMohhPlayerJoins(lastFetchTime, currentFetchTime, MOH07_OR_MOH08);
        List<GameConnectionEntity> mohhGameLeaving = gameConnectionRepository.findMohhPlayerLeaves(lastFetchTime, currentFetchTime, MOH07_OR_MOH08);

        List<GameConnectionEntity> notMohhGameJoining = gameConnectionRepository.findNotMohhPlayerJoins(lastFetchTime, currentFetchTime, MOH07_OR_MOH08);
        List<GameConnectionEntity> notMohhGameLeaving = gameConnectionRepository.findNotMohhPlayerLeaves(lastFetchTime, currentFetchTime, MOH07_OR_MOH08);

        // Filter out map rotation joins: if there is a previous GameConnectionEntity for the same personaConnection
        // where previousReport.endTime == previousReport.game.endTime and previousReport.endTime == this.startTime, skip
        List<GameConnectionEntity> mohhGameJoining = new ArrayList<>();
        for (GameConnectionEntity join : rawMohhGameJoining) {
            PersonaConnectionEntity personaConn = join.getPersonaConnection();
            LocalDateTime joinStart = join.getStartTime();
            // Find previous report for this personaConnection that ends exactly at this join's start time
            GameConnectionEntity prev = gameConnectionRepository
                    .findFirstByPersonaConnectionAndEndTimeOrderByEndTimeDesc(personaConn, joinStart);
            if (prev != null && prev.getEndTime() != null && prev.getGame() != null && prev.getEndTime().equals(prev.getGame().getEndTime())) {
                // This is a map rotation, skip
                continue;
            }
            mohhGameJoining.add(join);
        }

        List<GameConnectionEntity> allGameJoining = Stream.concat(mohhGameJoining.stream(), notMohhGameJoining.stream()).toList();
        List<GameConnectionEntity> allGameLeaving = Stream.concat(mohhGameLeaving.stream(), notMohhGameLeaving.stream()).toList();

        List<Event> events = new ArrayList<>();
        for (PersonaConnectionEntity login : personaLogins) {
            String persona = login.getPersona().getPers().replace("\"", "");
            events.add(new Event(
                    login.getId(),
                    login.getStartTime(),
                    "🟢 `[" + login.getVers() + "]` **" + persona + "** connected"
            ));
        }
        for (PersonaConnectionEntity logout : personaLogouts) {
            String persona = logout.getPersona().getPers().replace("\"", "");
            events.add(new Event(
                    logout.getId(),
                    logout.getEndTime(),
                    "🔴 `[" + logout.getVers() + "]` **" + persona + "** disconnected"
            ));
        }
        for (GameConnectionEntity join : allGameJoining) {
            String persona = join.getPersonaConnection().getPersona().getPers().replace("\"", "");
            String gameName = join.getGame().getName().replace("\"", "");
            String gameVers = join.getPersonaConnection().getVers();
            events.add(new Event(
                    join.getId(),
                    join.getStartTime(),
                    "➡️ `[" + gameVers + "]` **" + persona + "** joined game `" + gameName + "`"
            ));
        }
        for (GameConnectionEntity leave : allGameLeaving) {
            String persona = leave.getPersonaConnection().getPersona().getPers().replace("\"", "");
            String gameName = leave.getGame().getName().replace("\"", "");
            String gameVers = leave.getPersonaConnection().getVers();
            events.add(new Event(
                    leave.getId(),
                    leave.getEndTime(),
                    "⬅️ `[" + gameVers + "]` **" + persona + "** left game `" + gameName + "`"
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
            if (!lastKnownIp.equals(currentIp)) {
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
