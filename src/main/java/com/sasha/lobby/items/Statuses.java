/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import com.sasha.lobby.config.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Statuses: players pick one in their profile (an emoji and a word, like "Happy"), and it shows under their
 * name as "Status: (emoji) Happy". Statuses are listed in config.yml; the choice is saved on the player.
 */
public final class Statuses implements Listener {

    /** Menu rows hold 7 statuses; the menu has room for 4 rows. */
    public static final int MAX = 28;

    private final Lobby plugin;
    private final NamespacedKey key;

    public Statuses(Lobby plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "status");
    }

    public boolean enabled() {
        return plugin.lobbyConfig().statuses().enabled();
    }

    /** The player's status, if it still exists and they're still allowed it. */
    public LobbyConfig.Status chosen(Player player) {
        if (!enabled()) return null;
        String id = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return null;
        LobbyConfig.Status status = find(id);
        return status != null && allowed(player, status) ? status : null;
    }

    public LobbyConfig.Status find(String id) {
        for (LobbyConfig.Status status : plugin.lobbyConfig().statuses().statuses()) {
            if (status.id().equalsIgnoreCase(id)) return status;
        }
        return null;
    }

    private static boolean allowed(Player player, LobbyConfig.Status status) {
        return status.permission().isEmpty() || player.hasPermission(status.permission());
    }

    /** The emoji (from the pack) and name, e.g. for the profile: "(emoji) Happy". */
    public Component label(LobbyConfig.Status status) {
        Component name = plugin.messages().render(status.name());
        char glyph = MenuStyle.emoji(status.icon());
        if (glyph == 0 || !plugin.menuStyle().custom()) return name;
        // Siblings, not children: the name mustn't inherit the emoji's font.
        // White, or the emoji is tinted by the colour of the text around it.
        return Component.textOfChildren(Component.text(String.valueOf(glyph)).font(Key.key("fractal", "lobby"))
                .color(net.kyori.adventure.text.format.NamedTextColor.WHITE), Component.space(), name);
    }

    /** What shows under the player's name, or null for nothing. */
    public Component belowName(Player player) {
        if (plugin.afk().isAfk(player)) {
            Component icon = plugin.menuStyle().custom()
                    ? Component.text("\uF310").font(Key.key("fractal", "lobby"))
                            .color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
                    : Component.empty();
            return plugin.messages().component("afk.below-name", Placeholder.component("icon", icon));
        }
        LobbyConfig.Status status = chosen(player);
        if (status == null) return null;
        return plugin.messages().component("status.below-name", Placeholder.component("status", label(status)));
    }

    private void set(Player player, LobbyConfig.Status status) {
        if (status == null) {
            player.getPersistentDataContainer().remove(key);
            plugin.messages().send(player, "status.cleared");
        } else {
            player.getPersistentDataContainer().set(key, PersistentDataType.STRING, status.id());
            plugin.messages().send(player, "status.set", Placeholder.component("status", label(status)));
        }
        plugin.display().refreshStatus(player);
    }

    // ---------------------------------------------------------------- menu

    /** Which columns (of 7) a row of k statuses uses: centred; an even count leaves the middle empty. */
    static List<Integer> columns(int k) {
        List<Integer> cols = new ArrayList<>();
        if (k >= 7) {
            for (int i = 0; i < 7; i++) cols.add(i);
        } else if (k % 2 == 1) {
            for (int i = 0; i < k; i++) cols.add((7 - k) / 2 + i);
        } else {
            int width = k + 1, start = (7 - width) / 2;
            for (int i = 0; i < width; i++) if (i != width / 2) cols.add(start + i);
        }
        return cols;
    }

    /** The slots of n statuses, row by row (the same layout the pack's backgrounds have). */
    static List<Integer> slots(int n) {
        List<Integer> slots = new ArrayList<>();
        int rows = (n + 6) / 7;
        for (int r = 0; r < rows; r++) {
            for (int c : columns(Math.min(7, n - 7 * r))) slots.add((1 + r) * 9 + 1 + c);
        }
        return slots;
    }

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> slots = new HashMap<>();
        private int clearSlot = -1;
        private int backSlot = -1;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player) {
        Messages messages = plugin.messages();
        List<LobbyConfig.Status> statuses = plugin.lobbyConfig().statuses().statuses();
        int count = Math.max(1, Math.min(MAX, statuses.size()));
        int statusRows = (count + 6) / 7;
        int last = count - (statusRows - 1) * 7;
        int size = (statusRows + 2) * 9;
        MenuStyle style = plugin.menuStyle();
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, size,
                style.title(player, MenuStyle.status(statusRows + 2, last), messages.component("status.title")));
        ItemStack filler = style.filler(player);
        for (int i = 0; i < size; i++) menu.inventory.setItem(i, filler);

        LobbyConfig.Status current = chosen(player);
        List<Integer> slots = slots(count);
        for (int i = 0; i < statuses.size() && i < slots.size(); i++) {
            LobbyConfig.Status status = statuses.get(i);
            boolean selected = status == current;
            String loreKey = !allowed(player, status) ? "status.item-locked" : selected ? "status.item-selected" : "status.item";
            TagResolver preview = Placeholder.component("preview", messages.component("status.below-name",
                    Placeholder.component("status", label(status))));
            ItemStack item = ItemStack.of(status.item());
            item.editMeta(meta -> {
                meta.displayName(messages.render("<!italic>" + status.name()));
                meta.lore(messages.lore(loreKey, preview));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                if (selected) meta.setEnchantmentGlintOverride(true);
            });
            menu.inventory.setItem(slots.get(i), allowed(player, status) ? style.statusIcon(item, status.icon()) : style.locked(item));
            menu.slots.put(slots.get(i), status.id());
        }

        ItemStack clear = ItemStack.of(Material.BARRIER);
        clear.editMeta(meta -> {
            meta.displayName(messages.item("status.clear-item.name"));
            meta.lore(messages.lore("status.clear-item.lore"));
        });
        menu.clearSlot = size - 4;
        menu.inventory.setItem(menu.clearSlot, style.icon(clear, "reset"));
        menu.backSlot = size - 6;
        menu.inventory.setItem(menu.backSlot, style.icon(Profile.button(messages, Material.ARROW, "profile.back"), "back"));
        player.openInventory(menu.inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (event.getClick() != ClickType.LEFT) return; // every button here says Left-click
        if (slot == menu.backSlot) {
            click(player);
            plugin.profile().open(player);
            return;
        }
        if (slot == menu.clearSlot) {
            if (chosen(player) == null) return;
            set(player, null);
        } else {
            String id = menu.slots.get(slot);
            if (id == null) return;
            LobbyConfig.Status status = find(id);
            if (status == null) return;
            if (!allowed(player, status)) {
                player.playSound(Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 0.5f, 1f), Sound.Emitter.self());
                plugin.messages().send(player, "status.locked");
                return;
            }
            set(player, status == chosen(player) ? null : status);
        }
        click(player);
        player.closeInventory();
    }

    private static void click(Player player) {
        player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, 1.2f), Sound.Emitter.self());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }
}
