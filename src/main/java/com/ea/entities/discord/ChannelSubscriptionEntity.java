package com.ea.entities.discord;

import com.ea.enums.SubscriptionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "CHANNEL_SUBSCRIPTION", schema = "discord")
public class ChannelSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(name = "channel_id", nullable = false, length = 32)
    private String channelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_type", nullable = false, length = 32)
    private SubscriptionType subscriptionType;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
