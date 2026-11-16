/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.npc;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.sasha.lobby.Lobby;
import com.sasha.lobby.hologram.Hologram;
import com.sasha.lobby.hologram.HologramText;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.entity.LookAnchor;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The lobby's NPCs (replacing FancyNpcs): player-shaped mannequins with skins, items, click actions and a hologram
 * above their head that moves with them. Kept in npcs.yml; the first time, FancyNpcs' NPCs are brought over (and a
 * hologram with the same name as an NPC becomes that NPC's own).
 */
public final class Npcs implements Listener {

    private static final String TAG = "lobby_npc";

    private final Lobby plugin;
    private final File file;
    private final Map<String, Npc> npcs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Mannequin> entities = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private BukkitTask task;
    private long ticks;

    public Npcs(Lobby plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
    }

    // ---------------------------------------------------------------- loading and saving

    public void start() {
        stop();
        if (!file.exists()) importFancyNpcs();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("npcs");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                ConfigurationSection c = section.getConfigurationSection(name);
                if (c == null) continue;
                Npc npc = Npc.load(name, c);
                if (npc.location == null) plugin.getLogger().warning("npcs.yml " + name + ": its world isn't loaded; skipped.");
                npcs.put(name, npc);
            }
        }
        for (Entity old : allTagged()) old.remove();
        for (Npc npc : npcs.values()) attachHologram(npc);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 2L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (String name : new ArrayList<>(entities.keySet())) despawn(name);
        for (Npc npc : npcs.values()) plugin.holograms().detach(key(npc));
        npcs.clear();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("The lobby's NPCs. Edit in game with /npc (or here, then /npc reload)."));
        for (Npc npc : npcs.values()) npc.save(yaml.createSection("npcs." + npc.name));
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save npcs.yml: " + e.getMessage());
        }
    }

    public Collection<Npc> all() {
        return npcs.values();
    }

    public Npc get(String name) {
        return npcs.get(name);
    }

    public Npc create(String name, Location at) {
        Npc npc = new Npc(name, at);
        npcs.put(name, npc);
        changed(npc);
        return npc;
    }

    public void delete(Npc npc) {
        npcs.remove(npc.name);
        despawn(npc.name);
        plugin.holograms().detach(key(npc));
        save();
    }

    /** After an edit: saved, and shown again with the change. */
    public void changed(Npc npc) {
        save();
        despawn(npc.name);
        attachHologram(npc);
    }

    private static String key(Npc npc) {
        return "npc:" + npc.name.toLowerCase(Locale.ROOT);
    }

    private void attachHologram(Npc npc) {
        if (npc.lines.isEmpty() || npc.location == null) {
            plugin.holograms().detach(key(npc));
            return;
        }
        Hologram holo = new Hologram(key(npc), hologramSpot(npc));
        holo.downOrigin = true;
        Hologram.Page page = new Hologram.Page();
        page.lines.addAll(npc.lines);
        holo.pages.add(page);
        plugin.holograms().attach(key(npc), holo);
    }

    private static Location hologramSpot(Npc npc) {
        return npc.location.clone().add(0, npc.headHeight() + npc.hologramOffset, 0);
    }

    // ---------------------------------------------------------------- in the world

    private void tick() {
        ticks++;
        for (Npc npc : npcs.values()) {
            Location at = npc.location;
            if (at == null || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                despawn(npc.name);
                continue;
            }
            try {
                Mannequin body = entities.get(npc.name);
                if (body == null || !body.isValid()) {
                    if (body != null) body.remove();
                    body = spawn(npc);
                    entities.put(npc.name, body);
                }
                if (npc.turnToPlayer) turn(npc, body);
            } catch (RuntimeException e) {
                if (ticks % 600 == 1) plugin.getLogger().warning("NPC " + npc.name + ": " + e);
            }
        }
    }

    private Mannequin spawn(Npc npc) {
        Mannequin body = npc.location.getWorld().spawn(npc.location, Mannequin.class, m -> {
            m.setPersistent(false);
            m.addScoreboardTag(TAG);
            m.setImmovable(true);
            m.setInvulnerable(true);
            m.setGravity(false);
            m.setSilent(true);
            m.setCollidable(npc.collidable);
            m.setCustomNameVisible(false);
            m.setDescription(null);
            m.setRemoveWhenFarAway(false);
            m.setProfile(profile(npc));
            for (Map.Entry<EquipmentSlot, ItemStack> entry : npc.equipment.entrySet()) {
                m.getEquipment().setItem(entry.getKey(), entry.getValue().clone());
            }
            var scale = m.getAttribute(Attribute.SCALE);
            if (scale != null) scale.setBaseValue(npc.scale);
            m.setGlowing(npc.glowing);
        });
        NamedTextColor colour = npc.glowing ? NamedTextColor.NAMES.value(npc.glowColor.toLowerCase(Locale.ROOT)) : null;
        plugin.display().setEntityColour(body.getUniqueId().toString(), colour);
        return body;
    }

    /** The skin: the saved texture if there is one, else a texture link, else the player name (the game looks it up). */
    static ResolvableProfile profile(Npc npc) {
        ResolvableProfile.Builder builder = ResolvableProfile.resolvableProfile();
        if (!npc.skin.value.isBlank()) {
            return builder.uuid(UUID.nameUUIDFromBytes(("npc:" + npc.name).getBytes(StandardCharsets.UTF_8))).name(shortName(npc))
                    .addProperty(new ProfileProperty("textures", npc.skin.value,
                            npc.skin.signature.isBlank() ? null : npc.skin.signature)).build();
        }
        if (!npc.skin.url.isBlank()) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + npc.skin.url + "\""
                    + (npc.skin.slim ? ",\"metadata\":{\"model\":\"slim\"}" : "") + "}}}";
            return builder.uuid(UUID.nameUUIDFromBytes(("npc:" + npc.name).getBytes(StandardCharsets.UTF_8))).name(shortName(npc))
                    .addProperty(new ProfileProperty("textures", Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)))).build();
        }
        if (!npc.skin.name.isBlank()) return builder.name(npc.skin.name).build();
        return Mannequin.defaultProfile();
    }

    private static String shortName(Npc npc) {
        String clean = npc.name.replaceAll("[^A-Za-z0-9_]", "");
        return clean.isEmpty() ? "npc" : clean.substring(0, Math.min(16, clean.length()));
    }

    /** Looks at the nearest player close enough, or back the way it was placed. */
    private void turn(Npc npc, Mannequin body) {
        Player nearest = null;
        double best = npc.turnDistance * npc.turnDistance;
        for (Player player : body.getWorld().getPlayers()) {
            double d = player.getLocation().distanceSquared(npc.location);
            if (d <= best) {
                best = d;
                nearest = player;
            }
        }
        if (nearest != null) {
            Location eye = nearest.getEyeLocation();
            body.lookAt(eye.getX(), eye.getY(), eye.getZ(), LookAnchor.EYES);
        } else if (ticks % 10 == 0) {
            body.setRotation(npc.location.getYaw(), npc.location.getPitch());
            body.setBodyYaw(npc.location.getYaw());
        }
    }

    private void despawn(String name) {
        Mannequin body = entities.remove(name);
        if (body != null) {
            plugin.display().setEntityColour(body.getUniqueId().toString(), null);
            body.remove();
        }
    }

    private List<Entity> allTagged() {
        List<Entity> found = new ArrayList<>();
        for (var world : Bukkit.getWorlds()) for (Entity e : world.getEntities()) if (e.getScoreboardTags().contains(TAG)) found.add(e);
        return found;
    }

    /** Gives an NPC a new name (its hologram, skin and actions stay). False if the new name is taken. */
    public boolean rename(Npc npc, String newName) {
        if (npcs.containsKey(newName) && !npc.name.equalsIgnoreCase(newName)) return false;
        npcs.remove(npc.name);
        despawn(npc.name);
        plugin.holograms().detach(key(npc));
        Npc renamed = new Npc(newName, npc.location);
        renamed.skin.name = npc.skin.name;
        renamed.skin.url = npc.skin.url;
        renamed.skin.value = npc.skin.value;
        renamed.skin.signature = npc.skin.signature;
        renamed.skin.slim = npc.skin.slim;
        renamed.equipment.putAll(npc.equipment);
        renamed.glowing = npc.glowing;
        renamed.glowColor = npc.glowColor;
        renamed.turnToPlayer = npc.turnToPlayer;
        renamed.turnDistance = npc.turnDistance;
        renamed.scale = npc.scale;
        renamed.cooldownSeconds = npc.cooldownSeconds;
        renamed.collidable = npc.collidable;
        renamed.actions.putAll(npc.actions);
        renamed.lines.addAll(npc.lines);
        renamed.hologramOffset = npc.hologramOffset;
        npcs.put(newName, renamed);
        changed(renamed);
        return true;
    }

    /** Moves an NPC (and its hologram). */
    public void move(Npc npc, Location to) {
        npc.location = to;
        changed(npc);
    }

    // ---------------------------------------------------------------- clicks

    private Npc owner(Entity entity) {
        if (!(entity instanceof Mannequin) || !entity.getScoreboardTags().contains(TAG)) return null;
        for (Map.Entry<String, Mannequin> entry : entities.entrySet()) {
            if (entry.getValue().equals(entity)) return npcs.get(entry.getKey());
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRightClick(PlayerInteractEntityEvent event) {
        Npc npc = owner(event.getRightClicked());
        if (npc == null) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) click(npc, event.getPlayer(), "RIGHT_CLICK");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeftClick(PrePlayerAttackEntityEvent event) {
        Npc npc = owner(event.getAttacked());
        if (npc == null) return;
        event.setCancelled(true);
        click(npc, event.getPlayer(), "LEFT_CLICK");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity().getScoreboardTags().contains(TAG)) event.setCancelled(true);
    }

    private void click(Npc npc, Player player, String type) {
        String key = player.getUniqueId() + ":" + npc.name;
        long now = System.currentTimeMillis();
        if (now < cooldowns.getOrDefault(key, 0L)) return;
        cooldowns.put(key, now + Math.round(npc.cooldownSeconds * 1000));
        List<String> actions = new ArrayList<>(npc.actions.getOrDefault(type, List.of()));
        actions.addAll(npc.actions.getOrDefault("ANY_CLICK", List.of()));
        run(npc, player, actions, 0);
    }

    /**
     * Runs actions in order. Types (as in FancyNpcs): message, player_command, console_command, send_to_server,
     * play_sound, need_permission (stops if the player lacks it), wait (seconds), execute_random_action.
     */
    private void run(Npc npc, Player player, List<String> actions, int from) {
        for (int i = from; i < actions.size(); i++) {
            if (!player.isOnline()) return;
            String action = actions.get(i);
            int colon = action.indexOf(':');
            String type = (colon < 0 ? action : action.substring(0, colon)).trim().toLowerCase(Locale.ROOT);
            String value = colon < 0 ? "" : fill(action.substring(colon + 1).trim(), player);
            switch (type) {
                case "message" -> player.sendMessage(HologramText.render(value, 0));
                case "player_command", "command" -> player.performCommand(value.startsWith("/") ? value.substring(1) : value);
                case "console_command", "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value.startsWith("/") ? value.substring(1) : value);
                case "send_to_server", "connect" -> plugin.proxyCounts().connect(player, value);
                case "play_sound", "sound" -> {
                    String[] parts = value.split("[: ]");
                    try {
                        player.playSound(Sound.sound(Key.key(parts[0].toLowerCase(Locale.ROOT)), Sound.Source.MASTER,
                                parts.length > 1 ? Float.parseFloat(parts[1]) : 1f, parts.length > 2 ? Float.parseFloat(parts[2]) : 1f),
                                Sound.Emitter.self());
                    } catch (RuntimeException e) {
                        plugin.getLogger().warning("NPC " + npc.name + ": bad sound '" + value + "'");
                    }
                }
                case "need_permission" -> {
                    boolean negate = value.startsWith("!");
                    if (player.hasPermission(negate ? value.substring(1) : value) == negate) return;
                }
                case "wait" -> {
                    long ticks;
                    try {
                        ticks = Math.max(1, Math.round(Double.parseDouble(value) * 20));
                    } catch (NumberFormatException e) {
                        ticks = 20;
                    }
                    int next = i + 1;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> run(npc, player, actions, next), ticks);
                    return;
                }
                case "execute_random_action" -> {
                    // Runs one of the actions after it, chosen at random, and then stops.
                    if (i + 1 < actions.size()) {
                        int pick = ThreadLocalRandom.current().nextInt(i + 1, actions.size());
                        run(npc, player, List.of(actions.get(pick)), 0);
                    }
                    return;
                }
                default -> plugin.getLogger().warning("NPC " + npc.name + ": unknown action '" + action + "'");
            }
        }
    }

    private String fill(String text, Player player) {
        String out = text.replace("{player}", player.getName());
        return com.sasha.lobby.hook.Placeholders.apply(plugin, player, out);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String prefix = event.getPlayer().getUniqueId() + ":";
        cooldowns.keySet().removeIf(k -> k.startsWith(prefix));
    }

    // ---------------------------------------------------------------- FancyNpcs

    /**
     * The first time: FancyNpcs' npcs.yml is brought over (location, skin, items, glow, turning, scale, cooldown, click
     * actions). Skins come from FancyNpcs' own saved copies when it has them. A hologram named like an NPC (as the
     * DecentHolograms labels over the realm NPCs were) becomes that NPC's own hologram.
     */
    private void importFancyNpcs() {
        File folder = new File(plugin.getDataFolder().getParentFile(), "FancyNpcs");
        File source = new File(folder, "npcs.yml");
        if (!source.isFile()) return;
        YamlConfiguration fancy = YamlConfiguration.loadConfiguration(source);
        ConfigurationSection section = fancy.getConfigurationSection("npcs");
        if (section == null) return;
        Map<String, String> uuids = readUuids(new File(folder, ".data/uuids.json"));
        int count = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection c = section.getConfigurationSection(id);
            if (c == null) continue;
            String name = c.getString("name", id);
            ConfigurationSection loc = c.getConfigurationSection("location");
            Location at = Npc.readLocation(loc);
            if (at == null) continue;
            Npc npc = new Npc(name, at);
            String identifier = c.getString("skin.identifier", "");
            npc.skin.slim = "SLIM".equalsIgnoreCase(c.getString("skin.variant", ""));
            if (identifier.startsWith("http")) npc.skin.url = identifier;
            else if (!identifier.isBlank()) npc.skin.name = identifier;
            // FancyNpcs keeps the signed texture for each skin in .data/skins/<base64 of the url or uuid>.json
            String cacheKey = identifier.startsWith("http") ? identifier : uuids.getOrDefault(identifier.toLowerCase(Locale.ROOT), "");
            if (!cacheKey.isEmpty()) readSkin(new File(folder, ".data/skins/"
                    + Base64.getEncoder().encodeToString(cacheKey.getBytes(StandardCharsets.UTF_8)) + ".json"), npc.skin);
            ConfigurationSection equipment = c.getConfigurationSection("equipment");
            if (equipment != null) {
                for (String slot : equipment.getKeys(false)) {
                    EquipmentSlot s = Npc.slot(slot);
                    ItemStack item = equipment.getItemStack(slot);
                    if (s != null && item != null) npc.equipment.put(s, item);
                }
            }
            npc.glowing = c.getBoolean("glowing", false);
            npc.glowColor = c.getString("glowingColor", "light_purple");
            npc.turnToPlayer = c.getBoolean("turnToPlayer", true);
            npc.turnDistance = c.getDouble("turnToPlayerDistance", 8);
            npc.scale = c.getDouble("scale", 1.0);
            npc.cooldownSeconds = c.getDouble("interactionCooldown", 1.0);
            npc.collidable = c.getBoolean("collidable", true);
            ConfigurationSection actions = c.getConfigurationSection("actions");
            if (actions != null) {
                for (String click : actions.getKeys(false)) {
                    ConfigurationSection list = actions.getConfigurationSection(click);
                    if (list == null) continue;
                    List<String> out = new ArrayList<>();
                    for (String n : list.getKeys(false)) {
                        String type = list.getString(n + ".action", "");
                        String value = list.getString(n + ".value", "");
                        if (!type.isBlank()) out.add(value.isBlank() ? type : type + ":" + value);
                    }
                    if (!out.isEmpty()) npc.actions.put(click.toUpperCase(Locale.ROOT), out);
                }
            }
            // A separate hologram with the NPC's name was its label: it becomes the NPC's own.
            Hologram label = plugin.holograms().absorb(name);
            if (label != null && !label.pages.isEmpty()) {
                npc.lines.addAll(label.pages.getFirst().lines);
                if (label.location != null) {
                    double offset = label.location.getY() - at.getY() - npc.headHeight();
                    npc.hologramOffset = label.downOrigin ? offset : offset - total(label.pages.getFirst().lines);
                }
            }
            npcs.put(name, npc);
            count++;
        }
        save();
        npcs.clear();
        plugin.getLogger().info("Brought over " + count + " NPCs from FancyNpcs.");
    }

    private static double total(List<Hologram.Line> lines) {
        double sum = 0;
        for (int i = 0; i < lines.size() - 1; i++) sum += lines.get(i).height;
        return sum;
    }

    /** FancyNpcs' .data/uuids.json: player name → uuid (lower-case name). */
    private static Map<String, String> readUuids(File json) {
        Map<String, String> out = new HashMap<>();
        if (!json.isFile()) return out;
        try {
            var array = com.google.gson.JsonParser.parseString(Files.readString(json.toPath())).getAsJsonArray();
            for (var element : array) {
                var o = element.getAsJsonObject();
                if (o.has("username") && o.has("uuid")) out.put(o.get("username").getAsString().toLowerCase(Locale.ROOT), o.get("uuid").getAsString());
            }
        } catch (IOException | RuntimeException ignored) {
            // no saved names
        }
        return out;
    }

    private static void readSkin(File json, Npc.Skin skin) {
        if (!json.isFile()) return;
        try {
            var data = com.google.gson.JsonParser.parseString(Files.readString(json.toPath())).getAsJsonObject().getAsJsonObject("skinData");
            if (data != null && data.has("textureValue")) {
                skin.value = data.get("textureValue").getAsString();
                skin.signature = data.has("textureSignature") ? data.get("textureSignature").getAsString() : "";
            }
        } catch (IOException | RuntimeException ignored) {
            // keep the name or link
        }
    }
}
