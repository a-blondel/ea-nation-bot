package com.ea.repositories.core;

import com.ea.entities.core.GameConnectionEntity;
import com.ea.entities.core.PersonaConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GameConnectionRepository extends JpaRepository<GameConnectionEntity, Long> {


    // Count players in game (not hosts, not ended)
    @Query("SELECT COUNT(gc) FROM GameConnectionEntity gc WHERE gc.personaConnection.isHost = false AND gc.endTime IS NULL")
    int countPlayersInGame();

    // Find player joins (not hosts, not map rotation)
    @Query("SELECT gc FROM GameConnectionEntity gc WHERE gc.personaConnection.isHost = false AND gc.startTime BETWEEN :start AND :end AND (gc.endTime IS NULL OR gc.endTime <> gc.game.endTime) AND gc.personaConnection.vers IN :vers ORDER BY gc.startTime")
    List<GameConnectionEntity> findMohhPlayerJoins(LocalDateTime start, LocalDateTime end, List<String> vers);

    @Query("SELECT gc FROM GameConnectionEntity gc WHERE gc.personaConnection.isHost = false AND gc.startTime BETWEEN :start AND :end AND gc.endTime IS NULL AND gc.personaConnection.vers NOT IN :vers ORDER BY gc.startTime")
    List<GameConnectionEntity> findNotMohhPlayerJoins(LocalDateTime start, LocalDateTime end, List<String> vers);

    // Find player leaves (not hosts, not map rotation)
    @Query("SELECT gc FROM GameConnectionEntity gc WHERE gc.personaConnection.isHost = false AND gc.endTime BETWEEN :start AND :end AND (gc.game.endTime IS NULL OR gc.endTime <> gc.game.endTime) AND gc.personaConnection.vers IN :vers ORDER BY gc.endTime")
    List<GameConnectionEntity> findMohhPlayerLeaves(LocalDateTime start, LocalDateTime end, List<String> vers);

    @Query("SELECT gc FROM GameConnectionEntity gc WHERE gc.personaConnection.isHost = false AND gc.endTime BETWEEN :start AND :end AND gc.personaConnection.vers NOT IN :vers ORDER BY gc.endTime")
    List<GameConnectionEntity> findNotMohhPlayerLeaves(LocalDateTime start, LocalDateTime end, List<String> vers);

    // Find a personaConnection ending at a specific time (for map rotation detection)
    GameConnectionEntity findFirstByPersonaConnectionAndEndTimeOrderByEndTimeDesc(PersonaConnectionEntity personaConnection, LocalDateTime endTime);

}