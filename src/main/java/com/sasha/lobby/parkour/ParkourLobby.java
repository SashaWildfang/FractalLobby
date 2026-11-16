/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.parkour;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LegacyCodes;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.hook.LuckPermsHook;
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

/**
 * The lobby parkour: the Parkour NPC (action "parkour", or /parkour) starts an infinite parkour run in the
 * parkour area ({@link ParkourGame}), and a leaderboard hologram shows the best scores. Clicking the hologram opens
 * the full top 10.
 */
public final class ParkourLobby implements Listener {

    /** Roughly how tall one line of hologram text is, in blocks. */
    private static final double LINE_HEIGHT = 0.27;
    /** How far from a hologram's middle a click still counts, and how far away players can click it. */
    private static final double CLICK_RADIUS = 1.3;
    private static final double CLICK_REACH = 7.0;
    private static final long CLICK_COOLDOWN_MILLIS = 400;

    private final Lobby plugin;
    private final File file;
    private final YamlConfiguration data;
    private final ScoreStore scores;
    private final Leaderboard leaderboard;
    private final ParkourGame game;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> lastOpened = new HashMap<>();
    private final List<Hologram> holograms;
    /** The network playtime leaderboard (from Core on the proxy); kept apart, as clicking it opens nothing. */
    private final Hologram playtime;
    private int playtimeAsked;
    private final BukkitTask displayTask;

