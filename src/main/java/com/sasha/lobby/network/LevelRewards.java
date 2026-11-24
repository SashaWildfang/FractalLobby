/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.network;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.data.NetworkDb;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Network level rewards (config.yml level-rewards): each level from 2 up gives Mystery Dust and CTF coins, and every
 * few levels a Mystery Box of 1 to 3 stars (stars picked at random when claimed). Claimed in the Lobby (My Profile →
 * Levels); claims are kept in {@code network_level_rewards}. Dust and boxes are given through FractalCosmetics, CTF
 * coins go into {@code network_pending_coins} and the CTF servers pay them when the player next joins.
 *
 * <p>Also the coin multiplier a level gives on CTF (leveling.coin-multipliers), shown in the menu.
 */
public final class LevelRewards implements Listener {

    /** What one level gives. stars 0 = no box (or, once claimed, the stars it gave). */
    public record Reward(int level, long dust, int coins, boolean box) {}

    /** The levels this player has claimed, and the stars each box gave. */
    private final Map<UUID, Map<Integer, Integer>> claimed = new ConcurrentHashMap<>();
    private final Lobby plugin;
    private final NetworkDb db;

    public LevelRewards(Lobby plugin, NetworkDb db) {
        this.plugin = plugin;
        this.db = db;
    }

