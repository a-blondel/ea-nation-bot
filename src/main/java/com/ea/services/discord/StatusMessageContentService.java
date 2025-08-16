package com.ea.services.discord;

import com.ea.entities.core.GameConnectionEntity;
import com.ea.entities.core.GameEntity;
import com.ea.entities.core.PersonaConnectionEntity;
import com.ea.enums.Game;
import com.ea.enums.GameGenre;
import com.ea.repositories.core.GameRepository;
import com.ea.repositories.core.PersonaConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for generating status message content for different game genres.
 * This service creates formatted Markdown content showing lobby players, active games, and game participants.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatusMessageContentService {

    private final PersonaConnectionRepository personaConnectionRepository;
    private final GameRepository gameRepository;

    /**
     * Generate status message content for a specific game genre.
     *
     * @param gameGenre the game genre to generate status for
     * @return formatted Markdown content
     */
    public String generateStatusContent(GameGenre gameGenre) {
        log.debug("Generating status content for genre: {}", gameGenre);

        // Get all games for this genre
        Game[] games = Game.getGamesByGenre(gameGenre);
        if (games.length == 0) {
            return "No games found for genre: " + gameGenre.name();
        }

        // Get client and server VERS codes
        List<String> clientVersCodes = Arrays.asList(Game.getClientVersCodesByGenre(gameGenre));
        List<String> serverVersCodes = Arrays.asList(Game.getServerVersCodesByGenre(gameGenre));

        // Fetch data
        List<PersonaConnectionEntity> lobbyPlayers = personaConnectionRepository.findPlayersInLobbyByVers(clientVersCodes);
        List<GameEntity> activeGames = gameRepository.findActiveGamesByServerVers(serverVersCodes);

        // Group data by game
        Map<Game, List<PersonaConnectionEntity>> playersByGame = groupPlayersByGame(lobbyPlayers);
        Map<Game, List<GameEntity>> gamesByGame = groupActiveGamesByGame(activeGames, games);

        // Build the status message
        StringBuilder content = new StringBuilder();
        //content.append("# ").append(formatGenreName(gameGenre)).append(" Games\n\n");

        // Display status for ALL games in enum order (even without activity)
        for (Game game : games) {
            List<PersonaConnectionEntity> gameLobbyPlayers = playersByGame.getOrDefault(game, List.of());
            List<GameEntity> gameActiveGames = gamesByGame.getOrDefault(game, List.of());

            addGameSection(content, game, gameLobbyPlayers, gameActiveGames);
        }

        return content.toString();
    }

    /**
     * Group lobby players by their corresponding Game enum.
     */
    private Map<Game, List<PersonaConnectionEntity>> groupPlayersByGame(List<PersonaConnectionEntity> lobbyPlayers) {
        return lobbyPlayers.stream()
                .collect(Collectors.groupingBy(player -> {
                    Game game = Game.findByVers(player.getVers());
                    return game != null ? game : Game.MEDAL_OF_HONOR_HEROES; // fallback
                }));
    }

    /**
     * Group active games by their corresponding Game enum.
     */
    private Map<Game, List<GameEntity>> groupActiveGamesByGame(List<GameEntity> activeGames, Game[] genreGames) {
        return activeGames.stream()
                .collect(Collectors.groupingBy(gameEntity -> {
                    Game game = Game.findByServerVers(gameEntity.getVers());
                    return game != null ? game : genreGames[0]; // fallback to first game of genre
                }));
    }

    /**
     * Add a complete section for a specific game.
     */
    private void addGameSection(StringBuilder content, Game game,
                                List<PersonaConnectionEntity> lobbyPlayers,
                                List<GameEntity> activeGames) {
        content.append("## ").append(game.getName()).append("\n\n");

        // Lobby players subsection
        content.append("**Players in Lobby (").append(lobbyPlayers.size()).append(")**\n");
        if (!lobbyPlayers.isEmpty()) {
            for (PersonaConnectionEntity player : lobbyPlayers) {
                content.append("🔸 ").append(player.getPersona().getPers()).append("\n");
            }
        } else {
            content.append("*No players in lobby*\n");
        }
        content.append("\n");

        // Active games subsection
        content.append("**Active Games (").append(activeGames.size()).append(")**\n");
        if (!activeGames.isEmpty()) {
            for (GameEntity gameEntity : activeGames) {
                addGameDetails(content, gameEntity);
                if (activeGames.indexOf(gameEntity) < activeGames.size() - 1)
                    content.append("\n");
            }
        } else {
            content.append("*No active games*\n");
        }
        content.append("\n");
    }

    /**
     * Add details for a specific game instance.
     */
    private void addGameDetails(StringBuilder content, GameEntity game) {
        String status = determineGameStatus(game);
        int playerCount = game.getGameConnections() != null ?
                (int) game.getGameConnections().stream().filter(gc -> gc.getEndTime() == null).count() : 0;

        // Game header with status and player count - using dash and code quotes
        content.append("🔹 `").append(game.getName()).append("` ");
        content.append(status).append(" ");
        content.append("(").append(playerCount);
        if (game.getMaxsize() > 0) {
            content.append("/").append(game.getMaxsize());
        }
        content.append(" players)\n");

        // List players in game with double -- indentation
        if (game.getGameConnections() != null && !game.getGameConnections().isEmpty()) {
            List<GameConnectionEntity> activePlayers = game.getGameConnections().stream()
                    .filter(gc -> gc.getEndTime() == null)
                    .sorted((a, b) -> Boolean.compare(b.isHost(), a.isHost())) // Hosts first
                    .toList();

            if (!activePlayers.isEmpty()) {
                for (GameConnectionEntity connection : activePlayers) {
                    String playerName = connection.getPersonaConnection().getPersona().getPers();
                    content.append("  🔸 ");
                    if (connection.isHost()) {
                        content.append(playerName).append(" 👑");
                    } else {
                        content.append(playerName);
                    }
                    content.append("\n");
                }
            }
        }
    }

    /**
     * Determine the status of a game based on its properties.
     */
    private String determineGameStatus(GameEntity game) {
        if (game.isStarted()) {
            return "🟢 Started";
        } else {
            return "🟡 Waiting for players";
        }
    }

    /**
     * Format the genre name for display.
     */
    private String formatGenreName(GameGenre genre) {
        return switch (genre) {
            case FOOTBALL -> "Football";
            case AMERICAN_FOOTBALL -> "American Football";
            case BASKETBALL -> "Basketball";
            case RACING -> "Racing";
            case HOCKEY -> "Hockey";
            case FPS -> "FPS";
            case GOLF -> "Golf";
            case FIGHTING -> "Fighting";
        };
    }
}
