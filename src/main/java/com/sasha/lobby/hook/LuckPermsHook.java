/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.hook;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.entity.Player;

/** Rank prefixes and suffixes straight from LuckPerms. Only called when LuckPerms is installed. */
public final class LuckPermsHook {

    private LuckPermsHook() {
    }

    public static String prefix(Player player) {
        String prefix = meta(player).getPrefix();
        return prefix == null ? "" : prefix;
    }

    public static String suffix(Player player) {
        String suffix = meta(player).getSuffix();
        return suffix == null ? "" : suffix;
    }

    private static CachedMetaData meta(Player player) {
        return LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(player);
    }
}
