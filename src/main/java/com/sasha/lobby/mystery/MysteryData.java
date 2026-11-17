/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.mystery;

import com.sasha.lobby.Lobby;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everyone's Mystery Boxes, Mystery Dust and unlocked cosmetics, in mystery-boxes.yml. Kept in memory and written
 * out (in the background) a few seconds after a change, and when the plugin stops. Works for offline players too,
 * so the store or a vote plugin can give boxes from the console.
 */
public final class MysteryData {

    /** One box: its stars and the seed its possible prizes are drawn from (so every box is different). */
    public record Box(int stars, long seed) {
        String text() {
            return stars + ":" + seed;
        }

        static Box parse(String text) {
            try {
                int colon = text.indexOf(':');
                int stars = MysteryBoxes.clampStars(Integer.parseInt(text.substring(0, colon)));
                return new Box(stars, Long.parseLong(text.substring(colon + 1)));
            } catch (RuntimeException e) {
                return null;
            }
        }

        static Box random(int stars) {
            return new Box(MysteryBoxes.clampStars(stars), java.util.concurrent.ThreadLocalRandom.current().nextLong());
        }
    }

    /** One player's stash. */
    public static final class Stash {
        String name = "";
        long dust;
        /** Every box they have, newest last. */
        final java.util.List<Box> boxes = new ArrayList<>();
        final Set<String> owned = new LinkedHashSet<>();
        String lastDaily = "";
        /** What they're wearing or holding, by category ("hat" → "hat:top_hat"). */
        final Map<String, String> equipped = new HashMap<>();
        int streak;
        int opened;

        public long dust() {
            return dust;
        }

        public int boxes(int stars) {
            return (int) boxes.stream().filter(b -> b.stars() == stars).count();
        }

        public int totalBoxes() {
            return boxes.size();
        }

        /** Their boxes, best first. */
        public java.util.List<Box> boxList() {
            java.util.List<Box> list = new ArrayList<>(boxes);
            list.sort(java.util.Comparator.comparingInt(Box::stars).reversed());
            return list;
        }

        public boolean owns(String reward) {
            return owned.contains(reward);
        }

        public Set<String> owned() {
            return Set.copyOf(owned);
        }

        public int opened() {
            return opened;
        }

        public int streak() {
            return streak;
        }

        public String equipped(String category) {
            return equipped.get(category);
        }
    }

    private final Lobby plugin;
    private final File file;
    private final Map<UUID, Stash> stashes = new HashMap<>();
    private boolean dirty;

    MysteryData(Lobby plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mystery-boxes.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save(true);
        }, 100L, 100L);
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            ConfigurationSection c = players.getConfigurationSection(key);
            if (c == null) continue;
            try {
                Stash stash = new Stash();
                stash.name = c.getString("name", "");
                stash.dust = Math.max(0, c.getLong("dust"));
                for (String text : c.getStringList("box-list")) {
                    Box box = Box.parse(text);
                    if (box != null) stash.boxes.add(box);
                }
                // before 1.41 boxes were only counted by stars: each becomes its own box
                for (int stars = 1; stars <= MysteryBoxes.MAX_STARS; stars++) {
                    for (int i = Math.max(0, c.getInt("boxes." + stars)); i > 0; i--) stash.boxes.add(Box.random(stars));
                }
                stash.owned.addAll(c.getStringList("owned"));
                stash.lastDaily = c.getString("daily", "");
                stash.streak = c.getInt("streak");
                stash.opened = c.getInt("opened");
                ConfigurationSection worn = c.getConfigurationSection("equipped");
                if (worn != null) for (String category : worn.getKeys(false)) stash.equipped.put(category, worn.getString(category));
                stashes.put(UUID.fromString(key), stash);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("mystery-boxes.yml: skipped " + key + " (not a UUID)");
            }
        }
    }

    /** Writes everything out; in the background unless the plugin is stopping. */
    void save(boolean async) {
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(java.util.List.of("Mystery Boxes: each player's boxes (by stars), Mystery Dust and unlocked cosmetics."));
        for (Map.Entry<UUID, Stash> entry : stashes.entrySet()) {
            Stash s = entry.getValue();
            String p = "players." + entry.getKey() + ".";
            yaml.set(p + "name", s.name);
            yaml.set(p + "dust", s.dust);
            if (!s.boxes.isEmpty()) yaml.set(p + "box-list", s.boxes.stream().map(Box::text).toList());
            yaml.set(p + "owned", new ArrayList<>(s.owned));
            yaml.set(p + "daily", s.lastDaily);
            yaml.set(p + "streak", s.streak);
            yaml.set(p + "opened", s.opened);
            for (Map.Entry<String, String> worn : s.equipped.entrySet()) yaml.set(p + "equipped." + worn.getKey(), worn.getValue());
        }
        String text = yaml.saveToString();
        Runnable write = () -> {
            try {
                file.getParentFile().mkdirs();
                java.nio.file.Files.writeString(file.toPath(), text);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save mystery-boxes.yml: " + e.getMessage());
            }
        };
        if (async) plugin.getServer().getScheduler().runTaskAsynchronously(plugin, write);
        else write.run();
    }

    public Stash get(UUID id) {
        return stashes.computeIfAbsent(id, k -> new Stash());
    }

    void name(UUID id, String name) {
        Stash stash = get(id);
        if (!name.equals(stash.name)) {
            stash.name = name;
            dirty = true;
        }
    }

    /** Adds new boxes (each with its own prizes), or with a negative amount takes away that many of these stars. */
    void addBoxes(UUID id, int stars, int amount) {
        Stash stash = get(id);
        for (int i = 0; i < amount; i++) stash.boxes.add(Box.random(stars));
        for (int i = 0; i < -amount; i++) {
            for (int k = stash.boxes.size() - 1; k >= 0; k--) {
                if (stash.boxes.get(k).stars() == stars) {
                    stash.boxes.remove(k);
                    break;
                }
            }
        }
        dirty = true;
    }

    /** Takes one particular box; false if they don't have it (any more). */
    boolean take(UUID id, Box box) {
        boolean removed = get(id).boxes.remove(box);
        if (removed) dirty = true;
        return removed;
    }

    void addDust(UUID id, long amount) {
        Stash stash = get(id);
        stash.dust = Math.max(0, stash.dust + amount);
        dirty = true;
    }

    /** Unlocks a cosmetic; false if they had it already. */
    boolean unlock(UUID id, String reward) {
        boolean added = get(id).owned.add(reward);
        if (added) dirty = true;
        return added;
    }

    /** Wears (or with null takes off) something of a category. */
    public void equip(UUID id, String category, String item) {
        if (item == null) get(id).equipped.remove(category);
        else get(id).equipped.put(category, item);
        dirty = true;
    }

    /** For buying with dust and other unlocks outside boxes: false if they had it already. */
    public boolean grant(UUID id, String reward) {
        return unlock(id, reward);
    }

    public void spendDust(UUID id, long amount) {
        addDust(id, -amount);
    }

    void opened(UUID id) {
        get(id).opened++;
        dirty = true;
    }

    void reset(UUID id) {
        stashes.remove(id);
        dirty = true;
    }

    /** Records today's daily reward; returns the new streak (consecutive days), or 0 if it was already claimed today. */
    int claimDaily(UUID id, LocalDate today) {
        Stash stash = get(id);
        String day = today.toString();
        if (day.equals(stash.lastDaily)) return 0;
        stash.streak = today.minusDays(1).toString().equals(stash.lastDaily) ? stash.streak + 1 : 1;
        stash.lastDaily = day;
        dirty = true;
        return stash.streak;
    }
}