    public ParkourLobby(Lobby plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "parkour.yml");
        // A fresh lobby: the built-in parkour area above spawn (unless ajParkour has one to bring over).
        if (!file.exists() && !new File(plugin.getDataFolder().getParentFile(), "ajParkour/positions.yml").isFile()) {
            try (var in = plugin.getResource("defaults/parkour.yml")) {
                if (in != null) {
                    file.getParentFile().mkdirs();
                    java.nio.file.Files.copy(in, file.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Couldn't write parkour.yml: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
        scores = new ScoreStore(plugin);
        leaderboard = new Leaderboard(plugin, scores);
        if (!data.contains("area")) importAjParkourArea();
        game = new ParkourGame(plugin, this);
        holograms = List.of(new Hologram("leaderboard", "fractal_lobby_parkour_board_", this::leaderboardText));
        // The start plate is gone: the Parkour NPC starts runs now. Done once the world and NPCs are loaded.
        plugin.getServer().getScheduler().runTask(plugin, this::replacePlate);
        playtime = new Hologram("playtime", "fractal_lobby_playtime_board_", this::playtimeText);
        displayTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateDisplays, 1L, 20L);
    }

    // ---------------------------------------------------------------- setup

    /** Puts the Parkour NPC where the admin stands (making it if it's gone). */
    public void setup(Player admin) {
        Location at = admin.getLocation();
        Location spot = new Location(at.getWorld(), Math.round(at.getX() * 2) / 2.0, at.getBlockY(), Math.round(at.getZ() * 2) / 2.0,
                Math.round(at.getYaw() / 15) * 15f, 0f);
        placeNpc(spot);
        plugin.messages().send(admin, "parkour.setup");
    }

    /** The Parkour NPC: moved to {@code at}, or made there (from the built-in npcs.yml) if there isn't one. */
    private void placeNpc(Location at) {
        var npcs = plugin.npcs();
        var npc = npcs.get(NPC_NAME);
        if (npc != null) {
            npcs.move(npc, at);
            return;
        }
        npc = npcs.create(NPC_NAME, at);
        var builtIn = com.sasha.lobby.npc.Npc.builtIn(plugin, NPC_NAME);
        if (builtIn != null) npc.copyLook(builtIn);
        else npc.actions.put("RIGHT_CLICK", new ArrayList<>(List.of("parkour")));
        npcs.changed(npc);
        npcs.save();
    }

    static final String NPC_NAME = "Parkour";

    /** The Parkour NPC starts runs on a right click only (it used to be any click), and its label says so. */
    private void rightClickOnly() {
        var npc = plugin.npcs().get(NPC_NAME);
        if (npc == null) return;
        boolean changed = false;
        List<String> any = npc.actions.get("ANY_CLICK");
        if (any != null && any.remove("parkour")) {
            if (any.isEmpty()) npc.actions.remove("ANY_CLICK");
            npc.actions.computeIfAbsent("RIGHT_CLICK", k -> new ArrayList<>()).add("parkour");
            changed = true;
        }
        // Its label showed the left-click mouse: now the right-click one.
        for (var line : npc.lines) {
            if (line.content.contains("\uF010") && line.content.contains("ᴄʟɪᴄᴋ ᴛᴏ ꜱᴛᴀʀᴛ") && !line.content.contains("ʀɪɢʜᴛ")) {
                line.content = line.content.replace("\uF010", "\uF011").replace("ᴄʟɪᴄᴋ ᴛᴏ ꜱᴛᴀʀᴛ", "ʀɪɢʜᴛ ᴄʟɪᴄᴋ ᴛᴏ ꜱᴛᴀʀᴛ");
                changed = true;
            }
        }
        if (!changed) return;
        plugin.npcs().changed(npc);
        plugin.npcs().save();
    }

    /** Takes away an old start plate (and its hologram), with the Parkour NPC standing there instead. */
    private void replacePlate() {
        rightClickOnly();
        Location plate = readLocation("plate");
        if (plate == null && !data.contains("hologram")) return;
        if (plate != null) {
            if (plate.getBlock().getType().name().endsWith("PRESSURE_PLATE")) plate.getBlock().setType(Material.AIR, false);
            if (plugin.npcs().get(NPC_NAME) == null) placeNpc(plate.clone().add(0.5, 0, 0.5));
        }
        data.set("plate", null);
        data.set("hologram", null);
        save();
        plugin.getLogger().info("The parkour start plate is now the Parkour NPC (/lobbyparkour setup moves it).");
    }

    /** Puts the leaderboard hologram where the admin stands (centred on the block). */
    public void setupLeaderboard(Player admin) {
        Location at = admin.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5);
        saveLocation("leaderboard", at);
        save();
        holograms.getFirst().remove();
        updateDisplays();
        plugin.messages().send(admin, "parkour.leaderboard-set");
    }

    /** Removes the leaderboard hologram. */
    public void removeLeaderboard(Player admin) {
        data.set("leaderboard", null);
        save();
        holograms.getFirst().remove();
        plugin.messages().send(admin, "parkour.leaderboard-removed");
    }

    /**
     * Moves a hologram ("leaderboard" or "playtime") to exactly where the admin stands.
     */
    public void moveHologram(Player admin, String which) {
        Location at = admin.getLocation();
        Location rounded = new Location(at.getWorld(), Math.round(at.getX() * 10) / 10.0, Math.round(at.getY() * 10) / 10.0,
                Math.round(at.getZ() * 10) / 10.0);
        saveLocation(which, rounded);
        save();
        hologram(which).remove();
        updateDisplays();
        plugin.messages().send(admin, which.equals("playtime") ? "playtime.moved" : "parkour.leaderboard-moved");
    }

    /** Takes a hologram away ("leaderboard" or "playtime"). */
    public void removeHologram(Player admin, String which) {
        data.set(which, null);
        save();
        hologram(which).remove();
        plugin.messages().send(admin, which.equals("playtime") ? "playtime.removed" : "parkour.leaderboard-removed");
    }

    private Hologram hologram(String which) {
        return which.equals("playtime") ? playtime : holograms.getFirst();
    }

    /** Puts the playtime leaderboard hologram where the admin stands (centred on the block). */
    public void setupPlaytime(Player admin) {
        saveLocation("playtime", admin.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5));
        save();
        playtime.remove();
        playtimeAsked = 0;
        updateDisplays();
        plugin.messages().send(admin, "playtime.placed");
    }

