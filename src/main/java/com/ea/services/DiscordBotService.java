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
                        Commands.slash("subscribe", "Subscribe this channel to updates")
                            .addOptions(
                                new OptionData(OptionType.STRING, "type", "Type of updates to subscribe to", true)
                                    .addChoices(
                                        new Command.Choice(SubscriptionType.SCOREBOARD.getValue(), SubscriptionType.SCOREBOARD.getValue()),
                                        new Command.Choice(SubscriptionType.EVENTS.getValue(), SubscriptionType.EVENTS.getValue()),
                                        new Command.Choice(SubscriptionType.IP_UPDATE.getValue(), SubscriptionType.IP_UPDATE.getValue()),
                                        new Command.Choice(SubscriptionType.ACTIVITY_MAP.getValue(), SubscriptionType.ACTIVITY_MAP.getValue())
                                    )
                            ),
                        Commands.slash("unsubscribe", "Unsubscribe this server from updates")
                            .addOptions(
                                new OptionData(OptionType.STRING, "type", "Type of updates to unsubscribe from", true)
                                    .addChoices(
                                        new Command.Choice(SubscriptionType.SCOREBOARD.getValue(), SubscriptionType.SCOREBOARD.getValue()),
                                        new Command.Choice(SubscriptionType.EVENTS.getValue(), SubscriptionType.EVENTS.getValue()),
                                        new Command.Choice(SubscriptionType.IP_UPDATE.getValue(), SubscriptionType.IP_UPDATE.getValue()),
                                        new Command.Choice(SubscriptionType.ACTIVITY_MAP.getValue(), SubscriptionType.ACTIVITY_MAP.getValue())
                                    )
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

    public void sendImage(List<String> channelIds, File imageFile, String message) {
        if (!botActivityEnabled) {
            log.debug("Bot activity is disabled, skipping image: {}", imageFile.getName());
            return;
        }
        for (String channelId : channelIds) {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                Runnable sendTask = () -> {
                    if (message == null || message.isEmpty()) {
                        channel.sendFiles(FileUpload.fromData(imageFile)).queue();
                    } else {
                        channel.sendMessage(message).addFiles(FileUpload.fromData(imageFile)).queue();
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