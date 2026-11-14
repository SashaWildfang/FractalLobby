/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's lobby settings (the comparator join item). Some choose whose messages or players to see
 * (Everyone, Donors Only, Staff Only, Nobody); the rest are On or Off. Clicking a setting moves to its
 * next option (right-click goes back). Saved on the player.
 */
public final class Settings implements Listener {

    /** Who a player sees something from. Staff count as donors. */
    public enum Audience {
        EVERYONE, DONORS, STAFF, NOBODY
    }

    /** The two kinds of setting: a choice of audience, or on/off. */
    private enum Kind {
        AUDIENCE(List.of("everyone", "donors", "staff", "nobody")),
        TOGGLE(List.of("on", "off"));

        final List<String> options;

        Kind(List<String> options) {
            this.options = options;
        }
    }

    /** One setting: its id in messages.yml, its icon, its menu slot and its options. */
    public enum Setting {
        JOIN_MESSAGES("join-messages", Material.OAK_DOOR, 10, Kind.AUDIENCE),
        LEAVE_MESSAGES("leave-messages", Material.IRON_DOOR, 11, Kind.AUDIENCE),
        CHAT("chat", Material.WRITABLE_BOOK, 12, Kind.AUDIENCE),
        PLAYER_VISIBILITY("player-visibility", Material.ENDER_EYE, 13, Kind.AUDIENCE),
        MENTIONS("mentions", Material.NOTE_BLOCK, 14, Kind.TOGGLE),
        DOUBLE_JUMP("double-jump", Material.FEATHER, 15, Kind.TOGGLE),
        SCOREBOARD("scoreboard", Material.PAINTING, 16, Kind.TOGGLE);

        final String id;
        final Material icon;
        final int slot;
        final Kind kind;

        Setting(String id, Material icon, int slot, Kind kind) {
            this.id = id;
            this.icon = icon;
            this.slot = slot;
            this.kind = kind;
        }
    }

    private final Lobby plugin;
    private final Map<Setting, NamespacedKey> keys = new EnumMap<>(Setting.class);
    /** Before settings had options, hiding players was its own on/off flag. */
    private final NamespacedKey oldHiddenKey;
    /** Each online player's choices (also read from chat threads, hence concurrent). */
    private final Map<UUID, Map<Setting, String>> cache = new ConcurrentHashMap<>();

    public Settings(Lobby plugin) {
        this.plugin = plugin;
        for (Setting setting : Setting.values()) keys.put(setting, new NamespacedKey(plugin, "setting-" + setting.id));
        oldHiddenKey = new NamespacedKey(plugin, "players-hidden");
    }

    // ---------------------------------------------------------------- reading

    private String option(Player player, Setting setting) {
        Map<Setting, String> choices = cache.get(player.getUniqueId());
        if (choices == null) choices = load(player);
        return choices.getOrDefault(setting, setting.kind.options.getFirst());
    }

    /** For on/off settings: whether it's on. */
    public boolean on(Player player, Setting setting) {
        return !option(player, setting).equals("off");
    }

    /** For audience settings: who the player sees it from. */
    public Audience audience(Player player, Setting setting) {
        return switch (option(player, setting)) {
            case "donors" -> Audience.DONORS;
            case "staff" -> Audience.STAFF;
            case "nobody" -> Audience.NOBODY;
            default -> Audience.EVERYONE;
        };
    }

    /** Whether the viewer sees the subject's messages or self, by the viewer's choice for this setting. */
    public boolean shows(Player viewer, Setting setting, Player subject) {
        if (viewer.equals(subject)) return true;
        return switch (audience(viewer, setting)) {
            case EVERYONE -> true;
            case DONORS -> isDonor(subject);
            case STAFF -> isStaff(subject);
            case NOBODY -> false;
        };
    }

    public boolean isStaff(Player player) {
        return player.hasPermission(plugin.lobbyConfig().ranks().staffPermission());
    }

    /** Donors, and staff too. */
    public boolean isDonor(Player player) {
        return isStaff(player) || player.hasPermission(plugin.lobbyConfig().ranks().donorPermission());
    }

