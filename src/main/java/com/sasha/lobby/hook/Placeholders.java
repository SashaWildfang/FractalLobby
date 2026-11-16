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
    private static final Pattern STATUS = Pattern.compile("%lobby_(status|action)_([A-Za-z0-9_.\\-]+)%");

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
        if (out.contains("%lobby_status_") || out.contains("%lobby_action_")) {
            Matcher m = STATUS.matcher(out);
            StringBuilder sb = new StringBuilder();
            while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(realm(plugin, m.group(1), m.group(2))));
            m.appendTail(sb);
            out = sb.toString();
        }
        if (player != null) {
            if (plugin.luckPerms()) {
                if (out.contains("%luckperms_prefix%")) out = out.replace("%luckperms_prefix%", LuckPermsHook.prefix(player));
                if (out.contains("%luckperms_suffix%")) out = out.replace("%luckperms_suffix%", LuckPermsHook.suffix(player));
            }
            if (out.contains("%lobby_dust%") || out.contains("%lobby_boxes%")) {
                var mystery = plugin.mystery();
                var stash = mystery == null ? null : mystery.stash(player.getUniqueId());
                out = out.replace("%lobby_dust%", stash == null ? "0" : String.format(java.util.Locale.ROOT, "%,d", stash.dust()))
                        .replace("%lobby_boxes%", stash == null ? "0" : String.valueOf(stash.totalBoxes()));
            }
            if (out.contains("%lobby_parkour_")) {
                var board = plugin.parkour().leaderboard();
                int rank = board.rank(player);
                out = out.replace("%lobby_parkour_best%", board.best(player))
                        .replace("%lobby_parkour_rank%", rank > 0 ? "#" + rank : plugin.messages().raw("parkour.unranked"));
            }
            out = out.replace("%player_name%", player.getName()).replace("%player_ping%", String.valueOf(player.getPing()));
        }
        out = out.replace("%server_online%", String.valueOf(plugin.getServer().getOnlinePlayers().size()));
        if (out.indexOf('%') >= 0 && plugin.placeholderApi()) out = PlaceholderHook.apply(player, out);
        return out;
    }

    /**
     * For holograms and NPC labels: %lobby_status_REALM% says how a realm is doing ("● 12 playing now", full,
     * maintenance, restarting, offline, coming soon) and %lobby_action_REALM% what a click does then
     * (messages.yml realm-status and realm-action).
     */
    public static String realm(Lobby plugin, String which, String server) {
        String state = state(plugin, server);
        return plugin.messages().raw("realm-" + which + "." + state).replace("<count>", online(plugin, server));
    }

    /** online, full, maintenance, restarting, offline or coming-soon. */
    public static String state(Lobby plugin, String server) {
        if (comingSoon(plugin, server)) return "coming-soon";
        return switch (plugin.serverStatus().status(server).state()) {
            case FULL -> "full";
            case MAINTENANCE -> "maintenance";
            case OFFLINE -> plugin.serverStatus().restarting(server) ? "restarting" : "offline";
            default -> "online";
        };
    }

    /** A realm marked coming-soon: true in the realm selector (config.yml). */
    private static boolean comingSoon(Lobby plugin, String server) {
        var realms = plugin.getConfig().getConfigurationSection("realm-selector.realms");
        if (realms == null) return false;
        for (String id : realms.getKeys(false)) {
            if (server.equalsIgnoreCase(realms.getString(id + ".server", id))) return realms.getBoolean(id + ".coming-soon", false);
        }
        return false;
    }

    /** Players on a realm (from the proxy), "all" for the whole network, "here" for this server. */
    public static String online(Lobby plugin, String server) {
        if (server.equalsIgnoreCase("here")) return String.valueOf(plugin.getServer().getOnlinePlayers().size());
        return String.valueOf(plugin.proxyCounts().count(server));
    }
}
