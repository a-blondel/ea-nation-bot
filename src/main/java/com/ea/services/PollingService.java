package com.ea.services;

import com.ea.entities.GameEntity;
import com.ea.entities.discord.ParamEntity;
import com.ea.enums.Params;
import com.ea.repositories.GameRepository;
import com.ea.repositories.discord.ParamRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class PollingService {

    @Value("${dns.name}")
    private String dnsName;

    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSSSSS";
    private final List<String> vers = List.of("PSP/MOHGPS071", "PSP/MOH07");

    private final ParamRepository paramRepository;
    private final GameRepository gameRepository;
    private final ScoreboardService scoreboardService;

    @Scheduled(fixedRate = 20000)
    public void processDataSinceLastFetchTime() throws UnknownHostException {
        ParamEntity lastFetchTimeEntity = paramRepository.findById(Params.LAST_FETCH_TIME.name()).orElseGet(() -> {
            ParamEntity paramEntity = new ParamEntity();
            paramEntity.setParamKey(Params.LAST_FETCH_TIME.name());
            paramEntity.setParamValue(LocalDateTime.now().minusMinutes(5).toString());
            return paramEntity;
        });

        log.info("Last fetch time: {}", lastFetchTimeEntity.getParamValue());

        LocalDateTime lastFetchTime = LocalDateTime.parse(lastFetchTimeEntity.getParamValue(), DateTimeFormatter.ofPattern(DATETIME_FORMAT));
        LocalDateTime currentFetchTime = LocalDateTime.now();

        processScoreboard(lastFetchTime, currentFetchTime);

        lastFetchTimeEntity.setParamValue(currentFetchTime.format(DateTimeFormatter.ofPattern(DATETIME_FORMAT)));
        paramRepository.save(lastFetchTimeEntity);
    }

    private void processScoreboard(LocalDateTime lastFetchTime, LocalDateTime currentFetchTime) {
        List<GameEntity> games = gameRepository.findByVersInAndEndTimeBetween(vers, lastFetchTime, currentFetchTime);

        for (GameEntity game : games) {
            scoreboardService.generateScoreboard(game);
        }
    }

    //@PostConstruct // Enable this annotation when debugging
    @Scheduled(cron = "0 0 0,12 * * ?")
    public void processIpChange() throws UnknownHostException {
        ParamEntity lastKnownIpEntity = paramRepository.findById(Params.LAST_KNOWN_IP.name()).orElseGet(() -> {
            ParamEntity paramEntity = new ParamEntity();
            paramEntity.setParamKey(Params.LAST_KNOWN_IP.name());
            paramEntity.setParamValue("127.0.0.1");
            return paramEntity;
        });

        String lastKnownIp = lastKnownIpEntity.getParamValue();
        String currentIp = InetAddress.getByName(dnsName).getHostAddress();
        if(!lastKnownIp.equals(currentIp)) {
            log.info("NEW DNS ADDRESS: {}", currentIp);
            lastKnownIpEntity.setParamValue(currentIp);
            paramRepository.save(lastKnownIpEntity);
        }
    }

}
