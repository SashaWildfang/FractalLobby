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
import java.util.regex.Pattern;

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
        boolean tablistSortByRank,
        JoinItems joinItems,
        Selector selector,
        Border border,
        FlyingMinecarts flyingMinecarts,
        ChatColors chatColors,
        Parkour parkour,
        Ranks ranks,
        Mentions mentions,
        DoubleJump doubleJump
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

    public record Protection(String buildPermission, boolean allowOpsToBuild,
                             boolean disableBuilding, boolean disableBlockInteract, List<Pattern> allowInteract,
                             boolean disableEntityInteract, boolean disableTrampling, boolean disableItemPickup,
                             boolean disableProjectiles, boolean disablePvp, boolean disableMobAttacks,
                             boolean disableDamage, boolean disableFallDamage, boolean disableHunger,
                             boolean disablePlayerCollision,
                             boolean disableExplosions, boolean disableFireSpread, boolean disableMobSpawning,
                             boolean freezeBlocks, boolean disableLiquidFlow, boolean disableWeather, long lockTime) {

        /** Whether a block is on the allow-interact list (names like OAK_DOOR, or patterns like *_DOOR). */
        public boolean interactAllowed(Material block) {
            for (Pattern pattern : allowInteract) if (pattern.matcher(block.name()).matches()) return true;
            return false;
        }
    }

    /** The ajParkour leaderboard: how many names the hologram shows, and how far down to look for a player's rank. */
    public record Parkour(int hologramTop, int rankSearch) {
    }

    public record ChatColors(boolean enabled, List<ChatColor> colors) {
    }

    /** color: a hex colour, a colour name, "rainbow" or "gradient:#AAAAAA:#BBBBBB". permission "" = everyone. */
    public record ChatColor(String id, String name, String color, Material item, String permission) {
    }

    public record VoidRescue(boolean enabled, double belowY, String sound, float volume, float pitch) {
    }

    public record LaunchPads(boolean enabled, Material block, double forward, double upward,
                             String sound, float volume, float pitch) {
    }

    /** Who counts as staff and as a donor (for settings like "Donors Only", and join sounds). */
    public record Ranks(String donorPermission, String staffPermission) {
    }

    /** Saying a player's name pings them; @Name also highlights it (highlight = MiniMessage tags). */
    public record Mentions(boolean enabled, String sound, float volume, float pitch, String highlight) {
    }

    public record DoubleJump(boolean enabled, double forward, double upward, String sound, float volume, float pitch) {
    }

    public record JoinMessages(String firstJoin, String join, String quit,
                               String staffJoinSound, String staffLeaveSound,
                               String donorJoinSound, String donorLeaveSound, float soundVolume) {
    }

    public record Chat(boolean enabled, String defaultFormat, String rankedFormat) {
    }

    public record Nametag(boolean enabled, String format, boolean applyToTablist) {
    }

    public record Proxy(boolean enabled, long refreshTicks, List<String> servers) {
    }

    public record Scoreboard(boolean enabled, int updateTicks, DateTimeFormatter dateFormat, ZoneId timeZone) {
    }

    public record JoinItems(boolean clearInventory, boolean lockInventory, boolean blockOffHand,
                            boolean selector, int selectorSlot, Material selectorItem,
                            boolean radio, int radioSlot, Material radioItem, String radioCommand, String radioRequiresPlugin,
                            boolean visibility, int visibilitySlot, Material visibleItem, Material hiddenItem,
                            long visibilityCooldownMillis,
                            boolean settings, int settingsSlot, Material settingsItem) {
    }

    public record Border(boolean enabled, double radius, int warningDistance) {
    }

    public record FlyingMinecarts(double speed, int despawnSeconds, boolean explodeOnCollision) {
    }

    public record Selector(int rows, List<SelectorServer> servers,
                           boolean statusEnabled, long statusRefreshTicks, List<String> maintenanceWords) {
    }

    /** address: "" = ask the proxy. maintenance: shown as under maintenance whatever the ping says. */
    public record SelectorServer(String server, int slot, Material item, String name, List<String> description,
                                 String address, boolean maintenance) {
    }

    public static LobbyConfig load(FileConfiguration c, Logger log) {
        return new LobbyConfig(
                new Spawn(
                        c.getString("spawn.world", "world"),
                        c.getDouble("spawn.x", -46.5), c.getDouble("spawn.y", 103), c.getDouble("spawn.z", 892.5),
                        (float) c.getDouble("spawn.yaw"), (float) c.getDouble("spawn.pitch"),
                        c.getBoolean("spawn.teleport-on-join", true)),
                new Protection(
                        c.getString("protection.build-permission", "lobby.build"),
                        c.getBoolean("protection.allow-ops-to-build", true),
                        c.getBoolean("protection.disable-building", true),
                        c.getBoolean("protection.disable-block-interact", true),
                        patterns(c.getStringList("protection.allow-interact")),
                        c.getBoolean("protection.disable-entity-interact", true),
                        c.getBoolean("protection.disable-trampling", true),
                        c.getBoolean("protection.disable-item-pickup", true),
                        c.getBoolean("protection.disable-projectiles", true),
                        c.getBoolean("protection.disable-pvp", true),
                        c.getBoolean("protection.disable-mob-attacks", true),
                        c.getBoolean("protection.disable-damage", true),
                        c.getBoolean("protection.disable-fall-damage", true),
                        c.getBoolean("protection.disable-hunger", true),
                        c.getBoolean("protection.disable-player-collision", true),
                        c.getBoolean("protection.disable-explosions", true),
                        c.getBoolean("protection.disable-fire-spread", true),
                        c.getBoolean("protection.disable-mob-spawning", true),
                        c.getBoolean("protection.freeze-blocks", true),
                        c.getBoolean("protection.disable-liquid-flow", false),
                        c.getBoolean("protection.disable-weather", true),
                        c.getLong("protection.lock-time", -1)),
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
                        c.getString("join-messages.quit-message", ""),
                        sound(c.getString("join-messages.sounds.staff.join", "block.beacon.activate")),
                        sound(c.getString("join-messages.sounds.staff.leave", "block.beacon.deactivate")),
                        sound(c.getString("join-messages.sounds.donor.join", "entity.player.levelup")),
                        sound(c.getString("join-messages.sounds.donor.leave", "block.note_block.bass")),
                        (float) c.getDouble("join-messages.sounds.volume", 0.6)),
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
                        Math.max(5L, Math.round(c.getDouble("proxy.refresh-seconds", 1) * 20)),
                        c.getStringList("proxy.servers").stream().map(String::trim).filter(s -> !s.isEmpty()).toList()),
                new Scoreboard(
                        c.getBoolean("scoreboard.enabled", true),
                        Math.max(1, c.getInt("scoreboard.update-ticks", 10)),
                        dateFormat(c.getString("scoreboard.date-format", "dd/MM/yy"), log),
                        timeZone(c.getString("scoreboard.time-zone", ""), log)),
                c.getBoolean("tablist.enabled", true),
                c.getBoolean("tablist.sort-by-rank", true),
                new JoinItems(
                        c.getBoolean("join-items.clear-inventory", true),
                        c.getBoolean("join-items.lock-inventory", true),
                        c.getBoolean("join-items.block-off-hand", true),
                        c.getBoolean("join-items.realm-selector.enabled", true),
                        slot(c.getInt("join-items.realm-selector.slot", 2)),
                        item(c.getString("join-items.realm-selector.item"), Material.COMPASS, "join-items.realm-selector.item", log),
                        c.getBoolean("join-items.radio.enabled", true),
                        slot(c.getInt("join-items.radio.slot", 5)),
                        item(c.getString("join-items.radio.item"), Material.JUKEBOX, "join-items.radio.item", log),
                        c.getString("join-items.radio.command", "radio").trim().replaceFirst("^/", ""),
                        c.getString("join-items.radio.requires-plugin", "Radio").trim(),
                        c.getBoolean("join-items.player-visibility.enabled", true),
                        slot(c.getInt("join-items.player-visibility.slot", 9)),
                        item(c.getString("join-items.player-visibility.item-visible"), Material.LIME_DYE, "join-items.player-visibility.item-visible", log),
                        item(c.getString("join-items.player-visibility.item-hidden"), Material.GRAY_DYE, "join-items.player-visibility.item-hidden", log),
                        Math.round(Math.max(0, c.getDouble("join-items.player-visibility.cooldown-seconds", 1.5)) * 1000),
                        c.getBoolean("join-items.settings.enabled", true),
                        slot(c.getInt("join-items.settings.slot", 8)),
                        item(c.getString("join-items.settings.item"), Material.COMPARATOR, "join-items.settings.item", log)),
                selector(c, log),
                new Border(
                        c.getBoolean("border.enabled", true),
                        Math.max(5, c.getDouble("border.radius", 100)),
                        Math.max(0, c.getInt("border.warning-distance", 5))),
                new FlyingMinecarts(
                        Math.max(0.1, Math.min(3.0, c.getDouble("flying-minecarts.speed", 1.0))),
                        Math.max(5, c.getInt("flying-minecarts.despawn-after-seconds", 30)),
                        c.getBoolean("flying-minecarts.explode-on-collision", true)),
                chatColors(c, log),
                new Parkour(
                        Math.max(0, Math.min(10, c.getInt("parkour.hologram-top", 5))),
                        Math.max(10, Math.min(500, c.getInt("parkour.rank-search", 100)))),
                new Ranks(
                        c.getString("ranks.donor-permission", "lobby.donor"),
                        c.getString("ranks.staff-permission", "lobby.staff")),
                new Mentions(
                        c.getBoolean("mentions.enabled", true),
                        sound(c.getString("mentions.sound", "block.note_block.pling")),
                        (float) c.getDouble("mentions.volume", 1.0),
                        (float) c.getDouble("mentions.pitch", 1.4),
                        c.getString("mentions.highlight", "<#FFD27F><bold>")),
                new DoubleJump(
                        c.getBoolean("double-jump.enabled", true),
                        c.getDouble("double-jump.forward", 1.3),
                        c.getDouble("double-jump.upward", 0.9),
                        sound(c.getString("double-jump.sound", "entity.breeze.jump")),
                        (float) c.getDouble("double-jump.sound-volume", 0.6),
                        (float) c.getDouble("double-jump.sound-pitch", 1.2)));
    }

    private static ChatColors chatColors(FileConfiguration c, Logger log) {
        List<ChatColor> colors = new ArrayList<>();
        ConfigurationSection section = c.getConfigurationSection("chat-color.colors");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String path = "chat-color.colors." + id;
                String color = c.getString(path + ".color", "");
                if (!com.sasha.lobby.items.ChatColors.valid(color)) {
                    log.warning("config.yml " + path + ".color: '" + color + "' is not a colour; skipping " + id);
                    continue;
                }
                colors.add(new ChatColor(id, c.getString(path + ".name", id), color,
                        item(c.getString(path + ".item"), Material.WHITE_DYE, path + ".item", log),
                        c.getString(path + ".permission", "").trim()));
            }
        }
        return new ChatColors(c.getBoolean("chat-color.enabled", true), List.copyOf(colors));
    }

    /** Block names with * wildcards → patterns. */
    static List<Pattern> patterns(List<String> names) {
        List<Pattern> patterns = new ArrayList<>();
        for (String name : names) {
            String glob = name.trim().toUpperCase(Locale.ROOT);
            if (glob.isEmpty()) continue;
            patterns.add(Pattern.compile(Pattern.quote(glob).replace("*", "\\E.*\\Q")));
        }
        return List.copyOf(patterns);
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
                        c.getStringList(path + ".description"),
                        c.getString(path + ".address", "").trim(),
                        c.getBoolean(path + ".maintenance", false)));
            }
        }
        List<String> words = c.isList("realm-selector.status.maintenance-words")
                ? c.getStringList("realm-selector.status.maintenance-words") : List.of("maintenance");
        return new Selector(rows, List.copyOf(servers),
                c.getBoolean("realm-selector.status.enabled", true),
                Math.max(20L, Math.round(c.getDouble("realm-selector.status.refresh-seconds", 3) * 20)),
                List.copyOf(words));
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
