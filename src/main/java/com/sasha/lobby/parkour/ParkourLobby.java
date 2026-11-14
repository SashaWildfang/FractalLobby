/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.parkour;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.hook.PlaceholderHook;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Sets up a pressure plate that starts AjParkour and its high-score hologram. */
public final class ParkourLobby implements Listener {

    private final Lobby plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, TextDisplay> viewerDisplays = new HashMap<>();
    private BukkitTask displayTask;

    public ParkourLobby(Lobby plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "parkour.yml");
        data = YamlConfiguration.loadConfiguration(file);
        displayTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateDisplays, 1L, 20L);
    }

    /** Places the start plate at the admin's feet and its hologram one block above it. */
    public void setup(Player admin) {
        Location plate = admin.getLocation().getBlock().getLocation();
        plate.getBlock().setType(Material.POLISHED_BLACKSTONE_PRESSURE_PLATE, false);
        saveLocation("plate", plate);
        saveLocation("hologram", plate.clone().add(0.5, 1.0, 0.5));
        save();
        removeDisplays();
        updateDisplays();
        plugin.messages().send(admin, "parkour.setup");
    }

    /** A separate text display per viewer lets PlaceholderAPI resolve their own high score. */
    private void updateDisplays() {
        Location location = readLocation("hologram");
        if (location == null) {
            removeDisplays();
            return;
        }
        cleanupStackedDisplays(location);

        Iterator<Map.Entry<UUID, TextDisplay>> iterator = viewerDisplays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TextDisplay> entry = iterator.next();
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer == null || !viewer.isOnline() || !viewer.getWorld().equals(location.getWorld())) {
                entry.getValue().remove();
                iterator.remove();
            }
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getWorld().equals(location.getWorld())) continue;
            TextDisplay display = viewerDisplays.get(viewer.getUniqueId());
            if (display == null || !display.isValid()) {
                String tag = displayTag(viewer.getUniqueId());
                List<TextDisplay> tagged = location.getWorld().getEntitiesByClass(TextDisplay.class).stream()
                        .filter(candidate -> candidate.getScoreboardTags().contains(tag)).toList();
                display = tagged.isEmpty()
                        ? (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY)
                        : tagged.getFirst();
                for (int i = 1; i < tagged.size(); i++) tagged.get(i).remove();
                display.addScoreboardTag(tag);
                display.setBillboard(TextDisplay.Billboard.CENTER);
                display.setSeeThrough(true);
                display.setShadowed(true);
                display.setDefaultBackground(false);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setPersistent(false);
                viewerDisplays.put(viewer.getUniqueId(), display);
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.equals(viewer)) other.hideEntity(plugin, display);
                }
            }

            viewer.showEntity(plugin, display);
            String highScore = "%ajpk_stats_highscore%";
            if (plugin.placeholderApi()) highScore = PlaceholderHook.apply(viewer, highScore);
            Component text = plugin.messages().render("<brand><bold>Parkour!</bold></brand>\n"
                    + "<secondary>High Score: <primary>" + highScore);
            display.text(text);
        }
    }

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
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, this::updateDisplays);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
        TextDisplay display = viewerDisplays.remove(event.getPlayer().getUniqueId());
        if (display != null) display.remove();
    }

    public void shutdown() {
        if (displayTask != null) displayTask.cancel();
        removeDisplays();
    }

    private void removeDisplays() {
        viewerDisplays.values().forEach(TextDisplay::remove);
        viewerDisplays.clear();
    }

    /** Reconcile entity copies left by reloads or lost references before updating placeholder text. */
    private void cleanupStackedDisplays(Location location) {
        Map<String, List<TextDisplay>> taggedDisplays = new HashMap<>();
        for (TextDisplay candidate : location.getWorld().getEntitiesByClass(TextDisplay.class)) {
            if (candidate.getLocation().distanceSquared(location) > 9) continue;
            String ownTag = candidate.getScoreboardTags().stream()
                    .filter(tag -> tag.startsWith("fractal_lobby_parkour_holo_"))
                    .findFirst().orElse(null);
            if (ownTag != null) {
                taggedDisplays.computeIfAbsent(ownTag, ignored -> new ArrayList<>()).add(candidate);
            } else {
                String text = candidate.text().toString().toLowerCase(java.util.Locale.ROOT);
                if (text.contains("parkour") || text.contains("ᴘᴀʀᴋᴏᴜʀ")) candidate.remove();
            }
        }

        Set<TextDisplay> keep = new HashSet<>();
        taggedDisplays.forEach((tag, candidates) -> {
            TextDisplay preferred = null;
            for (Map.Entry<UUID, TextDisplay> entry : viewerDisplays.entrySet()) {
                if (displayTag(entry.getKey()).equals(tag) && candidates.contains(entry.getValue())) {
                    preferred = entry.getValue();
                    break;
                }
            }
            if (preferred == null) preferred = candidates.getFirst();
            keep.add(preferred);
            for (TextDisplay candidate : candidates) if (candidate != preferred) candidate.remove();
        });

        viewerDisplays.entrySet().removeIf(entry -> !entry.getValue().isValid() || !keep.contains(entry.getValue()));
    }

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

    private static String displayTag(UUID viewer) {
        return "fractal_lobby_parkour_holo_" + viewer;
    }
}
