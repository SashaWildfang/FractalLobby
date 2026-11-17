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
        if (player.hasPermission(ALL)) return true;
        MysteryData.Stash stash = data().get(player.getUniqueId());
        return stash.owns(item.id()) || item.set() != null && stash.owns("suit:" + item.set()); // a whole suit from 1.36
    }

    /** Whether it's that item's season now (Normal always is). */
    public boolean inSeason(Item item) {
        List<String> forced = plugin.getConfig().getStringList("collectibles.seasons-on");
        if (forced.contains(item.season().key())) return true;
        return item.season().on(LocalDate.now(plugin.lobbyConfig().scoreboard().timeZone()));
    }

    /** Mystery Dust to craft it (a suit piece costs half). */
    public long price(Item item) {
        long[] defaults = {100, 250, 600, 1500};
        long price = Math.max(1, plugin.getConfig().getLong("collectibles.price." + item.rarity().key(), defaults[item.rarity().ordinal()]));
        return item.piece() != null ? Math.max(1, price / 2) : price;
    }

    /** Only all-year collectibles can be crafted; seasonal ones only come from boxes in their season. */
    public boolean craftable(Item item) {
        return item.season() == Season.NORMAL;
    }

    private Item equipped(Player player, Category category) {
        return Catalog.get(data().get(player.getUniqueId()).equipped(category.prefix()));
    }

    private static String slotKey(Catalog.Piece piece) {
        return "suit_" + piece.key();
    }

    /** The suit piece worn in a slot (if still owned). */
    Item piece(Player player, Catalog.Piece piece) {
        Item item = Catalog.get(data().get(player.getUniqueId()).equipped(slotKey(piece)));
        return item != null && owns(player, item) ? item : null;
    }

    /** How many pieces of a set the player has on. */
    int worn(Player player, String set) {
        int count = 0;
        for (Catalog.Piece piece : Catalog.Piece.values()) {
            Item item = piece(player, piece);
            if (item != null && set.equals(item.set())) count++;
        }
        return count;
    }

    /** The suit the player wears all four pieces of, or null. */
    public Catalog.SuitSet fullSet(Player player) {
        Item chest = piece(player, Catalog.Piece.CHESTPLATE);
        if (chest == null || worn(player, chest.set()) < 4) return null;
        return Catalog.set(chest.set());
    }

    /** Astronauts float instead of double jumping. */
    public boolean blocksDoubleJump(Player player) {
        Catalog.SuitSet set = enabled() ? fullSet(player) : null;
        return set != null && set.key().equals("astronaut");
    }

    /** 1.36 kept a whole suit under "suit": it becomes its four pieces. */
    private void migrate(Player player) {
        MysteryData.Stash stash = data().get(player.getUniqueId());
        String old = stash.equipped("suit");
        if (old == null) return;
        Catalog.SuitSet set = Catalog.set(old.substring(old.indexOf(':') + 1));
        data().equip(player.getUniqueId(), "suit", null);
        if (set == null) return;
        for (Item piece : set.pieces()) data().equip(player.getUniqueId(), slotKey(piece.piece()), piece.id());
    }

    // ---------------------------------------------------------------- wearing

    /** Puts on what the player has equipped (on join, after a reload, after parkour). */
    public void apply(Player player) {
        if (!enabled()) return;
        migrate(player);
        PlayerInventory inventory = player.getInventory();
        Item hat = equipped(player, Category.HAT), gadget = equipped(player, Category.GADGET);
        if (hat != null && !owns(player, hat)) hat = null;
        Item helmet = piece(player, Catalog.Piece.HELMET);
        inventory.setHelmet(hat != null ? hatItem(player, hat) : helmet != null ? armour(player, helmet) : null);
        inventory.setChestplate(armourOrNull(player, Catalog.Piece.CHESTPLATE));
        inventory.setLeggings(armourOrNull(player, Catalog.Piece.LEGGINGS));
        inventory.setBoots(armourOrNull(player, Catalog.Piece.BOOTS));
        int gadgetSlot = slot("collectibles.gadget-slot", 4);
        inventory.setItem(gadgetSlot, gadget != null && owns(player, gadget) ? gadgetItem(gadget) : null);
        // The ability item from 1.36 is gone: abilities are a right-click with an empty hand now.
        int oldAbility = slot("collectibles.ability-slot", 6);
        ItemStack inOld = inventory.getItem(oldAbility);
        if (inOld != null && inOld.hasItemMeta()) {
            String what = inOld.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
            if (what != null && what.startsWith("ability:")) inventory.setItem(oldAbility, null);
        }
        if (plugin.getConfig().getBoolean("join-items.collectibles.enabled", true)) {
            inventory.setItem(joinSlot(plugin), joinItem());
        }
        effects.worn(player);
        if (plugin.doubleJump() != null) plugin.doubleJump().refresh(player);
    }

    private ItemStack armourOrNull(Player player, Catalog.Piece piece) {
        Item item = piece(player, piece);
        return item == null ? null : armour(player, item);
    }

    /** A config.yml from before 1.37 (radio in 5): the radio moves to 3 and the collectibles sit in the middle (5). */
    public static boolean oldHotbar(org.bukkit.plugin.Plugin plugin) {
        var config = plugin.getConfig();
        int collectibles = config.getInt("join-items.collectibles.slot", 5);
        // radio still in 5 with the collectibles in 3, or both in 5 (a config.yml without a collectibles section)
        return config.getInt("join-items.radio.slot", 3) == 5 && (collectibles == 3 || collectibles == 5);
    }

    public static int joinSlot(org.bukkit.plugin.Plugin plugin) {
        if (oldHotbar(plugin)) return 4;
        return Math.max(0, Math.min(8, plugin.getConfig().getInt("join-items.collectibles.slot", 5) - 1));
    }

    private int slot(String path, int fallback) {
        return Math.max(0, Math.min(8, plugin.getConfig().getInt(path, fallback) - 1));
    }

    private void equip(Player player, Item item) {
        MysteryData data = data();
        UUID id = player.getUniqueId();
        switch (item.category()) {
            case HAT -> {
                data.equip(id, "hat", item.id());
                data.equip(id, slotKey(Catalog.Piece.HELMET), null); // the hat goes where the helmet was
            }
            case SUIT -> {
                data.equip(id, slotKey(item.piece()), item.id());
                if (item.piece() == Catalog.Piece.HELMET) data.equip(id, "hat", null);
            }
            case GADGET -> data.equip(id, "gadget", item.id());
            case CLOAK -> data.equip(id, "cloak", item.id());
            default -> {
            }
        }
        apply(player);
        plugin.messages().send(player, item.category() == Category.GADGET ? "collectibles.equipped-gadget" : "collectibles.equipped", name(item));
        sound(player, "item.armor.equip_leather", 1.2f);
        if (item.set() != null && worn(player, item.set()) == 4) {
            Catalog.SuitSet set = Catalog.set(item.set());
            plugin.messages().send(player, "collectibles.full-set", Placeholder.parsed("set", set.name()),
                    Placeholder.parsed("ability", plugin.messages().raw("collectibles.ability." + set.suit().ability() + ".name")),
                    Placeholder.parsed("how", plugin.messages().raw("collectibles.ability." + set.suit().ability() + ".how")));
            sound(player, "block.beacon.power_select", 1.4f);
        }
    }

    /** Wears every piece of a set the player owns. */
    private void equipSet(Player player, Catalog.SuitSet set) {
        for (Item piece : set.pieces()) if (owns(player, piece)) data().equip(player.getUniqueId(), slotKey(piece.piece()), piece.id());
        data().equip(player.getUniqueId(), "hat", null);
        Item last = set.pieces().get(1);
        equip(player, owns(player, last) ? last : set.pieces().stream().filter(p -> owns(player, p)).findFirst().orElse(last));
    }

    private void takeOff(Player player, Category category) {
        if (category == Category.SUIT) {
            for (Catalog.Piece piece : Catalog.Piece.values()) data().equip(player.getUniqueId(), slotKey(piece), null);
        } else {
            data().equip(player.getUniqueId(), category.prefix(), null);
        }
        apply(player);
        plugin.messages().send(player, category == Category.GADGET ? "collectibles.unequipped-gadget" : "collectibles.took-off", Placeholder.parsed("category",
                plugin.messages().raw("collectibles.category." + category.key + ".name")));
        sound(player, "item.armor.equip_generic", 0.8f);
    }

    private void takeOffPiece(Player player, Item item) {
        data().equip(player.getUniqueId(), slotKey(item.piece()), null);
        apply(player);
        plugin.messages().send(player, "collectibles.took-off-piece", name(item));
        sound(player, "item.armor.equip_generic", 0.8f);
    }

    private void takeOffAll(Player player) {
        for (String key : List.of("hat", "gadget", "cloak")) data().equip(player.getUniqueId(), key, null);
        for (Catalog.Piece piece : Catalog.Piece.values()) data().equip(player.getUniqueId(), slotKey(piece), null);
        apply(player);
        plugin.messages().send(player, "collectibles.took-off-all");
        sound(player, "item.armor.equip_generic", 0.8f);
    }

    /** The cloak the player wears now, if they still own it. */
    Item hat(Player player) {
        Item hat = equipped(player, Category.HAT);
        return hat != null && owns(player, hat) ? hat : null;
    }

    /** Players in a parkour run can't use movement gadgets or suit bonuses. */
    boolean inParkour(Player player) {
        return plugin.parkour() != null && plugin.parkour().game().playing(player);
    }

    Item cloak(Player player) {
        Item cloak = equipped(player, Category.CLOAK);
        return cloak != null && owns(player, cloak) ? cloak : null;
    }

    /** A hat as worn: its picture, and its name and description when hovered. */
    ItemStack hatItem(Player player, Item hat) {
        ItemStack item = ItemStack.of(Material.PAPER);
        String model = hat.key().equals("propeller_cap") ? "hat/propeller_cap_base" : "hat/" + hat.key(); // the propeller spins on its own
        item.editMeta(meta -> {
            meta.displayName(plugin.messages().item("collectibles.worn.name", name(hat)));
            meta.lore(plugin.messages().lore("collectibles.worn.lore", wornTags(hat)));
            meta.setItemModel(new NamespacedKey("fractal", model));
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    /** A suit piece as worn: the suit's own armour look, no armour points, and the set and its bonus when hovered. */
    /** Suits whose helmet has a shape armour can't (ears, eyes, a beak): it's worn like a hat. */
    private static final java.util.Set<String> SHAPED_HELMETS = java.util.Set.of("bunny", "frog", "chick");

    private boolean shapedHelmet(Item piece) {
        return piece.piece() == Catalog.Piece.HELMET && SHAPED_HELMETS.contains(piece.set()) && plugin.menuStyle().custom();
    }

    private ItemStack armour(Player player, Item piece) {
        if (shapedHelmet(piece)) {
            Catalog.SuitSet set = Catalog.set(piece.set());
            int on = worn(player, piece.set());
            ItemStack hood = ItemStack.of(Material.PAPER);
            hood.editMeta(meta -> {
                meta.displayName(plugin.messages().item("collectibles.worn.name", name(piece)));
                List<Component> lore = new ArrayList<>(plugin.messages().lore("collectibles.worn.lore", wornTags(piece)));
                lore.addAll(plugin.messages().lore(on == 4 ? "collectibles.worn.set-complete" : "collectibles.worn.set", setTags(set, on)));
                meta.lore(lore);
                meta.setItemModel(new NamespacedKey("fractal", "hat/suit_" + piece.set()));
                meta.addItemFlags(ItemFlag.values());
            });
            return hood;
        }
        Material material = switch (piece.piece()) {
            case HELMET -> Material.LEATHER_HELMET;
            case CHESTPLATE -> Material.LEATHER_CHESTPLATE;
            case LEGGINGS -> Material.LEATHER_LEGGINGS;
            case BOOTS -> Material.LEATHER_BOOTS;
        };
        Catalog.SuitSet set = Catalog.set(piece.set());
        int on = worn(player, piece.set());
        Color colour = switch (piece.piece()) {
            case HELMET -> set.suit().helmet();
            case CHESTPLATE -> set.suit().chest();
            case LEGGINGS -> set.suit().legs();
            case BOOTS -> set.suit().boots();
        };
        ItemStack item = ItemStack.of(material);
        item.editMeta(LeatherArmorMeta.class, meta -> {
            meta.setColor(colour);
            meta.displayName(plugin.messages().item("collectibles.worn.name", name(piece)));
            List<Component> lore = new ArrayList<>(plugin.messages().lore("collectibles.worn.lore", wornTags(piece)));
            lore.addAll(plugin.messages().lore(on == 4 ? "collectibles.worn.set-complete" : "collectibles.worn.set", setTags(set, on)));
            meta.lore(lore);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.values());
            if (plugin.menuStyle().custom()) meta.setItemModel(new NamespacedKey("fractal", "suit/" + piece.set() + "/" + piece.piece().key()));
        });
        item.setData(io.papermc.paper.datacomponent.DataComponentTypes.ATTRIBUTE_MODIFIERS,
                io.papermc.paper.datacomponent.item.ItemAttributeModifiers.itemAttributes().build()); // no armour points
        if (plugin.menuStyle().custom()) {
            org.bukkit.inventory.EquipmentSlot slot = switch (piece.piece()) {
                case HELMET -> org.bukkit.inventory.EquipmentSlot.HEAD;
                case CHESTPLATE -> org.bukkit.inventory.EquipmentSlot.CHEST;
                case LEGGINGS -> org.bukkit.inventory.EquipmentSlot.LEGS;
                case BOOTS -> org.bukkit.inventory.EquipmentSlot.FEET;
            };
            item.setData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE,
                    io.papermc.paper.datacomponent.item.Equippable.equippable(slot).assetId(Key.key("fractal", "suit_" + piece.set())).build());
        }
        return item;
    }

    private TagResolver wornTags(Item item) {
        return TagResolver.resolver(name(item), Placeholder.parsed("rarity", plugin.mystery().rarityLabel(item.rarity())),
                Placeholder.parsed("description", item.description()),
                Placeholder.parsed("season", plugin.messages().raw("collectibles.season." + item.season().key())));
    }

    /** &lt;set&gt; &lt;count&gt; &lt;ability&gt; &lt;bonus&gt; &lt;how&gt; for a suit. */
    private TagResolver setTags(Catalog.SuitSet set, int on) {
        String ability = set.suit().ability();
        return TagResolver.resolver(Placeholder.parsed("set", plugin.mystery().rarityColourTag(set.rarity()) + set.name()),
                Messages.arg("count", on), Placeholder.parsed("ability", plugin.messages().raw("collectibles.ability." + ability + ".name")),
                Placeholder.parsed("bonus", plugin.messages().raw("collectibles.ability." + ability + ".description")),
                Placeholder.parsed("how", plugin.messages().raw("collectibles.ability." + ability + ".how")));
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
        if (held == null || held.getType().isAir()) {
            // An empty hand (the game only tells us when it's on a block; the swap-hands key works anywhere).
            triggerAbility(event.getPlayer());
            return;
        }
        if (!held.hasItemMeta()) return;
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
        if (!kind.equals("gadget")) return;
        if (inParkour(player)) return;
        if (!ready(player, what, gadgetCooldown(key))) return;
        effects.gadget(player, key);
    }

    /** The swap-hands key (F): the full suit's ability. */
    public void swapKey(Player player) {
        triggerAbility(player);
    }

    /** The full suit's right-click ability (passive suits and the frog's sneak-leap don't use this). */
    private void triggerAbility(Player player) {
        Catalog.SuitSet set = enabled() && !inParkour(player) ? fullSet(player) : null;
        if (set == null || set.suit().passive() || set.key().equals("frog")) return;
        String ability = set.suit().ability();
        if (ready(player, "ability:" + ability, effects.abilityCooldown(ability))) effects.ability(player, ability);
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
            case "disco_ball", "cookie_fountain", "trampoline" -> 25;
            case "balloon" -> 30;
            case "fake_tnt", "bat_blaster", "melody_maker" -> 8;
            case "grappling_hook", "blink_stick", "magic_wand" -> 3;
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
        private final Map<Integer, Catalog.SuitSet> sets = new HashMap<>();
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
                boolean now = tab == Season.NORMAL || inSeason(new Item("", category, "", tab, Reward.Rarity.COMMON, "", "", null, null, null, null));
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
        if (category == Category.SUIT) {
            // One suit a row: its four pieces, then the whole set.
            int row = 1;
            for (Catalog.SuitSet set : Catalog.sets(season)) {
                if (row > 4) break;
                for (Item piece : set.pieces()) {
                    int at = row * 9 + 1 + piece.piece().ordinal();
                    Item on = piece(player, piece.piece());
                    menu.inventory.setItem(at, entry(player, piece, on != null && on.id().equals(piece.id())));
                    menu.items.put(at, piece);
                }
                menu.inventory.setItem(row * 9 + 6, setEntry(player, set));
                menu.sets.put(row * 9 + 6, set);
                row++;
            }
        } else {
            Item worn = category.action() ? null : equipped(player, category);
            int slot = FIRST_ITEM;
            for (Item item : items) {
                if (slot > LAST_ITEM) break;
                menu.inventory.setItem(slot, entry(player, item, worn != null && worn.id().equals(item.id())));
                menu.items.put(slot, item);
                slot++;
            }
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
            if (item.set() != null) {
                Catalog.SuitSet set = Catalog.set(item.set());
                lore.addAll(messages.lore("collectibles.entry.suit", setTags(set, worn(player, item.set()))));
            }
            lore.add(Component.empty());
            String state;
            boolean gadget = item.category() == Category.GADGET;
            if (owned) state = worn ? (gadget ? "collectibles.entry.equipped" : "collectibles.entry.worn")
                    : item.category().action() ? "collectibles.entry.use" : gadget ? "collectibles.entry.equip-gadget" : "collectibles.entry.equip";
            else if (!craftable(item)) state = season ? "collectibles.entry.boxes-only" : "collectibles.entry.out-of-season";
            else state = dust >= price ? "collectibles.entry.buy" : "collectibles.entry.cant-afford";
            lore.addAll(messages.lore(state, tags));
            meta.lore(lore);
            if (worn) meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.values());
        });
        return icon;
    }

    /** A suit's whole set: how many pieces are found and worn, the bonus, and a click to wear them all. */
    private ItemStack setEntry(Player player, Catalog.SuitSet set) {
        Messages messages = plugin.messages();
        int found = (int) set.pieces().stream().filter(p -> owns(player, p)).count();
        int on = worn(player, set.key());
        ItemStack icon = modelled(Material.ARMOR_STAND, "suit/" + set.key() + "/set");
        TagResolver tags = TagResolver.resolver(setTags(set, on), Messages.arg("found", found),
                Placeholder.parsed("rarity", plugin.mystery().rarityLabel(set.rarity())));
        icon.editMeta(meta -> {
            meta.displayName(messages.item("collectibles.set.name", tags));
            List<Component> lore = new ArrayList<>(messages.lore("collectibles.set.lore", tags));
            lore.add(Component.empty());
            lore.addAll(messages.lore(found == 0 ? "collectibles.set.none" : on == found ? "collectibles.set.worn" : "collectibles.set.wear", tags));
            meta.lore(lore);
            if (on == 4) meta.setEnchantmentGlintOverride(true);
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
        Season season = menu.season == null ? Season.NORMAL : menu.season;
        Catalog.SuitSet set = menu.sets.get(slot);
        if (set != null) {
            boolean any = set.pieces().stream().anyMatch(p -> owns(player, p));
            if (!any) {
                sound(player, "entity.villager.no", 1f);
                return;
            }
            if (worn(player, set.key()) == set.pieces().stream().filter(p -> owns(player, p)).count()) takeOff(player, Category.SUIT);
            else equipSet(player, set);
            openPage(player, menu.category, season);
            return;
        }
        Item item = menu.items.get(slot);
        if (item == null) return;
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
            case SUIT -> {
                Item on = piece(player, item.piece());
                if (on != null && on.id().equals(item.id())) takeOffPiece(player, item);
                else equip(player, item);
                openPage(player, menu.category, season);
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
        if (!craftable(item)) {
            plugin.messages().send(player, inSeason(item) ? "collectibles.boxes-only" : "collectibles.out-of-season", name(item),
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
            case SUIT -> modelled(Material.LEATHER_CHESTPLATE, shapedHelmet(item) ? "hat/suit_" + item.set()
                    : "suit/" + item.set() + "/" + item.piece().key());
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
