/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.help;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Help for every Lobby command: /lobbyhelp shows the topics, /lobbyhelp TOPIC lists that topic's commands (click one
 * to put it in the chat box, hover for details), and /lobbyhelp COMMAND shows one command in full. Players only see
 * what they're allowed to use. The same list makes docs/COMMANDS.md.
 */
public final class CommandHelp {

    /** One command (or form of one). permission null = everyone. */
    public record Entry(String topic, String usage, String description, String permission, List<String> details) {
    }

    public record Topic(String id, String title, String blurb) {
    }

    public static final List<Topic> TOPICS = List.of(
            new Topic("player", "Everyone", "Commands every player can use"),
            new Topic("parkour", "Parkour", "Setting up the parkour course and its holograms"),
            new Topic("mystery", "Mystery Boxes", "Mystery Boxes, Mystery Dust and the Mystery Vault"),
            new Topic("holograms", "Holograms", "Making and editing holograms"),
            new Topic("npcs", "NPCs", "Making and editing NPCs and the holograms above them"),
            new Topic("admin", "Admin", "Reloading, spawn, playtime board and the rest"));

    public static final List<Entry> ENTRIES = build();

    private static final TextColor PRIMARY = TextColor.color(0xB266FF), SECONDARY = TextColor.color(0xE3C4FF),
            MUTED = TextColor.color(0x9C8DB8), ACCENT = TextColor.color(0xFF7BE5), DARK = TextColor.color(0x55556A);


