package com.ea.repositories.stats;

import com.ea.entities.stats.NfsGameReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NfsGameReportRepository extends JpaRepository<NfsGameReportEntity, Long> {

    /**
     * Find the best lap time (world record) for a specific track, direction, and game version.
     *
     * @param vers  Game version (PSP/NFS06, PSP/NFS07, etc.)
     * @param venue Track/venue ID
     * @param dir   Direction (0=forward, 1=reverse)
     * @return The minimum lap time in milliseconds, or null if no valid laps exist
     */
    @Query("SELECT MIN(r.lap) FROM NfsGameReportEntity r " +
            "WHERE r.gameConnection.game.vers = :vers " +
            "AND r.venue = :venue " +
            "AND r.dir = :dir " +
            "AND r.rnk = 1 " +
            "AND r.lap IS NOT NULL AND r.lap > 0")
    Integer findBestLapForTrack(@Param("vers") String vers,
                                @Param("venue") Integer venue,
                                @Param("dir") Integer dir);

    /**
     * Find the best race time (world record) for Most Wanted tracks.
     * MW uses racetime instead of lap for best times.
     *
     * @param vers  Game version (PSP/NFS06)
     * @param venue Track/venue ID
     * @param dir   Direction (0=forward, 1=reverse)
     * @return The minimum race time in milliseconds, or null if no valid times exist
     */
    @Query("SELECT MIN(r.racetime) FROM NfsGameReportEntity r " +
            "WHERE r.gameConnection.game.vers = :vers " +
            "AND r.venue = :venue " +
            "AND r.dir = :dir " +
            "AND r.rnk = 1 " +
            "AND r.racetime IS NOT NULL AND r.racetime > 0")
    Integer findBestRacetimeForTrack(@Param("vers") String vers,
                                     @Param("venue") Integer venue,
                                     @Param("dir") Integer dir);
}
