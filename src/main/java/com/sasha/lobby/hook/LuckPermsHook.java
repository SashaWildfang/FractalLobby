/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.hook;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
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

    /** The weight of the player's primary group (their highest rank by default), or 0. */
    public static int weight(Player player) {
        String primary = meta(player).getPrimaryGroup();
        if (primary == null) return 0;
        Group group = LuckPermsProvider.get().getGroupManager().getGroup(primary);
        return group == null ? 0 : group.getWeight().orElse(0);
    }

    /**
     * Whether the player was given exactly this permission (directly or through their groups), not just through a
     * wildcard like * or lobby.*.
     */
    public static boolean hasExactly(Player player, String permission) {
        var user = LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
        if (user == null) return false;
        return Boolean.TRUE.equals(user.getCachedData().getPermissionData().getPermissionMap().get(permission));
    }

    /** A group's weight (its place in the rank order), or -1 if there's no such group. */
    public static int groupWeight(String name) {
        Group group = LuckPermsProvider.get().getGroupManager().getGroup(name.toLowerCase(java.util.Locale.ROOT));
        return group == null ? -1 : group.getWeight().orElse(0);
    }

    /** Prefixes by lower-case name, for players who may be offline (leaderboards). */
    private static final java.util.Map<String, Cached> BY_NAME = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long KEEP_MILLIS = 30 * 60 * 1000;

    private record Cached(String prefix, long at) {
    }

    /**
     * A player's rank prefix by name, online or not. Offline players are looked up in the background, so
     * the first call can return "" and the next ones the prefix (kept 5 minutes).
     */
    public static String prefix(String name) {
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayerExact(name);
        if (online != null) return prefix(online);
        String key = name.toLowerCase(java.util.Locale.ROOT);
        Cached cached = BY_NAME.get(key);
        long now = System.currentTimeMillis();
        if (cached == null || now - cached.at() > KEEP_MILLIS) {
            BY_NAME.put(key, new Cached(cached == null ? "" : cached.prefix(), now)); // one lookup at a time
            var users = LuckPermsProvider.get().getUserManager();
            users.lookupUniqueId(name)
                    .thenCompose(id -> id == null ? java.util.concurrent.CompletableFuture.completedFuture(null) : users.loadUser(id))
                    .thenAccept(user -> {
                        if (user == null) return; // not found this time: keep what we had
                        String prefix = user.getCachedData().getMetaData().getPrefix();
                        BY_NAME.put(key, new Cached(prefix == null ? "" : prefix, System.currentTimeMillis()));
                    })
                    .exceptionally(error -> null);
        }
        return cached == null ? "" : cached.prefix();
    }

    /** Remembers an online player's prefix by name (when they leave), so leaderboards have it straight away. */
    public static void remember(Player player) {
        BY_NAME.put(player.getName().toLowerCase(java.util.Locale.ROOT), new Cached(prefix(player), System.currentTimeMillis()));
    }

    private static CachedMetaData meta(Player player) {
        return LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(player);
    }
}
