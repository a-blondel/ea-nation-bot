package com.ea.utils;

import com.ea.enums.Game;
import com.ea.enums.GameGenre;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class for game VERS and genre operations.
 * This class provides helper methods to work with game identifiers and categories.
 */
@Slf4j
public class GameVersUtils {

    /**
     * Get all VERS codes for a specific game genre.
     *
     * @param genre the game genre
     * @return list of VERS codes for games in the genre
     */
    public static List<String> getVersForGenre(GameGenre genre) {
        return Arrays.stream(Game.getGamesByGenre(genre))
                .map(Game::getVers)
                .toList();
    }

    /**
     * Get all game names for a specific game genre.
     *
     * @param genre the game genre
     * @return list of game names for games in the genre
     */
    public static List<String> getNamesForGenre(GameGenre genre) {
        return Arrays.stream(Game.getGamesByGenre(genre))
                .map(Game::getName)
                .toList();
    }

    /**
     * Get the game genre for a given VERS code.
     *
     * @param vers the VERS code
     * @return the corresponding game genre, or null if not found
     */
    public static GameGenre getGenreForVers(String vers) {
        Game game = Game.findByVers(vers);
        return game != null ? game.getGameGenre() : null;
    }
}
