/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LegacyCodes;
import com.sasha.lobby.config.LobbyConfig;
import com.sasha.lobby.items.ChatColors;
import com.sasha.lobby.items.Settings;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Join, quit and first-join messages (each player picks whose they see, and donors and staff can have
 * their own sounds), the chat format, and mentions: saying a player's name pings them, and @Name also
 * highlights it in their chat.
 */
public final class ChatListener implements Listener {

    private static final String MESSAGE = "%message%";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Lobby plugin;

    public ChatListener(Lobby plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- join / leave

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        LobbyConfig.JoinMessages messages = plugin.lobbyConfig().joinMessages();
        String format = !player.hasPlayedBefore() && !messages.firstJoin().isEmpty() ? messages.firstJoin() : messages.join();
        event.joinMessage(null);
        if (format.isEmpty()) return;
        String sound = plugin.settings().isStaff(player) ? messages.staffJoinSound()
                : plugin.settings().isDonor(player) ? messages.donorJoinSound() : "";
        if (!plugin.coreLink().coreSeen()) {
            broadcast(player, plugin.playerFormat().render(player, format), Settings.Setting.JOIN_MESSAGES, sound, false);
            return;
        }
        // With Core on the proxy, wait a moment to hear whether they're vanished (then only staff are told).
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            broadcast(player, plugin.playerFormat().render(player, format), Settings.Setting.JOIN_MESSAGES, sound, false);
        }, 10L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        LobbyConfig.JoinMessages messages = plugin.lobbyConfig().joinMessages();
        event.quitMessage(null);
        if (messages.quit().isEmpty()) return;
        String sound = plugin.settings().isStaff(player) ? messages.staffLeaveSound()
                : plugin.settings().isDonor(player) ? messages.donorLeaveSound() : "";
        broadcast(player, plugin.playerFormat().render(player, messages.quit()), Settings.Setting.LEAVE_MESSAGES, sound, true);
    }

    /** Sends a join/leave message (and its sound) to everyone whose setting allows it, and to the console. */
    private void broadcast(Player subject, Component message, Settings.Setting setting, String sound, boolean leaving) {
        plugin.getServer().getConsoleSender().sendMessage(message);
        float volume = plugin.lobbyConfig().joinMessages().soundVolume();
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (leaving && viewer == subject) continue;
            if (!plugin.settings().shows(viewer, setting, subject) || plugin.coreLink().hiddenFrom(viewer, subject)) continue;
            viewer.sendMessage(message);
            playSound(viewer, sound, volume, 1f);
        }
    }

    private void playSound(Player player, String sound, float volume, float pitch) {
        if (sound == null || sound.isEmpty()) return;
        try {
            player.playSound(Sound.sound(Key.key(sound), Sound.Source.PLAYER, volume, pitch), Sound.Emitter.self());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("'" + sound + "' in config.yml is not a sound name");
        }
    }

    // ---------------------------------------------------------------- chat

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        // Each player's Chat setting decides whose messages they see (their own always show).
        // Nobody sees chat from players they /ignore (Core on the proxy keeps the list).
        event.viewers().removeIf(viewer -> viewer instanceof Player other
                && (!plugin.settings().shows(other, Settings.Setting.CHAT, player) || plugin.coreLink().ignores(other, player)));

        String typed = PlainTextComponentSerializer.plainText().serialize(event.message());
        Set<UUID> highlighted = mentions(player, typed, event.viewers());

        LobbyConfig.Chat chat = plugin.lobbyConfig().chat();
        if (!chat.enabled()) return;
        String format = plugin.playerFormat().prefix(player).isEmpty() ? chat.defaultFormat() : chat.rankedFormat();

        // The format around %message% gets colours and the font; what the player typed is left exactly as is.
        int split = format.indexOf(MESSAGE);
        String before = split < 0 ? format : format.substring(0, split);
        String after = split < 0 ? "" : format.substring(split + MESSAGE.length());
        Component head = before.isEmpty() ? Component.empty() : plugin.playerFormat().render(player, before);
        Component tail = after.isEmpty() ? Component.empty() : plugin.playerFormat().render(player, after);

        // Like old-style codes, the message takes the colour written right before it (&7 → grey, &f → white),
        // unless the player picked a chat colour.
        LobbyConfig.ChatColor chosen = plugin.lobbyConfig().chatColors().enabled() ? plugin.chatColors().chosen(player) : null;
        TextColor formatColour = LegacyCodes.lastColor(before);
        Function<String, Component> paint = text -> chosen != null
                ? ChatColors.paint(chosen.color(), Component.text(text))
                : Component.text(text).colorIfAbsent(formatColour);

        Component line = split < 0 ? head : Component.empty().append(head).append(paint.apply(typed)).append(tail);
        if (highlighted.isEmpty() || split < 0) {
            event.renderer(ChatRenderer.viewerUnaware((source, displayName, message) -> line));
            return;
        }
        // Someone was @mentioned: in their chat, their @Name stands out.
        event.renderer((source, displayName, message, viewer) -> {
            if (!(viewer instanceof Player target) || !highlighted.contains(target.getUniqueId())) return line;
            return Component.empty().append(head).append(highlight(typed, target.getName(), paint)).append(tail);
        });
    }

    /**
     * Pings everyone whose name is in the message (and who can see it), and returns who was @mentioned,
     * for highlighting.
     */
    private Set<UUID> mentions(Player sender, String typed, Set<Audience> viewers) {
        Set<UUID> highlighted = new HashSet<>();
        LobbyConfig.Mentions config = plugin.lobbyConfig().mentions();
        if (!config.enabled()) return highlighted;
        for (Player target : plugin.getServer().getOnlinePlayers()) {
            if (target == sender || !viewers.contains(target)) continue;
            Matcher named = namePattern(target.getName(), false).matcher(typed);
            if (!named.find()) continue;
            if (namePattern(target.getName(), true).matcher(typed).find()) highlighted.add(target.getUniqueId());
            if (plugin.settings().on(target, Settings.Setting.MENTIONS)) {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> playSound(target, config.sound(), config.volume(), config.pitch()));
            }
        }
        return highlighted;
    }

    /** A player's name as a whole word (any case), or only as @Name. */
    static Pattern namePattern(String name, boolean atOnly) {
        return Pattern.compile("(?<![A-Za-z0-9_" + (atOnly ? "" : "@") + "])" + (atOnly ? "@" : "@?")
                + Pattern.quote(name) + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
    }

    /** The message with every @Name of the target in the highlight style; the rest painted as usual. */
    private Component highlight(String typed, String name, Function<String, Component> paint) {
        String style = plugin.lobbyConfig().mentions().highlight();
        TextComponent.Builder out = Component.text();
        Matcher matcher = namePattern(name, true).matcher(typed);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) out.append(paint.apply(typed.substring(last, matcher.start())));
            out.append(MM.deserialize(style + MM.escapeTags(matcher.group())));
            last = matcher.end();
        }
        if (last < typed.length()) out.append(paint.apply(typed.substring(last)));
        return out.build();
    }

    /** For tests: the parts of a message that are @mentions of the name. */
    static List<String> atMentions(String typed, String name) {
        return namePattern(name, true).matcher(typed).results().map(java.util.regex.MatchResult::group).toList();
    }
}
