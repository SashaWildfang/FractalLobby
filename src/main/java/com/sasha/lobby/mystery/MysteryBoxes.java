/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.mystery;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.items.MenuStyle;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mystery Boxes: boxes of 1 to 5 stars that give a random cosmetic (a trail, a chat colour, glowing) or a pile of
 * Mystery Dust. A cosmetic the player already has turns into Mystery Dust instead, and dust crafts new boxes.
 *
 * <p>Players get a 1-star box every day they join (a bigger one every streak-days in a row), and one for a new
 * parkour best; the store or a vote plugin gives more with /mysterybox give. Boxes are opened at the Mystery Vault
 * (/mysterybox vault set), with an animation everyone around can watch; without a vault they open in front of the
 * player.
 */
public final class MysteryBoxes implements Listener {

    public static final int MAX_STARS = 5;
    /** Background glyphs for the menus (resource pack). */
    private static final char VAULT_MENU = '', COLLECTION_MENU = '';
    /** Menu slots: the five boxes, the craft buttons under them, the stash, the collection. */
    private static final int[] BOX_SLOTS = {11, 12, 13, 14, 15}, CRAFT_SLOTS = {20, 21, 22, 23, 24};
    private static final int STASH = 4, COLLECTION = 40, BACK = 49;

    private final Lobby plugin;
    private final MysteryData data;
    private final Vault vault;
    private final List<Reward> rewards = new ArrayList<>();

    public MysteryBoxes(Lobby plugin) {
        this.plugin = plugin;
        this.data = new MysteryData(plugin);
        this.vault = new Vault(plugin, this);
        loadRewards();
    }

    /** Builds the vault (after holograms have started). */
    public void start() {
        vault.start();
    }

    /** /lobby reload: rewards and the vault again. */
    public void reload() {
        loadRewards();
        vault.start();
    }

    /** Reads the rewards from config.yml (skipping ones that don't exist or are free for everyone). */
    private void loadRewards() {
        rewards.clear();
        for (String line : plugin.getConfig().getStringList("mystery-boxes.rewards")) {
            Reward reward = Reward.parse(line);
            if (reward == null) {
                plugin.getLogger().warning("config.yml mystery-boxes.rewards: '" + line + "' isn't \"type:value rarity\"");
            } else if (!reward.exists(plugin)) {
                plugin.getLogger().warning("config.yml mystery-boxes.rewards: there's no " + reward.id() + " (left out)");
            } else if (reward.free(plugin)) {
                plugin.getLogger().info("mystery-boxes.rewards: " + reward.id() + " is free for everyone, so it's left out");
            } else {
                rewards.add(reward);
            }
        }
    }

    public void stop() {
        vault.stop();
        data.save(false);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("mystery-boxes.enabled", true) && !rewards.isEmpty();
    }

    public MysteryData.Stash stash(UUID id) {
        return data.get(id);
    }

    public Vault vault() {
        return vault;
    }

    List<Reward> rewards() {
        return rewards;
    }

    // ---------------------------------------------------------------- unlocks (used by trails, chat colours, glow)

    /** Whether the player unlocked this cosmetic from a Mystery Box ("trail:hearts", "chatcolor:sunset", "glow"). */
    public boolean owns(Player player, String reward) {
        return data.get(player.getUniqueId()).owns(reward);
    }

    /** For static checks elsewhere (trails, chat colours, glow). */
    public static boolean unlocked(Player player, String reward) {
        Lobby lobby = JavaPlugin.getPlugin(Lobby.class);
        return lobby.mystery() != null && lobby.mystery().owns(player, reward);
    }

    /** Whether the player unlocked any chat colour from a box (then /chatcolor opens for them). */
    public static boolean anyChatColor(Player player) {
        Lobby lobby = JavaPlugin.getPlugin(Lobby.class);
        if (lobby.mystery() == null) return false;
        return lobby.mystery().stash(player.getUniqueId()).owned().stream().anyMatch(id -> id.startsWith("chatcolor:"));
    }

    /** Whether the player can use this reward without the box (a rank permission, or found it already). */
    private boolean has(Player player, Reward reward) {
        return switch (reward.type()) {
            case "trail" -> com.sasha.lobby.items.Trails.allowed(player, reward.trail(plugin));
            case "chatcolor" -> player.hasPermission(com.sasha.lobby.items.ChatColors.PERMISSION)
                    && com.sasha.lobby.items.ChatColors.allowed(player, reward.chatColor(plugin)) || owns(player, reward.id());
            case "glow" -> com.sasha.lobby.listener.GlowListener.allowed(player);
            default -> false;
        };
    }

