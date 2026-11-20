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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mystery Boxes: boxes of 1 to 5 stars. Every box is different: when it's made it draws a handful of possible prizes
 * (cosmetics of every rarity, or piles of Mystery Dust), shown when you hover over it, and opening it gives one of
 * them by the box's odds. A cosmetic the player already has turns into Mystery Dust instead, and dust crafts new
 * boxes (1 to 3 stars for everyone, 4 and 5 stars with lobby.mystery.craft.high).
 *
 * <p>Boxes only open at a Mystery Vault, one at a time per vault, with an animation everyone around can watch.
 * Players with lobby.mystery.multi can open many at once: no animation, a summary in chat. Seasonal collectibles never
 * come from these boxes. The store, votes and games give boxes with /mysterybox give.
 */
public final class MysteryBoxes implements Listener {

    public static final int MAX_STARS = 5;
    public static final String MULTI = "lobby.mystery.multi", CRAFT_HIGH = "lobby.mystery.craft.high";
    /** Background glyphs for the menus (resource pack). */
    private static final char VAULT_MENU = '\uF173', CRAFT_MENU = '\uF17C';
    /** The vault menu: stash on top, a grid of boxes (4 rows of 7), then previous, craft, open all, next. */
    private static final int[] GRID = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    private static final int STASH = 4, PREVIOUS = 45, CRAFT = 47, OPEN_ALL = 51, NEXT = 53;
    /** The craft menu: 1 to 5 stars and back. */
    private static final int[] CRAFT_SLOTS = {11, 12, 13, 14, 15};
    private static final int CRAFT_BACK = 22;

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
            } else if (reward.type().equals("glow")) {
                plugin.getLogger().info("mystery-boxes.rewards: glow is a rank perk (glow.min-rank), so it's left out of boxes");
            } else if (!reward.exists(plugin)) {
                plugin.getLogger().warning("config.yml mystery-boxes.rewards: there's no " + reward.id() + " (left out)");
            } else if (reward.free(plugin)) {
                plugin.getLogger().info("mystery-boxes.rewards: " + reward.id() + " is free for everyone, so it's left out");
            } else {
                rewards.add(reward);
            }
        }
        // Every collectible (hats, suits, gadgets, cloaks, emotes, gestures), seasonal ones only in their season.
        if (plugin.getConfig().getBoolean("mystery-boxes.include-collectibles", true)) {
            for (var item : com.sasha.lobby.collectibles.Catalog.ITEMS) rewards.add(new Reward(item.id(), "collectible", item.id(), item.rarity()));
        }
    }

    /** Whether a reward can be in a box: seasonal collectibles never are (they come from Seasonal Boxes). */
    private boolean available(Reward reward) {
        return !reward.collectible() || reward.item() != null && reward.item().season() == com.sasha.lobby.collectibles.Catalog.Season.NORMAL;
    }

    public void stop() {
        vault.stop();
        data.save(false);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("mystery-boxes.enabled", true) && !rewards.isEmpty();
    }

    public MysteryData data() {
        return data;
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
            case "collectible" -> plugin.collectibles() != null && plugin.collectibles().owns(player, reward.item());
            default -> false;
        };
    }

    // ---------------------------------------------------------------- getting boxes and dust

    /** Gives boxes (online or not), each with its own prizes. */
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
        data.name(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    /** (Parkour bests no longer give boxes.) */
    public void parkourBest(Player player, int score) {
    }

    /** Whether the player may craft boxes with this many stars (4 and 5 need lobby.mystery.craft.high). */
    boolean canCraft(Player player, int stars) {
        return stars <= 3 || player.hasPermission(CRAFT_HIGH);
    }

    /** Crafts a box from Mystery Dust. */
    private void craft(Player player, int stars) {
        if (!canCraft(player, stars)) {
            plugin.messages().send(player, "mystery.craft-locked", starsTag(stars));
            sound(player, "entity.villager.no", 1f);
            return;
        }
        long cost = craftCost(stars);
        MysteryData.Stash stash = data.get(player.getUniqueId());
        if (stash.dust() < cost) {
            plugin.messages().send(player, "mystery.craft-short", Messages.arg("cost", compact(cost)), Messages.arg("dust", compact(stash.dust())),
                    Messages.arg("need", compact(cost - stash.dust())), starsTag(stars));
            sound(player, "entity.villager.no", 1f);
            return;
        }
        data.addDust(player.getUniqueId(), -cost);
        data.addBoxes(player.getUniqueId(), stars, 1);
        plugin.messages().send(player, "mystery.crafted", Messages.arg("cost", compact(cost)), starsTag(stars));
        sound(player, "block.smithing_table.use", 1.2f);
        refresh(player.getUniqueId());
    }

    long craftCost(int stars) {
        long[] defaults = {0, 150, 300, 600, 1200, 2500};
        return Math.max(1, plugin.getConfig().getLong("mystery-boxes.craft-cost." + stars, defaults[stars]));
    }

    // ---------------------------------------------------------------- what's in a box

    /** The chance (percent) of each rarity for a box with this many stars, only rarities that have rewards. */
    Map<Reward.Rarity, Double> odds(int stars) {
        double[][] defaults = {{}, {55, 30, 12, 3, 0}, {40, 32, 20, 7, 1}, {25, 30, 28, 13, 4}, {10, 22, 35, 24, 9}, {0, 10, 35, 35, 20}};
        List<Double> configured = plugin.getConfig().getDoubleList("mystery-boxes.chances." + stars);
        Map<Reward.Rarity, Double> odds = new EnumMap<>(Reward.Rarity.class);
        for (Reward.Rarity rarity : Reward.Rarity.values()) {
            int i = rarity.ordinal();
            double chance = configured.size() > i ? configured.get(i) : defaults[stars][i];
            if (chance > 0 && rewards.stream().anyMatch(r -> r.rarity() == rarity && available(r))) odds.put(rarity, chance);
        }
        if (odds.isEmpty()) odds.put(Reward.Rarity.COMMON, 1.0);
        return odds;
    }

    private static Reward.Rarity pick(Map<Reward.Rarity, Double> odds, java.util.Random random) {
        double total = odds.values().stream().mapToDouble(Double::doubleValue).sum();
        double roll = random.nextDouble() * total;
        for (Map.Entry<Reward.Rarity, Double> entry : odds.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) return entry.getKey();
        }
        return odds.keySet().iterator().next();
    }

    /** What a box can give: 5 + stars different prizes drawn from its seed, rarest first. */
    public List<Reward> contents(MysteryData.Box box) {
        java.util.Random random = new java.util.Random(box.seed());
        Map<Reward.Rarity, Double> odds = odds(box.stars());
        int count = 5 + box.stars();
        List<Reward> picked = new ArrayList<>();
        for (int tries = 0; tries < count * 6 && picked.size() < count; tries++) {
            Reward.Rarity rarity = pick(odds, random);
            List<Reward> pool = rewards.stream().filter(r -> r.rarity() == rarity && available(r) && !picked.contains(r)).toList();
            if (!pool.isEmpty()) picked.add(pool.get(random.nextInt(pool.size())));
        }
        picked.sort(java.util.Comparator.comparing(Reward::rarity).reversed());
        return picked;
    }

    /**
     * The chance (whole percent) of each rarity a box can actually give: the box's odds shared out among only the
     * rarities its prizes have, so they add up to exactly 100 (rounded so the leftovers go to the biggest fractions).
     * This is what opening it does (see roll).
     */
    Map<Reward.Rarity, Integer> chances(MysteryData.Box box) {
        Map<Reward.Rarity, Double> odds = odds(box.stars());
        Map<Reward.Rarity, Double> present = new EnumMap<>(Reward.Rarity.class);
        for (Reward reward : contents(box)) present.put(reward.rarity(), odds.getOrDefault(reward.rarity(), 1.0));
        Map<Reward.Rarity, Integer> out = new EnumMap<>(Reward.Rarity.class);
        double total = present.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) return out;
        int given = 0;
        List<Map.Entry<Reward.Rarity, Double>> remainders = new ArrayList<>();
        for (Map.Entry<Reward.Rarity, Double> entry : present.entrySet()) {
            double exact = entry.getValue() / total * 100;
            out.put(entry.getKey(), (int) Math.floor(exact));
            given += (int) Math.floor(exact);
            remainders.add(Map.entry(entry.getKey(), exact - Math.floor(exact)));
        }
        remainders.sort(Map.Entry.<Reward.Rarity, Double>comparingByValue().reversed());
        for (int i = 0; given < 100 && i < remainders.size(); i++, given++) out.merge(remainders.get(i).getKey(), 1, Integer::sum);
        return out;
    }

    /** Opens a box: one of its prizes, each rarity as likely as the box's odds say. */
    Reward roll(MysteryData.Box box) {
        List<Reward> inside = contents(box);
        if (inside.isEmpty()) return null;
        Map<Reward.Rarity, Double> odds = odds(box.stars());
        Map<Reward.Rarity, Double> present = new EnumMap<>(Reward.Rarity.class);
        for (Reward reward : inside) present.put(reward.rarity(), odds.getOrDefault(reward.rarity(), 1.0));
        Reward.Rarity rarity = pick(present, ThreadLocalRandom.current());
        List<Reward> of = inside.stream().filter(r -> r.rarity() == rarity).toList();
        return of.get(ThreadLocalRandom.current().nextInt(of.size()));
    }

    /** The result of opening one box (the prize is already given). */
    record Prize(Reward reward, boolean duplicate, long dust) {
    }

    /** Takes the box and gives its prize (a duplicate cosmetic becomes Mystery Dust); null if they don't have it. */
    private Prize claim(Player player, MysteryData.Box box) {
        Reward reward = roll(box);
        if (reward == null || !data.take(player.getUniqueId(), box)) return null;
        data.opened(player.getUniqueId());
        boolean duplicate = false;
        long dust;
        if (reward.dust()) {
            dust = reward.dustAmount();
        } else if (has(player, reward)) {
            duplicate = true;
            dust = duplicateDust(reward.rarity());
        } else {
            dust = 0;
            data.unlock(player.getUniqueId(), reward.id());
        }
        if (dust > 0) data.addDust(player.getUniqueId(), dust);
        return new Prize(reward, duplicate, dust);
    }

    /** Opens one box at the vault the menu was opened from, with the full show. */
    private void open(Player player, Vault.Spot spot, MysteryData.Box box) {
        if (spot == null) {
            plugin.messages().send(player, "mystery.vault-only");
            sound(player, "entity.villager.no", 1f);
            return;
        }
        if (!data.get(player.getUniqueId()).boxes.contains(box)) return;
        Vault.Place place = vault.reserve(player, spot);
        if (place == null) return; // told why (busy, or they're already opening one)
        player.closeInventory();
        Prize prize = claim(player, box);
        if (prize == null) {
            vault.free(player, spot);
            return;
        }
        refresh(player.getUniqueId());
        new Opening(plugin, this, player, place, box.stars(), prize.reward(), prize.duplicate(), prize.dust()).start();
    }

    /** Opens many boxes at once, no show: a summary in chat (lobby.mystery.multi). */
    private void openMany(Player player, Vault.Spot spot, List<MysteryData.Box> list) {
        if (!player.hasPermission(MULTI)) {
            plugin.messages().send(player, "mystery.multi-locked");
            sound(player, "entity.villager.no", 1f);
            return;
        }
        if (spot == null) {
            plugin.messages().send(player, "mystery.vault-only");
            return;
        }
        if (spot.busyFor != null && !player.getUniqueId().equals(spot.busyFor)) {
            plugin.messages().send(player, "mystery.vault.busy", Messages.name("player", spot.busyName));
            return;
        }
        if (list.isEmpty()) return;
        player.closeInventory();
        // what came out, rarest first: each prize with how many times, and whether it was new
        Map<String, int[]> counts = new java.util.LinkedHashMap<>();   // id -> {times, new (1/0)}
        Map<String, Reward> byId = new java.util.HashMap<>();
        long dust = 0;
        int opened = 0, fresh = 0;
        for (MysteryData.Box box : list) {
            Prize prize = claim(player, box);
            if (prize == null) continue;
            opened++;
            dust += prize.dust();
            boolean isNew = !prize.reward().dust() && !prize.duplicate();
            if (isNew) fresh++;
            byId.put(prize.reward().id(), prize.reward());
            int[] c = counts.computeIfAbsent(prize.reward().id(), k -> new int[2]);
            c[0]++;
            if (isNew) c[1] = 1;
            if (isNew && announced(prize.reward().rarity())) {
                TagResolver tags = TagResolver.resolver(Placeholder.parsed("reward", prize.reward().name(plugin)),
                        Placeholder.parsed("rarity", rarityName(prize.reward().rarity())), Messages.name("player", player.getName()),
                        starsTag(box.stars()));
                for (Player everyone : plugin.getServer().getOnlinePlayers()) plugin.messages().send(everyone, "mystery.announce", tags);
            }
        }
        refresh(player.getUniqueId());
        Messages messages = plugin.messages();
        messages.send(player, "mystery.summary.header", Messages.arg("count", opened), Messages.arg("new", fresh));
        List<Map.Entry<String, int[]>> lines = new ArrayList<>(counts.entrySet());
        lines.sort(java.util.Comparator.comparing((Map.Entry<String, int[]> e) -> byId.get(e.getKey()).rarity()).reversed());
        for (Map.Entry<String, int[]> line : lines) {
            Reward reward = byId.get(line.getKey());
            String key = reward.dust() ? "mystery.summary.dust-line" : line.getValue()[1] == 1 ? "mystery.summary.new-line" : "mystery.summary.dupe-line";
            messages.send(player, key, Placeholder.parsed("reward", reward.name(plugin)), Placeholder.parsed("rarity", rarityName(reward.rarity())),
                    Messages.arg("times", line.getValue()[0]));
        }
        messages.send(player, "mystery.summary.footer", Messages.arg("dust", compact(dust)),
                Messages.arg("total", compact(data.get(player.getUniqueId()).dust())));
        // a quick pop over the vault
        Location top = spot.top();
        top.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, top.clone().add(0, 0.6, 0), 40, 0.3, 0.3, 0.3, 0.25);
        top.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, top.clone().add(0, 0.6, 0), 30, 0.3, 0.4, 0.3, 0.4);
        sound(player, "ui.toast.challenge_complete", 1.2f);
        sound(player, "entity.player.levelup", 1.4f);
    }

    long duplicateDust(Reward.Rarity rarity) {
        long[] defaults = {10, 18, 25, 60, 150};
        return Math.max(0, plugin.getConfig().getLong("mystery-boxes.rarities." + rarity.key() + ".duplicate-dust", defaults[rarity.ordinal()]));
    }

    private static final String[] RARITY_NAMES = {"<#C9B8EE>Common", "<#7FE39A>Uncommon", "<#7FA8FF>Rare", "<#C77DFF>Epic", "<#FFD27F>Legendary"};
    private static final String[] RARITY_COLOURS = {"#C9B8EE", "#7FE39A", "#7FA8FF", "#C77DFF", "#FFD27F"};

    /** The rarity's name and colour from config.yml. */
    String rarityName(Reward.Rarity rarity) {
        return plugin.getConfig().getString("mystery-boxes.rarities." + rarity.key() + ".name", RARITY_NAMES[rarity.ordinal()]);
    }

    /** The rarity's name with its colour, as MiniMessage ("<#FFD27F>Legendary"). */
    public String rarityLabel(Reward.Rarity rarity) {
        return rarityName(rarity);
    }

    /** The rarity's colour as a MiniMessage tag ("<#FFD27F>"). */
    public String rarityColourTag(Reward.Rarity rarity) {
        org.bukkit.Color c = rarityColor(rarity);
        return String.format("<#%02X%02X%02X>", c.getRed(), c.getGreen(), c.getBlue());
    }

    org.bukkit.Color rarityColor(Reward.Rarity rarity) {
        String hex = plugin.getConfig().getString("mystery-boxes.rarities." + rarity.key() + ".color", RARITY_COLOURS[rarity.ordinal()]);
        try {
            return org.bukkit.Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (NumberFormatException e) {
            return org.bukkit.Color.fromRGB(Integer.parseInt(RARITY_COLOURS[rarity.ordinal()].substring(1), 16));
        }
    }

    /** Everyone is told when someone finds something this rare or rarer. */
    boolean announced(Reward.Rarity rarity) {
        Reward.Rarity from = Reward.Rarity.of(plugin.getConfig().getString("mystery-boxes.announce", "legendary"));
        return from != null && rarity.ordinal() >= from.ordinal();
    }

    /** 950, 1.2k, 5.2M, 1.1B. */
    public static String compact(long n) {
        long a = Math.abs(n);
        if (a < 1000) return String.valueOf(n);
        String[] units = {"k", "M", "B", "T"};
        double v = n;
        int u = -1;
        while (Math.abs(v) >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        String text = Math.abs(v) >= 100 ? String.valueOf((long) Math.floor(v)) : String.format(java.util.Locale.ROOT, "%.1f", Math.floor(v * 10) / 10);
        if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        return text + units[u];
    }

    // ---------------------------------------------------------------- menus

    private enum Kind { MAIN, CRAFT }

    private static final class Menu implements InventoryHolder {
        private final Kind kind;
        private final Vault.Spot spot;
        private final int page;
        private final Map<Integer, MysteryData.Box> boxes = new java.util.HashMap<>();
        private Inventory inventory;

        Menu(Kind kind, Vault.Spot spot, int page) {
            this.kind = kind;
            this.spot = spot;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** The Mystery Boxes menu away from a vault: look at your boxes (they open at a vault). */
    public void openMenu(Player player) {
        openMenu(player, null, 0);
    }

    /** The Mystery Vault menu: your stash, every box you have (with what it can give) and crafting. */
    public void openMenu(Player player, Vault.Spot spot, int page) {
        if (!enabled()) {
            plugin.messages().send(player, "mystery.disabled");
            return;
        }
        Messages messages = plugin.messages();
        MenuStyle style = plugin.menuStyle();
        MysteryData.Stash stash = data.get(player.getUniqueId());
        List<MysteryData.Box> all = stash.boxList();
        int pages = Math.max(1, (all.size() + GRID.length - 1) / GRID.length);
        int shown = Math.max(0, Math.min(page, pages - 1));
        Menu menu = new Menu(Kind.MAIN, spot, shown);
        menu.inventory = Bukkit.createInventory(menu, 54, style.title(VAULT_MENU, messages.component("mystery.menu.title")));
        ItemStack filler = style.filler();
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);

        menu.inventory.setItem(STASH, item(Material.GLOWSTONE_DUST, "mystery/dust", "mystery.menu.stash",
                TagResolver.resolver(Messages.arg("dust", compact(stash.dust())), Messages.arg("boxes", compact(stash.totalBoxes())),
                        Messages.arg("opened", compact(stash.opened())))));
        boolean multi = player.hasPermission(MULTI);
        for (int i = 0; i < GRID.length; i++) {
            int index = shown * GRID.length + i;
            if (index >= all.size()) break;
            MysteryData.Box box = all.get(index);
            menu.inventory.setItem(GRID[i], boxIcon(box, spot != null, multi, stash.boxes(box.stars())));
            menu.boxes.put(GRID[i], box);
        }
        if (all.isEmpty()) menu.inventory.setItem(22, style.icon(item(Material.BARRIER, null, "mystery.menu.empty", TagResolver.empty()), "none"));
        menu.inventory.setItem(PREVIOUS, shown > 0
                ? style.icon(item(Material.ARROW, null, "mystery.menu.previous", Messages.arg("page", shown)), "back")
                : style.icon(item(Material.GRAY_DYE, null, "mystery.menu.no-previous", TagResolver.empty()), "back-off"));
        menu.inventory.setItem(NEXT, shown < pages - 1
                ? style.icon(item(Material.ARROW, null, "mystery.menu.next", Messages.arg("page", shown + 2)), "next")
                : style.icon(item(Material.GRAY_DYE, null, "mystery.menu.no-next", TagResolver.empty()), "next-off"));
        menu.inventory.setItem(CRAFT, item(Material.CRAFTING_TABLE, "mystery/craft", "mystery.menu.craft-button",
                TagResolver.resolver(Messages.arg("dust", compact(stash.dust())))));
        // in colour only with two or more boxes to open; greyed out with one or none, a padlock without the perk
        String openAll = !multi ? "mystery.menu.open-all-locked" : all.isEmpty() ? "mystery.menu.open-all-none"
                : all.size() == 1 ? "mystery.menu.open-all-one" : "mystery.menu.open-all";
        String openAllIcon = !multi ? "menu/locked" : all.size() > 1 ? "mystery/open_all" : "mystery/open_all_off";
        menu.inventory.setItem(OPEN_ALL, item(multi && all.size() > 1 ? Material.CHEST : Material.GRAY_DYE, openAllIcon,
                openAll, TagResolver.resolver(Messages.arg("boxes", compact(all.size())))));
        player.openInventory(menu.inventory);
    }

    /** A box in the menu: its stars, every prize it can give (by rarity) and how to open it. */
    private ItemStack boxIcon(MysteryData.Box box, boolean atVault, boolean multi, int sameStars) {
        Messages messages = plugin.messages();
        TagResolver tags = TagResolver.resolver(starsTag(box.stars()), Messages.arg("count", sameStars));
        List<Component> lore = new ArrayList<>(messages.lore("mystery.menu.box.lore", tags));
        Reward.Rarity last = null;
        Map<Reward.Rarity, Integer> chances = chances(box);
        for (Reward reward : contents(box)) {
            if (reward.rarity() != last) {
                last = reward.rarity();
                lore.add(messages.item("mystery.menu.box.rarity", Placeholder.parsed("rarity", rarityName(last)),
                        Messages.arg("chance", chances.getOrDefault(last, 0))));
            }
            lore.add(messages.item("mystery.menu.box.prize", Placeholder.parsed("reward", rarityColourTag(reward.rarity()) + reward.name(plugin))));
        }
        lore.add(Component.empty());
        lore.addAll(messages.lore(!atVault ? "mystery.menu.box.go-to-vault" : multi ? "mystery.menu.box.open-hint-multi" : "mystery.menu.box.open-hint", tags));
        ItemStack icon = item(Material.CHEST, "mystery/gift_" + box.stars(), null, tags); // the flat gift; the 3D box is for opening
        icon.editMeta(meta -> {
            meta.displayName(messages.item("mystery.menu.box.name", tags));
            meta.lore(lore);
        });
        return icon;
    }

    /** Crafting boxes with Mystery Dust. */
    private void openCraft(Player player, Vault.Spot spot) {
        Messages messages = plugin.messages();
        MenuStyle style = plugin.menuStyle();
        Menu menu = new Menu(Kind.CRAFT, spot, 0);
        menu.inventory = Bukkit.createInventory(menu, 27, style.title(CRAFT_MENU, messages.component("mystery.craft-menu.title")));
        ItemStack filler = style.filler();
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);
        MysteryData.Stash stash = data.get(player.getUniqueId());
        menu.inventory.setItem(4, item(Material.GLOWSTONE_DUST, "mystery/dust", "mystery.menu.stash",
                TagResolver.resolver(Messages.arg("dust", compact(stash.dust())), Messages.arg("boxes", compact(stash.totalBoxes())),
                        Messages.arg("opened", compact(stash.opened())))));
        for (int stars = 1; stars <= MAX_STARS; stars++) {
            long cost = craftCost(stars);
            TagResolver tags = TagResolver.resolver(starsTag(stars), Messages.arg("cost", compact(cost)),
                    Messages.arg("need", compact(Math.max(0, cost - stash.dust()))));
            String key = !canCraft(player, stars) ? "mystery.craft-menu.locked" : stash.dust() >= cost ? "mystery.craft-menu.craft" : "mystery.craft-menu.short";
            ItemStack icon = item(Material.CHEST, canCraft(player, stars) ? "mystery/gift_" + stars : "menu/locked", key, tags);
            menu.inventory.setItem(CRAFT_SLOTS[stars - 1], icon);
        }
        menu.inventory.setItem(CRAFT_BACK, style.icon(item(Material.ARROW, null, "mystery.craft-menu.back", TagResolver.empty()), "back"));
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
        ClickType click = event.getClick();
        // Every button says Left-click; a box also says Shift + Left-click (open all of its kind) for donors.
        boolean openMany = menu.kind == Kind.MAIN && menu.boxes.containsKey(slot) && click == ClickType.SHIFT_LEFT
                && player.hasPermission(MULTI);
        if (click != ClickType.LEFT && !openMany) return;
        switch (menu.kind) {
            case CRAFT -> {
                if (slot == CRAFT_BACK) {
                    sound(player, "ui.button.click", 1f);
                    openMenu(player, menu.spot, 0);
                    return;
                }
                for (int i = 0; i < MAX_STARS; i++) {
                    if (slot == CRAFT_SLOTS[i]) {
                        int stars = i + 1;
                        long cost = craftCost(stars);
                        if (!canCraft(player, stars) || data.get(player.getUniqueId()).dust() < cost) {
                            craft(player, stars); // says why it can't
                            return;
                        }
                        TagResolver tags = TagResolver.resolver(starsTag(stars), Messages.arg("cost", compact(cost)),
                                Messages.arg("dust", compact(data.get(player.getUniqueId()).dust())));
                        plugin.confirm().open(player, item(Material.CHEST, "mystery/gift_" + stars, "mystery.confirm.craft", tags), () -> {
                            craft(player, stars);
                            openCraft(player, menu.spot);
                        }, () -> openCraft(player, menu.spot));
                        return;
                    }
                }
            }
            case MAIN -> {
                MysteryData.Box box = menu.boxes.get(slot);
                if (box != null) {
                    if (menu.spot == null) {
                        open(player, null, box); // says boxes open at a Mystery Vault
                        return;
                    }
                    Runnable back = () -> openMenu(player, menu.spot, menu.page);
                    TagResolver tags = TagResolver.resolver(starsTag(box.stars()), Messages.arg("count", data.get(player.getUniqueId()).boxes(box.stars())));
                    ItemStack shown = item(Material.CHEST, "mystery/gift_" + box.stars(), openMany ? "mystery.confirm.open-kind" : "mystery.confirm.open", tags);
                    if (openMany) {
                        int stars = box.stars();
                        plugin.confirm().open(player, shown, () -> openMany(player, menu.spot,
                                data.get(player.getUniqueId()).boxList().stream().filter(b -> b.stars() == stars).toList()), back);
                    } else {
                        plugin.confirm().open(player, shown, () -> open(player, menu.spot, box), back);
                    }
                    return;
                }
                switch (slot) {
                    case PREVIOUS -> openMenu(player, menu.spot, menu.page - 1);
                    case NEXT -> openMenu(player, menu.spot, menu.page + 1);
                    case CRAFT -> {
                        sound(player, "ui.button.click", 1f);
                        openCraft(player, menu.spot);
                    }
                    case OPEN_ALL -> {
                        List<MysteryData.Box> every = data.get(player.getUniqueId()).boxList();
                        if (every.size() < 2 || !player.hasPermission(MULTI)) {
                            if (!player.hasPermission(MULTI)) openMany(player, menu.spot, every); // says it's a donor perk
                            return;
                        }
                        ItemStack shown = item(Material.CHEST, "mystery/open_all", "mystery.confirm.open-all",
                                TagResolver.resolver(Messages.arg("boxes", compact(every.size()))));
                        plugin.confirm().open(player, shown, () -> openMany(player, menu.spot, data.get(player.getUniqueId()).boxList()),
                                () -> openMenu(player, menu.spot, menu.page));
                    }
                    default -> {
                    }
                }
            }
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
                Messages.arg("amount", compact(amount)), Messages.arg("dust", compact(data.get(target.getUniqueId()).dust())));
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
        plugin.messages().send(player, "mystery.dust", Messages.arg("dust", compact(stash.dust())), Messages.arg("boxes", compact(stash.totalBoxes())));
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
