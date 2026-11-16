/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Everything players read, loaded from messages.yml with the bundled file as fallback for missing keys.
 * Theme colours become tags (&lt;primary&gt; etc.), &lt;brand&gt; becomes the theme gradient and &lt;prefix&gt; the prefix.
 * With format.font: small-caps every letter is shown as tiny text.
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    /**
     * Player names are kept exactly as they are while the rest of a message turns into small caps. While a
     * message renders, each name stands in as a marker (private-use characters, which aren't letters, so
     * small caps leaves them alone) and is swapped back in afterwards.
     */
    private static final ThreadLocal<Map<String, Component>> KEPT = new ThreadLocal<>();

    private final YamlConfiguration yaml;
    private final String gradientOpen;
    private final TagResolver theme;
    private final boolean smallCaps;

    private Messages(YamlConfiguration yaml, Logger logger) {
        this.yaml = yaml;
        String font = text("format.font", "small-caps").trim();
        this.smallCaps = !font.equalsIgnoreCase("normal");
        if (!font.equalsIgnoreCase("normal") && !font.equalsIgnoreCase("small-caps")) {
            logger.warning("messages.yml format.font: unknown value '" + font + "', using small-caps");
        }

        List<String> gradient = yaml.getStringList("theme.gradient");
        this.gradientOpen = gradient.size() >= 2 ? "<gradient:" + String.join(":", gradient) + ">" : "<gradient:#8E2DE2:#E58BFF>";

        TagResolver colours = TagResolver.resolver(
                colour("primary", logger), colour("secondary", logger), colour("accent", logger),
                colour("muted", logger), colour("good", logger), colour("bad", logger));
        Component prefix = MM.deserialize(brand(text("prefix", "")), colours);
        if (smallCaps) prefix = SmallCaps.apply(prefix);
        this.theme = TagResolver.resolver(colours, Placeholder.component("prefix", prefix));
    }

    public static Messages load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);

        YamlConfiguration defaults = null;
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    defaults = YamlConfiguration.loadConfiguration(reader);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read the default messages.yml: " + e.getMessage());
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        // A newer plugin version ships new text: keep the server's old file as a backup and use the new one.
        if (defaults != null && yaml.getInt("messages-version", 1) < defaults.getInt("messages-version", 1)) {
            File backup = new File(plugin.getDataFolder(), "messages.yml.old");
            if (file.renameTo(backup) || (backup.delete() && file.renameTo(backup))) {
                plugin.saveResource("messages.yml", true);
                yaml = YamlConfiguration.loadConfiguration(file);
                plugin.getLogger().info("Updated messages.yml to the new version. Your previous file is saved as messages.yml.old.");
            }
        }
        return of(yaml, defaults, plugin.getLogger());
    }

    /** Messages from a server's file, with the plugin's built-in file filling any gaps. */
    static Messages of(YamlConfiguration yaml, YamlConfiguration defaults, Logger logger) {
        if (defaults != null) yaml.setDefaults(defaults);
        return new Messages(yaml, logger);
    }

    /** Sends a chat message. Empty messages are skipped, so admins can turn them off. */
    public void send(Audience to, String key, TagResolver... args) {
        String template = text(key, key);
        if (!template.isEmpty()) to.sendMessage(parse(template, args));
    }

    /** Shows a message above the hotbar. Empty messages are skipped. */
    public void actionBar(Audience to, String key, TagResolver... args) {
        String template = text(key, key);
        if (!template.isEmpty()) to.sendActionBar(parse(template, args));
    }

    public Component component(String key, TagResolver... args) {
        return parse(text(key, key), args);
    }

    /** The raw template of a single entry, for rendering repeatedly with {@link #render}. */
    public String raw(String key) {
        return text(key, key);
    }

    /** The raw lines of a list entry (e.g. scoreboard.lines), for rendering repeatedly with {@link #render}. */
    public List<String> lines(String key) {
        return yaml.getStringList(key);
    }

    /** Renders a template the caller already has (theme, brand and font applied). */
    public Component render(String template, TagResolver... args) {
        return parse(template, args);
    }

    /** A single line for an item name, without the default italics. */
    public Component item(String key, TagResolver... args) {
        return parse("<!italic>" + text(key, key), args);
    }

    public List<Component> lore(String key, TagResolver... args) {
        List<Component> lines = new ArrayList<>();
        for (String template : yaml.getStringList(key)) lines.add(parse("<!italic>" + template, args));
        return lines;
    }

    /** Placeholder whose value is inserted as plain text, so tags in it are never parsed. */
    public static TagResolver arg(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    /** Placeholder for a player's name: plain text, and always in normal letters (never small caps). */
    public static TagResolver name(String key, String name) {
        return keep(key, Component.text(name));
    }

    /** Placeholder inserted exactly as given: never parsed, and never turned into small caps. */
    public static TagResolver keep(String key, Component value) {
        return TagResolver.resolver(key, (arguments, context) -> Tag.selfClosingInserting(marker(value)));
    }

    /** Swaps kept text for a marker while a message renders (or inserts it directly when there's no small caps). */
    private static Component marker(Component value) {
        Map<String, Component> kept = KEPT.get();
        if (kept == null) return value;
        String marker = "\uE000" + (char) (0xE100 + kept.size()) + "\uE001";
        kept.put(marker, value);
        return Component.text(marker);
    }

    /**
     * A string from messages.yml, falling back to the plugin's built-in messages.yml for keys the server's
     * file doesn't have, and only then to {@code fallback}.
     * Note: Bukkit's {@code getString(path, def)} skips the built-in defaults, so it must not be used here.
     */
    private String text(String key, String fallback) {
        String value = yaml.getString(key);
        return value != null ? value : fallback;
    }

    private Component parse(String template, TagResolver... args) {
        String mini = brand(LegacyCodes.toMiniMessage(template));
        Map<String, Component> outer = KEPT.get();
        Map<String, Component> kept = smallCaps ? new LinkedHashMap<>() : null;
        KEPT.set(kept);
        Component component;
        try {
            component = MM.deserialize(mini, TagResolver.resolver(theme, TagResolver.resolver(args)));
        } finally {
            KEPT.set(outer);
        }
        if (!smallCaps) return component;
        // Applied after placeholders are filled in, so counts are converted too; then the kept pieces
        // (player names) go back in as they were.
        component = SmallCaps.apply(component);
        for (Map.Entry<String, Component> piece : kept.entrySet()) {
            component = component.replaceText(TextReplacementConfig.builder()
                    .matchLiteral(piece.getKey()).replacement(piece.getValue()).build());
        }
        return component;
    }

    private String brand(String template) {
        return template.replace("<brand>", gradientOpen).replace("</brand>", "</gradient>");
    }

    private TagResolver colour(String name, Logger logger) {
        String hex = text("theme." + name, "#FFFFFF");
        TextColor colour = TextColor.fromHexString(hex);
        if (colour == null) {
            logger.warning("messages.yml theme." + name + " is not a hex colour: " + hex);
            colour = TextColor.color(0xFFFFFF);
        }
        return Placeholder.styling(name, colour);
    }
}
