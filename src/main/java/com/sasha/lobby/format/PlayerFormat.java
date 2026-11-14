/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.format;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.hook.LuckPermsHook;
import com.sasha.lobby.hook.PlaceholderHook;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Renders formats about a player (chat, join messages, nametags, tab names). In a format:
 * <ul>
 *   <li>%player% is the player's name</li>
 *   <li>%luckperms_prefix% / %luckperms_suffix% come from LuckPerms (no PlaceholderAPI needed)</li>
 *   <li>any other %placeholder% is filled in by PlaceholderAPI when it's installed</li>
 *   <li>&amp; colour codes and MiniMessage tags both work, and the theme tags too</li>
 * </ul>
 */
public final class PlayerFormat {

    private final Lobby plugin;

    public PlayerFormat(Lobby plugin) {
        this.plugin = plugin;
    }

    /** Renders a format for a player. */
    public Component render(Player player, String format) {
        return plugin.messages().render(prepare(player, format), Messages.arg("player", player.getName()));
    }

    /** The player's rank prefix, or "" when they have none (or LuckPerms/PlaceholderAPI isn't installed). */
    public String prefix(Player player) {
        if (plugin.luckPerms()) return LuckPermsHook.prefix(player);
        if (plugin.placeholderApi()) {
            String value = PlaceholderHook.apply(player, "%luckperms_prefix%");
            return value.equals("%luckperms_prefix%") ? "" : value;
        }
        return "";
    }

    /** The weight of the player's LuckPerms rank (higher = more important), or 0 without LuckPerms. */
    public int weight(Player player) {
        return plugin.luckPerms() ? LuckPermsHook.weight(player) : 0;
    }

    /**
     * Fills in placeholders, giving a MiniMessage template where the player's name is the &lt;player&gt; tag
     * (inserted as plain text, so a name can never inject formatting).
     */
    public String prepare(Player player, String format) {
        String text = format;
        if (plugin.luckPerms()) {
            if (text.contains("%luckperms_prefix%")) text = text.replace("%luckperms_prefix%", LuckPermsHook.prefix(player));
            if (text.contains("%luckperms_suffix%")) text = text.replace("%luckperms_suffix%", LuckPermsHook.suffix(player));
        }
        text = text.replace("%player%", "<player>");
        if (plugin.placeholderApi() && text.indexOf('%') >= 0) text = PlaceholderHook.apply(player, text);
        return text;
    }
}
