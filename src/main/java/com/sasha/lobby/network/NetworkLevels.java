/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.network;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.data.NetworkDb;
import org.bukkit.Bukkit;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network levels: XP lives in the network database ({@code network_players}) and is earned on other servers
 * (CTF gives it for each minute of active play). This keeps the online players' XP to show in their profile.
 *
 * <p>Going from level L to L+1 takes {@code first + extra × (L − 1)} XP, so level 2 takes {@code first} XP and each
 * level after takes {@code extra} more. CTF uses the same numbers for its level-up message.
 */
public final class NetworkLevels implements Listener {

    /** Where a player is: their level, XP into it, XP the level takes, and all their XP. */
    public record Progress(int level, long into, long needed, long total) {
        public double fraction() { return needed <= 0 ? 0 : Math.min(1, (double) into / needed); }
    }

    private final Lobby plugin;
    private final NetworkDb db;
    private final Map<UUID, Long> xp = new ConcurrentHashMap<>();
    private BukkitTask task;

    public NetworkLevels(Lobby plugin, NetworkDb db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refresh, 40L, 600L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    /** XP needed to go from this level to the next. */
    public static long needed(int level, long first, long extra) {
        return Math.max(1, first + extra * (level - 1L));
    }

    public static Progress progress(long total, long first, long extra) {
        int level = 1;
        long left = Math.max(0, total);
        while (left >= needed(level, first, extra) && level < 10_000) {
            left -= needed(level, first, extra);
            level++;
        }
        return new Progress(level, left, needed(level, first, extra), total);
    }

    /** This player's level (level 1 with no XP, or until it's been read). */
    public Progress progress(Player player) {
        var c = plugin.getConfig();
        return progress(xp.getOrDefault(player.getUniqueId(), 0L),
                Math.max(1, c.getLong("leveling.first-level-xp", 200)), Math.max(0, c.getLong("leveling.extra-xp-per-level", 50)));
    }

    private void refresh() {
        List<UUID> online = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) online.add(player.getUniqueId());
        for (UUID uuid : online) read(uuid);
    }

    private void read(UUID uuid) {
        try {
            Long value = db.now(c -> {
                try (PreparedStatement s = c.prepareStatement("SELECT xp FROM network_players WHERE uuid = ?")) {
                    s.setString(1, uuid.toString());
                    try (ResultSet r = s.executeQuery()) {
                        return r.next() ? r.getLong(1) : 0L;
                    }
                }
            });
            xp.put(uuid, value);
        } catch (SQLException e) {
            plugin.getLogger().fine("Couldn't read network XP: " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> read(uuid));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        xp.remove(event.getPlayer().getUniqueId());
    }
}
