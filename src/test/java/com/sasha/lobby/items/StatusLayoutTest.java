package com.sasha.lobby.items;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusLayoutTest {

    @Test
    void partRowsAreCentred() {
        assertEquals(List.of(3), Statuses.columns(1));
        assertEquals(List.of(2, 4), Statuses.columns(2));
        assertEquals(List.of(1, 2, 4, 5), Statuses.columns(4));
        assertEquals(List.of(1, 2, 3, 4, 5), Statuses.columns(5));
    }

    @Test
    void twentyFiveStatusesFillThreeRowsAndCentreFour() {
        List<Integer> slots = Statuses.slots(25);
        assertEquals(25, slots.size());
        assertEquals(10, slots.get(0));
        assertEquals(34, slots.get(20));
        assertEquals(List.of(38, 39, 41, 42), slots.subList(21, 25));
    }
}
