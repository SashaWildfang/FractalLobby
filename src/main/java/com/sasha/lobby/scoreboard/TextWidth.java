/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

/**
 * Estimates how wide text is in Minecraft's default font (in pixels, including the 1px gap after each
 * character), so scoreboard lines can be centered with spaces.
 */
public final class TextWidth {

    public static final String CENTER = "<center>";
    private static final int SPACE = 4;

    private TextWidth() {
    }

    static int width(int codePoint) {
        return switch (codePoint) {
            case 'i', '!', '.', ',', ':', ';', '|', '\'' -> 2;
            case 'l', '`' -> 3;
            case 'I', 't', '[', ']', ' ', '"', '(', ')', '{', '}', '*' -> 4;
            case 'f', 'k', '<', '>' -> 5;
            case '@', '~' -> 7;
            case 0xF021 -> 99; // the resource pack's LOBBY logo
            case 0xF020 -> 21; // and its FractalSMP logo
            default -> 6;
        };
    }

    /** Width of a component, counting bold text 1px wider per character. */
    public static int width(Component component) {
        return width(component, false);
    }

    private static int width(Component component, boolean parentBold) {
        TextDecoration.State state = component.decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.NOT_SET ? parentBold : state == TextDecoration.State.TRUE;
        int total = 0;
        if (component instanceof TextComponent text) {
            total += text.content().codePoints().map(c -> width(c) + (bold && c != ' ' ? 1 : 0)).sum();
        }
        for (Component child : component.children()) total += width(child, bold);
        return total;
    }

    /**
     * Pads the lines marked as centered with leading spaces so they sit in the middle of the widest
     * line (or the title, if wider).
     */
    public static List<Component> center(Component title, List<Component> lines, List<Boolean> centered) {
        int widest = width(title);
        for (Component line : lines) widest = Math.max(widest, width(line));
        List<Component> result = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (!centered.get(i)) {
                result.add(line);
                continue;
            }
            int spaces = Math.round((widest - width(line)) / 2f / SPACE);
            result.add(spaces <= 0 ? line : Component.text(" ".repeat(spaces)).append(line));
        }
        return result;
    }
}
