/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.sasha.lobby.Lobby;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers what everyone looks like, so a head of someone who's offline still shows their skin (the friends menu).
 * Whenever a player joins the lobby their skin is kept (skins.yml); for anyone never seen here, the skin is looked up
 * once from Mojang in the background and the menu redrawn when it arrives.
 */
public final class Skins implements Listener {

    private final Lobby plugin;
    private final File file;
    private final Map<UUID, ProfileProperty> skins = new ConcurrentHashMap<>();
    /** Looked up already this run (found or not), so a missing skin is only asked for once. */
    private final Set<UUID> asked = ConcurrentHashMap.newKeySet();
    private boolean dirty;

    public Skins(Lobby plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "skins.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                String value = yaml.getString(key + ".value"), signature = yaml.getString(key + ".signature");
                if (value != null) skins.put(UUID.fromString(key), new ProfileProperty("textures", value, signature));
            } catch (IllegalArgumentException ignored) {
                // not a player's id
            }
        }
        // saved now and then (not on every join)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::save, 20L * 60, 20L * 60);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        remember(event.getPlayer().getUniqueId(), event.getPlayer().getPlayerProfile());
    }

    private void remember(UUID id, PlayerProfile profile) {
        for (ProfileProperty property : profile.getProperties()) {
            if (!property.getName().equals("textures")) continue;
            ProfileProperty old = skins.put(id, property);
            if (old == null || !old.getValue().equals(property.getValue())) dirty = true;
            return;
        }
    }

    /**
     * A profile for a head of this player, with their skin if it's known. If it isn't, it's looked up (once) and
     * {@code arrived} runs on the main thread when it comes.
     */
    public PlayerProfile profile(UUID id, String name, Runnable arrived) {
        PlayerProfile profile = Bukkit.createProfile(id, name);
        ProfileProperty skin = skins.get(id);
        if (skin != null) {
            profile.setProperty(skin);
            return profile;
        }
        if (asked.add(id)) {
            Bukkit.createProfile(id, name).update().thenAccept(found -> {
                remember(id, found);
                if (skins.containsKey(id)) Bukkit.getScheduler().runTask(plugin, arrived);
            }).exceptionally(error -> null);
        }
        return profile;
    }

    /** Writes skins.yml if anything changed. */
    public synchronized void save() {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, ProfileProperty> entry : skins.entrySet()) {
            yaml.set(entry.getKey() + ".value", entry.getValue().getValue());
            if (entry.getValue().getSignature() != null) yaml.set(entry.getKey() + ".signature", entry.getValue().getSignature());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't save skins.yml: " + e.getMessage());
        }
    }
}
