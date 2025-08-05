package com.ea.services.map;

import com.ea.model.GeoLocation;
import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class GeoIPService {
    private static final String MAXMIND_DOWNLOAD_URL = "https://download.maxmind.com/app/geoip_download";
    private static final String DATABASE_FILENAME = "GeoLite2-City.mmdb";
    private static final Pattern IP_PATTERN = Pattern.compile("^/?([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})(?::\\d+)?$");
    private static final Pattern PRIVATE_IP_PATTERN = Pattern.compile(
            "^(?:10\\.|172\\.(?:1[6-9]|2\\d|3[0-1])\\.|192\\.168\\.|127\\.|0\\.|169\\.254\\.|fc00::|fe80::)"
    );

    @Value("${geoip.license-key}")
    private String licenseKey;

    @Value("${geoip.data-dir}")
    private String dataDir;

    @Value("${services.map-enabled}")
    private boolean serviceEnabled;

    private DatabaseReader reader;

    @PostConstruct
    public void init() {
        if (!serviceEnabled) {
            log.debug("GeoIP initialization is disabled");
            return;
        }

        try {
            Path dataDirPath = Paths.get(dataDir);
            Files.createDirectories(dataDirPath);

            Path dbPath = dataDirPath.resolve(DATABASE_FILENAME);
            if (!Files.exists(dbPath)) {
                downloadDatabase();
            }

            initializeReader();
            log.info("GeoIP database initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize GeoIP database", e);
            throw new RuntimeException("Failed to initialize GeoIP database", e);
        }
    }

    private void initializeReader() throws IOException {
        Path dbPath = Paths.get(dataDir, DATABASE_FILENAME);
        reader = new DatabaseReader.Builder(dbPath.toFile())
                .withCache(new CHMCache())
                .build();
    }

    @Scheduled(cron = "0 0 10 * * 1")
    public void updateDatabase() {
        if (!serviceEnabled) {
            log.debug("GeoIP service is disabled");
            return;
        }

        try {
            log.info("Starting GeoIP database update");
            downloadDatabase();
            initializeReader();
            log.info("GeoIP database update completed");
        } catch (Exception e) {
            log.error("Failed to update GeoIP database", e);
        }
    }

    private void downloadDatabase() throws IOException {
        log.info("Downloading GeoIP database");
        String downloadUrl = String.format("%s?edition_id=GeoLite2-City&license_key=%s&suffix=tar.gz",
                MAXMIND_DOWNLOAD_URL, licenseKey);

        Path downloadPath = Paths.get(dataDir, "download.tar.gz");
        try {
            URL url = new URL(downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(downloadPath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            try (TarArchiveInputStream tarIn = new TarArchiveInputStream(
                    new GzipCompressorInputStream(Files.newInputStream(downloadPath)))) {
                TarArchiveEntry entry;
                while ((entry = tarIn.getNextTarEntry()) != null) {
                    if (entry.getName().endsWith(DATABASE_FILENAME)) {
                        Path dbPath = Paths.get(dataDir, DATABASE_FILENAME);
                        Files.copy(tarIn, dbPath, StandardCopyOption.REPLACE_EXISTING);
                        break;
                    }
                }
            }
        } finally {
            Files.deleteIfExists(downloadPath);
        }
    }

    public GeoLocation getLocation(String ipAddress) {
        if (reader == null) {
            return null;
        }

        try {
            Matcher matcher = IP_PATTERN.matcher(ipAddress);
            if (!matcher.find()) {
                return null;
            }

            String cleanIp = matcher.group(1);

            if (PRIVATE_IP_PATTERN.matcher(cleanIp).find()) {
                return null;
            }

            InetAddress ip = InetAddress.getByName(cleanIp);
            var city = reader.city(ip);

            return new GeoLocation(
                    city.getLocation().getLatitude(),
                    city.getLocation().getLongitude(),
                    city.getCountry().getIsoCode()
            );
        } catch (Exception e) {
            log.debug("Could not determine location for IP: {} - {}", ipAddress, e.getMessage());
            return null;
        }
    }

    public String getCountry(String ipAddress) {
        GeoLocation location = getLocation(ipAddress);
        return location != null ? location.getCountry() : "UNKNOWN";
    }
}