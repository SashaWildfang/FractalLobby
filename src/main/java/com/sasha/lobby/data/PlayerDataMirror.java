/*
 * FractalLobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.data;

import com.sasha.lobby.Lobby;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's lobby settings (everything FractalLobby and FractalRadio keep on the player: settings, status,
 * trail, chat colour, glow, radio volume and favourites...) live in the database, not only in the world's player
 * files. They're read before the player is let in, put on the player first thing when they join, and written back
 * when they leave (and every few minutes). A player the database has never seen keeps what's on them, and that is
 * saved the first time they leave: that's how the old data moves over.
 */
public final class PlayerDataMirror implements Listener {

    /** The plugins whose data is kept, by their key namespace. */
    public static final List<String> NAMESPACES = List.of("fractallobby", "fractalradio");

    private final Lobby plugin;
    private final NetworkDb db;
    private final Map<UUID, Map<String, byte[]>> waiting = new ConcurrentHashMap<>();

    public PlayerDataMirror(Lobby plugin, NetworkDb db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) save(player, false);
        }, 20L * 300, 20L * 300);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        try {
            Map<String, byte[]> rows = db.now(c -> {
                Map<String, byte[]> out = new HashMap<>();
                try (PreparedStatement s = c.prepareStatement("SELECT namespace, data FROM lobby_player_data WHERE uuid = ?")) {
                    s.setString(1, event.getUniqueId().toString());
                    try (ResultSet r = s.executeQuery()) {
                        while (r.next()) out.put(r.getString(1), r.getBytes(2));
                    }
                }
                return out;
            });
            waiting.put(event.getUniqueId(), rows);
        } catch (SQLException e) {
            plugin.getLogger().warning("Couldn't read " + event.getName() + "'s lobby data: " + e.getMessage());
        }
    }

    /** First of all join listeners, so everything that reads the player's settings sees the database's. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Map<String, byte[]> rows = waiting.remove(player.getUniqueId());
        if (rows == null || rows.isEmpty()) return;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        for (Map.Entry<String, byte[]> row : rows.entrySet()) {
            try {
                PersistentDataContainer saved = pdc.getAdapterContext().newPersistentDataContainer();
                saved.readFromBytes(row.getValue());
                for (NamespacedKey key : new ArrayList<>(pdc.getKeys())) if (key.getNamespace().equals(row.getKey())) pdc.remove(key);
                saved.copyTo(pdc, true);
            } catch (IOException e) {
                plugin.getLogger().warning("Couldn't read " + player.getName() + "'s " + row.getKey() + " data: " + e.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        waiting.remove(event.getPlayer().getUniqueId());
        save(event.getPlayer(), false);
    }

    /** What's on the player now, per namespace (on the main thread), written in the background (or now). */
    public void save(Player player, boolean now) {
        Map<String, byte[]> rows = new HashMap<>();
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        for (String namespace : NAMESPACES) {
            try {
                PersistentDataContainer part = pdc.getAdapterContext().newPersistentDataContainer();
                pdc.copyTo(part, true);
                for (NamespacedKey key : new ArrayList<>(part.getKeys())) if (!key.getNamespace().equals(namespace)) part.remove(key);
                rows.put(namespace, part.serializeToBytes());
            } catch (IOException e) {
                plugin.getLogger().warning("Couldn't save " + player.getName() + "'s " + namespace + " data: " + e.getMessage());
            }
        }
        UUID id = player.getUniqueId();
        NetworkDb.Work<Void> write = c -> {
            try (PreparedStatement s = c.prepareStatement("INSERT INTO lobby_player_data (uuid, namespace, data, updated) VALUES (?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE data = VALUES(data), updated = VALUES(updated)")) {
                long time = System.currentTimeMillis();
                for (Map.Entry<String, byte[]> row : rows.entrySet()) {
                    s.setString(1, id.toString());
                    s.setString(2, row.getKey());
                    s.setBytes(3, row.getValue());
                    s.setLong(4, time);
                    s.addBatch();
                }
                s.executeBatch();
            }
            return null;
        };
        if (now) {
            try {
                db.now(write);
            } catch (SQLException e) {
                plugin.getLogger().warning("Couldn't save " + player.getName() + "'s lobby data: " + e.getMessage());
            }
        } else {
            db.run(write);
        }
    }

    public void saveAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) save(player, true);
    }
}
