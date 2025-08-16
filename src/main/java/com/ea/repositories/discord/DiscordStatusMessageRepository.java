package com.ea.repositories.discord;

import com.ea.entities.discord.StatusMessageEntity;
import com.ea.enums.GameGenre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiscordStatusMessageRepository extends JpaRepository<StatusMessageEntity, Long> {
    Optional<StatusMessageEntity> findByGuildIdAndGameGenre(String guildId, GameGenre gameGenre);

    void deleteByGuildIdAndGameGenre(String guildId, GameGenre gameGenre);
}
