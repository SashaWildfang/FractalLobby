/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;

/** Joining puts players at the lobby spawn (spawn.teleport-on-join), and /spawn and /setspawn. */
public final class SpawnListener implements Listener {

    private final Lobby plugin;

    public SpawnListener(Lobby plugin) {
        this.plugin = plugin;
    }

    /** Sets where the player appears before they even load in, so there's no visible jump. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        if (plugin.lobbyConfig().spawn().teleportOnJoin()) event.setSpawnLocation(plugin.lobbyConfig().spawn().location());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().setFallDistance(0);
    }

    public void sendToSpawn(Player player) {
        player.teleportAsync(plugin.lobbyConfig().spawn().location());
    }

    /** Saves the player's position as the spawn in config.yml (comments are kept) and reloads. */
    public void setSpawn(Player player) {
        Location at = player.getLocation();
        var config = plugin.getConfig();
        config.set("spawn.world", at.getWorld().getName());
        config.set("spawn.x", round(at.getX(), 3));
        config.set("spawn.y", round(at.getY(), 3));
        config.set("spawn.z", round(at.getZ(), 3));
        config.set("spawn.yaw", round(at.getYaw(), 1));
        config.set("spawn.pitch", round(at.getPitch(), 1));
        plugin.saveConfig();
        plugin.reload();
    }

    private static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
