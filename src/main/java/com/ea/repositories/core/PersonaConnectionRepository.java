package com.ea.repositories.core;

import com.ea.entities.core.PersonaConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PersonaConnectionRepository extends JpaRepository<PersonaConnectionEntity, Long> {

    // Count online players (not hosts, not ended)
    @Query("SELECT COUNT(pc) FROM PersonaConnectionEntity pc WHERE pc.isHost = false AND pc.endTime IS NULL")
    int countPlayersOnline();

    // Find persona logins (not hosts)
    @Query("SELECT pc FROM PersonaConnectionEntity pc WHERE pc.isHost = false AND pc.startTime BETWEEN :start AND :end ORDER BY pc.startTime")
    List<PersonaConnectionEntity> findPersonaLogins(LocalDateTime start, LocalDateTime end);

    // Find persona logouts (not hosts)
    @Query("SELECT pc FROM PersonaConnectionEntity pc WHERE pc.isHost = false AND pc.endTime BETWEEN :start AND :end ORDER BY pc.endTime")
    List<PersonaConnectionEntity> findPersonaLogouts(LocalDateTime start, LocalDateTime end);

    List<PersonaConnectionEntity> findByStartTimeGreaterThan(LocalDateTime start);

    /**
     * Find persona connections by start time and VERS codes for game genre filtering.
     *
     * @param start         the start time threshold
     * @param versCodesList list of VERS codes to filter by
     * @return list of persona connections matching the criteria
     */
    @Query("SELECT pc FROM PersonaConnectionEntity pc WHERE pc.startTime > :start AND pc.vers IN :versCodesList")
    List<PersonaConnectionEntity> findByStartTimeGreaterThanAndVersIn(@Param("start") LocalDateTime start, @Param("versCodesList") List<String> versCodesList);

    /**
     * Find players currently connected in lobby (not in game) for specific VERS codes.
     * These are players connected but not participating in any active game.
     *
     * @param versCodesList list of client VERS codes to filter by
     * @return list of persona connections for players in lobby
     */
    @Query("SELECT pc FROM PersonaConnectionEntity pc WHERE pc.isHost = false AND pc.endTime IS NULL " +
            "AND pc.vers IN :versCodesList " +
            "AND NOT EXISTS (SELECT gc FROM GameConnectionEntity gc WHERE gc.personaConnection = pc AND gc.endTime IS NULL)")
    List<PersonaConnectionEntity> findPlayersInLobbyByVers(@Param("versCodesList") List<String> versCodesList);
}