    private static List<Entry> build() {
        List<Entry> e = new ArrayList<>();
        String admin = "lobby.admin";
        // ---- everyone
        e.add(new Entry("player", "/spawn", "Go back to the lobby spawn.", "lobby.spawn", List.of()));
        e.add(new Entry("player", "/afk", "Show that you're AFK (or that you're back).", "lobby.cmd.afk",
                List.of("Moving, chatting or clicking also brings you back.", "You go AFK by yourself after 5 minutes idle.")));
        e.add(new Entry("player", "/parkour", "Start a parkour run, or stop the one you're on.", "lobby.cmd.parkour",
                List.of("Clicking the Parkour NPC does the same.", "Jump to the sparkling block; falling ends the run.")));
        e.add(new Entry("player", "/chatcolor [colour|reset]", "Pick the colour of your chat messages.", "lobby.cmd.chatcolor",
                List.of("Without a colour it opens the menu.", "Also /chatcolour.")));
        e.add(new Entry("player", "/glow", "Glow in your rank colour (or stop).", "lobby.cmd.glow", List.of("Guardian rank or higher.")));
        e.add(new Entry("player", "/flyingminecart", "Summon a flying minecart anyone can ride.", "lobby.cmd.flyingminecart",
                List.of("W to fly, Space to climb, sneak to get out. Also /flycart.")));
        e.add(new Entry("player", "/lobbyhelp [topic|command]", "This help.", null,
                List.of("Example: /lobbyhelp npcs", "Example: /lobbyhelp npc skin")));
        // ---- parkour
        e.add(new Entry("mystery", "/mysterybox", "Open the Mystery Vault menu: your boxes and crafting with dust.", null,
                List.of("Aliases: /mb, /boxes. Boxes are opened at the Mystery Vault.")));
        e.add(new Entry("player", "/collectibles", "Open your Collectibles: hats, suits, gadgets, cloaks, emotes and gestures.", null,
                List.of("Aliases: /cosmetics, /wardrobe. Also the Collectibles item in your hotbar.")));
        e.add(new Entry("player", "/emote <name>", "Do an emote you've found (a big emoji over your head).", null, List.of()));
        e.add(new Entry("player", "/gesture <name>", "An armor stand of you acts out a gesture you've unlocked (wave, dab, floss...) while you watch.", null, List.of()));
        e.add(new Entry("mystery", "/dust", "See how much Mystery Dust you have.", null, List.of()));
        e.add(new Entry("mystery", "/mysterybox give <player> <stars> [amount]", "Give Mystery Boxes (works from the console, for the store and votes).", admin, List.of()));
        e.add(new Entry("mystery", "/mysterybox dust <player> <amount>", "Give Mystery Dust (a negative amount takes it away).", admin, List.of()));
        e.add(new Entry("mystery", "/mysterybox reset <player>", "Take away a player's boxes, dust and found cosmetics.", admin, List.of()));
        e.add(new Entry("mystery", "/mysterybox vault add", "Add a Mystery Vault on the block you stand on; players stand on the side you face.", admin, List.of()));
        e.add(new Entry("mystery", "/mysterybox vault remove", "Remove the nearest Mystery Vault (without any, boxes open in front of the player).", admin, List.of()));
        e.add(new Entry("mystery", "/mysterybox vault list", "List the Mystery Vaults.", admin, List.of()));
        e.add(new Entry("parkour", "/lobbyparkour setup", "Put the Parkour NPC where you stand (it starts runs; make it again if it was removed).", admin, List.of()));
        e.add(new Entry("parkour", "/lobbyparkour area pos1", "Set one corner of the area the course is built in.", admin,
                List.of("Stand in one corner, then the opposite one for pos2.", "The course is built anywhere inside the box.")));
        e.add(new Entry("parkour", "/lobbyparkour area pos2", "Set the opposite corner of the parkour area.", admin, List.of()));
        e.add(new Entry("parkour", "/lobbyparkour area return", "Set where players go after a run.", admin,
                List.of("Without it they go to the lobby spawn.")));
        e.add(new Entry("parkour", "/lobbyparkour leaderboard", "Put the parkour leaderboard hologram where you stand.", admin, List.of()));
        e.add(new Entry("parkour", "/lobbyparkour leaderboard move", "Move the leaderboard hologram to exactly where you stand.", admin, List.of()));
        e.add(new Entry("parkour", "/lobbyparkour leaderboard remove", "Take the leaderboard hologram away.", admin, List.of()));
        // ---- holograms
        String text = "Text can use & codes, &#RRGGBB, MiniMessage, {player}, %placeholders%, gradients like "
                + "<#FF7BE5>text</#FFC2F2> and animations like <#ANIM:burn:&f,&d>text</#ANIM> (burn, wave, colors, typewriter, scroll).";
        e.add(new Entry("holograms", "/holo create <name> [text]", "Make a hologram just above you.", admin, List.of(text)));
        e.add(new Entry("holograms", "/holo addline <name> <text>", "Add a line at the bottom.", admin,
                List.of(text, "#ICON: DIAMOND shows an item; #HEAD: name shows a head.")));
        e.add(new Entry("holograms", "/holo setline <name> <line> <text>", "Change a line (lines start at 1).", admin, List.of(text)));
        e.add(new Entry("holograms", "/holo insertline <name> <line> <text>", "Put a new line in before another.", admin, List.of()));
        e.add(new Entry("holograms", "/holo removeline <name> <line>", "Remove a line.", admin, List.of()));
        e.add(new Entry("holograms", "/holo height <name> <line> <blocks>", "Set the gap a line takes (0.3 is normal).", admin,
                List.of("Use a small height on an empty line for a little space.")));
        e.add(new Entry("holograms", "/holo movehere <name>", "Move a hologram to where you stand.", admin, List.of()));
        e.add(new Entry("holograms", "/holo tp <name>", "Go to a hologram.", admin, List.of()));
        e.add(new Entry("holograms", "/holo info <name>", "Show a hologram's lines.", admin, List.of()));
        e.add(new Entry("holograms", "/holo delete <name>", "Delete a hologram.", admin, List.of()));
        e.add(new Entry("holograms", "/holo toggle <name>", "Hide or show a hologram.", admin, List.of()));
        e.add(new Entry("holograms", "/holo origin <name>", "Switch whether its location is the top or bottom line.", admin, List.of()));
        e.add(new Entry("holograms", "/holo range <name> <blocks>", "How far away it can be seen.", admin, List.of()));
        e.add(new Entry("holograms", "/holo interval <name> <ticks>", "How often placeholders in it refresh (20 = once a second).", admin, List.of()));
        e.add(new Entry("holograms", "/holo action <name> <click> <TYPE:value>", "Add something that happens when it's clicked.", admin,
                List.of("click: ANY, LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT", "MESSAGE:text, COMMAND:spawn, CONSOLE:say hi, CONNECT:survival,",
                        "SOUND:entity.player.levelup:1:1, TELEPORT:world:0:100:0, NEXT_PAGE, PREV_PAGE, PAGE:2",
                        "/holo action <name> <click> clear removes them.")));
        e.add(new Entry("holograms", "/holo addpage <name>", "Add a page (clicking the hologram turns pages).", admin, List.of()));
        e.add(new Entry("holograms", "/holo list", "List every hologram.", admin, List.of()));
        e.add(new Entry("holograms", "/holo near", "List holograms within 30 blocks.", admin, List.of()));
        e.add(new Entry("holograms", "/holo reload", "Load holograms/*.yml again after editing them by hand.", admin, List.of()));
        // ---- npcs
        e.add(new Entry("npcs", "/npc create <name>", "Make an NPC where you stand, with your skin, facing your way.", admin, List.of()));
        e.add(new Entry("npcs", "/npc skin <name> <player|link|@me>", "Change its skin.", admin,
                List.of("A player name, a textures.minecraft.net link, or @me for yours.")));
        e.add(new Entry("npcs", "/npc item <name> <slot>", "Give it the item in your hand (empty hand takes it away).", admin,
                List.of("slot: mainhand, offhand, head, chest, legs, feet")));
        e.add(new Entry("npcs", "/npc line <name> add <text>", "Add a line to the hologram above its head.", admin, List.of(text)));
        e.add(new Entry("npcs", "/npc line <name> set <line> <text>", "Change a line of its hologram.", admin, List.of()));
        e.add(new Entry("npcs", "/npc line <name> insert <line> <text>", "Put a new line in before another.", admin, List.of()));
        e.add(new Entry("npcs", "/npc line <name> remove <line>", "Remove a line from its hologram.", admin, List.of()));
        e.add(new Entry("npcs", "/npc line <name> height <line> <blocks>", "Set the gap a line takes.", admin, List.of()));
        e.add(new Entry("npcs", "/npc line <name> offset <blocks>", "How far above its head the hologram starts (0.35 is normal).", admin, List.of()));
        e.add(new Entry("npcs", "/npc action <name> <any|left|right> add <type:value>", "Add something that happens when it's clicked.", admin,
                List.of("realm:survival (like the realm selector: checks offline, full, maintenance, coming soon), parkour (starts a run),",
                        "message:<text>, player_command:spawn, console_command:say hi, send_to_server:survival,",
                        "play_sound:entity.player.levelup, need_permission:some.perm (stops if missing), wait:1.5,",
                        "execute_random_action (runs one of the actions after it)")));
        e.add(new Entry("npcs", "/npc action <name> <any|left|right> clear", "Remove its click actions.", admin, List.of()));
        e.add(new Entry("npcs", "/npc glow <name> <colour|off>", "Make it glow in a colour.", admin, List.of()));
        e.add(new Entry("npcs", "/npc turn <name> <on|off> [distance]", "Whether it looks at nearby players.", admin, List.of()));
        e.add(new Entry("npcs", "/npc scale <name> <size>", "Make it bigger or smaller (1 is normal).", admin, List.of()));
        e.add(new Entry("npcs", "/npc cooldown <name> <seconds>", "Time before the same player can click it again.", admin, List.of()));
        e.add(new Entry("npcs", "/npc rename <name> <new name>", "Give an NPC a new name (everything else stays).", admin, List.of()));
        e.add(new Entry("npcs", "/npc movehere <name>", "Move it (and its hologram) to where you stand.", admin, List.of()));
        e.add(new Entry("npcs", "/npc tp <name>", "Go to an NPC.", admin, List.of()));
        e.add(new Entry("npcs", "/npc info <name>", "Show its skin, actions and hologram lines.", admin, List.of()));
        e.add(new Entry("npcs", "/npc remove <name>", "Delete an NPC.", admin, List.of()));
        e.add(new Entry("npcs", "/npc list", "List every NPC.", admin, List.of()));
        e.add(new Entry("npcs", "/npc near", "List NPCs within 30 blocks.", admin, List.of()));
        e.add(new Entry("npcs", "/npc reload", "Load npcs.yml again after editing it by hand.", admin, List.of()));
        // ---- admin
        e.add(new Entry("admin", "/lobby reload", "Reload config, messages, parkour, holograms and NPCs.", admin,
                List.of("Also /lobbycore reload, for when the proxy owns /lobby.")));
        e.add(new Entry("admin", "/setspawn", "Set the lobby spawn to where you stand.", admin, List.of()));
        e.add(new Entry("admin", "/lobbyplaytime", "Put the network playtime leaderboard hologram where you stand.", admin, List.of()));
        e.add(new Entry("admin", "/lobbyplaytime move", "Move the playtime hologram to exactly where you stand.", admin, List.of()));
        e.add(new Entry("admin", "/lobbyplaytime remove", "Take the playtime hologram away.", admin, List.of()));
        return List.copyOf(e);
    }

