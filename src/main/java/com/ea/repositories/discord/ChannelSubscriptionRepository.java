package com.ea.repositories.discord;

import com.ea.entities.discord.ChannelSubscriptionEntity;
import com.ea.enums.SubscriptionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelSubscriptionRepository extends JpaRepository<ChannelSubscriptionEntity, Long> {
    Optional<ChannelSubscriptionEntity> findByGuildIdAndSubscriptionType(String guildId, SubscriptionType subscriptionType);
    void deleteByGuildIdAndSubscriptionType(String guildId, SubscriptionType subscriptionType);
    List<ChannelSubscriptionEntity> findAllBySubscriptionType(SubscriptionType subscriptionType);
}
