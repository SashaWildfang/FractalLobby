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
        if (com.sasha.lobby.mystery.MysteryBoxes.unlocked(player, "chatcolor:" + colour.id().toLowerCase(java.util.Locale.ROOT))) return true;
        // Without the chat colour rank, only colours found in Mystery Boxes.
        if (!player.hasPermission(PERMISSION)) return false;
        return colour.permission().isEmpty() || player.hasPermission(colour.permission());
    }

    /** Whether the player may pick a chat colour at all: the rank, or a colour found in a Mystery Box. */
    public static boolean canUse(Player player) {
        return player.hasPermission(PERMISSION) || com.sasha.lobby.mystery.MysteryBoxes.anyChatColor(player);
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

    /** What the menu shows (the Sort button). Remembered per player while online. */
    enum Sort {
        ALL, UNLOCKED, LOCKED;

        Sort step(int by) {
            Sort[] all = values();
            return all[Math.floorMod(ordinal() + by, all.length)];
        }

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final Map<java.util.UUID, Sort> sorts = new java.util.concurrent.ConcurrentHashMap<>();

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> slots = new HashMap<>();
        private int resetSlot = -1;
        private int backSlot = -1;
        private int sortSlot = -1;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** The menu: every colour on a dark background, with a reset button at the bottom. */
    public void open(Player player) {
        List<LobbyConfig.ChatColor> every = plugin.lobbyConfig().chatColors().colors();
        Messages messages = plugin.messages();
        // the rows fit every colour, so the menu keeps its size whatever the Sort button shows
        int colourRows = Math.max(1, Math.min(4, (every.size() + 6) / 7));
        Sort sort = sorts.getOrDefault(player.getUniqueId(), Sort.ALL);
        List<LobbyConfig.ChatColor> colours = switch (sort) {
            case ALL -> every;
            case UNLOCKED -> every.stream().filter(colour -> allowed(player, colour)).toList();
            case LOCKED -> every.stream().filter(colour -> !allowed(player, colour)).toList();
        };
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
            menu.inventory.setItem(slot, allowed(player, colour) ? style.colorIcon(item, colour.id()) : style.locked(item));
            menu.slots.put(slot, colour.id());
        }

        if (colours.isEmpty()) { // the Sort button shows nothing: say so, with the red X
            ItemStack none = Profile.button(messages, Material.BARRIER, "chat-color.sort-empty");
            menu.inventory.setItem((1 + (colourRows - 1) / 2) * 9 + 4, style.icon(none, "none"));
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
        menu.sortSlot = menu.inventory.getSize() - 1;
        menu.inventory.setItem(menu.sortSlot, sortButton(sort));
        player.openInventory(menu.inventory);
    }

    /** The Sort button: what's shown now, every choice listed, Left-click for the next and Right-click to go back. */
    private ItemStack sortButton(Sort sort) {
        Messages messages = plugin.messages();
        ItemStack icon = ItemStack.of(Material.HOPPER);
        icon.editMeta(meta -> {
            meta.displayName(messages.item("chat-color.sort.name",
                    Placeholder.component("mode", messages.component("chat-color.sort.modes." + sort.key()))));
            List<Component> lore = new java.util.ArrayList<>(messages.lore("chat-color.sort.lore"));
            for (Sort option : Sort.values()) {
                lore.add(messages.item(option == sort ? "chat-color.sort.option-selected" : "chat-color.sort.option",
                        Placeholder.component("mode", messages.component("chat-color.sort.modes." + option.key()))));
            }
            lore.addAll(messages.lore("chat-color.sort.hint"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        if (plugin.menuStyle().custom()) icon.editMeta(meta -> meta.setItemModel(new org.bukkit.NamespacedKey("fractal", "radio/sort")));
        return icon;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot == menu.sortSlot && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)) {
            Sort now = sorts.getOrDefault(player.getUniqueId(), Sort.ALL);
            sorts.put(player.getUniqueId(), now.step(event.getClick() == ClickType.RIGHT ? -1 : 1));
            player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, 1.2f), Sound.Emitter.self());
            open(player);
            return;
        }
        if (event.getClick() != ClickType.LEFT) return; // every other button here says Left-click
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