    /** The playtime leaderboard: the longest network playtimes, then the viewer's own. */
    private List<Component> playtimeText(Player viewer) {
        Messages messages = plugin.messages();
        List<Component> lines = new ArrayList<>();
        lines.add(messages.component("playtime.hologram.title"));
        var top = plugin.coreLink().topPlaytime();
        if (top.isEmpty()) lines.add(messages.component("playtime.hologram.empty"));
        int position = 1;
        for (var entry : top) {
            String rank = plugin.luckPerms()
                    ? LegacyCodes.toMiniMessage(LuckPermsHook.prefix(entry.name())).replace("<reset>", "<reset><!italic>") : "";
            lines.add(messages.component("playtime.hologram.top-line", Messages.arg("position", position++),
                    Messages.name("name", entry.name()), Placeholder.parsed("name-rank", rank),
                    Messages.arg("time", com.sasha.lobby.proxy.CoreLink.duration(entry.millis()))));
        }
        var state = plugin.coreLink().state(viewer);
        if (state != null && state.playtimeNow() >= 0) {
            lines.add(Component.empty());
            lines.add(messages.component("playtime.hologram.you",
                    Messages.arg("time", com.sasha.lobby.proxy.CoreLink.duration(state.playtimeNow()))));
        }
        return lines;
    }

    public Leaderboard leaderboard() {
        return leaderboard;
    }

    public ScoreStore scores() {
        return scores;
    }

    public ParkourGame game() {
        return game;
    }

    // ---------------------------------------------------------------- the area

    /** The box the course is built in, or null until both corners are set. */
    ParkourGame.Area area() {
        Location a = readLocation("area.pos1"), b = readLocation("area.pos2");
        return a == null || b == null || !a.getWorld().equals(b.getWorld()) ? null : ParkourGame.Area.of(a, b);
    }

    /** Where players go after a run (null: the lobby spawn). */
    Location returnPoint() {
        Location at = readLocation("return");
        if (at != null) {
            at.setYaw((float) data.getDouble("return.yaw"));
            at.setPitch((float) data.getDouble("return.pitch"));
        }
        return at;
    }

    /** /lobbyparkour area pos1|pos2|return: sets a corner of the area, or where players go after a run. */
    public void setArea(Player admin, String which) {
        Location at = admin.getLocation();
        if (which.equals("return")) {
            saveLocation("return", at);
            data.set("return.yaw", Math.round(at.getYaw() * 10) / 10.0);
            data.set("return.pitch", Math.round(at.getPitch() * 10) / 10.0);
        } else {
            saveLocation("area." + which, at.getBlock().getLocation());
        }
        save();
        ParkourGame.Area area = area();
        plugin.messages().send(admin, "parkour.area-set", Messages.arg("which", which),
                Messages.arg("size", area == null ? "-" : (area.maxX() - area.minX() + 1) + "×" + (area.maxY() - area.minY() + 1)
                        + "×" + (area.maxZ() - area.minZ() + 1)));
    }

    /**
     * The first time, takes the area from ajParkour's positions.yml (the area called "spawn", or else the first one):
     * its two corners and its fall position (where players go after a run).
     */
    private void importAjParkourArea() {
        File positions = new File(plugin.getDataFolder().getParentFile(), "ajParkour/positions.yml");
        if (!positions.isFile()) return;
        YamlConfiguration aj = YamlConfiguration.loadConfiguration(positions);
        var areas = aj.getConfigurationSection("areas");
        if (areas == null || areas.getKeys(false).isEmpty()) return;
        String name = areas.contains("spawn") ? "spawn" : areas.getKeys(false).iterator().next();
        Location a = parseAj(areas.getString(name + ".pos1")), b = parseAj(areas.getString(name + ".pos2"));
        if (a == null || b == null) return;
        saveLocation("area.pos1", a.getBlock().getLocation());
        saveLocation("area.pos2", b.getBlock().getLocation());
        Location fall = parseAj(areas.getString(name + ".fallpos"));
        if (fall != null) {
            saveLocation("return", fall);
            data.set("return.yaw", (double) fall.getYaw());
            data.set("return.pitch", (double) fall.getPitch());
        }
        save();
        plugin.getLogger().info("Took the parkour area from ajParkour's \"" + name + "\" area.");
    }

