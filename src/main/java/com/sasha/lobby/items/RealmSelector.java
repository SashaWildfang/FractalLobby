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
import net.kyori.adventure.text.format.NamedTextColor;
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
        /** With the custom background: which layout it was drawn for (how many realm squares). */
        private int background = -1;
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
        Component title = plugin.messages().component("realm-selector.title");
        if (config.customGui()) {
            menu.background = layout(shown(player).size());
            title = customGuiTitle(config.rows(), menu.background);
        }
        menu.inventory = Bukkit.createInventory(menu, config.rows() * 9, title);
        ItemStack filler = filler();
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

    /** The font in the Fractal Lobby menus resource pack (FractalLobbyGUI.zip). */
    private static final Key GUI_FONT = Key.key("fractal", "lobby");

    /**
     * The menu's title, which is the resource pack's background (it has "Realms" drawn on it, like
     * Survival's menus): move 8px left to the menu's edge, draw the background for these rows with a
     * square behind each of this many centred realms (0 = no squares), then move back. White, so the
     * picture isn't tinted like normal title text.
     */
    static Component customGuiTitle(int rows, int realms) {
        String background = "\uF001" + (char) (0xF100 + rows * 16 + realms) + "\uF002";
        return Component.text(background).font(GUI_FONT).color(NamedTextColor.WHITE);
    }

    /** Which background fits: squares for centred realms (up to 9), otherwise none. */
    private int layout(int shown) {
        return plugin.lobbyConfig().selector().autoCenter() && shown <= 9 ? shown : 0;
    }

    /** The realms this player sees, in slot order. */
    private List<LobbyConfig.SelectorServer> shown(Player viewer) {
        List<LobbyConfig.SelectorServer> shown = new ArrayList<>();
        for (LobbyConfig.SelectorServer realm : plugin.lobbyConfig().selector().servers()) {
            if (plugin.lobbyConfig().selector().hideDownRealms() && !plugin.serverStatus().known(realm.server())) continue;
            if (!hidden(viewer, plugin.serverStatus().status(realm.server()).state())) shown.add(realm);
        }
        shown.sort(java.util.Comparator.comparingInt(LobbyConfig.SelectorServer::slot));
        return shown;
    }

    /** Black panes in empty slots, or nothing with the custom background (it draws its own slots). */
    private ItemStack filler() {
        if (plugin.lobbyConfig().selector().customGui()) return null;
        ItemStack filler = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.setHideTooltip(true));
        return filler;
    }

    private void draw(Menu menu) {
        Messages messages = plugin.messages();
        LobbyConfig.Selector config = plugin.lobbyConfig().selector();
        ItemStack filler = filler();

        // Which realms this player sees, in slot order.
        List<LobbyConfig.SelectorServer> shown = shown(menu.viewer);
        if (config.customGui() && menu.background >= 0 && layout(shown.size()) != menu.background) {
            // A realm went down or came back: the background's squares are in the title, so open it afresh.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (menu.viewer.getOpenInventory().getTopInventory() == menu.inventory) open(menu.viewer);
            });
            return;
        }
        int[] slots = config.autoCenter() ? centered(shown.size(), config.rows()) : null;

        // Clear where realms were last time (they move when one goes down or comes back).
        for (int slot : menu.realms.keySet()) menu.inventory.setItem(slot, filler);
        menu.realms.clear();

        for (int i = 0; i < shown.size(); i++) {
            LobbyConfig.SelectorServer realm = shown.get(i);
            int slot = slots != null ? slots[i] : realm.slot();
            ServerStatus.State state = plugin.serverStatus().status(realm.server()).state();
            TagResolver args = args(realm);
            List<Component> lore = new ArrayList<>();
            for (String line : realm.description()) lore.add(messages.render("<!italic>" + line, args));
            lore.addAll(messages.lore("realm-selector.button-lore", args));

            ItemStack item = ItemStack.of(state == ServerStatus.State.OFFLINE ? Material.GRAY_DYE : realm.item());
            item.editMeta(meta -> {
                meta.displayName(messages.render("<!italic>" + realm.name(), args));
                meta.lore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                // The resource pack's icon for the realm (an offline realm keeps its grey dye).
                if (realm.itemModel() != null && state != ServerStatus.State.OFFLINE) meta.setItemModel(realm.itemModel());
            });
            menu.inventory.setItem(slot, item);
            menu.realms.put(slot, realm);
        }
    }

    /**
     * Slots for this many realms, centred on the middle row: one gap between them while they fit
     * (1 → the middle, 3 → 11 13 15 in a 3-row menu), side by side up to 9, or null for more
     * (then each realm keeps its slot from config.yml).
     */
    static int[] centered(int count, int rows) {
        if (count == 0 || count > 9) return count == 0 ? new int[0] : null;
        int gap = count <= 5 ? 2 : 1;
        int first = 4 - (count - 1) * gap / 2;
        int row = (rows - 1) / 2;
        int[] slots = new int[count];
        for (int i = 0; i < count; i++) slots[i] = row * 9 + first + i * gap;
        return slots;
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
