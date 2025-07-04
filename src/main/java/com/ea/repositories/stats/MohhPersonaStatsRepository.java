package com.ea.repositories.stats;

import com.ea.entities.stats.MohhPersonaStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MohhPersonaStatsRepository extends JpaRepository<MohhPersonaStatsEntity, Long> {

}
