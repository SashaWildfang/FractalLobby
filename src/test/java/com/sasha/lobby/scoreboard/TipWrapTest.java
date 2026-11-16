/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.scoreboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TipWrapTest {

    @Test
    void breaksNearTheMiddleOutsideTags() {
        assertEquals("<white>Tip:</white> <muted>Try the parkour course<br>for a spot on the leaderboard",
                DisplayService.wrap("<white>Tip:</white> <muted>Try the parkour course for a spot on the leaderboard"));
    }

    @Test
    void neverBreaksInsideATag() {
        String wrapped = DisplayService.wrap("<white>Tip:</white> <muted>Get ranks and perks with <accent>/store");
        assertEquals(1, wrapped.split("<br>").length - 1);
        assertEquals(false, wrapped.contains("<ac<br>"));
    }
}
