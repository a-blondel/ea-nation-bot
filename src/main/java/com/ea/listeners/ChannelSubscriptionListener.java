package com.ea.listeners;

import com.ea.entities.discord.ChannelSubscriptionEntity;
import com.ea.entities.discord.ParamEntity;
import com.ea.enums.GameGenre;
import com.ea.enums.SubscriptionType;
import com.ea.repositories.discord.ParamRepository;
import com.ea.services.discord.ChannelSubscriptionService;
import com.ea.services.discord.DiscordBotService;
import com.ea.services.discord.StatusMessageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static net.dv8tion.jda.api.Permission.MANAGE_SERVER;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChannelSubscriptionListener extends ListenerAdapter {
    private final JDA jda;
    private final ChannelSubscriptionService subscriptionService;
    private final DiscordBotService discordBotService;
    private final ParamRepository paramRepository;
    private final StatusMessageService statusMessageService;

    @PostConstruct
    public void register() {
        jda.addEventListener(this);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();
        switch (command) {
            case "subscribe" -> handleSubscription(event, true);
            case "unsubscribe" -> handleSubscription(event, false);
            case "alert" -> handleAlert(event);
            case "dns" -> handleDns(event);
            case "stats" -> handleStats(event);
            default -> {
            }
        }
    }

    private void handleSubscription(SlashCommandInteractionEvent event, boolean subscribe) {
        String type = event.getOption("type").getAsString();
        String genreValue = event.getOption("genre").getAsString();

        SubscriptionType subscriptionType;
        GameGenre gameGenre;

        try {
            subscriptionType = SubscriptionType.fromValue(type);
            gameGenre = GameGenre.fromValue(genreValue);
        } catch (IllegalArgumentException e) {
            event.reply("Invalid subscription type or game genre.").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().hasPermission(MANAGE_SERVER)) {
            event.reply("You must have the 'Manage Server' permission to use this command.").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        if (subscribe) {
            String channelId = event.getChannel().getId();
            subscriptionService.subscribe(guildId, channelId, subscriptionType, gameGenre);
            if (subscriptionType == SubscriptionType.STATUS) {
                statusMessageService.upsertStatusMessage(guildId, channelId, gameGenre);
            }
            event.reply("Subscribed this channel for " + subscriptionType.getValue() + " updates in " + gameGenre.getValue() + " genre.").setEphemeral(true).queue();
        } else {
            subscriptionService.unsubscribe(guildId, subscriptionType, gameGenre);
            if (subscriptionType == SubscriptionType.STATUS) {
                statusMessageService.deleteStatusMessage(guildId, gameGenre);
            }
            event.reply("Unsubscribed this server from " + subscriptionType.getValue() + " updates in " + gameGenre.getValue() + " genre.").setEphemeral(true).queue();
        }
    }

    private void handleAlert(SlashCommandInteractionEvent event) {
        String message = event.getOption("message").getAsString();
        Optional<ParamEntity> roleParam = paramRepository.findById("ALERT_ROLE_ID");
        if (roleParam.isEmpty()) {
            event.reply("Alert role is not configured. Please set ALERT_ROLE_ID in the database.").setEphemeral(true).queue();
            return;
        }
        String alertRoleId = roleParam.get().getParamValue();
        if (event.getMember() == null || event.getMember().getRoles().stream().noneMatch(r -> r.getId().equals(alertRoleId))) {
            event.reply("You are not authorized to use this command.").setEphemeral(true).queue();
            return;
        }

        // Send alerts to all subscribers across all categories since alerts are global
        List<ChannelSubscriptionEntity> allAlertSubs = new ArrayList<>();
        for (GameGenre genre : GameGenre.values()) {
            allAlertSubs.addAll(subscriptionService.getAllByTypeAndGenre(SubscriptionType.ALERTS, genre));
        }
        List<String> channelIds = allAlertSubs.stream()
                .map(ChannelSubscriptionEntity::getChannelId)
                .distinct()
                .toList();
        discordBotService.sendMessage(channelIds, message);
        event.reply("Alert sent to all subscribers.").setEphemeral(true).queue();
    }

    private void handleDns(SlashCommandInteractionEvent event) {
        Optional<ParamEntity> ipParam = paramRepository.findById("LAST_KNOWN_IP");
        String ip = ipParam.map(ParamEntity::getParamValue).orElse("UNKNOWN");
        event.reply("`" + ip + "`").queue();
    }

    private void handleStats(SlashCommandInteractionEvent event) {
        event.reply("Not yet available 😉").setEphemeral(true).queue();
    }
}
