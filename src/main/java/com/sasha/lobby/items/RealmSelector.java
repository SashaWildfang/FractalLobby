/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.proxy.ServerStatus;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The realm selector menu. Each realm shows whether it's online, full, under maintenance or offline, and
 * how many are playing (kept up to date while the menu is open). Clicking a realm that's up sends the
 * player there through the proxy; otherwise they're told why not.
 */
public final class RealmSelector implements Listener {

    public static final String BYPASS_MAINTENANCE = "lobby.bypass.maintenance";
    public static final String BYPASS_FULL = "lobby.bypass.full";

    private final Lobby plugin;
    private BukkitTask refreshTask;

    public RealmSelector(Lobby plugin) {
        this.plugin = plugin;
    }

    /** Redraws open menus every second, so counts and status stay current. */
    public void start() {
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) draw(menu);
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (refreshTask != null) refreshTask.cancel();
    }

    /** The open menu; being the inventory's holder is how its clicks are recognised. */
    private static final class Menu implements InventoryHolder {
        private final Player viewer;
        private Inventory inventory;
        private final Map<Integer, LobbyConfig.SelectorServer> realms = new HashMap<>();

        Menu(Player viewer) {
            this.viewer = viewer;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player) {
        LobbyConfig.Selector config = plugin.lobbyConfig().selector();
        Menu menu = new Menu(player);
        menu.inventory = Bukkit.createInventory(menu, config.rows() * 9, plugin.messages().component("realm-selector.title"));
        ItemStack filler = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.setHideTooltip(true));
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);
        draw(menu);
        player.openInventory(menu.inventory);
    }

    /**
     * Whether a realm is left out of the menu for this player: offline realms always (with hide-down-realms
     * on), and realms under maintenance unless the player can join them anyway (lobby.bypass.maintenance).
     */
    private boolean hidden(Player viewer, ServerStatus.State state) {
        if (!plugin.lobbyConfig().selector().hideDownRealms()) return false;
        return state == ServerStatus.State.OFFLINE
                || state == ServerStatus.State.MAINTENANCE && !viewer.hasPermission(BYPASS_MAINTENANCE);
    }

    private void draw(Menu menu) {
        Messages messages = plugin.messages();
        ItemStack filler = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.setHideTooltip(true));
        for (LobbyConfig.SelectorServer realm : plugin.lobbyConfig().selector().servers()) {
            ServerStatus.State state = plugin.serverStatus().status(realm.server()).state();
            if (hidden(menu.viewer, state)) {
                menu.inventory.setItem(realm.slot(), filler);
                menu.realms.remove(realm.slot());
                continue;
            }
            TagResolver args = args(realm);
            List<Component> lore = new ArrayList<>();
            for (String line : realm.description()) lore.add(messages.render("<!italic>" + line, args));
            lore.addAll(messages.lore("realm-selector.button-lore", args));

            ItemStack item = ItemStack.of(state == ServerStatus.State.OFFLINE ? Material.GRAY_DYE : realm.item());
            item.editMeta(meta -> {
                meta.displayName(messages.render("<!italic>" + realm.name(), args));
                meta.lore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            });
            menu.inventory.setItem(realm.slot(), item);
            menu.realms.put(realm.slot(), realm);
        }
    }

    /** Tags for a realm: <online> <max> <players> <server> <realm> <status> <action>. */
    private TagResolver args(LobbyConfig.SelectorServer realm) {
        Messages messages = plugin.messages();
        ServerStatus.Status status = plugin.serverStatus().status(realm.server());
        String state = (status.state() == ServerStatus.State.UNKNOWN ? ServerStatus.State.ONLINE : status.state())
                .name().toLowerCase(Locale.ROOT);
        int online = plugin.proxyCounts().count(realm.server());
        TagResolver counts = TagResolver.resolver(Messages.arg("online", online), Messages.arg("max", status.max()));
        Component players = messages.component(status.max() > 0 ? "realm-selector.players-max" : "realm-selector.players", counts);
        return TagResolver.resolver(counts, Messages.arg("server", realm.server()),
                Placeholder.component("realm", messages.render(realm.name())),
                Placeholder.component("players", players),
                Placeholder.component("status", messages.component("realm-selector.status." + state)),
                Placeholder.component("action", messages.component("realm-selector.action." + state)));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        LobbyConfig.SelectorServer realm = menu.realms.get(event.getRawSlot());
        if (realm == null) return;

        TagResolver args = args(realm);
        String refusal = switch (plugin.serverStatus().status(realm.server()).state()) {
            case OFFLINE -> "realm-selector.offline";
            case MAINTENANCE -> player.hasPermission(BYPASS_MAINTENANCE) ? null : "realm-selector.maintenance";
            case FULL -> player.hasPermission(BYPASS_FULL) ? null : "realm-selector.full";
            default -> null;
        };
        if (refusal != null) {
            player.playSound(Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 0.5f, 1f), Sound.Emitter.self());
            plugin.messages().send(player, refusal, args);
            return;
        }
        player.closeInventory();
        player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, 1.2f), Sound.Emitter.self());
        plugin.messages().send(player, "realm-selector.connecting", args);
        plugin.proxyCounts().connect(player, realm.server());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }
}
