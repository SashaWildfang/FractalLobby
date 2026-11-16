/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.npc;

import com.sasha.lobby.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One NPC: a player-shaped mannequin with a skin, held items and armour, click actions, and its own hologram above
 * its head (which moves with it). Saved in npcs.yml.
 */
public final class Npc {

    /** The skin: a player name (looked up by the game), and/or the signed texture itself (value + signature). */
    public static final class Skin {
        public String name = "";
        public String url = "";
        public String value = "";
        public String signature = "";
        public boolean slim;
        /** A skin and cape from the resource pack (e.g. fractal:entity/npc/survival); the skin above is the fallback. */
        public String texture = "";
        public String cape = "";
    }

    public final String name;
    public Location location;
    public final Skin skin = new Skin();
    public final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
    public boolean glowing;
    public String glowColor = "light_purple";
    public boolean turnToPlayer = true;
    public double turnDistance = 8;
    public double scale = 1.0;
    public double cooldownSeconds = 1.0;
    public boolean collidable = true;
    /** Click type (ANY_CLICK, LEFT_CLICK, RIGHT_CLICK) → actions in order, like "send_to_server:survival". */
    public final Map<String, List<String>> actions = new LinkedHashMap<>();
    /** The hologram above its head: lines, and how far above the head the bottom line sits. */
    public final List<Hologram.Line> lines = new ArrayList<>();
    public double hologramOffset = 0.35;

    public Npc(String name, Location location) {
        this.name = name;
        this.location = location;
    }

    /** Height of the top of the head (for the hologram). */
    public double headHeight() {
        return 1.8 * scale;
    }

