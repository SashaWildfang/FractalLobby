/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.parkour;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.hook.PlaceholderHook;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * ajParkour's best scores, read through its PlaceholderAPI placeholders (%ajpk_stats_top_name_N% and
 * %ajpk_stats_top_score_N%). ajParkour has no placeholder for a player's position, so it's found by
 * looking for their name in the list. Refreshed every few seconds, not on every hologram update.
 */
public final class Leaderboard {

    /** One row of the leaderboard. */
    public record Entry(int position, String name, String score) {
    }

    private static final long REFRESH_MILLIS = 5000;

    private final Lobby plugin;
    private List<Entry> entries = List.of();
    private long refreshedAt;

    public Leaderboard(Lobby plugin) {
        this.plugin = plugin;
    }

    /** Whether the data is reachable: PlaceholderAPI and ajParkour are both installed. */
    public boolean available() {
        if (!plugin.placeholderApi()) return false;
        for (org.bukkit.plugin.Plugin other : plugin.getServer().getPluginManager().getPlugins()) {
            if (other.getName().equalsIgnoreCase("ajParkour") && other.isEnabled()) return true;
        }
        return false;
    }

    /** The leaderboard, refreshed when it's a few seconds old. Any online player serves as the placeholder context. */
    public List<Entry> entries(Player context) {
        long now = System.currentTimeMillis();
        if (available() && now - refreshedAt > REFRESH_MILLIS) {
            refreshedAt = now;
            entries = read(plugin.lobbyConfig().parkour().rankSearch(),
                    (index, kind) -> PlaceholderHook.apply(context, "%ajpk_stats_top_" + kind + "_" + index + "%"));
        }
        return entries;
    }

    /** The player's position (1 = best), or 0 when they're not on the board (as far as it's searched). */
    public int rank(Player player) {
        for (Entry entry : entries(player)) {
            if (entry.name().equalsIgnoreCase(player.getName())) return entry.position();
        }
        return 0;
    }

    /** The player's own best score. */
    public String best(Player player) {
        return available() ? PlaceholderHook.apply(player, "%ajpk_stats_highscore%") : "0";
    }

    /** A top-N time, formatted by ajParkour (only fetched for the menu). */
    public String time(Player context, int position) {
        return available() ? PlaceholderHook.apply(context, "%ajpk_stats_top_time_" + position + "%") : "";
    }

    /**
     * Reads rows until the first empty position: ajParkour then gives its "no data" text instead of a
     * number as the score.
     *
     * @param lookup (position, "name" or "score") → the placeholder's value
     */
    static List<Entry> read(int max, BiFunction<Integer, String, String> lookup) {
        List<Entry> rows = new ArrayList<>();
        for (int position = 1; position <= max; position++) {
            String score = lookup.apply(position, "score").trim();
            String name = lookup.apply(position, "name").trim();
            if (!score.matches("\\d+") || name.isEmpty() || name.equals("--") || name.contains("%")) break;
            rows.add(new Entry(position, name, score));
        }
        return List.copyOf(rows);
    }
}
