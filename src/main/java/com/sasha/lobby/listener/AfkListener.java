/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AFK: after afk.minutes without moving, looking around, chatting or clicking, a player's line under their name
 * shows a red "AFK" instead of their status (nothing is said in chat). Any activity takes it away again.
 */
public final class AfkListener implements Listener {

    public static final String PERMISSION = "lobby.cmd.afk";

    private final Lobby plugin;
    /** Players who typed /afk: they stay AFK (whatever the timer says) until they do something. */
    private final Set<UUID> manual = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
    private final Set<UUID> afk = ConcurrentHashMap.newKeySet();
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
        return enabled() && afk.contains(player.getUniqueId());
    }

    /** Checks everyone once a second. */
    public void start() {
        if (task != null) task.cancel();
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) lastActive.putIfAbsent(player.getUniqueId(), now);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::check, 20, 20);
    }

    private void check() {
        long now = System.currentTimeMillis();
        long timeout = timeoutMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            boolean idle = enabled() && (manual.contains(player.getUniqueId())
                    || now - lastActive.getOrDefault(player.getUniqueId(), now) >= timeout);
            if (idle && afk.add(player.getUniqueId())) plugin.display().refreshAfk(player);
            else if (!idle && afk.remove(player.getUniqueId())) plugin.display().refreshAfk(player);
        }
    }

    /** /afk: go AFK now, or come back if already AFK. Only the player is told. */
    public void toggle(Player player) {
        if (afk.contains(player.getUniqueId())) {
            manual.remove(player.getUniqueId());
            active(player);
            plugin.messages().send(player, "afk.off");
        } else {
            manual.add(player.getUniqueId());
            afk.add(player.getUniqueId());
            plugin.display().refreshAfk(player);
            plugin.messages().send(player, "afk.on");
        }
    }

    private void active(Player player) {
        manual.remove(player.getUniqueId());
        lastActive.put(player.getUniqueId(), System.currentTimeMillis());
        if (afk.remove(player.getUniqueId())) {
            // Chat and commands arrive off the main thread; the name line is changed on it.
            if (plugin.getServer().isPrimaryThread()) plugin.display().refreshAfk(player);
            else plugin.getServer().getScheduler().runTask(plugin, () -> plugin.display().refreshAfk(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        lastActive.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastActive.remove(event.getPlayer().getUniqueId());
        afk.remove(event.getPlayer().getUniqueId());
        manual.remove(event.getPlayer().getUniqueId());
    }

    /** Walking or looking around. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        var from = event.getFrom();
        var to = event.getTo();
        boolean looked = Math.abs(from.getYaw() - to.getYaw()) > 0.5 || Math.abs(from.getPitch() - to.getPitch()) > 0.5;
        // Sideways only: falling, floating or being bounced in place isn't the player doing anything.
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        boolean walked = dx * dx + dz * dz > 0.0025 && event.getPlayer().getVehicle() == null;
        if (looked || walked) active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        // /afk itself decides (otherwise it would always count as coming back first)
        String label = event.getMessage().split(" ", 2)[0].toLowerCase(java.util.Locale.ROOT);
        if (label.equals("/afk") || label.equals("/lobby:afk")) return;
        active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) active(player);
    }
}
