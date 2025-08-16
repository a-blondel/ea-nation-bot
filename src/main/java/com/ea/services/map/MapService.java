package com.ea.services.map;

import com.ea.entities.core.PersonaConnectionEntity;
import com.ea.entities.discord.ChannelSubscriptionEntity;
import com.ea.enums.GameGenre;
import com.ea.enums.SubscriptionType;
import com.ea.model.GeoLocation;
import com.ea.model.LocationInfo;
import com.ea.repositories.core.PersonaConnectionRepository;
import com.ea.services.discord.ChannelSubscriptionService;
import com.ea.services.discord.DiscordBotService;
import com.ea.utils.GameVersUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapService {
    private final GeoIPService geoIPService;
    private final PersonaConnectionRepository personaConnectionRepository;
    private final WorldMapGenerator worldMapGenerator;
    private final ChannelSubscriptionService channelSubscriptionService;
    private final DiscordBotService discordBotService;

    @Value("${reports.path}")
    private String reportsPath;

    @Value("${services.map-enabled}")
    private boolean serviceEnabled;

    @Value("${map.types}")
    private String mapTypes;

    @Scheduled(cron = "0 0 11 * * 1")
    public void generateMaps() {
        if (!serviceEnabled) {
            log.debug("Map service is disabled");
            return;
        }

        try {
            LocalDateTime startTime = LocalDateTime.now().minusDays(7);

            // Generate maps for each game genre
            for (GameGenre genre : GameGenre.values()) {
                generateMapsForGenre(genre, startTime);
            }

        } catch (Exception e) {
            log.error("Failed to generate weekly maps", e);
        }
    }

    private void generateMapsForGenre(GameGenre genre, LocalDateTime startTime) {
        try {
            // Get VERS codes for this genre
            List<String> versForGenre = GameVersUtils.getVersForGenre(genre);
            if (versForGenre.isEmpty()) {
                log.debug("No VERS codes found for genre: {}", genre);
                return;
            }

            // Get connections filtered by game genre
            List<PersonaConnectionEntity> connections = personaConnectionRepository
                    .findByStartTimeGreaterThanAndVersIn(startTime, versForGenre);

            if (connections.isEmpty()) {
                log.debug("No connections found for genre: {}", genre);
                return;
            }

            // Group by persona and get the latest connection for each (hosts are excluded)
            Map<Long, PersonaConnectionEntity> latestConnections = connections.stream()
                    .filter(conn -> conn.getPersona() != null
                            && conn.getPersona().getDeletedOn() == null
                            && conn.isHost() == false)
                    .collect(Collectors.groupingBy(
                            conn -> conn.getPersona().getId(),
                            Collectors.collectingAndThen(
                                    Collectors.maxBy(Comparator.comparing(PersonaConnectionEntity::getStartTime)),
                                    Optional::get
                            )
                    ));

            // Handle duplicate IPs by keeping the last connected persona
            Map<String, PersonaConnectionEntity> uniqueIpConnections = latestConnections.values().stream()
                    .collect(Collectors.groupingBy(
                            conn -> cleanIpAddress(conn.getAddress()),
                            Collectors.collectingAndThen(
                                    Collectors.maxBy(Comparator.comparing(conn ->
                                            conn.getStartTime())),
                                    Optional::get
                            )
                    ));

            log.info("Found {} unique IPs for genre {}", uniqueIpConnections.size(), genre);

            List<File> generatedFiles = new ArrayList<>();

            if (mapTypes.equals("ALL") || mapTypes.equals("HEATMAP")) {
                File heatMapFile = generateHeatmapForGenre(uniqueIpConnections, genre);
                if (heatMapFile != null) {
                    generatedFiles.add(heatMapFile);
                }
            }

            if (mapTypes.equals("ALL") || mapTypes.equals("LOCATION")) {
                File locationMapFile = generateLocationMapForGenre(uniqueIpConnections, genre);
                if (locationMapFile != null) {
                    generatedFiles.add(locationMapFile);
                }
            }

            // Send generated maps to subscribers of this genre
            if (!generatedFiles.isEmpty()) {
                List<ChannelSubscriptionEntity> activityMapSubs = channelSubscriptionService
                        .getAllByTypeAndGenre(SubscriptionType.ACTIVITY_MAP, genre);
                List<String> channelIds = activityMapSubs.stream()
                        .map(ChannelSubscriptionEntity::getChannelId)
                        .toList();

                if (!channelIds.isEmpty()) {
                    String message = String.format("📊 Weekly activity map for %s games", genre.getValue());
                    discordBotService.sendImages(channelIds, generatedFiles, message);
                }
            }

        } catch (Exception e) {
            log.error("Failed to generate maps for genre: {}", genre, e);
        }
    }

    private File generateHeatmapForGenre(Map<String, PersonaConnectionEntity> uniqueIpConnections, GameGenre genre) throws IOException {
        Map<String, Integer> countryHits = uniqueIpConnections.values().stream()
                .map(conn -> geoIPService.getCountry(cleanIpAddress(conn.getAddress())))
                .filter(country -> !"UNKNOWN".equals(country))
                .collect(Collectors.groupingBy(
                        country -> country,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));

        log.info("Found hits for {} countries in genre {}: {}", countryHits.size(), genre,
                countryHits.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .map(e -> String.format("%s=%d", e.getKey(), e.getValue()))
                        .collect(Collectors.joining(", "))
        );

        File heatMapFile = new File(reportsPath, "heat-map-" + genre.getValue() + "-" +
                LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".png");
        worldMapGenerator.generateHeatmap(countryHits, heatMapFile, genre);
        return heatMapFile;
    }

    private File generateLocationMapForGenre(Map<String, PersonaConnectionEntity> uniqueIpConnections, GameGenre genre) throws IOException {
        Map<String, LocationInfo> locationInfoMap = uniqueIpConnections.values().stream()
                .collect(Collectors.toMap(
                        conn -> cleanIpAddress(conn.getAddress()),
                        conn -> {
                            GeoLocation location = geoIPService.getLocation(cleanIpAddress(conn.getAddress()));
                            return location != null ? new LocationInfo(location, conn.getPersona().getPers()) : null;
                        },
                        (a, b) -> a,
                        HashMap::new
                ));

        // Remove null locations
        locationInfoMap.values().removeIf(Objects::isNull);

        File locationMapFile = new File(reportsPath, "location-map-" + genre.getValue() + "-" +
                LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".png");
        worldMapGenerator.generateLocationMap(locationInfoMap, locationMapFile, genre);
        return locationMapFile;
    }

    private String cleanIpAddress(String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }
        return address
                .replaceAll("^/", "")  // Remove leading slash
                .split(":")[0];        // Remove port number
    }
}