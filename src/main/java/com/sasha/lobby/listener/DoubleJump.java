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

    public DoubleJump(Lobby plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Parkour runs switch double jump off themselves (ParkourGame refreshes the player).
    }

    /** Takes double jump away from everyone (plugin disabling). */
    public void stop() {
        for (Player player : plugin.getServer().getOnlinePlayers()) revoke(player);
    }

    private boolean allowed(Player player) {
        LobbyConfig.DoubleJump config = plugin.lobbyConfig().doubleJump();
        GameMode mode = player.getGameMode();
        return config.enabled() && player.hasPermission(PERMISSION)
                && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && player.getVehicle() == null && !(plugin.parkour() != null && plugin.parkour().game().playing(player))
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
    }

}
