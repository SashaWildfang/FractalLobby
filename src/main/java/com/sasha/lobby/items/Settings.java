/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's lobby settings (the comparator join item): whether they see join messages, leave
 * messages, chat and the scoreboard. Saved on the player; everything is on until they turn it off.
 */
public final class Settings implements Listener {

    /** One setting: its id in messages.yml, its menu icon, and its place in the menu. */
    public enum Setting {
        JOIN_MESSAGES("join-messages", Material.OAK_DOOR, 10),
        LEAVE_MESSAGES("leave-messages", Material.IRON_DOOR, 12),
        CHAT("chat", Material.WRITABLE_BOOK, 14),
        SCOREBOARD("scoreboard", Material.PAINTING, 16);

        final String id;
        final Material icon;
        final int slot;

        Setting(String id, Material icon, int slot) {
            this.id = id;
            this.icon = icon;
            this.slot = slot;
        }
    }

    private final Lobby plugin;
    private final Map<Setting, NamespacedKey> keys = new java.util.EnumMap<>(Setting.class);
    /** The settings each online player turned off (read from chat threads too, hence concurrent). */
    private final Map<UUID, Set<Setting>> off = new ConcurrentHashMap<>();

    public Settings(Lobby plugin) {
        this.plugin = plugin;
        for (Setting setting : Setting.values()) keys.put(setting, new NamespacedKey(plugin, "setting-" + setting.id));
    }

    /** Whether the player has the setting on (it is unless they turned it off). */
    public boolean on(Player player, Setting setting) {
        Set<Setting> disabled = off.get(player.getUniqueId());
        return !(disabled == null ? load(player) : disabled).contains(setting);
    }

    private Set<Setting> load(Player player) {
        Set<Setting> disabled = EnumSet.noneOf(Setting.class);
        for (Setting setting : Setting.values()) {
            if (!player.getPersistentDataContainer().getOrDefault(keys.get(setting), PersistentDataType.BOOLEAN, true)) {
                disabled.add(setting);
            }
        }
        Set<Setting> shared = ConcurrentHashMap.newKeySet();
        shared.addAll(disabled);
        off.put(player.getUniqueId(), shared);
        return shared;
    }

    private void toggle(Player player, Setting setting) {
        boolean turnOn = !on(player, setting);
        player.getPersistentDataContainer().set(keys.get(setting), PersistentDataType.BOOLEAN, turnOn);
        Set<Setting> disabled = off.computeIfAbsent(player.getUniqueId(), id -> ConcurrentHashMap.newKeySet());
        if (turnOn) disabled.remove(setting);
        else disabled.add(setting);
        if (setting == Setting.SCOREBOARD) plugin.display().showSidebar(player, turnOn);

        Messages messages = plugin.messages();
        messages.send(player, turnOn ? "settings.turned-on" : "settings.turned-off",
                Placeholder.component("setting", messages.component("settings." + setting.id + ".name")));
        player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, turnOn ? 1.2f : 0.8f),
                Sound.Emitter.self());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        off.remove(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- menu

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Each setting as an icon, with an on/off pane under it. Clicking either changes it. */
    public void open(Player player) {
        Messages messages = plugin.messages();
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, 36, messages.component("settings.title"));
        ItemStack filler = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.setHideTooltip(true));
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);

        for (Setting setting : Setting.values()) {
            boolean on = on(player, setting);
            TagResolver status = Placeholder.component("status", messages.component(on ? "settings.on" : "settings.off"));
            ItemStack icon = ItemStack.of(setting.icon);
            icon.editMeta(meta -> {
                meta.displayName(messages.item("settings." + setting.id + ".name"));
                meta.lore(messages.lore("settings." + setting.id + ".lore", status));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                if (on) meta.setEnchantmentGlintOverride(true);
            });
            ItemStack pane = ItemStack.of(on ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
            pane.editMeta(meta -> {
                meta.displayName(messages.item(on ? "settings.on" : "settings.off"));
                meta.lore(messages.lore("settings.pane-lore"));
            });
            menu.inventory.setItem(setting.slot, icon);
            menu.inventory.setItem(setting.slot + 9, pane);
        }
        player.openInventory(menu.inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        for (Setting setting : Setting.values()) {
            if (event.getRawSlot() == setting.slot || event.getRawSlot() == setting.slot + 9) {
                toggle(player, setting);
                open(player); // redraw with the new state
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }
}
