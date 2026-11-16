/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.hook;

import com.sasha.lobby.Lobby;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Placeholders the Lobby fills in itself, so PlaceholderAPI isn't needed for them:
 * %lobby_online_SERVER% (also all, here), %luckperms_prefix%, %luckperms_suffix%, %player_name%, %player_ping%,
 * %server_online%. Anything else is passed to PlaceholderAPI when it's installed.
 */
public final class Placeholders {

    private static final Pattern ONLINE = Pattern.compile("%lobby_online_([A-Za-z0-9_.\\-]+)%");

    private Placeholders() {
    }

    public static String apply(Lobby plugin, Player player, String text) {
        if (text.indexOf('%') < 0) return text;
        String out = text;
        if (out.contains("%lobby_online_")) {
            Matcher m = ONLINE.matcher(out);
            StringBuilder sb = new StringBuilder();
            while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(online(plugin, m.group(1))));
            m.appendTail(sb);
            out = sb.toString();
        }
        if (player != null) {
            if (plugin.luckPerms()) {
                if (out.contains("%luckperms_prefix%")) out = out.replace("%luckperms_prefix%", LuckPermsHook.prefix(player));
                if (out.contains("%luckperms_suffix%")) out = out.replace("%luckperms_suffix%", LuckPermsHook.suffix(player));
            }
            out = out.replace("%player_name%", player.getName()).replace("%player_ping%", String.valueOf(player.getPing()));
        }
        out = out.replace("%server_online%", String.valueOf(plugin.getServer().getOnlinePlayers().size()));
        if (out.indexOf('%') >= 0 && plugin.placeholderApi()) out = PlaceholderHook.apply(player, out);
        return out;
    }

    /** Players on a realm (from the proxy), "all" for the whole network, "here" for this server. */
    public static String online(Lobby plugin, String server) {
        if (server.equalsIgnoreCase("here")) return String.valueOf(plugin.getServer().getOnlinePlayers().size());
        return String.valueOf(plugin.proxyCounts().count(server));
    }
}
