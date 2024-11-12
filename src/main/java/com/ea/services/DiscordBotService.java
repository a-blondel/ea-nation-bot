package com.ea.services;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class DiscordBotService {

    /*@Value("${discord.token}")
    private String token;

    private JDA jda;

    @PostConstruct
    public void init() throws Exception {
        jda = JDABuilder.createDefault(token).build();
    }

    public void sendMessage(String channelId, String message) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessage(message).queue();
        }
    }*/
}
