/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

/**
 * "Are you sure?": a small menu with what it's about in the middle, Confirm (a green tick) on the left and Cancel
 * (a red cross) on the right. Used before buying with Mystery Dust, taking cosmetics off and opening boxes.
 * Both buttons take a plain Left-click, as their hints say.
 */
public final class ConfirmMenu implements Listener {

    /** The background (resource pack): a "CONFIRM" title and three slots. */
    private static final char BACKGROUND = '';
    static final int YES = 11, SHOWN = 13, NO = 15;

    private final Lobby plugin;

    public ConfirmMenu(Lobby plugin) {
        this.plugin = plugin;
    }

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        private Runnable yes;
        private Runnable no;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /**
     * Asks the player. {@code shown} is what it's about (its name and lore say what will happen); {@code yes} runs on
     * Confirm, {@code no} on Cancel (usually reopening the menu they came from).
     */
    public void open(Player player, ItemStack shown, Runnable yes, Runnable no) {
        Messages messages = plugin.messages();
        MenuStyle style = plugin.menuStyle();
        Menu menu = new Menu();
        menu.yes = yes;
        menu.no = no;
        menu.inventory = Bukkit.createInventory(menu, 27, style.title(BACKGROUND, messages.component("confirm.title")));
        ItemStack filler = style.filler();
        for (int i = 0; i < 27; i++) menu.inventory.setItem(i, filler);
        menu.inventory.setItem(SHOWN, shown);
        menu.inventory.setItem(YES, style.icon(button(messages, Material.LIME_DYE, "confirm.accept"), "confirm"));
        menu.inventory.setItem(NO, style.icon(button(messages, Material.RED_DYE, "confirm.cancel"), "none"));
        player.openInventory(menu.inventory);
        click(player, 1f);
    }

    /** An item named and described from messages.yml (KEY.name, KEY.lore). */
    public static ItemStack describe(ItemStack item, Messages messages, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver tags) {
        item.editMeta(meta -> {
            meta.displayName(messages.item(key + ".name", tags));
            meta.lore(messages.lore(key + ".lore", tags));
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    private static ItemStack button(Messages messages, Material material, String key) {
        return describe(ItemStack.of(material), messages, key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.empty());
    }

    private static void click(Player player, float pitch) {
        player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.6f, pitch), Sound.Emitter.self());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClick() != ClickType.LEFT) return; // both buttons say Left-click
        if (event.getRawSlot() == YES) {
            menu.yes.run();
        } else if (event.getRawSlot() == NO) {
            click(player, 0.8f);
            menu.no.run();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }
}
