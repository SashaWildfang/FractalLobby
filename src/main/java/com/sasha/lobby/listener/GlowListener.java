/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * /glow: an outline around the player, in their rank colour. Minecraft colours a glow by the player's
 * scoreboard team, and Lobby keeps a team per player in their rank colour on every scoreboard, so the
 * sidebar is never touched. The choice is remembered between joins.
 */
public final class GlowListener implements Listener {

    public static final String PERMISSION = "lobby.cmd.glow";

    private final Lobby plugin;
    private final NamespacedKey glowKey;

    public GlowListener(Lobby plugin) {
        this.plugin = plugin;
        this.glowKey = new NamespacedKey(plugin, "glow");
    }

    /** Turns the glow on or off and tells the player. */
    public void toggle(Player player) {
        boolean glow = !player.getPersistentDataContainer().getOrDefault(glowKey, PersistentDataType.BOOLEAN, false);
        player.getPersistentDataContainer().set(glowKey, PersistentDataType.BOOLEAN, glow);
        player.setGlowing(glow);
        plugin.messages().send(player, glow ? "glow.on" : "glow.off");
    }

    /** Glowing doesn't survive a rejoin on its own; put it back if they still have the permission. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean glow = player.getPersistentDataContainer().getOrDefault(glowKey, PersistentDataType.BOOLEAN, false);
        player.setGlowing(glow && player.hasPermission(PERMISSION));
    }
}
