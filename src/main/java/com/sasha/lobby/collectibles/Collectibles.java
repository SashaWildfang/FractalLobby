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
import org.bukkit.event.inventory.ClickType;
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
    private static final int STASH = 4, TRAILS = 25, CHAT_COLOURS = 38, SEARCH = 40, TAKE_OFF_ALL = 42;
    /** The search results page's background (resource pack). */
    private static final char SEARCH_MENU = '\uF17F';
    /** Players asked to type a search in chat. */
    private final java.util.Set<UUID> searching = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final int[] CATEGORY_SLOTS = {19, 20, 21, 22, 23, 24};
    /** Category page: season tabs on top, items in the middle, the bottom row. */
    private static final int[] TAB_SLOTS = {2, 3, 4, 5, 6};
    private static final int FIRST_ITEM = 9, LAST_ITEM = 44, BACK = 45, PREVIOUS = 48, TAKE_OFF = 49, NEXT = 50, SORT = 53;

    /** What a category page shows, and in what order (the Sort button). Remembered per player while online. */
    enum Sort {
        ALL, UNLOCKED, LOCKED, PURCHASABLE, RARITY, SEASONAL;

        Sort step(int by) {
            Sort[] all = values();
            return all[Math.floorMod(ordinal() + by, all.length)];
        }

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final Map<UUID, Sort> sorts = new HashMap<>();
    private static final int PER_PAGE = LAST_ITEM - FIRST_ITEM + 1;
    /** One suit's own page: its four pieces, the whole set, and back. */
    private static final char SUIT_MENU = '\uF17D';
    private static final int SUIT_FIRST_PIECE = 10, SUIT_SET = 15, SUIT_BACK = 18;

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
        long[] defaults = {100, 175, 250, 600, 1500};
        long price = Math.max(1, plugin.getConfig().getLong("collectibles.price." + item.rarity().key(), defaults[item.rarity().ordinal()]));
        return item.piece() != null ? Math.max(1, price / 2) : price;
    }

    /** Only all-year collectibles can be crafted; seasonal ones only come from boxes in their season. */
    public boolean craftable(Item item) {
        return item.season() == Season.NORMAL;
    }

    private Item equipped(Player player, Category category) {
        Item item = Catalog.get(data().get(player.getUniqueId()).equipped(category.slotKey()));
        return item != null && item.category() == category ? item : null;
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
        Item hat = equipped(player, Category.HAT), gadget = Catalog.get(data().get(player.getUniqueId()).equipped("gadget"));
        if (hat != null && !owns(player, hat)) hat = null;
        Item helmet = piece(player, Catalog.Piece.HELMET);
        inventory.setHelmet(hat != null ? hatItem(player, hat) : helmet != null ? armour(player, helmet) : null);
        inventory.setChestplate(armourOrNull(player, Catalog.Piece.CHESTPLATE));
        inventory.setLeggings(armourOrNull(player, Catalog.Piece.LEGGINGS));
        inventory.setBoots(armourOrNull(player, Catalog.Piece.BOOTS));
        // the gadget sits right of the Collectibles item (slot 6); configs from before 1.39 said 4
        int configured = plugin.getConfig().getInt("collectibles.gadget-slot", 6);
        int gadgetSlot = Math.max(0, Math.min(8, (configured == 4 ? 6 : configured) - 1));
        if (gadgetSlot != 3) {
            ItemStack old = inventory.getItem(3);
            if (old != null && old.hasItemMeta() && "gadget".equals(kindOf(old))) inventory.setItem(3, null);
        }
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

    private String kindOf(ItemStack item) {
        String what = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        return what == null ? null : what.contains(":") ? what.substring(0, what.indexOf(':')) : what;
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
            case GADGET, EMOTE -> {
                effects.stopGadget(player);                                  // the old one's show ends right away
                data.equip(id, "gadget", item.id());                         // one or the other in the hotbar
            }
            case CLOAK -> data.equip(id, "cloak", item.id());
            default -> {
            }
        }
        apply(player);
        plugin.messages().send(player, item.category() == Category.GADGET ? "collectibles.equipped-gadget"
                : item.category() == Category.EMOTE ? "collectibles.equipped-emote" : "collectibles.equipped", name(item));
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
        if (category == Category.GADGET || category == Category.EMOTE) effects.stopGadget(player);
        if (category == Category.SUIT) {
            for (Catalog.Piece piece : Catalog.Piece.values()) data().equip(player.getUniqueId(), slotKey(piece), null);
        } else {
            data().equip(player.getUniqueId(), category.slotKey(), null);
        }
        apply(player);
        plugin.messages().send(player, category == Category.GADGET || category == Category.EMOTE ? "collectibles.unequipped-gadget" : "collectibles.took-off", Placeholder.parsed("category",
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
    private static final java.util.Set<String> SHAPED_HELMETS = java.util.Set.of("bunny", "frog", "chick", "disco", "warrior", "thor",
            "baker", "bumblebee");

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
            String kind = gadget.category() == Category.EMOTE ? "emote-item" : "gadget-item";
            meta.displayName(plugin.messages().item("collectibles." + kind + ".name", name(gadget)));
            meta.lore(plugin.messages().lore("collectibles." + kind + ".lore", TagResolver.resolver(name(gadget),
                    Placeholder.parsed("description", gadget.description()))));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, gadget.category() == Category.EMOTE
                    ? "emote:" + gadget.key() : "gadget:" + gadget.key());
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
        if (kind.equals("emote")) {
            Item emote = Catalog.get("emote:" + key);
            if (emote != null && owns(player, emote) && ready(player, emote.id(), 4)) effects.emote(player, emote);
            return;
        }
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

    /** Gives back a cooldown (when a gadget couldn't be used after all, like no room for it). */
    void refund(Player player, String what) {
        cooldowns.remove(player.getUniqueId() + ":" + what);
    }

    private double gadgetCooldown(String key) {
        return switch (key) {
            case "disco_ball", "cookie_fountain", "trampoline" -> 25;
            case "pinata" -> 1;                                        // (while it's up, right-click just swings the bat)
            case "tetherball", "swing", "diving_board", "scarecrow", "witch_cauldron", "sand_castle", "tic_tac_toe", "dj_booth",
                 "beach_ball" -> 20;
            case "pong" -> 10;
            case "hot_potato", "pigs_fly", "creeper_astronaut", "exploding_sheep", "duckling", "ghost", "rainbow", "pumpkin_rain",
                 "let_it_snow", "ice_skate", "jetpack", "diamond_shower" -> 12;
            case "rock_paper_shears", "magic_8_ball", "fortune_cookie", "catapult", "frisbee", "rush_pearl" -> 4;
            case "mob_gun", "railgun", "explosive_bow" -> 2;
            case "balloon" -> 1;
            case "bat_blaster" -> 8;
            case "fake_tnt" -> 4;
            case "blink_stick", "magic_wand" -> 3;
            case "grappling_hook" -> 0.25;
            case "firework_launcher", "snow_cannon", "pumpkin_launcher" -> 3;
            case "party_popper", "bubble_wand" -> 2;
            default -> 0.6; // paintball gun
        };
    }

    /** The search typed in chat: shown as a page of every collectible whose name has it. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSearchChat(io.papermc.paper.event.player.AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!searching.remove(player.getUniqueId())) return;
        event.setCancelled(true);
        String query = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (query.isEmpty() || query.equalsIgnoreCase("cancel")) {
                plugin.messages().send(player, "collectibles.search-cancelled");
                return;
            }
            openSearch(player, query.length() > 32 ? query.substring(0, 32) : query, 0);
        });
    }

    /** Every collectible (all categories and seasons) whose name has the search in it; 36 a page. */
    private void openSearch(Player player, String query, int page) {
        Messages messages = plugin.messages();
        MenuStyle style = plugin.menuStyle();
        Menu menu = new Menu(null, Season.NORMAL);
        menu.search = query;
        menu.page = page;
        menu.inventory = Bukkit.createInventory(menu, 54, style.title(SEARCH_MENU,
                messages.component("collectibles.search-title", Messages.arg("query", query))));
        fill(menu);
        String q = query.toLowerCase(java.util.Locale.ROOT);
        List<Item> found = Catalog.ITEMS.stream()
                .filter(item -> item.name().toLowerCase(java.util.Locale.ROOT).contains(q)
                        || item.key().replace('_', ' ').contains(q))
                .toList();
        int slot = FIRST_ITEM;
        for (int i = page * PER_PAGE; i < found.size() && slot <= LAST_ITEM; i++, slot++) {
            Item item = found.get(i);
            Item on = item.category().action() ? null
                    : item.category() == Category.SUIT ? piece(player, item.piece()) : equipped(player, item.category());
            menu.inventory.setItem(slot, entry(player, item, on != null && on.id().equals(item.id())));
            menu.items.put(slot, item);
        }
        int pages = Math.max(1, (found.size() + PER_PAGE - 1) / PER_PAGE);
        menu.more = page < pages - 1;
        menu.inventory.setItem(PREVIOUS, page > 0
                ? style.icon(button(Material.ARROW, null, "collectibles.previous", Messages.arg("page", page)), "back")
                : style.icon(button(Material.GRAY_DYE, null, "collectibles.no-previous", TagResolver.empty()), "back-off"));
        menu.inventory.setItem(NEXT, menu.more
                ? style.icon(button(Material.ARROW, null, "collectibles.next", Messages.arg("page", page + 2)), "next")
                : style.icon(button(Material.GRAY_DYE, null, "collectibles.no-next", TagResolver.empty()), "next-off"));
        if (found.isEmpty()) {
            menu.inventory.setItem(22, style.icon(button(Material.BARRIER, null, "collectibles.search-none",
                    Messages.arg("query", query)), "none"));
        }
        menu.inventory.setItem(BACK, style.icon(button(Material.ARROW, null, "collectibles.back", TagResolver.empty()), "back"));
        player.openInventory(menu.inventory);
        sound(player, "ui.button.click", 1.2f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        searching.remove(event.getPlayer().getUniqueId());
        String prefix = event.getPlayer().getUniqueId() + ":";
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
        sorts.remove(event.getPlayer().getUniqueId());
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
        /** Which page of the category (36 a page). */
        private int page;
        /** Set when this is one suit's own page. */
        private Catalog.SuitSet suit;
        /** Set when this is a page of search results. */
        private String search;
        private boolean more;

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
                TagResolver.resolver(Messages.arg("dust", com.sasha.lobby.mystery.MysteryBoxes.compact(stash.dust())),
                        Messages.arg("found", found), Messages.arg("total", Catalog.ITEMS.size()), Messages.arg("boxes", com.sasha.lobby.mystery.MysteryBoxes.compact(stash.totalBoxes())))));
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            Category category = categories[i];
            List<Item> all = Catalog.of(category);
            long owned = all.stream().filter(item -> owns(player, item)).count();
            Component wearing = wearingLine(player, category);
            TagResolver tags = TagResolver.resolver(Messages.arg("found", owned), Messages.arg("total", all.size()),
                    Placeholder.component("wearing", wearing));
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
        menu.inventory.setItem(TRAILS, button(Material.BLAZE_POWDER, "category/trails", "collectibles.trails", TagResolver.empty()));
        menu.inventory.setItem(CHAT_COLOURS, button(Material.NAME_TAG, "category/chat_colours", "collectibles.chat-colours", TagResolver.empty()));
        menu.inventory.setItem(SEARCH, button(Material.SPYGLASS, "radio/search", "collectibles.search", TagResolver.empty()));
        menu.inventory.setItem(TAKE_OFF_ALL, button(Material.BARRIER, "category/take_off", "collectibles.take-off-all", TagResolver.empty()));
        player.openInventory(menu.inventory);
    }

    private void openPage(Player player, Category category, Season season) {
        openPage(player, category, season, 0);
    }

    /** A category page: everything in it (all seasons), as the Sort button says; 36 things a page, with arrows. */
    private void openPage(Player player, Category category, Season season, int page) {
        Messages messages = plugin.messages();
        MenuStyle style = plugin.menuStyle();
        Menu menu = new Menu(category, Season.NORMAL);
        menu.page = page;
        menu.inventory = Bukkit.createInventory(menu, 54, style.title(PAGE_MENU.get(category),
                messages.component("collectibles.category." + category.key + ".title")));
        fill(menu);
        Sort sort = sorts.getOrDefault(player.getUniqueId(), Sort.ALL);
        menu.inventory.setItem(SORT, sortButton(sort));
        List<Item> items = sorted(player, Catalog.of(category), sort);
        int count;
        if (category == Category.SUIT) {
            // Every suit as one picture of the whole set: click to wear it, right-click to see its pieces.
            List<Catalog.SuitSet> sets = sortedSets(player, sort);
            count = sets.size();
            int slot = FIRST_ITEM;
            for (int i = page * PER_PAGE; i < sets.size() && slot <= LAST_ITEM; i++, slot++) {
                Catalog.SuitSet set = sets.get(i);
                ItemStack icon = setEntry(player, set);
                icon.editMeta(meta -> {
                    List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
                    lore.addAll(messages.lore("collectibles.set.pieces-hint"));
                    meta.lore(lore);
                });
                menu.inventory.setItem(slot, icon);
                menu.sets.put(slot, set);
            }
        } else {
            count = items.size();
            Item worn = category.action() ? null : equipped(player, category);
            int slot = FIRST_ITEM;
            for (int i = page * PER_PAGE; i < items.size() && slot <= LAST_ITEM; i++, slot++) {
                Item item = items.get(i);
                menu.inventory.setItem(slot, entry(player, item, worn != null && worn.id().equals(item.id())));
                menu.items.put(slot, item);
            }
        }
        int pages = Math.max(1, (count + PER_PAGE - 1) / PER_PAGE);
        menu.more = page < pages - 1;
        menu.inventory.setItem(PREVIOUS, page > 0
                ? style.icon(button(Material.ARROW, null, "collectibles.previous", Messages.arg("page", page)), "back")
                : style.icon(button(Material.GRAY_DYE, null, "collectibles.no-previous", TagResolver.empty()), "back-off"));
        menu.inventory.setItem(NEXT, menu.more
                ? style.icon(button(Material.ARROW, null, "collectibles.next", Messages.arg("page", page + 2)), "next")
                : style.icon(button(Material.GRAY_DYE, null, "collectibles.no-next", TagResolver.empty()), "next-off"));
        if (count == 0) {
            menu.inventory.setItem(22, plugin.menuStyle().icon(button(Material.BARRIER, null, "collectibles.empty", TagResolver.empty()), "none"));
        }
        menu.inventory.setItem(BACK, style.icon(button(Material.ARROW, null, "collectibles.back", TagResolver.empty()), "back"));
        if (!category.action()) {
            // What's on now sits here (Left-click to take it off); with nothing on, the hanger.
            Item on = wearing(player, category);
            if (on == null) {
                menu.inventory.setItem(TAKE_OFF, button(Material.BARRIER, "category/take_off", "collectibles.take-off",
                        Placeholder.parsed("category", messages.raw("collectibles.category." + category.key + ".name"))));
            } else {
                ItemStack worn = icon(on);
                worn.editMeta(meta -> {
                    meta.displayName(messages.item("collectibles.take-off-worn.name", name(on)));
                    meta.lore(messages.lore("collectibles.take-off-worn.lore", name(on)));
                    meta.setEnchantmentGlintOverride(true);
                    meta.addItemFlags(ItemFlag.values());
                });
                menu.inventory.setItem(TAKE_OFF, worn);
            }
        }
        player.openInventory(menu.inventory);
    }

    /** One suit's own page: its four pieces to wear one by one, and the whole set. */
    private void openSuit(Player player, Catalog.SuitSet set, Season season, int page) {
        Messages messages = plugin.messages();
        MenuStyle style = plugin.menuStyle();
        Menu menu = new Menu(Category.SUIT, season);
        menu.page = page;
        menu.suit = set;
        menu.inventory = Bukkit.createInventory(menu, 27, style.title(SUIT_MENU, messages.component("collectibles.suit-page.title",
                Placeholder.parsed("set", set.name()))));
        fill(menu);
        for (Item piece : set.pieces()) {
            int at = SUIT_FIRST_PIECE + piece.piece().ordinal();
            Item on = piece(player, piece.piece());
            menu.inventory.setItem(at, entry(player, piece, on != null && on.id().equals(piece.id())));
            menu.items.put(at, piece);
        }
        menu.inventory.setItem(SUIT_SET, setEntry(player, set));
        menu.sets.put(SUIT_SET, set);
        menu.inventory.setItem(SUIT_BACK, style.icon(button(Material.ARROW, null, "collectibles.suit-page.back", TagResolver.empty()), "back"));
        player.openInventory(menu.inventory);
    }

    /** Opens the page the player is on again (after something changed). */
    private void reopen(Player player, Menu menu) {
        Season season = menu.season == null ? Season.NORMAL : menu.season;
        if (menu.search != null) openSearch(player, menu.search, menu.page);
        else if (menu.suit != null) openSuit(player, menu.suit, season, menu.page);
        else openPage(player, menu.category, season, menu.page);
    }

    /**
     * Whether a click is one the item's hint offers: Left-click everywhere, and in a category page also
     * Right-click on a suit's set (to see its pieces) and on the Sort button (to go back one).
     */
    private boolean accepts(Menu menu, int slot, ClickType click, Player player) {
        if (click == ClickType.LEFT) return true;
        if (menu.category == null) return false;
        return click == ClickType.RIGHT && menu.suit == null && (menu.sets.containsKey(slot) || slot == SORT);
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
        // a padlock until it's unlocked: gold when they have the dust to buy it now
        ItemStack icon = owned ? icon(item) : affordable(player, item) ? plugin.menuStyle().lockedAffordable(icon(item))
                : plugin.menuStyle().locked(icon(item));
        icon.editMeta(meta -> {
            meta.displayName(messages.item(owned ? "collectibles.entry.name" : "collectibles.entry.locked-name", tags));
            List<Component> lore = new ArrayList<>(messages.lore("collectibles.entry.lore", tags));
            if (item.set() != null) {
                Catalog.SuitSet set = Catalog.set(item.set());
                lore.addAll(messages.lore("collectibles.entry.suit", setTags(set, worn(player, item.set()))));
            }
            lore.add(Component.empty());
            String state;
            boolean gadget = item.category() == Category.GADGET || item.category() == Category.EMOTE;
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
        ItemStack icon = found == 0 ? modelled(Material.ARMOR_STAND, "menu/locked") // no piece unlocked yet
                : modelled(Material.ARMOR_STAND, "suit/" + set.key() + "/set");
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

    /**
     * What a player wears in the lobby (hats, suits, anything in the armour slots) stays on: it can't be clicked off,
     * swapped, dragged or shift-clicked out. Players in creative mode (builders) are left alone.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onArmourClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onArmourDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        for (int raw : event.getRawSlots()) {
            if (event.getView().getSlotType(raw) == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (!accepts(menu, slot, event.getClick(), player)) return;
        if (menu.search != null) {
            if (slot == BACK) {
                sound(player, "ui.button.click", 0.9f);
                open(player);
                return;
            }
            if (slot == PREVIOUS && menu.page > 0 || slot == NEXT && menu.more) {
                sound(player, "ui.button.click", 1.1f);
                openSearch(player, menu.search, menu.page + (slot == NEXT ? 1 : -1));
                return;
            }
            if (!menu.items.containsKey(slot)) return;
            // a collectible: bought, worn or used just like on its own page (below)
        } else if (menu.category == null) {
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
                case SEARCH -> {
                    searching.add(player.getUniqueId());
                    player.closeInventory();
                    plugin.messages().send(player, "collectibles.search-prompt");
                    sound(player, "ui.button.click", 1.2f);
                }
                case TAKE_OFF_ALL -> confirm(player, button(Material.BARRIER, "category/take_off", "collectibles.confirm.take-off-all",
                        TagResolver.empty()), () -> {
                    takeOffAll(player);
                    open(player);
                }, () -> open(player));
                default -> {
                }
            }
            return;
        }
        if (menu.suit != null && slot == SUIT_BACK) {
            sound(player, "ui.button.click", 0.9f);
            openPage(player, Category.SUIT, menu.season == null ? Season.NORMAL : menu.season, menu.page);
            return;
        }
        if (menu.suit == null && slot == BACK) {
            sound(player, "ui.button.click", 0.9f);
            open(player);
            return;
        }
        if (menu.suit == null && (slot == PREVIOUS && menu.page > 0 || slot == NEXT && menu.more)) {
            sound(player, "ui.button.click", 1.1f);
            openPage(player, menu.category, menu.season == null ? Season.NORMAL : menu.season, menu.page + (slot == NEXT ? 1 : -1));
            return;
        }
        if (menu.suit == null && slot == SORT) {
            Sort now = sorts.getOrDefault(player.getUniqueId(), Sort.ALL);
            sorts.put(player.getUniqueId(), now.step(event.getClick() == ClickType.RIGHT ? -1 : 1));
            sound(player, "ui.button.click", 1.2f);
            openPage(player, menu.category, Season.NORMAL, 0);
            return;
        }
        if (menu.suit == null && slot == TAKE_OFF && !menu.category.action()) {
            Item on = wearing(player, menu.category);
            if (on == null) return; // nothing on: the hanger just says so
            ItemStack shown = icon(on);
            shown.editMeta(meta -> {
                meta.displayName(plugin.messages().item("collectibles.confirm.take-off.name", name(on)));
                meta.lore(plugin.messages().lore("collectibles.confirm.take-off.lore", name(on)));
                meta.addItemFlags(ItemFlag.values());
            });
            confirm(player, shown, () -> {
                takeOff(player, menu.category);
                reopen(player, menu);
            }, () -> reopen(player, menu));
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
            if (menu.suit == null && event.getClick() == ClickType.RIGHT) { // "Right-click to see its pieces"
                sound(player, "ui.button.click", 1.1f);
                openSuit(player, set, season, menu.page);
                return;
            }
            if (!any) {
                sound(player, "entity.villager.no", 1f);
                return;
            }
            if (worn(player, set.key()) == set.pieces().stream().filter(p -> owns(player, p)).count()) {
                takeOff(player, Category.SUIT);
                reopen(player, menu);
            } else {
                equipSet(player, set);
                player.closeInventory();
            }
            return;
        }
        Item item = menu.items.get(slot);
        if (item == null) return;
        if (!owns(player, item)) {
            buy(player, item, menu);
            return;
        }
        switch (item.category()) {
            case GESTURE -> {
                player.closeInventory();
                use(player, item);
            }
            case SUIT -> {
                Item on = piece(player, item.piece());
                if (on != null && on.id().equals(item.id())) {
                    takeOffPiece(player, item);
                    reopen(player, menu);
                } else {
                    equip(player, item);
                    player.closeInventory(); // put on: back to the lobby to show it off
                }
            }
            default -> {
                Item worn = equipped(player, item.category());
                if (worn != null && worn.id().equals(item.id())) {
                    takeOff(player, item.category());
                    reopen(player, menu);
                } else {
                    equip(player, item);
                    player.closeInventory(); // put on: back to the lobby to show it off
                }
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

    /** Buying with Mystery Dust: in season, with enough dust, after an "Are you sure?". */
    private void buy(Player player, Item item, Menu from) {
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
        TagResolver tags = TagResolver.resolver(name(item), Messages.arg("price", String.format(java.util.Locale.ROOT, "%,d", price)),
                Messages.arg("dust", String.format(java.util.Locale.ROOT, "%,d", stash.dust())));
        ItemStack shown = icon(item);
        shown.editMeta(meta -> {
            meta.displayName(plugin.messages().item("collectibles.confirm.buy.name", tags));
            meta.lore(plugin.messages().lore("collectibles.confirm.buy.lore", tags));
            meta.addItemFlags(ItemFlag.values());
        });
        confirm(player, shown, () -> {
            if (owns(player, item) || data().get(player.getUniqueId()).dust() < price) { // changed while it was open
                reopen(player, from);
                return;
            }
            data().spendDust(player.getUniqueId(), price);
            data().grant(player.getUniqueId(), item.id());
            plugin.messages().send(player, "collectibles.bought", name(item), Messages.arg("price", price));
            sound(player, "entity.player.levelup", 1.4f);
            plugin.display().refreshSidebar(player);
            reopen(player, from);
        }, () -> reopen(player, from));
    }

    /** The Sort button: the order now, every choice listed, and how to change it. */
    private ItemStack sortButton(Sort sort) {
        Messages messages = plugin.messages();
        ItemStack icon = button(Material.HOPPER, "radio/sort", null, TagResolver.empty());
        icon.editMeta(meta -> {
            meta.displayName(messages.item("collectibles.sort.name",
                    Placeholder.component("mode", messages.component("collectibles.sort.modes." + sort.key()))));
            List<Component> lore = new ArrayList<>(messages.lore("collectibles.sort.lore"));
            for (Sort option : Sort.values()) {
                lore.add(messages.item(option == sort ? "collectibles.sort.option-selected" : "collectibles.sort.option",
                        Placeholder.component("mode", messages.component("collectibles.sort.modes." + option.key()))));
            }
            lore.addAll(messages.lore("collectibles.sort.hint"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        return icon;
    }

    /** A category's collectibles as the Sort button says. */
    private List<Item> sorted(Player player, List<Item> all, Sort sort) {
        java.util.stream.Stream<Item> items = all.stream();
        return switch (sort) {
            case ALL -> all;
            case UNLOCKED -> items.filter(item -> owns(player, item)).toList();
            case LOCKED -> items.filter(item -> !owns(player, item)).toList();
            case PURCHASABLE -> items.filter(item -> affordable(player, item)).toList();
            case RARITY -> items.sorted(java.util.Comparator.comparing((Item item) -> item.rarity()).reversed()
                    .thenComparing(Item::name)).toList();
            case SEASONAL -> items.filter(item -> item.season() != Season.NORMAL)
                    .sorted(java.util.Comparator.comparing(Item::season)).toList();
        };
    }

    /** Every suit (all seasons) as the Sort button says; a suit counts as unlocked once any piece is. */
    private List<Catalog.SuitSet> sortedSets(Player player, Sort sort) {
        List<Catalog.SuitSet> all = Catalog.SETS;
        java.util.function.Predicate<Catalog.SuitSet> unlocked = set -> set.pieces().stream().anyMatch(p -> owns(player, p));
        return switch (sort) {
            case ALL -> all;
            case UNLOCKED -> all.stream().filter(unlocked).toList();
            case LOCKED -> all.stream().filter(unlocked.negate()).toList();
            case PURCHASABLE -> all.stream().filter(set -> set.pieces().stream().anyMatch(p -> affordable(player, p))).toList();
            case RARITY -> all.stream().sorted(java.util.Comparator.comparing((Catalog.SuitSet set) -> set.rarity()).reversed()
                    .thenComparing(Catalog.SuitSet::name)).toList();
            case SEASONAL -> all.stream().filter(set -> set.season() != Season.NORMAL)
                    .sorted(java.util.Comparator.comparing(Catalog.SuitSet::season)).toList();
        };
    }

    /** Not unlocked yet, for sale with dust (all-year and in season), and the player has enough dust for it now. */
    private boolean affordable(Player player, Item item) {
        return !owns(player, item) && craftable(item) && data().get(player.getUniqueId()).dust() >= price(item);
    }

    /** What's on in a category now (for suits, any piece), or null. */
    private Item wearing(Player player, Category category) {
        if (category == Category.SUIT) {
            for (Catalog.Piece piece : Catalog.Piece.values()) {
                Item on = piece(player, piece);
                if (on != null) return on;
            }
            return null;
        }
        Item on = equipped(player, category);
        return on != null && owns(player, on) ? on : null;
    }

    /** "Are you sure?" (the shared confirm menu). */
    private void confirm(Player player, ItemStack shown, Runnable yes, Runnable no) {
        plugin.confirm().open(player, shown, yes, no);
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

    /** "Wearing: ..." under a category button (suits: the set with the most pieces on, and how many). */
    private Component wearingLine(Player player, Category category) {
        Messages messages = plugin.messages();
        if (category.action()) return Component.empty();
        boolean equip = category == Category.GADGET || category == Category.EMOTE;
        if (category == Category.SUIT) {
            java.util.Map<String, Integer> sets = new java.util.LinkedHashMap<>();
            for (Catalog.Piece piece : Catalog.Piece.values()) {
                Item on = piece(player, piece);
                if (on != null) sets.merge(on.set(), 1, Integer::sum);
            }
            if (sets.isEmpty()) return messages.component("collectibles.wearing-nothing");
            String best = sets.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).get().getKey();
            Catalog.SuitSet set = Catalog.set(best);
            return messages.component("collectibles.wearing-suit", Placeholder.parsed("set", plugin.mystery().rarityColourTag(set.rarity()) + set.name()),
                    Messages.arg("count", sets.get(best)), Messages.arg("more", sets.size() > 1 ? "+" : ""));
        }
        Item worn = equipped(player, category);
        if (worn == null || !owns(player, worn)) return messages.component(equip ? "collectibles.equipped-nothing" : "collectibles.wearing-nothing");
        return messages.component(equip ? "collectibles.equipped-line" : "collectibles.wearing", name(worn));
    }

    private ItemStack categoryIcon(Category category) {
        return switch (category) {
            case HAT -> modelled(Material.LEATHER_HELMET, "category/hats");
            case SUIT -> modelled(Material.LEATHER_CHESTPLATE, "category/suits");
            case GADGET -> modelled(Material.FIREWORK_STAR, "category/gadgets");
            case CLOAK -> modelled(Material.PHANTOM_MEMBRANE, "category/cloaks");
            case EMOTE -> modelled(Material.PLAYER_HEAD, "category/emotes");
            case GESTURE -> modelled(Material.FEATHER, "category/gestures");
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
