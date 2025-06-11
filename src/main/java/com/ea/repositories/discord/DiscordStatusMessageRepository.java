package com.ea.repositories.discord;

import com.ea.entities.discord.StatusMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiscordStatusMessageRepository extends JpaRepository<StatusMessageEntity, Long> {
    Optional<StatusMessageEntity> findByGuildId(String guildId);
    void deleteByGuildId(String guildId);
}
