package com.ea.repositories.core;

import com.ea.entities.core.GameEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GameRepository extends JpaRepository<GameEntity, Long> {

    List<GameEntity> findByVersInAndEndTimeBetweenOrderByEndTimeAsc(List<String> vers, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Find currently active games for specific server VERS codes.
     * Active games are those that have not ended yet.
     *
     * @param serverVersCodesList list of server VERS codes to filter by
     * @return list of active games
     */
    @Query("SELECT g FROM GameEntity g WHERE g.vers IN :serverVersCodesList AND g.endTime IS NULL ORDER BY g.startTime")
    List<GameEntity> findActiveGamesByServerVers(@Param("serverVersCodesList") List<String> serverVersCodesList);

}
