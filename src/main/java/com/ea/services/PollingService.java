package com.ea.services;


import com.ea.entities.GameEntity;
import com.ea.repositories.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

@RequiredArgsConstructor
@Service
public class PollingService {

    List<String> vers = List.of("PSP/MOHGPS071", "PSP/MOH07");

    private final GameRepository gameRepository;
    private final ScoreboardService scoreboardService;

    /**
     * TO REMOVE
     */
    @PostConstruct
    public void init() {
        pollDatabase();
    }

    //@Scheduled(fixedRate = 20000)
    public void pollDatabase() {
        List<GameEntity> mohhGames = gameRepository.findByVersInAndEndTimeIsNotNull(vers);

        for (GameEntity game : mohhGames) {
            scoreboardService.generateScoreboard(game);
        }

    }
}
