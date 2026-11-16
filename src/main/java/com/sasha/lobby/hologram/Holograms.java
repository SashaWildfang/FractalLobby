/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.hologram;

import com.sasha.lobby.Lobby;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The lobby's holograms (replacing DecentHolograms): holograms/*.yml in DecentHolograms' format, shown with display
 * entities. Lines that are the same for everyone are one shared display; lines with {player} or placeholders, and
 * every line of a hologram with several pages, get one display per nearby player. Clicking a hologram runs its
 * page's actions (MESSAGE, COMMAND, CONSOLE, CONNECT, SOUND, TELEPORT, NEXT_PAGE, PREV_PAGE, PAGE).
 */
public final class Holograms implements Listener {

    private static final String TAG = "lobby_holo";
    /** How often everything is checked (ticks); animations move one step each time. */
    private static final int STEP = 2;

    private final Lobby plugin;
    private final File folder;
    private final Map<String, Live> live = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    /** Holograms that belong to something else (an NPC's label): not saved here, and kept across /holo reload. */
    private final Map<String, Live> attached = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private BukkitTask task;
    private long ticks;

    public Holograms(Lobby plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "holograms");
    }

    // ---------------------------------------------------------------- loading

    public void start() {
        stop();
        if (!folder.isDirectory()) importDecentHolograms();
        folder.mkdirs();
        clearLeftovers();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                Hologram holo = Hologram.load(file);
                if (holo.location == null) {
                    plugin.getLogger().warning("holograms/" + file.getName() + ": its world isn't loaded or the location is wrong; skipped.");
                }
                live.put(holo.name, new Live(holo));
            }
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, STEP);
    }

    /** Takes the file holograms down (attached ones stay; they're despawned only by {@link #shutdown}). */
    public void stop() {
        if (task != null) task.cancel();
        task = null;
        live.values().forEach(Live::despawn);
        live.clear();
        attached.values().forEach(Live::despawn);
    }

    public void shutdown() {
        stop();
        attached.clear();
    }

    /** Shows a hologram owned by something else (an NPC), until {@link #detach}. Calling it again replaces it. */
    public void attach(String key, Hologram holo) {
        Live old = attached.put(key, new Live(holo));
        if (old != null) old.despawn();
    }

    /** Moves an attached hologram (its displays follow on the next tick). */
    public void moveAttached(String key, Location to) {
        Live l = attached.get(key);
        if (l == null || l.holo.location == null || !l.holo.location.getWorld().equals(to.getWorld())
                || l.holo.location.distanceSquared(to) > 0.0001) {
            if (l != null) {
                l.holo.location = to;
                l.despawn();
            }
        }
    }

    public void detach(String key) {
        Live old = attached.remove(key);
        if (old != null) old.despawn();
    }

    /**
     * Takes a file hologram out (an NPC took its lines): it stops showing and its file is kept as NAME.yml.old.
     * Returns its lines, or null if there's no such hologram file.
     */
    public Hologram absorb(String name) {
        File file = new File(folder, name + ".yml");
        if (!file.isFile()) {
            File[] files = folder.listFiles((dir, n) -> n.equalsIgnoreCase(name + ".yml"));
            if (files == null || files.length == 0) return null;
            file = files[0];
        }
        Hologram holo = Hologram.load(file);
        Live l = live.remove(holo.name);
        if (l != null) l.despawn();
        file.renameTo(new File(folder, file.getName() + ".old"));
        return holo;
    }

    /** The first time: DecentHolograms' hologram files are copied over as they are. */
    private void importDecentHolograms() {
        File dh = new File(plugin.getDataFolder().getParentFile(), "DecentHolograms/holograms");
        File[] files = dh.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) return;
        folder.mkdirs();
        for (File file : files) {
            try {
                Files.copy(file.toPath(), new File(folder, file.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("Couldn't copy hologram " + file.getName() + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Brought over " + files.length + " holograms from DecentHolograms.");
    }

    /** Displays from before a crash or reload (they aren't saved, but just in case). */
    private void clearLeftovers() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) if (entity.getScoreboardTags().contains(TAG)) entity.remove();
        }
    }

    // ---------------------------------------------------------------- editing (for /holo)

    public Collection<Hologram> all() {
        List<Hologram> list = new ArrayList<>();
        for (Live l : live.values()) list.add(l.holo);
        return list;
    }

    public Hologram get(String name) {
        Live l = live.get(name);
        return l == null ? null : l.holo;
    }

    public Hologram create(String name, Location at, String firstLine) throws IOException {
        Hologram holo = new Hologram(name, at);
        Hologram.Page page = new Hologram.Page();
        page.lines.add(Hologram.Line.of(firstLine));
        holo.pages.add(page);
        holo.save(folder);
        live.put(name, new Live(holo));
        return holo;
    }

    public boolean delete(String name) {
        Live l = live.remove(name);
        if (l == null) return false;
        l.despawn();
        return new File(folder, l.holo.name + ".yml").delete();
    }

    /** Saves a hologram after a change and shows the change straight away. */
    public void changed(Hologram holo) throws IOException {
        holo.save(folder);
        Live l = live.get(holo.name);
        if (l != null) l.despawn();
        live.put(holo.name, new Live(holo));
    }

    // ---------------------------------------------------------------- showing

    private void tick() {
        ticks++;
        for (Live l : (Iterable<Live>) () -> java.util.stream.Stream.concat(live.values().stream(), attached.values().stream()).iterator()) {
            try {
                l.tick();
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().warning("Hologram " + l.holo.name + ": " + e);
            }
        }
    }

    /** A hologram in the world: its shared displays, each nearby viewer's own, and its click box. */
    private final class Live {
        final Hologram holo;
        /** line index → display everyone sees (page 1 only). */
        final Map<Integer, Display> shared = new HashMap<>();
        /** viewer → line index → their own display. */
        final Map<UUID, Map<Integer, Display>> own = new HashMap<>();
        final Map<UUID, Integer> page = new HashMap<>();
        Interaction clickBox;

        Live(Hologram holo) {
            this.holo = holo;
        }

        boolean multiPage() {
            return holo.pages.size() > 1;
        }

        boolean clickable() {
            if (multiPage()) return true;
            for (Hologram.Page p : holo.pages) if (!p.actions.isEmpty()) return true;
            return false;
        }

        void tick() {
            Location at = holo.location;
            if (!holo.enabled || at == null || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                despawn();
                return;
            }
            boolean refresh = ticks % Math.max(1, holo.updateInterval / STEP) == 0;
            double range = holo.displayRange;
            List<Player> near = new ArrayList<>();
            for (Player p : at.getWorld().getPlayers()) if (p.getLocation().distanceSquared(at) <= range * range) near.add(p);

            // Lines everyone sees the same (page 1 of a single-page hologram).
            List<Hologram.Line> first = holo.pages.getFirst().lines;
            double[] y = Hologram.offsets(first, holo.downOrigin);
            for (int i = 0; i < first.size(); i++) {
                Hologram.Line line = first.get(i);
                if (multiPage() || (!line.item() && HologramText.perViewer(line.content))) {
                    removeShared(i);
                    continue;
                }
                Display display = shared.get(i);
                boolean fresh = display == null || !display.isValid();
                if (fresh) {
                    if (display != null) display.remove();
                    display = spawn(line, at.clone().add(0, y[i], 0), null);
                    shared.put(i, display);
                }
                if (display instanceof TextDisplay text && (fresh || HologramText.animated(line.content))) {
                    text.text(HologramText.render(line.content, ticks));
                }
            }
            for (Iterator<Integer> it = shared.keySet().iterator(); it.hasNext(); ) {
                int i = it.next();
                if (i >= first.size()) {
                    shared.get(i).remove();
                    it.remove();
                }
            }

            // Each nearby viewer's own lines.
            for (Iterator<Map.Entry<UUID, Map<Integer, Display>>> it = own.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Map<Integer, Display>> entry = it.next();
                Player viewer = Bukkit.getPlayer(entry.getKey());
                if (viewer == null || !near.contains(viewer)) {
                    entry.getValue().values().forEach(Entity::remove);
                    it.remove();
                }
            }
            for (Player viewer : near) {
                int pageIndex = Math.min(page.getOrDefault(viewer.getUniqueId(), 0), holo.pages.size() - 1);
                List<Hologram.Line> lines = holo.pages.get(pageIndex).lines;
                double[] vy = Hologram.offsets(lines, holo.downOrigin);
                Map<Integer, Display> mine = own.computeIfAbsent(viewer.getUniqueId(), k -> new HashMap<>());
                for (int i = 0; i < lines.size(); i++) {
                    Hologram.Line line = lines.get(i);
                    boolean personal = multiPage() || (!line.item() && HologramText.perViewer(line.content));
                    Display display = mine.get(i);
                    if (!personal) {
                        if (display != null) mine.remove(i).remove();
                        continue;
                    }
                    boolean fresh = display == null || !display.isValid();
                    if (fresh) {
                        if (display != null) display.remove();
                        display = spawn(line, at.clone().add(0, vy[i], 0), viewer);
                        mine.put(i, display);
                    }
                    if (display instanceof TextDisplay text && (fresh || refresh || HologramText.animated(line.content))) {
                        text.text(HologramText.render(fill(line.content, viewer), ticks));
                    }
                }
                for (Iterator<Integer> it = mine.keySet().iterator(); it.hasNext(); ) {
                    int i = it.next();
                    if (i >= lines.size()) {
                        mine.get(i).remove();
                        it.remove();
                    }
                }
            }

            if (clickable()) ensureClickBox(at, first, y);
            else if (clickBox != null) {
                clickBox.remove();
                clickBox = null;
            }
        }

        private void removeShared(int i) {
            Display old = shared.remove(i);
            if (old != null) old.remove();
        }

        /** A display for one line; for a viewer only, if a viewer is given. */
        private Display spawn(Hologram.Line line, Location where, Player viewer) {
            float viewRange = (float) Math.max(0.1, holo.displayRange / 64.0);
            Display display;
            if (line.item()) {
                ItemStack item = item(line.content, viewer);
                display = where.getWorld().spawn(where.clone().add(0, 0.25, 0), ItemDisplay.class, spawned -> {
                    spawned.setItemStack(item);
                    spawned.setTransformation(new Transformation(new Vector3f(), new org.joml.Quaternionf(),
                            new Vector3f(0.5f, 0.5f, 0.5f), new org.joml.Quaternionf()));
                });
            } else {
                display = where.getWorld().spawn(where, TextDisplay.class, spawned -> {
                    spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
                    spawned.setShadowed(false);
                    spawned.setSeeThrough(false);
                    spawned.setDefaultBackground(true);
                    spawned.setLineWidth(1000);
                });
            }
            display.setBillboard(Display.Billboard.CENTER);
            display.setViewRange(viewRange);
            display.setPersistent(false);
            display.addScoreboardTag(TAG);
            if (viewer != null) {
                display.setVisibleByDefault(false);
                viewer.showEntity(plugin, display);
            }
            return display;
        }

        private void ensureClickBox(Location at, List<Hologram.Line> lines, double[] y) {
            double top = 0, bottom = 0;
            for (int i = 0; i < lines.size(); i++) {
                top = Math.max(top, y[i] + 0.3);
                bottom = Math.min(bottom, y[i]);
            }
            Location base = at.clone().add(0, bottom, 0);
            float height = (float) Math.max(0.5, top - bottom);
            if (clickBox == null || !clickBox.isValid() || clickBox.getLocation().distanceSquared(base) > 0.01) {
                if (clickBox != null) clickBox.remove();
                clickBox = at.getWorld().spawn(base, Interaction.class, box -> {
                    box.setPersistent(false);
                    box.setResponsive(true);
                    box.addScoreboardTag(TAG);
                });
            }
            if (Math.abs(clickBox.getInteractionHeight() - height) > 0.01) {
                clickBox.setInteractionHeight(height);
                clickBox.setInteractionWidth(3.0f);
            }
        }

        void forget(UUID viewer) {
            Map<Integer, Display> mine = own.remove(viewer);
            if (mine != null) mine.values().forEach(Entity::remove);
            page.remove(viewer);
        }

        void despawn() {
            shared.values().forEach(Entity::remove);
            shared.clear();
            for (Map<Integer, Display> mine : own.values()) mine.values().forEach(Entity::remove);
            own.clear();
            if (clickBox != null) clickBox.remove();
            clickBox = null;
        }

        void turnPage(Player viewer, int to) {
            int pages = holo.pages.size();
            page.put(viewer.getUniqueId(), Math.floorMod(to, pages));
            Map<Integer, Display> mine = own.remove(viewer.getUniqueId());
            if (mine != null) mine.values().forEach(Entity::remove); // redrawn next tick with the new page
        }
    }

    /** {player} and PlaceholderAPI placeholders, for this viewer. */
    private String fill(String content, Player viewer) {
        String text = content.replace("{player}", viewer.getName());
        return com.sasha.lobby.hook.Placeholders.apply(plugin, viewer, text);
    }

    private ItemStack item(String content, Player viewer) {
        String spec = content.substring(content.indexOf(':') + 1).trim();
        String kind = content.trim().toUpperCase(Locale.ROOT);
        if (kind.startsWith("#HEAD:") || kind.startsWith("#SMALLHEAD:")) {
            String owner = viewer != null ? spec.replace("{player}", viewer.getName()) : spec;
            ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(Bukkit.createProfile(owner)));
            return head;
        }
        Material material = Material.matchMaterial(spec.split("\\s+")[0]);
        return ItemStack.of(material == null || !material.isItem() ? Material.BARRIER : material);
    }

    // ---------------------------------------------------------------- clicks

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRightClick(PlayerInteractEntityEvent event) {
        Live l = owner(event.getRightClicked());
        if (l == null) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) click(l, event.getPlayer(), event.getPlayer().isSneaking() ? "SHIFT_RIGHT" : "RIGHT");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeftClick(PrePlayerAttackEntityEvent event) {
        Live l = owner(event.getAttacked());
        if (l == null) return;
        event.setCancelled(true);
        click(l, event.getPlayer(), event.getPlayer().isSneaking() ? "SHIFT_LEFT" : "LEFT");
    }

    private Live owner(Entity entity) {
        if (!(entity instanceof Interaction)) return null;
        for (Live l : live.values()) if (l.clickBox != null && l.clickBox.equals(entity)) return l;
        for (Live l : attached.values()) if (l.clickBox != null && l.clickBox.equals(entity)) return l;
        return null;
    }

    private final Map<UUID, Long> lastClick = new HashMap<>();

    private void click(Live l, Player player, String type) {
        long now = System.currentTimeMillis();
        if (now - lastClick.getOrDefault(player.getUniqueId(), 0L) < 250) return;
        lastClick.put(player.getUniqueId(), now);
        int pageIndex = Math.min(l.page.getOrDefault(player.getUniqueId(), 0), l.holo.pages.size() - 1);
        Hologram.Page page = l.holo.pages.get(pageIndex);
        List<String> actions = new ArrayList<>(page.actions.getOrDefault(type, List.of()));
        if (type.startsWith("SHIFT_") && actions.isEmpty()) actions.addAll(page.actions.getOrDefault(type.substring(6), List.of()));
        actions.addAll(page.actions.getOrDefault("ANY", List.of()));
        // A hologram with pages and no actions turns its page on a click.
        if (actions.isEmpty() && l.multiPage()) actions.add(type.contains("LEFT") ? "PREV_PAGE" : "NEXT_PAGE");
        for (String action : actions) run(l, player, action, pageIndex);
    }

    private void run(Live l, Player player, String action, int pageIndex) {
        int colon = action.indexOf(':');
        String type = (colon < 0 ? action : action.substring(0, colon)).trim().toUpperCase(Locale.ROOT);
        String value = colon < 0 ? "" : fill(action.substring(colon + 1), player);
        switch (type) {
            case "MESSAGE" -> player.sendMessage(HologramText.render(value, 0));
            case "COMMAND" -> player.performCommand(value.startsWith("/") ? value.substring(1) : value);
            case "CONSOLE" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value.startsWith("/") ? value.substring(1) : value);
            case "CONNECT" -> plugin.proxyCounts().connect(player, value.trim());
            case "SOUND" -> {
                String[] parts = value.split(":");
                try {
                    player.playSound(Sound.sound(Key.key(parts[0].toLowerCase(Locale.ROOT).replace('_', '.')
                                    .replaceFirst("^minecraft\\.", "")), Sound.Source.MASTER,
                            parts.length > 1 ? Float.parseFloat(parts[1]) : 1f, parts.length > 2 ? Float.parseFloat(parts[2]) : 1f),
                            Sound.Emitter.self());
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Hologram " + l.holo.name + ": bad sound '" + value + "'");
                }
            }
            case "TELEPORT" -> {
                String[] parts = value.split(":");
                World world = parts.length >= 4 ? Bukkit.getWorld(parts[0]) : null;
                if (world != null) {
                    try {
                        player.teleport(new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3])));
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Hologram " + l.holo.name + ": bad teleport '" + value + "'");
                    }
                }
            }
            case "NEXT_PAGE" -> l.turnPage(player, pageIndex + 1);
            case "PREV_PAGE" -> l.turnPage(player, pageIndex - 1);
            case "PAGE" -> {
                try {
                    l.turnPage(player, Integer.parseInt(value.trim()) - 1);
                } catch (NumberFormatException ignored) {
                    // not a page number
                }
            }
            default -> plugin.getLogger().warning("Hologram " + l.holo.name + ": unknown action '" + action + "'");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        for (Live l : live.values()) l.forget(event.getPlayer().getUniqueId());
        for (Live l : attached.values()) l.forget(event.getPlayer().getUniqueId());
        lastClick.remove(event.getPlayer().getUniqueId());
    }

    public static Component preview(String content) {
        return HologramText.render(content, 0);
    }
}
