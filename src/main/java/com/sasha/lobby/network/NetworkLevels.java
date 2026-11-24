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

    public long first() {
        return Math.max(1, plugin.getConfig().getLong("leveling.first-level-xp", 200));
    }

    public long extra() {
        return Math.max(0, plugin.getConfig().getLong("leveling.extra-xp-per-level", 50));
    }

    /** This player's level (level 1 with no XP, or until it's been read). */
    public Progress progress(Player player) {
        return progress(xp.getOrDefault(player.getUniqueId(), 0L), first(), extra());
    }

    /**
     * The XP bar shows the network level: the number is the level, the bar how far to the next
     * (leveling.xp-bar; the lobby has no use for vanilla XP).
     */
    public void showOnXpBar(Player player) {
        if (!plugin.getConfig().getBoolean("leveling.xp-bar", true) || !player.isOnline()) return;
        Progress p = progress(player);
        player.setLevel(p.level());
        player.setExp((float) Math.min(0.999, p.fraction()));
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
            Long before = xp.put(uuid, value);
            if (!value.equals(before)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) showOnXpBar(player);
                });
            }
        } catch (SQLException e) {
            plugin.getLogger().fine("Couldn't read network XP: " + e.getMessage());
        }
    }

    /** Vanilla gives XP back on respawn and world changes: show the level again. */
    @EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> showOnXpBar(event.getPlayer()));
    }

    @EventHandler
    public void onWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        showOnXpBar(event.getPlayer());
    }

    /** No XP orbs in the lobby (they'd change the bar). */
    @EventHandler(ignoreCancelled = true)
    public void onExp(org.bukkit.event.player.PlayerExpChangeEvent event) {
        if (plugin.getConfig().getBoolean("leveling.xp-bar", true)) event.setAmount(0);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        showOnXpBar(event.getPlayer());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> read(uuid));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        xp.remove(event.getPlayer().getUniqueId());
    }
}
