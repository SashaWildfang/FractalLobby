/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.hologram;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Locale;

/**
 * /holo (or /hologram): make and edit the lobby's holograms. Line numbers start at 1; edits are on page 1 unless a
 * page is given with /holo page.
 */
public final class HologramCommands {

    private final Lobby plugin;
    private final Holograms holograms;

    public HologramCommands(Lobby plugin, Holograms holograms) {
        this.plugin = plugin;
        this.holograms = holograms;
    }

    private Messages messages() {
        return plugin.messages();
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        SuggestionProvider<CommandSourceStack> names = (context, builder) -> {
            String typed = builder.getRemainingLowerCase();
            for (Hologram holo : holograms.all()) if (holo.name.toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(holo.name);
            return builder.buildFuture();
        };
        return Commands.literal("holo")
                .requires(source -> source.getSender().hasPermission("lobby.admin"))
                .executes(context -> help(context.getSource().getSender()))
                .then(Commands.literal("help").executes(context -> help(context.getSource().getSender())))
                .then(Commands.literal("list").executes(context -> list(context.getSource().getSender())))
                .then(Commands.literal("reload").executes(context -> {
                    holograms.start();
                    messages().send(context.getSource().getSender(), "holo.reloaded", Messages.arg("count", holograms.all().size()));
                    return 1;
                }))
                .then(Commands.literal("near").executes(context -> near(context.getSource().getSender())))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> create(context, "&fNew hologram"))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(context -> create(context, StringArgumentType.getString(context, "text"))))))
                .then(named("delete", names, (context, holo) -> {
                    holograms.delete(holo.name);
                    messages().send(context.getSource().getSender(), "holo.deleted", Messages.arg("name", holo.name));
                }))
                .then(named("info", names, (context, holo) -> info(context.getSource().getSender(), holo)))
                .then(named("movehere", names, (context, holo) -> {
                    Player player = player(context);
                    if (player == null) return;
                    Location at = player.getLocation();
                    holo.location = new Location(at.getWorld(), Math.round(at.getX() * 100) / 100.0,
                            Math.round(at.getY() * 100) / 100.0, Math.round(at.getZ() * 100) / 100.0);
                    save(context, holo, "holo.moved");
                }))
                .then(named("tp", names, (context, holo) -> {
                    Player player = player(context);
                    if (player != null && holo.location != null) player.teleport(holo.location);
                }))
                .then(named("toggle", names, (context, holo) -> {
                    holo.enabled = !holo.enabled;
                    save(context, holo, holo.enabled ? "holo.enabled" : "holo.disabled");
                }))
                .then(named("origin", names, (context, holo) -> {
                    holo.downOrigin = !holo.downOrigin;
                    save(context, holo, holo.downOrigin ? "holo.origin-bottom" : "holo.origin-top");
                }))
                .then(Commands.literal("range").then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 256)).executes(context -> edit(context, holo -> {
                            holo.displayRange = IntegerArgumentType.getInteger(context, "blocks");
                            holo.updateRange = holo.displayRange;
                            save(context, holo, "holo.saved");
                        })))))
                .then(Commands.literal("interval").then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1200)).executes(context -> edit(context, holo -> {
                            holo.updateInterval = IntegerArgumentType.getInteger(context, "ticks");
                            save(context, holo, "holo.saved");
                        })))))
                .then(Commands.literal("addline").then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                        .then(Commands.argument("text", StringArgumentType.greedyString()).executes(context -> edit(context, holo -> {
                            page(context, holo).lines.add(Hologram.Line.of(StringArgumentType.getString(context, "text")));
                            save(context, holo, "holo.saved");
                        })))))
                .then(lineCommand("setline", names, true, (context, holo, index, text) -> {
                    Hologram.Line line = page(context, holo).lines.get(index);
                    line.content = text;
                    save(context, holo, "holo.saved");
                }))
                .then(lineCommand("insertline", names, true, (context, holo, index, text) -> {
                    page(context, holo).lines.add(index, Hologram.Line.of(text));
                    save(context, holo, "holo.saved");
                }))
                .then(lineCommand("removeline", names, false, (context, holo, index, text) -> {
                    page(context, holo).lines.remove(index);
                    save(context, holo, "holo.saved");
                }))
                .then(Commands.literal("height").then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                        .then(Commands.argument("line", IntegerArgumentType.integer(1))
                                .then(Commands.argument("height", DoubleArgumentType.doubleArg(0, 5)).executes(context -> edit(context, holo -> {
                                    int index = IntegerArgumentType.getInteger(context, "line") - 1;
                                    if (!lineExists(context, holo, index)) return;
                                    page(context, holo).lines.get(index).height = DoubleArgumentType.getDouble(context, "height");
                                    save(context, holo, "holo.saved");
                                }))))))
                .then(named("addpage", names, (context, holo) -> {
                    holo.pages.add(new Hologram.Page());
                    holo.pages.getLast().lines.add(Hologram.Line.of("&fPage " + holo.pages.size()));
                    save(context, holo, "holo.saved");
                }))
                .then(Commands.literal("action").then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                        .then(Commands.argument("click", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String type : new String[] {"ANY", "LEFT", "RIGHT", "SHIFT_LEFT", "SHIFT_RIGHT"}) builder.suggest(type);
                                    return builder.buildFuture();
                                })
                                .then(Commands.literal("clear").executes(context -> edit(context, holo -> {
                                    page(context, holo).actions.remove(StringArgumentType.getString(context, "click").toUpperCase(Locale.ROOT));
                                    save(context, holo, "holo.saved");
                                })))
                                .then(Commands.argument("action", StringArgumentType.greedyString()).executes(context -> edit(context, holo -> {
                                    String click = StringArgumentType.getString(context, "click").toUpperCase(Locale.ROOT);
                                    page(context, holo).actions.computeIfAbsent(click, k -> new java.util.ArrayList<>())
                                            .add(StringArgumentType.getString(context, "action"));
                                    save(context, holo, "holo.saved");
                                }))))))
                .build();
    }

    // ---------------------------------------------------------------- helpers

    private interface HoloEdit {
        void apply(Hologram holo) throws IOException;
    }

    private interface Named {
        void apply(CommandContext<CommandSourceStack> context, Hologram holo) throws IOException;
    }

    private interface LineEdit {
        void apply(CommandContext<CommandSourceStack> context, Hologram holo, int index, String text) throws IOException;
    }

    private ArgumentBuilder<CommandSourceStack, ?> named(String literal, SuggestionProvider<CommandSourceStack> names, Named action) {
        return Commands.literal(literal).then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                .executes(context -> edit(context, holo -> action.apply(context, holo))));
    }

    private ArgumentBuilder<CommandSourceStack, ?> lineCommand(String literal, SuggestionProvider<CommandSourceStack> names,
                                                               boolean withText, LineEdit action) {
        var line = Commands.argument("line", IntegerArgumentType.integer(1));
        if (withText) {
            line.then(Commands.argument("text", StringArgumentType.greedyString()).executes(context -> edit(context, holo -> {
                int index = IntegerArgumentType.getInteger(context, "line") - 1;
                boolean insert = literal.equals("insertline");
                if (insert ? index > page(context, holo).lines.size() : !lineExists(context, holo, index)) {
                    if (insert) messages().send(context.getSource().getSender(), "holo.no-line");
                    return;
                }
                action.apply(context, holo, index, StringArgumentType.getString(context, "text"));
            })));
        } else {
            line.executes(context -> edit(context, holo -> {
                int index = IntegerArgumentType.getInteger(context, "line") - 1;
                if (!lineExists(context, holo, index)) return;
                action.apply(context, holo, index, null);
            }));
        }
        return Commands.literal(literal).then(Commands.argument("name", StringArgumentType.word()).suggests(names).then(line));
    }

    private boolean lineExists(CommandContext<CommandSourceStack> context, Hologram holo, int index) {
        if (index >= 0 && index < page(context, holo).lines.size()) return true;
        messages().send(context.getSource().getSender(), "holo.no-line");
        return false;
    }

    /** Edits are on page 1. */
    private static Hologram.Page page(CommandContext<CommandSourceStack> context, Hologram holo) {
        return holo.pages.getFirst();
    }

    private int edit(CommandContext<CommandSourceStack> context, HoloEdit edit) {
        String name = StringArgumentType.getString(context, "name");
        Hologram holo = holograms.get(name);
        if (holo == null) {
            messages().send(context.getSource().getSender(), "holo.unknown", Messages.arg("name", name));
            return 0;
        }
        try {
            edit.apply(holo);
        } catch (IOException e) {
            messages().send(context.getSource().getSender(), "holo.save-failed", Messages.arg("error", e.getMessage()));
        }
        return 1;
    }

    private void save(CommandContext<CommandSourceStack> context, Hologram holo, String messageKey) throws IOException {
        holograms.changed(holo);
        messages().send(context.getSource().getSender(), messageKey, Messages.arg("name", holo.name));
    }

    private Player player(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getSender() instanceof Player player) return player;
        messages().send(context.getSource().getSender(), "general.players-only");
        return null;
    }

    private int create(CommandContext<CommandSourceStack> context, String text) {
        Player player = player(context);
        if (player == null) return 0;
        String name = StringArgumentType.getString(context, "name");
        if (holograms.get(name) != null) {
            messages().send(player, "holo.exists", Messages.arg("name", name));
            return 0;
        }
        try {
            Location at = player.getLocation();
            holograms.create(name, new Location(at.getWorld(), Math.round(at.getX() * 100) / 100.0,
                    Math.round(at.getY() * 100) / 100.0 + 2, Math.round(at.getZ() * 100) / 100.0), text);
            messages().send(player, "holo.created", Messages.arg("name", name));
        } catch (IOException e) {
            messages().send(player, "holo.save-failed", Messages.arg("error", e.getMessage()));
        }
        return 1;
    }

    private int list(CommandSender sender) {
        messages().send(sender, "holo.list-header", Messages.arg("count", holograms.all().size()));
        for (Hologram holo : holograms.all()) {
            messages().send(sender, "holo.list-line", Messages.arg("name", holo.name),
                    Messages.arg("where", holo.location == null ? "?" : String.format(Locale.ROOT, "%s %.1f %.1f %.1f",
                            holo.location.getWorld().getName(), holo.location.getX(), holo.location.getY(), holo.location.getZ())));
        }
        return 1;
    }

    private int near(CommandSender sender) {
        if (!(sender instanceof Player player)) return list(sender);
        messages().send(sender, "holo.list-header", Messages.arg("count", "near"));
        for (Hologram holo : holograms.all()) {
            if (holo.location == null || !holo.location.getWorld().equals(player.getWorld())) continue;
            double distance = holo.location.distance(player.getLocation());
            if (distance <= 30) {
                messages().send(sender, "holo.list-line", Messages.arg("name", holo.name),
                        Messages.arg("where", Math.round(distance) + " blocks away"));
            }
        }
        return 1;
    }

    private void info(CommandSender sender, Hologram holo) {
        messages().send(sender, "holo.info-header", Messages.arg("name", holo.name), Messages.arg("pages", holo.pages.size()));
        int n = 1;
        for (Hologram.Line line : holo.pages.getFirst().lines) {
            sender.sendMessage(Component.text(n++ + ". ", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                    .append(Holograms.preview(line.content)));
        }
    }

    private int help(CommandSender sender) {
        for (String line : messages().lines("holo.help")) sender.sendMessage(messages().render(line));
        return 1;
    }
}