    private Map<Setting, String> load(Player player) {
        Map<Setting, String> choices = new ConcurrentHashMap<>();
        for (Setting setting : Setting.values()) {
            var data = player.getPersistentDataContainer();
            NamespacedKey key = keys.get(setting);
            String value = null;
            if (data.has(key, PersistentDataType.STRING)) {
                value = data.get(key, PersistentDataType.STRING);
            } else if (data.has(key, PersistentDataType.BOOLEAN)) {
                // Earlier versions saved these as true/false: true = everyone/on, false = nobody/off.
                value = Boolean.TRUE.equals(data.get(key, PersistentDataType.BOOLEAN))
                        ? setting.kind.options.getFirst() : setting.kind.options.getLast();
            }
            if (value == null && setting == Setting.PLAYER_VISIBILITY
                    && player.getPersistentDataContainer().getOrDefault(oldHiddenKey, PersistentDataType.BOOLEAN, false)) {
                value = "nobody";
            }
            if (value != null && setting.kind.options.contains(value)) choices.put(setting, value);
        }
        cache.put(player.getUniqueId(), choices);
        return choices;
    }

    // ---------------------------------------------------------------- changing

    /** Sets a setting and applies it straight away. */
    public void set(Player player, Setting setting, String value) {
        player.getPersistentDataContainer().remove(keys.get(setting)); // drops an old true/false value too
        player.getPersistentDataContainer().set(keys.get(setting), PersistentDataType.STRING, value);
        Map<Setting, String> choices = cache.get(player.getUniqueId());
        if (choices == null) choices = load(player);
        choices.put(setting, value);

        switch (setting) {
            case SCOREBOARD -> plugin.display().showSidebar(player, on(player, setting));
            case PLAYER_VISIBILITY -> plugin.joinItems().applyVisibility(player);
            case DOUBLE_JUMP -> plugin.doubleJump().refresh(player);
            default -> {
            }
        }
    }

    /** Moves a setting to its next option (or the one before). */
    private void cycle(Player player, Setting setting, boolean back) {
        List<String> options = setting.kind.options;
        int index = options.indexOf(option(player, setting));
        int next = Math.floorMod(index + (back ? -1 : 1), options.size());
        set(player, setting, options.get(next));
        player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, 0.8f + next * 0.15f),
                Sound.Emitter.self());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- menu

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Each setting as an icon listing its options (the current one marked), with a coloured pane under it. */
    public void open(Player player) {
        Messages messages = plugin.messages();
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, 36, messages.component("settings.title"));
        ItemStack filler = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.setHideTooltip(true));
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);

        for (Setting setting : Setting.values()) {
            String current = option(player, setting);
            List<Component> lore = new ArrayList<>(messages.lore("settings." + setting.id + ".lore"));
            lore.add(Component.empty());
            for (String option : setting.kind.options) {
                Component name = messages.component("settings.options." + option);
                lore.add(messages.item(option.equals(current) ? "settings.option-selected" : "settings.option",
                        Placeholder.component("option", name)));
            }
            lore.add(Component.empty());
            lore.addAll(messages.lore("settings.hint"));

            ItemStack icon = ItemStack.of(setting.icon);
            icon.editMeta(meta -> {
                meta.displayName(messages.item("settings." + setting.id + ".name"));
                meta.lore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            });
            menu.inventory.setItem(setting.slot, icon);

            ItemStack pane = ItemStack.of(paneFor(current));
            pane.editMeta(meta -> {
                meta.displayName(messages.item("settings.current",
                        Placeholder.component("option", messages.component("settings.options." + current))));
                meta.lore(messages.lore("settings.hint"));
            });
            menu.inventory.setItem(setting.slot + 9, pane);
        }
        player.openInventory(menu.inventory);
    }

    private static Material paneFor(String option) {
        return switch (option) {
            case "everyone", "on" -> Material.LIME_STAINED_GLASS_PANE;
            case "donors" -> Material.YELLOW_STAINED_GLASS_PANE;
            case "staff" -> Material.ORANGE_STAINED_GLASS_PANE;
            default -> Material.RED_STAINED_GLASS_PANE;
        };
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        for (Setting setting : Setting.values()) {
            if (event.getRawSlot() == setting.slot || event.getRawSlot() == setting.slot + 9) {
                cycle(player, setting, event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT);
                open(player); // redraw with the new choice
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }
}
