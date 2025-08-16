package com.ea.services.discord;

import com.ea.entities.discord.StatusMessageEntity;
import com.ea.enums.GameGenre;
import com.ea.repositories.discord.DiscordStatusMessageRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatusMessageService {
    private final DiscordStatusMessageRepository statusMessageRepository;
    private final JDA jda;
    private final Object driverLock = new Object();
    @Value("${services.bot-activity-enabled}")
    private boolean botActivityEnabled;
    @Value("${dns.name}")
    private String dnsName;
    // Singleton ChromeDriver instance
    private ChromeDriver singletonDriver;

    private static ChromeOptions getChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",
                "--disable-extensions",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1240,1200",
                "--hide-scrollbars",
                "--memory-pressure-off",
                "--max_old_space_size=512",
                "--disable-background-timer-throttling",
                "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding"
        );
        return options;
    }

    @Scheduled(fixedDelay = 30000)
    public void updateStatusMessages() {
        if (!botActivityEnabled) {
            log.debug("Bot activity updates are disabled");
            return;
        }

        // Only update FPS genre status messages for now (as mentioned in requirements)
        List<StatusMessageEntity> entries = statusMessageRepository.findAll().stream()
                .filter(entry -> entry.getGameGenre() == GameGenre.FPS)
                .toList();

        if (entries.isEmpty()) return;
        File screenshot;
        try {
            screenshot = takeScreenshot();
        } catch (Exception e) {
            log.error("Failed to take status screenshot", e);
            return;
        }
        long unixTimestamp = Instant.now().getEpochSecond();
        String content = "Updated <t:" + unixTimestamp + ":R>";

        // Use CountDownLatch to wait for all async operations
        CountDownLatch latch = new CountDownLatch(entries.size());
        List<StatusMessageEntity> updatedEntries = new CopyOnWriteArrayList<>();

        for (StatusMessageEntity entry : entries) {
            TextChannel channel = jda.getTextChannelById(entry.getChannelId());
            if (channel == null) {
                latch.countDown();
                continue;
            }
            try {
                if (entry.getMessageId() == null) {
                    channel.sendMessage(content)
                            .addFiles(FileUpload.fromData(screenshot, "status.png"))
                            .queue(msg -> {
                                entry.setMessageId(msg.getId());
                                entry.setUpdatedAt(LocalDateTime.now());
                                updatedEntries.add(entry);
                                latch.countDown();
                            }, error -> {
                                log.error("Failed to send status message for channel {}", entry.getChannelId(), error);
                                latch.countDown();
                            });
                } else {
                    channel.editMessageById(entry.getMessageId(), content)
                            .setFiles(FileUpload.fromData(screenshot, "status.png"))
                            .queue(success -> {
                                entry.setUpdatedAt(LocalDateTime.now());
                                updatedEntries.add(entry);
                                latch.countDown();
                            }, error -> {
                                log.error("Failed to edit status message for channel {}", entry.getChannelId(), error);
                                latch.countDown();
                            });
                }
            } catch (Exception e) {
                log.error("Failed to send or edit status message for channel {}", entry.getChannelId(), e);
                latch.countDown();
            }
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for Discord message operations to complete", e);
        }
        if (!updatedEntries.isEmpty()) {
            statusMessageRepository.saveAll(updatedEntries);
        }

        // Clean up the screenshot file
        if (screenshot.exists()) {
            boolean deleted = screenshot.delete();
            if (!deleted) {
                log.warn("Failed to delete temporary screenshot file: {}", screenshot.getAbsolutePath());
            }
        }
    }

    private File takeScreenshot() throws IOException {
        String statusUrl = "http://" + dnsName + "/gamelist.html";
        ChromeDriver driver = getOrCreateDriver();
        synchronized (driverLock) {
            // Always reset window size to default before each screenshot
            int width = 1240;
            int height = 1200;
            driver.manage().window().setSize(new Dimension(width, height));

            driver.get(statusUrl);

            // Force dark mode for prefers-color-scheme
            try {
                driver.executeCdpCommand(
                        "Emulation.setEmulatedMedia",
                        Map.of("features", List.of(
                                Map.of("name", "prefers-color-scheme", "value", "dark")
                        ))
                );
            } catch (Exception e) {
                log.warn("Could not set prefers-color-scheme to dark: {}", e.getMessage());
            }

            // Also set theme attribute and localStorage for frontend JS
            try {
                driver.executeScript(
                        "document.documentElement.setAttribute('data-theme', 'dark'); localStorage.setItem('theme', 'dark');"
                );
            } catch (Exception e) {
                log.warn("Could not set data-theme or localStorage for dark mode: {}", e.getMessage());
            }

            // Wait for the loading div to disappear (data loaded)
            try {
                new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(d -> {
                            WebElement loading = d.findElement(By.id("loading"));
                            return !loading.isDisplayed() || "none".equals(loading.getCssValue("display"));
                        });
            } catch (Exception e) {
                log.warn("Timeout waiting for loading to disappear, taking screenshot anyway");
            }

            // Remove the <header> element
            try {
                driver.executeScript(
                        "var header = document.querySelector('header'); if(header) header.remove();"
                );
            } catch (Exception e) {
                log.warn("Could not remove header element before screenshot");
            }

            // Measure actual content height and set window size accordingly
            try {
                // Scroll to bottom first to ensure all content is rendered
                driver.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(200);

                // Get the actual content height using multiple methods
                Object result = driver.executeScript(
                        "var body = document.body, html = document.documentElement;" +
                                "return Math.max(" +
                                "body.scrollHeight, body.offsetHeight, body.clientHeight," +
                                "html.scrollHeight, html.offsetHeight, html.clientHeight" +
                                ");"
                );
                if (result instanceof Long) {
                    height = ((Long) result).intValue();
                } else if (result instanceof Number) {
                    height = ((Number) result).intValue();
                }
                // Add more padding to ensure we don't cut anything
                height += 150;
                if (height < 400) height = 400;
                if (height > 5000) height = 5000;

                driver.manage().window().setSize(new Dimension(width, height));
                driver.executeScript(
                        "document.body.style.overflow='hidden'; document.documentElement.style.overflow='hidden';"
                );
                // Small delay to let the browser render at new size
                Thread.sleep(500);
            } catch (Exception e) {
                log.warn("Could not set dynamic window size, using default: {}", e.getMessage());
            }

            // Use regular screenshot
            File temp = File.createTempFile("status", ".png");
            try {
                File screenshot = driver.getScreenshotAs(OutputType.FILE);
                Files.copy(screenshot.toPath(), temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return temp;
            } catch (Exception e) {
                log.error("Failed to take screenshot: {}", e.getMessage());
                // Clean up temp file if screenshot failed
                if (temp.exists()) {
                    boolean deleted = temp.delete();
                    if (!deleted) {
                        log.warn("Failed to delete temporary file: {}", temp.getAbsolutePath());
                    }
                }
                throw new IOException("Screenshot failed", e);
            }
        }
    }

    private ChromeDriver getOrCreateDriver() {
        synchronized (driverLock) {
            if (singletonDriver == null) {
                singletonDriver = new ChromeDriver(getChromeOptions());
            }
            return singletonDriver;
        }
    }

    @PreDestroy
    public void shutdownDriver() {
        synchronized (driverLock) {
            if (singletonDriver != null) {
                try {
                    singletonDriver.quit();
                } catch (Exception e) {
                    log.warn("Error while closing singleton ChromeDriver: {}", e.getMessage());
                }
            }
        }
    }

    @Transactional
    public StatusMessageEntity upsertStatusMessage(String guildId, String channelId, GameGenre gameGenre) {
        StatusMessageEntity entity = statusMessageRepository.findByGuildIdAndGameGenre(guildId, gameGenre)
                .orElse(new StatusMessageEntity());
        entity.setGuildId(guildId);
        entity.setChannelId(channelId);
        entity.setGameGenre(gameGenre);
        entity.setUpdatedAt(LocalDateTime.now());
        return statusMessageRepository.save(entity);
    }

    @Transactional
    public void deleteStatusMessage(String guildId, GameGenre gameGenre) {
        statusMessageRepository.deleteByGuildIdAndGameGenre(guildId, gameGenre);
    }
}
