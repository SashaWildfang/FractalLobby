/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import com.sasha.lobby.config.Messages;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Punching staff: players with lobby.punch can hit a staff member to launch them into the air (it never
 * hurts, PvP stays off). Each puncher has a cooldown. Staff with lobby.punch.immune can't be punched.
 */
public final class PunchListener implements Listener {

    public static final String PERMISSION = "lobby.punch";
    public static final String IMMUNE = "lobby.punch.immune";

    private final Lobby plugin;
    private final Map<UUID, Long> lastPunch = new HashMap<>();

    public PunchListener(Lobby plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPunch(PrePlayerAttackEntityEvent event) {
        LobbyConfig.Punch config = plugin.lobbyConfig().punch();
        if (!config.enabled() || !(event.getAttacked() instanceof Player target)) return;
        Player puncher = event.getPlayer();
        if (!puncher.hasPermission(PERMISSION) || target.hasPermission(IMMUNE)) return;
        if (config.staffOnly() && !plugin.settings().isStaff(target)) return;
        if (target.getVehicle() != null || puncher.getVehicle() != null) return;
        event.setCancelled(true); // no damage, no knockback of its own: the launch below is the punch

        Messages messages = plugin.messages();
        long now = System.currentTimeMillis();
        long readyAt = lastPunch.getOrDefault(puncher.getUniqueId(), 0L) + config.cooldownMillis();
        if (now < readyAt) {
            long seconds = (readyAt - now + 999) / 1000;
            messages.actionBar(puncher, "punch.cooldown", Messages.arg("seconds", seconds));
            return;
        }
        lastPunch.put(puncher.getUniqueId(), now);

        // Up and away from the puncher.
        Vector away = target.getLocation().toVector().subtract(puncher.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 1.0E-4) away = puncher.getLocation().getDirection().setY(0);
        if (away.lengthSquared() > 0) away.normalize();
        target.setVelocity(away.multiply(config.backward()).setY(config.upward()));
        target.setFallDistance(0);

        target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation().add(0, 1, 0), 1);
        target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation(), 15, 0.4, 0.1, 0.4, 0.05);
        if (!config.sound().isEmpty()) {
            try {
                target.getWorld().playSound(Sound.sound(Key.key(config.sound()), Sound.Source.PLAYER, 1f, 1f),
                        target.getX(), target.getY(), target.getZ());
            } catch (RuntimeException e) {
                plugin.getLogger().warning("config.yml punch.sound '" + config.sound() + "' is not a sound name");
            }
        }
        var names = net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                Messages.arg("puncher", puncher.getName()), Messages.arg("target", target.getName()));
        if (config.announce()) {
            for (Player viewer : plugin.getServer().getOnlinePlayers()) messages.send(viewer, "punch.announce", names);
        } else {
            messages.send(puncher, "punch.you-punched", names);
            messages.send(target, "punch.punched", names);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastPunch.remove(event.getPlayer().getUniqueId());
    }
}
