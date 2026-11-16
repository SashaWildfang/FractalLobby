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
 * The runner's guide drawn with the lobby resource pack: an arrow bobbing above the next block, pointing down at it
 * (fractal:parkour/arrow), and a row of small glowing orbs along the jump (fractal:parkour/dot); only the runner can
 * see them. A wave runs along the orbs towards the next block. Display entities, so they move smoothly between jumps.
 */
final class Guide {

    static final String TAG = "lobby_parkour_guide";
    private static final int DOTS = 9;
    private static final Key ARROW = Key.key("fractal", "parkour/arrow");
    /** The orbs' size, and how much the wave along them grows and shrinks them. */
    private static final float DOT_SIZE = 0.2f, DOT_WAVE = 0.06f;
    /** The arrow: its size, how high its middle is above the block's top, and how far it bobs. */
    private static final float ARROW_SIZE = 0.75f, ARROW_HEIGHT = 1.05f, ARROW_BOB = 0.3f;
    private static final Key DOT = Key.key("fractal", "parkour/dot");

    private final Plugin plugin;
    private final Player player;
    private ItemDisplay target;
    private final List<ItemDisplay> dots = new ArrayList<>();
    private Block shownFor;

    Guide(Plugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    /** Called every 4 game ticks: puts the guide on the jump from {@code from} to {@code to} and animates it. */
    void update(Block from, Block to, long step) {
        World world = to.getWorld();
        Location targetAt = to.getLocation().add(0.5, 1 + ARROW_HEIGHT, 0.5);
        if (target == null || !target.isValid() || target.getWorld() != world) {
            remove();
            target = spawn(targetAt, ARROW, Display.Billboard.VERTICAL);
            for (int i = 0; i < DOTS; i++) dots.add(spawn(arc(from, to, i), DOT, Display.Billboard.CENTER));
            shownFor = to;
        } else if (!to.equals(shownFor)) {
            target.teleport(targetAt);
            for (int i = 0; i < dots.size(); i++) dots.get(i).teleport(arc(from, to, i));
            shownFor = to;
        }
        // The arrow bobs up and down, half a second each way.
        if (step % 5 == 0) animate(target, ARROW_SIZE, (step / 5) % 2 == 0 ? ARROW_BOB : 0f, 10);
        // A wave of light runs along the orbs towards the next block.
        for (int i = 0; i < dots.size(); i++) {
            double wave = Math.sin(step * 0.9 - i * 0.8);
            animate(dots.get(i), (float) (DOT_SIZE + DOT_WAVE * wave), 0f, 4);
        }
    }

    void remove() {
        if (target != null) target.remove();
        for (ItemDisplay dot : dots) dot.remove();
        target = null;
        dots.clear();
        shownFor = null;
    }

    /** Where orb {@code i} sits on the arc from one block's top to the other's. */
    private static Location arc(Block from, Block to, int i) {
        double x0 = from.getX() + 0.5, y0 = from.getY() + 1.15, z0 = from.getZ() + 0.5;
        double x1 = to.getX() + 0.5, y1 = to.getY() + 1.15, z1 = to.getZ() + 0.5;
        double lift = 1.1 + Math.max(0, y1 - y0) * 0.5;
        double t = (i + 1) / (double) (DOTS + 1);
        double y = y0 + (y1 - y0) * t + lift * 4 * t * (1 - t);
        return new Location(to.getWorld(), x0 + (x1 - x0) * t, y, z0 + (z1 - z0) * t);
    }

    private ItemDisplay spawn(Location at, Key model, Display.Billboard billboard) {
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
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(model == DOT ? DOT_SIZE : ARROW_SIZE), new AxisAngle4f()));
            player.showEntity(plugin, display);
        });
    }

    /** Moves a display to this size and height (above where it stands) over {@code ticks}. */
    private static void animate(ItemDisplay display, float scale, float lift, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(new Transformation(new Vector3f(0, lift, 0), new AxisAngle4f(),
                new Vector3f(scale), new AxisAngle4f()));
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
