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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Falling off the map lifts players back up and flies them in an arc to the last block they stood on,
 * leaving a particle trail. With no safe spot yet, they're flown to spawn.
 */
public final class VoidRescueListener implements Listener {

    /** Give up flying after this long and put them there directly (e.g. something is in the way). */
    private static final int MAX_FLIGHT_TICKS = 80;

    private final Lobby plugin;
    private final Map<UUID, Location> lastSafe = new HashMap<>();
    private final Map<UUID, BukkitTask> flights = new HashMap<>();

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
            if (!flights.containsKey(player.getUniqueId()) && player.getVehicle() == null) startFlight(player, rescueSpot(player, to), config);
            return;
        }
        if (event.hasChangedBlock() && !flights.containsKey(player.getUniqueId()) && standingOnGround(to)) {
            lastSafe.put(player.getUniqueId(), to.toBlockLocation().add(0.5, 0, 0.5));
        }
    }

    /** A teleport to another world shouldn't leave a safe spot from the old one behind. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() != event.getTo().getWorld()) lastSafe.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastSafe.remove(id);
        BukkitTask flight = flights.remove(id);
        if (flight != null) flight.cancel();
    }

    /** Stops every flight (plugin disabling). */
    public void stopAll() {
        flights.values().forEach(BukkitTask::cancel);
        flights.clear();
    }

    private void startFlight(Player player, Location target, LobbyConfig.VoidRescue config) {
        playSound(player, config.sound(), config.volume(), config.pitch());
        plugin.messages().actionBar(player, "void-rescued");
        Location landing = target.clone().add(0, 0.2, 0);
        int[] ticks = {0};

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stopFlight(player);
                return;
            }
            Location at = player.getLocation();
            Vector toTarget = landing.toVector().subtract(at.toVector());
            double distance = toTarget.length();
            player.setFallDistance(0);

            if (distance < 1.3 || ticks[0]++ > MAX_FLIGHT_TICKS || at.getWorld() != landing.getWorld()) {
                if (distance >= 1.3) player.teleport(landing.clone().setDirection(at.getDirection()));
                player.setVelocity(new Vector(0, 0.1, 0));
                at.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.2, 0), 20, 0.5, 0.05, 0.5, 0.02);
                stopFlight(player);
                return;
            }

            // Head for the landing spot, rising extra while still below it, so the path is an arc out of the void.
            double speed = Math.min(1.6, 0.45 + distance * 0.08);
            Vector velocity = toTarget.normalize().multiply(speed);
            if (at.getY() < landing.getY() + 1.5) velocity.setY(Math.max(velocity.getY(), 0.55));
            player.setVelocity(velocity);

            Location trail = at.clone().add(0, 0.9, 0);
            at.getWorld().spawnParticle(Particle.CLOUD, trail, 3, 0.15, 0.15, 0.15, 0.01);
            at.getWorld().spawnParticle(Particle.WITCH, trail, 4, 0.2, 0.2, 0.2, 0.02);
        }, 0L, 1L);
        flights.put(player.getUniqueId(), task);
    }

    private void stopFlight(Player player) {
        BukkitTask task = flights.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    private Location rescueSpot(Player player, Location current) {
        Location safe = lastSafe.get(player.getUniqueId());
        return safe != null && safe.getWorld() == current.getWorld() ? safe.clone() : plugin.lobbyConfig().spawn().location();
    }

    private static boolean standingOnGround(Location at) {
        Block below = at.clone().subtract(0, 0.1, 0).getBlock();
        return below.getType().isSolid() && !below.isPassable();
    }

    private void playSound(Player player, String sound, float volume, float pitch) {
        if (sound.isEmpty()) return;
        try {
            player.playSound(Sound.sound(Key.key(sound), Sound.Source.PLAYER, volume, pitch), Sound.Emitter.self());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("config.yml void-rescue.sound '" + sound + "' is not a sound name");
        }
    }
}
