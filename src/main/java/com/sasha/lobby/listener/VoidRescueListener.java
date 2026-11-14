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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Falling off the map puts players back where they last stood on solid ground, in a puff of clouds.
 * The last safe spot is remembered as players walk; with none yet, they go to the world spawn.
 */
public final class VoidRescueListener implements Listener {

    private final Lobby plugin;
    private final Map<UUID, Location> lastSafe = new HashMap<>();

    public VoidRescueListener(Lobby plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        LobbyConfig.VoidRescue config = plugin.lobbyConfig().voidRescue();
        if (!config.enabled()) return;
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to.getY() < config.belowY()) {
            Location target = rescueSpot(player, to);
            event.setTo(target);
            player.setFallDistance(0);
            player.setVelocity(new Vector());
            effects(player, target, config);
            return;
        }
        if (event.hasChangedBlock() && standingOnGround(to)) {
            lastSafe.put(player.getUniqueId(), to.toBlockLocation().add(0.5, 0, 0.5));
        }
    }

    /** A teleport elsewhere (warps, /spawn) shouldn't leave an old safe spot behind in another place. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() != event.getTo().getWorld()) lastSafe.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastSafe.remove(event.getPlayer().getUniqueId());
    }

    private Location rescueSpot(Player player, Location current) {
        Location safe = lastSafe.get(player.getUniqueId());
        Location target = safe != null && safe.getWorld() == current.getWorld()
                ? safe.clone()
                : plugin.lobbyConfig().spawn().location();
        target.setYaw(current.getYaw());
        target.setPitch(current.getPitch());
        return target;
    }

    private static boolean standingOnGround(Location at) {
        Block below = at.clone().subtract(0, 0.1, 0).getBlock();
        return below.getType().isSolid() && !below.isPassable();
    }

    private void effects(Player player, Location at, LobbyConfig.VoidRescue config) {
        at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 1, 0), 40, 0.35, 0.6, 0.35, 0.03);
        at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 0.1, 0), 20, 0.6, 0.05, 0.6, 0.01);
        if (!config.sound().isEmpty()) {
            try {
                player.playSound(Sound.sound(Key.key(config.sound()), Sound.Source.PLAYER,
                        config.volume(), config.pitch()), Sound.Emitter.self());
            } catch (RuntimeException e) {
                plugin.getLogger().warning("config.yml void-rescue.sound '" + config.sound() + "' is not a sound name");
            }
        }
        plugin.messages().actionBar(player, "void-rescued");
    }
}
