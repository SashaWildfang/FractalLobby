/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import com.sasha.lobby.config.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Particle trails: a cosmetic players pick in their profile. While they walk, particles follow their
 * feet. Trails are listed in config.yml; each can need its own permission (to sell as rank perks).
 * Only players who can see the walker (player visibility) see their trail.
 */
public final class Trails implements Listener {

    private final Lobby plugin;
    private final NamespacedKey key;
    private final Map<UUID, Location> lastSpot = new HashMap<>();
    private BukkitTask task;

    public Trails(Lobby plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "trail");
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    /** The player's trail, if they still have it (it may have been removed or its permission taken away). */
    public LobbyConfig.Trail chosen(Player player) {
        String id = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return null;
        for (LobbyConfig.Trail trail : plugin.lobbyConfig().trails().trails()) {
            if (trail.id().equalsIgnoreCase(id)) return allowed(player, trail) ? trail : null;
        }
        return null;
    }

    public static boolean allowed(Player player, LobbyConfig.Trail trail) {
        return trail.permission().isEmpty() || player.hasPermission(trail.permission())
                || com.sasha.lobby.mystery.MysteryBoxes.unlocked(player, "trail:" + trail.id().toLowerCase(java.util.Locale.ROOT));
    }

    private void tick() {
        if (!plugin.lobbyConfig().trails().enabled()) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Location at = player.getLocation();
            Location last = lastSpot.put(player.getUniqueId(), at);
            // Only while walking: standing still leaves no trail.
            if (last == null || last.getWorld() != at.getWorld() || last.distanceSquared(at) < 0.01) continue;
            LobbyConfig.Trail trail = chosen(player);
            if (trail == null || player.getGameMode() == GameMode.SPECTATOR || player.isInvisible()) continue;
            Location spot = at.clone().add(0, 0.15, 0);
            for (Player viewer : at.getWorld().getPlayers()) {
                if (viewer != player && !viewer.canSee(player)) continue;
                if (viewer.getLocation().distanceSquared(at) > 48 * 48) continue;
                viewer.spawnParticle(trail.particle(), spot, trail.amount(), 0.2, 0.05, 0.2, trail.speed());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastSpot.remove(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- menu

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> slots = new HashMap<>();
        private int noneSlot = -1;
        private int backSlot = -1;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Every trail on a dark background; the one in use glows, ones not unlocked say so. */
    public void open(Player player) {
        List<LobbyConfig.Trail> trails = plugin.lobbyConfig().trails().trails();
        Messages messages = plugin.messages();
        int rows = Math.max(1, Math.min(4, (trails.size() + 6) / 7));
        Menu menu = new Menu();
        MenuStyle style = plugin.menuStyle();
        menu.inventory = Bukkit.createInventory(menu, (rows + 2) * 9,
                style.title(MenuStyle.trails(rows + 2), messages.component("trails.title")));
        ItemStack filler = style.filler();
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);

        LobbyConfig.Trail current = chosen(player);
        for (int i = 0; i < trails.size() && i < rows * 7; i++) {
            LobbyConfig.Trail trail = trails.get(i);
            int slot = (1 + i / 7) * 9 + 1 + i % 7;
            boolean selected = trail.equals(current);
            String loreKey = !allowed(player, trail) ? "trails.item-locked" : selected ? "trails.item-selected" : "trails.item";
            ItemStack item = ItemStack.of(trail.item());
            item.editMeta(meta -> {
                meta.displayName(messages.render("<!italic>" + trail.name()));
                meta.lore(messages.lore(loreKey));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                if (selected) meta.setEnchantmentGlintOverride(true);
            });
            menu.inventory.setItem(slot, style.trailIcon(item, trail.id()));
            menu.slots.put(slot, trail.id());
        }

        int bottom = menu.inventory.getSize() - 9;
        menu.backSlot = bottom + 3;
        menu.noneSlot = bottom + 5;
        menu.inventory.setItem(menu.backSlot, style.icon(Profile.button(messages, Material.ARROW, "profile.back"), "back"));
        menu.inventory.setItem(menu.noneSlot, style.icon(Profile.button(messages, Material.BARRIER, "trails.none-item"), "reset"));
        player.openInventory(menu.inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Messages messages = plugin.messages();
        int slot = event.getRawSlot();

        if (slot == menu.backSlot) {
            plugin.profile().open(player);
            return;
        }
        if (slot == menu.noneSlot) {
            player.getPersistentDataContainer().remove(key);
            messages.send(player, "trails.removed");
            click(player, 0.8f);
            open(player);
            return;
        }
        String id = menu.slots.get(slot);
        if (id == null) return;
        for (LobbyConfig.Trail trail : plugin.lobbyConfig().trails().trails()) {
            if (!trail.id().equals(id)) continue;
            if (!allowed(player, trail)) {
                player.playSound(Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 0.5f, 1f), Sound.Emitter.self());
                messages.send(player, "trails.locked");
                return;
            }
            player.getPersistentDataContainer().set(key, PersistentDataType.STRING, trail.id());
            messages.send(player, "trails.set", Placeholder.component("trail", messages.render(trail.name())));
            click(player, 1.2f);
            open(player);
        }
    }

    private static void click(Player player, float pitch) {
        player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, pitch), Sound.Emitter.self());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }
}
