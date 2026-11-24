/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.network.LevelRewards;
import com.sasha.lobby.network.NetworkBoosters;
import com.sasha.lobby.network.NetworkLevels;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * My Profile → Levels (/levels): the network level snake.
 *
 * <p>The snake runs across row 1, turns down the right, back along row 3, down the left and across row 5: 29 slots,
 * a level every third slot (ten a page) and two beads between levels that fill with the XP towards the next one;
 * the last bead leads on to the next page. Opening it, the snake fills up from the start to where the player is,
 * then a shine runs along the filled part (Java with the resource pack; Bedrock gets plain items).
 *
 * <p>Top row: back, coin multiplier, the player's level, Claim All, next Mystery Box. Page arrows sit beside the
 * snake's start (previous) and end (next).
 */
public final class LevelingMenu implements Listener {

    private static final char BACKGROUND = '';
    /** The snake, in order: row 1 left to right, the right of row 2, row 3 right to left, the left of row 4, row 5. */
    static final int[] PATH;
    static final int BACK = 0, MULTIPLIER = 2, HEAD = 4, CLAIM_ALL = 6, NEXT_BOX = 8, PREVIOUS = 18, NEXT = 44;

    static {
        List<Integer> path = new ArrayList<>();
        for (int s = 9; s <= 17; s++) path.add(s);
        path.add(26);
        for (int s = 35; s >= 27; s--) path.add(s);
        path.add(36);
        for (int s = 45; s <= 53; s++) path.add(s);
        PATH = path.stream().mapToInt(Integer::intValue).toArray();
    }

    private final Lobby plugin;

    public LevelingMenu(Lobby plugin) {
        this.plugin = plugin;
    }