    private ConfigurationSection config() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("level-rewards");
        if (c == null || c.getKeys(false).isEmpty()) c = defaults().getConfigurationSection("level-rewards"); // an older config.yml
        return c != null ? c : new org.bukkit.configuration.MemoryConfiguration();
    }

    private org.bukkit.configuration.file.YamlConfiguration defaults;

    /** The plugin's own config.yml, for sections an older server config.yml doesn't have yet. */
    private org.bukkit.configuration.file.YamlConfiguration defaults() {
        if (defaults == null) {
            var in = plugin.getResource("config.yml");
            defaults = in == null ? new org.bukkit.configuration.file.YamlConfiguration()
                    : org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        return defaults;
    }

    public boolean enabled() {
        return config().getBoolean("enabled", true);
    }

    public int levelsShown() {
        return Math.max(10, config().getInt("levels-shown", 100));
    }

    public Reward reward(int level) {
        ConfigurationSection c = config();
        long dust = c.getLong("dust", 25) + c.getLong("dust-per-level", 5) * level;
        int coins = c.getInt("ctf-coins", 50) + c.getInt("ctf-coins-per-level", 10) * level;
        int every = Math.max(0, c.getInt("mystery-box-every", 5));
        return new Reward(level, Math.max(0, dust), Math.max(0, coins), every > 0 && level % every == 0);
    }

    /** level-rewards.mystery-box-stars: stars → weight, in star order. */
    public NavigableMap<Integer, Integer> boxWeights() {
        NavigableMap<Integer, Integer> map = new TreeMap<>();
        ConfigurationSection weights = config().getConfigurationSection("mystery-box-stars");
        if (weights != null) {
            for (String key : weights.getKeys(false)) {
                try {
                    int stars = Math.max(1, Math.min(5, Integer.parseInt(key))), weight = weights.getInt(key);
                    if (weight > 0) map.merge(stars, weight, Integer::sum);
                } catch (NumberFormatException ignored) {
                    // not a star count
                }
            }
        }
        if (map.isEmpty()) for (int i = 1; i <= 3; i++) map.put(i, 1);
        return map;
    }

    /** A Mystery Box's stars, by level-rewards.mystery-box-stars weights. */
    private int rollStars() {
        NavigableMap<Integer, Integer> table = new TreeMap<>();
        int total = 0;
        for (var w : boxWeights().entrySet()) {
            total += w.getValue();
            table.put(total, w.getKey());
        }
        return table.higherEntry(ThreadLocalRandom.current().nextInt(total)).getValue();
    }

    /** The claimed levels (level → box stars, 0 for none); empty until read. */
    public Map<Integer, Integer> claimed(Player player) {
        return claimed.getOrDefault(player.getUniqueId(), Map.of());
    }

    /** Reached levels (2 up to their level) not claimed yet. */
    public List<Integer> waiting(Player player, int level) {
        List<Integer> list = new ArrayList<>();
        Map<Integer, Integer> mine = claimed(player);
        for (int l = 2; l <= level; l++) if (!mine.containsKey(l)) list.add(l);
        return list;
    }

    /** Reads what the player has claimed. */
    public CompletableFuture<Map<Integer, Integer>> load(Player player) {
        UUID id = player.getUniqueId();
        return db.run(c -> {
            Map<Integer, Integer> mine = new ConcurrentHashMap<>();
            try (PreparedStatement s = c.prepareStatement("SELECT level, stars FROM network_level_rewards WHERE uuid = ?")) {
                s.setString(1, id.toString());
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) mine.put(r.getInt(1), r.getInt(2));
                }
            }
            claimed.put(id, mine);
            return mine;
        });
    }

    /** One claimed level, as it was given. */
    public record Given(int level, long dust, int coins, int stars) {}

    /**
     * Claims these levels (ones already claimed, or above the player's level, are skipped). The claim rows and the
     * CTF coins are written in one transaction; then the dust and boxes are given on the main thread.
     * Completes with what was given (empty if nothing was).
     */
    public CompletableFuture<List<Given>> claim(Player player, List<Integer> levels, int level) {
        UUID id = player.getUniqueId();
        String name = player.getName();
        List<Given> plan = new ArrayList<>();
        for (int l : levels) {
            if (l < 2 || l > level) continue;
            Reward r = reward(l);
            plan.add(new Given(l, r.dust(), r.coins(), r.box() ? rollStars() : 0));
        }
        if (plan.isEmpty()) return CompletableFuture.completedFuture(List.of());
        return db.run(c -> write(c, id, name, plan)).thenApply(given -> {
            Map<Integer, Integer> mine = claimed.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
            for (Given g : given) mine.put(g.level(), g.stars());
            return given;
        });
    }

    private static List<Given> write(Connection c, UUID id, String name, List<Given> plan) throws SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            List<Given> given = new ArrayList<>();
            long now = System.currentTimeMillis();
            try (PreparedStatement claim = c.prepareStatement("INSERT IGNORE INTO network_level_rewards (uuid, level, stars, claimed_at)"
                    + " VALUES (?, ?, ?, ?)")) {
                for (Given g : plan) {
                    claim.setString(1, id.toString());
                    claim.setInt(2, g.level());
                    claim.setInt(3, g.stars());
                    claim.setLong(4, now);
                    if (claim.executeUpdate() == 1) given.add(g);             // 0: already claimed (another lobby, a double click)
                }
            }
            int coins = given.stream().mapToInt(Given::coins).sum();
            if (coins > 0) {
                try (PreparedStatement pay = c.prepareStatement("INSERT INTO network_pending_coins (uuid, name, game, coins, source, created)"
                        + " VALUES (?, ?, 'ctf', ?, ?, ?)")) {
                    pay.setString(1, id.toString());
                    pay.setString(2, name);
                    pay.setInt(3, coins);
                    pay.setString(4, "level rewards");
                    pay.setLong(5, now);
                    pay.executeUpdate();
                }
            }
            c.commit();
            return given;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    /** The CTF coin multiplier for this level (leveling.coin-multipliers: the highest step reached; 1 below them). */
    public double multiplier(int level) {
        double best = 1;
        for (var step : multipliers().entrySet()) if (level >= step.getKey()) best = step.getValue();
        return best;
    }

    /** leveling.coin-multipliers, by level. */
    public NavigableMap<Integer, Double> multipliers() {
        NavigableMap<Integer, Double> map = new TreeMap<>();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("leveling.coin-multipliers");
        if (s == null || s.getKeys(false).isEmpty()) s = defaults().getConfigurationSection("leveling.coin-multipliers"); // an older config.yml
        if (s == null) return map;
        for (String key : s.getKeys(false)) {
            try {
                map.put(Integer.parseInt(key), s.getDouble(key));
            } catch (NumberFormatException ignored) {
                // not a level
            }
        }
        return map;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        load(player).thenRun(() -> Bukkit.getScheduler().runTaskLater(plugin, () -> remind(player), 60L));
    }

    /** "You have N level rewards to claim" (after the level is read). */
    private void remind(Player player) {
        if (!player.isOnline() || plugin.levels() == null || !enabled()) return;
        int count = waiting(player, plugin.levels().progress(player).level()).size();
        if (count <= 0) return;
        var tags = com.sasha.lobby.config.Messages.arg("count", count);
        for (var line : plugin.messages().lore(count == 1 ? "levels.notify-one" : "levels.notify-many", tags)) {
            player.sendMessage(center(line));
        }
        player.playSound(net.kyori.adventure.sound.Sound.sound(net.kyori.adventure.key.Key.key("block.note_block.pling"),
                net.kyori.adventure.sound.Sound.Source.MASTER, 0.6f, 1.6f), net.kyori.adventure.sound.Sound.Emitter.self());
    }

    /** Pads a chat line so it sits in the middle of the chat box. */
    static net.kyori.adventure.text.Component center(net.kyori.adventure.text.Component line) {
        int width = com.sasha.lobby.scoreboard.TextWidth.width(line);
        int pad = (154 - width / 2) / 4;
        return width <= 0 || pad <= 0 ? line : net.kyori.adventure.text.Component.text(" ".repeat(pad)).append(line);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        claimed.remove(event.getPlayer().getUniqueId());
    }
}
