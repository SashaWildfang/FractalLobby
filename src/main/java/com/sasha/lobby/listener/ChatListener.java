/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LegacyCodes;
import com.sasha.lobby.config.LobbyConfig;
import com.sasha.lobby.items.Settings;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Join, quit and first-join messages, and the chat format. */
public final class ChatListener implements Listener {

    private static final String MESSAGE = "%message%";

    private final Lobby plugin;

    public ChatListener(Lobby plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        LobbyConfig.JoinMessages messages = plugin.lobbyConfig().joinMessages();
        String format = !player.hasPlayedBefore() && !messages.firstJoin().isEmpty() ? messages.firstJoin() : messages.join();
        event.joinMessage(null);
        if (!format.isEmpty()) broadcast(plugin.playerFormat().render(player, format), Settings.Setting.JOIN_MESSAGES, null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        String format = plugin.lobbyConfig().joinMessages().quit();
        event.quitMessage(null);
        if (!format.isEmpty()) {
            broadcast(plugin.playerFormat().render(event.getPlayer(), format), Settings.Setting.LEAVE_MESSAGES, event.getPlayer());
        }
    }

    /** Sends a join/leave message to everyone who has that setting on, and to the console. */
    private void broadcast(Component message, Settings.Setting setting, Player except) {
        plugin.getServer().getConsoleSender().sendMessage(message);
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (viewer != except && plugin.settings().on(viewer, setting)) viewer.sendMessage(message);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        // Players who turned chat off in their settings don't see others' messages (they still see their own).
        event.viewers().removeIf(viewer -> viewer instanceof Player other && other != player
                && !plugin.settings().on(other, Settings.Setting.CHAT));
        LobbyConfig.Chat chat = plugin.lobbyConfig().chat();
        if (!chat.enabled()) return;
        String format = plugin.playerFormat().prefix(player).isEmpty() ? chat.defaultFormat() : chat.rankedFormat();

        // The format around %message% gets colours and the font; what the player typed is left exactly as is.
        int split = format.indexOf(MESSAGE);
        String before = split < 0 ? format : format.substring(0, split);
        String after = split < 0 ? "" : format.substring(split + MESSAGE.length());
        Component head = before.isEmpty() ? Component.empty() : plugin.playerFormat().render(player, before);
        Component tail = after.isEmpty() ? Component.empty() : plugin.playerFormat().render(player, after);
        // Like old-style codes, the message takes the colour written right before it (&7 → grey, &f → white).
        LobbyConfig.ChatColor chosen = plugin.lobbyConfig().chatColors().enabled() ? plugin.chatColors().chosen(player) : null;
        Component message = chosen != null
                ? com.sasha.lobby.items.ChatColors.paint(chosen.color(), event.message())
                : event.message().colorIfAbsent(LegacyCodes.lastColor(before));
        Component line = split < 0 ? head : Component.empty().append(head).append(message).append(tail);
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, typed) -> line));
    }
}
