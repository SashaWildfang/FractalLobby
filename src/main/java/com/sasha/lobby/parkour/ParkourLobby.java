/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.parkour;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The lobby parkour: a pressure plate that starts ajParkour with a hologram above it (each player's own
 * best and rank), and a separate leaderboard hologram with the best scores. Clicking either hologram
 * opens the full top 10.
 */
public final class ParkourLobby implements Listener {

    /** Roughly how tall one line of hologram text is, in blocks. */
    private static final double LINE_HEIGHT = 0.27;
    /** How far from a hologram's middle a click still counts, and how far away players can click it. */
    private static final double CLICK_RADIUS = 1.3;
    private static final double CLICK_REACH = 7.0;
    private static final long CLICK_COOLDOWN_MILLIS = 400;
    private static final Pattern TIME = Pattern.compile("(\\d+\\s*h\\s*)?(\\d+\\s*m\\s*)?\\d+\\s*s");

    private final Lobby plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Leaderboard leaderboard;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> lastOpened = new HashMap<>();
    private final List<Hologram> holograms;
    private final BukkitTask displayTask;

    public ParkourLobby(Lobby plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "parkour.yml");
        data = YamlConfiguration.loadConfiguration(file);
        leaderboard = new Leaderboard(plugin);
        holograms = List.of(
                new Hologram("hologram", "fractal_lobby_parkour_holo_", this::plateText),
                new Hologram("leaderboard", "fractal_lobby_parkour_board_", this::leaderboardText));
        displayTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateDisplays, 1L, 20L);
    }

    // ---------------------------------------------------------------- setup

    /** Places the start plate at the admin's feet and its hologram one block above it. */
    public void setup(Player admin) {
        Location plate = admin.getLocation().getBlock().getLocation();
        plate.getBlock().setType(Material.POLISHED_BLACKSTONE_PRESSURE_PLATE, false);
        saveLocation("plate", plate);
        saveLocation("hologram", plate.clone().add(0.5, 1.0, 0.5));
        save();
        holograms.getFirst().remove();
        updateDisplays();
        plugin.messages().send(admin, "parkour.setup");
    }

    /** Puts the leaderboard hologram where the admin stands (centred on the block). */
    public void setupLeaderboard(Player admin) {
        Location at = admin.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5);
        saveLocation("leaderboard", at);
        save();
        holograms.get(1).remove();
        updateDisplays();
        plugin.messages().send(admin, "parkour.leaderboard-set");
    }

    /** Removes the leaderboard hologram. */
    public void removeLeaderboard(Player admin) {
        data.set("leaderboard", null);
        save();
        holograms.get(1).remove();
        plugin.messages().send(admin, "parkour.leaderboard-removed");
    }

    public void shutdown() {
        displayTask.cancel();
        holograms.forEach(Hologram::remove);
    }

    private void updateDisplays() {
        for (Hologram hologram : holograms) hologram.update();
    }

    // ---------------------------------------------------------------- hologram text

    /** Above the start plate: the title and the viewer's own best and rank. */
    private List<Component> plateText(Player viewer) {
        Messages messages = plugin.messages();
        return List.of(messages.component("parkour.hologram.title"),
                messages.component("parkour.hologram.you", you(viewer)),
                messages.component("parkour.hologram.hint"));
    }

    /** The leaderboard: the best scores, then the viewer's own best and rank. */
    private List<Component> leaderboardText(Player viewer) {
        Messages messages = plugin.messages();
        List<Component> lines = new ArrayList<>();
        lines.add(messages.component("parkour.leaderboard.title"));
        List<Leaderboard.Entry> top = leaderboard.entries(viewer);
        int shown = Math.min(top.size(), plugin.lobbyConfig().parkour().hologramTop());
        if (shown == 0) lines.add(messages.component("parkour.leaderboard.no-scores"));
        for (int i = 0; i < shown; i++) lines.add(messages.component("parkour.leaderboard.top-line", row(top.get(i))));
        lines.add(Component.empty());
        lines.add(messages.component("parkour.leaderboard.you", you(viewer)));
        lines.add(messages.component("parkour.leaderboard.hint"));
        return lines;
    }

    private static TagResolver row(Leaderboard.Entry entry) {
        return TagResolver.resolver(Messages.arg("position", entry.position()),
                Messages.arg("name", entry.name()), Messages.arg("score", entry.score()));
    }

    /** The score and rank tags for the viewer themself. */
    private TagResolver you(Player viewer) {
        int rank = leaderboard.rank(viewer);
        Component rankText = rank > 0
                ? plugin.messages().component("parkour.rank", Messages.arg("position", rank))
                : plugin.messages().component("parkour.unranked");
        return TagResolver.resolver(Messages.arg("score", leaderboard.best(viewer)), Placeholder.component("rank", rankText));
    }

    // ---------------------------------------------------------------- per-viewer holograms

    /**
     * A hologram where every player sees their own text (their own best and rank), made of one text
     * display per viewer that only that viewer can see, plus an invisible click box.
     */
    private final class Hologram {
        private final String path;
        private final String tagPrefix;
        private final String clickTag;
        private final Function<Player, List<Component>> text;
        private final Map<UUID, TextDisplay> displays = new HashMap<>();
        private Interaction clickBox;
        private Location location;
        private double height = 1.0;

        Hologram(String path, String tagPrefix, Function<Player, List<Component>> text) {
            this.path = path;
            this.tagPrefix = tagPrefix;
            this.clickTag = tagPrefix + "click";
            this.text = text;
        }

        void update() {
            location = readLocation(path);
            if (location == null) {
                remove();
                return;
            }
            World world = location.getWorld();
            // Leftovers from a reload or crash (they aren't saved, but just in case): only ours are kept.
            for (TextDisplay old : world.getEntitiesByClass(TextDisplay.class)) {
                for (String tag : old.getScoreboardTags()) {
                    if (tag.startsWith(tagPrefix) && displays.get(viewerOf(tag)) != old) old.remove();
                }
            }
            displays.entrySet().removeIf(entry -> {
                Player viewer = Bukkit.getPlayer(entry.getKey());
                boolean gone = viewer == null || !viewer.getWorld().equals(world) || !entry.getValue().isValid();
                if (gone) entry.getValue().remove();
                return gone;
            });

            int lines = 1;
            for (Player viewer : world.getPlayers()) {
                TextDisplay display = displays.get(viewer.getUniqueId());
                if (display == null) {
                    display = world.spawn(location, TextDisplay.class, spawned -> {
                        spawned.addScoreboardTag(tagPrefix + viewer.getUniqueId());
                        spawned.setBillboard(TextDisplay.Billboard.CENTER);
                        spawned.setSeeThrough(true);
                        spawned.setShadowed(true);
                        spawned.setDefaultBackground(false);
                        spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
                        spawned.setPersistent(false);
                        spawned.setVisibleByDefault(false); // only its own viewer sees it
                    });
                    viewer.showEntity(plugin, display);
                    displays.put(viewer.getUniqueId(), display);
                }
                List<Component> content = text.apply(viewer);
                lines = Math.max(lines, content.size());
                display.text(Component.join(JoinConfiguration.newlines(), content));
            }
            height = lines * LINE_HEIGHT + 0.2;
            ensureClickBox(world);
        }

        private UUID viewerOf(String tag) {
            try {
                return UUID.fromString(tag.substring(tagPrefix.length()));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        /** Right-clicks with an empty hand only reach the server when they hit an entity: this box. */
        private void ensureClickBox(World world) {
            if (clickBox == null || !clickBox.isValid() || clickBox.getLocation().distanceSquared(location) > 0.01) {
                if (clickBox != null) clickBox.remove();
                for (Interaction old : world.getEntitiesByClass(Interaction.class)) {
                    if (old.getScoreboardTags().contains(clickTag)) old.remove();
                }
                clickBox = world.spawn(location, Interaction.class, box -> {
                    box.setResponsive(true);
                    box.setPersistent(false);
                    box.addScoreboardTag(clickTag);
                });
            }
            if (Math.abs(clickBox.getInteractionHeight() - height) > 0.01) {
                clickBox.setInteractionWidth((float) (CLICK_RADIUS * 2));
                clickBox.setInteractionHeight((float) height);
            }
        }

        /** Whether the player is looking at the hologram, within reach. */
        boolean lookedAtBy(Player player) {
            if (location == null || !player.getWorld().equals(location.getWorld())) return false;
            Location eye = player.getEyeLocation();
            Vector direction = eye.getDirection();
            for (double step = 0; step <= CLICK_REACH; step += 0.1) {
                Vector point = eye.toVector().add(direction.clone().multiply(step));
                double dx = point.getX() - location.getX();
                double dz = point.getZ() - location.getZ();
                double dy = point.getY() - location.getY();
                if (dx * dx + dz * dz <= CLICK_RADIUS * CLICK_RADIUS && dy >= -0.2 && dy <= height + 0.1) return true;
            }
            return false;
        }

        boolean owns(org.bukkit.entity.Entity entity) {
            return entity.getScoreboardTags().contains(clickTag);
        }

        void forget(UUID viewer) {
            TextDisplay display = displays.remove(viewer);
            if (display != null) display.remove();
        }

        void remove() {
            displays.values().forEach(TextDisplay::remove);
            displays.clear();
            if (clickBox != null) clickBox.remove();
            clickBox = null;
        }
    }

    // ---------------------------------------------------------------- clicking

    /** Any click while looking at a hologram (left or right, with or without an item) opens the leaderboard. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) return;
        Player player = event.getPlayer();
        for (Hologram hologram : holograms) {
            if (!hologram.lookedAtBy(player)) continue;
            // A click on a block right in front of the hologram is meant for the block.
            if (event.getClickedBlock() != null
                    && event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5).distanceSquared(player.getEyeLocation())
                    < hologram.location.distanceSquared(player.getEyeLocation()) - 1) return;
            event.setUseItemInHand(Event.Result.DENY); // the item in hand (e.g. the compass) stays shut
            event.setUseInteractedBlock(Event.Result.DENY);
            openLeaderboard(player);
            return;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClickBox(PlayerInteractEntityEvent event) {
        if (holograms.stream().noneMatch(hologram -> hologram.owns(event.getRightClicked()))) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) openLeaderboard(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHitBox(PrePlayerAttackEntityEvent event) {
        if (holograms.stream().noneMatch(hologram -> hologram.owns(event.getAttacked()))) return;
        event.setCancelled(true);
        openLeaderboard(event.getPlayer());
    }

    // ---------------------------------------------------------------- the menu

    /** The menu holder; being the inventory's holder is how its clicks are recognised. */
    private static final class Menu implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** The top 10 as player heads, and the viewer's own best and rank at the bottom. */
    public void openLeaderboard(Player player) {
        long now = System.currentTimeMillis();
        if (now - lastOpened.getOrDefault(player.getUniqueId(), 0L) < CLICK_COOLDOWN_MILLIS) return;
        lastOpened.put(player.getUniqueId(), now);
        Messages messages = plugin.messages();
        if (!leaderboard.available()) {
            messages.send(player, "parkour.unavailable");
            return;
        }
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, 45, messages.component("parkour.menu.title"));
        ItemStack filler = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.setHideTooltip(true));
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);

        List<Leaderboard.Entry> top = leaderboard.entries(player);
        int[] slots = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
        for (int i = 0; i < slots.length; i++) {
            ItemStack item;
            if (i < top.size()) {
                Leaderboard.Entry entry = top.get(i);
                TagResolver args = TagResolver.resolver(row(entry),
                        Messages.arg("time", cleanTime(leaderboard.time(player, entry.position()))));
                boolean isYou = entry.name().equalsIgnoreCase(player.getName());
                item = head(entry.name(), messages.item("parkour.menu.entry-name", args),
                        messages.lore(isYou ? "parkour.menu.entry-lore-you" : "parkour.menu.entry-lore", args));
            } else {
                item = ItemStack.of(Material.SKELETON_SKULL);
                TagResolver args = Messages.arg("position", i + 1);
                item.editMeta(meta -> meta.displayName(messages.item("parkour.menu.empty-name", args)));
            }
            menu.inventory.setItem(slots[i], item);
        }
        menu.inventory.setItem(40, head(player.getName(), messages.item("parkour.menu.you-name"),
                messages.lore("parkour.menu.you-lore", you(player))));
        player.openInventory(menu.inventory);
        player.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.5f, 1.2f), Sound.Emitter.self());
    }

    private static ItemStack head(String name, Component title, List<Component> lore) {
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            meta.setPlayerProfile(Bukkit.createProfile(name)); // the skin is looked up by name
            meta.displayName(title);
            meta.lore(lore);
        });
        // Without this, the game adds a line under the name about the head's skin ("Dynamic").
        head.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.PROFILE).build());
        return head;
    }

    /**
     * ajParkour's time text can come with its chat prefix and colour codes in front (e.g.
     * "[AL!] ALParkour ➜ &r1m 56s"). Only the time itself is kept.
     */
    static String cleanTime(String raw) {
        String text = raw.replaceAll("[&§](#[0-9a-fA-F]{6}|[0-9a-fk-orA-FK-OR])", "").replaceAll("<[^>]*>", "");
        Matcher matcher = TIME.matcher(text.toLowerCase(Locale.ROOT));
        String last = null;
        while (matcher.find()) last = matcher.group();
        if (last != null) return last.trim();
        int arrow = Math.max(text.lastIndexOf('➜'), text.lastIndexOf('»'));
        return (arrow >= 0 ? text.substring(arrow + 1) : text).trim();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }

    // ---------------------------------------------------------------- the start plate

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlate(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Location plate = readLocation("plate");
        if (plate == null || player.isInsideVehicle() || !sameBlock(event.getClickedBlock().getLocation(), plate)) return;
        if (cooldowns.getOrDefault(player.getUniqueId(), 0L) > now) return;
        cooldowns.put(player.getUniqueId(), now + 1000L);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ajp start " + player.getName() + " spawn");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cooldowns.remove(id);
        lastOpened.remove(id);
        for (Hologram hologram : holograms) hologram.forget(id);
    }

    // ---------------------------------------------------------------- parkour.yml

    private void saveLocation(String path, Location location) {
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
    }

    private Location readLocation(String path) {
        String worldName = data.getString(path + ".world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, data.getDouble(path + ".x"),
                data.getDouble(path + ".y"), data.getDouble(path + ".z"));
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save parkour setup: " + exception.getMessage());
        }
    }

    private static boolean sameBlock(Location first, Location second) {
        return first.getWorld().equals(second.getWorld()) && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY() && first.getBlockZ() == second.getBlockZ();
    }
}
