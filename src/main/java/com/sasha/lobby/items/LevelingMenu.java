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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * My Profile → Levels (/levels): the network level snake, like Hypixel's skill menus.
 *
 * <p>The snake winds through rows 1-4: up a column, across the top, down the next column, across the bottom, and on.
 * Every other square on it is a level; between two levels is one link, a green striped bar (animated by the resource
 * pack) that fills along the way it flows as the player earns XP towards the next level. It goes on past the menu's
 * edge: the arrows in the bottom corners scroll it four columns at a time, and Re-center brings back the player's
 * level.
 *
 * <p>Below the snake: coin multiplier, the player's level, claim all; then scroll, back, re-center, scroll. Java with the resource pack gets the pack's badges and bars; Bedrock gets plain items.
 */
public final class LevelingMenu implements Listener {

    private static final char BACKGROUND = '\uF1C6';
    static final int MULTIPLIER = 38, HEAD = 40, CLAIM_ALL = 42, LEFT = 45, BACK = 48, RECENTER = 50, RIGHT = 53;
    /** Columns the arrows move the snake. */
    static final int STEP = 4;

    /** Where square {@code i} of the snake is: its column (from the start, 0 up) and row (0 top to 3 bottom). */
    static int[] cell(int i) {
        int cycle = i / 10, r = i % 10, base = cycle * 4;
        if (r <= 3) return new int[] {base, 3 - r};                   // up the first column
        if (r == 4) return new int[] {base + 1, 0};                   // across the top
        if (r <= 8) return new int[] {base + 2, r - 5};               // down the next
        return new int[] {base + 3, 3};                               // across the bottom
    }

    /** The shape of the link at square {@code i} (always odd): the way the bar runs through it. */
    static String linkShape(int i) {
        return switch (i % 10) {
            case 1 -> "sn";                                           // up
            case 3 -> "se";                                           // up, then right
            case 5 -> "ws";                                           // right, then down
            case 7 -> "ns";                                           // down
            default -> "we";                                          // 9: right along the bottom
        };
    }

    /** The column a level sits in. */
    static int column(int level) {
        return cell(2 * (level - 1))[0];
    }

    private final Lobby plugin;

