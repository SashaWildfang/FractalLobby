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
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * The Mystery Vault: a glowing chest in the lobby (fractal:mystery/vault_closed, _open) with a hologram showing each
 * player their own boxes and dust. Right-clicking it opens the Mystery Vault menu; boxes are opened on top of it, one
 * at a time. Set with /mysterybox vault set (kept in mystery-vault.yml). Without a vault, boxes open in front of the
 * player wherever they are.
 */
public final class Vault implements Listener {

    static final String TAG = "lobby_mystery_vault";
    private static final String HOLOGRAM = "mystery-vault";
    private static final Key CLOSED = Key.key("fractal", "mystery/vault_closed"), OPEN = Key.key("fractal", "mystery/vault_open");

    private final Lobby plugin;
    private final MysteryBoxes boxes;
    private final File file;
    private Location location;
    private ItemDisplay chest;
    private Interaction clickBox;
    /** The opening playing on the vault right now, and who it's for. */
    private UUID busyFor;
    private String busyName = "";

    Vault(Lobby plugin, MysteryBoxes boxes) {
        this.plugin = plugin;
        this.boxes = boxes;
        this.file = new File(plugin.getDataFolder(), "mystery-vault.yml");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** (Re)builds the vault where mystery-vault.yml says. */
    void start() {
        stop();
        clearStray();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        location = yaml.getLocation("location");
        if (location == null || location.getWorld() == null || !boxes.enabled()) return;
        World world = location.getWorld();
        chest = world.spawn(location.clone().add(0, 0.5, 0), ItemDisplay.class, display -> {
            display.setItemStack(model(CLOSED));
            display.setPersistent(false);
            display.addScoreboardTag(TAG);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1f), new AxisAngle4f()));
        });
        clickBox = world.spawn(location.clone(), Interaction.class, box -> {
            box.setInteractionWidth(1.2f);
            box.setInteractionHeight(1.2f);
            box.setResponsive(true);
            box.setPersistent(false);
            box.addScoreboardTag(TAG);
        });
        showHologram();
    }

    void stop() {
        if (chest != null) chest.remove();
        if (clickBox != null) clickBox.remove();
        chest = null;
        clickBox = null;
        plugin.holograms().detach(HOLOGRAM);
    }

    private void clearStray() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(TAG) || entity.getScoreboardTags().contains(Opening.TAG)) entity.remove();
            }
        }
    }

    /** The vault's hologram: the title, the viewer's boxes and dust, and how to open it. */
    private void showHologram() {
        if (location == null) return;
        Hologram holo = new Hologram(HOLOGRAM, location.clone().add(0, 1.35, 0));
        holo.downOrigin = true;
        holo.updateInterval = 10;
        Hologram.Page page = new Hologram.Page();
        for (String line : plugin.messages().lines("mystery.vault.hologram")) page.lines.add(new Hologram.Line(line, Hologram.TEXT_HEIGHT));
        holo.pages.add(page);
        plugin.holograms().attach(HOLOGRAM, holo);
    }

    /** /mysterybox vault set: the vault goes on the block the admin stands on. */
    public void set(Player admin) {
        Location at = admin.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        at.setYaw(Math.round(admin.getLocation().getYaw() / 90f) * 90f + 180f);
        at.setPitch(0);
        save(at);
        start();
        plugin.messages().send(admin, "mystery.vault.set");
    }

    /** /mysterybox vault remove */
    public void remove(org.bukkit.command.CommandSender admin) {
        save(null);
        start();
        plugin.messages().send(admin, "mystery.vault.removed");
    }

    private void save(Location at) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("location", at);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't save mystery-vault.yml: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- opening on the vault

    /**
     * Where a box the player opens now plays: on the vault (if they're near it and it's free), or with no vault set,
     * just in front of them. Null (after telling them why) when it can't be opened now.
     */
    Location openingSpot(Player player) {
        if (busyFor != null && busyFor.equals(player.getUniqueId())) {
            plugin.messages().send(player, "mystery.already-opening");
            return null;
        }
        if (location == null) {
            Location front = player.getLocation().clone();
            front.setPitch(0);
            Location spot = front.add(front.getDirection().multiply(2.5));
            spot.setY(player.getLocation().getY());
            return spot;
        }
        if (!player.getWorld().equals(location.getWorld()) || player.getLocation().distanceSquared(location) > 14 * 14) {
            plugin.messages().send(player, "mystery.vault.too-far");
            return null;
        }
        if (busyFor != null) {
            plugin.messages().send(player, "mystery.vault.busy", Messages.name("player", busyName));
            return null;
        }
        return location.clone().add(0, 1.0, 0); // the top of the vault
    }

    /** An opening starts on the vault: it's busy, its lid opens, its hologram makes room. */
    void busy(Player player, boolean onVault) {
        if (!onVault) return;
        busyFor = player.getUniqueId();
        busyName = player.getName();
        if (chest != null) chest.setItemStack(model(OPEN));
        plugin.holograms().detach(HOLOGRAM);
    }

    void free(boolean onVault) {
        if (!onVault) return;
        busyFor = null;
        if (chest != null && chest.isValid()) chest.setItemStack(model(CLOSED));
        showHologram();
    }

    boolean isVault(Location spot) {
        return location != null && spot.getWorld().equals(location.getWorld()) && spot.distanceSquared(location.clone().add(0, 1, 0)) < 0.01;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEntityEvent event) {
        if (clickBox == null || !event.getRightClicked().equals(clickBox)) return;
        event.setCancelled(true);
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        MysteryBoxes.sound(event.getPlayer(), "block.ender_chest.open", 1.3f);
        boxes.openMenu(event.getPlayer());
    }

    private static ItemStack model(Key key) {
        ItemStack item = ItemStack.of(Material.CHEST);
        item.setData(DataComponentTypes.ITEM_MODEL, key);
        return item;
    }
}
