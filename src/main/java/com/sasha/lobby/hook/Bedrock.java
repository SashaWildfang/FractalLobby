package com.sasha.lobby.hook;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Bedrock players (joined through Geyser and Floodgate).
 *
 * <p>Floodgate is looked up by reflection, so the plugin still loads without it. Without Floodgate, a
 * Bedrock player is told apart by Floodgate's UUID form (the top 64 bits are zero).
 */
public final class Bedrock {
    /** The icon Bedrock players see in place of a player head (see {@link #head}). */
    public static final Key PLAYER_ICON = Key.key("fractal", "menu/player");

    private static Object api;
    private static Method isFloodgatePlayer;
    private static boolean looked;

    private Bedrock() {
    }

    public static boolean is(Player player) {
        return player != null && is(player.getUniqueId());
    }

    public static boolean is(UUID id) {
        if (!looked) {
            looked = true;
            try {
                Class<?> type = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                api = type.getMethod("getInstance").invoke(null);
                isFloodgatePlayer = type.getMethod("isFloodgatePlayer", UUID.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                api = null; // no Floodgate: fall back to the UUID form below
            }
        }
        if (api != null) {
            try {
                return (boolean) isFloodgatePlayer.invoke(api, id);
            } catch (ReflectiveOperationException ignored) {
                // fall through
            }
        }
        return id.getMostSignificantBits() == 0;
    }

    /**
     * A player head as the viewer should see it. Bedrock can't draw a Java skin on a head in a menu (every
     * head shows as Steve), so for a Bedrock viewer the head gets the {@code fractal:menu/player} icon instead;
     * the Bedrock pack maps it on player heads. Java viewers get the head unchanged.
     */
    public static ItemStack head(Player viewer, ItemStack head) {
        if (head != null && is(viewer)) head.setData(DataComponentTypes.ITEM_MODEL, PLAYER_ICON);
        return head;
    }
}
