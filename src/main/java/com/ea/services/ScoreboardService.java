package com.ea.services;

import com.ea.entities.GameEntity;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class ScoreboardService {

    private final TemplateEngine templateEngine;

    public void generateScoreboard(GameEntity game) {
        try {
            Context context = new Context();
            context.setVariable("gameName", game.getName());

            // Read CSS content without modifications
            String cssContent = new String(Files.readAllBytes(Paths.get("src/main/resources/static/styles.css")), StandardCharsets.UTF_8);
            context.setVariable("styles", cssContent);

            // Read and encode the background image
            byte[] imageBytes = Files.readAllBytes(Paths.get("src/main/resources/static/images/holland-bridge.png"));
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String backgroundImageDataUrl = "data:image/png;base64," + base64Image;
            context.setVariable("backgroundImageDataUrl", backgroundImageDataUrl);

            // Generate the HTML content
            String htmlContent = templateEngine.process("scoreboard-dm", context);

            // Save HTML content to a temporary file
            File htmlFile = File.createTempFile("scoreboard", ".html");
            Files.write(htmlFile.toPath(), htmlContent.getBytes(StandardCharsets.UTF_8));

            // Set up headless Chrome
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--disable-gpu", "--window-size=1920,1080", "--hide-scrollbars");
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
                    Paths.get(imageDir.getPath(), "scoreboard.png"),
                    StandardCopyOption.REPLACE_EXISTING);

            // Clean up
            driver.quit();
            htmlFile.delete();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}