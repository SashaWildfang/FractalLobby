/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.proxy;

import com.sasha.lobby.Lobby;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The link to Core on the proxy (channel {@code fractal:core}). Core tells the lobby each player's
 * network settings (private messages, message sounds, and for staff social spy, command spy and vanish)
 * and who they ignore; the Settings menu shows and changes them, chat hides ignored players and vanished
 * staff are hidden from everyone but staff. Without Core on the proxy nothing arrives and none of this
 * shows.
 */
public final class CoreLink implements PluginMessageListener, Listener {

    public static final String CHANNEL = "fractal:core";

    /** Core's toggle ids. "-off" toggles are on when the feature is off. */
    public static final String MESSAGES_OFF = "messages-off";
    public static final String MESSAGE_SOUNDS_OFF = "message-sounds-off";
    public static final String SOCIAL_SPY = "social-spy";
    public static final String COMMAND_SPY = "command-spy";
    public static final String VANISH = "vanish";
    public static final String FRIEND_REQUESTS_OFF = "friend-requests-off";
    public static final String FRIEND_NOTIFY_OFF = "friend-notify-off";
    public static final String PARTY_INVITES_OFF = "party-invites-off";

    /** A friend as Core last sent them: their name, the server they're on ("" when offline), since when friends. */
    public record Friend(String name, String server, long since) {
        public boolean online() {
            return !server.isEmpty();
        }
    }

    /**
     * A player's network settings as Core last sent them: the toggles they may change, who they ignore, their
     * network playtime and their friends.
     */
    public record State(Map<String, Boolean> toggles, Map<UUID, String> ignored, long playtime, long receivedAt,
                        Map<UUID, Friend> friends) {
        /** Network playtime right now in milliseconds, or -1 if Core didn't send it (an older Core). */
        public long playtimeNow() {
            return playtime < 0 ? -1 : playtime + (System.currentTimeMillis() - receivedAt);
        }

        public boolean has(String toggle) {
            return toggles.containsKey(toggle);
        }

        public boolean on(String toggle) {
            return Boolean.TRUE.equals(toggles.get(toggle));
        }
    }

    private final Lobby plugin;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    /** Called on the main thread with a player whose state just arrived or changed. */
    private Consumer<Player> onUpdate = player -> {
    };
    /** Whether Core has ever answered (then join messages wait a moment for vanish to be known). */
    private volatile boolean coreSeen;
    private volatile List<TopPlaytime> topPlaytime = List.of();

    public CoreLink(Lobby plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        // After /reload, ask again for everyone already here.
        for (Player player : plugin.getServer().getOnlinePlayers()) send(player, "Ask", out -> {
        });
    }

    public void stop() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void onUpdate(Consumer<Player> listener) {
        this.onUpdate = listener;
    }

    // ---------------------------------------------------------------- reading

    /** The player's network settings, or null if Core hasn't sent them (not installed, or not yet). */
    public State state(Player player) {
        return states.get(player.getUniqueId());
    }

    public boolean coreSeen() {
        return coreSeen;
    }

    /** Whether the viewer ignores the sender. */
    public boolean ignores(Player viewer, Player sender) {
        State state = states.get(viewer.getUniqueId());
        return state != null && state.ignored().containsKey(sender.getUniqueId());
    }

    public boolean vanished(Player player) {
        State state = states.get(player.getUniqueId());
        return state != null && state.on(VANISH);
    }

    /** Whether the subject is kept out of the viewer's sight: vanished staff, for anyone who isn't staff. */
    public boolean hiddenFrom(Player viewer, Player subject) {
        return viewer != subject && vanished(subject) && !plugin.settings().isStaff(viewer);
    }

    // ---------------------------------------------------------------- changing (Core has the last word)

    /** Asks Core to change a toggle; the menu shows it straight away and Core's reply confirms it. */
    public void set(Player player, String toggle, boolean on) {
        State state = states.get(player.getUniqueId());
        if (state == null || !state.has(toggle)) return;
        Map<String, Boolean> toggles = new LinkedHashMap<>(state.toggles());
        toggles.put(toggle, on);
        states.put(player.getUniqueId(), new State(Collections.unmodifiableMap(toggles), state.ignored(), state.playtime(),
                state.receivedAt(), state.friends()));
        send(player, "Set", out -> {
            out.writeUTF(toggle);
            out.writeBoolean(on);
        });
    }

    public void unignore(Player player, UUID ignored) {
        State state = states.get(player.getUniqueId());
        if (state == null) return;
        Map<UUID, String> list = new LinkedHashMap<>(state.ignored());
        list.remove(ignored);
        states.put(player.getUniqueId(), new State(state.toggles(), Collections.unmodifiableMap(list), state.playtime(),
                state.receivedAt(), state.friends()));
        send(player, "Unignore", out -> out.writeUTF(ignored.toString()));
    }

