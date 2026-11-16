/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.proxy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether each realm is up: Online, Full, Maintenance or Offline. Each realm server is pinged the way
 * the game's server list does it, at the address the proxy has for it (asked over the BungeeCord
 * channel) or one set in config.yml. Pings run off the main thread.
 */
public final class ServerStatus {

    public enum State {
        ONLINE, FULL, MAINTENANCE, OFFLINE,
        /** Not pinged (no address yet, or status checks are off): treated as online. */
        UNKNOWN
    }

    /** A realm's state, with its max players (0 when unknown). */
    public record Status(State state, int max) {
        static final Status UNKNOWN = new Status(State.UNKNOWN, 0);
    }

    private static final int TIMEOUT_MILLIS = 1500;

    private final Lobby plugin;
    private final Map<String, Status> statuses = new ConcurrentHashMap<>();
    /** When each realm was last seen up (so a realm that just went down shows as restarting for a while). */
    private final Map<String, Long> lastUp = new ConcurrentHashMap<>();
    /** Addresses the proxy gave, by server name. */
    private final Map<String, InetSocketAddress> proxyAddresses = new ConcurrentHashMap<>();
    private BukkitTask task;

    public ServerStatus(Lobby plugin) {
        this.plugin = plugin;
    }

    /** (Re)starts pinging with the current config. */
    public void start() {
        if (task != null) task.cancel();
        LobbyConfig.Selector config = plugin.lobbyConfig().selector();
        if (!config.statusEnabled()) {
            statuses.clear();
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::pingAll, 20L, config.statusRefreshTicks());
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public Status status(String server) {
        LobbyConfig.SelectorServer realm = realm(server);
        if (realm != null && realm.maintenance()) return new Status(State.MAINTENANCE, statuses.getOrDefault(key(server), Status.UNKNOWN).max());
        return statuses.getOrDefault(key(server), Status.UNKNOWN);
    }

    /**
     * A realm that's down now but was up within realm-selector.status.restart-seconds: most likely restarting.
     */
    public boolean restarting(String server) {
        if (status(server).state() != State.OFFLINE) return false;
        Long up = lastUp.get(key(server));
        long window = Math.max(0, plugin.getConfig().getLong("realm-selector.status.restart-seconds", 180)) * 1000;
        return up != null && System.currentTimeMillis() - up < window;
    }

    /** Servers whose address should be asked from the proxy (no address set in config.yml, none known yet). */
    List<String> needAddress() {
        return plugin.lobbyConfig().selector().servers().stream()
                .filter(realm -> realm.address().isEmpty() && !proxyAddresses.containsKey(key(realm.server())))
                .map(LobbyConfig.SelectorServer::server).toList();
    }

    void proxyAddress(String server, String host, int port) {
        InetSocketAddress before = proxyAddresses.put(key(server), InetSocketAddress.createUnresolved(host, port));
        // First time we can reach it: check it now instead of at the next refresh.
        if (before == null && plugin.lobbyConfig().selector().statusEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::pingAll);
        }
    }

    /**
     * Whether we know how a realm is doing yet: it's been checked at least once (or status checks are off,
     * or config.yml marks it as under maintenance). Until then the realm selector leaves it out, so a realm
     * that's down doesn't flash up in the menu after a restart.
     */
    public boolean known(String server) {
        if (!plugin.lobbyConfig().selector().statusEnabled()) return true;
        LobbyConfig.SelectorServer realm = realm(server);
        return realm != null && realm.maintenance() || statuses.containsKey(key(server));
    }

    private LobbyConfig.SelectorServer realm(String server) {
        for (LobbyConfig.SelectorServer realm : plugin.lobbyConfig().selector().servers()) {
            if (realm.server().equalsIgnoreCase(server)) return realm;
        }
        return null;
    }

    private static String key(String server) {
        return server.toLowerCase(Locale.ROOT);
    }

