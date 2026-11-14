/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyCodesTest {

    @Test
    void convertsColourAndStyleCodes() {
        assertEquals("<reset><gray>hi <bold>there", LegacyCodes.toMiniMessage("&7hi &lthere"));
        assertEquals("<reset><#B266FF>purple", LegacyCodes.toMiniMessage("&#B266FFpurple"));
        assertEquals("<reset><#b266ff>purple", LegacyCodes.toMiniMessage("§x§b§2§6§6§f§fpurple"));
        assertEquals("Elmo & Patsy", LegacyCodes.toMiniMessage("Elmo & Patsy"));
    }

    @Test
    void findsTheLastColour() {
        assertEquals(NamedTextColor.GRAY, LegacyCodes.lastColor("&7%player%: "));
        assertEquals(NamedTextColor.WHITE, LegacyCodes.lastColor("&c[Admin] &f"));
        assertEquals(TextColor.fromHexString("#B266FF"), LegacyCodes.lastColor("&#B266FF"));
        assertNull(LegacyCodes.lastColor("%player%: "));
    }

    /** The join/quit formats from config.yml render with their colours and the small-caps font. */
    @Test
    void joinMessageFormatsRender() throws Exception {
        Messages messages = Messages.of(new YamlConfiguration(), null, Logger.getLogger("test"));
        Component join = messages.render("&a+ &7<player>", Messages.arg("player", "SashaWildfang"));

        assertEquals("+ " + SmallCaps.convert("SashaWildfang"), PlainTextComponentSerializer.plainText().serialize(join));
        assertEquals(NamedTextColor.GREEN, firstColour(join));
    }

    private static TextColor firstColour(Component component) {
        if (component instanceof TextComponent text && !text.content().isBlank() && component.color() != null) {
            return component.color();
        }
        for (Component child : component.children()) {
            TextColor colour = firstColour(child);
            if (colour != null) return colour;
        }
        return component.color();
    }
}
