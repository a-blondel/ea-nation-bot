package com.ea.repositories.discord;

import com.ea.entities.discord.ParamEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParamRepository extends JpaRepository<ParamEntity, String> {
}
