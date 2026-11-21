/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
 * The items players get on join (realm selector, radio, settings and player visibility toggle), and the inventory lock:
 * without lobby.staff, items can't be moved, dropped or swapped.
 */
public final class JoinItems implements Listener {

    public static final String STAFF = "lobby.staff";
    /** The off-hand's index in the player's inventory. */
    private static final int OFF_HAND_SLOT = 40;
    private static final String VISIBILITY = "visibility";
    private static final String SELECTOR = "realm-selector";
    private static final String RADIO = "radio";
    private static final String SETTINGS = "settings";
    private static final String PROFILE = "profile";
    /** Clicks closer together than this are one physical click reported twice. */
    private static final long DUPLICATE_CLICK_MILLIS = 250;

    private final Lobby plugin;
    /** Marks our items, so they're recognised whatever their name or material. */
    private final NamespacedKey itemKey;
    private final Map<UUID, Long> lastToggle = new HashMap<>();

    private final RealmSelector selector;

    public JoinItems(Lobby plugin, RealmSelector selector) {
        this.plugin = plugin;
        this.selector = selector;
        this.itemKey = new NamespacedKey(plugin, "join-item");
    }

    // ---------------------------------------------------------------- join / visibility

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.lobbyConfig().joinItems().clearInventory()) player.getInventory().clear();
        giveItems(player);

        // The newcomer sees whoever their visibility setting allows, and everyone else decides about them.
        applyVisibility(player);
        refreshSeenBy(player);
    }

    /** Whether the viewer sees the subject: by the viewer's Player Visibility setting, and never vanished staff unless staff. */
    public boolean sees(Player viewer, Player subject) {
        return plugin.settings().shows(viewer, Settings.Setting.PLAYER_VISIBILITY, subject)
                && !plugin.coreLink().hiddenFrom(viewer, subject);
    }

    /** Shows or hides the subject for everyone else (after they join or vanish). */
    public void refreshSeenBy(Player subject) {
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other == subject) continue;
            if (sees(other, subject)) other.showPlayer(plugin, subject);
            else other.hidePlayer(plugin, subject);
        }
    }

    /** Shows and hides other players for the viewer, by their Player Visibility setting, and updates the item. */
    public void applyVisibility(Player viewer) {
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other == viewer) continue;
            if (sees(viewer, other)) viewer.showPlayer(plugin, other);
            else viewer.hidePlayer(plugin, other);
        }
        giveItems(viewer);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastToggle.remove(event.getPlayer().getUniqueId());
    }

    /** Gives (or refreshes) the join items. Also used after /lobby reload. */
    public void giveItems(Player player) {
        LobbyConfig.JoinItems config = plugin.lobbyConfig().joinItems();
        int selectorSlot = config.selectorSlot();
        // An older config.yml may still have the realm selector in slot 2, where the profile now goes: it moves one left.
        if (config.profile() && selectorSlot == config.profileSlot()) selectorSlot = selectorSlot > 0 ? selectorSlot - 1 : selectorSlot + 1;
        if (config.selector()) player.getInventory().setItem(selectorSlot, selectorItem());
        if (config.radio() && (config.radioRequiresPlugin().isEmpty()
                || plugin.getServer().getPluginManager().isPluginEnabled(config.radioRequiresPlugin()))) {
            int radioSlot = (com.sasha.lobby.Lobby.cosmeticsHook() != null && com.sasha.lobby.Lobby.cosmeticsHook().oldHotbar()) ? 2 : config.radioSlot();
            player.getInventory().setItem(radioSlot, plugin.menuStyle().icon(joinItem(config.radioItem(), "items.radio", RADIO), "radio"));
        }
        int visibilitySlot = config.visibilitySlot();
        // An older config.yml may still have visibility in slot 8, where settings now go: it moves one over.
        if (config.settings() && visibilitySlot == config.settingsSlot()) visibilitySlot = visibilitySlot < 8 ? visibilitySlot + 1 : visibilitySlot - 1;
        if (config.visibility()) player.getInventory().setItem(visibilitySlot, visibilityItem(player));
        if (config.settings()) player.getInventory().setItem(config.settingsSlot(),
                plugin.menuStyle().icon(joinItem(config.settingsItem(), "items.settings", SETTINGS), "header"));
        if (config.profile()) {
            ItemStack base = joinItem(org.bukkit.Material.PLAYER_HEAD, "items.profile", PROFILE);
            player.getInventory().setItem(config.profileSlot(), plugin.profile().hotbarItem(player, base));
        }
        if (com.sasha.lobby.Lobby.cosmeticsHook() != null) com.sasha.lobby.Lobby.cosmeticsHook().applyJoinItems(player);
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

        // The item switches between everyone and nobody; Donors/Staff Only are picked in the settings menu.
        boolean hide = plugin.settings().audience(player, Settings.Setting.PLAYER_VISIBILITY) != Settings.Audience.NOBODY;
        plugin.settings().set(player, Settings.Setting.PLAYER_VISIBILITY, hide ? "nobody" : "everyone");
        plugin.messages().send(player, hide ? "visibility.hidden" : "visibility.shown");
        player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, hide ? 0.8f : 1.2f),
                Sound.Emitter.self());
    }

    private ItemStack visibilityItem(Player player) {
        LobbyConfig.JoinItems config = plugin.lobbyConfig().joinItems();
        return switch (plugin.settings().audience(player, Settings.Setting.PLAYER_VISIBILITY)) {
            case EVERYONE -> plugin.menuStyle().icon(joinItem(config.visibleItem(), "items.visibility-shown", VISIBILITY), "players-shown");
            case NOBODY -> plugin.menuStyle().icon(joinItem(config.hiddenItem(), "items.visibility-hidden", VISIBILITY), "players-hidden");
            case DONORS -> plugin.menuStyle().icon(joinItem(config.visibleItem(), "items.visibility-partial", VISIBILITY,
                    Placeholder.component("option", plugin.messages().component("settings.options.donors"))), "players-shown");
            case STAFF -> plugin.menuStyle().icon(joinItem(config.visibleItem(), "items.visibility-partial", VISIBILITY,
                    Placeholder.component("option", plugin.messages().component("settings.options.staff"))), "players-shown");
        };
    }

    private ItemStack selectorItem() {
        return plugin.menuStyle().icon(joinItem(plugin.lobbyConfig().joinItems().selectorItem(), "items.realm-selector", SELECTOR), "realms");
    }

    private ItemStack joinItem(org.bukkit.Material material, String key, String type, TagResolver... args) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            meta.displayName(plugin.messages().item(key + ".name", args));
            meta.lore(plugin.messages().lore(key + ".lore", args));
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
        if (event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return; // e.g. the click opened the parkour leaderboard
        String type = joinItemType(event.getItem());
        if (type == null) return;
        event.setCancelled(true);
        switch (type) {
            case VISIBILITY -> toggleVisibility(event.getPlayer());
            case SELECTOR -> selector.open(event.getPlayer());
            case RADIO -> event.getPlayer().performCommand(plugin.lobbyConfig().joinItems().radioCommand());
            case SETTINGS -> plugin.settings().open(event.getPlayer());
            case PROFILE -> plugin.profile().open(event.getPlayer());
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- inventory lock

    private boolean locked(Player player) {
        return plugin.lobbyConfig().joinItems().lockInventory() && !player.hasPermission(STAFF);
    }

    private boolean offHandBlocked() {
        return plugin.lobbyConfig().joinItems().blockOffHand();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        boolean offHandSlot = event.getClickedInventory() != null
                && event.getClickedInventory().getType() == InventoryType.PLAYER && event.getSlot() == OFF_HAND_SLOT;
        if (offHandBlocked() && (offHandSlot || event.getClick() == ClickType.SWAP_OFFHAND)) {
            event.setCancelled(true);
            return;
        }
        if (!locked(player)) return;
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
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (offHandBlocked() && event.getRawSlots().stream().anyMatch(raw ->
                event.getView().getInventory(raw) == player.getInventory() && event.getView().convertSlot(raw) == OFF_HAND_SLOT)) {
            event.setCancelled(true);
            return;
        }
        if (!locked(player)) return;
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
        if (offHandBlocked() || locked(event.getPlayer())) event.setCancelled(true);
        if (com.sasha.lobby.Lobby.cosmeticsHook() != null) com.sasha.lobby.Lobby.cosmeticsHook().swapKey(event.getPlayer());
    }
}
