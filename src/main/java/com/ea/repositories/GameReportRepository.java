package com.ea.repositories;

import com.ea.entities.GameReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GameReportRepository extends JpaRepository<GameReportEntity, Long> {

    int countByIsHostIsFalseAndEndTimeIsNull();

    List<GameReportEntity> findByIsHostIsFalseAndStartTimeBetweenOrderByStartTime(LocalDateTime start, LocalDateTime end);

    List<GameReportEntity> findByIsHostIsFalseAndEndTimeBetweenOrderByEndTime(LocalDateTime start, LocalDateTime end);

}