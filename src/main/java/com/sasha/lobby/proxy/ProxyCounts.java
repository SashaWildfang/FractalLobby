/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.proxy;

import com.sasha.lobby.Lobby;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player counts for every server on the network, asked from the proxy over the standard "BungeeCord"
 * plugin-messaging channel. Velocity answers these too (bungee-plugin-message-channel, on by default),
 * so no proxy plugin is needed. Requests travel through any online player, so counts only refresh while
 * someone is in the lobby; the last known values are kept in between.
 */
public final class ProxyCounts implements PluginMessageListener {

    static final String CHANNEL = "BungeeCord";
    /** The proxy's name for "every server". */
    public static final String ALL = "ALL";

    private final Lobby plugin;
    private final Map<String, Integer> counts = new ConcurrentHashMap<>();
    private BukkitTask task;
    /** This server's name on the proxy (asked once); its count is taken live from here instead of polled. */
    private volatile String ownName;

    public ProxyCounts(Lobby plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        restartTask();
    }

    /** (Re)starts polling with the current config. */
    public void restartTask() {
        if (task != null) task.cancel();
        if (!plugin.lobbyConfig().proxy().enabled()) return;
        long period = plugin.lobbyConfig().proxy().refreshTicks();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::requestAll, 20L, period);
    }

    /**
     * Someone joined or left this server: ask again now and once more a moment later (the proxy
     * takes a little while to see a player arrive on their next server), then redraw.
     */
    public void requestSoon() {
        if (!plugin.lobbyConfig().proxy().enabled()) return;
        plugin.getServer().getScheduler().runTask(plugin, this::requestAll);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::requestAll, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
    }

    /** Players on a proxy server (case-insensitive), or on the whole network for "all". 0 until known. */
    public int count(String server) {
        String key = server.equalsIgnoreCase("all") ? ALL : server.toLowerCase(Locale.ROOT);
        if (key.equals(ownName)) return plugin.getServer().getOnlinePlayers().size();
        return counts.getOrDefault(key, 0);
    }

    private void requestAll() {
        Player carrier = plugin.getServer().getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier == null) return;
        if (ownName == null) carrier.sendPluginMessage(plugin, CHANNEL, simple("GetServer"));
        List<String> servers = plugin.lobbyConfig().proxy().servers();
        for (String server : servers) carrier.sendPluginMessage(plugin, CHANNEL, request(server));
        carrier.sendPluginMessage(plugin, CHANNEL, request(ALL));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        decodeServerName(message).ifPresent(name -> {
            ownName = name.toLowerCase(Locale.ROOT);
            plugin.display().updateSoon();
        });
        decode(message).ifPresent(reply -> {
            String key = reply.server().equals(ALL) ? ALL : reply.server().toLowerCase(Locale.ROOT);
            Integer before = counts.put(key, reply.count());
            if (before == null || before != reply.count()) plugin.display().updateSoon(); // show changes right away
        });
    }

    /** Sends a player to another server on the network. */
    public void connect(Player player, String server) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(server);
        } catch (IOException e) {
            throw new IllegalStateException(e); // writing to memory can't fail
        }
        player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
    }

    /** A reply to a PlayerCount request. */
    record Reply(String server, int count) {
    }

    /** Builds a "PlayerCount" request for one server (or {@link #ALL}). */
    static byte[] request(String server) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("PlayerCount");
            out.writeUTF(server);
        } catch (IOException e) {
            throw new IllegalStateException(e); // writing to memory can't fail
        }
        return bytes.toByteArray();
    }

    static byte[] simple(String subchannel) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(subchannel);
        } catch (IOException e) {
            throw new IllegalStateException(e); // writing to memory can't fail
        }
        return bytes.toByteArray();
    }

    /** Reads a GetServer reply (this server's name on the proxy). */
    static Optional<String> decodeServerName(byte[] message) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!"GetServer".equals(in.readUTF())) return Optional.empty();
            return Optional.of(in.readUTF());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Reads a PlayerCount reply; anything else on the channel is ignored. */
    static Optional<Reply> decode(byte[] message) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!"PlayerCount".equals(in.readUTF())) return Optional.empty();
            return Optional.of(new Reply(in.readUTF(), in.readInt()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
