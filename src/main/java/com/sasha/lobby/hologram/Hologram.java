/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One hologram, saved as holograms/NAME.yml in DecentHolograms' own format (so its files can be copied straight in):
 * location "world:x:y:z", enabled, display-range, update-range, update-interval, down-origin, and pages, each with
 * lines (content, height) and click actions.
 */
public final class Hologram {

    public static final double TEXT_HEIGHT = 0.3, ICON_HEIGHT = 0.6;

    /** A line: text, or "#ICON: MATERIAL", "#HEAD: name" / "#SMALLHEAD: name". */
    public static final class Line {
        public String content;
        public double height;

        public Line(String content, double height) {
            this.content = content;
            this.height = height;
        }

        public static Line of(String content) {
            return new Line(content, isItem(content) ? ICON_HEIGHT : TEXT_HEIGHT);
        }

        public boolean item() {
            return isItem(content);
        }

        static boolean isItem(String content) {
            String upper = content.trim().toUpperCase(Locale.ROOT);
            return upper.startsWith("#ICON:") || upper.startsWith("#HEAD:") || upper.startsWith("#SMALLHEAD:");
        }
    }

    /** A page: its lines and its click actions (click type → actions like "MESSAGE:hi"). */
    public static final class Page {
        public final List<Line> lines = new ArrayList<>();
        public final Map<String, List<String>> actions = new LinkedHashMap<>();
    }

    public final String name;
    public Location location;
    public boolean enabled = true;
    public int displayRange = 48;
    public int updateRange = 48;
    public int updateInterval = 20;
    public boolean downOrigin;
    public final List<Page> pages = new ArrayList<>();

    public Hologram(String name, Location location) {
        this.name = name;
        this.location = location;
    }

    static Hologram load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String name = file.getName().replaceFirst("\\.yml$", "");
        Location location = parseLocation(yaml.getString("location", ""));
        Hologram holo = new Hologram(name, location);
        holo.enabled = yaml.getBoolean("enabled", true);
        holo.displayRange = yaml.getInt("display-range", 48);
        holo.updateRange = yaml.getInt("update-range", 48);
        holo.updateInterval = Math.max(1, yaml.getInt("update-interval", 20));
        holo.downOrigin = yaml.getBoolean("down-origin", false);
        for (Map<?, ?> rawPage : yaml.getMapList("pages")) {
            Page page = new Page();
            if (rawPage.get("lines") instanceof List<?> lines) {
                for (Object rawLine : lines) {
                    if (!(rawLine instanceof Map<?, ?> line)) continue;
                    String content = String.valueOf(line.get("content") == null ? "" : line.get("content"));
                    double height = line.get("height") instanceof Number number ? number.doubleValue()
                            : Line.isItem(content) ? ICON_HEIGHT : TEXT_HEIGHT;
                    page.lines.add(new Line(content, height));
                }
            }
            if (rawPage.get("actions") instanceof Map<?, ?> actions) {
                for (Map.Entry<?, ?> entry : actions.entrySet()) {
                    List<String> list = new ArrayList<>();
                    if (entry.getValue() instanceof List<?> values) for (Object value : values) list.add(String.valueOf(value));
                    if (!list.isEmpty()) page.actions.put(String.valueOf(entry.getKey()).toUpperCase(Locale.ROOT), list);
                }
            }
            holo.pages.add(page);
        }
        if (holo.pages.isEmpty()) holo.pages.add(new Page());
        return holo;
    }

    void save(File folder) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("location", location == null ? "" : formatLocation(location));
        yaml.set("enabled", enabled);
        yaml.set("display-range", displayRange);
        yaml.set("update-range", updateRange);
        yaml.set("update-interval", updateInterval);
        yaml.set("facing", 0.0);
        yaml.set("down-origin", downOrigin);
        List<Map<String, Object>> rawPages = new ArrayList<>();
        for (Page page : pages) {
            List<Map<String, Object>> rawLines = new ArrayList<>();
            for (Line line : page.lines) {
                Map<String, Object> rawLine = new LinkedHashMap<>();
                rawLine.put("content", line.content);
                rawLine.put("height", line.height);
                rawLines.add(rawLine);
            }
            Map<String, Object> rawPage = new LinkedHashMap<>();
            rawPage.put("lines", rawLines);
            rawPage.put("actions", new LinkedHashMap<>(page.actions));
            rawPages.add(rawPage);
        }
        yaml.set("pages", rawPages);
        yaml.save(new File(folder, name + ".yml"));
    }

    /** "world:x:y:z" (DecentHolograms' way of writing it). */
    static Location parseLocation(String text) {
        String[] parts = text.split(":");
        if (parts.length < 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String formatLocation(Location at) {
        return String.format(Locale.ROOT, "%s:%.3f:%.3f:%.3f", at.getWorld().getName(), at.getX(), at.getY(), at.getZ());
    }

    /**
     * Where each line goes (its height above the hologram's location). Without down-origin the location is the top
     * line and the rest hang below it; with down-origin it's the bottom line and the rest stack above.
     */
    static double[] offsets(List<Line> lines, boolean downOrigin) {
        double[] y = new double[lines.size()];
        if (!downOrigin) {
            double at = 0;
            for (int i = 0; i < lines.size(); i++) {
                y[i] = at;
                at -= lines.get(i).height;
            }
        } else {
            double at = 0;
            for (int i = lines.size() - 1; i >= 0; i--) {
                y[i] = at;
                at += i > 0 ? lines.get(i - 1).height : 0;
            }
        }
        return y;
    }
}
