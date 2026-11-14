/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.scoreboard;

import com.sasha.lobby.listener.BorderListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextWidthTest {

    @Test
    void measuresCharactersAndBold() {
        assertEquals(6 * 3, TextWidth.width(Component.text("abc")));
        assertEquals(2 + 3 + 4, TextWidth.width(Component.text("il ")));
        assertEquals(7 * 3, TextWidth.width(Component.text("abc").decorate(TextDecoration.BOLD)));
    }

    /** A centered line gets spaces in front so it sits in the middle of the widest line. */
    @Test
    void centersMarkedLines() {
        Component title = Component.text("ab");
        List<Component> lines = List.of(Component.text("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), Component.text("aaaaaaaa"));
        List<Component> out = TextWidth.center(title, lines, List.of(false, true));

        assertEquals(lines.getFirst(), out.getFirst());
        // (240 - 48) / 2 = 96px = 24 spaces.
        assertEquals(" ".repeat(24) + "aaaaaaaa", PlainTextComponentSerializer.plainText().serialize(out.get(1)));
    }

    @Test
    void borderIsASquareAroundSpawn() {
        Location center = new Location(null, 27.5, 84, -8.5);
        assertEquals(-100, BorderListener.outside(center, center, 100), 1e-9);
        assertEquals(5, BorderListener.outside(new Location(null, 27.5 + 105, 70, -8.5), center, 100), 1e-9);
        assertEquals(1, BorderListener.outside(new Location(null, 0, 70, -8.5 - 101), center, 100), 1e-9);
    }
}
