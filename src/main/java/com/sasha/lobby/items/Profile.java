/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.listener.GlowListener;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
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
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;

/**
 * The profile (the player's own head in the hotbar): their rank, when they first joined, playtime and
 * parkour best, and buttons for everything they can customise: chat colour, trail, glow and settings.
 */
public final class Profile implements Listener {

    /*
     * Four rows: the player's head at the top with their status under it, an empty row, then five buttons in the middle
     * of the bottom row: cosmetics (chat colour, glow) on the left, friends in the middle (with the ignored list inside),
     * parkour and settings on the right. Trails live in the Collectibles menu, realms in the realm selector.
     */
    private static final int HEAD = 4;
    private static final int STATUS = 13;
    private static final int CHAT_COLOR = 29;
    private static final int GLOW = 30;
    private static final int FRIENDS = 31;
    private static final int PARKOUR = 32;
    private static final int SETTINGS = 33;

    private final Lobby plugin;

    public Profile(Lobby plugin) {
        this.plugin = plugin;
    }

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** The hotbar item: the player's own head. */
    public ItemStack hotbarItem(Player player, ItemStack base) {
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        head.setItemMeta(base.getItemMeta() == null ? head.getItemMeta() : base.getItemMeta().clone());
        head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(player.getPlayerProfile()));
        hideProfileLine(head);
        return head;
    }

    public void open(Player player) {
        Messages messages = plugin.messages();
        Menu menu = new Menu();
        MenuStyle style = plugin.menuStyle();
        menu.inventory = Bukkit.createInventory(menu, 36, style.title(MenuStyle.PROFILE,
                messages.component("profile.title", Messages.name("player", player.getName()))));
        ItemStack filler = style.filler();
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);

        TagResolver info = info(player);
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            meta.setPlayerProfile(player.getPlayerProfile());
            meta.displayName(messages.item("profile.head.name", info));
            meta.lore(messages.lore("profile.head.lore", info));
        });
        hideProfileLine(head);
        menu.inventory.setItem(HEAD, head);

        if (plugin.statuses().enabled()) {
            LobbyConfig.Status status = plugin.statuses().chosen(player);
            ItemStack button = style.statusIcon(button(messages, Material.SUNFLOWER, "profile.status", info),
                    status == null ? "happy" : status.icon());
            menu.inventory.setItem(STATUS, button);
        }
        menu.inventory.setItem(CHAT_COLOR, style.icon(button(messages, Material.NAME_TAG, "profile.chat-color", info), "chat-color"));
        menu.inventory.setItem(GLOW, GlowListener.allowed(player)
                ? style.icon(button(messages, Material.GLOWSTONE_DUST, "profile.glow", info), "glow")
                : style.locked(button(messages, Material.GLOWSTONE_DUST, "profile.glow-locked", info)));
        menu.inventory.setItem(SETTINGS, style.icon(button(messages, Material.COMPARATOR, "profile.settings", info), "header"));
        menu.inventory.setItem(PARKOUR, style.icon(button(messages, Material.GOLDEN_BOOTS, "profile.parkour", info), "parkour"));
        // Friends (and who they ignore, inside) are kept by Core on the proxy; without it there's nothing to show.
        var network = plugin.coreLink().state(player);
        if (network != null) menu.inventory.setItem(FRIENDS, plugin.friendsMenu().button(network));
        player.openInventory(menu.inventory);
    }

    /** Everything the profile shows about a player, as tags. */
    private TagResolver info(Player player) {
        Messages messages = plugin.messages();
        String prefix = plugin.playerFormat().prefix(player);
        Component rank = prefix.isBlank() ? messages.component("profile.no-rank") : plugin.playerFormat().render(player, prefix.trim());

        // Network playtime from Core on the proxy; without Core, the time spent on this server.
        var network = plugin.coreLink().state(player);
        long networkPlaytime = network == null ? -1 : network.playtimeNow();
        long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE); // despite the name, it's in ticks
        String playtime = com.sasha.lobby.proxy.CoreLink.duration(networkPlaytime >= 0 ? networkPlaytime : ticks * 50);
        LobbyConfig.Scoreboard board = plugin.lobbyConfig().scoreboard();
        String joined = board.dateFormat().format(Instant.ofEpochMilli(player.getFirstPlayed()).atZone(board.timeZone()));

        LobbyConfig.ChatColor colour = plugin.chatColors().chosen(player);
        LobbyConfig.Trail trail = plugin.trails().chosen(player);
        LobbyConfig.Status status = plugin.statuses().chosen(player);
        int parkourRank = plugin.parkour().leaderboard().rank(player);

        return TagResolver.resolver(
                Messages.name("player", player.getName()),
                Placeholder.component("rank", rank),
                Messages.arg("joined", joined),
                Messages.arg("playtime", playtime),
                Messages.arg("parkour-best", plugin.parkour().leaderboard().best(player)),
                Placeholder.component("parkour-rank", parkourRank > 0
                        ? messages.component("parkour.rank", Messages.arg("position", parkourRank))
                        : messages.component("parkour.unranked")),
                Placeholder.component("chat-color", colour == null ? messages.component("profile.default") : messages.render(colour.name())),
                Placeholder.component("status", status == null ? messages.component("profile.none") : plugin.statuses().label(status)),
                Placeholder.component("trail", trail == null ? messages.component("profile.none") : messages.render(trail.name())),
                Placeholder.component("glow", messages.component(player.isGlowing() ? "settings.options.on" : "settings.options.off")));
    }

    /** A menu button: name and lore from messages.yml (key.name, key.lore). */
    static ItemStack button(Messages messages, Material material, String key, TagResolver... args) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            meta.displayName(messages.item(key + ".name", args));
            meta.lore(messages.lore(key + ".lore", args));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return item;
    }

    /** Without this, the game adds a line under a head's name about its skin ("Dynamic"). */
    static void hideProfileLine(ItemStack head) {
        head.setData(io.papermc.paper.datacomponent.DataComponentTypes.TOOLTIP_DISPLAY,
                io.papermc.paper.datacomponent.item.TooltipDisplay.tooltipDisplay()
                        .addHiddenComponents(io.papermc.paper.datacomponent.DataComponentTypes.PROFILE).build());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClick() != ClickType.LEFT) return; // every button here says Left-click

        switch (event.getRawSlot()) {
            case CHAT_COLOR -> {
                if (plugin.lobbyConfig().chatColors().enabled() && ChatColors.canUse(player)) {
                    click(player);
                    plugin.chatColors().open(player);
                } else {
                    locked(player);
                }
            }
            case STATUS -> {
                if (!plugin.statuses().enabled()) return;
                click(player);
                plugin.statuses().open(player);
            }
            case GLOW -> {
                if (!GlowListener.allowed(player)) {
                    locked(player);
                    return;
                }
                plugin.glow().toggle(player);
                click(player);
                open(player);
            }
            case FRIENDS -> {
                if (plugin.coreLink().state(player) == null) return;
                click(player);
                plugin.friendsMenu().open(player);
            }
            case SETTINGS -> {
                click(player);
                plugin.settings().open(player, true);
            }
            case PARKOUR -> plugin.parkour().openLeaderboard(player);
            default -> {
            }
        }
    }

    private void locked(Player player) {
        player.playSound(Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 0.5f, 1f), Sound.Emitter.self());
        plugin.messages().send(player, "profile.locked");
    }

    private static void click(Player player) {
        player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, 1.2f), Sound.Emitter.self());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }
}
