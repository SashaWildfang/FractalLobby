/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import org.bukkit.event.player.PlayerRecipeBookSettingsChangeEvent;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * No crafting in the lobby (join-items.no-crafting): the 2x2 grid in the player's inventory takes no items, the
 * recipe book is emptied and kept closed. The resource pack paints over the grid and hides the recipe book button.
 */
public final class NoCrafting implements Listener {

    private final Lobby plugin;
    private boolean warned;

    public NoCrafting(Lobby plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("join-items.no-crafting", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled()) return;
        Player player = event.getPlayer();
        if (!player.getDiscoveredRecipes().isEmpty()) player.undiscoverRecipes(player.getDiscoveredRecipes());
        closeRecipeBook(player);
    }

    /** Someone opened the (invisible) recipe book: close it again for the next time the inventory opens. */
    @EventHandler
    public void onBookSettings(PlayerRecipeBookSettingsChangeEvent event) {
        if (enabled() && event.isOpen()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> closeRecipeBook(event.getPlayer()));
        }
    }

    /** Picking up an item would teach its recipes; the book stays empty. */
    @EventHandler(ignoreCancelled = true)
    public void onDiscover(PlayerRecipeDiscoverEvent event) {
        if (enabled()) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRecipeClick(PlayerRecipeBookClickEvent event) {
        if (enabled()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!enabled() || event.getView().getTopInventory().getType() != InventoryType.CRAFTING) return;
        if (event.getRawSlot() >= 0 && event.getRawSlot() <= 4) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!enabled() || event.getView().getTopInventory().getType() != InventoryType.CRAFTING) return;
        if (event.getRawSlots().stream().anyMatch(raw -> raw >= 0 && raw <= 4)) event.setCancelled(true);
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent event) {
        if (enabled() && event.getInventory().getType() == InventoryType.CRAFTING) event.getInventory().setResult(null);
    }

    /**
     * Paper has no API for the recipe book's open/closed state, so this sets it on the server player and sends the
     * client its settings again. If the server's internals ever change, it logs once and the rest still works.
     */
    private void closeRecipeBook(Player player) {
        if (!player.isOnline()) return;
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object book = handle.getClass().getMethod("getRecipeBook").invoke(handle);
            Class<?> type = Class.forName("net.minecraft.world.inventory.RecipeBookType");
            Method setOpen = book.getClass().getMethod("setOpen", type, boolean.class);
            for (Object t : type.getEnumConstants()) setOpen.invoke(book, t, false);
            Object settings = book.getClass().getMethod("getBookSettings").invoke(book);
            settings = settings.getClass().getMethod("copy").invoke(settings);
            Class<?> packetType = Class.forName("net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket");
            Object packet = packetType.getConstructor(settings.getClass()).newInstance(settings);
            Object connection = handle.getClass().getField("connection").get(handle);
            connection.getClass().getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"))
                    .invoke(connection, packet);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) {
                warned = true;
                plugin.getLogger().log(Level.FINE, "Couldn't close the recipe book", e);
                plugin.getLogger().info("Couldn't close players' recipe books on this server version; "
                        + "the crafting grid is still blocked.");
            }
        }
    }
}
