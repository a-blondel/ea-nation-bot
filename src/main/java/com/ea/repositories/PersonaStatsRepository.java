package com.ea.repositories;

import com.ea.entities.PersonaStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonaStatsRepository extends JpaRepository<PersonaStatsEntity, Long> {

}
