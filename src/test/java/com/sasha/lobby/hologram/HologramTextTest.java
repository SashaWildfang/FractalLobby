package com.sasha.lobby.hologram;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HologramTextTest {

    private static String plain(String content, long frame) {
        return PlainTextComponentSerializer.plainText().serialize(HologramText.render(content, frame));
    }

    @Test
    void decentHologramsGradientBecomesAMiniMessageGradient() {
        assertEquals("<gradient:#FF7BE5:#FFC2F2><bold>✦ Lifesteal ✦</gradient>",
                HologramText.toMiniMessage("<#FF7BE5>&l✦ Lifesteal ✦</#FFC2F2>"));
        assertEquals("✦ Lifesteal ✦", plain("<#FF7BE5>&l✦ Lifesteal ✦</#FFC2F2>", 0));
    }

    @Test
    void legacyAndHexCodesWork() {
        assertEquals("Hey Sasha, glad you're here!", plain("&#E8DDFFHey &f&lSasha&#E8DDFF, glad you're here!", 0));
    }

    @Test
    void burnKeepsTheTextAndChangesColourOverTime() {
        String line = "&d&l» <#ANIM:burn:&f&l,&d&l>Click to play</#ANIM> &d&l«";
        assertEquals("» Click to play «", plain(line, 0));
        assertEquals("» Click to play «", plain(line, 7));
        assertFalse(HologramText.animate(line, 0).equals(HologramText.animate(line, 7)));
    }

    @Test
    void typewriterShowsMoreEachFrame() {
        assertEquals("Hel", plain("<#ANIM:typewriter:&f>Hello</#ANIM>", 3));
        assertEquals("Hello", plain("<#ANIM:typewriter:&f>Hello</#ANIM>", 9));
    }

    @Test
    void knowsWhichLinesDifferPerViewer() {
        assertTrue(HologramText.perViewer("Hey {player}"));
        assertTrue(HologramText.perViewer("&a● %lobby_online_survival% playing"));
        assertFalse(HologramText.perViewer("&#C77DFF▸ Lost? Type /spawn"));
    }
}
