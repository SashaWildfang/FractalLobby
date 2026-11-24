/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.network.LevelRewards;
import com.sasha.lobby.network.NetworkLevels;
import io.papermc.paper.datacomponent.DataComponentTypes;
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

/**
 * My Profile → Levels: the network level trail. Ten levels a page wind across the menu like a path (five left to
 * right, a bend, five back), joined by panes that light up as they're reached. Each level shows its reward: ready to
 * claim (glowing), claimed (with the box's stars) or still ahead (how much XP to go); every fifth one has a Mystery
 * Box. Above: the coin multiplier, the player's level and Claim All; below: the next Mystery Box, pages and back.
 */
public final class LevelingMenu implements Listener {

    private static final char BACKGROUND = '';
    /** The ten levels of a page, in order along the trail, and the panes between them. */
    static final int[] NODES = {9, 11, 13, 15, 17, 35, 33, 31, 29, 27};
    static final int[][] LINKS = {{10}, {12}, {14}, {16}, {26}, {34}, {32}, {30}, {28}};
    static final int MULTIPLIER = 1, HEAD = 4, CLAIM_ALL = 7, NEXT_BOX = 40, PREVIOUS = 45, BACK = 49, NEXT = 53;

    private final Lobby plugin;

    public LevelingMenu(Lobby plugin) {
        this.plugin = plugin;
    }

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        private int page;
        private final Map<Integer, Integer> levels = new HashMap<>();

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
        for (int i = 0; i < NODES.length; i++) {
            int l = page * 10 + i + 1;
            menu.inventory.setItem(NODES[i], node(player, l, level, progress, claimed));
            menu.levels.put(NODES[i], l);
            if (i < LINKS.length) {
                boolean lit = l + 1 <= level;
                for (int slot : LINKS[i]) menu.inventory.setItem(slot, link(lit));
            }
        }
        menu.inventory.setItem(HEAD, head(player, progress));
        menu.inventory.setItem(MULTIPLIER, multiplier(level));
        menu.inventory.setItem(CLAIM_ALL, claimAll(rewards.waiting(player, level).size()));
        menu.inventory.setItem(NEXT_BOX, nextBox(level, progress));
        if (page > 0) menu.inventory.setItem(PREVIOUS, style.icon(Profile.button(messages, Material.ARROW, "levels.previous",
                Messages.arg("page", page)), "back"));
        if (page < pages(level) - 1) menu.inventory.setItem(NEXT, style.icon(Profile.button(messages, Material.ARROW, "levels.next",
                Messages.arg("page", page + 2)), "next"));
        menu.inventory.setItem(BACK, style.icon(Profile.button(messages, Material.ARROW, "profile.back"), "back"));
        player.openInventory(menu.inventory);
    }

    private TagResolver rewardTags(LevelRewards.Reward reward) {
        return TagResolver.resolver(Messages.arg("level", reward.level()), Messages.arg("dust", String.format(Locale.ROOT, "%,d", reward.dust())),
                Messages.arg("coins", String.format(Locale.ROOT, "%,d", reward.coins())));
    }

    /** One level on the trail. */
    private ItemStack node(Player player, int l, int level, NetworkLevels.Progress progress, Map<Integer, Integer> claimed) {
        Messages messages = plugin.messages();
        MenuStyle style = plugin.menuStyle();
        LevelRewards.Reward reward = plugin.levelRewards().reward(l);
        List<Component> lore = new ArrayList<>();
        TagResolver tags = rewardTags(reward);
        String state;
        ItemStack item;
        if (l == 1) {
            state = "start";
            item = style.icon(ItemStack.of(Material.LIME_DYE), "confirm");
        } else if (claimed.containsKey(l)) {
            state = "claimed";
            item = style.icon(ItemStack.of(Material.LIME_DYE), "confirm");
        } else if (l <= level) {
            state = "ready";
            item = ItemStack.of(reward.box() ? Material.ENDER_CHEST : Material.EXPERIENCE_BOTTLE);
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else {
            state = "locked";
            item = reward.box() ? ItemStack.of(Material.ENDER_CHEST) : style.locked(ItemStack.of(Material.GRAY_DYE));
        }
        long toGo = 0;
        if (state.equals("locked")) {
            toGo = progress.needed() - progress.into();
            for (int x = level + 1; x < l; x++) toGo += NetworkLevels.needed(x, first(), extra());
        }
        TagResolver all = TagResolver.resolver(tags, Messages.arg("xp", String.format(Locale.ROOT, "%,d", toGo)),
                Messages.arg("stars", claimed.getOrDefault(l, 0)));
        if (!state.equals("start")) {
            lore.addAll(messages.lore("levels.rewards", all));
            if (reward.box()) {
                int got = claimed.getOrDefault(l, 0);
                lore.addAll(messages.lore(got > 0 ? "levels.reward-box-got" : "levels.reward-box", all));
            }
        }
        lore.addAll(messages.lore("levels.node." + state + ".lore", all));
        Component name = messages.item("levels.node." + state + ".name", all);
        item.editMeta(meta -> {
            meta.displayName(name);
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 99);
        item.setAmount(Math.max(1, Math.min(99, l)));
        return item;
    }

    private long first() {
        return Math.max(1, plugin.getConfig().getLong("leveling.first-level-xp", 200));
    }

    private long extra() {
        return Math.max(0, plugin.getConfig().getLong("leveling.extra-xp-per-level", 50));
    }

    private static ItemStack link(boolean lit) {
        ItemStack pane = ItemStack.of(lit ? Material.PURPLE_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
        pane.editMeta(meta -> meta.setHideTooltip(true));
        return pane;
    }

    private ItemStack head(Player player, NetworkLevels.Progress progress) {
        int width = 20, filled = (int) Math.round(progress.fraction() * width);
        Locale l = Locale.ROOT;
        TagResolver tags = TagResolver.resolver(Messages.arg("level", progress.level()), Messages.arg("next", progress.level() + 1),
                Placeholder.parsed("bar", "<#B266FF>" + "■".repeat(filled) + "<dark_gray>" + "■".repeat(width - filled)),
                Messages.arg("percent", (int) (progress.fraction() * 100)),
                Messages.arg("xp", String.format(l, "%,d", progress.into())), Messages.arg("needed", String.format(l, "%,d", progress.needed())),
                Messages.arg("total", String.format(l, "%,d", progress.total())),
                Messages.arg("multiplier", com.sasha.lobby.network.NetworkBoosters.multiplier(plugin.levelRewards().multiplier(progress.level()))));
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
    private ItemStack multiplier(int level) {
        Messages messages = plugin.messages();
        LevelRewards rewards = plugin.levelRewards();
        List<Component> lore = new ArrayList<>(messages.lore("levels.multiplier.lore",
                Messages.arg("multiplier", com.sasha.lobby.network.NetworkBoosters.multiplier(rewards.multiplier(level)))));
        boolean nextShown = false;
        for (var step : rewards.multipliers().entrySet()) {
            String key = level >= step.getKey() ? "levels.multiplier.step-reached" : !nextShown ? "levels.multiplier.step-next" : "levels.multiplier.step";
            if (level < step.getKey()) nextShown = true;
            lore.add(messages.render(messages.raw(key), Messages.arg("level", step.getKey()),
                    Messages.arg("multiplier", com.sasha.lobby.network.NetworkBoosters.multiplier(step.getValue()))));
        }
        lore.addAll(messages.lore("levels.multiplier.footer"));
        ItemStack item = ItemStack.of(Material.GOLD_INGOT);
        item.editMeta(meta -> {
            meta.displayName(messages.item("levels.multiplier.name",
                    Messages.arg("multiplier", com.sasha.lobby.network.NetworkBoosters.multiplier(rewards.multiplier(level)))));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    private ItemStack claimAll(int count) {
        ItemStack item = Profile.button(plugin.messages(), count > 0 ? Material.CHEST : Material.BARREL,
                count > 0 ? "levels.claim-all" : "levels.claim-none", Messages.arg("count", count));
        if (count > 0) item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return item;
    }

    /** The next level with a Mystery Box, and how far off it is. */
    private ItemStack nextBox(int level, NetworkLevels.Progress progress) {
        int every = Math.max(0, plugin.getConfig().getInt("level-rewards.mystery-box-every", 5));
        if (every == 0) return null;
        int target = (level / every + 1) * every;
        long toGo = progress.needed() - progress.into();
        for (int x = level + 1; x < target; x++) toGo += NetworkLevels.needed(x, first(), extra());
        return Profile.button(plugin.messages(), Material.ENDER_EYE, "levels.next-box", Messages.arg("level", target),
                Messages.arg("levels", target - level), Messages.arg("xp", String.format(Locale.ROOT, "%,d", toGo)));
    }

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
                if (l == null || l < 2) return;
                if (plugin.levelRewards().claimed(player).containsKey(l)) return;
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
        if (levels.isEmpty()) return;
        var cosmetics = Lobby.cosmeticsHook();
        if (!busy.add(player.getUniqueId())) return;                  // one claim at a time
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
            Map<Integer, Integer> boxes = new java.util.TreeMap<>();
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
                boxText.append(box.getValue()).append("× ").append(box.getKey()).append("★");
            }
            TagResolver tags = TagResolver.resolver(Messages.arg("count", given.size()), Messages.arg("dust", String.format(Locale.ROOT, "%,d", dust)),
                    Messages.arg("coins", String.format(Locale.ROOT, "%,d", coins)), Messages.arg("boxes", boxText.toString()));
            messages.send(player, given.size() == 1 ? "levels.claimed-one" : "levels.claimed-many",
                    TagResolver.resolver(tags, Messages.arg("level", given.get(0).level())));
            if (!boxes.isEmpty()) messages.send(player, "levels.claimed-boxes", tags);
            player.playSound(Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 0.7f, 1.3f), Sound.Emitter.self());
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
