/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.scoreboard;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LegacyCodes;
import com.sasha.lobby.config.LobbyConfig;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.hook.PlaceholderHook;
import com.sasha.lobby.items.Settings;
import com.sasha.lobby.proxy.ProxyCounts;
import com.sasha.lobby.proxy.ServerStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * Everything drawn around the game: the sidebar, nametags above heads, and the tab list (header, footer
 * and rank-prefixed names). Each player gets their own scoreboard (the sidebar needs it), so nametag teams
 * are kept in sync on every board. One loop updates it all, sending only what changed.
 */
public final class DisplayService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final Lobby plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Sidebar> sidebars = new HashMap<>();
    /** What each player's nametag currently shows, so teams are only touched when a rank changes. */
    private final Map<UUID, Nametag> nametags = new HashMap<>();
    private final Map<UUID, Component[]> tabShown = new HashMap<>();
    private BukkitTask task;

    private record Nametag(Component prefix, Component suffix, NamedTextColor color, Component listName) {
    }

    public DisplayService(Lobby plugin) {
        this.plugin = plugin;
    }

    /** (Re)starts everything with the current config. */
    public void start() {
        stop();
        for (Player player : plugin.getServer().getOnlinePlayers()) add(player);
        long period = plugin.lobbyConfig().scoreboard().updateTicks();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateAll, period, period);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (Player player : plugin.getServer().getOnlinePlayers()) remove(player);
        boards.clear();
        sidebars.clear();
        nametags.clear();
        tabShown.clear();
    }

    /** A player joined: give them a board with everyone's nametag, and add theirs to everyone else's. */
    public void add(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        boards.put(player.getUniqueId(), board);
        if (plugin.lobbyConfig().scoreboard().enabled() && plugin.settings().on(player, Settings.Setting.SCOREBOARD)) {
            sidebars.put(player.getUniqueId(), new Sidebar(board));
        }
        player.setScoreboard(board);

        // Teams are always kept, even with nametags off: a team's colour is also the colour of /glow.
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            Nametag tag = nametags.get(other.getUniqueId());
            if (tag != null) applyTeam(board, other, tag);
        }
        refreshNametag(player, true);
        update(player);
    }

    /** A player left (or the plugin is stopping): clean up their board and their nametag everywhere. */
    public void remove(Player player) {
        UUID id = player.getUniqueId();
        Sidebar sidebar = sidebars.remove(id);
        if (sidebar != null) sidebar.remove();
        Scoreboard own = boards.remove(id);
        if (own != null && player.getScoreboard() == own) player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        nametags.remove(id);
        tabShown.remove(id);
        for (Scoreboard board : boards.values()) {
            Team team = board.getTeam(teamName(id));
            if (team != null) team.unregister();
        }
        if (plugin.lobbyConfig().tablist()) player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }

    /** Shows or hides one player's sidebar (their scoreboard setting). The tab list and nametags stay. */
    public void showSidebar(Player player, boolean show) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;
        Sidebar sidebar = sidebars.get(player.getUniqueId());
        if (!show && sidebar != null) {
            sidebar.remove();
            sidebars.remove(player.getUniqueId());
        } else if (show && sidebar == null && plugin.lobbyConfig().scoreboard().enabled()) {
            sidebars.put(player.getUniqueId(), new Sidebar(board));
            update(player);
        }
    }

    private boolean updateQueued;

    /** Redraws everything on the next tick (once, however often it's asked), e.g. when a count changes. */
    public void updateSoon() {
        if (updateQueued || task == null) return;
        updateQueued = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            updateQueued = false;
            if (task != null) updateAll();
        });
    }

    private void updateAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!boards.containsKey(player.getUniqueId())) continue;
            refreshNametag(player, false);
            update(player);
        }
    }

    // ---------------------------------------------------------------- sidebar + tab header/footer

    private static final java.util.regex.Pattern ONLINE_TAG = java.util.regex.Pattern.compile("<online:([^>]+)>");

    /** Whether a scoreboard line counts a realm that's offline or in maintenance (then it's left out). */
    static boolean aboutDownServer(String template, java.util.function.Function<String, ServerStatus.State> state) {
        var matcher = ONLINE_TAG.matcher(template);
        while (matcher.find()) {
            String server = matcher.group(1).trim();
            if (server.equalsIgnoreCase("all")) continue;
            ServerStatus.State now = state.apply(server);
            if (now == ServerStatus.State.OFFLINE || now == ServerStatus.State.MAINTENANCE) return true;
        }
        return false;
    }

    private void update(Player player) {
        Messages messages = plugin.messages();
        TagResolver tags = tags(plugin.lobbyConfig(), plugin.proxyCounts()::count,
                plugin.getServer().getOnlinePlayers().size(), player.getName());

        Sidebar sidebar = sidebars.get(player.getUniqueId());
        if (sidebar != null) {
            Component title = messages.render(placeholders(player, messages.raw("scoreboard.title")), tags);
            List<Component> lines = new ArrayList<>();
            List<Boolean> centered = new ArrayList<>();
            boolean hideDown = plugin.lobbyConfig().scoreboard().hideOfflineRealms();
            for (String template : messages.lines("scoreboard.lines")) {
                if (hideDown && aboutDownServer(template, server -> plugin.serverStatus().status(server).state())) continue;
                boolean center = template.startsWith(TextWidth.CENTER);
                if (center) template = template.substring(TextWidth.CENTER.length());
                centered.add(center);
                lines.add(messages.render(placeholders(player, template), tags));
            }
            sidebar.update(title, TextWidth.center(title, lines, centered));
        }

        if (plugin.lobbyConfig().tablist()) {
            TagResolver tabTags = TagResolver.resolver(tags, Messages.arg("ping", player.getPing()), tip(player));
            Component header = joinLines(player, "tablist.header", tabTags);
            Component footer = joinLines(player, "tablist.footer", tabTags);
            Component[] last = tabShown.get(player.getUniqueId());
            if (last == null || !last[0].equals(header) || !last[1].equals(footer)) {
                player.sendPlayerListHeaderAndFooter(header, footer);
                tabShown.put(player.getUniqueId(), new Component[] {header, footer});
            }
        }
    }

    private Component joinLines(Player player, String key, TagResolver tags) {
        List<Component> lines = new ArrayList<>();
        for (String template : plugin.messages().lines(key)) lines.add(plugin.messages().render(placeholders(player, template), tags));
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    /** <tip>: one of tablist.tips, changing every tablist.tip-seconds (the same tip for everyone at once). */
    private TagResolver tip(Player player) {
        List<String> tips = plugin.messages().lines("tablist.tips");
        if (tips.isEmpty()) return Placeholder.component("tip", Component.empty());
        long every = Math.max(3, plugin.getConfig().getLong("tablist.tip-seconds", 12)) * 1000;
        String tip = tips.get((int) (System.currentTimeMillis() / every % tips.size()));
        return Placeholder.component("tip", plugin.messages().render(placeholders(player, tip)));
    }

    private String placeholders(Player player, String template) {
        return plugin.placeholderApi() && template.indexOf('%') >= 0 ? PlaceholderHook.apply(player, template) : template;
    }

    // ---------------------------------------------------------------- nametags

    /**
     * Recomputes a player's nametag and team colour (the rank colour, also used by /glow); pushes it to every
     * board only if it changed (or if forced). With nametags off, only the colour is set.
     */
    private void refreshNametag(Player player, boolean force) {
        LobbyConfig.Nametag config = plugin.lobbyConfig().nametag();
        String format = config.enabled() ? config.format() : "%luckperms_prefix%%player%";
        int split = format.indexOf("%player%");
        String before = split < 0 ? format : format.substring(0, split);
        String after = split < 0 ? "" : format.substring(split + "%player%".length());

        // The colour right before %player% colours the name itself (teams only take named colours).
        TextColor nameColour = LegacyCodes.lastColor(plugin.playerFormat().prepare(player, before));
        Nametag tag = new Nametag(
                !config.enabled() || before.isEmpty() ? Component.empty() : plugin.playerFormat().render(player, before),
                !config.enabled() || after.isEmpty() ? Component.empty() : plugin.playerFormat().render(player, after),
                nameColour == null ? null : NamedTextColor.nearestTo(nameColour),
                config.enabled() && config.applyToTablist() ? plugin.playerFormat().render(player, format) : null);

        // Tab list order: higher LuckPerms weight first (the game sorts by this before anything else).
        int order = plugin.lobbyConfig().tablistSortByRank() ? Math.max(0, plugin.playerFormat().weight(player)) : 0;
        if (player.getPlayerListOrder() != order) player.setPlayerListOrder(order);

        if (!force && Objects.equals(nametags.get(player.getUniqueId()), tag)) return;
        nametags.put(player.getUniqueId(), tag);
        for (Scoreboard board : boards.values()) applyTeam(board, player, tag);
        if (tag.listName() != null) player.playerListName(tag.listName());
    }

    private void applyTeam(Scoreboard board, Player player, Nametag tag) {
        String name = teamName(player.getUniqueId());
        Team team = board.getTeam(name);
        if (team == null) team = board.registerNewTeam(name);
        team.prefix(tag.prefix());
        team.suffix(tag.suffix());
        team.color(tag.color());
        // Players walk through each other (the team rule is what the game itself checks).
        Team.OptionStatus collision = plugin.lobbyConfig().protection().disablePlayerCollision()
                ? Team.OptionStatus.NEVER : Team.OptionStatus.ALWAYS;
        if (team.getOption(Team.Option.COLLISION_RULE) != collision) team.setOption(Team.Option.COLLISION_RULE, collision);
        if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
    }

    /** Short, unique per player (team names are kept under 16 characters for older clients). */
    private static String teamName(UUID id) {
        return "lb" + id.toString().replace("-", "").substring(0, 14);
    }

    // ---------------------------------------------------------------- tags

    /**
     * The tags for scoreboard and tab text: &lt;online:NAME&gt;, &lt;online:all&gt;, &lt;here&gt;, &lt;player&gt;,
     * &lt;date&gt;, &lt;time&gt;. Separate from the plugin so it can be tested.
     */
    public static TagResolver tags(LobbyConfig config, ToIntFunction<String> online, int here, String player) {
        ZonedDateTime now = ZonedDateTime.now(config.scoreboard().timeZone());
        return TagResolver.resolver(
                TagResolver.resolver("online", (args, context) -> {
                    String server = args.hasNext() ? args.pop().value() : ProxyCounts.ALL;
                    return Tag.selfClosingInserting(Component.text(online.applyAsInt(server)));
                }),
                Messages.arg("here", here),
                Messages.name("player", player),
                Messages.arg("date", now.format(config.scoreboard().dateFormat())),
                Messages.arg("time", LocalTime.from(now).format(TIME)));
    }
}
