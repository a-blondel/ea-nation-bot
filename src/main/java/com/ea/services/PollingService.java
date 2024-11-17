package com.ea.services;

import com.ea.entities.GameEntity;
import com.ea.entities.discord.ParamEntity;
import com.ea.enums.Params;
import com.ea.repositories.GameRepository;
import com.ea.repositories.discord.ParamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class PollingService {

    List<String> vers = List.of("PSP/MOHGPS071", "PSP/MOH07");

    private final ParamRepository paramRepository;
    private final GameRepository gameRepository;
    private final ScoreboardService scoreboardService;

    @Scheduled(fixedRate = 20000)
    public void pollDatabase() {
        ParamEntity lastFetchTimeEntity = paramRepository.findById(Params.LAST_FETCH_TIME.name()).orElseGet(() -> {
            ParamEntity paramEntity = new ParamEntity();
            paramEntity.setParamKey(Params.LAST_FETCH_TIME.name());
            paramEntity.setParamValue(LocalDateTime.now().minusMinutes(5));
            return paramEntity;
        });
        LocalDateTime lastFetchTime = lastFetchTimeEntity.getParamValue();
        LocalDateTime currentFetchTime = LocalDateTime.now();

        List<GameEntity> mohhGames = gameRepository.findByVersInAndEndTimeBetween(vers, lastFetchTime, currentFetchTime);

        for (GameEntity game : mohhGames) {
            scoreboardService.generateScoreboard(game);
        }

        lastFetchTimeEntity.setParamValue(currentFetchTime);
        paramRepository.save(lastFetchTimeEntity);
    }
}
