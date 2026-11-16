/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.parkour;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Infinite parkour: a block appears in the air, then the next one to jump to, and so on, inside the parkour area.
 * Jumps get longer as the score goes up. The next block is outlined with sparkles and a dotted arc shows the jump,
 * only for the runner. While running, the sidebar shows the score; falling ends the run (and the sidebar goes back).
 */
public final class ParkourGame implements Listener {

    /** The box the course is built in (block coordinates, inclusive). */
    public record Area(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static Area of(Location a, Location b) {
            return new Area(a.getWorld(), Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()),
                    Math.min(a.getBlockZ(), b.getBlockZ()), Math.max(a.getBlockX(), b.getBlockX()),
                    Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
        }

        boolean holds(Block block, int margin, int headroom) {
            return block.getWorld().equals(world)
                    && block.getX() >= minX + margin && block.getX() <= maxX - margin
                    && block.getZ() >= minZ + margin && block.getZ() <= maxZ - margin
                    && block.getY() >= minY && block.getY() <= maxY - headroom;
        }

        double centerX() {
            return (minX + maxX) / 2.0 + 0.5;
        }

        double centerZ() {
            return (minZ + maxZ) / 2.0 + 0.5;
        }
    }

    private final class Run {
        final Player player;
        final long started = System.currentTimeMillis();
        long lastProgress = started;
        int score;
        float heading;
        Block current;
        Block next;
        int bestBefore;
        Guide guide;

        Run(Player player) {
            this.player = player;
        }

        int seconds() {
            return (int) ((System.currentTimeMillis() - started) / 1000);
        }
    }

    private static final Particle.DustOptions OUTLINE = new Particle.DustOptions(Color.fromRGB(0xB266FF), 0.9f);
    private static final Particle.DustOptions ARC = new Particle.DustOptions(Color.fromRGB(0xFF7BE5), 0.55f);

    private final Lobby plugin;
    private final ParkourLobby parkour;
    private final Map<UUID, Run> runs = new HashMap<>();
    /** Every block a run has placed (so they're never stacked on, and all go when the plugin stops). */
    private final Set<Block> placed = new HashSet<>();
    /** Players being moved by the game (those teleports don't end the run). */
    private final Set<UUID> moving = new HashSet<>();
    private BukkitTask task;
    private long ticks;

    ParkourGame(Lobby plugin, ParkourLobby parkour) {
        this.plugin = plugin;
        this.parkour = parkour;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
        Guide.clearStray(plugin);
    }

    public boolean playing(Player player) {
        return runs.containsKey(player.getUniqueId());
    }

    // ---------------------------------------------------------------- starting

    /** Starts a run (from the Parkour NPC or /parkour). */
    public void start(Player player) {
        Messages messages = plugin.messages();
        if (playing(player)) return;
        Area area = parkour.area();
        if (area == null) {
            messages.send(player, "parkour.game.no-area");
            return;
        }
        Block first = startBlock(area);
        if (first == null) {
            messages.send(player, "parkour.game.no-room");
            return;
        }
        Run run = new Run(player);
        run.bestBefore = parkour.scores().best(player.getUniqueId());
        run.current = first;
        place(first);
        // Face the middle of the area to begin with.
        run.heading = yawTowards(first.getX() + 0.5, first.getZ() + 0.5, area.centerX(), area.centerZ());
        run.next = nextBlock(run, area);
        if (run.next == null) {
            unplace(first);
            messages.send(player, "parkour.game.no-room");
            return;
        }
        place(run.next);
        runs.put(player.getUniqueId(), run);

        Location stand = first.getLocation().add(0.5, 1.0, 0.5);
        stand.setYaw(yawTowards(stand.getX(), stand.getZ(), run.next.getX() + 0.5, run.next.getZ() + 0.5));
        stand.setPitch(20);
        teleport(player, stand);
        player.setFallDistance(0);
        plugin.doubleJump().refresh(player);
        plugin.display().setSidebarOverride(player, () -> sidebar(run));
        messages.send(player, "parkour.game.start", Messages.arg("best", run.bestBefore));
        player.playSound(Sound.sound(Key.key("block.note_block.chime"), Sound.Source.MASTER, 0.7f, 1.2f), Sound.Emitter.self());
    }

    /** /parkour: start, or stop if already running. */
    public void toggle(Player player) {
        if (playing(player)) end(player, "parkour.game.left", true);
        else start(player);
    }

