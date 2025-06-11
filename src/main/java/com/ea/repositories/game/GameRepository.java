package com.ea.repositories.game;

import com.ea.entities.game.GameEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GameRepository extends JpaRepository<GameEntity, Long> {

    List<GameEntity> findByVersInAndEndTimeBetweenOrderByEndTimeAsc(List<String> vers, LocalDateTime startTime, LocalDateTime endTime);

}
