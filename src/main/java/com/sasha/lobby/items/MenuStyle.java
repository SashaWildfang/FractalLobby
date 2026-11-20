/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.List;
import org.bukkit.inventory.ItemStack;

/**
 * The look of Lobby's menus with the resource pack (FractalLobby.zip): a background drawn in the
 * title instead of black panes, and the pack's icons on the buttons. menus.custom-gui in config.yml
 * turns it on or off (realm-selector.custom-gui for the realm selector).
 */
public final class MenuStyle {

    /** The backgrounds, by menu (the pack's fractal:lobby font). */
    public static final char SETTINGS = '\uF170', IGNORED = '\uF171', PARKOUR = '\uF1A5', PROFILE = '\uF1C4';

    /** Chat colours and trails the pack has an icon for (by their id in config.yml). */
    private static final java.util.Set<String> COLOR_ICONS = java.util.Set.of("white", "gray", "red", "orange", "yellow",
            "green", "aqua", "blue", "purple", "lavender", "pink", "fractal", "sunset", "ocean", "rainbow");
    private static final java.util.Set<String> TRAIL_ICONS = java.util.Set.of("notes", "clouds", "sparkles", "hearts",
            "snowflakes", "flames", "soul-flames", "magic", "stars", "enchanted", "blossom", "sparks", "totem");

    /** The pack's status emojis, in the order of their glyphs (U+F300 on); never reorder (build_pack.py EMOJIS). */
    public static final List<String> EMOJIS = List.of("angel", "angry", "begging", "clown", "cool", "crying", "dead",
            "evil", "eyes", "happy", "heart", "joy", "kiss", "love", "melt", "mindblown", "money", "poop", "sad", "scream",
            "skull", "sweat", "thinking", "thumbsup", "wink");

    /** The status menu's background: menu rows (3-6) and how many emojis are in its last row (1-7). */
    public static char status(int rows, int last) {
        return (char) (0xF1D0 + rows * 8 + last);
    }

    /** The emoji's glyph in the fractal:lobby font, or 0 if the pack has no such emoji. */
    public static char emoji(String icon) {
        int i = EMOJIS.indexOf(icon);
        return i < 0 ? 0 : (char) (0xF300 + i);
    }

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
        return model(item, "menu/" + name);
    }

    /** The padlock from the pack, for a cosmetic the player hasn't unlocked yet (with the pack on). */
    public ItemStack locked(ItemStack item) {
        return model(item, "menu/locked");
    }

    /** A chat colour's paint drop from the pack, if it has one for this colour. */
    public ItemStack colorIcon(ItemStack item, String id) {
        return COLOR_ICONS.contains(id) ? model(item, "color/" + id) : item;
    }

    /** A trail's particle icon from the pack, if it has one for this trail. */
    public ItemStack trailIcon(ItemStack item, String id) {
        return TRAIL_ICONS.contains(id) ? model(item, "trail/" + id) : item;
    }

    /** A status's emoji from the pack, if it has one by that name. */
    public ItemStack statusIcon(ItemStack item, String icon) {
        return EMOJIS.contains(icon) ? model(item, "status/" + icon) : item;
    }

    private ItemStack model(ItemStack item, String path) {
        if (custom() && item != null) item.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", path)));
        return item;
    }
}
