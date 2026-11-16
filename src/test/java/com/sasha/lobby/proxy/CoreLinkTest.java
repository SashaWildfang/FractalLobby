/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.proxy;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreLinkTest {

    @Test
    void readsCoreState() throws IOException {
        UUID player = UUID.randomUUID();
        UUID ignored = UUID.randomUUID();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("State");
            out.writeUTF(player.toString());
            out.writeInt(2);
            out.writeUTF(CoreLink.MESSAGES_OFF);
            out.writeBoolean(true);
            out.writeUTF(CoreLink.VANISH);
            out.writeBoolean(false);
            out.writeInt(1);
            out.writeUTF(ignored.toString());
            out.writeUTF("Steve");
        }
        CoreLink.Decoded decoded = CoreLink.decode(bytes.toByteArray());
        assertEquals(player, decoded.player());
        assertTrue(decoded.state().on(CoreLink.MESSAGES_OFF));
        assertTrue(decoded.state().has(CoreLink.VANISH));
        assertFalse(decoded.state().on(CoreLink.VANISH));
        assertFalse(decoded.state().has(CoreLink.SOCIAL_SPY));
        assertEquals("Steve", decoded.state().ignored().get(ignored));
    }

    @Test
    void skipsOtherMessages() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Ignores");
            out.writeUTF(UUID.randomUUID().toString());
            out.writeInt(0);
        }
        assertNull(CoreLink.decode(bytes.toByteArray()));
        assertNull(CoreLink.decode(new byte[] {1, 2}));
    }
}
