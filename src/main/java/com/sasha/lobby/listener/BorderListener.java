/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Sets the spawn world's real world border: a square around the spawn point, the same for everyone.
 * Applied on startup, on /lobby reload, on /setspawn, and if the spawn world loads later.
 */
public final class BorderListener implements Listener {

    private final Lobby plugin;

    public BorderListener(Lobby plugin) {
        this.plugin = plugin;
    }

    /** Puts the world border around spawn (does nothing when the border is turned off in the config). */
    public void apply() {
        LobbyConfig.Border border = plugin.lobbyConfig().border();
        if (!border.enabled()) return;
        LobbyConfig.Spawn spawn = plugin.lobbyConfig().spawn();
        org.bukkit.World world = plugin.getServer().getWorld(spawn.world());
        if (world == null) return;
        WorldBorder wall = world.getWorldBorder();
        wall.setCenter(spawn.x(), spawn.z());
        wall.setSize(border.radius() * 2.0);
        wall.setWarningDistance(border.warningDistance());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (event.getWorld().getName().equals(plugin.lobbyConfig().spawn().world())) apply();
    }

    /** How far past the square border a location is (0 or less = inside). */
    public static double outside(Location at, Location center, double radius) {
        double dx = Math.abs(at.getX() - center.getX());
        double dz = Math.abs(at.getZ() - center.getZ());
        return Math.max(dx, dz) - radius;
    }
}
