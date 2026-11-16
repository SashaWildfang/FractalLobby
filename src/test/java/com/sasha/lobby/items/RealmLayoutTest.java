/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.items;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RealmLayoutTest {

    @Test
    void centresOnTheMiddleRow() {
        assertArrayEquals(new int[] {13}, RealmSelector.centered(1, 3));
        assertArrayEquals(new int[] {12, 14}, RealmSelector.centered(2, 3));
        assertArrayEquals(new int[] {11, 13, 15}, RealmSelector.centered(3, 3));
        assertArrayEquals(new int[] {10, 12, 14, 16}, RealmSelector.centered(4, 3));
        assertArrayEquals(new int[] {4}, RealmSelector.centered(1, 1));
        assertArrayEquals(new int[] {22}, RealmSelector.centered(1, 5));
        assertNull(RealmSelector.centered(10, 3));
    }
}
