/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Stepping on a launch pad block (a stone pressure plate by default) flings the player the way they face. */
public final class LaunchPadListener implements Listener {

    /** A pad is pressed for several ticks while standing on it; launch only once per step. */
    private static final long RELAUNCH_MILLIS = 600;

    private final Lobby plugin;
    private final Map<UUID, Long> lastLaunch = new HashMap<>();

    public LaunchPadListener(Lobby plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onStep(PlayerInteractEvent event) {
        LobbyConfig.LaunchPads pads = plugin.lobbyConfig().launchPads();
        if (!pads.enabled() || event.getAction() != Action.PHYSICAL) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != pads.block()) return;
        event.setCancelled(true); // no redstone signal, no plate click sound

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastLaunch.get(player.getUniqueId());
        if (last != null && now - last < RELAUNCH_MILLIS) return;
        lastLaunch.put(player.getUniqueId(), now);

        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 1.0E-4) forward = new Vector(0, 0, 0);
        else forward.normalize().multiply(pads.forward());
        player.setVelocity(forward.setY(pads.upward()));

        Location at = block.getLocation().toCenterLocation();
        at.getWorld().spawnParticle(Particle.CLOUD, at, 25, 0.3, 0.1, 0.3, 0.05);
        at.getWorld().spawnParticle(Particle.WITCH, at, 15, 0.3, 0.2, 0.3, 0.1);
        if (!pads.sound().isEmpty()) {
            try {
                at.getWorld().playSound(Sound.sound(Key.key(pads.sound()), Sound.Source.PLAYER, pads.volume(), pads.pitch()),
                        at.getX(), at.getY(), at.getZ());
            } catch (RuntimeException e) {
                plugin.getLogger().warning("config.yml launch-pads.sound '" + pads.sound() + "' is not a sound name");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastLaunch.remove(event.getPlayer().getUniqueId());
    }
}
