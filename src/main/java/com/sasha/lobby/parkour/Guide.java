package com.sasha.lobby.parkour;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * The runner's guide drawn with the lobby resource pack, which only the runner sees:
 * <ul>
 *   <li>a straight dotted line from the block they're on to the next one (fractal:parkour/dot), with a wave of light
 *   running along it;</li>
 *   <li>a ghost outline of the block after that (fractal:parkour/ghost), so they can plan the next jump.</li>
 * </ul>
 * Display entities, updated every 2 game ticks with matching interpolation, so everything moves smoothly. The next
 * block itself is marked by the dotted line ending on it.
 */
final class Guide {

    static final String TAG = "lobby_parkour_guide";
    private static final int DOTS = 7;
    private static final Key DOT = Key.key("fractal", "parkour/dot");
    private static final Key GHOST = Key.key("fractal", "parkour/ghost");
    /** The dots' size, and how much the wave along them grows and shrinks them. */
    private static final float DOT_SIZE = 0.2f, DOT_WAVE = 0.06f;

    private final Plugin plugin;
    private final Player player;
    private ItemDisplay ghost;
    private final List<ItemDisplay> dots = new ArrayList<>();
    private Block shownFor;
    private Block ghostFor;

    Guide(Plugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    /** Called every 2 game ticks: the guide for the jump from {@code from} to {@code to}, with {@code after} (or null) next. */
    void update(Block from, Block to, Block after, long step) {
        if (dots.isEmpty() || !dots.getFirst().isValid() || dots.getFirst().getWorld() != to.getWorld()) {
            removeDots();
            for (int i = 0; i < DOTS; i++) dots.add(spawn(line(from, to, i), DOT, Display.Billboard.CENTER, DOT_SIZE));
            shownFor = to;
        } else if (!to.equals(shownFor)) {
            for (int i = 0; i < dots.size(); i++) dots.get(i).teleport(line(from, to, i));
            shownFor = to;
        }

        // The ghost of the block after: shown, moved, or taken away.
        if (after == null) {
            if (ghost != null) ghost.remove();
            ghost = null;
            ghostFor = null;
        } else if (ghost == null || !ghost.isValid()) {
            ghost = spawn(after.getLocation().add(0.5, 0.5, 0.5), GHOST, Display.Billboard.FIXED, 1f);
            ghostFor = after;
        } else if (!after.equals(ghostFor)) {
            ghost.teleport(after.getLocation().add(0.5, 0.5, 0.5));
            ghostFor = after;
        }

        // A wave of light runs along the dots towards the next block.
        for (int i = 0; i < dots.size(); i++) {
            double wave = Math.sin(step * 0.6 - i * 0.9);
            set(dots.get(i), new Vector3f(), new Vector3f((float) (DOT_SIZE + DOT_WAVE * wave)), 2);
        }
        // The ghost breathes, very gently.
        if (ghost != null && step % 10 == 0) {
            set(ghost, new Vector3f(), new Vector3f((step / 10) % 2 == 0 ? 1.03f : 0.97f), 20);
        }
    }

    void remove() {
        if (ghost != null) ghost.remove();
        ghost = null;
        ghostFor = null;
        removeDots();
    }

    private void removeDots() {
        for (ItemDisplay dot : dots) dot.remove();
        dots.clear();
        shownFor = null;
    }

    /** Where dot {@code i} sits on the straight line from one block's top to the other's. */
    private static Location line(Block from, Block to, int i) {
        double x0 = from.getX() + 0.5, y0 = from.getY() + 1.12, z0 = from.getZ() + 0.5;
        double x1 = to.getX() + 0.5, y1 = to.getY() + 1.12, z1 = to.getZ() + 0.5;
        double t = (i + 1) / (double) (DOTS + 1);
        return new Location(to.getWorld(), x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t);
    }

    private ItemDisplay spawn(Location at, Key model, Display.Billboard billboard, float scale) {
        return at.getWorld().spawn(at, ItemDisplay.class, display -> {
            ItemStack item = ItemStack.of(Material.PAPER);
            item.setData(DataComponentTypes.ITEM_MODEL, model);
            display.setItemStack(item);
            display.setBillboard(billboard);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTeleportDuration(3);
            display.setViewRange(2f);
            display.setPersistent(false);
            display.setVisibleByDefault(false);
            display.addScoreboardTag(TAG);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale), new AxisAngle4f()));
            player.showEntity(plugin, display);
        });
    }

    /** Moves a display to this offset and size over {@code ticks}. */
    private static void set(ItemDisplay display, Vector3f offset, Vector3f scale, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(new Transformation(offset, new AxisAngle4f(), scale, new AxisAngle4f()));
    }

    /** Guide entities left over from before (a crash, a reload). */
    static void clearStray(Plugin plugin) {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ItemDisplay.class)) {
                if (entity.getScoreboardTags().contains(TAG)) entity.remove();
            }
        }
    }
}
