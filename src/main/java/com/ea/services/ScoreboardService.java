package com.ea.services;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.ea.entities.GameReportEntity;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
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

    private final TemplateEngine templateEngine;

    public void generateScoreboard(GameEntity game) {
        log.info("Generating scoreboard for game #{}", game.getId());
        try {
            Context context = new Context();
            boolean proceed = setGameInfoIntoContext(context, game);
            if(!proceed) {
                log.info("Skipping game #{}", game.getId());
                return; // The report is not interesting (no kills or less than 2 players)
            }

            // Read CSS content without modifications
            String cssContent = new String(Files.readAllBytes(Paths.get("src/main/resources/static/styles.css")), StandardCharsets.UTF_8);
            context.setVariable("styles", cssContent);

            setImagesIntoContext(context);

            // Generate the HTML content
            String htmlContent;
            if(context.getVariable("gameModeId").toString().equals("8")) {
                htmlContent = templateEngine.process("scoreboard-dm", context);
            } else {
                htmlContent = templateEngine.process("scoreboard-team", context);
            }

            // Save HTML content to a temporary file
            File htmlFile = File.createTempFile("scoreboard", ".html");
            Files.write(htmlFile.toPath(), htmlContent.getBytes(StandardCharsets.UTF_8));

            // Set up headless Chrome
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments(
                "--disable-gpu",
                "--window-size=1920,1080",
                "--hide-scrollbars",
                "--allow-file-access-from-files");
            WebDriver driver = new ChromeDriver(options);

            // Load the HTML file
            driver.get(htmlFile.toURI().toString());

            // Take a screenshot
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);

            // Save the screenshot
            File imageDir = new File("images");
            if (!imageDir.exists()) {
                imageDir.mkdirs();
            }
            Files.copy(screenshot.toPath(),
                    Paths.get(imageDir.getPath(), "scoreboard_#" + game.getId() + ".png"),
                    StandardCopyOption.REPLACE_EXISTING);

            // Clean up
            driver.quit();
            htmlFile.delete();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean setGameInfoIntoContext(Context context, GameEntity game) {
        String[] params = game.getParams().split(",");
        context.setVariable("gameName", game.getName());
        context.setVariable("gameModeId", params[0]);
        context.setVariable("mapName", MapMoHH.getMapNameByHexId(params[1]));
        context.setVariable("friendlyFireMode", params[2]);
        context.setVariable("aimAssist", params[3]);
        context.setVariable("ranked", params[8]);
        context.setVariable("gameStartTime", game.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        context.setVariable("gameDuration", Duration.between(game.getStartTime(), game.getEndTime()).toMinutes());

        Set<GameReportEntity> reports = game.getGameReports();
        Map<String, GameReportEntity> aggregatedReports = new HashMap<>();

        for (GameReportEntity report : reports) {
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

        if(params[0].equals("8")) {
            setDeathmatchInfoIntoContext(context, aggregatedReports);
        } else {
            setTeamInfoIntoContext(context, aggregatedReports);
        }
        return true;
    }

    private void setDeathmatchInfoIntoContext(Context context, Map<String, GameReportEntity> aggregatedReports) {
        List<GameReportEntity> sortedReports = aggregatedReports.values().stream()
                .sorted((report1, report2) -> {
                    int score1 = report1.getKill() - report1.getDeath();
                    int score2 = report2.getKill() - report2.getDeath();
                    return Integer.compare(score2, score1);
                })
                .limit(16)
                .toList();

        String winner = sortedReports.stream()
                        .filter(report -> report.getWin() > 0)
                        .findFirst()
                        .map(report -> report.getPersonaConnection().getPersona().getPers())
                        .orElse(null);

        context.setVariable("reports", sortedReports != null ? sortedReports : new ArrayList<>());
        context.setVariable("winner", winner + " Wins the Battle");
    }

    private void setTeamInfoIntoContext(Context context, Map<String, GameReportEntity> aggregatedReports) {
        List<GameReportEntity> sortedAxisReports = aggregatedReports.values().stream()
                .filter(report -> report.getAxis() > 0)
                .sorted((report1, report2) -> {
                    int score1 = report1.getKill() - report1.getDeath();
                    int score2 = report2.getKill() - report2.getDeath();
                    return Integer.compare(score2, score1);
                })
                .limit(16)
                .toList();

        List<GameReportEntity> sortedAlliesReports = aggregatedReports.values().stream()
                .filter(report -> report.getAllies() > 0)
                .sorted((report1, report2) -> {
                    int score1 = report1.getKill() - report1.getDeath();
                    int score2 = report2.getKill() - report2.getDeath();
                    return Integer.compare(score2, score1);
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

        context.setVariable("axisReports", sortedAxisReports != null ? sortedAxisReports : new ArrayList<>());
        context.setVariable("alliesReports", sortedAlliesReports != null ? sortedAlliesReports : new ArrayList<>());
        context.setVariable("axisTotalKills", axisTotalKills);
        context.setVariable("axisTotalDeaths", axisTotalDeaths);
        context.setVariable("alliesTotalKills", alliesTotalKills);
        context.setVariable("alliesTotalDeaths", alliesTotalDeaths);
        context.setVariable("winner", winner);
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
        aggregatedReport.setAllies(existingReport.getAllies() + report.getAllies());
        aggregatedReport.setAxis(existingReport.getAxis() + report.getAxis());
        aggregatedReport.setPlayTime(existingReport.getPlayTime() + report.getPlayTime());
        return aggregatedReport;
    }

    private void setImagesIntoContext(Context context) throws IOException {
        setImageIntoContext(context, "src/main/resources/static/images/holland-bridge.png", "backgroundImg");

        if(context.getVariable("ranked").toString().equals("1")) {
            setImageIntoContext(context, "src/main/resources/static/images/ranked.png", "rankedImg");
        }

        String friendlyFireMode = context.getVariable("friendlyFireMode").toString();
        if(friendlyFireMode.equals("1") || friendlyFireMode.equals("2")) {
            String imagePath = friendlyFireMode.equals("1") ? "src/main/resources/static/images/friendly-fire.png" : "src/main/resources/static/images/reverse-friendly-fire.png";
            setImageIntoContext(context, imagePath, "friendlyFireImg");
        }

        if(context.getVariable("aimAssist").toString().equals("1")) {
            setImageIntoContext(context, "src/main/resources/static/images/aim-assist.png", "aimAssistImg");
        }
    }
    
    private void setImageIntoContext(Context context, String imagePath, String variableName) throws IOException {
        byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String Img = "data:image/png;base64," + base64Image;
        context.setVariable(variableName, Img);
    }
}