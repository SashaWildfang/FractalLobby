/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.parkour;

import com.sasha.lobby.Lobby;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everyone's parkour bests, in parkour-scores.yml: per player their name, best score, how long that run took and
 * how many runs they've done. The first time it's made, ajParkour's scores are brought over if they're there.
 */
public final class ScoreStore {

    /** One player's record. timeSeconds: how long their best run took. */
    public record Record(UUID id, String name, int best, int timeSeconds, int games) {
    }

    private final Lobby plugin;
    private final File file;
    private final Map<UUID, Record> records = new HashMap<>();
    private List<Record> sorted = List.of();

    public ScoreStore(Lobby plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "parkour-scores.yml");
        boolean fresh = !file.exists();
        load();
        if (fresh) importAjParkour();
    }

    private void load() {
        records.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    ConfigurationSection p = players.getConfigurationSection(key);
                    if (p == null) continue;
                    records.put(id, new Record(id, p.getString("name", "?"), p.getInt("best"), p.getInt("time"), p.getInt("games")));
                } catch (IllegalArgumentException ignored) {
                    // not a player id
                }
            }
        }
        resort();
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("Lobby parkour scores. best = most jumps in one run, time = how long that run took (seconds)."));
        for (Record r : records.values()) {
            String path = "players." + r.id();
            yaml.set(path + ".name", r.name());
            yaml.set(path + ".best", r.best());
            yaml.set(path + ".time", r.timeSeconds());
            yaml.set(path + ".games", r.games());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save parkour-scores.yml: " + e.getMessage());
        }
    }

    private void resort() {
        List<Record> list = new ArrayList<>();
        for (Record r : records.values()) if (r.best() > 0) list.add(r);
        list.sort(Comparator.comparingInt(Record::best).reversed().thenComparingInt(Record::timeSeconds)
                .thenComparing(Record::name, String.CASE_INSENSITIVE_ORDER));
        sorted = List.copyOf(list);
    }

    /** The best scores, best first. */
    public List<Record> top(int limit) {
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    public int best(UUID id) {
        Record r = records.get(id);
        return r == null ? 0 : r.best();
    }

    /** Position on the leaderboard (1 = best), or 0 without a score. */
    public int rank(UUID id) {
        for (int i = 0; i < sorted.size(); i++) if (sorted.get(i).id().equals(id)) return i + 1;
        return 0;
    }

    /** The server record (0 when nobody has played). */
    public int record() {
        return sorted.isEmpty() ? 0 : sorted.getFirst().best();
    }

    /** Saves a finished run; returns true if it's the player's new best. */
    public boolean finish(UUID id, String name, int score, int timeSeconds) {
        Record old = records.get(id);
        boolean better = old == null ? score > 0 : score > old.best() || (score == old.best() && score > 0 && timeSeconds < old.timeSeconds());
        int games = (old == null ? 0 : old.games()) + 1;
        records.put(id, better ? new Record(id, name, score, timeSeconds, games)
                : new Record(id, name, old == null ? 0 : old.best(), old == null ? 0 : old.timeSeconds(), games));
        resort();
        save();
        return better && (old == null || score > old.best());
    }

    // ---------------------------------------------------------------- ajParkour

    /**
     * Brings over ajParkour's bests (plugins/ajParkour/scores.db, its default SQLite storage): each player's best
     * "overall" score and its time, and how many games they played.
     */
    private void importAjParkour() {
        File db = new File(plugin.getDataFolder().getParentFile(), "ajParkour/scores.db");
        if (!db.isFile()) return;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("Can't read ajParkour's scores (no SQLite driver on this server).");
            return;
        }
        int imported = 0;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
             Statement statement = connection.createStatement()) {
            String scores = null, players = null;
            try (ResultSet tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                while (tables.next()) {
                    String name = tables.getString(1);
                    if (name.endsWith("scores")) scores = name;
                    else if (name.endsWith("players")) players = name;
                }
            }
            if (scores == null) return;
            Map<UUID, String> names = new HashMap<>();
            Map<UUID, Integer> games = new HashMap<>();
            if (players != null) {
                try (ResultSet rows = statement.executeQuery("SELECT id, name, gamesplayed FROM " + players)) {
                    while (rows.next()) {
                        UUID id = uuid(rows.getString(1));
                        if (id == null) continue;
                        names.put(id, rows.getString(2));
                        games.put(id, rows.getInt(3));
                    }
                }
            }
            try (ResultSet rows = statement.executeQuery("SELECT player, MAX(score), MIN(time) FROM " + scores
                    + " WHERE area = 'overall' GROUP BY player")) {
                while (rows.next()) {
                    UUID id = uuid(rows.getString(1));
                    if (id == null) continue;
                    String name = names.getOrDefault(id, plugin.getServer().getOfflinePlayer(id).getName());
                    records.put(id, new Record(id, name == null ? "?" : name, rows.getInt(2), Math.max(0, rows.getInt(3)),
                            games.getOrDefault(id, 1)));
                    imported++;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Couldn't read ajParkour's scores.db: " + e.getMessage());
            return;
        }
        resort();
        save();
        if (imported > 0) plugin.getLogger().info("Brought over " + imported + " parkour scores from ajParkour.");
    }

    private static UUID uuid(String text) {
        try {
            return text == null ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** "1m 56s", "42s" */
    public static String duration(int seconds) {
        int m = seconds / 60, s = seconds % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }
}
