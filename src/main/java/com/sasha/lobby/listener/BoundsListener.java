/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * An invisible wall from the sky to the void around the lobby (bounds in config.yml): anyone who goes past
 * it is sent straight back to spawn. Players with lobby.bypass.bounds can go anywhere.
 */
public final class BoundsListener implements Listener {

    public static final String BYPASS = "lobby.bypass.bounds";

    private final Lobby plugin;
    private SpawnListener spawn;

    public BoundsListener(Lobby plugin, SpawnListener spawn) {
        this.plugin = plugin;
        this.spawn = spawn;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return; // cheap: only when they step into another block
        ConfigurationSection bounds = plugin.getConfig().getConfigurationSection("bounds");
        if (bounds == null || !bounds.getBoolean("enabled", false)) return;
        Location to = event.getTo();
        String world = bounds.getString("world", plugin.lobbyConfig().spawn().world());
        if (!to.getWorld().getName().equals(world)) return;
        double x1 = bounds.getDouble("x1"), x2 = bounds.getDouble("x2"), z1 = bounds.getDouble("z1"), z2 = bounds.getDouble("z2");
        boolean inside = to.getX() >= Math.min(x1, x2) && to.getX() <= Math.max(x1, x2)
                && to.getZ() >= Math.min(z1, z2) && to.getZ() <= Math.max(z1, z2);
        if (inside) return;
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS)) return;
        if (player.getVehicle() != null) player.leaveVehicle();
        player.setFallDistance(0);
        spawn.sendToSpawn(player);
        plugin.messages().actionBar(player, "out-of-bounds");
        player.playSound(Sound.sound(Key.key("entity.enderman.teleport"), Sound.Source.PLAYER, 0.6f, 1.2f), Sound.Emitter.self());
    }
}
