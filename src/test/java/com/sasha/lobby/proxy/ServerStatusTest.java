/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.proxy;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerStatusTest {

    private static final List<String> WORDS = List.of("maintenance");

    @Test
    void readsPlayersAndFull() {
        assertEquals(new ServerStatus.Status(ServerStatus.State.ONLINE, 100),
                ServerStatus.read("{\"version\":{\"name\":\"Paper 1.21.11\"},\"players\":{\"online\":4,\"max\":100},\"description\":\"A server\"}", WORDS));
        assertEquals(new ServerStatus.Status(ServerStatus.State.FULL, 50),
                ServerStatus.read("{\"players\":{\"online\":50,\"max\":50},\"description\":{\"text\":\"hi\"}}", WORDS));
    }

    /** Maintenance plugins put it in the MOTD (as text or a component) or the version name. */
    @Test
    void spotsMaintenance() {
        assertEquals(ServerStatus.State.MAINTENANCE, ServerStatus.read(
                "{\"players\":{\"online\":0,\"max\":0},\"description\":{\"text\":\"\",\"extra\":[{\"text\":\"Under MAINTENANCE\"}]}}", WORDS).state());
        assertEquals(ServerStatus.State.MAINTENANCE, ServerStatus.read(
                "{\"version\":{\"name\":\"Maintenance\"},\"players\":{\"online\":0,\"max\":20},\"description\":\"x\"}", WORDS).state());
    }

    @Test
    void readsTheProxysServerAddress() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("ServerIP");
            out.writeUTF("survival");
            out.writeUTF("127.0.0.1");
            out.writeShort(30066);
        }
        assertEquals(new ProxyCounts.ServerIp("survival", "127.0.0.1", 30066), ProxyCounts.decodeServerIp(bytes.toByteArray()).orElseThrow());
    }
}