    private void pingAll() {
        LobbyConfig.Selector config = plugin.lobbyConfig().selector();
        for (LobbyConfig.SelectorServer realm : config.servers()) {
            InetSocketAddress address = address(realm);
            if (address == null) continue;
            Status status;
            try {
                status = read(ping(address), config.maintenanceWords());
            } catch (IOException | RuntimeException e) {
                // Unreachable from here. If the proxy still counts players on it, it's up after all
                // (e.g. the lobby can't reach that machine directly).
                status = plugin.proxyCounts().count(realm.server()) > 0 ? Status.UNKNOWN : new Status(State.OFFLINE, 0);
            }
            statuses.put(key(realm.server()), status);
            if (status.state() != State.OFFLINE) lastUp.put(key(realm.server()), System.currentTimeMillis());
        }
    }

    private InetSocketAddress address(LobbyConfig.SelectorServer realm) {
        if (!realm.address().isEmpty()) {
            String[] parts = realm.address().split(":");
            int port = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 25565;
            return new InetSocketAddress(parts[0].trim(), port);
        }
        InetSocketAddress fromProxy = proxyAddresses.get(key(realm.server()));
        if (fromProxy == null) return null;
        // "0.0.0.0" or an empty host means "this machine" for a server that listens on every address.
        String host = fromProxy.getHostString();
        if (host.isEmpty() || host.equals("0.0.0.0")) host = "127.0.0.1";
        return new InetSocketAddress(host, fromProxy.getPort());
    }

    /** Reads a ping reply: players, max players, and whether its MOTD or version says maintenance. */
    static Status read(String json, List<String> maintenanceWords) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int online = 0;
        int max = 0;
        if (root.has("players")) {
            JsonObject players = root.getAsJsonObject("players");
            online = players.has("online") ? players.get("online").getAsInt() : 0;
            max = players.has("max") ? players.get("max").getAsInt() : 0;
        }
        StringBuilder text = new StringBuilder();
        if (root.has("version") && root.getAsJsonObject("version").has("name")) {
            text.append(root.getAsJsonObject("version").get("name").getAsString()).append(' ');
        }
        if (root.has("description")) text.append(plain(root.get("description")));
        String lower = text.toString().toLowerCase(Locale.ROOT);
        for (String word : maintenanceWords) {
            if (!word.isBlank() && lower.contains(word.toLowerCase(Locale.ROOT))) return new Status(State.MAINTENANCE, max);
        }
        return new Status(max > 0 && online >= max ? State.FULL : State.ONLINE, max);
    }

    private static String plain(JsonElement description) {
        if (description.isJsonPrimitive()) return description.getAsString();
        return PlainTextComponentSerializer.plainText().serialize(GsonComponentSerializer.gson().deserializeFromTree(description));
    }

    // ---------------------------------------------------------------- the server list ping

    /** Asks a server for its status, like the multiplayer screen does; returns the JSON reply. */
    private static String ping(InetSocketAddress address) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address.getHostString(), address.getPort()), TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);
            OutputStream out = socket.getOutputStream();

            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            DataOutputStream body = new DataOutputStream(handshake);
            writeVarInt(body, 0x00); // handshake
            writeVarInt(body, -1); // protocol version: "just asking"
            byte[] host = address.getHostString().getBytes(StandardCharsets.UTF_8);
            writeVarInt(body, host.length);
            body.write(host);
            body.writeShort(address.getPort());
            writeVarInt(body, 1); // next: status
            sendPacket(out, handshake.toByteArray());
            sendPacket(out, new byte[] {0x00}); // status request

            DataInputStream in = new DataInputStream(socket.getInputStream());
            readVarInt(in); // packet length
            if (readVarInt(in) != 0x00) throw new IOException("unexpected reply");
            byte[] json = new byte[readVarInt(in)];
            in.readFully(json);
            return new String(json, StandardCharsets.UTF_8);
        }
    }

    private static void sendPacket(OutputStream out, byte[] packet) throws IOException {
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        writeVarInt(new DataOutputStream(framed), packet.length);
        framed.write(packet);
        out.write(framed.toByteArray());
        out.flush();
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.read();
            if (b < 0) throw new IOException("connection closed");
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("bad varint");
    }
}