    // ---------------------------------------------------------------- getting boxes and dust

    /** Gives boxes (online or not). */
    public void give(UUID id, int stars, int amount) {
        data.addBoxes(id, stars, amount);
        refresh(id);
    }

    public void giveDust(UUID id, long amount) {
        data.addDust(id, amount);
        refresh(id);
    }

    public void reset(UUID id) {
        data.reset(id);
        refresh(id);
    }

    private void refresh(UUID id) {
        Player player = plugin.getServer().getPlayer(id);
        if (player != null) plugin.display().refreshSidebar(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        data.name(player.getUniqueId(), player.getName());
        if (!enabled() || !plugin.getConfig().getBoolean("mystery-boxes.daily.enabled", true)) return;
        LocalDate today = LocalDate.now(plugin.lobbyConfig().scoreboard().timeZone());
        int streak = data.claimDaily(player.getUniqueId(), today);
        if (streak == 0) return;
        int every = Math.max(0, plugin.getConfig().getInt("mystery-boxes.daily.streak-days", 7));
        boolean bonus = every > 0 && streak % every == 0;
        int stars = clampStars(plugin.getConfig().getInt(bonus ? "mystery-boxes.daily.streak-stars" : "mystery-boxes.daily.stars", bonus ? 3 : 1));
        give(player.getUniqueId(), stars, 1);
        TagResolver tags = TagResolver.resolver(Messages.arg("streak", streak), starsTag(stars));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            plugin.messages().send(player, bonus ? "mystery.daily-streak" : "mystery.daily", tags);
            player.playSound(Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 0.5f, 1.4f), Sound.Emitter.self());
        }, 60L);
    }

    /** A new parkour personal best: a box, once a day, for a good enough score. */
    public void parkourBest(Player player, int score) {
        if (!enabled() || !plugin.getConfig().getBoolean("mystery-boxes.parkour-best.enabled", true)) return;
        if (score < plugin.getConfig().getInt("mystery-boxes.parkour-best.min-score", 10)) return;
        String key = "parkour-" + LocalDate.now(plugin.lobbyConfig().scoreboard().timeZone());
        if (!data.unlock(player.getUniqueId(), key)) return; // once a day
        int stars = clampStars(plugin.getConfig().getInt("mystery-boxes.parkour-best.stars", 1));
        give(player.getUniqueId(), stars, 1);
        plugin.messages().send(player, "mystery.parkour-best", starsTag(stars));
    }

    /** Crafts a box from Mystery Dust. */
    private void craft(Player player, int stars) {
        long cost = craftCost(stars);
        MysteryData.Stash stash = data.get(player.getUniqueId());
        if (stash.dust() < cost) {
            plugin.messages().send(player, "mystery.craft-short", Messages.arg("cost", cost), Messages.arg("dust", stash.dust()),
                    Messages.arg("need", cost - stash.dust()), starsTag(stars));
            sound(player, "entity.villager.no", 1f);
            return;
        }
        data.addDust(player.getUniqueId(), -cost);
        data.addBoxes(player.getUniqueId(), stars, 1);
        plugin.messages().send(player, "mystery.crafted", Messages.arg("cost", cost), starsTag(stars));
        sound(player, "block.smithing_table.use", 1.2f);
        refresh(player.getUniqueId());
    }

    long craftCost(int stars) {
        long[] defaults = {0, 150, 300, 600, 1200, 2500};
        return Math.max(1, plugin.getConfig().getLong("mystery-boxes.craft-cost." + stars, defaults[stars]));
    }

    // ---------------------------------------------------------------- opening

    /** Rolls a box: a rarity by the box's odds, then one of that rarity's rewards. */
    Reward roll(int stars) {
        Map<Reward.Rarity, Double> odds = odds(stars);
        double total = odds.values().stream().mapToDouble(Double::doubleValue).sum();
        double pick = ThreadLocalRandom.current().nextDouble(total);
        Reward.Rarity rarity = Reward.Rarity.COMMON;
        for (Map.Entry<Reward.Rarity, Double> entry : odds.entrySet()) {
            pick -= entry.getValue();
            if (pick < 0) {
                rarity = entry.getKey();
                break;
            }
        }
        Reward.Rarity chosen = rarity;
        List<Reward> pool = rewards.stream().filter(r -> r.rarity() == chosen).toList();
        if (pool.isEmpty()) pool = rewards; // nothing of that rarity: anything
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    /** The chance (percent) of each rarity for a box with this many stars, only rarities that have rewards. */
    Map<Reward.Rarity, Double> odds(int stars) {
        double[][] defaults = {{}, {70, 25, 5, 0}, {50, 35, 13, 2}, {30, 40, 24, 6}, {10, 40, 38, 12}, {0, 25, 50, 25}};
        List<Double> configured = plugin.getConfig().getDoubleList("mystery-boxes.odds." + stars);
        Map<Reward.Rarity, Double> odds = new EnumMap<>(Reward.Rarity.class);
        for (Reward.Rarity rarity : Reward.Rarity.values()) {
            int i = rarity.ordinal();
            double chance = configured.size() > i ? configured.get(i) : defaults[stars][i];
            if (chance > 0 && rewards.stream().anyMatch(r -> r.rarity() == rarity)) odds.put(rarity, chance);
        }
        if (odds.isEmpty()) odds.put(Reward.Rarity.COMMON, 1.0);
        return odds;
    }

    /** Opens one of the player's boxes: checks, then takes the box, gives the prize and plays the opening. */
    private void open(Player player, int stars) {
        MysteryData.Stash stash = data.get(player.getUniqueId());
        if (stash.boxes(stars) <= 0) {
            plugin.messages().send(player, "mystery.no-box", starsTag(stars));
            sound(player, "entity.villager.no", 1f);
            return;
        }
        Location at = vault.openingSpot(player);
        if (at == null) return; // told why (too far from the vault, or it's busy)
        player.closeInventory();
        data.addBoxes(player.getUniqueId(), stars, -1);
        data.opened(player.getUniqueId());
        Reward reward = roll(stars);
        // The prize is given now, so nothing is lost if they leave during the animation.
        boolean duplicate;
        long dust;
        if (reward.dust()) {
            duplicate = false;
            dust = reward.dustAmount();
        } else if (has(player, reward)) {
            duplicate = true;
            dust = duplicateDust(reward.rarity());
        } else {
            duplicate = false;
            dust = 0;
            data.unlock(player.getUniqueId(), reward.id());
        }
        if (dust > 0) data.addDust(player.getUniqueId(), dust);
        refresh(player.getUniqueId());
        new Opening(plugin, this, player, at, stars, reward, duplicate, dust).start();
    }

    long duplicateDust(Reward.Rarity rarity) {
        long[] defaults = {10, 25, 60, 150};
        return Math.max(0, plugin.getConfig().getLong("mystery-boxes.rarities." + rarity.key() + ".duplicate-dust", defaults[rarity.ordinal()]));
    }

    /** The rarity's name and colour from config.yml. */
    String rarityName(Reward.Rarity rarity) {
        String[] defaults = {"<#C9B8EE>Common", "<#7FA8FF>Rare", "<#C77DFF>Epic", "<#FFD27F>Legendary"};
        return plugin.getConfig().getString("mystery-boxes.rarities." + rarity.key() + ".name", defaults[rarity.ordinal()]);
    }

    org.bukkit.Color rarityColor(Reward.Rarity rarity) {
        String[] defaults = {"#C9B8EE", "#7FA8FF", "#C77DFF", "#FFD27F"};
        String hex = plugin.getConfig().getString("mystery-boxes.rarities." + rarity.key() + ".color", defaults[rarity.ordinal()]);
        try {
            return org.bukkit.Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (NumberFormatException e) {
            return org.bukkit.Color.fromRGB(Integer.parseInt(defaults[rarity.ordinal()].substring(1), 16));
        }
    }

    /** Everyone is told when someone finds something this rare or rarer. */
    boolean announced(Reward.Rarity rarity) {
        Reward.Rarity from = Reward.Rarity.of(plugin.getConfig().getString("mystery-boxes.announce", "legendary"));
        return from != null && rarity.ordinal() >= from.ordinal();
    }

    // ---------------------------------------------------------------- menus

    private static final class Menu implements InventoryHolder {
        private final boolean collection;
        private Inventory inventory;

        Menu(boolean collection) {
            this.collection = collection;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** The Mystery Vault menu: the player's boxes, crafting with dust, and their collection. */
    public void openMenu(Player player) {
        if (!enabled()) {
            plugin.messages().send(player, "mystery.disabled");
            return;
        }
        Messages messages = plugin.messages();
        MenuStyle style = plugin.menuStyle();
        Menu menu = new Menu(false);
        menu.inventory = Bukkit.createInventory(menu, 45, style.title(VAULT_MENU, messages.component("mystery.menu.title")));
        ItemStack filler = style.filler();
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);
        MysteryData.Stash stash = data.get(player.getUniqueId());

        menu.inventory.setItem(STASH, item(Material.GLOWSTONE_DUST, "mystery/dust", "mystery.menu.stash",
                TagResolver.resolver(Messages.arg("dust", stash.dust()), Messages.arg("boxes", stash.totalBoxes()),
                        Messages.arg("opened", stash.opened()), Messages.arg("streak", stash.streak()))));
        for (int stars = 1; stars <= MAX_STARS; stars++) {
            int count = stash.boxes(stars);
            List<Component> lore = new ArrayList<>(messages.lore("mystery.menu.box.lore", Messages.arg("count", count)));
            for (Map.Entry<Reward.Rarity, Double> odd : odds(stars).entrySet()) {
                lore.add(messages.item("mystery.menu.box.odds", Placeholder.parsed("rarity", rarityName(odd.getKey())),
                        Messages.arg("chance", trim(odd.getValue()))));
            }
            lore.add(Component.empty());
            lore.addAll(messages.lore(count > 0 ? "mystery.menu.box.open-hint" : "mystery.menu.box.none-hint"));
            TagResolver boxTags = TagResolver.resolver(starsTag(stars), Messages.arg("count", count));
            ItemStack box = item(Material.CHEST, "mystery/box_" + stars, null, boxTags);
            int shown = Math.max(1, Math.min(64, count));
            box.setAmount(shown);
            box.editMeta(meta -> {
                meta.displayName(messages.item("mystery.menu.box.name", boxTags));
                meta.lore(lore);
            });
            menu.inventory.setItem(BOX_SLOTS[stars - 1], box);

            long cost = craftCost(stars);
            boolean afford = stash.dust() >= cost;
            menu.inventory.setItem(CRAFT_SLOTS[stars - 1], item(afford ? Material.LIME_DYE : Material.GRAY_DYE,
                    afford ? "mystery/craft" : "mystery/craft_locked", afford ? "mystery.menu.craft" : "mystery.menu.craft-short",
                    TagResolver.resolver(starsTag(stars), Messages.arg("cost", cost), Messages.arg("need", Math.max(0, cost - stash.dust())))));
        }
        int found = (int) rewards.stream().filter(r -> !r.dust() && (owns(player, r.id()) || has(player, r))).count();
        int total = (int) rewards.stream().filter(r -> !r.dust()).count();
        menu.inventory.setItem(COLLECTION, item(Material.BOOK, "mystery/collection", "mystery.menu.collection",
                TagResolver.resolver(Messages.arg("found", found), Messages.arg("total", total))));
        player.openInventory(menu.inventory);
    }

    /** Every cosmetic a box can give: found ones in colour, the rest locked. */
    private void openCollection(Player player) {
        Messages messages = plugin.messages();
        MenuStyle style = plugin.menuStyle();
        Menu menu = new Menu(true);
        menu.inventory = Bukkit.createInventory(menu, 54, style.title(COLLECTION_MENU, messages.component("mystery.collection.title")));
        ItemStack filler = style.filler();
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);
        int slot = 0;
        for (Reward.Rarity rarity : Reward.Rarity.values()) {
            for (Reward reward : rewards) {
                if (reward.rarity() != rarity || reward.dust() || slot >= 45) continue;
                boolean found = owns(player, reward.id()) || has(player, reward);
                TagResolver tags = TagResolver.resolver(Placeholder.parsed("reward", reward.name(plugin)),
                        Placeholder.parsed("rarity", rarityName(rarity)));
                ItemStack icon = found ? reward.icon(plugin) : style.icon(ItemStack.of(Material.GRAY_DYE), "mystery/locked");
                icon.editMeta(meta -> {
                    meta.displayName(messages.item(found ? "mystery.collection.found.name" : "mystery.collection.locked.name", tags));
                    meta.lore(messages.lore(found ? "mystery.collection.found.lore" : "mystery.collection.locked.lore", tags));
                    meta.addItemFlags(ItemFlag.values());
                });
                menu.inventory.setItem(slot++, icon);
            }
        }
        menu.inventory.setItem(BACK, style.icon(item(Material.ARROW, null, "mystery.collection.back", TagResolver.empty()), "back"));
        player.openInventory(menu.inventory);
    }

    /** A menu button with a name and lore from messages.yml (KEY.name, KEY.lore) and, with the pack, its picture. */
    private ItemStack item(Material material, String model, String key, TagResolver tags) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            if (key != null) {
                meta.displayName(plugin.messages().item(key + ".name", tags));
                meta.lore(plugin.messages().lore(key + ".lore", tags));
            }
            meta.addItemFlags(ItemFlag.values());
            if (model != null && plugin.menuStyle().custom()) meta.setItemModel(new NamespacedKey("fractal", model));
        });
        return item;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (menu.collection) {
            if (slot == BACK) {
                sound(player, "ui.button.click", 1f);
                openMenu(player);
            }
            return;
        }
        for (int i = 0; i < MAX_STARS; i++) {
            if (slot == BOX_SLOTS[i]) {
                open(player, i + 1);
                return;
            }
            if (slot == CRAFT_SLOTS[i]) {
                craft(player, i + 1);
                if (player.getOpenInventory().getTopInventory() == menu.inventory) openMenu(player);
                return;
            }
        }
        if (slot == COLLECTION) {
            sound(player, "ui.button.click", 1f);
            openCollection(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }

    // ---------------------------------------------------------------- commands

    /** /mysterybox give NAME STARS [AMOUNT] (console or admins), for the store, votes and events. */
    public void giveCommand(org.bukkit.command.CommandSender sender, String name, int stars, int amount) {
        OfflinePlayer target = find(name);
        if (target == null) {
            plugin.messages().send(sender, "mystery.unknown-player", Messages.name("input", name));
            return;
        }
        give(target.getUniqueId(), clampStars(stars), amount);
        String shown = target.getName() == null ? name : target.getName();
        plugin.messages().send(sender, "mystery.given", Messages.name("player", shown), Messages.arg("amount", amount), starsTag(stars));
        Player online = target.getPlayer();
        if (online != null && amount > 0) {
            plugin.messages().send(online, "mystery.received", Messages.arg("amount", amount), starsTag(stars));
            sound(online, "entity.player.levelup", 1.4f);
        }
    }

    /** /mysterybox dust NAME AMOUNT (negative takes dust away). */
    public void dustCommand(org.bukkit.command.CommandSender sender, String name, long amount) {
        OfflinePlayer target = find(name);
        if (target == null) {
            plugin.messages().send(sender, "mystery.unknown-player", Messages.name("input", name));
            return;
        }
        giveDust(target.getUniqueId(), amount);
        plugin.messages().send(sender, "mystery.dust-given", Messages.name("player", target.getName() == null ? name : target.getName()),
                Messages.arg("amount", amount), Messages.arg("dust", data.get(target.getUniqueId()).dust()));
    }

    /** /mysterybox reset NAME: takes away everything (boxes, dust, unlocks). */
    public void resetCommand(org.bukkit.command.CommandSender sender, String name) {
        OfflinePlayer target = find(name);
        if (target == null) {
            plugin.messages().send(sender, "mystery.unknown-player", Messages.name("input", name));
            return;
        }
        reset(target.getUniqueId());
        plugin.messages().send(sender, "mystery.reset", Messages.name("player", target.getName() == null ? name : target.getName()));
    }

    /** /dust: how much Mystery Dust the player has. */
    public void showDust(Player player) {
        MysteryData.Stash stash = data.get(player.getUniqueId());
        plugin.messages().send(player, "mystery.dust", Messages.arg("dust", stash.dust()), Messages.arg("boxes", stash.totalBoxes()));
    }

    private OfflinePlayer find(String name) {
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer cached = plugin.getServer().getOfflinePlayerIfCached(name);
        if (cached != null) return cached;
        for (OfflinePlayer known : plugin.getServer().getOfflinePlayers()) {
            if (name.equalsIgnoreCase(known.getName())) return known;
        }
        return null;
    }

    // ---------------------------------------------------------------- bits

    static int clampStars(int stars) {
        return Math.max(1, Math.min(MAX_STARS, stars));
    }

    /** &lt;stars&gt; as ★★★ (gold) followed by the missing ones dimmed, and &lt;star-count&gt;. */
    TagResolver starsTag(int stars) {
        String full = "★".repeat(stars), empty = "★".repeat(MAX_STARS - stars);
        Component shown = Component.text(full).color(net.kyori.adventure.text.format.TextColor.color(0xFFD27F))
                .append(Component.text(empty).color(NamedTextColor.DARK_GRAY));
        return TagResolver.resolver(Placeholder.component("stars", shown), Messages.arg("star-count", stars));
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    static void sound(Player player, String key, float pitch) {
        player.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, 0.6f, pitch), Sound.Emitter.self());
    }
}
