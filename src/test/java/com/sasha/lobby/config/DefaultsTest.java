/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultsTest {

    private static YamlConfiguration builtIn() throws Exception {
        try (InputStream in = DefaultsTest.class.getResourceAsStream("/config.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /** A blank config.yml (deleted, or sections removed) reads the built-in lists, and doesn't blank them in the file. */
    @Test
    void missingSectionsUseBuiltInDefaults() throws Exception {
        YamlConfiguration file = new YamlConfiguration();
        file.setDefaults(builtIn());
        for (String path : new String[] {"realm-selector.realms", "chat-color.colors", "trails.trails"}) {
            var section = LobbyConfig.section(file, path);
            assertNotNull(section, path);
            assertFalse(section.getKeys(false).isEmpty(), path);
        }
        assertTrue(file.getKeys(false).isEmpty(), "nothing was written into the file");
    }

    /** A list emptied on purpose stays empty. */
    @Test
    void emptiedSectionStaysEmpty() throws Exception {
        YamlConfiguration file = new YamlConfiguration();
        file.setDefaults(builtIn());
        file.createSection("trails.trails");
        assertTrue(LobbyConfig.section(file, "trails.trails").getKeys(false).isEmpty());
    }

    @Test
    void borderIsOffByDefault() throws Exception {
        assertFalse(builtIn().getBoolean("border.enabled"));
    }
}
