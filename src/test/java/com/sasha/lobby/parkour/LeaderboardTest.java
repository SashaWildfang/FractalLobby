/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.parkour;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaderboardTest {

    /** Rows are read until ajParkour's "no data" text shows up instead of a score. */
    @Test
    void readsUntilTheFirstEmptyPosition() {
        Map<String, String> values = Map.of(
                "name1", "SashaWildfang", "score1", "42",
                "name2", "Flexxycodes", "score2", "17",
                "name3", "--", "score3", "---");
        List<Leaderboard.Entry> rows = Leaderboard.read(100,
                (position, kind) -> values.getOrDefault(kind + position, "%ajpk_stats_top_" + kind + "_" + position + "%"));

        assertEquals(List.of(new Leaderboard.Entry(1, "SashaWildfang", "42"), new Leaderboard.Entry(2, "Flexxycodes", "17")), rows);
    }

    /** Without ajParkour, PlaceholderAPI leaves the placeholders as they are: no rows. */
    @Test
    void unresolvedPlaceholdersGiveNoRows() {
        assertEquals(List.of(), Leaderboard.read(10, (position, kind) -> "%ajpk_stats_top_" + kind + "_" + position + "%"));
    }
}
