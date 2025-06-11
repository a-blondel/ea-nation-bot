package com.ea.services;

import com.ea.entities.game.PersonaConnectionEntity;
import com.ea.model.GeoLocation;
import com.ea.model.LocationInfo;
import com.ea.repositories.game.PersonaConnectionRepository;
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
            // Get IPs from the last 7 days
            LocalDateTime startTime = LocalDateTime.now().minusDays(7);
            
            // Get all connections with their personas
            List<PersonaConnectionEntity> connections = personaConnectionRepository
                .findByStartTimeGreaterThan(startTime);

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

            log.info("Found {} unique IPs from personas", uniqueIpConnections.size());

            if (mapTypes.equals("ALL") || mapTypes.equals("HEATMAP")) {
                generateHeatmap(uniqueIpConnections);
            }

            if (mapTypes.equals("ALL") || mapTypes.equals("LOCATION")) {
                generateLocationMap(uniqueIpConnections);
            }
            
        } catch (Exception e) {
            log.error("Failed to generate weekly maps", e);
        }
    }

    private void generateHeatmap(Map<String, PersonaConnectionEntity> uniqueIpConnections) throws IOException {
        Map<String, Integer> countryHits = uniqueIpConnections.values().stream()
            .map(conn -> geoIPService.getCountry(cleanIpAddress(conn.getAddress())))
            .filter(country -> !"UNKNOWN".equals(country))
            .collect(Collectors.groupingBy(
                country -> country,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
            ));

        log.info("Found hits for {} countries: {}", countryHits.size(),
            countryHits.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> String.format("%s=%d", e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "))
        );

        File heatMapFile = new File(reportsPath, "heat-map-" +
            LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".png");
        worldMapGenerator.generateHeatmap(countryHits, heatMapFile);
    }

    private void generateLocationMap(Map<String, PersonaConnectionEntity> uniqueIpConnections) throws IOException {
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

        File locationMapFile = new File(reportsPath, "location-map-" +
            LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".png");
        worldMapGenerator.generateLocationMap(locationInfoMap, locationMapFile);
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