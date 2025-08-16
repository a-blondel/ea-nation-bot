package com.ea.services.map;

import com.ea.entities.discord.ChannelSubscriptionEntity;
import com.ea.enums.GameGenre;
import com.ea.enums.SubscriptionType;
import com.ea.model.GeoLocation;
import com.ea.model.LocationInfo;
import com.ea.services.discord.ChannelSubscriptionService;
import com.ea.services.discord.DiscordBotService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.style.Rule;
import org.geotools.api.style.Style;
import org.geotools.filter.FilterFactoryImpl;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.MapContent;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.StyleBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class WorldMapGenerator {
    private static final int MAP_WIDTH = 1920;
    private static final int MAP_HEIGHT = 1080;
    private static final int LEGEND_PADDING = 20;
    private static final int LEGEND_WIDTH = 250;
    private static final int LEGEND_HEIGHT = 120;
    private static final int LEGEND_ITEM_HEIGHT = 20;
    private static final Font LEGEND_FONT = new Font("Arial", Font.PLAIN, 12);
    private static final Font LEGEND_TITLE_FONT = new Font("Arial", Font.BOLD, 13);
    private static final Font LEGEND_NUMBER_FONT = new Font("Arial", Font.BOLD, 14);
    private static final Font IP_FONT = new Font("Arial", Font.PLAIN, 10);

    private final ResourceLoader resourceLoader;
    private final DiscordBotService discordBotService;
    private final ChannelSubscriptionService channelSubscriptionService;

    @Value("${services.map-enabled}")
    private boolean serviceEnabled;

    @Value("${map.colors.dots.size}")
    private int dotSize;

    @Value("${map.colors.background-rgb}")
    private String backgroundRgb;

    @Value("${map.colors.border-rgb}")
    private String borderRgb;

    @Value("${map.colors.no-data-rgb}")
    private String noDataRgb;

    @Value("${map.colors.dots.color-rgb}")
    private String dotColorRgb;

    @Value("${map.colors.dots.border-color-rgb}")
    private String dotBorderColorRgb;

    @Value("${map.colors.dots.opacity}")
    private int dotOpacity;

    @Value("${map.colors.dots.border-opacity}")
    private int dotBorderOpacity;

    @Value("${map.colors.gradient.min-red}")
    private int minRed;

    @Value("${map.colors.gradient.min-green}")
    private int minGreen;

    @Value("${map.colors.gradient.min-blue}")
    private int minBlue;

    @Value("${map.colors.gradient.max-red}")
    private int maxRed;

    @Value("${map.colors.gradient.max-green}")
    private int maxGreen;

    @Value("${map.colors.gradient.max-blue}")
    private int maxBlue;

    @Value("${map.show-names}")
    private boolean showNames;

    private Color backgroundColor;
    private Color borderColor;
    private Color noDataColor;
    private Color dotColor;
    private Color dotBorderColor;

    @PostConstruct
    public void init() {
        if (!serviceEnabled) {
            log.debug("WorldMapGenerator service is disabled");
            return;
        }

        backgroundColor = parseRgbString(backgroundRgb);
        borderColor = parseRgbString(borderRgb);
        noDataColor = parseRgbString(noDataRgb);

        Color baseDotColor = parseRgbString(dotColorRgb);
        Color baseDotBorderColor = parseRgbString(dotBorderColorRgb);

        dotColor = new Color(
                baseDotColor.getRed(),
                baseDotColor.getGreen(),
                baseDotColor.getBlue(),
                dotOpacity
        );

        dotBorderColor = new Color(
                baseDotBorderColor.getRed(),
                baseDotBorderColor.getGreen(),
                baseDotBorderColor.getBlue(),
                dotBorderOpacity
        );
    }

    private Color parseRgbString(String rgb) {
        String[] parts = rgb.split(",");
        return new Color(
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())
        );
    }

    public void generateHeatmap(Map<String, Integer> countryHits, File outputFile, GameGenre genre) throws IOException {
        if (countryHits == null || countryHits.isEmpty()) {
            log.warn("No country hits data provided");
            return;
        }

        log.info("Generating heat map based on {} countries", countryHits.size());

        Resource shapefileResource = resourceLoader.getResource("classpath:world-map/ne_110m_admin_0_countries.shp");
        FileDataStore dataStore = FileDataStoreFinder.getDataStore(shapefileResource.getURL());
        SimpleFeatureSource featureSource = dataStore.getFeatureSource();

        try {
            MapContent map = new MapContent();
            map.getViewport().setCoordinateReferenceSystem(DefaultGeographicCRS.WGS84);

            ReferencedEnvelope worldBounds = new ReferencedEnvelope(
                    -180, 180, -90, 90, DefaultGeographicCRS.WGS84
            );
            map.getViewport().setBounds(worldBounds);
            map.addLayer(new FeatureLayer(featureSource, createHeatmapStyle(countryHits)));

            BufferedImage image = new BufferedImage(MAP_WIDTH, MAP_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            graphics.setColor(backgroundColor);
            graphics.fillRect(0, 0, MAP_WIDTH, MAP_HEIGHT);

            StreamingRenderer renderer = new StreamingRenderer();
            renderer.setMapContent(map);
            renderer.setRendererHints(Map.of(
                    StreamingRenderer.ADVANCED_PROJECTION_HANDLING_KEY, true,
                    StreamingRenderer.CONTINUOUS_MAP_WRAPPING, true
            ));

            renderer.paint(graphics, new Rectangle(0, 0, MAP_WIDTH, MAP_HEIGHT), worldBounds);

            addLegend(graphics, getMinMaxHits(countryHits), countryHits);

            outputFile.getParentFile().mkdirs();
            ImageIO.write(image, "PNG", outputFile);

            graphics.dispose();
            map.dispose();
            log.info("Heat map generated successfully at: {}", outputFile.getAbsolutePath());

            List<ChannelSubscriptionEntity> mapSubs = channelSubscriptionService.getAllByTypeAndGenre(SubscriptionType.ACTIVITY_MAP, genre);
            List<String> channelIds = mapSubs.stream().map(ChannelSubscriptionEntity::getChannelId).collect(Collectors.toList());
            discordBotService.sendImages(channelIds, List.of(outputFile), "🗺️ Weekly activity map");
        } finally {
            dataStore.dispose();
        }
    }

    private void addLegend(Graphics2D graphics, int[] minMaxHits, Map<String, Integer> countryHits) {
        int legendY = MAP_HEIGHT - LEGEND_HEIGHT - LEGEND_PADDING - 100;

        graphics.setColor(new Color(0, 0, 0, 200));
        graphics.fillRoundRect(LEGEND_PADDING, legendY,
                LEGEND_WIDTH, LEGEND_HEIGHT, 10, 10);

        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        graphics.setColor(Color.WHITE);
        graphics.setFont(LEGEND_TITLE_FONT);
        graphics.drawString("Playerbase heatmap",
                LEGEND_PADDING + 10,
                legendY + 20);

        int samples = 5;
        int sampleWidth = (LEGEND_WIDTH - 20) / samples;
        int y = legendY + 35;

        for (int i = 0; i < samples; i++) {
            float ratio = i / (float) (samples - 1);
            Color color = getHeatmapColor(
                    (int) (minMaxHits[0] + ratio * (minMaxHits[1] - minMaxHits[0])),
                    minMaxHits[1]
            );

            graphics.setColor(color);
            graphics.fillRect(
                    LEGEND_PADDING + 10 + (i * sampleWidth),
                    y,
                    sampleWidth,
                    LEGEND_ITEM_HEIGHT
            );

            graphics.setColor(new Color(100, 100, 100));
            graphics.drawRect(
                    LEGEND_PADDING + 10 + (i * sampleWidth),
                    y,
                    sampleWidth,
                    LEGEND_ITEM_HEIGHT
            );

            graphics.setColor(Color.WHITE);
            graphics.setFont(LEGEND_NUMBER_FONT);
            String value = String.valueOf((int) (minMaxHits[0] + ratio * (minMaxHits[1] - minMaxHits[0])));
            int textWidth = graphics.getFontMetrics().stringWidth(value);
            graphics.drawString(value,
                    LEGEND_PADDING + 10 + (i * sampleWidth) + (sampleWidth - textWidth) / 2,
                    y + LEGEND_ITEM_HEIGHT + 15);
        }

        graphics.setColor(Color.WHITE);
        graphics.setFont(LEGEND_FONT);
        int statsY = y + LEGEND_ITEM_HEIGHT + 40;
        int statsX = LEGEND_PADDING + 10;

        graphics.drawString(String.format("Total players: %d", getMapStats(countryHits)),
                statsX, statsY);
        graphics.drawString(String.format("Total countries: %d", countryHits.size()),
                statsX, statsY + 20);
    }

    private int getMapStats(Map<String, Integer> countryHits) {
        return countryHits.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int[] getMinMaxHits(Map<String, Integer> countryHits) {
        int min = countryHits.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = countryHits.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return new int[]{min, max};
    }

    private Style createHeatmapStyle(Map<String, Integer> countryHits) {
        StyleBuilder styleBuilder = new StyleBuilder();
        FilterFactoryImpl ff = new FilterFactoryImpl();
        List<Rule> rules = new ArrayList<>();

        int maxHits = countryHits.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(1);

        for (Map.Entry<String, Integer> entry : countryHits.entrySet()) {
            Color color = getHeatmapColor(entry.getValue(), maxHits);
            Rule rule = styleBuilder.createRule(styleBuilder.createPolygonSymbolizer(
                    styleBuilder.createStroke(new Color(100, 100, 100, 180), 0.3), // Subtle gray border with transparency
                    styleBuilder.createFill(color)
            ));

            rule.setFilter(ff.or(
                    ff.equals(ff.property("ISO_A2"), ff.literal(entry.getKey())),
                    ff.equals(ff.property("ISO_A2_EH"), ff.literal(entry.getKey()))
            ));
            rules.add(rule);
        }

        Rule defaultRule = styleBuilder.createRule(styleBuilder.createPolygonSymbolizer(
                styleBuilder.createStroke(new Color(70, 70, 70, 180), 0.3), // Even more subtle border for inactive countries
                styleBuilder.createFill(noDataColor)
        ));
        defaultRule.setElseFilter(true);
        rules.add(defaultRule);

        Style style = styleBuilder.createStyle();
        style.featureTypeStyles().add(styleBuilder.createFeatureTypeStyle("Feature", rules.toArray(new Rule[0])));
        return style;
    }

    private Color getHeatmapColor(int hits, int maxHits) {
        float intensity = (float) hits / maxHits;
        intensity = 1.0f - intensity; // Invert for brighter = less players

        return new Color(
                maxRed + (int) ((minRed - maxRed) * intensity),
                maxGreen + (int) ((minGreen - maxGreen) * intensity),
                maxBlue + (int) ((minBlue - maxBlue) * intensity)
        );
    }

    public void generateLocationMap(Map<String, LocationInfo> locationInfoMap, File outputFile, GameGenre genre) throws IOException {
        if (locationInfoMap == null || locationInfoMap.isEmpty()) {
            log.warn("No location information provided");
            return;
        }

        log.info("Generating location map based on {} personas", locationInfoMap.size());

        Resource shapefileResource = resourceLoader.getResource("classpath:world-map/ne_110m_admin_0_countries.shp");
        FileDataStore dataStore = FileDataStoreFinder.getDataStore(shapefileResource.getURL());
        SimpleFeatureSource featureSource = dataStore.getFeatureSource();

        try {
            MapContent map = new MapContent();
            map.getViewport().setCoordinateReferenceSystem(DefaultGeographicCRS.WGS84);

            ReferencedEnvelope worldBounds = new ReferencedEnvelope(
                    -180, 180, -90, 90, DefaultGeographicCRS.WGS84
            );
            map.getViewport().setBounds(worldBounds);

            // Create base map style with just country outlines
            StyleBuilder styleBuilder = new StyleBuilder();
            Rule rule = styleBuilder.createRule(styleBuilder.createPolygonSymbolizer(
                    styleBuilder.createStroke(borderColor, 0.3),
                    styleBuilder.createFill(noDataColor)
            ));
            Style style = styleBuilder.createStyle();
            style.featureTypeStyles().add(styleBuilder.createFeatureTypeStyle("Feature", new Rule[]{rule}));

            map.addLayer(new FeatureLayer(featureSource, style));

            BufferedImage image = new BufferedImage(MAP_WIDTH, MAP_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            graphics.setColor(backgroundColor);
            graphics.fillRect(0, 0, MAP_WIDTH, MAP_HEIGHT);

            StreamingRenderer renderer = new StreamingRenderer();
            renderer.setMapContent(map);
            renderer.setRendererHints(Map.of(
                    StreamingRenderer.ADVANCED_PROJECTION_HANDLING_KEY, true,
                    StreamingRenderer.CONTINUOUS_MAP_WRAPPING, true
            ));

            renderer.paint(graphics, new Rectangle(0, 0, MAP_WIDTH, MAP_HEIGHT), worldBounds);

            drawLocations(graphics, locationInfoMap, worldBounds);

            addDotLegend(graphics, locationInfoMap.size());

            outputFile.getParentFile().mkdirs();
            ImageIO.write(image, "PNG", outputFile);

            graphics.dispose();
            map.dispose();
            log.info("Location map generated successfully at: {}", outputFile.getAbsolutePath());

            List<ChannelSubscriptionEntity> mapSubs = channelSubscriptionService.getAllByTypeAndGenre(SubscriptionType.ACTIVITY_MAP, genre);
            List<String> channelIds = mapSubs.stream().map(ChannelSubscriptionEntity::getChannelId).collect(Collectors.toList());
            discordBotService.sendImages(channelIds, List.of(outputFile), "🗺️ Weekly density map");
        } finally {
            dataStore.dispose();
        }
    }

    private void drawLocations(Graphics2D graphics, Map<String, LocationInfo> locationInfoMap, ReferencedEnvelope worldBounds) {
        graphics.setFont(IP_FONT);

        for (LocationInfo info : locationInfoMap.values()) {
            GeoLocation location = info.getLocation();
            String personaName = info.getPersonaName();

            double screenX = MAP_WIDTH * (location.getLongitude() - worldBounds.getMinX()) / worldBounds.getWidth();
            double screenY = MAP_HEIGHT * (1 - (location.getLatitude() - worldBounds.getMinY()) / worldBounds.getHeight());

            // Draw dot
            graphics.setColor(dotBorderColor);
            graphics.fillOval((int) screenX - dotSize / 2 - 1, (int) screenY - dotSize / 2 - 1, dotSize + 2, dotSize + 2);
            graphics.setColor(dotColor);
            graphics.fillOval((int) screenX - dotSize / 2, (int) screenY - dotSize / 2, dotSize, dotSize);

            // Draw persona name only if showNames is true
            if (showNames) {
                FontMetrics fm = graphics.getFontMetrics();
                int textWidth = fm.stringWidth(personaName);
                int textHeight = fm.getHeight();

                // Draw text background
                graphics.setColor(new Color(0, 0, 0, 180));
                graphics.fillRect((int) screenX - textWidth / 2 - 2,
                        (int) screenY - dotSize - textHeight - 2,
                        textWidth + 4,
                        textHeight);

                // Draw persona name
                graphics.setColor(Color.WHITE);
                graphics.drawString(personaName,
                        (int) screenX - textWidth / 2,
                        (int) screenY - dotSize - 4);
            }
        }
    }

    private void addDotLegend(Graphics2D graphics, int totalLocations) {
        int legendY = MAP_HEIGHT - LEGEND_HEIGHT - LEGEND_PADDING - 100;

        graphics.setColor(new Color(0, 0, 0, 200));
        graphics.fillRoundRect(LEGEND_PADDING, legendY,
                LEGEND_WIDTH, LEGEND_HEIGHT, 10, 10);

        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        graphics.setColor(Color.WHITE);
        graphics.setFont(LEGEND_TITLE_FONT);
        graphics.drawString("Player locations",
                LEGEND_PADDING + 10,
                legendY + 20);

        int dotY = legendY + 40;
        int dotX = LEGEND_PADDING + 20;

        graphics.setColor(dotBorderColor);
        graphics.fillOval(dotX - dotSize / 2 - 1, dotY - dotSize / 2 - 1, dotSize + 2, dotSize + 2);
        graphics.setColor(dotColor);
        graphics.fillOval(dotX - dotSize / 2, dotY - dotSize / 2, dotSize, dotSize);

        graphics.setColor(Color.WHITE);
        graphics.setFont(LEGEND_FONT);
        graphics.drawString("= 1 player",
                dotX + 15, dotY + 5);
        graphics.drawString(String.format("Total players: %d", totalLocations),
                LEGEND_PADDING + 10, dotY + 30);
    }
}