    /** The NPC of this name in the plugin's built-in npcs.yml (not the one on disk), or null. */
    public static Npc builtIn(org.bukkit.plugin.Plugin plugin, String name) {
        try (var in = plugin.getResource("npcs.yml")) {
            if (in == null) return null;
            var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            ConfigurationSection c = yaml.getConfigurationSection("npcs." + name);
            return c == null ? null : load(name, c);
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    /** Takes everything but the name and where it stands from another NPC. */
    public void copyLook(Npc from) {
        skin.name = from.skin.name; skin.url = from.skin.url; skin.value = from.skin.value; skin.signature = from.skin.signature;
        skin.slim = from.skin.slim; skin.texture = from.skin.texture; skin.cape = from.skin.cape;
        equipment.clear(); equipment.putAll(from.equipment);
        glowing = from.glowing; glowColor = from.glowColor; turnToPlayer = from.turnToPlayer; turnDistance = from.turnDistance;
        scale = from.scale; cooldownSeconds = from.cooldownSeconds; collidable = from.collidable;
        actions.clear();
        from.actions.forEach((k, v) -> actions.put(k, new ArrayList<>(v)));
        lines.clear(); lines.addAll(from.lines);
        hologramOffset = from.hologramOffset;
    }

    static Npc load(String name, ConfigurationSection c) {
        Npc npc = new Npc(name, readLocation(c.getConfigurationSection("location")));
        ConfigurationSection skin = c.getConfigurationSection("skin");
        if (skin != null) {
            npc.skin.name = skin.getString("name", "");
            npc.skin.url = skin.getString("url", "");
            npc.skin.value = skin.getString("value", "");
            npc.skin.signature = skin.getString("signature", "");
            npc.skin.slim = skin.getBoolean("slim", false);
            npc.skin.texture = skin.getString("texture", "");
            npc.skin.cape = skin.getString("cape", "");
        }
        ConfigurationSection equipment = c.getConfigurationSection("equipment");
        if (equipment != null) {
            for (String key : equipment.getKeys(false)) {
                EquipmentSlot slot = slot(key);
                ItemStack item = equipment.getItemStack(key);
                if (slot != null && item != null) npc.equipment.put(slot, item);
            }
        }
        npc.glowing = c.getBoolean("glowing", false);
        npc.glowColor = c.getString("glow-color", "light_purple");
        npc.turnToPlayer = c.getBoolean("turn-to-player", true);
        npc.turnDistance = c.getDouble("turn-distance", 8);
        npc.scale = Math.max(0.1, Math.min(5, c.getDouble("scale", 1.0)));
        npc.cooldownSeconds = c.getDouble("cooldown", 1.0);
        npc.collidable = c.getBoolean("collidable", true);
        ConfigurationSection actions = c.getConfigurationSection("actions");
        if (actions != null) {
            for (String click : actions.getKeys(false)) {
                List<String> list = actions.getStringList(click);
                if (!list.isEmpty()) npc.actions.put(click.toUpperCase(Locale.ROOT), new ArrayList<>(list));
            }
        }
        npc.hologramOffset = c.getDouble("hologram.offset", 0.35);
        for (Map<?, ?> raw : c.getMapList("hologram.lines")) {
            String content = String.valueOf(raw.get("content") == null ? "" : raw.get("content"));
            double height = raw.get("height") instanceof Number number ? number.doubleValue() : Hologram.TEXT_HEIGHT;
            npc.lines.add(new Hologram.Line(content, height));
        }
        return npc;
    }

    void save(ConfigurationSection c) {
        if (location != null) {
            c.set("location.world", location.getWorld().getName());
            c.set("location.x", location.getX());
            c.set("location.y", location.getY());
            c.set("location.z", location.getZ());
            c.set("location.yaw", (double) location.getYaw());
            c.set("location.pitch", (double) location.getPitch());
        }
        c.set("skin.name", skin.name);
        c.set("skin.url", skin.url);
        c.set("skin.value", skin.value);
        c.set("skin.signature", skin.signature);
        c.set("skin.slim", skin.slim);
        c.set("skin.texture", skin.texture);
        c.set("skin.cape", skin.cape);
        c.set("equipment", null);
        for (Map.Entry<EquipmentSlot, ItemStack> entry : equipment.entrySet()) {
            c.set("equipment." + entry.getKey().name(), entry.getValue());
        }
        c.set("glowing", glowing);
        c.set("glow-color", glowColor);
        c.set("turn-to-player", turnToPlayer);
        c.set("turn-distance", turnDistance);
        c.set("scale", scale);
        c.set("cooldown", cooldownSeconds);
        c.set("collidable", collidable);
        c.set("actions", null);
        for (Map.Entry<String, List<String>> entry : actions.entrySet()) c.set("actions." + entry.getKey(), entry.getValue());
        c.set("hologram.offset", hologramOffset);
        List<Map<String, Object>> raw = new ArrayList<>();
        for (Hologram.Line line : lines) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("content", line.content);
            map.put("height", line.height);
            raw.add(map);
        }
        c.set("hologram.lines", raw);
    }

    static Location readLocation(ConfigurationSection c) {
        if (c == null) return null;
        World world = Bukkit.getWorld(c.getString("world", "world"));
        if (world == null) return null;
        return new Location(world, c.getDouble("x"), c.getDouble("y"), c.getDouble("z"),
                (float) c.getDouble("yaw"), (float) c.getDouble("pitch"));
    }

    /** MAINHAND, OFFHAND, HEAD, CHEST, LEGS, FEET (also hand, helmet, chestplate, leggings, boots). */
    public static EquipmentSlot slot(String name) {
        return switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "MAINHAND", "MAIN_HAND", "HAND" -> EquipmentSlot.HAND;
            case "OFFHAND", "OFF_HAND" -> EquipmentSlot.OFF_HAND;
            case "HEAD", "HELMET" -> EquipmentSlot.HEAD;
            case "CHEST", "CHESTPLATE" -> EquipmentSlot.CHEST;
            case "LEGS", "LEGGINGS" -> EquipmentSlot.LEGS;
            case "FEET", "BOOTS" -> EquipmentSlot.FEET;
            default -> null;
        };
    }
}