    public LevelingMenu(Lobby plugin) {
        this.plugin = plugin;
    }

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        /** The first column shown. */
        private int offset;
        private final Map<Integer, Integer> levels = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Opens with the player's level in view (after reading what they've claimed). */
    public void open(Player player) {
        open(player, -1);
    }

    private void open(Player player, int offset) {
        LevelRewards rewards = plugin.levelRewards();
        if (rewards == null || plugin.levels() == null) {
            plugin.messages().send(player, "boosters.unavailable");
            return;
        }
        rewards.load(player).whenComplete((claimed, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            show(player, offset < 0 ? centred(plugin.levels().progress(player).level()) : offset);
        }));
    }

    /** The first column that puts this level a little left of the middle. */
    private static int centred(int level) {
        return Math.max(0, column(level) - 3);
    }

    /** The furthest the snake scrolls: a little past the last level shown. */
    private int maxOffset(int level) {
        int last = Math.max(plugin.levelRewards().levelsShown(), level + 20);
        return Math.max(0, column(last) - 8);
    }

    private boolean custom(Player player) {
        return plugin.menuStyle().filler(player) == null;              // the resource pack's backgrounds are on
    }

    private void show(Player player, int offset) {
        Messages messages = plugin.messages();
        MenuStyle style = plugin.menuStyle();
        LevelRewards rewards = plugin.levelRewards();
        NetworkLevels.Progress progress = plugin.levels().progress(player);
        int level = progress.level();
        offset = Math.max(0, Math.min(offset, maxOffset(level)));

        Menu menu = new Menu();
        menu.offset = offset;
        int firstLevel = Math.max(1, firstLevelFrom(offset)), lastLevel = lastLevelTo(offset + 8);
        menu.inventory = Bukkit.createInventory(menu, 54, style.title(player, BACKGROUND,
                messages.component("levels.title", Messages.arg("from", firstLevel), Messages.arg("to", lastLevel))));
        ItemStack filler = style.filler(player);
        for (int i = 0; i < 54; i++) menu.inventory.setItem(i, filler);

        Map<Integer, Integer> claimed = rewards.claimed(player);
        int cap = Math.max(rewards.levelsShown(), level + 20);
        for (int i = 0; ; i++) {
            int[] at = cell(i);
            if (at[0] > offset + 8) break;
            if (at[0] < offset) continue;
            int slot = at[1] * 9 + (at[0] - offset);
            int l = i / 2 + 1;
            if (l > cap) break;
            if (i % 2 == 0) {
                menu.inventory.setItem(slot, node(player, l, level, progress, claimed));
                menu.levels.put(slot, l);
            } else {
                // the link from level l to l + 1: full once past l, filling while at l
                double fill = level > l ? 1 : level == l ? progress.fraction() : 0;
                menu.inventory.setItem(slot, link(player, linkShape(i), fill, level == l, progress));
            }
        }
        menu.inventory.setItem(MULTIPLIER, multiplier(player, level));
        menu.inventory.setItem(HEAD, head(player, progress));
        menu.inventory.setItem(BACK, style.icon(Profile.button(messages, Material.ARROW, "profile.back"), "back"));
        menu.inventory.setItem(RECENTER, recenter(player, level));
        menu.inventory.setItem(CLAIM_ALL, claimAll(player, rewards.waiting(player, level).size()));
        // the arrows stay put; at either end they're greyed out
        boolean back = offset > 0, on = offset < maxOffset(level);
        menu.inventory.setItem(LEFT, packIcon(player, back
                ? Profile.button(messages, Material.ARROW, "levels.scroll-left", Messages.arg("from", Math.max(1, firstLevelFrom(Math.max(0, offset - STEP)))))
                : Profile.button(messages, Material.GRAY_DYE, "levels.scroll-left-off"), back ? "level/scroll_left" : "level/scroll_left_off"));
        menu.inventory.setItem(RIGHT, packIcon(player, on
                ? Profile.button(messages, Material.ARROW, "levels.scroll-right", Messages.arg("to", lastLevelTo(offset + STEP + 8)))
                : Profile.button(messages, Material.GRAY_DYE, "levels.scroll-right-off"), on ? "level/scroll_right" : "level/scroll_right_off"));
        player.openInventory(menu.inventory);
    }

    /** The first level at or after this column. */
    private static int firstLevelFrom(int column) {
        for (int i = 0; ; i += 2) if (cell(i)[0] >= column) return i / 2 + 1;
    }

    /** The last level at or before this column. */
    private static int lastLevelTo(int column) {
        int last = 1;
        for (int i = 0; cell(i)[0] <= column; i += 2) last = i / 2 + 1;
        return last;
    }

    // ---------------------------------------------------------------- the snake

    /** A link: Java gets the pack's striped bar (filled in quarters the way it flows); Bedrock a pane. */
    private ItemStack link(Player player, String shape, double fill, boolean current, NetworkLevels.Progress progress) {
        int quarters = fill <= 0 ? 0 : fill >= 1 ? 4 : Math.max(1, Math.min(3, (int) Math.round(fill * 4)));
        ItemStack item;
        if (custom(player)) {
            item = ItemStack.of(Material.PAPER);
            item.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", "level/link_" + shape + "_" + quarters)));
        } else {
            item = ItemStack.of(quarters >= 4 ? Material.LIME_STAINED_GLASS_PANE : quarters > 0 ? Material.YELLOW_STAINED_GLASS_PANE
                    : Material.GRAY_STAINED_GLASS_PANE);
        }
        if (current) {
            // the link to the next level says how far along it is
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

    /** With the resource pack: the pack's icon fractal:PATH on the item. */
    private ItemStack packIcon(Player player, ItemStack item, String path) {
        if (custom(player)) item.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", path)));
        return item;
    }

    private ItemStack recenter(Player player, int level) {
        ItemStack item = Profile.button(plugin.messages(), Material.COMPASS, "levels.recenter", Messages.arg("level", level));
        if (custom(player)) item.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", "level/recenter")));
        return item;
    }

    // ---------------------------------------------------------------- levels

    private TagResolver progressTags(NetworkLevels.Progress p) {
        int width = 20, filled = (int) Math.round(p.fraction() * width);
        Locale l = Locale.ROOT;
        return TagResolver.resolver(Messages.arg("level", p.level()), Messages.arg("next", p.level() + 1),
                Placeholder.parsed("bar", "<#6CE36C>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(width - filled)),
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
        String look = state.equals("start") ? "claimed" : state;
        Material base = switch (state) {
            case "start", "claimed" -> Material.LIME_DYE;
            case "ready" -> reward.box() ? Material.CHEST : Material.EXPERIENCE_BOTTLE;
            default -> reward.box() ? Material.CHEST : Material.GRAY_DYE;
        };
        String model = "level/" + (reward.box() ? "box_" : "node_") + look;
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
        if (state.equals("ready") && !java) item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
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
            if (java) meta.setItemModel(new NamespacedKey("fractal", "level/coin"));
        });
        return item;
    }

    /** Claim All: a lit round button when there's something to claim, a grey one when there isn't. */
    private ItemStack claimAll(Player player, int count) {
        Messages messages = plugin.messages();
        ItemStack item = Profile.button(messages, count > 0 ? Material.LIME_DYE : Material.GRAY_DYE,
                count > 0 ? "levels.claim-all" : "levels.claim-none", Messages.arg("count", count));
        if (custom(player)) item.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", count > 0 ? "level/claim_on" : "level/claim_off")));
        else if (count > 0) item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
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
        if (custom(player)) item.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", "level/box_ready")));
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
            case LEFT -> {
                if (menu.offset > 0) {
                    scroll(player);
                    show(player, menu.offset - STEP);
                }
            }
            case RIGHT -> {
                if (menu.offset < maxOffset(level)) {
                    scroll(player);
                    show(player, menu.offset + STEP);
                }
            }
            case RECENTER -> {
                click(player);
                show(player, centred(level));
            }
            case CLAIM_ALL -> claim(player, plugin.levelRewards().waiting(player, level), menu.offset);
            default -> {
                Integer l = menu.levels.get(slot);
                if (l == null || l < 2 || plugin.levelRewards().claimed(player).containsKey(l)) return;
                if (l > level) {
                    player.playSound(Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 0.5f, 1f), Sound.Emitter.self());
                    return;
                }
                claim(player, List.of(l), menu.offset);
            }
        }
    }

    private static void scroll(Player player) {
        player.playSound(Sound.sound(Key.key("item.book.page_turn"), Sound.Source.MASTER, 0.7f, 1f), Sound.Emitter.self());
    }

    private final java.util.Set<java.util.UUID> busy = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void claim(Player player, List<Integer> levels, int offset) {
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
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) show(player, offset);
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
