/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.proxy.CoreLink;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The friends menu (from the profile): every friend as a head, online ones first with the server they're on.
 * Left-click an online friend to join them; Shift + right-click removes a friend. Along the bottom: Back, Add a
 * Friend, Friend Requests and Ignored Players. Friends come from Core on the proxy.
 */
public final class FriendsMenu implements Listener {

    /** Background glyph for the menu (resource pack). */
    public static final char BACKGROUND = '';
    private static final int SHOWN = 45;
    private static final int BACK = 45, ADD = 47, REQUESTS = 49, IGNORED = 51;

    private final Lobby plugin;

    public FriendsMenu(Lobby plugin) {
        this.plugin = plugin;
    }

    private static final class Menu implements InventoryHolder {
        private final Map<Integer, UUID> friends = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** The Friends button for the profile: how many friends are online. */
    public ItemStack button(CoreLink.State state) {
        long online = state.friends().values().stream().filter(CoreLink.Friend::online).count();
        ItemStack item = Profile.button(plugin.messages(), Material.PLAYER_HEAD, "friends.button",
                TagResolver.resolver(Messages.arg("online", online), Messages.arg("count", state.friends().size())));
        return plugin.menuStyle().icon(item, "friends");
    }

    public void open(Player player) {
        CoreLink.State state = plugin.coreLink().state(player);
        if (state == null) {
            plugin.messages().send(player, "friends.menu.no-core");
            return;
        }
        Menu menu = new Menu();
        MenuStyle style = plugin.menuStyle();
        menu.inventory = Bukkit.createInventory(menu, 54, style.title(BACKGROUND, plugin.messages().component("friends.menu.title")));
        draw(player, menu, state);
        player.openInventory(menu.inventory);
    }

    private void draw(Player player, Menu menu, CoreLink.State state) {
        Messages messages = plugin.messages();
        MenuStyle style = plugin.menuStyle();
        ItemStack filler = style.filler();
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);
        menu.friends.clear();

        // Online first, then everyone else, each by name.
        List<Map.Entry<UUID, CoreLink.Friend>> friends = new ArrayList<>(state.friends().entrySet());
        friends.sort(Comparator.<Map.Entry<UUID, CoreLink.Friend>, Boolean>comparing(entry -> !entry.getValue().online())
                .thenComparing(entry -> entry.getValue().name(), String.CASE_INSENSITIVE_ORDER));
        if (friends.isEmpty()) {
            menu.inventory.setItem(22, style.icon(Profile.button(messages, Material.LIME_DYE, "friends.menu.empty"), "none"));
        }
        long now = System.currentTimeMillis();
        int slot = 0;
        for (Map.Entry<UUID, CoreLink.Friend> entry : friends) {
            if (slot == SHOWN - 1 && friends.size() > SHOWN) {
                menu.inventory.setItem(slot, style.icon(Profile.button(messages, Material.PAPER, "friends.menu.more",
                        Messages.arg("count", friends.size() - slot)), "more"));
                break;
            }
            CoreLink.Friend friend = entry.getValue();
            String key = friend.online() ? "friends.menu.online" : "friends.menu.offline";
            TagResolver tags = TagResolver.resolver(Messages.name("player", friend.name()),
                    Messages.arg("server", serverName(friend.server())),
                    Messages.arg("since", CoreLink.duration(now - friend.since())));
            ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
            Player online = plugin.getServer().getPlayer(entry.getKey());
            head.editMeta(SkullMeta.class, meta -> {
                meta.setPlayerProfile(online != null ? online.getPlayerProfile() : Bukkit.createProfile(entry.getKey(), friend.name()));
                meta.displayName(messages.item(key + ".name", tags));
                meta.lore(messages.lore(key + ".lore", tags));
            });
            Profile.hideProfileLine(head);
            menu.inventory.setItem(slot, head);
            menu.friends.put(slot, entry.getKey());
            slot++;
        }

        menu.inventory.setItem(BACK, style.icon(Profile.button(messages, Material.ARROW, "profile.back"), "back"));
        menu.inventory.setItem(ADD, style.icon(Profile.button(messages, Material.WRITABLE_BOOK, "friends.menu.add"), "add-friend"));
        menu.inventory.setItem(REQUESTS, style.icon(Profile.button(messages, Material.BOOK, "friends.menu.requests"), "friend-requests"));
        menu.inventory.setItem(IGNORED, plugin.settings().ignoredButton(state));
    }

    /** A server's name as players read it: "survival" → "Survival", "ctf" → "CTF". */
    private static String serverName(String server) {
        if (server == null || server.isEmpty()) return "";
        if (server.length() <= 3) return server.toUpperCase(Locale.ROOT);
        return Character.toUpperCase(server.charAt(0)) + server.substring(1);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        switch (slot) {
            case BACK -> {
                click(player, 0);
                plugin.profile().open(player);
            }
            case ADD -> {
                // Just says how to add a friend.
            }
            case REQUESTS -> {
                player.closeInventory();
                click(player, 1);
                player.performCommand("friend requests");
            }
            case IGNORED -> {
                click(player, 1);
                plugin.settings().openIgnored(player);
            }
            default -> {
                UUID friend = menu.friends.get(slot);
                CoreLink.State state = plugin.coreLink().state(player);
                if (friend == null || state == null) return;
                CoreLink.Friend data = state.friends().get(friend);
                if (data == null) return;
                if (event.getClick() == ClickType.SHIFT_RIGHT) {
                    plugin.coreLink().unfriend(player, friend);
                    plugin.messages().send(player, "friends.menu.removed", Messages.name("player", data.name()));
                    click(player, 0);
                    CoreLink.State now = plugin.coreLink().state(player);
                    if (now != null) draw(player, menu, now);
                } else if (event.getClick().isLeftClick() && data.online()) {
                    player.closeInventory();
                    click(player, 1);
                    plugin.coreLink().joinFriend(player, friend);
                }
            }
        }
    }

    /** Core sent new friends (someone came online, a friend was added): redraw an open menu. */
    public void refresh(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) {
            CoreLink.State state = plugin.coreLink().state(player);
            if (state != null) draw(player, menu, state);
        }
    }

    private static void click(Player player, int step) {
        player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, 0.9f + step * 0.2f), Sound.Emitter.self());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }
}