    /** Asks Core to end a friendship (from the friends menu). */
    public void unfriend(Player player, UUID friend) {
        State state = states.get(player.getUniqueId());
        if (state == null) return;
        Map<UUID, Friend> list = new LinkedHashMap<>(state.friends());
        list.remove(friend);
        states.put(player.getUniqueId(), new State(state.toggles(), state.ignored(), state.playtime(), state.receivedAt(),
                Collections.unmodifiableMap(list)));
        send(player, "Unfriend", out -> out.writeUTF(friend.toString()));
    }

    /** Asks Core to send the player to the server their friend is on. */
    public void joinFriend(Player player, UUID friend) {
        send(player, "Goto", out -> out.writeUTF(friend.toString()));
    }

    /** Whether the two are friends (as the viewer's state from Core says). */
    public boolean friends(Player viewer, Player other) {
        State state = states.get(viewer.getUniqueId());
        return state != null && state.friends().containsKey(other.getUniqueId());
    }

    private interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    private void send(Player player, String type, Body body) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(type);
            out.writeUTF(player.getUniqueId().toString());
            body.write(out);
        } catch (IOException e) {
            throw new IllegalStateException(e); // writing to memory can't fail
        }
        player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
    }

    // ---------------------------------------------------------------- from Core

    @Override
    public void onPluginMessageReceived(String channel, Player carrier, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        java.util.Set<String> maintenance = decodeMaintenance(message);
        if (maintenance != null) {
            coreSeen = true;
            plugin.serverStatus().coreMaintenance(maintenance);
            return;
        }
        List<TopPlaytime> top = decodeTop(message);
        if (top != null) {
            coreSeen = true;
            topPlaytime = top;
            return;
        }
        Decoded decoded = decode(message);
        if (decoded == null) return;
        coreSeen = true;
        Player player = plugin.getServer().getPlayer(decoded.player());
        if (player == null) return;
        states.put(decoded.player(), decoded.state());
        onUpdate.accept(player);
    }

    record Decoded(UUID player, State state) {
    }

    /** One line of the network playtime leaderboard. */
    public record TopPlaytime(String name, long millis) {
    }

    /** The longest playtimes on the network, most first (empty until Core answers). */
    public List<TopPlaytime> topPlaytime() {
        return topPlaytime;
    }

    /** Asks Core for the longest playtimes; the answer arrives a moment later. Needs someone online. */
    public void requestTopPlaytime(int limit) {
        plugin.getServer().getOnlinePlayers().stream().findFirst().ifPresent(carrier -> send(carrier, "Top", out -> out.writeInt(limit)));
    }

    /** A "Maintenance" message from Core (boolean network, int count, count x server name), or null for anything else. */
    static java.util.Set<String> decodeMaintenance(byte[] message) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!"Maintenance".equals(in.readUTF())) return null;
            in.readBoolean(); // the whole network: nobody would be here to see it
            java.util.Set<String> servers = new java.util.HashSet<>();
            for (int i = in.readInt(); i > 0; i--) servers.add(in.readUTF());
            return servers;
        } catch (IOException e) {
            return null;
        }
    }

    /** A "Top" message from Core, or null for anything else. */
    static List<TopPlaytime> decodeTop(byte[] message) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!"Top".equals(in.readUTF())) return null;
            List<TopPlaytime> top = new java.util.ArrayList<>();
            for (int i = in.readInt(); i > 0; i--) top.add(new TopPlaytime(in.readUTF(), in.readLong()));
            return List.copyOf(top);
        } catch (IOException e) {
            return null;
        }
    }

    /** A length of time like Core's /playtime shows it: "3d 4h 12m", "4h 12m", "12m". */
    public static String duration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86400, hours = seconds % 86400 / 3600, minutes = seconds % 3600 / 60;
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    /** A "State" message from Core, or null for anything else (like the older "Ignores"). */
    static Decoded decode(byte[] message) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!"State".equals(in.readUTF())) return null;
            UUID player = UUID.fromString(in.readUTF());
            Map<String, Boolean> toggles = new LinkedHashMap<>();
            for (int i = in.readInt(); i > 0; i--) toggles.put(in.readUTF(), in.readBoolean());
            Map<UUID, String> ignored = new LinkedHashMap<>();
            for (int i = in.readInt(); i > 0; i--) ignored.put(UUID.fromString(in.readUTF()), in.readUTF());
            long playtime = in.available() >= 8 ? in.readLong() : -1; // older Cores don't send it
            Map<UUID, Friend> friends = new LinkedHashMap<>();
            if (in.available() >= 4) { // Core 1.5 and later
                for (int i = in.readInt(); i > 0; i--) {
                    friends.put(UUID.fromString(in.readUTF()), new Friend(in.readUTF(), in.readUTF(), in.readLong()));
                }
            }
            return new Decoded(player, new State(Collections.unmodifiableMap(toggles), Collections.unmodifiableMap(ignored),
                    playtime, System.currentTimeMillis(), Collections.unmodifiableMap(friends)));
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Kept until after other quit handlers (the leave message checks vanish), then dropped.
        UUID id = event.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.getServer().getPlayer(id) == null) states.remove(id);
        });
    }
}
