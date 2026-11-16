/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/**
 * The look of Lobby's menus with the resource pack (FractalLobby.zip): a background drawn in the
 * title instead of black panes, and the pack's icons on the buttons. menus.custom-gui in config.yml
 * turns it on or off (realm-selector.custom-gui for the realm selector).
 */
public final class MenuStyle {

    /** The backgrounds, by menu (the pack's fractal:lobby font). */
    public static final char SETTINGS = '', IGNORED = '', PARKOUR = '';

    public static char chatColor(int rows) {
        return (char) (0xF180 + rows);
    }

    public static char trails(int rows) {
        return (char) (0xF190 + rows);
    }

    private final Lobby plugin;

    public MenuStyle(Lobby plugin) {
        this.plugin = plugin;
    }

    /** Whether the menus use the resource pack's look. */
    public boolean custom() {
        var config = plugin.getConfig();
        return config.getBoolean("menus.custom-gui", config.getBoolean("settings-menu.custom-gui", true));
    }

    /** The menu title: the background, or the normal title without the custom look. */
    public Component title(char background, Component normal) {
        return custom() ? RealmSelector.backgroundTitle(background) : normal;
    }

    /** What fills empty slots: black panes, or nothing on the custom background. */
    public ItemStack filler() {
        if (custom()) return null;
        ItemStack filler = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.setHideTooltip(true));
        return filler;
    }

    /** With the custom look, gives the item the pack's icon fractal:menu/NAME. */
    public ItemStack icon(ItemStack item, String name) {
        if (custom() && item != null) item.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", "menu/" + name)));
        return item;
    }
}
