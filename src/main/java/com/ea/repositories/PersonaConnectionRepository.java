package com.ea.repositories;

import com.ea.entities.PersonaConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PersonaConnectionRepository extends JpaRepository<PersonaConnectionEntity, Long> {

    int countByIsHostIsFalseAndEndTimeIsNull();

    List<PersonaConnectionEntity> findByIsHostIsFalseAndStartTimeBetweenOrderByStartTime(LocalDateTime start, LocalDateTime end);

    List<PersonaConnectionEntity> findByIsHostIsFalseAndEndTimeBetweenOrderByEndTime(LocalDateTime start, LocalDateTime end);

}
