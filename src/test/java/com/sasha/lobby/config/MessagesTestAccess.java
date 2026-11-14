/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/** Builds {@link Messages} for tests in other packages, using the bundled messages.yml as defaults. */
public final class MessagesTestAccess {

    private MessagesTestAccess() {
    }

    public static YamlConfiguration builtIn() throws Exception {
        try (InputStream in = MessagesTestAccess.class.getResourceAsStream("/messages.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    public static Messages messages(YamlConfiguration server) throws Exception {
        return Messages.of(server, builtIn(), Logger.getLogger("test"));
    }

    public static String smallCaps(String text) {
        return SmallCaps.convert(text);
    }
}
