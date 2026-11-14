/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.WorldBorder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Sets the spawn world's real world border: a square around the spawn point, the same for everyone.
 * Applied on startup, on /lobby reload, on /setspawn, and if the spawn world loads later.
 */
public final class BorderListener implements Listener {

    private final Lobby plugin;

    private final NamespacedKey key;

    public BorderListener(Lobby plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "border");
    }

    /**
     * Puts the world border around spawn. Turned off in the config, it removes the border Lobby set
     * (one centred on spawn, or marked as Lobby's), and leaves any other border alone.
     */
    public void apply() {
        LobbyConfig.Border border = plugin.lobbyConfig().border();
        LobbyConfig.Spawn spawn = plugin.lobbyConfig().spawn();
        org.bukkit.World world = plugin.getServer().getWorld(spawn.world());
        if (world == null) return;
        WorldBorder wall = world.getWorldBorder();
        PersistentDataContainer data = world.getPersistentDataContainer();
        if (!border.enabled()) {
            boolean centredOnSpawn = Math.abs(wall.getCenter().getX() - spawn.x()) < 0.01
                    && Math.abs(wall.getCenter().getZ() - spawn.z()) < 0.01;
            if (centredOnSpawn) wall.reset();
            // Also any world Lobby put a border on before (e.g. the old lobby world after moving spawn).
            for (org.bukkit.World other : plugin.getServer().getWorlds()) {
                if (other.getPersistentDataContainer().has(key)) {
                    other.getWorldBorder().reset();
                    other.getPersistentDataContainer().remove(key);
                }
            }
            return;
        }
        data.set(key, PersistentDataType.BOOLEAN, true);
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
