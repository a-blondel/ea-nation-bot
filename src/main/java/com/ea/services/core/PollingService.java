package com.ea.services.core;

import com.ea.entities.core.GameConnectionEntity;
import com.ea.entities.core.GameEntity;
import com.ea.entities.core.PersonaConnectionEntity;
import com.ea.entities.discord.ChannelSubscriptionEntity;
import com.ea.entities.discord.ParamEntity;
import com.ea.enums.Game;
import com.ea.enums.GameGenre;
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
import com.ea.services.stats.NfsScoreboardService;
import com.ea.services.stats.NhlScoreboardService;
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
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Service
public class PollingService {
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSSSSS";
    public static final String PSP_MOH_07 = "PSP/MOH07";
    public static final String PSP_MOH_08 = "PSP/MOH08";
    public static final String WII_MOH_08 = "WII/MOH08";
    public static final List<String> MOH07_OR_MOH08 = List.of(PSP_MOH_07, PSP_MOH_08, WII_MOH_08);
    private final ParamRepository paramRepository;
    private final GameRepository gameRepository;
    private final GameConnectionRepository gameConnectionRepository;
    private final PersonaConnectionRepository personaConnectionRepository;
    private final MohhScoreboardService mohhScoreboardService;
    private final NfsScoreboardService nfsScoreboardService;
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
        // Process scoreboards for all game categories
        for (GameGenre gameGenre : GameGenre.values()) {
            List<String> versForGenre = GameVersUtils.getAllVersForGenre(gameGenre);
            if (!versForGenre.isEmpty()) {
                List<GameEntity> games = gameRepository.findByVersInAndEndTimeBetweenOrderByEndTimeAsc(versForGenre, lastFetchTime, currentFetchTime);
                for (GameEntity game : games) {
                    // Determine which scoreboard service to use based on gameGenre
                    switch (gameGenre) {
                        case FPS -> mohhScoreboardService.generateScoreboard(game);
                        case HOCKEY -> nhlScoreboardService.generateScoreboard(game);
                        case RACING -> nfsScoreboardService.generateScoreboard(game);
                        // Other categories can be added here when their scoreboard services are implemented
                        default -> {
                        }
                    }
                }
            }
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

        // Group events by game genre for targeted distribution
        List<Event> events = new ArrayList<>();

        // Process logins - group by genre
        for (PersonaConnectionEntity login : personaLogins) {
            GameGenre genre = GameVersUtils.getGenreForVers(login.getVers());
            String persona = login.getPersona().getPers().replace("\"", "");
            String gameName = GameVersUtils.getNamesForGenre(genre)
                    .stream()
                    .filter(name -> Game.findByVers(login.getVers()) != null && Game.findByVers(login.getVers()).getName().equals(name))
                    .findFirst()
                    .orElse(login.getVers());

            Event event = new Event(
                    login.getId(),
                    login.getStartTime(),
                    "🟢 `" + gameName + "` **" + persona + "** connected",
                    genre
            );
            events.add(event);
        }

        // Process logouts - group by genre
        for (PersonaConnectionEntity logout : personaLogouts) {
            GameGenre genre = GameVersUtils.getGenreForVers(logout.getVers());
            String persona = logout.getPersona().getPers().replace("\"", "");
            String gameName = GameVersUtils.getNamesForGenre(genre)
                    .stream()
                    .filter(name -> Game.findByVers(logout.getVers()) != null && Game.findByVers(logout.getVers()).getName().equals(name))
                    .findFirst()
                    .orElse(logout.getVers());

            Event event = new Event(
                    logout.getId(),
                    logout.getEndTime(),
                    "🔴 `" + gameName + "` **" + persona + "** disconnected",
                    genre
            );
            events.add(event);
        }

        // Process game joins and leaves with genre grouping
        for (GameConnectionEntity join : allGameJoining) {
            GameGenre genre = GameVersUtils.getGenreForVers(join.getPersonaConnection().getVers());
            String persona = join.getPersonaConnection().getPersona().getPers().replace("\"", "");
            String gameName = join.getGame().getName().replace("\"", "");
            String gameDisplayName = Game.findByVers(join.getPersonaConnection().getVers()) != null ?
                    Game.findByVers(join.getPersonaConnection().getVers()).getName() : join.getPersonaConnection().getVers();

            Event event = new Event(
                    join.getId(),
                    join.getStartTime(),
                    "➡️ `" + gameDisplayName + "` **" + persona + "** joined game `" + gameName + "`",
                    genre
            );
            events.add(event);
        }

        for (GameConnectionEntity leave : allGameLeaving) {
            GameGenre genre = GameVersUtils.getGenreForVers(leave.getPersonaConnection().getVers());
            String persona = leave.getPersonaConnection().getPersona().getPers().replace("\"", "");
            String gameName = leave.getGame().getName().replace("\"", "");
            String gameDisplayName = Game.findByVers(leave.getPersonaConnection().getVers()) != null ?
                    Game.findByVers(leave.getPersonaConnection().getVers()).getName() : leave.getPersonaConnection().getVers();

            Event event = new Event(
                    leave.getId(),
                    leave.getEndTime(),
                    "⬅️ `" + gameDisplayName + "` **" + persona + "** left game `" + gameName + "`",
                    genre
            );
            events.add(event);
        }

        Collections.sort(events);

        // Send events grouped by genre to respective subscribers
        for (GameGenre genre : GameGenre.values()) {
            List<Event> genreEvents = events.stream()
                    .filter(event -> event.getGameGenre() == genre)
                    .toList();

            if (!genreEvents.isEmpty()) {
                String message = String.join("\n", genreEvents.stream().map(Event::getMessage).toList());
                List<ChannelSubscriptionEntity> logSubs = channelSubscriptionService.getAllByTypeAndGenre(SubscriptionType.LOGS, genre);
                List<String> channelIds = logSubs.stream().map(ChannelSubscriptionEntity::getChannelId).toList();
                if (!channelIds.isEmpty()) {
                    discordBotService.sendMessage(channelIds, message);
                }
            }
        }
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
            }
        }
    }

}
