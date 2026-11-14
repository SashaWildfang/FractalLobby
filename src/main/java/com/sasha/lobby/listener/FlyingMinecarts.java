/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /flyingminecart: a minecart anyone can ride and fly around the lobby. The rider steers with W (towards
 * where they look), S (brake/reverse), Space (climb) and Sprint (boost); sneaking gets out. Empty carts
 * vanish after a while, and two ridden carts that touch both explode (a harmless, visual explosion).
 */
public final class FlyingMinecarts implements Listener {

    public static final String PERMISSION = "lobby.cmd.flyingminecart";
    private static final double ACCELERATION = 0.12;
    private static final double CLIMB = 0.09;
    private static final double DRAG = 0.86;
    private static final double CRASH_DISTANCE = 1.4;

    private final Lobby plugin;
    private final NamespacedKey key;
    /** Our carts, with the time each became empty (0 while someone is riding). */
    private final Map<UUID, Long> carts = new HashMap<>();
    private BukkitTask task;

    public FlyingMinecarts(Lobby plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "flying-minecart");
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Removes every flying minecart (plugin disabling). */
    public void stop() {
        if (task != null) task.cancel();
        for (UUID id : List.copyOf(carts.keySet())) {
            Entity cart = plugin.getServer().getEntity(id);
            if (cart != null) cart.remove();
        }
        carts.clear();
    }

