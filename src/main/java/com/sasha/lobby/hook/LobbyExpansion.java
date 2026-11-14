/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.hook;

import com.sasha.lobby.Lobby;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI placeholders with the same player counts as the Lobby scoreboard, so holograms and
 * other plugins always agree with it. Only loaded when PlaceholderAPI is installed.
 *
 * <pre>
 * %lobby_online_all%       players on the whole network
 * %lobby_online_&lt;server&gt;%  players on one proxy server, e.g. %lobby_online_survival%
 * %lobby_online_here%      players on this server
 * </pre>
 */
public final class LobbyExpansion extends PlaceholderExpansion {

    private final Lobby plugin;

    public LobbyExpansion(Lobby plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "lobby";
    }

    @Override
    public String getAuthor() {
        return "Sasha Wildfang";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (!params.startsWith("online_")) return null;
        String server = params.substring("online_".length());
        if (server.equalsIgnoreCase("here")) return String.valueOf(plugin.getServer().getOnlinePlayers().size());
        return String.valueOf(plugin.proxyCounts().count(server));
    }
}
