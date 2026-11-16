/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AFK: after afk.minutes idle, or straight away with /afk, the player's rank becomes the AFK badge, their name
 * goes light red and "AFK" shows under it. Nothing is said in chat (only /afk tells the player).
 *
 * Coming back is measured from where the player stood when they went AFK, not from one movement packet to the
 * next, so mouse jitter, pressure plates or being nudged never flicker it off:
 * walking a block away, turning around properly, chatting, a command, clicking, or /afk again.
 */
public final class AfkListener implements Listener {

    public static final String PERMISSION = "lobby.cmd.afk";
    /** How far (blocks, sideways) and how much (degrees) a player has to move or turn to count as back. */
    private static final double WALK = 1.0, TURN = 30;

    private final Lobby plugin;
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
    /** AFK players and where they were when they went AFK. */
    private final Map<UUID, Location> afk = new ConcurrentHashMap<>();
    private BukkitTask task;

    public AfkListener(Lobby plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("afk.enabled", true);
    }

    private long timeoutMillis() {
        return Math.round(Math.max(0.1, plugin.getConfig().getDouble("afk.minutes", 5)) * 60_000);
    }

    public boolean isAfk(Player player) {
        return enabled() && afk.containsKey(player.getUniqueId());
    }

    /** Checks everyone once a second. */
    public void start() {
        if (task != null) task.cancel();
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) lastActive.putIfAbsent(player.getUniqueId(), now);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::check, 20, 20);
    }

    private void check() {
        if (!enabled()) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (afk.remove(player.getUniqueId()) != null) plugin.display().refreshAfk(player);
            }
            return;
        }
        long now = System.currentTimeMillis();
        long timeout = timeoutMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!afk.containsKey(player.getUniqueId())
                    && now - lastActive.getOrDefault(player.getUniqueId(), now) >= timeout) {
                goAfk(player);
            }
        }
    }

    private void goAfk(Player player) {
        afk.put(player.getUniqueId(), player.getLocation());
        plugin.display().refreshAfk(player);
    }

    /** /afk: go AFK now, or come back if already AFK. Only the player is told. */
    public void toggle(Player player) {
        if (afk.containsKey(player.getUniqueId())) {
            back(player);
            plugin.messages().send(player, "afk.off");
        } else {
            goAfk(player);
            plugin.messages().send(player, "afk.on");
        }
    }

    /** The player did something: they're not idle, and if they were AFK they're back. */
    private void active(Player player) {
        lastActive.put(player.getUniqueId(), System.currentTimeMillis());
        if (afk.containsKey(player.getUniqueId())) {
            // Chat arrives off the main thread; the tags are changed on it.
            if (plugin.getServer().isPrimaryThread()) back(player);
            else plugin.getServer().getScheduler().runTask(plugin, () -> back(player));
        }
    }

    private void back(Player player) {
        lastActive.put(player.getUniqueId(), System.currentTimeMillis());
        if (afk.remove(player.getUniqueId()) != null) plugin.display().refreshAfk(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        lastActive.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastActive.remove(event.getPlayer().getUniqueId());
        afk.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location since = afk.get(player.getUniqueId());
        if (since == null) {
            // Not AFK: any real walking or looking keeps the idle timer from running out.
            Location from = event.getFrom();
            double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
            if (dx * dx + dz * dz > 0.0025 || Math.abs(from.getYaw() - to.getYaw()) > 1
                    || Math.abs(from.getPitch() - to.getPitch()) > 1) {
                lastActive.put(player.getUniqueId(), System.currentTimeMillis());
            }
            return;
        }
        // AFK: back only once they've walked away from that spot or properly turned around.
        if (!since.getWorld().equals(to.getWorld())) return;
        double dx = to.getX() - since.getX(), dz = to.getZ() - since.getZ();
        float turned = Math.abs(((to.getYaw() - since.getYaw()) % 360 + 540) % 360 - 180);
        if (dx * dx + dz * dz > WALK * WALK || turned > TURN || Math.abs(to.getPitch() - since.getPitch()) > TURN) {
            active(player);
        }
    }

    /** Teleported while AFK (void rescue, parkour fall...): measure from the new spot. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        afk.computeIfPresent(event.getPlayer().getUniqueId(), (id, old) -> event.getTo().clone());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        // /afk decides for itself (otherwise it would always count as coming back first).
        String label = event.getMessage().split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (label.equals("/afk") || label.equals("/lobby:afk")) return;
        active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        // Standing on a pressure plate or tripwire fires this over and over; that isn't the player doing anything.
        if (event.getAction() == Action.PHYSICAL) return;
        active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) active(player);
    }
}
