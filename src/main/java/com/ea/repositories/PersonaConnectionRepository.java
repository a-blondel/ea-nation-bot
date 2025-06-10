package com.ea.repositories;

import com.ea.entities.PersonaConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

}
