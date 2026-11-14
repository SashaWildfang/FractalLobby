/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/** A validated snapshot of config.yml, rebuilt on /lobby reload. */
public record LobbyConfig(
        Spawn spawn,
        Protection protection,
        VoidRescue voidRescue,
        LaunchPads launchPads,
        JoinMessages joinMessages,
        Chat chat,
        Nametag nametag,
        Proxy proxy,
        Scoreboard scoreboard,
        boolean tablist,
        JoinItems joinItems,
        Selector selector
) {

    public record Spawn(String world, double x, double y, double z, float yaw, float pitch, boolean teleportOnJoin) {
        /** The spawn point, or the world's own spawn if the configured world isn't loaded. */
        public Location location() {
            World w = Bukkit.getWorld(world);
            if (w == null) w = Bukkit.getWorlds().getFirst();
            if (!w.getName().equals(world)) return w.getSpawnLocation().toCenterLocation().subtract(0, 0.5, 0);
            return new Location(w, x, y, z, yaw, pitch);
        }
    }

    public record Protection(String buildPermission, boolean allowOpsToBuild, boolean disablePvp,
                             boolean disableMobAttacks, boolean disableFallDamage, boolean disableHunger) {
    }

    public record VoidRescue(boolean enabled, double belowY, String sound, float volume, float pitch) {
    }

    public record LaunchPads(boolean enabled, Material block, double forward, double upward,
                             String sound, float volume, float pitch) {
    }

    public record JoinMessages(String firstJoin, String join, String quit) {
    }

    public record Chat(boolean enabled, String defaultFormat, String rankedFormat) {
    }

    public record Nametag(boolean enabled, String format, boolean applyToTablist) {
    }

    public record Proxy(boolean enabled, int refreshSeconds, List<String> servers) {
    }

    public record Scoreboard(boolean enabled, int updateTicks, DateTimeFormatter dateFormat, ZoneId timeZone) {
    }

    public record JoinItems(boolean clearInventory, boolean lockInventory,
                            boolean selector, int selectorSlot, Material selectorItem,
                            boolean radio, int radioSlot, Material radioItem, String radioCommand, String radioRequiresPlugin,
                            boolean visibility, int visibilitySlot, Material visibleItem, Material hiddenItem,
                            long visibilityCooldownMillis) {
    }

    public record Selector(int rows, List<SelectorServer> servers) {
    }

    public record SelectorServer(String server, int slot, Material item, String name, List<String> description) {
    }

    public static LobbyConfig load(FileConfiguration c, Logger log) {
        return new LobbyConfig(
                new Spawn(
                        c.getString("spawn.world", "world"),
                        c.getDouble("spawn.x"), c.getDouble("spawn.y", 64), c.getDouble("spawn.z"),
                        (float) c.getDouble("spawn.yaw"), (float) c.getDouble("spawn.pitch"),
                        c.getBoolean("spawn.teleport-on-join", true)),
                new Protection(
                        c.getString("protection.build-permission", "lobby.build"),
                        c.getBoolean("protection.allow-ops-to-build", true),
                        c.getBoolean("protection.disable-pvp", true),
                        c.getBoolean("protection.disable-mob-attacks", true),
                        c.getBoolean("protection.disable-fall-damage", true),
                        c.getBoolean("protection.disable-hunger", true)),
                new VoidRescue(
                        c.getBoolean("void-rescue.enabled", true),
                        c.getDouble("void-rescue.below-y", 0),
                        sound(c.getString("void-rescue.sound", "entity.player.teleport")),
                        (float) c.getDouble("void-rescue.sound-volume", 0.6),
                        (float) c.getDouble("void-rescue.sound-pitch", 1.4)),
                new LaunchPads(
                        c.getBoolean("launch-pads.enabled", true),
                        block(c.getString("launch-pads.block"), Material.STONE_PRESSURE_PLATE, "launch-pads.block", log),
                        c.getDouble("launch-pads.forward", 2.4),
                        c.getDouble("launch-pads.upward", 1.0),
                        sound(c.getString("launch-pads.sound", "entity.firework_rocket.launch")),
                        (float) c.getDouble("launch-pads.sound-volume", 0.8),
                        (float) c.getDouble("launch-pads.sound-pitch", 1.2)),
                new JoinMessages(
                        c.getString("join-messages.first-join", ""),
                        c.getString("join-messages.join-message", ""),
                        c.getString("join-messages.quit-message", "")),
                new Chat(
                        c.getBoolean("chat.enabled", true),
                        c.getString("chat.default-format", "&7%player%: %message%"),
                        c.getString("chat.ranked-format", "%luckperms_prefix%%player%&f: %message%")),
                new Nametag(
                        c.getBoolean("nametag.enabled", true),
                        c.getString("nametag.format", "%luckperms_prefix%%player%"),
                        c.getBoolean("nametag.apply-to-tablist", true)),
                new Proxy(
                        c.getBoolean("proxy.enabled", true),
                        Math.max(1, c.getInt("proxy.refresh-seconds", 2)),
                        c.getStringList("proxy.servers").stream().map(String::trim).filter(s -> !s.isEmpty()).toList()),
                new Scoreboard(
                        c.getBoolean("scoreboard.enabled", true),
                        Math.max(1, c.getInt("scoreboard.update-ticks", 10)),
                        dateFormat(c.getString("scoreboard.date-format", "dd/MM/yy"), log),
                        timeZone(c.getString("scoreboard.time-zone", ""), log)),
                c.getBoolean("tablist.enabled", true),
                new JoinItems(
                        c.getBoolean("join-items.clear-inventory", true),
                        c.getBoolean("join-items.lock-inventory", true),
                        c.getBoolean("join-items.realm-selector.enabled", true),
                        slot(c.getInt("join-items.realm-selector.slot", 2)),
                        item(c.getString("join-items.realm-selector.item"), Material.COMPASS, "join-items.realm-selector.item", log),
                        c.getBoolean("join-items.radio.enabled", true),
                        slot(c.getInt("join-items.radio.slot", 5)),
                        item(c.getString("join-items.radio.item"), Material.JUKEBOX, "join-items.radio.item", log),
                        c.getString("join-items.radio.command", "radio").trim().replaceFirst("^/", ""),
                        c.getString("join-items.radio.requires-plugin", "Radio").trim(),
                        c.getBoolean("join-items.player-visibility.enabled", true),
                        slot(c.getInt("join-items.player-visibility.slot", 8)),
                        item(c.getString("join-items.player-visibility.item-visible"), Material.LIME_DYE, "join-items.player-visibility.item-visible", log),
                        item(c.getString("join-items.player-visibility.item-hidden"), Material.GRAY_DYE, "join-items.player-visibility.item-hidden", log),
                        Math.round(Math.max(0, c.getDouble("join-items.player-visibility.cooldown-seconds", 1.5)) * 1000)),
                selector(c, log));
    }

    private static Selector selector(FileConfiguration c, Logger log) {
        int rows = Math.max(1, Math.min(6, c.getInt("realm-selector.rows", 3)));
        List<SelectorServer> servers = new ArrayList<>();
        ConfigurationSection section = c.getConfigurationSection("realm-selector.realms");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String path = "realm-selector.realms." + id;
                int slot = c.getInt(path + ".slot", -1);
                if (slot < 0 || slot >= rows * 9) {
                    log.warning("config.yml " + path + ".slot must be 0 to " + (rows * 9 - 1) + "; skipping " + id);
                    continue;
                }
                servers.add(new SelectorServer(
                        c.getString(path + ".server", id),
                        slot,
                        item(c.getString(path + ".item"), Material.GRASS_BLOCK, path + ".item", log),
                        c.getString(path + ".name", id),
                        c.getStringList(path + ".description")));
            }
        }
        return new Selector(rows, List.copyOf(servers));
    }

    /** Hotbar slot 1-9 in the config → inventory index 0-8. */
    private static int slot(int configured) {
        return Math.max(1, Math.min(9, configured)) - 1;
    }

    private static String sound(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static Material item(String name, Material fallback, String path, Logger log) {
        Material material = name == null ? null : Material.matchMaterial(name.trim());
        if (material == null || !material.isItem()) {
            log.warning("config.yml " + path + ": '" + name + "' is not an item, using " + fallback.name());
            return fallback;
        }
        return material;
    }

    private static Material block(String name, Material fallback, String path, Logger log) {
        Material material = name == null ? null : Material.matchMaterial(name.trim());
        if (material == null || !material.isBlock()) {
            log.warning("config.yml " + path + ": '" + name + "' is not a block, using " + fallback.name());
            return fallback;
        }
        return material;
    }

    private static DateTimeFormatter dateFormat(String pattern, Logger log) {
        try {
            return DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException e) {
            log.warning("config.yml scoreboard.date-format is not a valid pattern, using dd/MM/yy");
            return DateTimeFormatter.ofPattern("dd/MM/yy");
        }
    }

    private static ZoneId timeZone(String name, Logger log) {
        if (name == null || name.isBlank()) return ZoneId.systemDefault();
        try {
            return ZoneId.of(name.trim());
        } catch (RuntimeException e) {
            log.warning("config.yml scoreboard.time-zone '" + name + "' is not a time zone, using the server's");
            return ZoneId.systemDefault();
        }
    }
}
