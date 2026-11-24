/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.network.NetworkBoosters;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * My Profile → Boosters: the boosters a player owns (bought in the store) and hasn't used. Clicking one asks to
 * confirm, then it runs for everyone on its game, after any boosters already running or waiting there.
 */
public final class BoostersMenu implements Listener {

    /** The background (resource pack): a "BOOSTERS" title, three rows of seven wells and two at the bottom. */
    private static final char BACKGROUND = '';
    private static final int BACK = 39, INFO = 41, EMPTY = 22;
    private static final int[] SLOTS = new int[21];

    static {
        for (int i = 0; i < 21; i++) SLOTS[i] = (1 + i / 7) * 9 + 1 + i % 7;
    }

    private final Lobby plugin;

    public BoostersMenu(Lobby plugin) {
        this.plugin = plugin;
    }

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, NetworkBoosters.Item> items = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Reads the player's boosters (off the main thread), then opens the menu. */
    public void open(Player player) {
        NetworkBoosters boosters = plugin.boosters();
        if (boosters == null) {
            plugin.messages().send(player, "boosters.unavailable");
            return;
        }
        boosters.items(player).whenComplete((list, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (error != null) {
                plugin.messages().send(player, "boosters.unavailable");
                return;
            }
            show(player, list);
        }));
    }

    private void show(Player player, List<NetworkBoosters.Item> list) {
        Messages messages = plugin.messages();
        MenuStyle style = plugin.menuStyle();
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, 45, style.title(player, BACKGROUND, messages.component("boosters.title")));
        ItemStack filler = style.filler(player);
        for (int i = 0; i < 45; i++) menu.inventory.setItem(i, filler);

        for (int i = 0; i < list.size() && i < SLOTS.length; i++) {
            NetworkBoosters.Item item = list.get(i);
            menu.inventory.setItem(SLOTS[i], icon(item, "boosters.item"));
            menu.items.put(SLOTS[i], item);
        }
        if (list.isEmpty()) menu.inventory.setItem(EMPTY, Profile.button(messages, Material.GLASS_BOTTLE, "boosters.empty"));
        menu.inventory.setItem(BACK, style.icon(Profile.button(messages, Material.ARROW, "profile.back"), "back"));
        menu.inventory.setItem(INFO, info());
        player.openInventory(menu.inventory);
    }

    /** What a booster is shown as: the game's icon (CTF's flags), named and described from messages.yml KEY. */
    private ItemStack icon(NetworkBoosters.Item item, String key) {
        ItemStack stack = ItemStack.of(Material.PAPER);
        stack.editMeta(meta -> {
            meta.displayName(plugin.messages().item(key + ".name", tags(item)));
            meta.lore(plugin.messages().lore(key + ".lore", tags(item)));
            meta.addItemFlags(ItemFlag.values());
            String model = plugin.messages().raw("boosters.icon." + item.game());
            if (!model.isBlank() && !model.startsWith("boosters.")) meta.setItemModel(NamespacedKey.fromString(model));
        });
        return stack;
    }

    private TagResolver tags(NetworkBoosters.Item item) {
        NetworkBoosters boosters = plugin.boosters();
        int waiting = boosters.waiting(item.game()) + (boosters.active(item.game()) != null ? 1 : 0);
        return TagResolver.resolver(Messages.arg("multiplier", NetworkBoosters.multiplier(item.multiplier())),
                Messages.arg("minutes", item.minutes()),
                Placeholder.component("game", game(item.game())),
                Messages.arg("ahead", waiting));
    }

    /** The game's name from messages.yml (boosters.games.GAME), or its id. */
    public Component game(String id) {
        String text = plugin.messages().raw("boosters.games." + id);
        return plugin.messages().render(text.startsWith("boosters.") ? id.toUpperCase(java.util.Locale.ROOT) : text);
    }

    /** The book at the bottom: what's running on each game with boosters. */
    private ItemStack info() {
        Messages messages = plugin.messages();
        NetworkBoosters boosters = plugin.boosters();
        ItemStack book = ItemStack.of(Material.BOOK);
        List<Component> lore = new java.util.ArrayList<>(messages.lore("boosters.info.lore"));
        String game = "ctf";
        NetworkBoosters.Running now = boosters.active(game);
        TagResolver tags = TagResolver.resolver(Placeholder.component("game", game(game)),
                Messages.arg("waiting", boosters.waiting(game)),
                Messages.arg("multiplier", now == null ? "1.0" : NetworkBoosters.multiplier(now.multiplier())),
                Messages.name("player", now == null ? "" : now.owner()),
                Messages.arg("minutes", now == null ? 0 : now.minutesLeft(System.currentTimeMillis())));
        lore.addAll(messages.lore(now == null ? "boosters.info.idle" : "boosters.info.running", tags));
        book.editMeta(meta -> {
            meta.displayName(messages.item("boosters.info.name"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        return book;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClick() != ClickType.LEFT) return;
        int slot = event.getRawSlot();
        if (slot == BACK) {
            click(player);
            plugin.profile().open(player);
            return;
        }
        NetworkBoosters.Item item = menu.items.get(slot);
        if (item == null) return;
        click(player);
        plugin.confirm().open(player, icon(item, "boosters.confirm"), () -> activate(player, item), () -> open(player));
    }

    private void activate(Player player, NetworkBoosters.Item item) {
        player.closeInventory();
        plugin.boosters().activate(player, item).whenComplete((start, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            Messages messages = plugin.messages();
            if (error != null) {
                messages.send(player, "boosters.unavailable");
                return;
            }
            if (start == null) {
                messages.send(player, "boosters.already-used");
                return;
            }
            TagResolver tags = TagResolver.resolver(tags(item), Messages.name("player", player.getName()),
                    Messages.arg("wait", Math.max(1, (start - System.currentTimeMillis() + 59_999) / 60_000)));
            boolean now = start <= System.currentTimeMillis() + 2000;
            messages.send(player, now ? "boosters.activated-now" : "boosters.activated-queued", tags);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other != player) messages.send(other, "boosters.announce", tags);
            }
            player.playSound(Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 0.7f, 1.2f), Sound.Emitter.self());
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
