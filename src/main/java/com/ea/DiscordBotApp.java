package com.ea;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DiscordBotApp {

	public static void main(String[] args) {
		SpringApplication.run(DiscordBotApp.class, args);
	}

}
