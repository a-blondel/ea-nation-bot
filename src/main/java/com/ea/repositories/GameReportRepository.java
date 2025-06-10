package com.ea.repositories;

import com.ea.entities.GameReportEntity;
import com.ea.entities.PersonaConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GameReportRepository extends JpaRepository<GameReportEntity, Long> {


    // Count players in game (not hosts, not ended)
    @Query("SELECT COUNT(gr) FROM GameReportEntity gr WHERE gr.isHost = false AND gr.endTime IS NULL")
    int countPlayersInGame();

    // Find player joins (not hosts, not map rotation)
    @Query("SELECT gr FROM GameReportEntity gr WHERE gr.isHost = false AND gr.startTime BETWEEN :start AND :end AND (gr.endTime IS NULL OR gr.endTime <> gr.game.endTime) ORDER BY gr.startTime")
    List<GameReportEntity> findPlayerJoins(LocalDateTime start, LocalDateTime end);

    // Find player leaves (not hosts, not map rotation)
    @Query("SELECT gr FROM GameReportEntity gr WHERE gr.isHost = false AND gr.endTime BETWEEN :start AND :end AND (gr.game.endTime IS NULL OR gr.endTime <> gr.game.endTime) ORDER BY gr.endTime")
    List<GameReportEntity> findPlayerLeaves(LocalDateTime start, LocalDateTime end);

    // Find a personaConnection ending at a specific time (for map rotation detection)
    GameReportEntity findFirstByPersonaConnectionAndEndTimeOrderByEndTimeDesc(PersonaConnectionEntity personaConnection, LocalDateTime endTime);

}