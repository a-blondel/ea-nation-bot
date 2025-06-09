package com.ea.listeners;

import com.ea.enums.SubscriptionType;
import com.ea.services.ChannelSubscriptionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import static net.dv8tion.jda.api.Permission.MANAGE_SERVER;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChannelSubscriptionListener extends ListenerAdapter {
    private final JDA jda;
    private final ChannelSubscriptionService subscriptionService;

    @PostConstruct
    public void register() {
        jda.addEventListener(this);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();
        if (command.equals("subscribe")) {
            handleSubscription(event, true);
        } else if (command.equals("unsubscribe")) {
            handleSubscription(event, false);
        }
    }

    private void handleSubscription(SlashCommandInteractionEvent event, boolean subscribe) {
        if (!event.getMember().hasPermission(MANAGE_SERVER)) {
            event.reply("You must have the 'Manage Server' permission to use this command.").setEphemeral(true).queue();
            return;
        }
        String type = event.getOption("type").getAsString();
        SubscriptionType subscriptionType;
        try {
            subscriptionType = SubscriptionType.fromValue(type);
        } catch (IllegalArgumentException e) {
            event.reply("Invalid subscription type.").setEphemeral(true).queue();
            return;
        }
        String guildId = event.getGuild().getId();
        if (subscribe) {
            String channelId = event.getChannel().getId();
            subscriptionService.subscribe(guildId, channelId, subscriptionType);
            event.reply("Subscribed this channel for " + subscriptionType.getValue() + " updates.").setEphemeral(true).queue();
        } else {
            subscriptionService.unsubscribe(guildId, subscriptionType);
            event.reply("Unsubscribed this server from " + subscriptionType.getValue() + " updates.").setEphemeral(true).queue();
        }
    }
}
