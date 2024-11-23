package com.ea.services;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.ea.entities.GameReportEntity;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.ea.entities.GameEntity;
import com.ea.enums.MapMoHH;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreboardService {

    @Value("${discord.channel-id}")
    private String discordChannelId;

    @Value("${reports.path}")
    private String reportsPath;

    private final TemplateEngine templateEngine;
    private final DiscordBotService discordBotService;

    public void generateScoreboard(GameEntity game) {
        log.info("Generating scoreboard for game #{}", game.getId());
        try {
            Context context = new Context();
            boolean proceed = setGameInfoIntoContext(context, game);
            if(!proceed) {
                log.info("Skipping game #{}", game.getId());
                return; // The report is not interesting (no kills or less than 2 players)
            }

            try (InputStream cssStream = getClass().getResourceAsStream("/static/styles.css")) {
                if (cssStream == null) {
                    throw new IOException("Could not find resource styles.css");
                }
                String cssContent = new String(cssStream.readAllBytes());
                context.setVariable("styles", cssContent);
            }

            setImagesIntoContext(context);

            String htmlContent;
            if(context.getVariable("gameModeId").toString().equals("8")) {
                htmlContent = templateEngine.process("scoreboard-dm", context);
            } else {
                htmlContent = templateEngine.process("scoreboard-team", context);
            }

            File htmlFile = File.createTempFile("scoreboard", ".html");
            Files.writeString(htmlFile.toPath(), htmlContent);

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new",
                "--disable-extensions",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1920,1080",
                "--hide-scrollbars",
                "--allow-file-access-from-files");
            WebDriver driver = new ChromeDriver(options);
            driver.get(htmlFile.toURI().toString());

            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            File imageDir = new File(reportsPath);
            if (!imageDir.exists()) {
                imageDir.mkdirs();
            }
            File imageFile = new File(imageDir, "scoreboard_#" + game.getId() + ".png");
            Files.copy(screenshot.toPath(), imageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            driver.quit();
            htmlFile.delete();

            discordBotService.sendImage(discordChannelId, imageFile, null);

        } catch (Exception e) {
            log.error("Error generating scoreboard for game #{}", game.getId(), e);
        }
    }

    private boolean setGameInfoIntoContext(Context context, GameEntity game) {
        context.setVariable("gameName", game.getName().replaceAll("\"", ""));
        String[] params = game.getParams().split(",");
        String gameModeId = params[0];
        context.setVariable("gameModeId", gameModeId);
        context.setVariable("mapHexId", params[1]);
        context.setVariable("mapName", MapMoHH.getMapNameByHexId(params[1]));
        context.setVariable("friendlyFireMode", params[2]);
        context.setVariable("aimAssist", params[3]);
        context.setVariable("ranked", params[8]);
        context.setVariable("gameStartTime", game.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        context.setVariable("gameDuration", Duration.between(game.getStartTime(), game.getEndTime()).toMinutes());

        Set<GameReportEntity> reports = game.getGameReports();
        Map<String, GameReportEntity> aggregatedReports = new HashMap<>();

        for (GameReportEntity report : reports) {
            if(report.getPersonaConnection().isHost()) {
                continue;
            }
            String personaConnectionId = report.getPersonaConnection().getPersona().getPers();
            if (aggregatedReports.containsKey(personaConnectionId)) {
                GameReportEntity existingReport = aggregatedReports.get(personaConnectionId);
                GameReportEntity aggregatedReport = getAggregatedReport(report, existingReport);
                aggregatedReports.put(personaConnectionId, aggregatedReport);
            } else {
                aggregatedReports.put(personaConnectionId, report);
            }
        }

        if(aggregatedReports.size() < 2 || aggregatedReports.values().stream().noneMatch(report -> report.getKill() > 0)) {
            return false;
        }

        if(gameModeId.equals("8")) {
            setDeathmatchInfoIntoContext(context, aggregatedReports);
        } else {
            setTeamInfoIntoContext(context, aggregatedReports);
        }
        return true;
    }

    private void setDeathmatchInfoIntoContext(Context context, Map<String, GameReportEntity> aggregatedReports) {
        List<GameReportEntity> sortedReports = aggregatedReports.values().stream()
                .filter(report -> report.getKill() > 0 || report.getDeath() > 0)
                .sorted((report1, report2) -> {
                    int score1 = report1.getKill() - report1.getDeath();
                    int score2 = report2.getKill() - report2.getDeath();
                    return Integer.compare(score2, score1);
                })
                .peek(report -> {
                    String persona = report.getPersonaConnection().getPersona().getPers().replaceAll("\"", "");
                    report.getPersonaConnection().getPersona().setPers(persona);
                })
                .limit(16)
                .toList();

        String winner = sortedReports.stream()
                        .filter(report -> report.getWin() > 0)
                        .findFirst()
                        .map(report -> report.getPersonaConnection().getPersona().getPers())
                        .orElse(null);

        context.setVariable("reports", sortedReports);
        context.setVariable("winner", winner == null ? "Draw Battle" : winner + " Wins the Battle");
    }

    private void setTeamInfoIntoContext(Context context, Map<String, GameReportEntity> aggregatedReports) {
        List<GameReportEntity> sortedAxisReports = aggregatedReports.values().stream()
                .filter(report -> report.getKill() > 0 || report.getDeath() > 0)
                .filter(report -> report.getAxis() > 0 || (report.getAllies() == 0 && isMostlyAxis(report)))
                .sorted((report1, report2) -> {
                    int score1 = report1.getKill() - report1.getDeath();
                    int score2 = report2.getKill() - report2.getDeath();
                    return Integer.compare(score2, score1);
                })
                .peek(report -> {
                    String persona = report.getPersonaConnection().getPersona().getPers().replaceAll("\"", "");
                    report.getPersonaConnection().getPersona().setPers(persona);
                })
                .limit(16)
                .toList();

        List<GameReportEntity> sortedAlliesReports = aggregatedReports.values().stream()
                .filter(report -> report.getKill() > 0 || report.getDeath() > 0)
                .filter(report -> report.getAllies() > 0 || (report.getAxis() == 0 && !isMostlyAxis(report)))
                .sorted((report1, report2) -> {
                    int score1 = report1.getKill() - report1.getDeath();
                    int score2 = report2.getKill() - report2.getDeath();
                    return Integer.compare(score2, score1);
                })
                .peek(report -> {
                    String persona = report.getPersonaConnection().getPersona().getPers().replaceAll("\"", "");
                    report.getPersonaConnection().getPersona().setPers(persona);
                })
                .limit(16)
                .toList();

        int axisTotalKills = sortedAxisReports.stream().mapToInt(GameReportEntity::getKill).sum();
        int axisTotalDeaths = sortedAxisReports.stream().mapToInt(GameReportEntity::getDeath).sum();
        int alliesTotalKills = sortedAlliesReports.stream().mapToInt(GameReportEntity::getKill).sum();
        int alliesTotalDeaths = sortedAlliesReports.stream().mapToInt(GameReportEntity::getDeath).sum();

        String winner = sortedAxisReports.stream()
                        .filter(report -> report.getWin() > 0)
                        .findFirst()
                        .map(report -> "Axis")
                        .orElseGet(() -> sortedAlliesReports.stream()
                                .filter(report -> report.getWin() > 0)
                                .findFirst()
                                .map(report -> "Allies")
                                .orElse("Draw"));

        context.setVariable("axisReports", sortedAxisReports);
        context.setVariable("alliesReports", sortedAlliesReports);
        context.setVariable("axisTotalKills", axisTotalKills);
        context.setVariable("axisTotalDeaths", axisTotalDeaths);
        context.setVariable("alliesTotalKills", alliesTotalKills);
        context.setVariable("alliesTotalDeaths", alliesTotalDeaths);
        context.setVariable("winner", winner);
    }

    private boolean isMostlyAxis(GameReportEntity report) {
        int axisShots = report.getLugerShot() + report.getMp40Shot() + report.getMp44Shot() +
                report.getKarShot() + report.getGewrShot() + report.getPanzShot();
        int alliesShots = report.getColtShot() + report.getTomShot() + report.getBarShot() +
                report.getGarShot() + report.getEnfieldShot() + report.getBazShot();
        return axisShots > alliesShots;
    }

    private GameReportEntity getAggregatedReport(GameReportEntity report, GameReportEntity existingReport) {
        GameReportEntity aggregatedReport = new GameReportEntity();
        aggregatedReport.setKill(existingReport.getKill() + report.getKill());
        aggregatedReport.setDeath(existingReport.getDeath() + report.getDeath());
        aggregatedReport.setHead(existingReport.getHead() + report.getHead());
        aggregatedReport.setHit(existingReport.getHit() + report.getHit());
        aggregatedReport.setShot(existingReport.getShot() + report.getShot());
        aggregatedReport.setWin(existingReport.getWin() + report.getWin());
        aggregatedReport.setLoss(existingReport.getLoss() + report.getLoss());
        aggregatedReport.setDmRnd(existingReport.getDmRnd() + report.getDmRnd());
        aggregatedReport.setAllies(existingReport.getAllies() + report.getAllies());
        aggregatedReport.setAxis(existingReport.getAxis() + report.getAxis());
        aggregatedReport.setPlayTime(existingReport.getPlayTime() + report.getPlayTime());
        aggregatedReport.setGame(existingReport.getGame());
        aggregatedReport.setPersonaConnection(existingReport.getPersonaConnection());
        return aggregatedReport;
    }

    private void setImagesIntoContext(Context context) throws IOException {
        String mapHexId = context.getVariable("mapHexId").toString();
        Resource mapImageResource = findImageByMapHexId(mapHexId);
        setImageIntoContext(context, mapImageResource, "backgroundImg");

        setImageIntoContext(context, new ClassPathResource("/static/images/logout.png"), "logoutImg");

        if(context.getVariable("ranked").toString().equals("1")) {
            setImageIntoContext(context, new ClassPathResource("/static/images/ranked.png"), "rankedImg");
        }

        String friendlyFireMode = context.getVariable("friendlyFireMode").toString();
        if(friendlyFireMode.equals("1") || friendlyFireMode.equals("2")) {
            Resource friendlyFireResource = friendlyFireMode.equals("1")
                    ? new ClassPathResource("/static/images/friendly-fire.png")
                    : new ClassPathResource("/static/images/reverse-friendly-fire.png");
            setImageIntoContext(context, friendlyFireResource, "friendlyFireImg");
        }

        if(context.getVariable("aimAssist").toString().equals("1")) {
            setImageIntoContext(context, new ClassPathResource("/static/images/aim-assist.png"), "aimAssistImg");
        }
    }

    private Resource findImageByMapHexId(String mapHexId) throws IOException {
    ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources = resolver.getResources("classpath:/static/images/maps/" + mapHexId + "*.jpg");
    if (resources.length > 0) {
        return resources[0];
    } else {
        throw new IOException("No image found for mapHexId: " + mapHexId);
    }
}
    
    private void setImageIntoContext(Context context, Resource resource, String variableName) throws IOException {
        try (InputStream imageStream = resource.getInputStream()) {
            byte[] imageBytes = imageStream.readAllBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String Img = "data:image/png;base64," + base64Image;
            context.setVariable(variableName, Img);
        }
    }
}