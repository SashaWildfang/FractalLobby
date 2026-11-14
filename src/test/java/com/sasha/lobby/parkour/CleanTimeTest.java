/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.parkour;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CleanTimeTest {

    /** ajParkour's time text can carry its chat prefix and colour codes; only the time is kept. */
    @Test
    void keepsOnlyTheTime() {
        assertEquals("1m 56s", ParkourLobby.cleanTime("&8[&dAL!&8] &5ALParkour &7➜ &r1m 56s"));
        assertEquals("1m 56s", ParkourLobby.cleanTime("[AL!] ALParkour ➜ r1m 56s"));
        assertEquals("42s", ParkourLobby.cleanTime("§a42s"));
        assertEquals("1h 2m 3s", ParkourLobby.cleanTime("1h 2m 3s"));
        assertEquals("---", ParkourLobby.cleanTime("[AL!] ALParkour ➜ ---"));
    }
}