    /** Summons a flying minecart just in front of the player. */
    public void summon(Player player) {
        Location at = player.getLocation();
        Location spot = at.clone().add(at.getDirection().setY(0).normalize().multiply(1.5)).add(0, 0.3, 0);
        RideableMinecart cart = at.getWorld().spawn(spot, RideableMinecart.class, minecart -> {
            minecart.setGravity(false);
            minecart.setPersistent(false); // never saved with the world, so restarts leave nothing behind
            minecart.setMaxSpeed(plugin.lobbyConfig().flyingMinecarts().speed() * 2);
            minecart.setSlowWhenEmpty(false);
            minecart.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        });
        carts.put(cart.getUniqueId(), System.currentTimeMillis());
        at.getWorld().spawnParticle(Particle.WITCH, spot.clone().add(0, 0.5, 0), 25, 0.4, 0.3, 0.4, 0.05);
        player.playSound(Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.PLAYER, 0.6f, 0.8f), Sound.Emitter.self());
        plugin.messages().send(player, "flying-minecart.summoned");
    }

    public boolean isFlyingCart(Entity entity) {
        return entity instanceof RideableMinecart
                && entity.getPersistentDataContainer().getOrDefault(key, PersistentDataType.BOOLEAN, false);
    }

    private void tick() {
        LobbyConfig.FlyingMinecarts config = plugin.lobbyConfig().flyingMinecarts();
        long now = System.currentTimeMillis();
        List<RideableMinecart> ridden = new ArrayList<>();

        for (UUID id : List.copyOf(carts.keySet())) {
            if (!(plugin.getServer().getEntity(id) instanceof RideableMinecart cart) || !cart.isValid()) {
                carts.remove(id);
                continue;
            }
            Player rider = rider(cart);
            if (rider == null) {
                long emptySince = carts.get(id) == 0 ? now : carts.get(id);
                carts.put(id, emptySince);
                cart.setVelocity(cart.getVelocity().multiply(0.8)); // drift to a hover
                if (now - emptySince > config.despawnSeconds() * 1000L) poof(cart);
                continue;
            }
            carts.put(id, 0L);
            fly(cart, rider, config);
            ridden.add(cart);
        }

        if (config.explodeOnCollision()) crashes(ridden);
    }

    private void fly(RideableMinecart cart, Player rider, LobbyConfig.FlyingMinecarts config) {
        Input input = rider.getCurrentInput();
        Vector look = rider.getLocation().getDirection();
        Vector velocity = cart.getVelocity().multiply(DRAG);
        if (input.isForward()) velocity.add(look.clone().multiply(ACCELERATION));
        if (input.isBackward()) velocity.subtract(look.clone().multiply(ACCELERATION * 0.6));
        if (input.isJump()) velocity.setY(velocity.getY() + CLIMB);

        double max = config.speed() * (input.isSprint() ? 1.6 : 1.0);
        if (velocity.length() > max) velocity.normalize().multiply(max);

        // Stay inside the spawn border: steer back in when crossing it.
        LobbyConfig.Border border = plugin.lobbyConfig().border();
        if (border.enabled() && !rider.hasPermission(border.bypassPermission())) {
            Location center = plugin.lobbyConfig().spawn().location();
            Location next = cart.getLocation().add(velocity);
            if (center.getWorld() == next.getWorld() && BorderListener.outside(next, center, border.radius()) > 0) {
                velocity = center.toVector().subtract(cart.getLocation().toVector()).setY(0).normalize().multiply(0.5);
            }
        }
        cart.setVelocity(velocity);
        rider.setFallDistance(0);

        if (velocity.lengthSquared() > 0.04) {
            cart.getWorld().spawnParticle(Particle.WITCH, cart.getLocation().add(0, 0.3, 0), 2, 0.2, 0.1, 0.2, 0.0);
        }
    }

    /** Two ridden carts close enough together both blow up. */
    private void crashes(List<RideableMinecart> ridden) {
        List<RideableMinecart> exploding = new ArrayList<>();
        for (int i = 0; i < ridden.size(); i++) {
            for (int j = i + 1; j < ridden.size(); j++) {
                RideableMinecart a = ridden.get(i), b = ridden.get(j);
                if (a.getWorld() == b.getWorld() && a.getLocation().distanceSquared(b.getLocation()) < CRASH_DISTANCE * CRASH_DISTANCE) {
                    if (!exploding.contains(a)) exploding.add(a);
                    if (!exploding.contains(b)) exploding.add(b);
                }
            }
        }
        for (RideableMinecart cart : exploding) explode(cart);
    }

    /** A visual explosion: no block damage, no player damage. Riders are thrown clear. */
    private void explode(RideableMinecart cart) {
        Location at = cart.getLocation();
        Player rider = rider(cart);
        cart.eject();
        carts.remove(cart.getUniqueId());
        cart.remove();

        at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
        at.getWorld().spawnParticle(Particle.WITCH, at, 40, 0.8, 0.8, 0.8, 0.2);
        at.getWorld().playSound(Sound.sound(Key.key("entity.generic.explode"), Sound.Source.PLAYER, 1f, 1f), at.getX(), at.getY(), at.getZ());
        if (rider != null) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            rider.setVelocity(new Vector(random.nextDouble(-0.6, 0.6), 0.9, random.nextDouble(-0.6, 0.6)));
            rider.setFallDistance(0);
            plugin.messages().actionBar(rider, "flying-minecart.crashed");
        }
    }

    private void poof(RideableMinecart cart) {
        cart.getWorld().spawnParticle(Particle.CLOUD, cart.getLocation().add(0, 0.4, 0), 15, 0.3, 0.2, 0.3, 0.02);
        carts.remove(cart.getUniqueId());
        cart.remove();
    }

    private static Player rider(RideableMinecart cart) {
        for (Entity passenger : cart.getPassengers()) {
            if (passenger instanceof Player player) return player;
        }
        return null;
    }

    // ---------------------------------------------------------------- protection

    /** Only admins can break flying minecarts. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(VehicleDamageEvent event) {
        if (!isFlyingCart(event.getVehicle())) return;
        if (!(event.getAttacker() instanceof Player player) || !player.hasPermission(PERMISSION)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDestroy(VehicleDestroyEvent event) {
        if (isFlyingCart(event.getVehicle())) carts.remove(event.getVehicle().getUniqueId());
    }

    /** No shoving carts around by walking into them; crashes are handled by the flight code. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBump(VehicleEntityCollisionEvent event) {
        if (isFlyingCart(event.getVehicle())) {
            event.setCancelled(true);
        }
    }
}
