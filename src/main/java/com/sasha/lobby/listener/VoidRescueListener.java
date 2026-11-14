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
 * Falling off the map flies players straight back to the last block they stood on, the quickest way
 * there, leaving a particle trail. With no safe spot yet, they're flown to spawn.
 */
public final class VoidRescueListener implements Listener {

    /** Give up flying after this long and put them there directly (e.g. something is in the way). */
    private static final int MAX_FLIGHT_TICKS = 200;

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
        Flight flight = new Flight(player, target);
        flights.put(player.getUniqueId(), plugin.getServer().getScheduler().runTaskTimer(plugin, flight::tick, 0L, 1L));
    }

    /**
     * The rescue path, the quickest way back to the exact block they last stood on: straight up the column
     * they fell down (it's open, they just came through it) to just above that block's height, then a
     * straight line onto it, hopping over anything in the way. If they still get stuck, they're put there.
     */
    private final class Flight {
        private final Player player;
        private final Location landing;
        private boolean rising = true;
        private int ticks;
        private int stuckTicks;
        private Location last;

        Flight(Player player, Location landing) {
            this.player = player;
            this.landing = landing;
            this.last = player.getLocation();
        }

        void tick() {
            if (!player.isOnline()) {
                stopFlight(player);
                return;
            }
            Location at = player.getLocation();
            player.setFallDistance(0);
            if (at.getWorld() != landing.getWorld() || ++ticks > MAX_FLIGHT_TICKS || stuck(at)) {
                land(true);
                return;
            }

            Vector velocity;
            double riseTo = landing.getY() + 1.2;
            if (rising && at.getY() < riseTo - 0.2) {
                if (blockedAbove(at)) {
                    land(true);
                    return;
                }
                velocity = new Vector(0, Math.min(2.2, 0.6 + (riseTo - at.getY()) * 0.15), 0);
            } else {
                rising = false;
                Vector toLanding = landing.toVector().subtract(at.toVector());
                double distance = toLanding.length();
                if (distance < 0.7) {
                    land(true); // a tiny snap onto the exact spot
                    return;
                }
                velocity = toLanding.normalize().multiply(Math.min(1.6, 0.35 + distance * 0.3));
                if (blockedAhead(at, velocity)) velocity.setY(Math.max(velocity.getY(), 0.6));
            }
            player.setVelocity(velocity);

            Location trail = at.clone().add(0, 0.9, 0);
            at.getWorld().spawnParticle(Particle.CLOUD, trail, 2, 0.15, 0.15, 0.15, 0.01);
            at.getWorld().spawnParticle(Particle.WITCH, trail, 4, 0.2, 0.2, 0.2, 0.02);
        }

        /** Not moving for a while: something is in the way. */
        private boolean stuck(Location at) {
            stuckTicks = at.distanceSquared(last) < 0.0025 ? stuckTicks + 1 : 0;
            last = at;
            return stuckTicks > 8;
        }

        private void land(boolean placeDirectly) {
            if (placeDirectly) player.teleport(landing.clone().setDirection(player.getLocation().getDirection()));
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0);
            player.getWorld().spawnParticle(Particle.CLOUD, landing.clone().add(0, 0.2, 0), 20, 0.5, 0.05, 0.5, 0.02);
            stopFlight(player);
        }
    }

    /** A solid block in the two blocks above the player's head. */
    private static boolean blockedAbove(Location at) {
        for (int up = 2; up <= 3; up++) {
            if (!at.clone().add(0, up, 0).getBlock().isPassable()) return true;
        }
        return false;
    }

    /** A solid block just ahead at foot or head height. */
    private static boolean blockedAhead(Location at, Vector velocity) {
        Vector ahead = velocity.clone().setY(0);
        if (ahead.lengthSquared() < 1.0E-4) return false;
        ahead.normalize().multiply(0.7);
        for (double up : new double[] {0.1, 1.1}) {
            if (!at.clone().add(ahead).add(0, up, 0).getBlock().isPassable()) return true;
        }
        return false;
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
