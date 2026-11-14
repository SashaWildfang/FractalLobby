/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.proxy;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyCountsTest {

    /** The request must be exactly what the proxy expects: "PlayerCount" then the server name. */
    @Test
    void requestMatchesTheProxyFormat() throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(ProxyCounts.request("survival")));
        assertEquals("PlayerCount", in.readUTF());
        assertEquals("survival", in.readUTF());
        assertEquals(0, in.available());
    }

    @Test
    void readsPlayerCountReplies() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF("PlayerCount");
        out.writeUTF("survival");
        out.writeInt(4);

        ProxyCounts.Reply reply = ProxyCounts.decode(bytes.toByteArray()).orElseThrow();
        assertEquals("survival", reply.server());
        assertEquals(4, reply.count());
    }

    /** Other plugins use the same channel; their messages must be ignored, not crash anything. */
    @Test
    void ignoresOtherMessages() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeUTF("GetServers");
        assertTrue(ProxyCounts.decode(bytes.toByteArray()).isEmpty());
        assertTrue(ProxyCounts.decode(new byte[] {1, 2}).isEmpty());
    }
}
