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

    /**
     * Glow is a rank perk: players whose rank is glow.min-rank (Guardian by default) or higher in LuckPerms'
     * rank order (group weight), or anyone with lobby.cmd.glow. It can't be found in Mystery Boxes.
     */
    public static boolean allowed(Player player) {
        if (player.hasPermission(PERMISSION)) return true;
        Lobby lobby = org.bukkit.plugin.java.JavaPlugin.getPlugin(Lobby.class);
        if (!lobby.luckPerms()) return false;
        String minRank = lobby.getConfig().getString("glow.min-rank", "guardian").trim();
        if (minRank.isEmpty()) return false;
        int needed = com.sasha.lobby.hook.LuckPermsHook.groupWeight(minRank);
        return needed >= 0 && com.sasha.lobby.hook.LuckPermsHook.weight(player) >= needed;
    }

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
        player.setGlowing(glow && allowed(player));
    }
}
