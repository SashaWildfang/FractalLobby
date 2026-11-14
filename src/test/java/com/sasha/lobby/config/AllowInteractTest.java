/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllowInteractTest {

    private static boolean allowed(List<Pattern> patterns, String block) {
        return patterns.stream().anyMatch(p -> p.matcher(block).matches());
    }

    /** allow-interact takes block names and * wildcards, in any case. */
    @Test
    void wildcardsMatchBlockNames() {
        List<Pattern> patterns = LobbyConfig.patterns(List.of("*_DOOR", "lectern", " "));
        assertTrue(allowed(patterns, "OAK_DOOR"));
        assertTrue(allowed(patterns, "IRON_DOOR"));
        assertTrue(allowed(patterns, "LECTERN"));
        assertFalse(allowed(patterns, "OAK_TRAPDOOR"));
        assertFalse(allowed(patterns, "CHEST"));
    }
}
