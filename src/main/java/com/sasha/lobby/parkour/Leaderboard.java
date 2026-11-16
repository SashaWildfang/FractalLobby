/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.parkour;

import com.sasha.lobby.Lobby;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** The parkour leaderboard, from the Lobby's own scores (parkour-scores.yml). */
public final class Leaderboard {

    /** One row of the leaderboard. */
    public record Entry(int position, String name, String score, String time) {
    }

    private final Lobby plugin;
    private final ScoreStore store;

    Leaderboard(Lobby plugin, ScoreStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public boolean available() {
        return true;
    }

    /** The best scores, as far down as parkour.rank-search. */
    public List<Entry> entries(Player context) {
        List<Entry> rows = new ArrayList<>();
        int position = 1;
        for (ScoreStore.Record r : store.top(plugin.lobbyConfig().parkour().rankSearch())) {
            rows.add(new Entry(position++, r.name(), String.valueOf(r.best()), ScoreStore.duration(r.timeSeconds())));
        }
        return rows;
    }

    /** The player's position (1 = best), or 0 when they have no score yet. */
    public int rank(Player player) {
        return store.rank(player.getUniqueId());
    }

    /** The player's own best score. */
    public String best(Player player) {
        return String.valueOf(store.best(player.getUniqueId()));
    }

    /** How long the run at this position took. */
    public String time(Player context, int position) {
        List<ScoreStore.Record> top = store.top(position);
        return top.size() < position ? "" : ScoreStore.duration(top.get(position - 1).timeSeconds());
    }
}
