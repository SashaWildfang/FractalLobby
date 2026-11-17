/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.collectibles;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.collectibles.Catalog.Category;
import com.sasha.lobby.collectibles.Catalog.Item;
import com.sasha.lobby.collectibles.Catalog.Season;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.items.MenuStyle;
import com.sasha.lobby.mystery.MysteryData;
import com.sasha.lobby.mystery.Reward;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Collectibles, like Hypixel's: a join item opens the Collectibles menu with hats, suits, gadgets, cloaks, emotes and
 * gestures (plus the trails, chat colours and the Mystery Vault). Hats and suits are sorted into seasons. Everything
 * is found in Mystery Boxes or bought with Mystery Dust (seasonal things only in their season).
 *
 * <p>Wearing: a hat goes on the head; a suit is all four armour pieces, and the full set puts its ability in the
 * hotbar (ability-slot); a gadget goes in the hotbar (gadget-slot); a cloak plays particles around the player. Emotes
 * and gestures are done from the menu (or /emote, /gesture). What's worn is remembered.
 */
public final class Collectibles implements Listener {

    /** Everything, for staff testing. */
    public static final String ALL = "lobby.collectibles.all";
    private static final char MAIN_MENU = '';
    private static final Map<Category, Character> PAGE_MENU = Map.of(Category.HAT, '', Category.SUIT, '',
            Category.GADGET, '', Category.CLOAK, '', Category.EMOTE, '', Category.GESTURE, '');
    /** Main menu: the stash; the categories; the extras. */
    private static final int STASH = 4, TRAILS = 25, CHAT_COLOURS = 38, VAULT = 40, TAKE_OFF_ALL = 42;
    private static final int[] CATEGORY_SLOTS = {19, 20, 21, 22, 23, 24};
    /** Category page: season tabs on top, items in the middle, the bottom row. */
    private static final int[] TAB_SLOTS = {2, 3, 4, 5, 6};
    private static final int FIRST_ITEM = 9, LAST_ITEM = 44, BACK = 45, TAKE_OFF = 49;

    private final Lobby plugin;
    private final NamespacedKey itemKey;
    private final Effects effects;
    private final Map<String, Long> cooldowns = new HashMap<>();

