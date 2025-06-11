package com.ea.services;

import com.ea.enums.SubscriptionType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordBotService {

    @Value("${services.bot-activity-enabled}")
    private boolean botActivityEnabled;

    private final JDA jda;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @PostConstruct
    public void init() {
        if (!botActivityEnabled) {
            log.debug("Bot activity is disabled, skipping initialization");
            return;
        }
        jda.addEventListener(new ListenerAdapter() {
            @Override
            public void onReady(ReadyEvent event) {
                jda.updateCommands()
                    .addCommands(
                        Commands.slash("subscribe", "Subscribe this channel to updates. Types: scoreboard, logs, alerts, activity-map, status")
                            .addOptions(
                                new OptionData(OptionType.STRING, "type", "Type of updates to subscribe to", true)
                                    .addChoices(
                                        new Command.Choice("Game scoreboards (end of round)", SubscriptionType.SCOREBOARD.getValue()),
                                        new Command.Choice("Player connection/game logs", SubscriptionType.LOGS.getValue()),
                                        new Command.Choice("Alerts: DNS IP update, maintenance", SubscriptionType.ALERTS.getValue()),
                                        new Command.Choice("Periodic activity map", SubscriptionType.ACTIVITY_MAP.getValue()),
                                        new Command.Choice("Server status (games, players). Should be used on its own channel", SubscriptionType.STATUS.getValue())
                                    )
                            ),
                        Commands.slash("unsubscribe", "Unsubscribe this server from updates. Types: scoreboard, logs, alerts, activity-map, status")
                            .addOptions(
                                new OptionData(OptionType.STRING, "type", "Type of updates to unsubscribe from", true)
                                    .addChoices(
                                        new Command.Choice("Game scoreboards (end of round)", SubscriptionType.SCOREBOARD.getValue()),
                                        new Command.Choice("Player connection/game logs", SubscriptionType.LOGS.getValue()),
                                        new Command.Choice("Alerts: DNS IP update, maintenance", SubscriptionType.ALERTS.getValue()),
                                        new Command.Choice("Periodic activity map", SubscriptionType.ACTIVITY_MAP.getValue()),
                                        new Command.Choice("Server status (games, players)", SubscriptionType.STATUS.getValue())
                                    )
                            ),
                        Commands.slash("alert", "Send an alert to all 'alerts' subscribers (restricted to alert role)")
                            .addOptions(
                                new OptionData(OptionType.STRING, "message", "The alert message to broadcast", true)
                            ),
                        Commands.slash("dns", "Show the current DNS IP address"),
                        Commands.slash("stats", "Show player stats")
                            .addOptions(
                                new OptionData(OptionType.STRING, "game", "Game name", true)
                                    .addChoices(
                                        new Command.Choice("psp/mohh", "PSP/MOH07"),
                                        new Command.Choice("psp/mohh2", "PSP/MOH08"),
                                        new Command.Choice("wii/mohh2", "WII/MOH08")
                                    ),
                                new OptionData(OptionType.STRING, "name", "Player name", true)
                            )
                    )
                    .queue();
            }
        });
    }

    public void sendMessage(List<String> channelIds, String message) {
        if (!botActivityEnabled) {
            log.debug("Bot activity is disabled, skipping message: {}", message);
            return;
        }
        for (String channelId : channelIds) {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null && message != null && !message.isEmpty()) {
                Runnable sendTask = () -> channel.sendMessage(message).queue();
                scheduler.schedule(sendTask, 1, TimeUnit.SECONDS);
            }
        }
    }

    public void sendImages(List<String> channelIds, List<File> imageFiles, String message) {
        if (!botActivityEnabled) {
            log.debug("Bot activity is disabled, skipping images: {}", imageFiles.stream().map(File::getName).toList());
            return;
        }
        for (String channelId : channelIds) {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                Runnable sendTask = () -> {
                    try {
                        // Discord allows up to 10 files per message
                        int batchSize = 10;
                        for (int i = 0; i < imageFiles.size(); i += batchSize) {
                            List<File> batch = imageFiles.subList(i, Math.min(i + batchSize, imageFiles.size()));
                            List<FileUpload> uploads = batch.stream().map(FileUpload::fromData).toList();
                            if (message == null || message.isEmpty() || i > 0) {
                                channel.sendFiles(uploads).queue();
                            } else {
                                channel.sendMessage(message).addFiles(uploads).queue();
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error sending images to Discord channel {}", channelId, e);
                    }
                };
                scheduler.schedule(sendTask, 1, TimeUnit.SECONDS);
            }
        }
    }

    public void updateActivity(String activity) {
        if (!botActivityEnabled) {
            log.debug("Bot activity is disabled, skipping activity update: {}", activity);
            return;
        }

        if (jda != null) {
            jda.getPresence().setActivity(Activity.customStatus(activity));
        }
    }
}