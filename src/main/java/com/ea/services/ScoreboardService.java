package com.ea.services;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

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
            setGameInfoIntoContext(context, game);

            // Read CSS content without modifications
            String cssContent = new String(Files.readAllBytes(Paths.get("src/main/resources/static/styles.css")), StandardCharsets.UTF_8);
            context.setVariable("styles", cssContent);

            setImagesIntoContext(context);

            // Generate the HTML content
            String htmlContent = templateEngine.process("scoreboard-dm", context);
            if(!context.getVariable("gameModeId").equals("8")) {
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

    private void setGameInfoIntoContext(Context context, GameEntity game) {
        String[] params = game.getParams().split(",");
        context.setVariable("gameName", game.getName());
        context.setVariable("gameModeId", params[0]);
        context.setVariable("mapName", MapMoHH.getMapNameByHexId(params[1]));
        context.setVariable("friendlyFireMode", params[2]);
        context.setVariable("aimAssist", params[3]);
        context.setVariable("ranked", params[8]);
        context.setVariable("gameStartTime", game.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        context.setVariable("gameDuration", Duration.between(game.getStartTime(), game.getEndTime()).toMinutes());
    }

    private void setImagesIntoContext(Context context) throws IOException {
        byte[] imageBytes = Files.readAllBytes(Paths.get("src/main/resources/static/images/holland-bridge.png"));
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String backgroundImageDataUrl = "data:image/png;base64," + base64Image;
        context.setVariable("backgroundImageDataUrl", backgroundImageDataUrl);

        if(context.getVariable("ranked").toString().equals("1")) {
            byte[] rankedImageBytes = Files.readAllBytes(Paths.get("src/main/resources/static/images/ranked.png"));
            String base64RankedImage = Base64.getEncoder().encodeToString(rankedImageBytes);
            String rankedImageDataUrl = "data:image/png;base64," + base64RankedImage;
            context.setVariable("rankedImageDataUrl", rankedImageDataUrl);
        }

        String friendlyFireMode = context.getVariable("friendlyFireMode").toString();
        if(friendlyFireMode.equals("1") || friendlyFireMode.equals("2")) {
            byte[] friendlyFireImageBytes = null;
            if (friendlyFireMode.equals("1")) {
                friendlyFireImageBytes = Files.readAllBytes(Paths.get("src/main/resources/static/images/friendly-fire.png"));
            } else if (friendlyFireMode.equals("2")) {
                friendlyFireImageBytes = Files.readAllBytes(Paths.get("src/main/resources/static/images/reverse-friendly-fire.png"));
            }
            String base64FriendlyFireImage = Base64.getEncoder().encodeToString(friendlyFireImageBytes);
            String friendlyFireImageDataUrl = "data:image/png;base64," + base64FriendlyFireImage;
            context.setVariable("friendlyFireImageDataUrl", friendlyFireImageDataUrl);
        }

        if(context.getVariable("aimAssist").toString().equals("1")) {
            byte[] aimAssistImageBytes = Files.readAllBytes(Paths.get("src/main/resources/static/images/aim-assist.png"));
            String base64AimAssistImage = Base64.getEncoder().encodeToString(aimAssistImageBytes);
            String aimAssistImageDataUrl = "data:image/png;base64," + base64AimAssistImage;
            context.setVariable("aimAssistImageDataUrl", aimAssistImageDataUrl);
        }
    }

}