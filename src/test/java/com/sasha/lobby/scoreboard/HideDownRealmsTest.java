/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.scoreboard;

import com.sasha.lobby.proxy.ServerStatus.State;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HideDownRealmsTest {

    private static final Map<String, State> STATES = Map.of(
            "survival", State.ONLINE, "lifesteal", State.OFFLINE, "practice", State.MAINTENANCE, "lobby", State.UNKNOWN);

    private static boolean hidden(String line) {
        return DisplayService.aboutDownServer(line, server -> STATES.getOrDefault(server.toLowerCase(), State.UNKNOWN));
    }

    @Test
    void hidesOfflineAndMaintenanceRealms() {
        assertTrue(hidden(" <primary>▪</primary> <white>Lifesteal: <primary><online:lifesteal>"));
        assertTrue(hidden(" <white>Practice: <online:practice>"));
    }

    @Test
    void keepsOnlineUnknownAndOtherLines() {
        assertFalse(hidden(" <white>Survival: <primary><online:survival>"));
        assertFalse(hidden(" <white>Lobby: <online:lobby>"));
        assertFalse(hidden("<secondary>Realms <muted>(<online:all>)"));
        assertFalse(hidden("<center><dark_gray><date>"));
    }
}
