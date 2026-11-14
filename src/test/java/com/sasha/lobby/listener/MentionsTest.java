/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MentionsTest {

    /** A name counts as a whole word, in any case, with or without @. */
    @Test
    void findsNamesAsWholeWords() {
        assertTrue(ChatListener.namePattern("Sasha", false).matcher("hey sasha!").find());
        assertTrue(ChatListener.namePattern("Sasha", false).matcher("hi @Sasha").find());
        assertFalse(ChatListener.namePattern("Sasha", false).matcher("sashawildfang").find());
        assertFalse(ChatListener.namePattern("Sasha", false).matcher("mysasha").find());
    }

    /** Only @Name gets highlighted. */
    @Test
    void highlightsOnlyAtMentions() {
        assertEquals(List.of("@SASHA"), ChatListener.atMentions("sasha come here @SASHA", "Sasha"));
        assertEquals(List.of(), ChatListener.atMentions("sasha come here", "Sasha"));
        assertEquals(List.of("@Sasha", "@sasha"), ChatListener.atMentions("@Sasha, @sasha", "Sasha"));
    }
}
