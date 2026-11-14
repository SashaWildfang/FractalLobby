/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatColorsTest {

    @Test
    void acceptsHexNamesGradientsAndRainbow() {
        assertTrue(ChatColors.valid("#B266FF"));
        assertTrue(ChatColors.valid("light_purple"));
        assertTrue(ChatColors.valid("rainbow"));
        assertTrue(ChatColors.valid("gradient:#8E2DE2:#E58BFF"));
        assertFalse(ChatColors.valid("gradient:#8E2DE2"));
        assertFalse(ChatColors.valid("purpleish"));
        assertFalse(ChatColors.valid("#12345"));
    }

    @Test
    void paintsTheWholeMessage() {
        Component typed = Component.text("hi <b>there</b>");
        assertEquals(TextColor.fromHexString("#B266FF"), ChatColors.paint("#B266FF", typed).color());

        // Gradients keep the text exactly as typed: tags players type are never parsed.
        Component gradient = ChatColors.paint("gradient:#8E2DE2:#E58BFF", typed);
        assertEquals("hi <b>there</b>", PlainTextComponentSerializer.plainText().serialize(gradient));
    }

    /** Every colour in the built-in config.yml is valid. */
    @Test
    void builtInColoursAreValid() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
        var colours = Objects.requireNonNull(yaml.getConfigurationSection("chat-color.colors"));
        assertEquals(15, colours.getKeys(false).size());
        for (String id : colours.getKeys(false)) {
            assertTrue(ChatColors.valid(colours.getString(id + ".color", "")), id);
        }
    }
}
