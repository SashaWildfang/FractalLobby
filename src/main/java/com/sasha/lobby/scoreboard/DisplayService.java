/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.scoreboard;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LegacyCodes;
import com.sasha.lobby.config.LobbyConfig;
import com.sasha.lobby.config.Messages;
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
    /** Something else showing on a player's sidebar for now (the parkour board): title and lines. */
    private final Map<UUID, java.util.function.Supplier<java.util.Map.Entry<Component, List<Component>>>> overrides = new HashMap<>();
    /** Sidebars made only for an override (the player has the lobby scoreboard turned off). */
    private final java.util.Set<UUID> overrideOnly = new java.util.HashSet<>();
    /** Glow colours for non-player entities (NPCs): scoreboard entry (the entity's UUID) → colour. */
    private final Map<String, NamedTextColor> entityColours = new HashMap<>();
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
        for (Map.Entry<String, NamedTextColor> entry : entityColours.entrySet()) applyEntityColour(board, entry.getKey(), entry.getValue());
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other != player) applyStatus(board, other);
        }
        refreshStatus(player);
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
        overrides.remove(id);
        overrideOnly.remove(id);
        for (Scoreboard board : boards.values()) {
            Team team = board.getTeam(teamName(id));
            if (team != null) team.unregister();
        }
        if (plugin.lobbyConfig().tablist()) player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }

    /**
     * Shows something else on the player's sidebar until {@link #clearSidebarOverride} (the parkour board). The
     * content is asked for on every update; {@link #refreshSidebar} redraws straight away.
     */
    public void setSidebarOverride(Player player, java.util.function.Supplier<java.util.Map.Entry<Component, List<Component>>> content) {
        overrides.put(player.getUniqueId(), content);
        refreshSidebar(player);
    }

    /** Back to the lobby sidebar (or none, if the player has it turned off). */
    public void clearSidebarOverride(Player player) {
        if (overrides.remove(player.getUniqueId()) == null) return;
        if (overrideOnly.remove(player.getUniqueId())) {
            Sidebar sidebar = sidebars.remove(player.getUniqueId());
            if (sidebar != null) sidebar.remove();
        }
        refreshSidebar(player);
    }

    public boolean hasSidebarOverride(Player player) {
        return overrides.containsKey(player.getUniqueId());
    }

    public void refreshSidebar(Player player) {
        if (boards.containsKey(player.getUniqueId())) update(player);
    }

    /** Shows or hides one player's sidebar (their scoreboard setting). The tab list and nametags stay. */
    public void showSidebar(Player player, boolean show) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;
        Sidebar sidebar = sidebars.get(player.getUniqueId());
        if (overrides.containsKey(player.getUniqueId())) {
            // Mid-run: the parkour board stays; this decides what's left once it goes.
            if (show) overrideOnly.remove(player.getUniqueId());
            else overrideOnly.add(player.getUniqueId());
            return;
        }
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
        var override = overrides.get(player.getUniqueId());
        if (override != null) {
            Scoreboard board = boards.get(player.getUniqueId());
            if (sidebar == null && board != null) {
                sidebar = new Sidebar(board);
                sidebars.put(player.getUniqueId(), sidebar);
                overrideOnly.add(player.getUniqueId());
            }
            if (sidebar != null) {
                var content = override.get();
                sidebar.update(content.getKey(), content.getValue());
            }
        } else if (sidebar != null) {
            Component title = messages.render(placeholders(player, messages.raw("scoreboard.title")), tags);
            List<Component> lines = new ArrayList<>();
            List<Boolean> centered = new ArrayList<>();
            boolean hideDown = plugin.lobbyConfig().scoreboard().hideOfflineRealms();
            boolean bedrock = com.sasha.lobby.hook.Bedrock.is(player);
            for (String template : messages.lines("scoreboard.lines")) {
                // Bedrock draws the date line at full size right under the logo: it is left out there.
                if (bedrock && template.contains("<date>")) continue;
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
        String tip = placeholders(player, tips.get((int) (System.currentTimeMillis() / every % tips.size())));
        Component shown = plugin.messages().render(tip);
        if (!tip.contains("<br>") && TextWidth.width(shown) > TIP_WIDTH) shown = plugin.messages().render(wrap(tip));
        return Placeholder.component("tip", shown);
    }

    /** Wider than this (in pixels), a tip goes over two lines. */
    private static final int TIP_WIDTH = 290;

    /** Puts a line break at the space nearest the middle of the visible text (never inside a tag). */
    static String wrap(String template) {
        int visible = 0, depth = 0;
        for (char c : template.toCharArray()) {
            if (c == '<') depth++;
            else if (c == '>') depth = Math.max(0, depth - 1);
            else if (depth == 0) visible++;
        }
        int best = -1, bestDistance = Integer.MAX_VALUE, seen = 0;
        depth = 0;
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth = Math.max(0, depth - 1);
            else if (depth == 0) {
                if (c == ' ' && Math.abs(seen - visible / 2) < bestDistance) {
                    best = i;
                    bestDistance = Math.abs(seen - visible / 2);
                }
                seen++;
            }
        }
        return best < 0 ? template : template.substring(0, best) + "<br>" + template.substring(best + 1);
    }

    private String placeholders(Player player, String template) {
        return template.indexOf('%') >= 0 ? com.sasha.lobby.hook.Placeholders.apply(plugin, player, template) : template;
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
        Nametag tag;
        if (plugin.afk().isAfk(player)) {
            // AFK: a red AFK tag in place of the rank, above the head and in the tab list, until they move.
            // The name (and so the glow) goes light red (Minecraft's RED, the only light red a team can have).
            Component afkTag = plugin.messages().component("afk.tag");
            Component rest = Component.text(player.getName(), NamedTextColor.RED);
            tag = new Nametag(afkTag,
                    !config.enabled() || after.isEmpty() ? Component.empty() : plugin.playerFormat().render(player, after),
                    NamedTextColor.RED,
                    Component.textOfChildren(afkTag, rest));
        } else {
            tag = new Nametag(
                    !config.enabled() || before.isEmpty() ? Component.empty() : plugin.playerFormat().render(player, before),
                    !config.enabled() || after.isEmpty() ? Component.empty() : plugin.playerFormat().render(player, after),
                    nameColour == null ? null : NamedTextColor.nearestTo(nameColour),
                    config.enabled() && config.applyToTablist() ? plugin.playerFormat().render(player, format) : null);
        }

        // Tab list order: higher LuckPerms weight first (the game sorts by this before anything else).
        int order = plugin.lobbyConfig().tablistSortByRank() ? Math.max(0, plugin.playerFormat().weight(player)) : 0;
        if (player.getPlayerListOrder() != order) player.setPlayerListOrder(order);

        Nametag old = nametags.get(player.getUniqueId());
        if (!force && Objects.equals(old, tag)) return;
        nametags.put(player.getUniqueId(), tag);
        for (Scoreboard board : boards.values()) applyTeam(board, player, tag);
        if (tag.listName() != null) player.playerListName(tag.listName());
        else if (old != null && old.listName() != null) player.playerListName(null); // back from AFK
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

    // ---------------------------------------------------------------- status under the name

    private static final String STATUS = "lbstatus";
    /** Pulls the next text back 4px: cancels the space the game puts between the line and the objective's name. */
    private static final Component BACKSPACE = Component.text("\uF003").font(net.kyori.adventure.key.Key.key("fractal", "lobby"));

    /** A player went AFK or came back: their rank tag (head and tab list) and the line under their name. */
    public void refreshAfk(Player player) {
        refreshNametag(player, false);
        refreshStatus(player);
    }

    /** Shows the player's status (or nothing) under their name, on everyone's board. */
    public void refreshStatus(Player player) {
        for (Scoreboard board : boards.values()) applyStatus(board, player);
    }

    private void applyStatus(Scoreboard board, Player player) {
        org.bukkit.scoreboard.Objective objective = board.getObjective(STATUS);
        if (!plugin.statuses().enabled() && !plugin.afk().enabled()) {
            if (objective != null) objective.unregister();
            return;
        }
        boolean pack = plugin.menuStyle().custom();
        if (objective == null) {
            objective = board.registerNewObjective(STATUS, org.bukkit.scoreboard.Criteria.DUMMY, Component.empty());
            objective.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.BELOW_NAME);
            // Everyone without a status: an empty line (as narrow as the pack allows).
            objective.numberFormat(pack ? io.papermc.paper.scoreboard.numbers.NumberFormat.fixed(BACKSPACE)
                    : io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        }
        Component line = plugin.statuses().belowName(player);
        org.bukkit.scoreboard.Score score = objective.getScore(player);
        if (line == null) {
            if (score.isScoreSet()) score.resetScore();
            return;
        }
        score.setScore(0);
        score.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.fixed(pack ? Component.textOfChildren(line, BACKSPACE) : line));
    }

    /** Gives an entity (an NPC) a glow colour on everyone's board; null takes it away. */
    public void setEntityColour(String entry, NamedTextColor colour) {
        if (colour == null) {
            if (entityColours.remove(entry) == null) return;
            for (Scoreboard board : boards.values()) {
                Team team = board.getTeam(entityTeam(entry));
                if (team != null) team.unregister();
            }
            return;
        }
        entityColours.put(entry, colour);
        for (Scoreboard board : boards.values()) applyEntityColour(board, entry, colour);
    }

    private static void applyEntityColour(Scoreboard board, String entry, NamedTextColor colour) {
        String name = entityTeam(entry);
        Team team = board.getTeam(name);
        if (team == null) team = board.registerNewTeam(name);
        team.color(colour);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        if (!team.hasEntry(entry)) team.addEntry(entry);
    }

    private static String entityTeam(String entry) {
        return "lbe" + entry.replace("-", "").substring(0, 13);
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