    /** A bead on the snake: its place along it and how full it is (0..1). */
    private record Bead(int slot, int order, double fill) {}

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        private int page;
        private final Map<Integer, Integer> levels = new HashMap<>();
        private final List<Bead> beads = new ArrayList<>();
        private BukkitTask animation;
        private int frame;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Opens on the page with the player's level (after reading what they've claimed). */
    public void open(Player player) {
        open(player, -1);
    }

    private void open(Player player, int page) {
        LevelRewards rewards = plugin.levelRewards();
        if (rewards == null || plugin.levels() == null) {
            plugin.messages().send(player, "boosters.unavailable");
            return;
        }
        rewards.load(player).whenComplete((claimed, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            int level = plugin.levels().progress(player).level();
            show(player, page < 0 ? (level - 1) / 10 : page);
        }));
    }

    private int pages(int level) {
        return (Math.max(plugin.levelRewards().levelsShown(), level + 10) + 9) / 10;
    }

    private boolean custom(Player player) {
        return plugin.menuStyle().filler(player) == null;              // the resource pack's backgrounds are on
    }

    private void show(Player player, int page) {
        Messages messages = plugin.messages();
        MenuStyle style = plugin.menuStyle();
        LevelRewards rewards = plugin.levelRewards();
        NetworkLevels.Progress progress = plugin.levels().progress(player);
        int level = progress.level();
        page = Math.max(0, Math.min(page, pages(level) - 1));

        Menu menu = new Menu();
        menu.page = page;
        menu.inventory = Bukkit.createInventory(menu, 54, style.title(player, BACKGROUND,
                messages.component("levels.title", Messages.arg("page", page + 1), Messages.arg("pages", pages(level)))));
        ItemStack filler = style.filler(player);
        for (int i = 0; i < 54; i++) menu.inventory.setItem(i, filler);

        Map<Integer, Integer> claimed = rewards.claimed(player);
        for (int i = 0; i < PATH.length; i++) {
            int node = i / 3 * 3;                                     // the level slot at or before this one
            int l = page * 10 + node / 3 + 1;
            if (i % 3 == 0) {
                menu.inventory.setItem(PATH[i], node(player, l, level, progress, claimed));
                menu.levels.put(PATH[i], l);
            } else {
                // two beads between level l and l + 1: full once past l, filling while at l
                double fill = level > l ? 1 : level == l ? Math.max(0, Math.min(1, progress.fraction() * 2 - (i % 3 - 1))) : 0;
                menu.beads.add(new Bead(PATH[i], i, fill));
                menu.inventory.setItem(PATH[i], bead(player, fill, false, level == l, progress, l));
            }
        }
        menu.inventory.setItem(BACK, style.icon(Profile.button(messages, Material.ARROW, "profile.back"), "back"));
        menu.inventory.setItem(MULTIPLIER, multiplier(player, level));
        menu.inventory.setItem(HEAD, head(player, progress));
        menu.inventory.setItem(CLAIM_ALL, claimAll(player, rewards.waiting(player, level).size()));
        menu.inventory.setItem(NEXT_BOX, nextBox(player, level, progress));
        if (page > 0) menu.inventory.setItem(PREVIOUS, style.icon(Profile.button(messages, Material.ARROW, "levels.previous",
                Messages.arg("page", page), Messages.arg("from", (page - 1) * 10 + 1), Messages.arg("to", page * 10)), "back"));
        if (page < pages(level) - 1) menu.inventory.setItem(NEXT, style.icon(Profile.button(messages, Material.ARROW, "levels.next",
                Messages.arg("page", page + 2), Messages.arg("from", (page + 1) * 10 + 1), Messages.arg("to", (page + 2) * 10)), "next"));
        player.openInventory(menu.inventory);
        if (custom(player)) animate(player, menu);
    }

    // ---------------------------------------------------------------- the snake

    /** One bead: Java gets the pack's bead (empty, quarters, full, shining); Bedrock a pane. */
    private ItemStack bead(Player player, double fill, boolean shine, boolean current, NetworkLevels.Progress progress, int l) {
        int quarters = fill <= 0 ? 0 : Math.max(1, (int) Math.round(fill * 4));
        ItemStack item;
        if (custom(player)) {
            item = ItemStack.of(Material.PAPER);
            String model = shine ? "level/bead_glow" : "level/bead_" + quarters;
            item.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", model)));
        } else {
            item = ItemStack.of(quarters >= 4 ? Material.PURPLE_STAINED_GLASS_PANE : quarters > 0 ? Material.MAGENTA_STAINED_GLASS_PANE
                    : Material.GRAY_STAINED_GLASS_PANE);
        }
        if (current) {
            // the beads towards the next level say how far along it is
            TagResolver tags = progressTags(progress);
            item.editMeta(meta -> {
                meta.displayName(plugin.messages().item("levels.bead.name", tags));
                meta.lore(plugin.messages().lore("levels.bead.lore", tags));
                meta.addItemFlags(ItemFlag.values());
            });
        } else {
            item.editMeta(meta -> meta.setHideTooltip(true));
        }
        return item;
    }

    /**
     * Every 2 ticks while the menu is open: first the snake fills from its start to where the player is (about a
     * second), then a shine runs along the filled beads, again and again.
     */
    private void animate(Player player, Menu menu) {
        NetworkLevels.Progress progress = plugin.levels().progress(player);
        List<Bead> filled = menu.beads.stream().filter(b -> b.fill() >= 1).toList();
        int reveal = 12;                                                   // frames for the fill (2 ticks each)
        int run = filled.size() + 5;                                       // the shine's trip, then a short pause
        menu.animation = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != menu.inventory) {
                menu.animation.cancel();
                return;
            }
            int f = menu.frame++;
            int level = progress.level();
            if (f < reveal) {
                // filling: beads up to this point along the snake show their fill, the rest wait empty
                double reach = (f + 1.0) / reveal * PATH.length;
                for (Bead b : menu.beads) {
                    int l = menu.page * 10 + b.order() / 3 + 1;
                    menu.inventory.setItem(b.slot(), bead(player, b.order() > reach ? 0 : b.fill(), false, level == l, progress, l));
                }
                if (f == reveal - 1) player.playSound(Sound.sound(Key.key("block.amethyst_block.chime"), Sound.Source.MASTER, 0.5f, 1.4f),
                        Sound.Emitter.self());
                return;
            }
            if (filled.isEmpty()) {
                menu.animation.cancel();
                return;
            }
            int now = (f - reveal) % run, before = (f - reveal - 1 + run) % run;
            if (before < filled.size()) set(player, menu, filled.get(before), false, progress);
            if (now < filled.size()) set(player, menu, filled.get(now), true, progress);
        }, 1L, 2L);
    }

    private void set(Player player, Menu menu, Bead b, boolean shine, NetworkLevels.Progress progress) {
        int l = menu.page * 10 + b.order() / 3 + 1;
        menu.inventory.setItem(b.slot(), bead(player, b.fill(), shine, progress.level() == l, progress, l));
    }

    // ---------------------------------------------------------------- levels

    private TagResolver progressTags(NetworkLevels.Progress p) {
        int width = 20, filled = (int) Math.round(p.fraction() * width);
        Locale l = Locale.ROOT;
        return TagResolver.resolver(Messages.arg("level", p.level()), Messages.arg("next", p.level() + 1),
                Placeholder.parsed("bar", "<#B266FF>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(width - filled)),
                Messages.arg("percent", (int) (p.fraction() * 100)),
                Messages.arg("xp", String.format(l, "%,d", p.into())), Messages.arg("needed", String.format(l, "%,d", p.needed())),
                Messages.arg("left", String.format(l, "%,d", p.needed() - p.into())),
                Messages.arg("total", String.format(l, "%,d", p.total())));
    }

    /** "1★ 60% · 2★ 30% · 3★ 10%" from level-rewards.mystery-box-stars. */
    private String odds() {
        var weights = plugin.levelRewards().boxWeights();
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        StringBuilder out = new StringBuilder();
        for (var w : weights.entrySet()) {
            if (!out.isEmpty()) out.append(" <dark_gray>·</dark_gray> ");
            out.append("<white>").append(w.getKey()).append("★</white> <muted>").append(Math.round(w.getValue() * 100.0 / total)).append("%</muted>");
        }
        return out.toString();
    }

    /** One level on the snake. */
    private ItemStack node(Player player, int l, int level, NetworkLevels.Progress progress, Map<Integer, Integer> claimed) {
        Messages messages = plugin.messages();
        LevelRewards.Reward reward = plugin.levelRewards().reward(l);
        boolean java = custom(player);
        String state = l == 1 ? "start" : claimed.containsKey(l) ? "claimed" : l <= level ? "ready" : "locked";
        Material base;
        String model;
        switch (state) {
            case "start", "claimed" -> {
                base = Material.LIME_DYE;
                model = "level/node_claimed";
            }
            case "ready" -> {
                base = reward.box() ? Material.ENDER_CHEST : Material.EXPERIENCE_BOTTLE;
                model = reward.box() ? "mystery/box_3" : "level/node_ready";
            }
            default -> {
                base = reward.box() ? Material.ENDER_CHEST : Material.GRAY_DYE;
                model = reward.box() ? "mystery/box_1" : "level/node_locked";
            }
        }
        long toGo = 0;
        if (state.equals("locked")) {
            toGo = progress.needed() - progress.into();
            for (int x = level + 1; x < l; x++) toGo += NetworkLevels.needed(x, plugin.levels().first(), plugin.levels().extra());
        }
        TagResolver tags = TagResolver.resolver(Messages.arg("level", l),
                Messages.arg("dust", String.format(Locale.ROOT, "%,d", reward.dust())),
                Messages.arg("coins", String.format(Locale.ROOT, "%,d", reward.coins())),
                Messages.arg("xp", String.format(Locale.ROOT, "%,d", toGo)),
                Messages.arg("stars", claimed.getOrDefault(l, 0)), Placeholder.parsed("odds", odds()));
        List<Component> lore = new ArrayList<>();
        if (!state.equals("start")) {
            lore.addAll(messages.lore("levels.rewards", tags));
            if (reward.box()) lore.addAll(messages.lore(claimed.getOrDefault(l, 0) > 0 ? "levels.reward-box-got" : "levels.reward-box", tags));
        }
        lore.addAll(messages.lore("levels.node." + state + ".lore", tags));
        ItemStack item = ItemStack.of(base);
        item.editMeta(meta -> {
            meta.displayName(messages.item("levels.node." + state + ".name", tags));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            if (java) meta.setItemModel(new NamespacedKey("fractal", model));
        });
        if (state.equals("ready")) item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 99);
        item.setAmount(Math.max(1, Math.min(99, l)));
        return item;
    }

    // ---------------------------------------------------------------- the top row

    private ItemStack head(Player player, NetworkLevels.Progress progress) {
        TagResolver tags = TagResolver.resolver(progressTags(progress),
                Messages.arg("multiplier", NetworkBoosters.multiplier(plugin.levelRewards().multiplier(progress.level()))),
                Messages.arg("waiting", plugin.levelRewards().waiting(player, progress.level()).size()));
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            meta.setPlayerProfile(player.getPlayerProfile());
            meta.displayName(plugin.messages().item("levels.head.name", tags));
            meta.lore(plugin.messages().lore("levels.head.lore", tags));
        });
        Profile.hideProfileLine(head);
        return com.sasha.lobby.hook.Bedrock.head(player, head);
    }

    /** The coin multiplier and its steps: reached ones ticked, the next one marked. */
    private ItemStack multiplier(Player player, int level) {
        Messages messages = plugin.messages();
        LevelRewards rewards = plugin.levelRewards();
        String now = NetworkBoosters.multiplier(rewards.multiplier(level));
        List<Component> lore = new ArrayList<>(messages.lore("levels.multiplier.lore", Messages.arg("multiplier", now)));
        boolean nextShown = false;
        for (var step : rewards.multipliers().entrySet()) {
            boolean reached = level >= step.getKey();
            String key = reached ? "levels.multiplier.step-reached" : !nextShown ? "levels.multiplier.step-next" : "levels.multiplier.step";
            if (!reached) nextShown = true;
            lore.add(messages.render(messages.raw(key), Messages.arg("level", step.getKey()),
                    Messages.arg("multiplier", NetworkBoosters.multiplier(step.getValue()))));
        }
        lore.addAll(messages.lore("levels.multiplier.footer"));
        ItemStack item = ItemStack.of(Material.GOLD_INGOT);
        boolean java = custom(player);
        item.editMeta(meta -> {
            meta.displayName(messages.item("levels.multiplier.name", Messages.arg("multiplier", now)));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            if (java) meta.setItemModel(new NamespacedKey("fractal", "level/coins"));
        });
        return item;
    }

    private ItemStack claimAll(Player player, int count) {
        Messages messages = plugin.messages();
        ItemStack item = Profile.button(messages, count > 0 ? Material.CHEST : Material.BARREL,
                count > 0 ? "levels.claim-all" : "levels.claim-none", Messages.arg("count", count));
        if (custom(player)) item.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", "level/claim")));
        if (count > 0) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            item.setData(DataComponentTypes.MAX_STACK_SIZE, 99);
            item.setAmount(Math.min(99, count));
        }
        return item;
    }

    /** The next level with a Mystery Box, and how far off it is. */
    private ItemStack nextBox(Player player, int level, NetworkLevels.Progress progress) {
        int every = Math.max(0, plugin.getConfig().getInt("level-rewards.mystery-box-every", 5));
        if (every == 0) return null;
        int target = (level / every + 1) * every;
        long toGo = progress.needed() - progress.into();
        for (int x = level + 1; x < target; x++) toGo += NetworkLevels.needed(x, plugin.levels().first(), plugin.levels().extra());
        ItemStack item = Profile.button(plugin.messages(), Material.ENDER_CHEST, "levels.next-box", Messages.arg("level", target),
                Messages.arg("levels", target - level), Messages.arg("xp", String.format(Locale.ROOT, "%,d", toGo)),
                Placeholder.parsed("odds", odds()));
        if (custom(player)) item.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", "mystery/box_2")));
        return item;
    }

    // ---------------------------------------------------------------- clicks

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClick() != ClickType.LEFT) return;
        int slot = event.getRawSlot();
        int level = plugin.levels().progress(player).level();
        switch (slot) {
            case BACK -> {
                click(player);
                plugin.profile().open(player);
            }
            case PREVIOUS -> {
                if (menu.page > 0) {
                    click(player);
                    show(player, menu.page - 1);
                }
            }
            case NEXT -> {
                if (menu.page < pages(level) - 1) {
                    click(player);
                    show(player, menu.page + 1);
                }
            }
            case CLAIM_ALL -> claim(player, plugin.levelRewards().waiting(player, level), menu.page);
            default -> {
                Integer l = menu.levels.get(slot);
                if (l == null || l < 2 || plugin.levelRewards().claimed(player).containsKey(l)) return;
                if (l > level) {
                    player.playSound(Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 0.5f, 1f), Sound.Emitter.self());
                    return;
                }
                claim(player, List.of(l), menu.page);
            }
        }
    }

    private final java.util.Set<java.util.UUID> busy = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void claim(Player player, List<Integer> levels, int page) {
        if (levels.isEmpty() || !busy.add(player.getUniqueId())) return;     // one claim at a time
        var cosmetics = Lobby.cosmeticsHook();
        int level = plugin.levels().progress(player).level();
        plugin.levelRewards().claim(player, levels, level).whenComplete((given, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            busy.remove(player.getUniqueId());
            Messages messages = plugin.messages();
            if (error != null) {
                messages.send(player, "boosters.unavailable");
                return;
            }
            if (given.isEmpty()) return;
            long dust = 0;
            int coins = 0;
            Map<Integer, Integer> boxes = new TreeMap<>();
            for (LevelRewards.Given g : given) {
                dust += g.dust();
                coins += g.coins();
                if (g.stars() > 0) boxes.merge(g.stars(), 1, Integer::sum);
            }
            boolean delivered = true;
            if (cosmetics != null) {
                if (dust > 0) delivered &= cosmetics.giveDust(player.getUniqueId(), dust);
                for (var box : boxes.entrySet()) delivered &= cosmetics.giveBoxes(player.getUniqueId(), box.getKey(), box.getValue());
            } else {
                delivered = dust == 0 && boxes.isEmpty();
            }
            if (!delivered) plugin.getLogger().warning("Level rewards for " + player.getName() + ": FractalCosmetics couldn't give "
                    + dust + " dust and boxes " + boxes + " (update FractalCosmetics to 1.1.1+).");
            StringBuilder boxText = new StringBuilder();
            for (var box : boxes.entrySet()) {
                if (!boxText.isEmpty()) boxText.append(", ");
                boxText.append(box.getValue() > 1 ? box.getValue() + "× " : "").append(box.getKey()).append("★");
            }
            TagResolver tags = TagResolver.resolver(Messages.arg("count", given.size()), Messages.arg("level", given.get(0).level()),
                    Messages.arg("dust", String.format(Locale.ROOT, "%,d", dust)),
                    Messages.arg("coins", String.format(Locale.ROOT, "%,d", coins)), Messages.arg("boxes", boxText.toString()));
            for (Component line : messages.lore(given.size() == 1 ? "levels.claimed-one" : "levels.claimed-many", tags)) {
                player.sendMessage(line);
            }
            if (!boxes.isEmpty()) messages.send(player, "levels.claimed-boxes", tags);
            messages.send(player, "levels.claimed-footer", tags);
            player.playSound(Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 0.7f, 1.3f), Sound.Emitter.self());
            if (!boxes.isEmpty()) player.playSound(Sound.sound(Key.key("block.amethyst_block.resonate"), Sound.Source.MASTER, 0.8f, 1.2f),
                    Sound.Emitter.self());
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) show(player, page);
        }));
    }

    private static void click(Player player) {
        player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, 1.2f), Sound.Emitter.self());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }
}
