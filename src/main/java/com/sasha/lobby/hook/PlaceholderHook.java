/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/** PlaceholderAPI support. Only called when PlaceholderAPI is installed, since it uses its classes. */
public final class PlaceholderHook {

    private PlaceholderHook() {
    }

    public static String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
