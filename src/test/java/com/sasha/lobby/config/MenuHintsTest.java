/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Menu hints name the exact click (the menus only react to the clicks their hints show): Left-click,
 * Right-click or Shift + Left-click with the mouse pictures, never a vague "Click" or "Shift-click".
 */
class MenuHintsTest {

    /** Shown in the world, where any click works: an NPC or a realm's action line, and the parkour hologram. */
    private static final Set<String> ANY_CLICK = Set.of("realm-selector.action.online", "parkour.leaderboard.hint",
            "holo.help");

    private static YamlConfiguration messages() throws Exception {
        try (InputStream in = MenuHintsTest.class.getResourceAsStream("/messages.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static List<String> lines(YamlConfiguration yaml, String key) {
        if (yaml.isList(key)) return yaml.getStringList(key);
        return yaml.isString(key) ? List.of(yaml.getString(key)) : List.of();
    }

    @Test
    void menuHintsNameTheExactClick() throws Exception {
        YamlConfiguration yaml = messages();
        List<String> vague = new ArrayList<>();
        for (String key : yaml.getKeys(true)) {
            if (ANY_CLICK.contains(key)) continue;
            for (String line : lines(yaml, key)) {
                if (line.contains("<click>") || line.contains("Shift-click</accent>") || line.contains("<accent>Right-click")) {
                    vague.add(key + ": " + line);
                }
            }
        }
        assertEquals(List.of(), vague);
    }

    @Test
    void countsSayUnlocked() throws Exception {
        YamlConfiguration yaml = messages();
        for (String key : yaml.getKeys(true)) {
            for (String line : lines(yaml, key)) {
                assertFalse(line.contains("Found: <white><found>"), key + " should say Unlocked: " + line);
            }
        }
        assertEquals("<dark_gray>Click the Parkour NPC to try again!", yaml.getStringList("parkour.menu.you-lore").getLast());
    }

    /** YAML reads keys like yes, no, on and off as true/false, so their text is never found (unless quoted). */
    @Test
    void noKeysReadAsTrueOrFalse() throws Exception {
        YamlConfiguration yaml = messages();
        List<String> bad = yaml.getKeys(true).stream()
                .filter(key -> java.util.Arrays.stream(key.split(java.util.regex.Pattern.quote("."))).anyMatch(part -> part.equals("true") || part.equals("false")))
                .toList();
        assertEquals(List.of(), bad);
        for (String key : List.of("confirm.title", "confirm.accept.name", "confirm.cancel.name", "collectibles.confirm.buy.name",
                "mystery.confirm.open.name", "mystery.menu.open-all-none.name", "collectibles.sort.name")) {
            assertFalse(yaml.getString(key, "").isEmpty(), key + " is missing");
        }
    }

    @Test
    void noCollectionPageAndNoGlowInBoxes() throws Exception {
        YamlConfiguration yaml = messages();
        assertNull(yaml.get("mystery.collection"), "the Collection page is gone");
        assertNull(yaml.get("mystery.menu.collection"), "the vault's Collection button is gone");
        try (InputStream in = MenuHintsTest.class.getResourceAsStream("/config.yml")) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            assertFalse(config.getStringList("mystery-boxes.rewards").stream().anyMatch(r -> r.startsWith("glow")),
                    "glow is a rank perk, not a box reward");
        }
    }
}
