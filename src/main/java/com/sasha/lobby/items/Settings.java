/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.proxy.CoreLink;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's settings (the comparator join item). Lobby settings are saved on the player: some choose
 * whose messages or players to see (Everyone, Donors Only, Staff Only, Nobody), the rest are On or Off.
 * Clicking the switch under one moves it to its next option (right-click goes back). Network settings (private messages,
 * message sounds, friend requests, friend join/leave messages, who you ignore, and staff's social spy, command spy and vanish) belong to Core on the
 * proxy, so they follow the player to every realm; they show when Core is installed.
 */
public final class Settings implements Listener {

    /** Who a player sees something from. Staff count as donors. */
    public enum Audience {
        EVERYONE, DONORS, STAFF, NOBODY
    }

    /** The two kinds of setting: a choice of audience, or on/off. */
    private enum Kind {
        AUDIENCE(List.of("everyone", "donors", "staff", "nobody")),
        TOGGLE(List.of("on", "off"));

        final List<String> options;

        Kind(List<String> options) {
            this.options = options;
        }
    }

    /** One setting: its id in messages.yml, its icon, its menu slot and its options. */
    public enum Setting {
        JOIN_MESSAGES("join-messages", Material.OAK_DOOR, 1, Kind.AUDIENCE),
        LEAVE_MESSAGES("leave-messages", Material.IRON_DOOR, 2, Kind.AUDIENCE),
        CHAT("chat", Material.WRITABLE_BOOK, 3, Kind.AUDIENCE),
        PLAYER_VISIBILITY("player-visibility", Material.ENDER_EYE, 4, Kind.AUDIENCE),
        MENTIONS("mentions", Material.NOTE_BLOCK, 5, Kind.TOGGLE),
        DOUBLE_JUMP("double-jump", Material.FEATHER, 6, Kind.TOGGLE),
        SCOREBOARD("scoreboard", Material.PAINTING, 7, Kind.TOGGLE);

        final String id;
        final Material icon;
        final int slot;
        final Kind kind;

        Setting(String id, Material icon, int slot, Kind kind) {
            this.id = id;
            this.icon = icon;
            this.slot = slot;
            this.kind = kind;
        }
    }

    private static final int BACK = 45;

    private final Lobby plugin;
    private final Map<Setting, NamespacedKey> keys = new EnumMap<>(Setting.class);
    /** Before settings had options, hiding players was its own on/off flag. */
    private final NamespacedKey oldHiddenKey;
    /** Each online player's choices (also read from chat threads, hence concurrent). */
    private final Map<UUID, Map<Setting, String>> cache = new ConcurrentHashMap<>();

    public Settings(Lobby plugin) {
        this.plugin = plugin;
        for (Setting setting : Setting.values()) keys.put(setting, new NamespacedKey(plugin, "setting-" + setting.id));
        oldHiddenKey = new NamespacedKey(plugin, "players-hidden");
    }

    // ---------------------------------------------------------------- reading

    private String option(Player player, Setting setting) {
        Map<Setting, String> choices = cache.get(player.getUniqueId());
        if (choices == null) choices = load(player);
        return choices.getOrDefault(setting, setting.kind.options.getFirst());
    }

    /** For on/off settings: whether it's on. */
    public boolean on(Player player, Setting setting) {
        return !option(player, setting).equals("off");
    }

    /** For audience settings: who the player sees it from. */
    public Audience audience(Player player, Setting setting) {
        return switch (option(player, setting)) {
            case "donors" -> Audience.DONORS;
            case "staff" -> Audience.STAFF;
            case "nobody" -> Audience.NOBODY;
            default -> Audience.EVERYONE;
        };
    }

    /** Whether the viewer sees the subject's messages or self, by the viewer's choice for this setting. */
    public boolean shows(Player viewer, Setting setting, Player subject) {
        if (viewer.equals(subject)) return true;
        return switch (audience(viewer, setting)) {
            case EVERYONE -> true;
            case DONORS -> isDonor(subject);
            case STAFF -> isStaff(subject);
            case NOBODY -> false;
        };
    }

    public boolean isStaff(Player player) {
        return player.hasPermission(plugin.lobbyConfig().ranks().staffPermission());
    }

    /** Donors, and staff too. */
    public boolean isDonor(Player player) {
        return isStaff(player) || player.hasPermission(plugin.lobbyConfig().ranks().donorPermission());
    }

    private Map<Setting, String> load(Player player) {
        Map<Setting, String> choices = new ConcurrentHashMap<>();
        for (Setting setting : Setting.values()) {
            var data = player.getPersistentDataContainer();
            NamespacedKey key = keys.get(setting);
            String value = null;
            if (data.has(key, PersistentDataType.STRING)) {
                value = data.get(key, PersistentDataType.STRING);
            } else if (data.has(key, PersistentDataType.BOOLEAN)) {
                // Earlier versions saved these as true/false: true = everyone/on, false = nobody/off.
                value = Boolean.TRUE.equals(data.get(key, PersistentDataType.BOOLEAN))
                        ? setting.kind.options.getFirst() : setting.kind.options.getLast();
            }
            if (value == null && setting == Setting.PLAYER_VISIBILITY
                    && player.getPersistentDataContainer().getOrDefault(oldHiddenKey, PersistentDataType.BOOLEAN, false)) {
                value = "nobody";
            }
            if (value != null && setting.kind.options.contains(value)) choices.put(setting, value);
        }
        cache.put(player.getUniqueId(), choices);
        return choices;
    }

    // ---------------------------------------------------------------- changing

    /** Sets a setting and applies it straight away. */
    public void set(Player player, Setting setting, String value) {
        player.getPersistentDataContainer().remove(keys.get(setting)); // drops an old true/false value too
        player.getPersistentDataContainer().set(keys.get(setting), PersistentDataType.STRING, value);
        Map<Setting, String> choices = cache.get(player.getUniqueId());
        if (choices == null) choices = load(player);
        choices.put(setting, value);

        switch (setting) {
            case SCOREBOARD -> plugin.display().showSidebar(player, on(player, setting));
            case PLAYER_VISIBILITY -> plugin.joinItems().applyVisibility(player);
            case DOUBLE_JUMP -> plugin.doubleJump().refresh(player);
            default -> {
            }
        }
    }

    /** Moves a setting to its next option (or the one before). */
    private void cycle(Player player, Setting setting, boolean back) {
        List<String> options = setting.kind.options;
        int index = options.indexOf(option(player, setting));
        int next = Math.floorMod(index + (back ? -1 : 1), options.size());
        set(player, setting, options.get(next));
        click(player, next);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- menu

    /*
     * Six rows: Lobby settings (icons, then the switches under them); a row of space; Network settings from
     * Core (icons, then switches); Back and Reset at the bottom. Each row of icons starts with a label. Only the
     * switches are clicked; the icons above say what each one is.
     */
    private static final int LOBBY_LABEL = 0;
    private static final int NETWORK_LABEL = 27;
    private static final int NETWORK_UNAVAILABLE = 31;
    private static final int RESET = 49;
    /** The ignored-players list shows this many heads (Core keeps the rest; /unignore reaches them). */
    private static final int IGNORED_SHOWN = 45;

    /** A network setting kept by Core: its id in messages.yml, Core's toggle, whether Core's toggle means "off". */
    private enum NetworkSetting {
        PRIVATE_MESSAGES("private-messages", Material.PAPER, 28, CoreLink.MESSAGES_OFF, true),
        MESSAGE_SOUNDS("message-sounds", Material.BELL, 29, CoreLink.MESSAGE_SOUNDS_OFF, true),
        FRIEND_REQUESTS("friend-requests", Material.WRITABLE_BOOK, 30, CoreLink.FRIEND_REQUESTS_OFF, true),
        FRIEND_NOTIFY("friend-notify", Material.EMERALD, 31, CoreLink.FRIEND_NOTIFY_OFF, true),
        SOCIAL_SPY("social-spy", Material.SPYGLASS, 32, CoreLink.SOCIAL_SPY, false),
        COMMAND_SPY("command-spy", Material.COMMAND_BLOCK, 33, CoreLink.COMMAND_SPY, false),
        VANISH("vanish", Material.PHANTOM_MEMBRANE, 34, CoreLink.VANISH, false);

        final String id;
        final Material icon;
        final int slot;
        final String toggle;
        final boolean inverted;

        NetworkSetting(String id, Material icon, int slot, String toggle, boolean inverted) {
            this.id = id;
            this.icon = icon;
            this.slot = slot;
            this.toggle = toggle;
            this.inverted = inverted;
        }

        boolean on(CoreLink.State state) {
            return state.on(toggle) != inverted;
        }
    }

    private enum Page {
        SETTINGS, IGNORED
    }

    private interface Action {
        void click(Player player, ClickType click);
    }

    private static final class Menu implements InventoryHolder {
        private final Page page;
        /** Opened from the profile (then Back returns there); from the hotbar item there's no Back. */
        private final boolean fromProfile;
        private final Map<Integer, Action> actions = new java.util.HashMap<>();
        private Inventory inventory;

        Menu(Page page, boolean fromProfile) {
            this.page = page;
            this.fromProfile = fromProfile;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Opens settings from the hotbar item (no Back button). */
    public void open(Player player) {
        open(player, false);
    }

    /** Opens settings; from the profile, a Back button returns there. */
    public void open(Player player, boolean fromProfile) {
        Menu menu = new Menu(Page.SETTINGS, fromProfile);
        menu.inventory = Bukkit.createInventory(menu, 54, customGui()
                ? RealmSelector.backgroundTitle(MenuStyle.SETTINGS) : plugin.messages().component("settings.title"));
        draw(player, menu);
        player.openInventory(menu.inventory);
    }

    /** Opens the list of ignored players (from the profile; its Back button returns there). */
    public void openIgnored(Player player) {
        Menu menu = new Menu(Page.IGNORED, true);
        menu.inventory = Bukkit.createInventory(menu, 54, customGui()
                ? RealmSelector.backgroundTitle(MenuStyle.IGNORED) : plugin.messages().component("settings.ignored-menu.title"));
        draw(player, menu);
        player.openInventory(menu.inventory);
    }

    /** Redraws the player's open settings menu, if they have one (after Core sends new settings). */
    public void refresh(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) draw(player, menu);
    }

    /** Fills the menu in place (so the cursor stays where it is). */
    private void draw(Player player, Menu menu) {
        menu.actions.clear();
        ItemStack filler = null; // the custom background draws the menu itself
        if (!customGui()) {
            filler = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
            filler.editMeta(meta -> meta.setHideTooltip(true));
        }
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);
        if (menu.page == Page.SETTINGS) drawSettings(player, menu);
        else drawIgnored(player, menu);
    }

    private void drawSettings(Player player, Menu menu) {
        Messages messages = plugin.messages();
        menu.inventory.setItem(LOBBY_LABEL, styled(Profile.button(messages, Material.GRASS_BLOCK, "settings.lobby-section"), "lobby"));
        menu.inventory.setItem(NETWORK_LABEL, styled(Profile.button(messages, Material.ENDER_PEARL, "settings.network-section"), "network"));

        for (Setting setting : Setting.values()) {
            put(menu, setting.slot, setting.icon, setting.id, setting.kind.options, option(player, setting),
                    (who, click) -> cycle(who, setting, back(click)));
        }

        CoreLink.State state = plugin.coreLink().state(player);
        if (state == null) {
            menu.inventory.setItem(NETWORK_UNAVAILABLE, styled(Profile.button(messages, Material.BARRIER, "settings.network-unavailable"), "unavailable"));
        } else {
            for (NetworkSetting setting : NetworkSetting.values()) {
                if (!state.has(setting.toggle)) continue; // staff settings only show for staff
                put(menu, setting.slot, setting.icon, setting.id, Kind.TOGGLE.options, setting.on(state) ? "on" : "off",
                        (who, click) -> flip(who, setting));
            }
        }

        if (menu.fromProfile) {
            menu.inventory.setItem(BACK, styled(Profile.button(messages, Material.ARROW, "profile.back"), "back"));
            menu.actions.put(BACK, (who, click) -> plugin.profile().open(who));
        }
        menu.inventory.setItem(RESET, styled(Profile.button(messages, Material.TNT_MINECART, "settings.reset"), "reset"));
        menu.actions.put(RESET, (who, click) -> {
            if (!click.isShiftClick()) return;
            reset(who);
            draw(who, menu);
        });
    }

    /** A setting's icon, listing its options with the current one marked, and a coloured pane under it. */
    private void put(Menu menu, int slot, Material material, String id, List<String> options, String current, Action action) {
        Messages messages = plugin.messages();
        List<Component> lore = new ArrayList<>(messages.lore("settings." + id + ".lore"));
        lore.add(Component.empty());
        for (String option : options) {
            Component name = messages.component("settings.options." + option);
            lore.add(messages.item(option.equals(current) ? "settings.option-selected" : "settings.option",
                    Placeholder.component("option", name)));
        }
        lore.add(Component.empty());
        lore.addAll(messages.lore("settings.switch-below"));

        ItemStack icon = ItemStack.of(material);
        icon.editMeta(meta -> {
            meta.displayName(messages.item("settings." + id + ".name"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        menu.inventory.setItem(slot, styled(icon, id));

        ItemStack pane = ItemStack.of(paneFor(current));
        pane.editMeta(meta -> {
            meta.displayName(messages.item("settings." + id + ".name"));
            List<Component> paneLore = new ArrayList<>();
            paneLore.add(messages.item("settings.current",
                    Placeholder.component("option", messages.component("settings.options." + current))));
            paneLore.add(Component.empty());
            paneLore.addAll(messages.lore(options.size() > 2 ? "settings.hint" : "settings.toggle-hint"));
            meta.lore(paneLore);
        });
        menu.inventory.setItem(slot + 9, styled(pane, paneModel(current)));
        menu.actions.put(slot + 9, action); // only the switch; the icon above just says what it is
    }

    /** The Ignored Players button (shown in the profile): how many, who, and a click opens the list. */
    public ItemStack ignoredButton(CoreLink.State state) {
        Messages messages = plugin.messages();
        Map<UUID, String> ignored = state.ignored();
        List<Component> lore = new ArrayList<>(messages.lore("settings.ignored.lore"));
        lore.add(Component.empty());
        if (ignored.isEmpty()) {
            lore.addAll(messages.lore("settings.ignored.none"));
        } else {
            int shown = 0;
            for (String name : ignored.values()) {
                if (shown++ == 6) break;
                lore.add(messages.item("settings.ignored.entry", Messages.name("player", name)));
            }
            if (ignored.size() > 6) lore.add(messages.item("settings.ignored.more", Messages.arg("count", ignored.size() - 6)));
        }
        lore.add(Component.empty());
        lore.addAll(messages.lore("settings.ignored.hint"));

        ItemStack icon = ItemStack.of(Material.SKELETON_SKULL);
        icon.editMeta(meta -> {
            meta.displayName(messages.item("settings.ignored.name"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        return styled(icon, "ignored");
    }

    /** Everyone the player ignores, as heads; clicking one unignores them. */
    private void drawIgnored(Player player, Menu menu) {
        Messages messages = plugin.messages();
        CoreLink.State state = plugin.coreLink().state(player);
        Map<UUID, String> ignored = state == null ? Map.of() : state.ignored();
        if (ignored.isEmpty()) {
            menu.inventory.setItem(22, styled(Profile.button(messages, Material.LIME_DYE, "settings.ignored-menu.empty"), "none"));
        }
        int slot = 0;
        for (Map.Entry<UUID, String> entry : ignored.entrySet()) {
            if (slot == IGNORED_SHOWN) break;
            UUID id = entry.getKey();
            String name = entry.getValue();
            ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
            Player online = plugin.getServer().getPlayer(id);
            head.editMeta(SkullMeta.class, meta -> {
                meta.setPlayerProfile(online != null ? online.getPlayerProfile() : Bukkit.createProfile(id, name));
                meta.displayName(messages.item("settings.ignored-menu.head.name", Messages.name("player", name)));
                meta.lore(messages.lore("settings.ignored-menu.head.lore", Messages.name("player", name)));
            });
            Profile.hideProfileLine(head);
            menu.inventory.setItem(slot, head);
            menu.actions.put(slot, (who, click) -> {
                plugin.coreLink().unignore(who, id);
                messages.send(who, "settings.ignored-menu.removed", Messages.name("player", name));
                click(who, 1);
                draw(who, menu);
            });
            slot++;
        }
        if (ignored.size() > IGNORED_SHOWN) {
            menu.inventory.setItem(53, styled(Profile.button(messages, Material.PAPER, "settings.ignored-menu.more",
                    Messages.arg("count", ignored.size() - IGNORED_SHOWN)), "more"));
        }
        menu.inventory.setItem(49, styled(Profile.button(messages, Material.ARROW, "settings.ignored-menu.back"), "back"));
        menu.actions.put(49, (who, click) -> {
            plugin.friendsMenu().open(who); // the ignored list is opened from the friends menu
            click(who, 0);
        });
    }

    private static boolean back(ClickType click) {
        return click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
    }

    private static void click(Player player, int step) {
        player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, 0.8f + step * 0.15f),
                Sound.Emitter.self());
    }

    /** Flips a network setting through Core. */
    private void flip(Player player, NetworkSetting setting) {
        CoreLink.State state = plugin.coreLink().state(player);
        if (state == null) return;
        boolean nowOn = !setting.on(state);
        plugin.coreLink().set(player, setting.toggle, nowOn != setting.inverted);
        click(player, nowOn ? 0 : 1);
        if (setting == NetworkSetting.VANISH) plugin.joinItems().refreshSeenBy(player);
    }

    /** Puts every lobby setting back to its default. */
    private void reset(Player player) {
        for (Setting setting : Setting.values()) {
            if (!option(player, setting).equals(setting.kind.options.getFirst())) set(player, setting, setting.kind.options.getFirst());
        }
        plugin.messages().send(player, "settings.reset.done");
        player.playSound(Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER, 0.5f, 1f), Sound.Emitter.self());
    }

    /** Whether the menus use the resource pack's backgrounds and icons (settings-menu.custom-gui). */
    private boolean customGui() {
        return plugin.menuStyle().custom();
    }

    /** With the custom look, gives the item the resource pack's icon fractal:menu/NAME. */
    private ItemStack styled(ItemStack item, String name) {
        if (customGui()) item.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", "menu/" + name)));
        return item;
    }

    /**
     * The icon under a setting: an on/off switch, or for "whose ... you see" settings a picture of the
     * choice (a globe for Everyone, a gem for Donors Only, a shield for Staff Only, a no sign for Nobody).
     */
    private static String paneModel(String option) {
        return switch (option) {
            case "on" -> "on";
            case "off" -> "off";
            default -> "audience-" + option;
        };
    }

    private static Material paneFor(String option) {
        return switch (option) {
            case "everyone", "on" -> Material.LIME_STAINED_GLASS_PANE;
            case "donors" -> Material.YELLOW_STAINED_GLASS_PANE;
            case "staff" -> Material.ORANGE_STAINED_GLASS_PANE;
            default -> Material.RED_STAINED_GLASS_PANE;
        };
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Action action = menu.actions.get(event.getRawSlot());
        if (action == null) return;
        action.click(player, event.getClick());
        if (menu.page == Page.SETTINGS && player.getOpenInventory().getTopInventory() == menu.inventory) draw(player, menu);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }
}
