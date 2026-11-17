/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.mystery;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.hologram.Hologram;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Mystery Vaults: glowing chests in the lobby (fractal:mystery/vault_body with a separate lid,
 * fractal:mystery/vault_lid) with a hologram showing each player their own boxes and dust. Right-clicking one opens
 * the Mystery Vault menu; a box opened there plays on top of it, one at a time per vault, while the player stands in
 * front of it, facing it, held in place until the prize shows.
 *
 * <p>Any number of vaults: /mysterybox vault add (where you stand; players stand on the side you face),
 * /mysterybox vault remove (the nearest), /mysterybox vault list. Kept in mystery-vault.yml. The vaults rebuild
 * themselves if their chunk was unloaded. Without any vault, boxes open in front of the player.
 */
public final class Vault implements Listener {

    static final String TAG = "lobby_mystery_vault";
    private static final Key BODY = Key.key("fractal", "mystery/vault_body"), LID = Key.key("fractal", "mystery/vault_lid");
    /** How far in front of the vault the opener stands, and how close they must be to open a box there. */
    private static final double STAND = 2.6, REACH = 14;

    /** One vault: where it is (block centre, facing its front), its entities, and who is opening a box on it. */
    final class Spot {
        final int index;
        final Location location;
        ItemDisplay body, lid;
        Interaction clickBox;
        UUID busyFor;
        String busyName = "";

        Spot(int index, Location location) {
            this.index = index;
            this.location = location;
        }

        String hologramId() {
            return "mystery-vault-" + index;
        }

        /** Where the box plays: just above the open chest. */
        Location top() {
            return location.clone().add(0, 0.95, 0);
        }

        /** Where the opener stands: in front, on the ground, facing the vault. */
        Location stand() {
            Vector front = location.getDirection().setY(0).normalize().multiply(STAND);
            Location at = location.clone().add(front);
            at.setDirection(location.clone().add(0, 1.6, 0).toVector().subtract(at.clone().add(0, 1.62, 0).toVector()));
            return at;
        }

        boolean alive() {
            return body != null && body.isValid() && lid != null && lid.isValid() && clickBox != null && clickBox.isValid();
        }

        void spawn() {
            despawn();
            World world = location.getWorld();
            Location centre = location.clone().add(0, 0.5, 0);
            centre.setYaw(location.getYaw());   // the chest's front (lock) faces where players stand
            centre.setPitch(0);
            body = world.spawn(centre, ItemDisplay.class, display -> setup(display, model(BODY)));
            lid = world.spawn(centre, ItemDisplay.class, display -> setup(display, model(LID)));
            clickBox = world.spawn(location.clone(), Interaction.class, box -> {
                box.setInteractionWidth(1.1f);
                box.setInteractionHeight(1.25f);
                box.setResponsive(true);
                box.setPersistent(false);
                box.addScoreboardTag(TAG);
            });
            solid(true);
        }

        void despawn() {
            if (body != null) body.remove();
            if (lid != null) lid.remove();
            if (clickBox != null) clickBox.remove();
            body = null;
            lid = null;
            clickBox = null;
        }

        /** Invisible walls (barriers) where the vault stands, so nobody walks through it. */
        void solid(boolean on) {
            for (int dy = 0; dy <= 1; dy++) {
                org.bukkit.block.Block block = location.getBlock().getRelative(0, dy, 0);
                if (on && block.getType().isAir()) block.setType(Material.BARRIER, false);
                else if (!on && block.getType() == Material.BARRIER) block.setType(Material.AIR, false);
            }
        }

        /** The lid swings open on its hinge at the back (and the inside glows), or shuts again. */
        void lid(boolean open) {
            if (lid == null || !lid.isValid()) return;
            lid.setInterpolationDelay(0);
            lid.setInterpolationDuration(open ? 16 : 10);
            // hinge: the top back edge of the chest (model 8, 11, 3 -> 0, 0.1875, -0.3125 from the display's centre)
            org.joml.Matrix4f m = new org.joml.Matrix4f().translate(0, 0.1875f, -0.3125f)
                    .rotateX((float) Math.toRadians(open ? -105 : 0)).translate(0, -0.1875f, 0.3125f);
            lid.setTransformationMatrix(m);
        }
    }