    public Collectibles(Lobby plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "collectible");
        this.effects = new Effects(plugin, this);
    }

    public void start() {
        effects.start();
    }

    public void stop() {
        effects.stop();
    }

    Effects effects() {
        return effects;
    }

    /** The listener for thrown things and eggs (registered by the plugin). */
    public org.bukkit.event.Listener effectsListener() {
        return effects;
    }

    // ---------------------------------------------------------------- owning

    private MysteryData data() {
        return plugin.mystery().data();
    }

    public boolean owns(Player player, Item item) {
        return player.hasPermission(ALL) || data().get(player.getUniqueId()).owns(item.id());
    }

    /** Whether it's that item's season now (Normal always is). */
    public boolean inSeason(Item item) {
        List<String> forced = plugin.getConfig().getStringList("collectibles.seasons-on");
        if (forced.contains(item.season().key())) return true;
        return item.season().on(LocalDate.now(plugin.lobbyConfig().scoreboard().timeZone()));
    }

    public long price(Item item) {
        long[] defaults = {100, 250, 600, 1500};
        return Math.max(1, plugin.getConfig().getLong("collectibles.price." + item.rarity().key(), defaults[item.rarity().ordinal()]));
    }

    private Item equipped(Player player, Category category) {
        return Catalog.get(data().get(player.getUniqueId()).equipped(category.prefix()));
    }

    // ---------------------------------------------------------------- wearing

    /** Puts on what the player has equipped (on join, after a reload, after parkour). */
    public void apply(Player player) {
        if (!enabled()) return;
        PlayerInventory inventory = player.getInventory();
        Item hat = equipped(player, Category.HAT), suit = equipped(player, Category.SUIT), gadget = equipped(player, Category.GADGET);
        if (suit != null && owns(player, suit)) {
            inventory.setHelmet(hat != null && owns(player, hat) ? hatItem(hat) : armour(suit, Material.LEATHER_HELMET, suit.suit().helmet()));
            inventory.setChestplate(armour(suit, Material.LEATHER_CHESTPLATE, suit.suit().chest()));
            inventory.setLeggings(armour(suit, Material.LEATHER_LEGGINGS, suit.suit().legs()));
            inventory.setBoots(armour(suit, Material.LEATHER_BOOTS, suit.suit().boots()));
        } else {
            inventory.setHelmet(hat != null && owns(player, hat) ? hatItem(hat) : null);
            inventory.setChestplate(null);
            inventory.setLeggings(null);
            inventory.setBoots(null);
        }
        int abilitySlot = slot("collectibles.ability-slot", 6), gadgetSlot = slot("collectibles.gadget-slot", 4);
        boolean fullSuit = suit != null && owns(player, suit) && (hat == null || !owns(player, hat));
        inventory.setItem(abilitySlot, fullSuit ? effects.abilityItem(suit, tag(suit.suit().ability(), "ability")) : null);
        inventory.setItem(gadgetSlot, gadget != null && owns(player, gadget) ? gadgetItem(gadget) : null);
        if (plugin.getConfig().getBoolean("join-items.collectibles.enabled", true)) {
            inventory.setItem(slot("join-items.collectibles.slot", 3), joinItem());
        }
    }

    private int slot(String path, int fallback) {
        return Math.max(0, Math.min(8, plugin.getConfig().getInt(path, fallback) - 1));
    }

    private void equip(Player player, Item item) {
        MysteryData data = data();
        UUID id = player.getUniqueId();
        switch (item.category()) {
            case HAT -> data.equip(id, "hat", item.id());
            case SUIT -> {
                data.equip(id, "suit", item.id());
                data.equip(id, "hat", null); // the suit's own helmet: the full set
            }
            case GADGET -> data.equip(id, "gadget", item.id());
            case CLOAK -> data.equip(id, "cloak", item.id());
            default -> {
            }
        }
        apply(player);
        plugin.messages().send(player, "collectibles.equipped", name(item));
        sound(player, "item.armor.equip_leather", 1.2f);
    }

    private void takeOff(Player player, Category category) {
        data().equip(player.getUniqueId(), category.prefix(), null);
        apply(player);
        plugin.messages().send(player, "collectibles.took-off", Placeholder.parsed("category",
                plugin.messages().raw("collectibles.category." + category.key + ".name")));
        sound(player, "item.armor.equip_generic", 0.8f);
    }

    private void takeOffAll(Player player) {
        for (Category category : Category.values()) if (!category.action()) data().equip(player.getUniqueId(), category.prefix(), null);
        apply(player);
        plugin.messages().send(player, "collectibles.took-off-all");
        sound(player, "item.armor.equip_generic", 0.8f);
    }

    /** The cloak the player wears now, if they still own it. */
    Item cloak(Player player) {
        Item cloak = equipped(player, Category.CLOAK);
        return cloak != null && owns(player, cloak) ? cloak : null;
    }

    /** A hat as an item (for the head and the menu). */
    ItemStack hatItem(Item hat) {
        ItemStack item = ItemStack.of(Material.PAPER);
        item.editMeta(meta -> {
            meta.displayName(plugin.messages().item("collectibles.worn-name", name(hat)));
            meta.setItemModel(new NamespacedKey("fractal", "hat/" + hat.key()));
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    private ItemStack armour(Item suit, Material material, Color colour) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(LeatherArmorMeta.class, meta -> {
            meta.setColor(colour);
            meta.displayName(plugin.messages().item("collectibles.worn-name", name(suit)));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    private ItemStack gadgetItem(Item gadget) {
        ItemStack item = icon(gadget);
        item.editMeta(meta -> {
            meta.displayName(plugin.messages().item("collectibles.gadget-item.name", name(gadget)));
            meta.lore(plugin.messages().lore("collectibles.gadget-item.lore", TagResolver.resolver(name(gadget),
                    Placeholder.parsed("description", gadget.description()))));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "gadget:" + gadget.key());
        });
        return item;
    }

    private String tag(String value, String kind) {
        return kind + ":" + value;
    }

    NamespacedKey itemKey() {
        return itemKey;
    }

    private ItemStack joinItem() {
        ItemStack item = ItemStack.of(Material.ENDER_CHEST);
        item.editMeta(meta -> {
            meta.displayName(plugin.messages().item("collectibles.join-item.name"));
            meta.lore(plugin.messages().lore("collectibles.join-item.lore"));
            meta.addItemFlags(ItemFlag.values());
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "menu");
        });
        return plugin.menuStyle().icon(item, "collectibles");
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("collectibles.enabled", true) && plugin.mystery() != null;
    }

    // ---------------------------------------------------------------- using hotbar items

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack held = event.getItem();
        if (held == null || !held.hasItemMeta()) return;
        String what = held.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (what == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (what.equals("menu")) {
            sound(player, "block.ender_chest.open", 1.4f);
            open(player);
            return;
        }
        int colon = what.indexOf(':');
        String kind = what.substring(0, colon), key = what.substring(colon + 1);
        if (!ready(player, what, kind.equals("gadget") ? gadgetCooldown(key) : effects.abilityCooldown(key))) return;
        if (kind.equals("gadget")) effects.gadget(player, key);
        else effects.ability(player, key);
    }

    /** Cooldowns, shown in the action bar while waiting. True when it can be used now (and starts the cooldown). */
    boolean ready(Player player, String what, double seconds) {
        String key = player.getUniqueId() + ":" + what;
        long now = System.currentTimeMillis();
        Long until = cooldowns.get(key);
        if (until != null && until > now) {
            player.sendActionBar(plugin.messages().component("collectibles.cooldown",
                    Messages.arg("seconds", String.format(java.util.Locale.ROOT, "%.1f", (until - now) / 1000.0))));
            return false;
        }
        cooldowns.put(key, now + Math.round(seconds * 1000));
        return true;
    }

    private double gadgetCooldown(String key) {
        return switch (key) {
            case "disco_ball" -> 20;
            case "fake_tnt", "bat_blaster", "cookie_fountain" -> 8;
            case "firework_launcher", "snow_cannon", "pumpkin_launcher" -> 3;
            case "party_popper", "bubble_wand" -> 2;
            default -> 0.6; // paintball gun
        };
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String prefix = event.getPlayer().getUniqueId() + ":";
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
        effects.quit(event.getPlayer());
    }

    // ---------------------------------------------------------------- menus

    private static final class Menu implements InventoryHolder {
        private final Category category;   // null: the main menu
        private final Season season;
        private final Map<Integer, Item> items = new HashMap<>();
        private final Map<Integer, Season> tabs = new HashMap<>();
        private Inventory inventory;

        Menu(Category category, Season season) {
            this.category = category;
            this.season = season;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** The Collectibles menu. */
    public void open(Player player) {
        if (!enabled()) return;
        Messages messages = plugin.messages();
        MenuStyle style = plugin.menuStyle();
        Menu menu = new Menu(null, null);
        menu.inventory = Bukkit.createInventory(menu, 54, style.title(MAIN_MENU, messages.component("collectibles.title")));
        fill(menu);
        MysteryData.Stash stash = data().get(player.getUniqueId());
        int found = (int) Catalog.ITEMS.stream().filter(item -> owns(player, item)).count();
        menu.inventory.setItem(STASH, button(Material.GLOWSTONE_DUST, "mystery/dust", "collectibles.stash",
                TagResolver.resolver(Messages.arg("dust", String.format(java.util.Locale.ROOT, "%,d", stash.dust())),
                        Messages.arg("found", found), Messages.arg("total", Catalog.ITEMS.size()), Messages.arg("boxes", stash.totalBoxes()))));
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            Category category = categories[i];
            List<Item> all = Catalog.of(category);
            long owned = all.stream().filter(item -> owns(player, item)).count();
            Item worn = category.action() ? null : equipped(player, category);
            TagResolver tags = TagResolver.resolver(Messages.arg("found", owned), Messages.arg("total", all.size()),
                    Placeholder.component("wearing", worn != null && owns(player, worn)
                            ? messages.component("collectibles.wearing", name(worn)) : messages.component("collectibles.wearing-nothing")));
            ItemStack icon = categoryIcon(category);
            icon.editMeta(meta -> {
                meta.displayName(messages.item("collectibles.category." + category.key + ".name", tags));
                List<Component> lore = new ArrayList<>(messages.lore("collectibles.category." + category.key + ".lore", tags));
                lore.addAll(messages.lore(category.action() ? "collectibles.category-footer-action" : "collectibles.category-footer", tags));
                meta.lore(lore);
                meta.addItemFlags(ItemFlag.values());
            });
            menu.inventory.setItem(CATEGORY_SLOTS[i], icon);
        }
        menu.inventory.setItem(TRAILS, button(Material.BLAZE_POWDER, "trail/hearts", "collectibles.trails", TagResolver.empty()));
        menu.inventory.setItem(CHAT_COLOURS, button(Material.NAME_TAG, "color/rainbow", "collectibles.chat-colours", TagResolver.empty()));
        menu.inventory.setItem(VAULT, button(Material.ENDER_CHEST, "mystery/box_5", "collectibles.vault",
                TagResolver.resolver(Messages.arg("boxes", stash.totalBoxes()))));
        menu.inventory.setItem(TAKE_OFF_ALL, button(Material.BARRIER, "mystery/locked", "collectibles.take-off-all", TagResolver.empty()));
        player.openInventory(menu.inventory);
    }

    /** A category page (for hats and suits, one season at a time). */
    private void openPage(Player player, Category category, Season season) {
        Messages messages = plugin.messages();
        MenuStyle style = plugin.menuStyle();
        Menu menu = new Menu(category, category.seasonal() ? season : null);
        menu.inventory = Bukkit.createInventory(menu, 54, style.title(PAGE_MENU.get(category),
                messages.component("collectibles.category." + category.key + ".title")));
        fill(menu);
        if (category.seasonal()) {
            Season[] seasons = Season.values();
            for (int i = 0; i < seasons.length; i++) {
                Season tab = seasons[i];
                boolean chosen = tab == season;
                boolean now = tab == Season.NORMAL || inSeason(new Item("", category, "", tab, Reward.Rarity.COMMON, "", "", null, null));
                ItemStack icon = button(Material.PAPER, "season/" + tab.key(), null, TagResolver.empty());
                icon.editMeta(meta -> {
                    meta.displayName(messages.item("collectibles.season." + tab.key()));
                    meta.lore(messages.lore(chosen ? "collectibles.tab-chosen" : now ? "collectibles.tab-in-season" : "collectibles.tab-out-of-season"));
                    if (chosen) meta.setEnchantmentGlintOverride(true);
                });
                menu.inventory.setItem(TAB_SLOTS[i], icon);
                menu.tabs.put(TAB_SLOTS[i], tab);
            }
        }
        List<Item> items = category.seasonal() ? Catalog.of(category, season) : Catalog.of(category);
        Item worn = category.action() ? null : equipped(player, category);
        int slot = FIRST_ITEM;
        for (Item item : items) {
            if (slot > LAST_ITEM) break;
            menu.inventory.setItem(slot, entry(player, item, worn != null && worn.id().equals(item.id())));
            menu.items.put(slot, item);
            slot++;
        }
        if (items.isEmpty()) {
            menu.inventory.setItem(22, button(Material.GRAY_DYE, "mystery/locked", "collectibles.empty", TagResolver.empty()));
        }
        menu.inventory.setItem(BACK, style.icon(button(Material.ARROW, null, "collectibles.back", TagResolver.empty()), "back"));
        if (!category.action()) {
            menu.inventory.setItem(TAKE_OFF, button(Material.BARRIER, "mystery/locked", "collectibles.take-off",
                    Placeholder.parsed("category", messages.raw("collectibles.category." + category.key + ".name"))));
        }
        player.openInventory(menu.inventory);
    }

    /** One collectible in a category page: found or locked, worn, and what a click does. */
    private ItemStack entry(Player player, Item item, boolean worn) {
        Messages messages = plugin.messages();
        boolean owned = owns(player, item);
        boolean season = inSeason(item);
        long price = price(item);
        long dust = data().get(player.getUniqueId()).dust();
        TagResolver tags = TagResolver.resolver(name(item), Placeholder.parsed("rarity", plugin.mystery().rarityLabel(item.rarity())),
                Placeholder.parsed("description", item.description()), Placeholder.parsed("season", messages.raw("collectibles.season." + item.season().key())),
                Messages.arg("price", String.format(java.util.Locale.ROOT, "%,d", price)), Messages.arg("need", Math.max(0, price - dust)),
                Placeholder.parsed("ability", item.suit() == null ? "" : messages.raw("collectibles.ability." + item.suit().ability() + ".name")));
        ItemStack icon = icon(item);
        icon.editMeta(meta -> {
            meta.displayName(messages.item(owned ? "collectibles.entry.name" : "collectibles.entry.locked-name", tags));
            List<Component> lore = new ArrayList<>(messages.lore("collectibles.entry.lore", tags));
            if (item.suit() != null) lore.addAll(messages.lore("collectibles.entry.suit", tags));
            lore.add(Component.empty());
            String state;
            if (owned) state = worn ? "collectibles.entry.worn" : item.category().action() ? "collectibles.entry.use" : "collectibles.entry.equip";
            else if (!season) state = "collectibles.entry.out-of-season";
            else state = dust >= price ? "collectibles.entry.buy" : "collectibles.entry.cant-afford";
            lore.addAll(messages.lore(state, tags));
            meta.lore(lore);
            if (worn) meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.values());
        });
        return icon;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (menu.category == null) {
            for (int i = 0; i < CATEGORY_SLOTS.length; i++) {
                if (slot == CATEGORY_SLOTS[i]) {
                    sound(player, "ui.button.click", 1.1f);
                    openPage(player, Category.values()[i], Season.NORMAL);
                    return;
                }
            }
            switch (slot) {
                case TRAILS -> plugin.trails().open(player);
                case CHAT_COLOURS -> {
                    if (com.sasha.lobby.items.ChatColors.canUse(player)) plugin.chatColors().open(player);
                    else plugin.messages().send(player, "collectibles.no-chat-colours");
                }
                case VAULT -> plugin.mystery().openMenu(player);
                case TAKE_OFF_ALL -> {
                    takeOffAll(player);
                    open(player);
                }
                default -> {
                }
            }
            return;
        }
        if (slot == BACK) {
            sound(player, "ui.button.click", 0.9f);
            open(player);
            return;
        }
        if (slot == TAKE_OFF && !menu.category.action()) {
            takeOff(player, menu.category);
            openPage(player, menu.category, menu.season == null ? Season.NORMAL : menu.season);
            return;
        }
        Season tab = menu.tabs.get(slot);
        if (tab != null) {
            sound(player, "ui.button.click", 1.2f);
            openPage(player, menu.category, tab);
            return;
        }
        Item item = menu.items.get(slot);
        if (item == null) return;
        Season season = menu.season == null ? Season.NORMAL : menu.season;
        if (!owns(player, item)) {
            buy(player, item, event.isShiftClick());
            if (player.getOpenInventory().getTopInventory() == menu.inventory) openPage(player, menu.category, season);
            return;
        }
        switch (item.category()) {
            case EMOTE, GESTURE -> {
                player.closeInventory();
                use(player, item);
            }
            default -> {
                Item worn = equipped(player, item.category());
                if (worn != null && worn.id().equals(item.id())) takeOff(player, item.category());
                else equip(player, item);
                openPage(player, menu.category, season);
            }
        }
    }

    /** Emotes and gestures. */
    public void use(Player player, Item item) {
        if (!owns(player, item)) {
            plugin.messages().send(player, "collectibles.not-owned", name(item));
            return;
        }
        if (!ready(player, item.id(), item.category() == Category.EMOTE ? 4 : 3)) return;
        if (item.category() == Category.EMOTE) effects.emote(player, item);
        else effects.gesture(player, item.key());
    }

    /** /emote NAME and /gesture NAME */
    public void use(Player player, Category category, String key) {
        Item item = Catalog.get(category.prefix() + ":" + key.toLowerCase(java.util.Locale.ROOT));
        if (item == null) {
            plugin.messages().send(player, "collectibles.unknown", Messages.name("input", key));
            return;
        }
        use(player, item);
    }

    /** Names of a category's items the player owns (for command suggestions). */
    public List<String> owned(Player player, Category category) {
        return Catalog.of(category).stream().filter(item -> owns(player, item)).map(Item::key).toList();
    }

    /** Buying with Mystery Dust: a shift-click, in season, with enough dust. */
    private void buy(Player player, Item item, boolean shift) {
        if (!inSeason(item)) {
            plugin.messages().send(player, "collectibles.out-of-season", name(item),
                    Placeholder.parsed("season", plugin.messages().raw("collectibles.season." + item.season().key())));
            sound(player, "entity.villager.no", 1f);
            return;
        }
        long price = price(item);
        MysteryData.Stash stash = data().get(player.getUniqueId());
        if (stash.dust() < price) {
            plugin.messages().send(player, "collectibles.cant-afford", name(item), Messages.arg("price", price),
                    Messages.arg("need", price - stash.dust()));
            sound(player, "entity.villager.no", 1f);
            return;
        }
        if (!shift) {
            plugin.messages().send(player, "collectibles.shift-to-buy", name(item), Messages.arg("price", price));
            sound(player, "ui.button.click", 0.7f);
            return;
        }
        data().spendDust(player.getUniqueId(), price);
        data().grant(player.getUniqueId(), item.id());
        plugin.messages().send(player, "collectibles.bought", name(item), Messages.arg("price", price));
        sound(player, "entity.player.levelup", 1.4f);
        plugin.display().refreshSidebar(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }

    // ---------------------------------------------------------------- icons and names

    /** &lt;name&gt;: the item's name in its rarity's colour. */
    public TagResolver name(Item item) {
        return Placeholder.parsed("name", plugin.mystery().rarityColourTag(item.rarity()) + item.name());
    }

    /** The menu picture of a collectible. */
    public ItemStack icon(Item item) {
        return switch (item.category()) {
            case HAT -> modelled(Material.LEATHER_HELMET, "hat/" + item.key());
            case SUIT -> {
                ItemStack chest = ItemStack.of(Material.LEATHER_CHESTPLATE);
                chest.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(item.suit().chest()));
                yield chest;
            }
            case GADGET -> modelled(Material.FIREWORK_STAR, "gadget/" + item.key());
            case CLOAK -> modelled(Material.PHANTOM_MEMBRANE, "cloak/" + item.key());
            case EMOTE -> modelled(Material.PLAYER_HEAD, "status/" + item.emoji());
            case GESTURE -> modelled(Material.FEATHER, "gesture/" + item.key());
        };
    }

    private ItemStack categoryIcon(Category category) {
        return switch (category) {
            case HAT -> modelled(Material.LEATHER_HELMET, "hat/top_hat");
            case SUIT -> {
                ItemStack chest = ItemStack.of(Material.LEATHER_CHESTPLATE);
                chest.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(0xE03A3A)));
                yield chest;
            }
            case GADGET -> modelled(Material.FIREWORK_STAR, "gadget/party_popper");
            case CLOAK -> modelled(Material.PHANTOM_MEMBRANE, "cloak/halo");
            case EMOTE -> modelled(Material.PLAYER_HEAD, "status/love");
            case GESTURE -> modelled(Material.FEATHER, "gesture/wave");
        };
    }

    private ItemStack modelled(Material fallback, String model) {
        ItemStack item = ItemStack.of(fallback);
        if (plugin.menuStyle().custom()) item.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", model)));
        return item;
    }

    private ItemStack button(Material material, String model, String key, TagResolver tags) {
        ItemStack item = model == null ? ItemStack.of(material) : modelled(material, model);
        item.editMeta(meta -> {
            if (key != null) {
                meta.displayName(plugin.messages().item(key + ".name", tags));
                meta.lore(plugin.messages().lore(key + ".lore", tags));
            }
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    private void fill(Menu menu) {
        ItemStack filler = plugin.menuStyle().filler();
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);
    }

    static void sound(Player player, String key, float pitch) {
        player.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, 0.6f, pitch), Sound.Emitter.self());
    }
}
