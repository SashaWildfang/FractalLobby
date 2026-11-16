/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.scoreboard;

import com.sasha.lobby.config.LobbyConfig;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.config.MessagesTestAccess;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreboardTagsTest {

    private static final Map<String, Integer> COUNTS = Map.of("lobby", 1, "survival", 4, "ALL", 5);

    private static TagResolver tags() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("scoreboard.date-format", "dd/MM/yy");
        LobbyConfig config = LobbyConfig.load(yaml, Logger.getLogger("test"));
        return DisplayService.tags(config,
                server -> COUNTS.getOrDefault(server.equalsIgnoreCase("all") ? "ALL" : server.toLowerCase(), 0),
                1, "SashaWildfang");
    }

    private static String plain(Messages messages, String template, TagResolver... more) {
        return PlainTextComponentSerializer.plainText().serialize(messages.render(template, TagResolver.resolver(tags(), TagResolver.resolver(more))));
    }

    @Test
    void onlineTagsShowProxyCounts() throws Exception {
        YamlConfiguration server = new YamlConfiguration();
        server.set("format.font", "normal");
        Messages messages = MessagesTestAccess.messages(server);

        assertEquals("Realms (5)", plain(messages, "<secondary>Realms <muted>(<online:all>)"));
        assertEquals("Survival: 4", plain(messages, "<white>Survival: <primary><online:survival>"));
        assertEquals("Practice: 0", plain(messages, "Practice: <online:practice>"));
        assertEquals("1 here, SashaWildfang", plain(messages, "<here> here, <player>"));
    }

    /** The default scoreboard renders with small caps, and numbers stay as digits. */
    @Test
    void defaultScoreboardUsesSmallCaps() throws Exception {
        Messages messages = MessagesTestAccess.messages(new YamlConfiguration());
        List<String> lines = messages.lines("scoreboard.lines");

        assertEquals(MessagesTestAccess.smallCaps("Realms (5)"), plain(messages, lines.get(3)));
        assertEquals(" ▪ " + MessagesTestAccess.smallCaps("Survival: 4"), plain(messages, lines.get(5)));
        assertEquals("\uF021", plain(messages, messages.raw("scoreboard.title"))); // the pack's LOBBY logo
    }

    /** In small caps, the player's name still shows in normal letters. */
    @Test
    void playerNameStaysNormalInSmallCaps() throws Exception {
        Messages messages = MessagesTestAccess.messages(new YamlConfiguration());
        assertEquals(MessagesTestAccess.smallCaps("Hi ") + "SashaWildfang", plain(messages, "Hi <player>"));
    }

    /** A rank prefix pasted in front of the name (like chat and tab do) keeps one space and the name normal. */
    @Test
    void rankedNameInSmallCaps() throws Exception {
        Messages messages = MessagesTestAccess.messages(new YamlConfiguration());
        String rendered = PlainTextComponentSerializer.plainText().serialize(
                messages.render("&f\uE0EA &#c42b2b<player>" + "<gray>: hi", Messages.name("player", "SashaWildfang")));
        assertEquals("\uE0EA SashaWildfang: " + MessagesTestAccess.smallCaps("hi"), rendered);
    }

    /** The tab list header and footer show the realm counts and "playing with X out of Y". */
    @Test
    void tablistShowsSloganAndCounts() throws Exception {
        YamlConfiguration server = new YamlConfiguration();
        server.set("format.font", "normal");
        Messages messages = MessagesTestAccess.messages(server);

        assertEquals("An experience like no other", plain(messages, messages.lines("tablist.header").get(6)));
        assertEquals("1 here · 5 online · 42ms", plain(messages, messages.lines("tablist.footer").get(1),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("ping", "42")));
    }

    /** A server file from an older version lacks newer keys: the built-in text fills in, not the key name. */
    @Test
    void missingKeysFallBackToBuiltInText() throws Exception {
        YamlConfiguration server = new YamlConfiguration();
        server.set("format.font", "normal");
        Messages messages = MessagesTestAccess.messages(server);

        assertEquals("Players: Visible", PlainTextComponentSerializer.plainText().serialize(
                messages.item("items.visibility-shown.name")));
    }
}
