package com.ea.enums;

import lombok.Getter;

/**
 * Represents individual games supported by the bot.
 * Each game has a name, VERS code (used for API queries), and belongs to a genre.
 */
@Getter
public enum Game {
    // FOOTBALL genre
    UEFA_CHAMPIONS_LEAGUE_2006_2007("UEFA Champions League 2006-2007", "PSP/UEFA07", GameGenre.FOOTBALL),
    FIFA_07("FIFA 07", "PSP/FIFA07", GameGenre.FOOTBALL),
    FIFA_08("FIFA 08", "PSP/FIFA08", GameGenre.FOOTBALL),
    FIFA_09("FIFA 09", "PSP/FIFA09", GameGenre.FOOTBALL),
    FIFA_10("FIFA 10", "PSP/FIFA10", GameGenre.FOOTBALL),
    FIFA_WORLD_CUP_GERMANY_2006("FIFA World Cup Germany 2006", "FLM", GameGenre.FOOTBALL),
    FIFA_WORLD_CUP_SOUTH_AFRICA_2010("FIFA World Cup South Africa 2010", "PSP/WORLDCUP10", GameGenre.FOOTBALL),

    // FIGHTING genre
    FIGHT_NIGHT_ROUND_3("Fight Night Round 3", "PSP/KOK06", GameGenre.FIGHTING),

    // AMERICAN_FOOTBALL genre
    MADDEN_NFL_07("Madden NFL 07", "PSP/MADDEN07", GameGenre.AMERICAN_FOOTBALL),
    MADDEN_NFL_08("Madden NFL 08", "PSP/MADDEN-2008", GameGenre.AMERICAN_FOOTBALL),
    MADDEN_NFL_09("Madden NFL 09", "PSP/MADDEN-2009", GameGenre.AMERICAN_FOOTBALL),
    MADDEN_NFL_10("Madden NFL 10", "PSP/MADDEN-2010", GameGenre.AMERICAN_FOOTBALL),
    NCAA_FOOTBALL_07("NCAA Football 07", "PSP/NCAA07", GameGenre.AMERICAN_FOOTBALL),

    // BASKETBALL genre
    NBA_LIVE_07("NBA Live 07", "PSP/NBA07", GameGenre.BASKETBALL),
    NBA_LIVE_08("NBA Live 08", "PSP/NBA08", GameGenre.BASKETBALL),

    // RACING genre
    NEED_FOR_SPEED_MOST_WANTED_5_1_0("Need for Speed: Most Wanted 5-1-0", "PSP/NFS06", GameGenre.RACING),
    NEED_FOR_SPEED_CARBON_OWN_THE_CITY("Need for Speed: Carbon - Own the City", "PSP/NFS07", GameGenre.RACING),
    NEED_FOR_SPEED_PROSTREET("Need for Speed: ProStreet", "PSP/NFS08", GameGenre.RACING),
    NEED_FOR_SPEED_UNDERCOVER("Need for Speed: Undercover", "PSP/NFS09", GameGenre.RACING),

    // HOCKEY genre
    NHL_07("NHL 07", "PSP/NHL07", GameGenre.HOCKEY),

    // FPS genre
    MEDAL_OF_HONOR_HEROES("Medal of Honor: Heroes", "PSP/MOH07", "PSP/MOHGPS071", GameGenre.FPS),
//    MEDAL_OF_HONOR_HEROES_2("Medal of Honor: Heroes 2", "PSP/MOH08", GameGenre.FPS),
//    MEDAL_OF_HONOR_HEROES_2_WII("Medal of Honor: Heroes 2 (Wii)", "WII/MOH08", GameGenre.FPS),

    // GOLF genre
    TIGER_WOODS_PGA_TOUR_07("Tiger Woods PGA Tour 07", "PSP/TW07", GameGenre.GOLF),
    TIGER_WOODS_PGA_TOUR_08("Tiger Woods PGA Tour 08", "PSP/TW08", GameGenre.GOLF),
    TIGER_WOODS_PGA_TOUR_10("Tiger Woods PGA Tour 10", "PSP/TEST10", GameGenre.GOLF);

    private final String name;
    private final String vers; // Client VERS code
    private final String serverVers; // Server VERS code (null if same as client)
    private final GameGenre gameGenre;

    // Constructor for games where client and server VERS are the same
    Game(String name, String vers, GameGenre gameGenre) {
        this.name = name;
        this.vers = vers;
        this.serverVers = null;
        this.gameGenre = gameGenre;
    }

    // Constructor for games where client and server VERS are different
    Game(String name, String vers, String serverVers, GameGenre gameGenre) {
        this.name = name;
        this.vers = vers;
        this.serverVers = serverVers;
        this.gameGenre = gameGenre;
    }

    /**
     * Find a game by its client VERS code.
     *
     * @param vers the client VERS code to search for
     * @return the corresponding Game, or null if not found
     */
    public static Game findByVers(String vers) {
        for (Game game : values()) {
            if (game.vers.equals(vers)) {
                return game;
            }
        }
        return null;
    }

    /**
     * Find a game by its server VERS code.
     *
     * @param serverVers the server VERS code to search for
     * @return the corresponding Game, or null if not found
     */
    public static Game findByServerVers(String serverVers) {
        for (Game game : values()) {
            if (game.getEffectiveServerVers().equals(serverVers)) {
                return game;
            }
        }
        return null;
    }

    /**
     * Get all games belonging to a specific genre.
     *
     * @param genre the genre to filter by
     * @return array of games in the specified genre
     */
    public static Game[] getGamesByGenre(GameGenre genre) {
        return java.util.Arrays.stream(values())
                .filter(game -> game.gameGenre == genre)
                .toArray(Game[]::new);
    }

    /**
     * Get all client VERS codes for a specific genre.
     *
     * @param genre the genre to get VERS codes for
     * @return array of client VERS codes
     */
    public static String[] getClientVersCodesByGenre(GameGenre genre) {
        return java.util.Arrays.stream(values())
                .filter(game -> game.gameGenre == genre)
                .map(Game::getVers)
                .toArray(String[]::new);
    }

    /**
     * Get all server VERS codes for a specific genre.
     *
     * @param genre the genre to get server VERS codes for
     * @return array of server VERS codes
     */
    public static String[] getServerVersCodesByGenre(GameGenre genre) {
        return java.util.Arrays.stream(values())
                .filter(game -> game.gameGenre == genre)
                .map(Game::getEffectiveServerVers)
                .distinct()
                .toArray(String[]::new);
    }

    /**
     * Get the effective server VERS code.
     * Returns serverVers if available, otherwise falls back to vers.
     *
     * @return the VERS code used by the server
     */
    public String getEffectiveServerVers() {
        return serverVers != null ? serverVers : vers;
    }
}
