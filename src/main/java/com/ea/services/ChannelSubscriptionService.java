package com.ea.services;

import com.ea.entities.discord.ChannelSubscriptionEntity;
import com.ea.enums.SubscriptionType;
import com.ea.repositories.discord.ChannelSubscriptionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
public class ChannelSubscriptionService {
    private final ChannelSubscriptionRepository repository;
    private final Map<SubscriptionType, List<ChannelSubscriptionEntity>> subscriptionCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadCache() {
        for (SubscriptionType type : SubscriptionType.values()) {
            subscriptionCache.put(type, repository.findAllBySubscriptionType(type));
        }
    }

    @Transactional
    public ChannelSubscriptionEntity subscribe(String guildId, String channelId, SubscriptionType subscriptionType) {
        Optional<ChannelSubscriptionEntity> existing = repository.findByGuildIdAndSubscriptionType(guildId, subscriptionType);
        ChannelSubscriptionEntity entity = existing.orElseGet(ChannelSubscriptionEntity::new);
        entity.setGuildId(guildId);
        entity.setChannelId(channelId);
        entity.setSubscriptionType(subscriptionType);
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        ChannelSubscriptionEntity saved = repository.save(entity);
        // Update cache
        subscriptionCache.compute(subscriptionType, (type, list) -> {
            List<ChannelSubscriptionEntity> filtered = list == null ? List.of() : list.stream()
                .filter(sub -> !(sub.getGuildId().equals(guildId)))
                .collect(Collectors.toList());
            // Only one per guild/type
            return new java.util.ArrayList<>() {{ addAll(filtered); add(saved); }};
        });
        return saved;
    }

    @Transactional
    public void unsubscribe(String guildId, SubscriptionType subscriptionType) {
        repository.deleteByGuildIdAndSubscriptionType(guildId, subscriptionType);
        // Update cache
        subscriptionCache.computeIfPresent(subscriptionType, (type, list) ->
            list.stream().filter(sub -> !sub.getGuildId().equals(guildId)).collect(Collectors.toList())
        );
    }

    public List<ChannelSubscriptionEntity> getAllByType(SubscriptionType subscriptionType) {
        return subscriptionCache.getOrDefault(subscriptionType, List.of());
    }
}