    private static boolean allowed(CommandSender sender, Entry entry) {
        if (com.sasha.lobby.listener.GlowListener.PERMISSION.equals(entry.permission()) && sender instanceof org.bukkit.entity.Player player) {
            return com.sasha.lobby.listener.GlowListener.allowed(player); // Guardian rank or higher
        }
        return entry.permission() == null || sender.hasPermission(entry.permission());
    }

    private static List<Entry> visible(CommandSender sender, String topic) {
        List<Entry> list = new ArrayList<>();
        for (Entry entry : ENTRIES) if (entry.topic().equals(topic) && allowed(sender, entry)) list.add(entry);
        return list;
    }

    /** /lobbyhelp [query]: the topics, one topic, or one command. */
    public void show(CommandSender sender, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT).replaceFirst("^/", "");
        if (q.isEmpty()) {
            overview(sender);
            return;
        }
        for (Topic topic : TOPICS) {
            if (topic.id().equals(q) || topic.title().toLowerCase(Locale.ROOT).equals(q)) {
                topic(sender, topic);
                return;
            }
        }
        List<Entry> matches = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (allowed(sender, entry) && entry.usage().toLowerCase(Locale.ROOT).substring(1).startsWith(q)) matches.add(entry);
        }
        if (matches.isEmpty()) {
            sender.sendMessage(line(Component.text("Nothing called \"" + q + "\". ", MUTED))
                    .append(Component.text("/lobbyhelp", ACCENT).clickEvent(ClickEvent.runCommand("/lobbyhelp"))));
        } else if (matches.size() == 1) {
            detail(sender, matches.getFirst());
        } else {
            header(sender, "Matching \"/" + q + "\"");
            for (Entry entry : matches) sender.sendMessage(row(entry));
        }
    }

    private void overview(CommandSender sender) {
        header(sender, "Lobby help");
        for (Topic topic : TOPICS) {
            int count = visible(sender, topic.id()).size();
            if (count == 0) continue;
            sender.sendMessage(Component.text(" ▸ ", PRIMARY)
                    .append(Component.text(topic.title(), SECONDARY).decorate(TextDecoration.BOLD))
                    .append(Component.text("  " + topic.blurb() + " (" + count + ")", MUTED))
                    .clickEvent(ClickEvent.runCommand("/lobbyhelp " + topic.id()))
                    .hoverEvent(HoverEvent.showText(Component.text("Click: /lobbyhelp " + topic.id(), ACCENT))));
        }
        sender.sendMessage(Component.text(" Click a topic, or /lobbyhelp <command> for one command.", DARK));
    }

    private void topic(CommandSender sender, Topic topic) {
        List<Entry> list = visible(sender, topic.id());
        header(sender, topic.title());
        if (list.isEmpty()) sender.sendMessage(Component.text(" You can't use any of these.", MUTED));
        for (Entry entry : list) sender.sendMessage(row(entry));
        sender.sendMessage(Component.text(" Click a command to type it, hover for details. ", DARK)
                .append(Component.text("« Topics", ACCENT).clickEvent(ClickEvent.runCommand("/lobbyhelp"))));
    }

    private Component row(Entry entry) {
        return Component.text(" " + entry.usage(), ACCENT)
                .append(Component.text(" - " + entry.description(), MUTED))
                .clickEvent(ClickEvent.suggestCommand(typed(entry.usage())))
                .hoverEvent(HoverEvent.showText(hover(entry)));
    }

    private void detail(CommandSender sender, Entry entry) {
        header(sender, entry.usage());
        sender.sendMessage(Component.text(" " + entry.description(), SECONDARY));
        for (String line : entry.details()) sender.sendMessage(Component.text("  " + line, MUTED));
        sender.sendMessage(Component.text("  Permission: ", DARK)
                .append(Component.text(entry.permission() == null ? "everyone" : entry.permission(), MUTED)));
        sender.sendMessage(Component.text("  [Type it]", ACCENT).clickEvent(ClickEvent.suggestCommand(typed(entry.usage())))
                .append(Component.text("  ", DARK))
                .append(Component.text("[" + title(entry.topic()) + "]", PRIMARY).clickEvent(ClickEvent.runCommand("/lobbyhelp " + entry.topic()))));
    }

    private static Component hover(Entry entry) {
        Component text = Component.text(entry.usage(), ACCENT).append(Component.newline())
                .append(Component.text(entry.description(), SECONDARY));
        for (String line : entry.details()) text = text.append(Component.newline()).append(Component.text(line, MUTED));
        return text.append(Component.newline()).append(Component.text("Permission: "
                + (entry.permission() == null ? "everyone" : entry.permission()), DARK));
    }

    /** What clicking puts in the chat box: the command up to its first <argument> or [option]. */
    static String typed(String usage) {
        int cut = usage.length();
        for (char c : new char[] {'<', '['}) {
            int at = usage.indexOf(c);
            if (at >= 0) cut = Math.min(cut, at);
        }
        String out = usage.substring(0, cut).trim();
        return out.contains("|") ? out.substring(0, out.lastIndexOf(' ') + 1) : out + " ";
    }

    private static String title(String topic) {
        for (Topic t : TOPICS) if (t.id().equals(topic)) return t.title();
        return topic;
    }

    private void header(CommandSender sender, String title) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("✦ ", PRIMARY).append(Component.text(title, PRIMARY).decorate(TextDecoration.BOLD)));
    }

    private static Component line(Component c) {
        return Component.text(" ").append(c);
    }

    /** docs/COMMANDS.md, made from the same list. */
    public static String markdown() {
        StringBuilder out = new StringBuilder("# Lobby commands\n\n")
                .append("In game: `/lobbyhelp` shows the topics, `/lobbyhelp <topic>` a topic, `/lobbyhelp <command>` one command.\n")
                .append("Players only see the commands they're allowed to use.\n");
        Map<String, List<Entry>> byTopic = new LinkedHashMap<>();
        for (Topic topic : TOPICS) byTopic.put(topic.id(), new ArrayList<>());
        for (Entry entry : ENTRIES) byTopic.get(entry.topic()).add(entry);
        for (Topic topic : TOPICS) {
            out.append("\n## ").append(topic.title()).append("\n\n").append(topic.blurb()).append(".\n\n")
                    .append("| Command | Permission | What it does |\n|---|---|---|\n");
            for (Entry entry : byTopic.get(topic.id())) {
                StringBuilder what = new StringBuilder(entry.description());
                for (String d : entry.details()) what.append("<br>").append(d);
                out.append("| `").append(entry.usage().replace("|", "\\|")).append("` | ")
                        .append(entry.permission() == null ? "everyone" : "`" + entry.permission() + "`").append(" | ")
                        .append(what.toString().replace("|", "\\|").replace("<#", "&lt;#").replace("</#", "&lt;/#")).append(" |\n");
            }
        }
        return out.toString();
    }
}
