/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.network;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.data.NetworkDb;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network boosters, kept in the network database.
 * <ul>
 *   <li>A bought booster goes into the buyer's booster inventory ({@code network_booster_items}); the store gives it
 *       with {@code /booster give <player> <game> <minutes> <multiplier>}.</li>
 *   <li>The player activates it from My Profile → Boosters. It then runs for everyone on that game
 *       ({@code network_boosters}), after any boosters already running or waiting there.</li>
 *   <li>The game's servers (CTF) read the running booster from the same table; the realm selector shows it here.</li>
 * </ul>
 */
public final class NetworkBoosters implements Listener {

    /** A booster in a player's inventory, not used yet. */
    public record Item(long id, String game, double multiplier, int minutes) {}

    /** A booster running or waiting on a game. */
    public record Running(String game, double multiplier, long starts, long ends, String owner) {
        public long minutesLeft(long now) { return Math.max(1, (ends - Math.max(now, starts) + 59_999) / 60_000); }
    }

    private final Lobby plugin;
    private final NetworkDb db;
    /** Every booster running or waiting, per game, oldest first (read every 10 seconds). */
    private volatile Map<String, List<Running>> running = Map.of();
    /** How many unused boosters each online player has (for the profile button). */
    private final Map<UUID, Integer> owned = new ConcurrentHashMap<>();
    private BukkitTask task;

    public NetworkBoosters(Lobby plugin, NetworkDb db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refresh, 20L, 200L);
        for (Player player : Bukkit.getOnlinePlayers()) count(player);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void refresh() {
        try {
            running = db.now(c -> {
                Map<String, List<Running>> map = new HashMap<>();
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT game, multiplier, starts, ends, owner_name FROM network_boosters WHERE ends > ? ORDER BY starts")) {
                    s.setLong(1, System.currentTimeMillis());
                    try (ResultSet r = s.executeQuery()) {
                        while (r.next()) {
                            Running b = new Running(r.getString(1), r.getDouble(2), r.getLong(3), r.getLong(4), r.getString(5));
                            map.computeIfAbsent(b.game().toLowerCase(Locale.ROOT), g -> new ArrayList<>()).add(b);
                        }
                    }
                }
                return map;
            });
        } catch (SQLException e) {
            plugin.getLogger().fine("Couldn't read the boosters: " + e.getMessage());
        }
    }

    /** The booster running on this game now, or null. */
    public Running active(String game) {
        long now = System.currentTimeMillis();
        for (Running b : running.getOrDefault(game.toLowerCase(Locale.ROOT), List.of())) {
            if (b.starts() <= now && now < b.ends()) return b;
        }
        return null;
    }

    /** Boosters waiting their turn on this game. */
    public int waiting(String game) {
        long now = System.currentTimeMillis();
        int n = 0;
        for (Running b : running.getOrDefault(game.toLowerCase(Locale.ROOT), List.of())) if (b.starts() > now) n++;
        return n;
    }

    /** Unused boosters this player has (0 until read after they join). */
    public int owned(Player player) {
        return owned.getOrDefault(player.getUniqueId(), 0);
    }

    private static final String MINE = "used_at IS NULL AND (uuid = ? OR (uuid = '' AND LOWER(name) = LOWER(?)))";

    /** The player's unused boosters, oldest first. Also claims ones given by name before their UUID was known. */
    public CompletableFuture<List<Item>> items(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        return db.run(c -> {
            try (PreparedStatement claim = c.prepareStatement(
                    "UPDATE network_booster_items SET uuid = ? WHERE uuid = '' AND LOWER(name) = LOWER(?)")) {
                claim.setString(1, uuid.toString());
                claim.setString(2, name);
                claim.executeUpdate();
            }
            List<Item> list = new ArrayList<>();
            try (PreparedStatement s = c.prepareStatement("SELECT id, game, multiplier, minutes FROM network_booster_items WHERE "
                    + MINE + " ORDER BY created, id")) {
                s.setString(1, uuid.toString());
                s.setString(2, name);
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) list.add(new Item(r.getLong(1), r.getString(2), r.getDouble(3), r.getInt(4)));
                }
            }
            owned.put(uuid, list.size());
            return list;
        });
    }

    private void count(Player player) {
        items(player).exceptionally(e -> List.of());
    }

    /**
     * /booster give: puts a booster in a player's inventory (online or not; a name the server has never seen is
     * matched when they next open their boosters).
     */
    public CompletableFuture<Void> give(String name, String game, int minutes, double multiplier, String source) {
        Player online = Bukkit.getPlayerExact(name);
        OfflinePlayer known = online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
        String uuid = known == null ? "" : known.getUniqueId().toString();
        String shownName = known != null && known.getName() != null ? known.getName() : name;
        return db.run(c -> {
            try (PreparedStatement s = c.prepareStatement("INSERT INTO network_booster_items (uuid, name, game, multiplier, minutes,"
                    + " source, created) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                s.setString(1, uuid);
                s.setString(2, shownName);
                s.setString(3, game.toLowerCase(Locale.ROOT));
                s.setDouble(4, multiplier);
                s.setInt(5, minutes);
                s.setString(6, source);
                s.setLong(7, System.currentTimeMillis());
                s.executeUpdate();
            }
            return null;
        }).thenRun(() -> {
            if (online != null) count(online);
        });
    }

    /**
     * Uses one of the player's boosters: it's taken out of their inventory and runs on its game after any running or
     * waiting there. Completes with when it starts, or null if it was already used (another click, another lobby).
     */
    public CompletableFuture<Long> activate(Player player, Item item) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        return db.run(c -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                try (PreparedStatement use = c.prepareStatement("UPDATE network_booster_items SET used_at = ? WHERE id = ? AND " + MINE)) {
                    use.setLong(1, now);
                    use.setLong(2, item.id());
                    use.setString(3, uuid.toString());
                    use.setString(4, name);
                    if (use.executeUpdate() != 1) {
                        c.rollback();
                        return null;
                    }
                }
                long start = now;
                // FOR UPDATE: two boosters activated at the same moment queue up instead of overlapping
                try (PreparedStatement last = c.prepareStatement("SELECT MAX(ends) FROM network_boosters WHERE game = ? FOR UPDATE")) {
                    last.setString(1, item.game());
                    try (ResultSet r = last.executeQuery()) {
                        if (r.next()) start = Math.max(start, r.getLong(1));
                    }
                }
                try (PreparedStatement add = c.prepareStatement("INSERT INTO network_boosters (game, multiplier, starts, ends, owner_uuid,"
                        + " owner_name, created) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    add.setString(1, item.game());
                    add.setDouble(2, item.multiplier());
                    add.setLong(3, start);
                    add.setLong(4, start + item.minutes() * 60_000L);
                    add.setString(5, uuid.toString());
                    add.setString(6, name);
                    add.setLong(7, now);
                    add.executeUpdate();
                }
                c.commit();
                return start;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        }).whenComplete((start, error) -> {
            count(player);
            refresh();
        });
    }

    /** "2.0", "2.5": how multipliers are shown. */
    public static String multiplier(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        count(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        owned.remove(event.getPlayer().getUniqueId());
    }
}
