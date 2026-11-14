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
import org.bukkit.World;
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
 * Falling off the map lifts players back up, flies them over to the last block they stood on and sets
 * them down on it, leaving a particle trail. With no safe spot yet, they're flown to spawn.
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
        Flight flight = new Flight(player, target.clone().add(0, 0.05, 0));
        flights.put(player.getUniqueId(), plugin.getServer().getScheduler().runTaskTimer(plugin, flight::tick, 0L, 1L));
    }

    /**
     * The rescue path: straight up the column they fell down (it's open, they just came through it), across
     * above every block between there and the landing spot, then gently down onto it. If anything still gets
     * in the way, they're put down there directly.
     */
    private final class Flight {
        private final Player player;
        private final Location landing;
        private final double cruiseY;
        private Phase phase = Phase.RISE;
        private int ticks;
        private int stuckTicks;
        private Location last;

        Flight(Player player, Location landing) {
            this.player = player;
            this.landing = landing;
            this.cruiseY = cruiseHeight(player.getLocation(), landing);
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
            switch (phase) {
                case RISE -> {
                    if (blockedAbove(at)) {
                        land(true);
                        return;
                    }
                    velocity = new Vector(0, Math.min(1.8, 0.4 + (cruiseY - at.getY()) * 0.1), 0);
                    if (at.getY() >= cruiseY - 0.3) phase = Phase.GLIDE;
                }
                case GLIDE -> {
                    Vector across = landing.toVector().subtract(at.toVector()).setY(0);
                    double distance = across.length();
                    if (distance < 0.4) {
                        phase = Phase.DESCEND;
                        velocity = new Vector();
                    } else {
                        velocity = across.normalize().multiply(Math.min(1.1, 0.25 + distance * 0.15));
                        velocity.setY((cruiseY - at.getY()) * 0.3);
                    }
                }
                default -> {
                    double above = at.getY() - landing.getY();
                    if (above < 0.15 || standingOnGround(at)) {
                        land(false);
                        return;
                    }
                    Vector across = landing.toVector().subtract(at.toVector()).setY(0).multiply(0.4);
                    velocity = across.setY(-Math.max(0.15, Math.min(0.7, above * 0.25)));
                }
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
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.2, 0), 20, 0.5, 0.05, 0.5, 0.02);
            stopFlight(player);
        }
    }

    private enum Phase { RISE, GLIDE, DESCEND }

    /** A height above every block between the two spots (and the landing spot), with room to spare. */
    private static double cruiseHeight(Location from, Location to) {
        World world = to.getWorld();
        double highest = to.getY();
        Vector step = to.toVector().subtract(from.toVector()).setY(0);
        int samples = Math.max(1, (int) Math.ceil(step.length() * 2));
        for (int i = 0; i <= samples; i++) {
            double x = from.getX() + step.getX() * i / samples;
            double z = from.getZ() + step.getZ() * i / samples;
            for (double ox = -0.5; ox <= 0.5; ox += 0.5) {
                for (double oz = -0.5; oz <= 0.5; oz += 0.5) {
                    highest = Math.max(highest, world.getHighestBlockYAt((int) Math.floor(x + ox), (int) Math.floor(z + oz)) + 1);
                }
            }
        }
        return highest + 2.5;
    }

    /** A solid block in the two blocks above the player's head. */
    private static boolean blockedAbove(Location at) {
        for (int up = 2; up <= 3; up++) {
            Block block = at.clone().add(0, up, 0).getBlock();
            if (!block.isPassable()) return true;
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
