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
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

/**
 * A square border around spawn. Each player gets their own visible border wall (the server's real world
 * border is untouched), and walking past it bounces them back in. Players with the bypass permission
 * can leave, e.g. to build.
 */
public final class BorderListener implements Listener {

    /** Further out than this past the edge (e.g. after a teleport), players are sent to spawn instead. */
    private static final double FAR_OUTSIDE = 8;

    private final Lobby plugin;

    public BorderListener(Lobby plugin) {
        this.plugin = plugin;
    }

    private LobbyConfig.Border border() {
        return plugin.lobbyConfig().border();
    }

    private boolean applies(Player player) {
        LobbyConfig.Border border = border();
        return border.enabled() && !player.hasPermission(border.bypassPermission())
                && player.getWorld().getName().equals(plugin.lobbyConfig().spawn().world());
    }

    /** Shows (or removes) the player's border wall. Called on join, world change and /lobby reload. */
    public void refresh(Player player) {
        if (!applies(player) || !border().showWall()) {
            player.setWorldBorder(null);
            return;
        }
        Location center = plugin.lobbyConfig().spawn().location();
        WorldBorder wall = plugin.getServer().createWorldBorder();
        wall.setCenter(center.getX(), center.getZ());
        wall.setSize(border().radius() * 2.0);
        wall.setWarningDistance(0);
        player.setWorldBorder(wall);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        Player player = event.getPlayer();
        if (player.getVehicle() != null || !applies(player)) return; // flying minecarts keep themselves inside

        Location center = plugin.lobbyConfig().spawn().location();
        double past = outside(event.getTo(), center, border().radius());
        if (past <= 0) return;

        if (past > FAR_OUTSIDE) {
            event.setTo(center);
            return;
        }
        // Bounce back toward spawn.
        Vector inward = center.toVector().subtract(event.getTo().toVector()).setY(0).normalize().multiply(0.9).setY(0.35);
        event.setTo(event.getFrom());
        player.setVelocity(inward);
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.05);
        player.playSound(Sound.sound(Key.key("block.amethyst_block.chime"), Sound.Source.PLAYER, 0.8f, 1.2f), Sound.Emitter.self());
        plugin.messages().actionBar(player, "border");
    }

    /** How far past the square border a location is (0 or less = inside). */
    public static double outside(Location at, Location center, double radius) {
        double dx = Math.abs(at.getX() - center.getX());
        double dz = Math.abs(at.getZ() - center.getZ());
        return Math.max(dx, dz) - radius;
    }
}
