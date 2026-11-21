/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * /fly in the lobby (lobby.cmd.fly). Flight is switched off again when the player leaves; double jump
 * steps aside while it's on, and comes back when it's off. No flying during a parkour run.
 */
public final class FlyCommand implements Listener {

    public static final String PERMISSION = "lobby.cmd.fly";

    private final Lobby plugin;
    private final Set<UUID> flying = new HashSet<>();

    public FlyCommand(Lobby plugin) {
        this.plugin = plugin;
    }

    public boolean on(Player player) {
        return flying.contains(player.getUniqueId());
    }

    public void toggle(Player player) {
        if (flying.remove(player.getUniqueId())) {
            player.setFlying(false);
            player.setAllowFlight(false);
            player.setFallDistance(0);
            plugin.doubleJump().refresh(player); // double jump comes back
            plugin.messages().send(player, "fly.off");
            return;
        }
        if (plugin.parkour() != null && plugin.parkour().game().playing(player)) {
            plugin.messages().send(player, "fly.parkour");
            return;
        }
        plugin.doubleJump().release(player); // flight is /fly's now, not double jump's
        flying.add(player.getUniqueId());
        player.setAllowFlight(true);
        plugin.messages().send(player, "fly.on");
    }

    /** No flying during a parkour run. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFly(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying() || !on(player)) return;
        if (plugin.parkour() != null && plugin.parkour().game().playing(player)) {
            event.setCancelled(true);
            plugin.messages().send(player, "fly.parkour");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        flying.remove(event.getPlayer().getUniqueId());
    }
}
