/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The items players get on join (realm selector, radio and player visibility toggle), and the inventory lock:
 * without lobby.staff, items can't be moved, dropped or swapped.
 */
public final class JoinItems implements Listener {

    public static final String STAFF = "lobby.staff";
    private static final String VISIBILITY = "visibility";
    private static final String SELECTOR = "realm-selector";
    private static final String RADIO = "radio";
    /** Clicks closer together than this are one physical click reported twice. */
    private static final long DUPLICATE_CLICK_MILLIS = 250;

    private final Lobby plugin;
    /** Marks our items, so they're recognised whatever their name or material. */
    private final NamespacedKey itemKey;
    /** Stored on the player: true while they've hidden other players. Kept between joins. */
    private final NamespacedKey hiddenKey;
    private final Map<UUID, Long> lastToggle = new HashMap<>();

    private final RealmSelector selector;

    public JoinItems(Lobby plugin, RealmSelector selector) {
        this.plugin = plugin;
        this.selector = selector;
        this.itemKey = new NamespacedKey(plugin, "join-item");
        this.hiddenKey = new NamespacedKey(plugin, "players-hidden");
    }

    // ---------------------------------------------------------------- join / visibility

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.lobbyConfig().joinItems().clearInventory()) player.getInventory().clear();
        giveItems(player);

        // The newcomer hides everyone if that's their setting; anyone hiding players hides the newcomer.
        boolean hides = hidesPlayers(player);
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other == player) continue;
            if (hides) player.hidePlayer(plugin, other);
            if (hidesPlayers(other)) other.hidePlayer(plugin, player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastToggle.remove(event.getPlayer().getUniqueId());
    }

    /** Gives (or refreshes) the join items. Also used after /lobby reload. */
    public void giveItems(Player player) {
        LobbyConfig.JoinItems config = plugin.lobbyConfig().joinItems();
        if (config.selector()) player.getInventory().setItem(config.selectorSlot(), selectorItem());
        if (config.radio() && (config.radioRequiresPlugin().isEmpty()
                || plugin.getServer().getPluginManager().isPluginEnabled(config.radioRequiresPlugin()))) {
            player.getInventory().setItem(config.radioSlot(), joinItem(config.radioItem(), "items.radio", RADIO));
        }
        if (config.visibility()) player.getInventory().setItem(config.visibilitySlot(), visibilityItem(hidesPlayers(player)));
    }

    public boolean hidesPlayers(Player player) {
        return player.getPersistentDataContainer().getOrDefault(hiddenKey, PersistentDataType.BOOLEAN, false);
    }

    private void toggleVisibility(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastToggle.get(player.getUniqueId());
        if (last != null) {
            long since = now - last;
            // One right-click can arrive as two events (e.g. aiming at a block fires a block click and an
            // air click). Anything this close to the last use is that same click: ignore it silently.
            if (since < DUPLICATE_CLICK_MILLIS) return;
            if (since < plugin.lobbyConfig().joinItems().visibilityCooldownMillis()) {
                plugin.messages().send(player, "visibility.cooldown");
                return;
            }
        }
        lastToggle.put(player.getUniqueId(), now);

        boolean hide = !hidesPlayers(player);
        player.getPersistentDataContainer().set(hiddenKey, PersistentDataType.BOOLEAN, hide);
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other == player) continue;
            if (hide) player.hidePlayer(plugin, other);
            else player.showPlayer(plugin, other);
        }
        giveItems(player);
        plugin.messages().send(player, hide ? "visibility.hidden" : "visibility.shown");
        player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, hide ? 0.8f : 1.2f),
                Sound.Emitter.self());
    }

    private ItemStack visibilityItem(boolean hidden) {
        LobbyConfig.JoinItems config = plugin.lobbyConfig().joinItems();
        String key = hidden ? "items.visibility-hidden" : "items.visibility-shown";
        return joinItem(hidden ? config.hiddenItem() : config.visibleItem(), key, VISIBILITY);
    }

    private ItemStack selectorItem() {
        return joinItem(plugin.lobbyConfig().joinItems().selectorItem(), "items.realm-selector", SELECTOR);
    }

    private ItemStack joinItem(org.bukkit.Material material, String key, String type) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            meta.displayName(plugin.messages().item(key + ".name"));
            meta.lore(plugin.messages().lore(key + ".lore"));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type);
        });
        return item;
    }

    private String joinItemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String type = joinItemType(event.getItem());
        if (type == null) return;
        event.setCancelled(true);
        switch (type) {
            case VISIBILITY -> toggleVisibility(event.getPlayer());
            case SELECTOR -> selector.open(event.getPlayer());
            case RADIO -> event.getPlayer().performCommand(plugin.lobbyConfig().joinItems().radioCommand());
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- inventory lock

    private boolean locked(Player player) {
        return plugin.lobbyConfig().joinItems().lockInventory() && !player.hasPermission(STAFF);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !locked(player)) return;
        boolean ownInventory = event.getClickedInventory() != null
                && event.getClickedInventory().getType() == InventoryType.PLAYER;
        boolean ownScreen = event.getView().getTopInventory().getType() == InventoryType.CRAFTING;
        boolean pullsFromHotbar = event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR;
        // Other plugins' menus (top inventory) keep working; only the player's own items are locked.
        if (ownInventory || ownScreen || pullsFromHotbar) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !locked(player)) return;
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesOwn = event.getView().getTopInventory().getType() == InventoryType.CRAFTING
                || event.getRawSlots().stream().anyMatch(slot -> slot >= topSize);
        if (touchesOwn) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (locked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (locked(event.getPlayer())) event.setCancelled(true);
    }
}
