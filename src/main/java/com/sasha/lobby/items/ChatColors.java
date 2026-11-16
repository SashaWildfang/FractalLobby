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
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * /chatcolor: players pick the colour their chat messages are written in, from a menu or by name.
 * Colours are listed in config.yml; each can need its own permission. The choice is saved on the player.
 */
public final class ChatColors implements Listener {

    public static final String PERMISSION = "lobby.cmd.chatcolor";
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final String PREVIEW = "Hello there!";

    private final Lobby plugin;
    private final NamespacedKey key;

    public ChatColors(Lobby plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "chat-color");
    }

    // ---------------------------------------------------------------- colours

    /**
     * Whether a colour setting is valid: a hex colour (#B266FF), a colour name (light_purple),
     * "rainbow", or "gradient:#8E2DE2:#E58BFF" (two or more colours).
     */
    public static boolean valid(String spec) {
        String s = spec.trim().toLowerCase(Locale.ROOT);
        if (s.equals("rainbow")) return true;
        if (s.startsWith("gradient:")) {
            String[] parts = s.substring("gradient:".length()).split(":");
            if (parts.length < 2) return false;
            for (String part : parts) if (single(part) == null) return false;
            return true;
        }
        return single(s) != null;
    }

    private static TextColor single(String s) {
        if (HEX.matcher(s).matches()) return TextColor.fromHexString(s);
        return NamedTextColor.NAMES.value(s);
    }

    /** Writes the text in the colour (gradients and rainbow run across the whole message). */
    public static Component paint(String spec, Component message) {
        String s = spec.trim().toLowerCase(Locale.ROOT);
        if (s.equals("rainbow") || s.startsWith("gradient:")) {
            String text = MM.escapeTags(PlainTextComponentSerializer.plainText().serialize(message));
            String tag = s.equals("rainbow") ? "rainbow" : "gradient:" + s.substring("gradient:".length());
            return MM.deserialize("<" + tag + ">" + text);
        }
        TextColor colour = single(s);
        return colour == null ? message : message.color(colour);
    }

    /** The player's chosen colour, if they still have it (it may have been removed or its permission taken away). */
    public LobbyConfig.ChatColor chosen(Player player) {
        String id = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return null;
        LobbyConfig.ChatColor colour = find(id);
        return colour != null && allowed(player, colour) ? colour : null;
    }

    public LobbyConfig.ChatColor find(String id) {
        for (LobbyConfig.ChatColor colour : plugin.lobbyConfig().chatColors().colors()) {
            if (colour.id().equalsIgnoreCase(id)) return colour;
        }
        return null;
    }

    public static boolean allowed(Player player, LobbyConfig.ChatColor colour) {
        return colour.permission().isEmpty() || player.hasPermission(colour.permission());
    }

    /** The names players can type after /chatcolor. */
    public List<String> names(Player player) {
        List<String> names = new ArrayList<>();
        for (LobbyConfig.ChatColor colour : plugin.lobbyConfig().chatColors().colors()) {
            if (allowed(player, colour)) names.add(colour.id());
        }
        names.add("reset");
        return names;
    }

    /** /chatcolor NAME */
    public void choose(Player player, String input) {
        Messages messages = plugin.messages();
        if (input.equalsIgnoreCase("reset")) {
            reset(player);
            return;
        }
        LobbyConfig.ChatColor colour = find(input);
        if (colour == null) {
            messages.send(player, "chat-color.unknown", Messages.arg("input", input));
        } else if (!allowed(player, colour)) {
            messages.send(player, "chat-color.locked");
        } else {
            player.getPersistentDataContainer().set(key, PersistentDataType.STRING, colour.id());
            messages.send(player, "chat-color.set", Placeholder.component("color", name(colour)));
        }
    }

    private void reset(Player player) {
        player.getPersistentDataContainer().remove(key);
        plugin.messages().send(player, "chat-color.reset");
    }

    private Component name(LobbyConfig.ChatColor colour) {
        return plugin.messages().render(colour.name());
    }

    // ---------------------------------------------------------------- menu

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> slots = new HashMap<>();
        private int resetSlot = -1;
        private int backSlot = -1;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** The menu: every colour on a dark background, with a reset button at the bottom. */
    public void open(Player player) {
        List<LobbyConfig.ChatColor> colours = plugin.lobbyConfig().chatColors().colors();
        Messages messages = plugin.messages();
        int colourRows = Math.max(1, Math.min(4, (colours.size() + 6) / 7));
        Menu menu = new Menu();
        MenuStyle style = plugin.menuStyle();
        menu.inventory = Bukkit.createInventory(menu, (colourRows + 2) * 9,
                style.title(MenuStyle.chatColor(colourRows + 2), messages.component("chat-color.title")));
        ItemStack filler = style.filler();
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);

        LobbyConfig.ChatColor current = chosen(player);
        for (int i = 0; i < colours.size() && i < colourRows * 7; i++) {
            LobbyConfig.ChatColor colour = colours.get(i);
            int slot = (1 + i / 7) * 9 + 1 + i % 7;
            boolean selected = colour == current;
            String loreKey = !allowed(player, colour) ? "chat-color.item-locked"
                    : selected ? "chat-color.item-selected" : "chat-color.item";
            TagResolver preview = Placeholder.component("preview", paint(colour.color(), Component.text(PREVIEW)));

            ItemStack item = ItemStack.of(colour.item());
            item.editMeta(meta -> {
                meta.displayName(messages.render("<!italic>" + colour.name()));
                meta.lore(messages.lore(loreKey, preview));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                if (selected) meta.setEnchantmentGlintOverride(true);
            });
            menu.inventory.setItem(slot, style.colorIcon(item, colour.id()));
            menu.slots.put(slot, colour.id());
        }

        ItemStack reset = ItemStack.of(Material.BARRIER);
        reset.editMeta(meta -> {
            meta.displayName(messages.item("chat-color.reset-item.name"));
            meta.lore(messages.lore("chat-color.reset-item.lore"));
        });
        menu.resetSlot = menu.inventory.getSize() - 4;
        menu.inventory.setItem(menu.resetSlot, style.icon(reset, "reset"));
        menu.backSlot = menu.inventory.getSize() - 6;
        menu.inventory.setItem(menu.backSlot, style.icon(Profile.button(plugin.messages(), Material.ARROW, "profile.back"), "back"));
        player.openInventory(menu.inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot == menu.backSlot) {
            plugin.profile().open(player);
            return;
        }
        if (slot == menu.resetSlot) {
            reset(player);
        } else {
            String id = menu.slots.get(slot);
            if (id == null) return;
            LobbyConfig.ChatColor colour = find(id);
            if (colour != null && !allowed(player, colour)) {
                player.playSound(Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 0.5f, 1f), Sound.Emitter.self());
                plugin.messages().send(player, "chat-color.locked");
                return;
            }
            choose(player, id);
        }
        player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, 1.2f), Sound.Emitter.self());
        player.closeInventory();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }
}
