/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import com.sasha.lobby.items.Settings;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Double jump: pressing jump in mid-air launches the player the way they're facing. It works through the
 * game's "allow flight" switch, which only Lobby turns on (and only for players nothing else gave flight),
 * and is off in creative, in vehicles and during an ajParkour run. Each player can turn it off in settings.
 */
public final class DoubleJump implements Listener {

    public static final String PERMISSION = "lobby.doublejump";

    private final Lobby plugin;
    /** Players Lobby gave "allow flight" to for double jumping. */
    private final Set<UUID> granted = new HashSet<>();
    /** Players in an ajParkour run right now (checked twice a second). */
    private final Set<UUID> runners = new HashSet<>();
    private BukkitTask parkourTask;

    public DoubleJump(Lobby plugin) {
        this.plugin = plugin;
    }

    public void start() {
        parkourTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkParkour, 20L, 10L);
    }

    /** Takes double jump away from everyone (plugin disabling). */
    public void stop() {
        if (parkourTask != null) parkourTask.cancel();
        for (Player player : plugin.getServer().getOnlinePlayers()) revoke(player);
    }

    private boolean allowed(Player player) {
        LobbyConfig.DoubleJump config = plugin.lobbyConfig().doubleJump();
        GameMode mode = player.getGameMode();
        return config.enabled() && player.hasPermission(PERMISSION)
                && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && player.getVehicle() == null && !runners.contains(player.getUniqueId())
                && plugin.settings().on(player, Settings.Setting.DOUBLE_JUMP);
    }

    /** Gives or takes away double jump to match the player's situation. */
    public void refresh(Player player) {
        if (!allowed(player)) {
            revoke(player);
            return;
        }
        if (granted.contains(player.getUniqueId())) {
            if (!player.getAllowFlight() && onGround(player)) player.setAllowFlight(true); // landed: jump is back
        } else if (!player.getAllowFlight()) { // never take over flight another plugin gave (e.g. /fly)
            granted.add(player.getUniqueId());
            player.setAllowFlight(true);
        }
    }

    private void revoke(Player player) {
        if (!granted.remove(player.getUniqueId())) return;
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    @SuppressWarnings("deprecation") // the client's own on-ground flag is fine for this
    private static boolean onGround(Player player) {
        return player.isOnGround();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDoubleJump(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying() || !granted.contains(player.getUniqueId())) return;
        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false); // until they land
        if (!allowed(player)) return;

        LobbyConfig.DoubleJump config = plugin.lobbyConfig().doubleJump();
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() > 0) direction.normalize();
        player.setVelocity(direction.multiply(config.forward()).setY(config.upward()));
        player.setFallDistance(0);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 12, 0.3, 0.05, 0.3, 0.02);
        if (!config.sound().isEmpty()) {
            try {
                player.getWorld().playSound(Sound.sound(Key.key(config.sound()), Sound.Source.PLAYER, config.volume(), config.pitch()),
                        player.getX(), player.getY(), player.getZ());
            } catch (RuntimeException e) {
                plugin.getLogger().warning("config.yml double-jump.sound '" + config.sound() + "' is not a sound name");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        // Cheap checks first: this runs on every step.
        if (granted.contains(player.getUniqueId()) && player.getAllowFlight()) return;
        if (!onGround(player)) return;
        refresh(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> refresh(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        // Creative and spectator handle flight themselves: let go of it, and pick it up again afterwards.
        granted.remove(player.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> refresh(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        revoke(event.getPlayer());
        runners.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Double jump would be cheating in parkour. ajParkour has no placeholder that says "in a run" (its
     * score placeholder shows 0 outside a run too), so its own Manager#inParkour(Player) is asked,
     * found by name so Lobby doesn't need ajParkour to build or run.
     */
    private void checkParkour() {
        if (!findParkourCheck()) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            boolean running;
            try {
                running = Boolean.TRUE.equals(inParkour.invoke(parkourManager.get(), player));
            } catch (ReflectiveOperationException | RuntimeException e) {
                plugin.getLogger().warning("Couldn't ask ajParkour who is in a run; double jump stays on during parkour: " + e);
                parkourManager = null;
                parkourChecked = true;
                return;
            }
            boolean changed = running ? runners.add(player.getUniqueId()) : runners.remove(player.getUniqueId());
            if (changed) refresh(player);
        }
    }

    /** Gets ajParkour's Manager (its static getInstance(), or the main class's "man" field in older versions). */
    private java.util.function.Supplier<Object> parkourManager;
    private java.lang.reflect.Method inParkour;
    private boolean parkourChecked;

    /** Looks up ajParkour's Manager.getInstance() and Manager#inParkour(Player) once, if ajParkour is there. */
    private boolean findParkourCheck() {
        if (parkourManager != null) return true;
        if (parkourChecked) return false;
        org.bukkit.plugin.Plugin ajParkour = null;
        for (org.bukkit.plugin.Plugin other : plugin.getServer().getPluginManager().getPlugins()) {
            if (other.getName().equalsIgnoreCase("ajParkour") && other.isEnabled()) ajParkour = other;
        }
        if (ajParkour == null) return false; // may still be loading: try again next time
        parkourChecked = true;
        try {
            Class<?> manager = Class.forName("us.ajg0702.parkour.game.Manager", true, ajParkour.getClass().getClassLoader());
            inParkour = manager.getMethod("inParkour", Player.class);
            java.lang.reflect.Method getInstance = null;
            try {
                getInstance = manager.getMethod("getInstance");
            } catch (NoSuchMethodException older) {
                // fall back to the field below
            }
            java.lang.reflect.Method instance = getInstance;
            java.lang.reflect.Field field = instance == null ? ajParkour.getClass().getField("man") : null;
            org.bukkit.plugin.Plugin owner = ajParkour;
            parkourManager = () -> {
                try {
                    return instance != null ? instance.invoke(null) : field.get(owner);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            };
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("This ajParkour version has no Manager#inParkour; double jump stays on during parkour.");
            return false;
        }
    }
}
