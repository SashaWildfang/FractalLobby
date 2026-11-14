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
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The realm selector menu. Clicking a realm sends the player to its server through the proxy. */
public final class RealmSelector implements Listener {

    private final Lobby plugin;

    public RealmSelector(Lobby plugin) {
        this.plugin = plugin;
    }

    /** The open menu; being the inventory's holder is how its clicks are recognised. */
    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> servers = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player) {
        LobbyConfig.Selector config = plugin.lobbyConfig().selector();
        Messages messages = plugin.messages();
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, config.rows() * 9, messages.component("realm-selector.title"));

        ItemStack filler = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.setHideTooltip(true));
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);

        for (LobbyConfig.SelectorServer server : config.servers()) {
            int online = plugin.proxyCounts().count(server.server());
            TagResolver args = TagResolver.resolver(
                    Messages.arg("online", online), Messages.arg("server", server.server()));
            List<Component> lore = new ArrayList<>();
            for (String line : server.description()) lore.add(messages.render("<!italic>" + line, args));
            lore.addAll(messages.lore("realm-selector.button-lore", args));

            ItemStack item = ItemStack.of(server.item());
            item.editMeta(meta -> {
                meta.displayName(messages.render("<!italic>" + server.name(), args));
                meta.lore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            });
            menu.inventory.setItem(server.slot(), item);
            menu.servers.put(server.slot(), server.server());
        }
        player.openInventory(menu.inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String server = menu.servers.get(event.getRawSlot());
        if (server == null) return;

        player.closeInventory();
        player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, 1.2f), Sound.Emitter.self());
        plugin.messages().send(player, "realm-selector.connecting", Messages.arg("server", server));
        plugin.proxyCounts().connect(player, server);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }
}
