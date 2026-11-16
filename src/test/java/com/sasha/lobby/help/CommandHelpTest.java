package com.sasha.lobby.help;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandHelpTest {

    @Test
    void everyEntryHasAKnownTopicAndStartsWithASlash() {
        Set<String> topics = CommandHelp.TOPICS.stream().map(CommandHelp.Topic::id).collect(Collectors.toSet());
        for (CommandHelp.Entry entry : CommandHelp.ENTRIES) {
            assertTrue(topics.contains(entry.topic()), entry.usage());
            assertTrue(entry.usage().startsWith("/"), entry.usage());
        }
    }

    @Test
    void clickingPutsTheCommandUpToItsFirstArgument() {
        assertEquals("/npc skin ", CommandHelp.typed("/npc skin <name> <player|link|@me>"));
        assertEquals("/spawn ", CommandHelp.typed("/spawn"));
        assertEquals("/chatcolor ", CommandHelp.typed("/chatcolor [colour|reset]"));
    }

    /** Keeps docs/COMMANDS.md the same as the in-game help. */
    @Test
    void writesTheCommandsReference() throws IOException {
        Path docs = Path.of("docs", "COMMANDS.md");
        Files.createDirectories(docs.getParent());
        Files.writeString(docs, CommandHelp.markdown());
        assertTrue(Files.readString(docs).contains("/npc create"));
    }
}
