package com.ea.enums;

import lombok.Getter;

/**
 * Represents different game categories supported by the bot.
 * Each genre groups related games together for subscription and service management.
 */
@Getter
public enum GameGenre {
    FOOTBALL("football"),
    FIGHTING("fighting"),
    AMERICAN_FOOTBALL("american_football"),
    BASKETBALL("basketball"),
    RACING("racing"),
    HOCKEY("hockey"),
    FPS("fps"),
    GOLF("golf");

    private final String value;

    GameGenre(String value) {
        this.value = value;
    }

    /**
     * Converts a string value to the corresponding GameGenre enum.
     *
     * @param value the string value to convert
     * @return the corresponding GameGenre
     * @throws IllegalArgumentException if the value doesn't match any genre
     */
    public static GameGenre fromValue(String value) {
        for (GameGenre genre : values()) {
            if (genre.value.equalsIgnoreCase(value)) {
                return genre;
            }
        }
        throw new IllegalArgumentException("Unknown game genre: " + value);
    }

}
