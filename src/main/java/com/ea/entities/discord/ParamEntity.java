package com.ea.entities.discord;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "param", schema = "discord")
public class ParamEntity {

    @Id
    private String paramKey;

    private LocalDateTime paramValue;
}