    private final Lobby plugin;
    private final MysteryBoxes boxes;
    private final File file;
    private final List<Spot> spots = new ArrayList<>();
    /** Players opening a box on a vault: held in front of it (where they stand), facing it. */
    private final Map<UUID, Location> held = new HashMap<>();
    private BukkitTask watchdog;

    Vault(Lobby plugin, MysteryBoxes boxes) {
        this.plugin = plugin;
        this.boxes = boxes;
        this.file = new File(plugin.getDataFolder(), "mystery-vault.yml");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** (Re)builds the vaults from mystery-vault.yml. */
    void start() {
        stop();
        clearStray();
        if (!boxes.enabled()) return;
        int i = 0;
        for (Location location : load()) {
            if (location.getWorld() == null) continue;
            Spot spot = new Spot(i++, location);
            spots.add(spot);
            if (location.isChunkLoaded()) spot.spawn();
            showHologram(spot);
        }
        // Entities that aren't saved go when their chunk unloads: put them back once it's loaded again.
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Spot spot : spots) {
                if (spot.busyFor == null && !spot.alive() && spot.location.isChunkLoaded()) spot.spawn();
            }
        }, 40L, 40L);
    }

    void stop() {
        if (watchdog != null) watchdog.cancel();
        watchdog = null;
        for (Spot spot : spots) {
            spot.despawn();
            plugin.holograms().detach(spot.hologramId());
        }
        spots.clear();
        held.clear();
    }

    private void clearStray() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(TAG) || entity.getScoreboardTags().contains(Opening.TAG)) entity.remove();
            }
        }
    }

    private List<Location> load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Location> list = new ArrayList<>();
        Location old = yaml.getLocation("location"); // before there could be several
        if (old != null) list.add(old);
        for (Object entry : yaml.getList("vaults", List.of())) if (entry instanceof Location location) list.add(location);
        return list;
    }

    private void save(List<Location> list) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("The Mystery Vaults (/mysterybox vault add, remove, list)."));
        yaml.set("vaults", list);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't save mystery-vault.yml: " + e.getMessage());
        }
    }

    /** The vault's hologram: the title, the viewer's boxes and dust, and how to open it. */
    private void showHologram(Spot spot) {
        Hologram holo = new Hologram(spot.hologramId(), spot.location.clone().add(0, 1.55, 0));
        holo.downOrigin = true;
        holo.updateInterval = 10;
        Hologram.Page page = new Hologram.Page();
        for (String line : plugin.messages().lines("mystery.vault.hologram")) page.lines.add(new Hologram.Line(line, Hologram.TEXT_HEIGHT));
        holo.pages.add(page);
        plugin.holograms().attach(spot.hologramId(), holo);
    }

    // ---------------------------------------------------------------- admin

    /** /mysterybox vault add: a vault on the block the admin stands on; players will stand on the side they face. */
    public void add(Player admin) {
        Location at = admin.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        at.setYaw(Math.round(admin.getLocation().getYaw() / 90f) * 90f);
        at.setPitch(0);
        List<Location> list = new ArrayList<>(spots.stream().map(s -> s.location).toList());
        if (spots.isEmpty()) list = new ArrayList<>(load());
        list.add(at);
        save(list);
        start();
        plugin.messages().send(admin, "mystery.vault.added", Messages.arg("count", list.size()));
    }

    /** /mysterybox vault remove: the nearest vault (within 6 blocks). */
    public void remove(Player admin) {
        Spot nearest = null;
        for (Spot spot : spots) {
            if (!spot.location.getWorld().equals(admin.getWorld())) continue;
            if (nearest == null || spot.location.distanceSquared(admin.getLocation()) < nearest.location.distanceSquared(admin.getLocation())) {
                nearest = spot;
            }
        }
        if (nearest == null || nearest.location.distanceSquared(admin.getLocation()) > 36) {
            plugin.messages().send(admin, "mystery.vault.none-near");
            return;
        }
        Spot gone = nearest;
        gone.solid(false);
        save(spots.stream().filter(s -> s != gone).map(s -> s.location).toList());
        start();
        plugin.messages().send(admin, "mystery.vault.removed", Messages.arg("count", spots.size()));
    }

    /** /mysterybox vault list */
    public void list(org.bukkit.command.CommandSender sender) {
        if (spots.isEmpty()) {
            plugin.messages().send(sender, "mystery.vault.list-empty");
            return;
        }
        plugin.messages().send(sender, "mystery.vault.list-header", Messages.arg("count", spots.size()));
        for (Spot spot : spots) {
            Location l = spot.location;
            plugin.messages().send(sender, "mystery.vault.list-entry", Messages.arg("number", spot.index + 1),
                    Messages.arg("world", l.getWorld().getName()), Messages.arg("x", l.getBlockX()), Messages.arg("y", l.getBlockY()),
                    Messages.arg("z", l.getBlockZ()));
        }
    }

    // ---------------------------------------------------------------- opening

    /** Where a box the player opens now plays (with which vault, if any); null after telling them why it can't. */
    record Place(Location at, Spot spot) {
    }

    /** Reserves this vault for the player's opening; null after telling them why it can't. */
    Place reserve(Player player, Spot spot) {
        for (Spot other : spots) {
            if (player.getUniqueId().equals(other.busyFor)) {
                plugin.messages().send(player, "mystery.already-opening");
                return null;
            }
        }
        if (!spots.contains(spot)) {
            plugin.messages().send(player, "mystery.vault-only");
            return null;
        }
        if (spot.busyFor != null) {
            plugin.messages().send(player, "mystery.vault.busy", Messages.name("player", spot.busyName));
            return null;
        }
        if (!spot.alive()) spot.spawn();
        return new Place(spot.top(), spot);
    }

    /** An opening starts: the vault is busy, its lid lifts, its hologram makes room, the player is held in front. */
    void busy(Player player, Spot spot) {
        if (spot == null) return;
        spot.busyFor = player.getUniqueId();
        spot.busyName = player.getName();
        spot.lid(true);
        plugin.holograms().detach(spot.hologramId());
        Location stand = spot.stand();
        player.teleport(stand);
        held.put(player.getUniqueId(), stand);
    }

    /** The prize is out: the player can move again. */
    void release(Player player) {
        held.remove(player.getUniqueId());
    }

    void free(Player player, Spot spot) {
        release(player);
        if (spot == null) return;
        spot.busyFor = null;
        spot.lid(false);
        showHologram(spot);
    }

    /** Held players can look around, but not walk off (and are turned back if they look far away). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location stand = held.get(event.getPlayer().getUniqueId());
        if (stand == null) return;
        Location to = event.getTo();
        if (to.getX() != stand.getX() || to.getZ() != stand.getZ() || Math.abs(to.getY() - stand.getY()) > 0.05) {
            Location back = stand.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        held.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEntityEvent event) {
        for (Spot spot : spots) {
            if (spot.clickBox == null || !event.getRightClicked().equals(spot.clickBox)) continue;
            event.setCancelled(true);
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            if (spot.busyFor != null) {   // someone is opening a box here: nobody else can use it until it's done
                if (!event.getPlayer().getUniqueId().equals(spot.busyFor)) {
                    plugin.messages().send(event.getPlayer(), "mystery.vault.busy", Messages.name("player", spot.busyName));
                }
                return;
            }
            MysteryBoxes.sound(event.getPlayer(), "block.ender_chest.open", 1.3f);
            boxes.openMenu(event.getPlayer(), spot, 0);
            return;
        }
    }

    private void setup(ItemDisplay display, ItemStack item) {
        display.setItemStack(item);
        display.setPersistent(false);
        display.addScoreboardTag(TAG);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1f), new AxisAngle4f()));
        display.setInterpolationDuration(0);
    }

    private ItemStack model(Key key) {
        ItemStack item = ItemStack.of(key == BODY ? Material.CHEST : Material.OAK_SLAB);
        if (plugin.menuStyle().custom()) item.setData(DataComponentTypes.ITEM_MODEL, key);
        return item;
    }
}
