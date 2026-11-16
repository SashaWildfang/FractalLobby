/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.hologram;

import com.sasha.lobby.config.LegacyCodes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a hologram line (DecentHolograms' format) into text: &amp; codes and &amp;#RRGGBB, MiniMessage tags,
 * DecentHolograms gradients (&lt;#AAAAAA&gt;text&lt;/#BBBBBB&gt;) and animations
 * (&lt;#ANIM:burn:&amp;f,&amp;d&gt;text&lt;/#ANIM&gt;: burn, wave, colors, typewriter, scroll).
 */
public final class HologramText {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern ANIM = Pattern.compile("<#ANIM:([a-zA-Z_]+)(?::([^>]*))?>(.*?)</#ANIM>", Pattern.DOTALL);
    private static final Pattern GRADIENT = Pattern.compile("<#([0-9a-fA-F]{6})>(.*?)</#([0-9a-fA-F]{6})>", Pattern.DOTALL);
    private static final Pattern CODES = Pattern.compile("^((?:[&§](?:#[0-9a-fA-F]{6}|[0-9a-fk-orA-FK-OR]))*)");

    private HologramText() {
    }

    public static boolean animated(String content) {
        return content.contains("<#ANIM:");
    }

    /** Whether the line is different for each viewer (their name, placeholders). */
    public static boolean perViewer(String content) {
        return content.contains("{player}") || content.indexOf('%') >= 0;
    }

    /** The finished line, for animation frame {@code frame}. Placeholders must already be filled in. */
    public static Component render(String content, long frame) {
        return MM.deserialize(toMiniMessage(animate(content, frame)));
    }

    static String toMiniMessage(String content) {
        Matcher gradient = GRADIENT.matcher(content);
        StringBuilder out = new StringBuilder();
        while (gradient.find()) {
            gradient.appendReplacement(out, Matcher.quoteReplacement(
                    "<gradient:#" + gradient.group(1) + ":#" + gradient.group(3) + ">" + gradient.group(2) + "</gradient>"));
        }
        gradient.appendTail(out);
        return LegacyCodes.toMiniMessage(out.toString());
    }

    /** Replaces each animation with what it shows at this frame (in &amp; codes). */
    static String animate(String content, long frame) {
        if (!animated(content)) return content;
        Matcher anim = ANIM.matcher(content);
        StringBuilder out = new StringBuilder();
        while (anim.find()) {
            String type = anim.group(1).toLowerCase(java.util.Locale.ROOT);
            List<String> colours = colours(anim.group(2));
            anim.appendReplacement(out, Matcher.quoteReplacement(frame(type, colours, anim.group(3), frame)));
        }
        anim.appendTail(out);
        return out.toString();
    }

    private static List<String> colours(String args) {
        List<String> list = new ArrayList<>();
        if (args != null) for (String part : args.split(",")) if (!part.isBlank()) list.add(part.trim());
        if (list.isEmpty()) list.add("&f");
        if (list.size() == 1) list.add("&7");
        return list;
    }

    private static String frame(String type, List<String> colours, String text, long frame) {
        // Colour codes at the front of the text apply to every letter.
        Matcher lead = CODES.matcher(text);
        String prefix = lead.find() ? lead.group(1) : "";
        String plain = text.substring(prefix.length());
        int n = plain.codePointCount(0, plain.length());
        if (n == 0) return text;
        int[] chars = plain.codePoints().toArray();
        String a = colours.get(0), b = colours.get(1);
        StringBuilder out = new StringBuilder();
        switch (type) {
            case "burn" -> {
                // The second colour spreads across, holds, then the first colour comes back.
                int cycle = 2 * n + 16;
                int k = (int) (frame % cycle);
                for (int i = 0; i < n; i++) {
                    boolean burnt = k < n + 8 ? i < k : i >= k - (n + 8);
                    out.append(burnt ? b : a).append(prefix).appendCodePoint(chars[i]);
                }
            }
            case "wave" -> {
                int at = (int) (frame % (n + 8)) - 3;
                for (int i = 0; i < n; i++) {
                    out.append(Math.abs(i - at) <= 1 ? b : a).append(prefix).appendCodePoint(chars[i]);
                }
            }
            case "shimmer" -> {
                // A gradient sliding back and forth across the text (colours as #RRGGBB or &#RRGGBB).
                double phase = Math.sin(frame / 12.0);
                StringBuilder tag = new StringBuilder("<gradient");
                for (String c : colours) tag.append(':').append(c.replace("&", ""));
                tag.append(':').append(colours.getFirst().replace("&", "")).append(':')
                        .append(String.format(java.util.Locale.ROOT, "%.2f", phase)).append('>');
                return tag + prefix + plain + "</gradient>";
            }
            case "colors" -> out.append(colours.get((int) ((frame / 4) % colours.size()))).append(prefix).append(plain);
            case "typewriter" -> {
                int shown = (int) Math.min(n, frame % (n + 20));
                out.append(a).append(prefix);
                for (int i = 0; i < shown; i++) out.appendCodePoint(chars[i]);
            }
            case "scroll" -> {
                int shift = (int) (frame % n);
                out.append(a).append(prefix);
                for (int i = 0; i < n; i++) out.appendCodePoint(chars[(i + shift) % n]);
            }
            default -> out.append(a).append(prefix).append(plain);
        }
        return out.toString();
    }
}