    /** ajParkour's "world,x,y,z,yaw,pitch". */
    private static Location parseAj(String text) {
        if (text == null) return null;
        String[] parts = text.split(",");
        if (parts.length < 4) return null;
        World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) return null;
        try {
            Location at = new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
            if (parts.length >= 6) {
                at.setYaw(Float.parseFloat(parts[4]));
                at.setPitch(Float.parseFloat(parts[5]));
            }
            return at;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Re-reads parkour.yml (/lobby reload), in case it was edited by hand. */
    public void reload() {
        try {
            data.load(file);
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            if (file.exists()) plugin.getLogger().warning("Could not read parkour.yml: " + e.getMessage());
        }
        holograms.forEach(Hologram::remove);
        playtime.remove();
        updateDisplays();
    }

    public void shutdown() {
        game.shutdown();
        displayTask.cancel();
        holograms.forEach(Hologram::remove);
        playtime.remove();
    }

    private void updateDisplays() {
        for (Hologram hologram : holograms) hologram.update();
        playtime.update();
        // Ask Core for the longest playtimes now and then (this runs every second).
        if (readLocation("playtime") != null && playtimeAsked++ % 15 == 0) {
            plugin.coreLink().requestTopPlaytime(Math.max(1, Math.min(20, plugin.getConfig().getInt("playtime.hologram-top", 10))));
        }
        // Look up the top 10's ranks ahead of time, so the leaderboard menu has them when it opens.
        if (plugin.luckPerms()) {
            plugin.getServer().getOnlinePlayers().stream().findFirst().ifPresent(anyone -> {
                for (Leaderboard.Entry entry : leaderboard.entries(anyone)) LuckPermsHook.prefix(entry.name());
            });
        }
    }

    // ---------------------------------------------------------------- hologram text

    /** The leaderboard: the best scores, then the viewer's own best and rank. */
    private List<Component> leaderboardText(Player viewer) {
        Messages messages = plugin.messages();
        List<Component> lines = new ArrayList<>();
        lines.add(messages.component("parkour.leaderboard.title"));
        List<Leaderboard.Entry> top = leaderboard.entries(viewer);
        int places = plugin.lobbyConfig().parkour().hologramTop();
        // Every place is listed; the ones nobody has taken yet show as empty.
        for (int i = 0; i < places; i++) {
            lines.add(i < top.size() ? messages.component("parkour.leaderboard.top-line", row(top.get(i)))
                    : messages.component("parkour.leaderboard.empty-line", Messages.arg("position", i + 1)));
        }
        lines.add(Component.empty());
        lines.add(messages.component("parkour.leaderboard.you", you(viewer)));
        lines.add(messages.component("parkour.leaderboard.hint"));
        return lines;
    }

    /** A leaderboard row: <position> <name> <score>, and <name-rank> (the player's LuckPerms prefix). */
    private TagResolver row(Leaderboard.Entry entry) {
        // Colour codes reset formatting, which would turn the name in menus back to italic.
        String rank = plugin.luckPerms()
                ? LegacyCodes.toMiniMessage(LuckPermsHook.prefix(entry.name())).replace("<reset>", "<reset><!italic>") : "";
        return TagResolver.resolver(Messages.arg("position", entry.position()),
                Messages.name("name", entry.name()), Messages.arg("score", entry.score()),
                Placeholder.parsed("name-rank", rank));
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
        Menu menu = new Menu();
        var style = plugin.menuStyle();
        menu.inventory = Bukkit.createInventory(menu, 45,
                style.title(com.sasha.lobby.items.MenuStyle.PARKOUR, messages.component("parkour.menu.title")));
        ItemStack filler = style.filler();
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);

        List<Leaderboard.Entry> top = leaderboard.entries(player);
        int[] slots = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
        for (int i = 0; i < slots.length; i++) {
            ItemStack item;
            if (i < top.size()) {
                Leaderboard.Entry entry = top.get(i);
                TagResolver args = TagResolver.resolver(row(entry), Messages.arg("time", entry.time()));
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Keep a leaving player's rank for the leaderboards, which show offline players too.
        if (plugin.luckPerms()) LuckPermsHook.remember(event.getPlayer());
        UUID id = event.getPlayer().getUniqueId();
        cooldowns.remove(id);
        lastOpened.remove(id);
        for (Hologram hologram : holograms) hologram.forget(id);
        playtime.forget(id);
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