    private Block startBlock(Area area) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int margin = Math.min(3, Math.max(0, (Math.min(area.maxX() - area.minX(), area.maxZ() - area.minZ())) / 4));
        int y = area.minY() + Math.max(2, (area.maxY() - area.minY()) / 4);
        for (int attempt = 0; attempt < 40; attempt++) {
            int x = random.nextInt(area.minX() + margin, area.maxX() - margin + 1);
            int z = random.nextInt(area.minZ() + margin, area.maxZ() - margin + 1);
            Block block = area.world().getBlockAt(x, y, z);
            if (free(block)) return block;
        }
        return null;
    }

    // ---------------------------------------------------------------- the course

    /** How far the next block is (centre to centre, in blocks) for a score: longer jumps as the score goes up. */
    private int[] distance(int score) {
        var c = plugin.getConfig();
        String level = score < c.getInt("parkour.jumps.medium-from", 5) ? "easy"
                : score < c.getInt("parkour.jumps.hard-from", 25) ? "medium"
                : score < c.getInt("parkour.jumps.expert-from", 50) ? "hard" : "expert";
        int[] defaults = switch (level) {
            case "easy" -> new int[] {2, 2};
            case "medium" -> new int[] {3, 3};
            case "hard" -> new int[] {3, 4};
            default -> new int[] {4, 5};
        };
        int min = Math.max(2, Math.min(5, c.getInt("parkour.jumps." + level + "-min", defaults[0])));
        int max = Math.max(min, Math.min(5, c.getInt("parkour.jumps." + level + "-max", defaults[1])));
        return new int[] {min, max};
    }

    /** Picks the next block: roughly ahead (turning a little each time), inside the area, and always makeable. */
    private Block nextBlock(Run run, Area area) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int[] range = distance(run.score);
        Block from = run.current;
        for (int attempt = 0; attempt < 60; attempt++) {
            // Later attempts turn harder, and the last ones head back towards the middle.
            float spread = attempt < 20 ? 50 : attempt < 40 ? 120 : 0;
            float heading = attempt < 40
                    ? run.heading + (float) random.nextDouble(-spread, spread)
                    : yawTowards(from.getX() + 0.5, from.getZ() + 0.5, area.centerX(), area.centerZ())
                    + (float) random.nextDouble(-40, 40);
            int d = random.nextInt(range[0], range[1] + 1);
            int dy = switch (random.nextInt(4)) {
                case 0 -> 1;
                case 1 -> -1;
                default -> 0;
            };
            if (attempt >= 30 && dy > 0) dy = 0;
            double rad = Math.toRadians(heading);
            int dx = (int) Math.round(-Math.sin(rad) * d);
            int dz = (int) Math.round(Math.cos(rad) * d);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue; // needs a gap to jump
            double reach = Math.hypot(dx, dz);
            double limit = dy > 0 ? 4.3 : dy < 0 ? 5.6 : 5.1;
            if (reach > limit) continue;
            Block target = from.getRelative(dx, dy, dz);
            if (!area.holds(target, 1, 3) || !free(target)) continue;
            run.heading = yawTowards(from.getX(), from.getZ(), target.getX(), target.getZ());
            return target;
        }
        return null;
    }

    /** Empty, with room to stand above, and not touching another run's blocks. */
    private boolean free(Block block) {
        for (int up = 0; up <= 2; up++) if (!block.getRelative(0, up, 0).getType().isAir()) return false;
        for (Block other : placed) {
            if (other.getWorld().equals(block.getWorld()) && Math.abs(other.getX() - block.getX()) <= 1
                    && Math.abs(other.getZ() - block.getZ()) <= 1 && Math.abs(other.getY() - block.getY()) <= 2) return false;
        }
        return true;
    }

    private Material blockType() {
        List<Material> types = new ArrayList<>();
        for (String name : plugin.getConfig().getStringList("parkour.blocks")) {
            Material type = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (type != null && type.isBlock() && type.isSolid()) types.add(type);
        }
        if (types.isEmpty()) types = List.of(Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE, Material.PINK_CONCRETE);
        return types.get(ThreadLocalRandom.current().nextInt(types.size()));
    }

    private void place(Block block) {
        block.setType(blockType(), false);
        placed.add(block);
    }

    private void unplace(Block block) {
        if (block == null) return;
        if (placed.remove(block)) block.setType(Material.AIR, false);
    }

    // ---------------------------------------------------------------- playing

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Run run = runs.get(event.getPlayer().getUniqueId());
        if (run == null || !event.hasChangedPosition()) return;
        if (standingOn(event.getTo(), run.next)) advance(run);
    }

    /** Whether someone at this spot is standing on the block (any part of their feet on its top). */
    private static boolean standingOn(Location at, Block block) {
        if (block == null || !at.getWorld().equals(block.getWorld())) return false;
        double above = at.getY() - (block.getY() + 1);
        if (above < -0.01 || above > 0.05) return false;
        for (double ox : new double[] {-0.299, 0.299}) {
            for (double oz : new double[] {-0.299, 0.299}) {
                if ((int) Math.floor(at.getX() + ox) == block.getX() && (int) Math.floor(at.getZ() + oz) == block.getZ()) return true;
            }
        }
        return false;
    }

    private void advance(Run run) {
        Area area = parkour.area();
        if (area == null) {
            end(run.player, "parkour.game.left", true);
            return;
        }
        run.score++;
        run.lastProgress = System.currentTimeMillis();
        unplace(run.current);
        run.current = run.next;
        run.next = nextBlock(run, area);
        if (run.next == null) {
            // Boxed in: start a fresh line from somewhere else in the area, keeping the score.
            Block restart = startBlock(area);
            if (restart == null) {
                end(run.player, "parkour.game.no-room", true);
                return;
            }
            run.next = restart;
        }
        place(run.next);

        Player player = run.player;
        Location spot = run.next.getLocation().add(0.5, 0.5, 0.5);
        player.spawnParticle(Particle.CLOUD, spot, 6, 0.3, 0.3, 0.3, 0.01);
        boolean milestone = run.score % 10 == 0;
        player.playSound(Sound.sound(Key.key(milestone ? "entity.player.levelup" : "entity.experience_orb.pickup"),
                Sound.Source.MASTER, milestone ? 0.6f : 0.5f, milestone ? 1.2f : 0.8f + (run.score % 10) * 0.07f), Sound.Emitter.self());
        if (run.score == run.bestBefore + 1 && run.bestBefore > 0) {
            plugin.messages().actionBar(player, "parkour.game.beating-best");
        }
        plugin.display().refreshSidebar(player);
    }

    private void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        long idle = Math.max(5, plugin.getConfig().getLong("parkour.idle-seconds", 30)) * 1000;
        for (Run run : new ArrayList<>(runs.values())) {
            Player player = run.player;
            if (!player.isOnline()) continue;
            Location at = player.getLocation();
            // Fell below the blocks: the run's over.
            if (at.getY() < Math.min(run.current.getY(), run.next.getY()) - 1.5) {
                end(player, "parkour.game.fell", true);
                continue;
            }
            if (now - run.lastProgress > idle) {
                end(player, "parkour.game.idle", true);
                continue;
            }
            if (ticks % 2 == 0) guide(run);
            if (ticks % 10 == 0) {
                if (!plugin.display().hasSidebarOverride(player)) plugin.display().setSidebarOverride(player, () -> sidebar(run));
                else plugin.display().refreshSidebar(player); // the time ticks on
            }
        }
    }

    /**
     * Shows the runner where to jump: with the resource pack's guide (parkour.guide: pack) a glowing frame on the next
     * block and orbs along the jump, else (particles) sparkles and a dotted arc. Only the runner sees it.
     */
    private void guide(Run run) {
        if (!"particles".equalsIgnoreCase(plugin.getConfig().getString("parkour.guide", "pack"))) {
            if (run.guide == null) run.guide = new Guide(plugin, run.player);
            run.guide.update(run.current, run.next, ticks / 2);
            return;
        }
        if (run.guide != null) {
            run.guide.remove();
            run.guide = null;
        }
        Player player = run.player;
        Block next = run.next;
        World world = next.getWorld();
        double y = next.getY() + 1.02;
        for (double t = 0; t <= 1.0001; t += 0.25) {
            spark(player, world, next.getX() + t, y, next.getZ());
            spark(player, world, next.getX() + t, y, next.getZ() + 1);
            spark(player, world, next.getX(), y, next.getZ() + t);
            spark(player, world, next.getX() + 1, y, next.getZ() + t);
        }
        double x0 = run.current.getX() + 0.5, y0 = run.current.getY() + 1.1, z0 = run.current.getZ() + 0.5;
        double x1 = next.getX() + 0.5, y1 = next.getY() + 1.1, z1 = next.getZ() + 0.5;
        double lift = 1.1 + Math.max(0, y1 - y0) * 0.5;
        for (int i = 1; i < 10; i++) {
            double t = i / 10.0;
            double x = x0 + (x1 - x0) * t, z = z0 + (z1 - z0) * t;
            double yy = y0 + (y1 - y0) * t + lift * 4 * t * (1 - t);
            player.spawnParticle(Particle.DUST, new Location(world, x, yy, z), 1, 0, 0, 0, 0, ARC);
        }
    }

    private static void spark(Player player, World world, double x, double y, double z) {
        player.spawnParticle(Particle.DUST, new Location(world, x, y, z), 1, 0, 0, 0, 0, OUTLINE);
    }

    private Map.Entry<Component, List<Component>> sidebar(Run run) {
        Messages messages = plugin.messages();
        var args = net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                Messages.arg("score", run.score), Messages.arg("time", ScoreStore.duration(run.seconds())),
                Messages.arg("best", Math.max(run.bestBefore, run.score)),
                Messages.arg("record", Math.max(parkour.scores().record(), run.score)));
        List<Component> lines = new ArrayList<>();
        for (String line : messages.lines("parkour.sidebar.lines")) lines.add(messages.render(line, args));
        return new AbstractMap.SimpleEntry<>(messages.component("parkour.sidebar.title", args), lines);
    }

    // ---------------------------------------------------------------- ending

    /**
     * Ends a run: the blocks go, the score is saved, the lobby sidebar comes back. reason: the message key to send
     * (null for none). send: whether to put the player back at the parkour's return point.
     */
    public void end(Player player, String reason, boolean sendBack) {
        Run run = runs.remove(player.getUniqueId());
        if (run == null) return;
        if (run.guide != null) run.guide.remove();
        unplace(run.current);
        unplace(run.next);
        int seconds = run.seconds();
        boolean best = parkour.scores().finish(player.getUniqueId(), player.getName(), run.score, seconds);
        plugin.display().clearSidebarOverride(player);
        plugin.doubleJump().refresh(player);
        if (!player.isOnline()) return;

        Messages messages = plugin.messages();
        var args = net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                Messages.arg("score", run.score), Messages.arg("time", ScoreStore.duration(seconds)),
                Messages.arg("best", Math.max(run.bestBefore, run.score)));
        if (reason != null) messages.send(player, reason, args);
        if (best) {
            messages.send(player, "parkour.game.new-best", args);
            player.playSound(Sound.sound(Key.key("ui.toast.challenge_complete"), Sound.Source.MASTER, 0.5f, 1f), Sound.Emitter.self());
        } else if (reason != null) {
            player.playSound(Sound.sound(Key.key("block.note_block.bass"), Sound.Source.MASTER, 0.6f, 0.7f), Sound.Emitter.self());
        }
        if (best && parkour.scores().rank(player.getUniqueId()) == 1 && run.score > 0) {
            for (Player everyone : plugin.getServer().getOnlinePlayers()) {
                messages.send(everyone, "parkour.game.record", Messages.name("player", player.getName()), Messages.arg("score", run.score));
            }
        }
        if (sendBack) {
            Location back = parkour.returnPoint();
            teleport(player, back != null ? back : plugin.lobbyConfig().spawn().location());
            player.setFallDistance(0);
        }
    }

    private void teleport(Player player, Location to) {
        moving.add(player.getUniqueId());
        try {
            player.teleport(to);
        } finally {
            moving.remove(player.getUniqueId());
        }
    }

    /** Teleporting away (/spawn, a realm, an admin) ends the run without sending them back. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (moving.contains(event.getPlayer().getUniqueId()) || !playing(event.getPlayer())) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> end(event.getPlayer(), "parkour.game.left", false));
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        end(event.getPlayer(), null, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer(), null, false);
    }

    /** Plugin stopping: every run ends and every block goes. */
    public void shutdown() {
        if (task != null) task.cancel();
        for (Run run : new ArrayList<>(runs.values())) end(run.player, "parkour.game.left", true);
        for (Block block : new ArrayList<>(placed)) unplace(block);
    }

    private static float yawTowards(double fromX, double fromZ, double toX, double toZ) {
        return (float) Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
    }
}
