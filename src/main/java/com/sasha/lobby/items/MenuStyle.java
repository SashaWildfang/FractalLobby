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

    /**
     * Whether this player sees the pack's menu backgrounds. Bedrock players don't: Bedrock can't draw a
     * picture in a menu title, so they get the normal titles and grey panes (the item icons still work).
     */
    public boolean backgrounds(org.bukkit.entity.Player viewer) {
        return custom() && !com.sasha.lobby.hook.Bedrock.is(viewer);
    }

    /** The menu title: the background, or the normal title without the custom look (and on Bedrock). */
    public Component title(org.bukkit.entity.Player viewer, char background, Component normal) {
        return backgrounds(viewer) ? RealmSelector.backgroundTitle(background) : normal;
    }

    /** What fills empty slots: nothing on the custom background, grey panes on Bedrock, black panes otherwise. */
    public ItemStack filler(org.bukkit.entity.Player viewer) {
        if (backgrounds(viewer)) return null;
        return pane(com.sasha.lobby.hook.Bedrock.is(viewer));
    }

    /** Older form (FractalCosmetics 1.0.x): the custom look for everyone. Use the forms with the viewer. */
    @Deprecated
    public Component title(char background, Component normal) {
        return custom() ? RealmSelector.backgroundTitle(background) : normal;
    }

    /** Older form (FractalCosmetics 1.0.x). Use {@link #filler(org.bukkit.entity.Player)}. */
    @Deprecated
    public ItemStack filler() {
        return custom() ? null : pane(false);
    }

    /** A pane with no tooltip: grey for Bedrock, black otherwise. */
    public static ItemStack pane(boolean bedrock) {
        ItemStack filler = ItemStack.of(bedrock ? Material.GRAY_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE);
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

    /** The gold padlock: not unlocked yet, but the player has the Mystery Dust to buy it now. */
    public ItemStack lockedAffordable(ItemStack item) {
        return model(item, "menu/locked_buy");
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
