/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.npc;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.hologram.Hologram;
import com.sasha.lobby.hologram.HologramText;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /npc: make and edit the lobby's NPCs and the holograms above them. Line numbers start at 1. */
public final class NpcCommands {

    private final Lobby plugin;
    private final Npcs npcs;

    public NpcCommands(Lobby plugin, Npcs npcs) {
        this.plugin = plugin;
        this.npcs = npcs;
    }

    private Messages messages() {
        return plugin.messages();
    }

    private interface Edit {
        void apply(CommandContext<CommandSourceStack> context, Npc npc);
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        SuggestionProvider<CommandSourceStack> names = (context, builder) -> {
            String typed = builder.getRemainingLowerCase();
            for (Npc npc : npcs.all()) if (npc.name.toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(npc.name);
            return builder.buildFuture();
        };
        SuggestionProvider<CommandSourceStack> slots = (context, builder) -> {
            for (String s : List.of("mainhand", "offhand", "head", "chest", "legs", "feet")) builder.suggest(s);
            return builder.buildFuture();
        };
        SuggestionProvider<CommandSourceStack> clicks = (context, builder) -> {
            for (String s : List.of("any", "left", "right")) builder.suggest(s);
            return builder.buildFuture();
        };
        SuggestionProvider<CommandSourceStack> colours = (context, builder) -> {
            builder.suggest("off");
            for (String s : NamedTextColor.NAMES.keys()) builder.suggest(s);
            return builder.buildFuture();
        };
        return Commands.literal("npc")
                .requires(source -> source.getSender().hasPermission("lobby.admin"))
                .executes(context -> help(context.getSource().getSender()))
                .then(Commands.literal("help").executes(context -> help(context.getSource().getSender())))
                .then(Commands.literal("list").executes(context -> list(context.getSource().getSender(), false)))
                .then(Commands.literal("near").executes(context -> list(context.getSource().getSender(), true)))
                .then(Commands.literal("reload").executes(context -> {
                    npcs.start();
                    messages().send(context.getSource().getSender(), "npc.reloaded", Messages.arg("count", npcs.all().size()));
                    return 1;
                }))
                .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
                    Player player = player(context);
                    if (player == null) return 0;
                    String name = StringArgumentType.getString(context, "name");
                    if (npcs.get(name) != null) {
                        messages().send(player, "npc.exists", Messages.arg("name", name));
                        return 0;
                    }
                    Npc made = npcs.create(name, standing(player));
                    copySkin(player.getPlayerProfile(), made.skin);
                    made.skin.name = player.getName();
                    npcs.changed(made);
                    messages().send(player, "npc.created", Messages.arg("name", name));
                    return 1;
                })))
                .then(named("remove", names, (context, npc) -> {
                    npcs.delete(npc);
                    messages().send(context.getSource().getSender(), "npc.removed", Messages.arg("name", npc.name));
                }))
                .then(Commands.literal("rename").then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                        .then(Commands.argument("new", StringArgumentType.word()).executes(context -> edit(context, (ctx, npc) -> {
                            String newName = StringArgumentType.getString(ctx, "new");
                            String old = npc.name;
                            if (!npcs.rename(npc, newName)) {
                                messages().send(ctx.getSource().getSender(), "npc.exists", Messages.arg("name", newName));
                                return;
                            }
                            messages().send(ctx.getSource().getSender(), "npc.renamed", Messages.arg("name", old), Messages.arg("new", newName));
                        })))))
                .then(named("info", names, (context, npc) -> info(context.getSource().getSender(), npc)))
                .then(named("movehere", names, (context, npc) -> {
                    Player player = player(context);
                    if (player == null) return;
                    npcs.move(npc, standing(player));
                    done(context, npc, "npc.moved");
                }))
                .then(named("tp", names, (context, npc) -> {
                    Player player = player(context);
                    if (player != null && npc.location != null) player.teleport(npc.location);
                }))
                .then(Commands.literal("skin").then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                        .then(Commands.argument("skin", StringArgumentType.greedyString()).executes(context -> edit(context, (ctx, npc) -> {
                            String skin = StringArgumentType.getString(ctx, "skin").trim();
                            setSkin(ctx, npc, skin);
                        })))))
                .then(Commands.literal("item").then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                        .then(Commands.argument("slot", StringArgumentType.word()).suggests(slots).executes(context -> edit(context, (ctx, npc) -> {
                            Player player = player(ctx);
                            if (player == null) return;
                            EquipmentSlot slot = Npc.slot(StringArgumentType.getString(ctx, "slot"));
                            if (slot == null) {
                                messages().send(player, "npc.bad-slot");
                                return;
                            }
                            ItemStack hand = player.getInventory().getItemInMainHand();
                            if (hand.getType().isAir()) npc.equipment.remove(slot);
                            else npc.equipment.put(slot, hand.clone());
                            npcs.changed(npc);
                            done(ctx, npc, "npc.saved");
                        })))))
                .then(Commands.literal("glow").then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                        .then(Commands.argument("colour", StringArgumentType.word()).suggests(colours).executes(context -> edit(context, (ctx, npc) -> {
                            String colour = StringArgumentType.getString(ctx, "colour").toLowerCase(Locale.ROOT);
                            if (colour.equals("off")) npc.glowing = false;
                            else if (NamedTextColor.NAMES.value(colour) == null) {
                                messages().send(ctx.getSource().getSender(), "npc.bad-colour");
                                return;
                            } else {
                                npc.glowing = true;
                                npc.glowColor = colour;
                            }
                            npcs.changed(npc);
                            done(ctx, npc, "npc.saved");
                        })))))
                .then(Commands.literal("turn").then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                        .then(Commands.literal("off").executes(context -> edit(context, (ctx, npc) -> {
                            npc.turnToPlayer = false;
                            npcs.changed(npc);
                            done(ctx, npc, "npc.saved");
                        })))
                        .then(Commands.literal("on").executes(context -> edit(context, (ctx, npc) -> {
                                    npc.turnToPlayer = true;
                                    npcs.changed(npc);
                                    done(ctx, npc, "npc.saved");
                                }))
                                .then(Commands.argument("distance", DoubleArgumentType.doubleArg(1, 64)).executes(context -> edit(context, (ctx, npc) -> {
                                    npc.turnToPlayer = true;
                                    npc.turnDistance = DoubleArgumentType.getDouble(ctx, "distance");
                                    npcs.changed(npc);
                                    done(ctx, npc, "npc.saved");
                                }))))))
                .then(Commands.literal("scale").then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                        .then(Commands.argument("size", DoubleArgumentType.doubleArg(0.1, 5)).executes(context -> edit(context, (ctx, npc) -> {
                            npc.scale = DoubleArgumentType.getDouble(ctx, "size");
                            npcs.changed(npc);
                            done(ctx, npc, "npc.saved");
                        })))))
                .then(Commands.literal("cooldown").then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0, 600)).executes(context -> edit(context, (ctx, npc) -> {
                            npc.cooldownSeconds = DoubleArgumentType.getDouble(ctx, "seconds");
                            npcs.changed(npc);
                            done(ctx, npc, "npc.saved");
                        })))))
                .then(Commands.literal("action").then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                        .then(Commands.argument("click", StringArgumentType.word()).suggests(clicks)
                                .then(Commands.literal("clear").executes(context -> edit(context, (ctx, npc) -> {
                                    npc.actions.remove(click(ctx));
                                    npcs.changed(npc);
                                    done(ctx, npc, "npc.saved");
                                })))
                                .then(Commands.literal("add").then(Commands.argument("action", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> {
                                            for (String s : List.of("message:", "player_command:", "console_command:", "send_to_server:",
                                                    "play_sound:", "need_permission:", "wait:", "execute_random_action")) builder.suggest(s);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> edit(context, (ctx, npc) -> {
                                            npc.actions.computeIfAbsent(click(ctx), k -> new ArrayList<>())
                                                    .add(StringArgumentType.getString(ctx, "action"));
                                            npcs.changed(npc);
                                            done(ctx, npc, "npc.saved");
                                        })))))))
                .then(Commands.literal("line").then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                        .then(Commands.literal("add").then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(context -> edit(context, (ctx, npc) -> {
                                    npc.lines.add(Hologram.Line.of(StringArgumentType.getString(ctx, "text")));
                                    npcs.changed(npc);
                                    done(ctx, npc, "npc.saved");
                                }))))
                        .then(Commands.literal("set").then(Commands.argument("line", IntegerArgumentType.integer(1))
                                .then(Commands.argument("text", StringArgumentType.greedyString()).executes(context -> edit(context, (ctx, npc) -> {
                                    int i = IntegerArgumentType.getInteger(ctx, "line") - 1;
                                    if (!lineExists(ctx, npc, i)) return;
                                    npc.lines.get(i).content = StringArgumentType.getString(ctx, "text");
                                    npcs.changed(npc);
                                    done(ctx, npc, "npc.saved");
                                })))))
                        .then(Commands.literal("insert").then(Commands.argument("line", IntegerArgumentType.integer(1))
                                .then(Commands.argument("text", StringArgumentType.greedyString()).executes(context -> edit(context, (ctx, npc) -> {
                                    int i = IntegerArgumentType.getInteger(ctx, "line") - 1;
                                    if (i > npc.lines.size()) {
                                        messages().send(ctx.getSource().getSender(), "npc.no-line");
                                        return;
                                    }
                                    npc.lines.add(i, Hologram.Line.of(StringArgumentType.getString(ctx, "text")));
                                    npcs.changed(npc);
                                    done(ctx, npc, "npc.saved");
                                })))))
                        .then(Commands.literal("remove").then(Commands.argument("line", IntegerArgumentType.integer(1))
                                .executes(context -> edit(context, (ctx, npc) -> {
                                    int i = IntegerArgumentType.getInteger(ctx, "line") - 1;
                                    if (!lineExists(ctx, npc, i)) return;
                                    npc.lines.remove(i);
                                    npcs.changed(npc);
                                    done(ctx, npc, "npc.saved");
                                }))))
                        .then(Commands.literal("height").then(Commands.argument("line", IntegerArgumentType.integer(1))
                                .then(Commands.argument("height", DoubleArgumentType.doubleArg(0, 5)).executes(context -> edit(context, (ctx, npc) -> {
                                    int i = IntegerArgumentType.getInteger(ctx, "line") - 1;
                                    if (!lineExists(ctx, npc, i)) return;
                                    npc.lines.get(i).height = DoubleArgumentType.getDouble(ctx, "height");
                                    npcs.changed(npc);
                                    done(ctx, npc, "npc.saved");
                                })))))
                        .then(Commands.literal("offset").then(Commands.argument("blocks", DoubleArgumentType.doubleArg(-2, 5))
                                .executes(context -> edit(context, (ctx, npc) -> {
                                    npc.hologramOffset = DoubleArgumentType.getDouble(ctx, "blocks");
                                    npcs.changed(npc);
                                    done(ctx, npc, "npc.saved");
                                }))))))
                .build();
    }

    // ---------------------------------------------------------------- helpers

    private ArgumentBuilder<CommandSourceStack, ?> named(String literal, SuggestionProvider<CommandSourceStack> names, Edit action) {
        return Commands.literal(literal).then(Commands.argument("name", StringArgumentType.word()).suggests(names)
                .executes(context -> edit(context, action)));
    }

    private int edit(CommandContext<CommandSourceStack> context, Edit edit) {
        String name = StringArgumentType.getString(context, "name");
        Npc npc = npcs.get(name);
        if (npc == null) {
            messages().send(context.getSource().getSender(), "npc.unknown", Messages.arg("name", name));
            return 0;
        }
        edit.apply(context, npc);
        return 1;
    }

    private void done(CommandContext<CommandSourceStack> context, Npc npc, String key) {
        messages().send(context.getSource().getSender(), key, Messages.arg("name", npc.name));
    }

    private boolean lineExists(CommandContext<CommandSourceStack> context, Npc npc, int index) {
        if (index >= 0 && index < npc.lines.size()) return true;
        messages().send(context.getSource().getSender(), "npc.no-line");
        return false;
    }

    private static String click(CommandContext<CommandSourceStack> context) {
        String click = StringArgumentType.getString(context, "click").toUpperCase(Locale.ROOT);
        return click.endsWith("_CLICK") ? click : click + "_CLICK";
    }

    private Player player(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getSender() instanceof Player player) return player;
        messages().send(context.getSource().getSender(), "general.players-only");
        return null;
    }

    /** Where the player stands, centred on the block, facing the way they face. */
    private static Location standing(Player player) {
        Location at = player.getLocation();
        return new Location(at.getWorld(), at.getBlockX() + 0.5, at.getY(), at.getBlockZ() + 0.5,
                Math.round(at.getYaw() / 5) * 5f, 0);
    }

    private static void copySkin(PlayerProfile profile, Npc.Skin skin) {
        for (ProfileProperty property : profile.getProperties()) {
            if (property.getName().equals("textures")) {
                skin.value = property.getValue();
                skin.signature = property.getSignature() == null ? "" : property.getSignature();
            }
        }
    }

    /** A player name, a texture link (textures.minecraft.net/...), or @me for your own skin. */
    private void setSkin(CommandContext<CommandSourceStack> context, Npc npc, String skin) {
        CommandSender sender = context.getSource().getSender();
        npc.skin.name = "";
        npc.skin.url = "";
        npc.skin.value = "";
        npc.skin.signature = "";
        if (skin.equalsIgnoreCase("@me") && sender instanceof Player player) {
            npc.skin.name = player.getName();
            copySkin(player.getPlayerProfile(), npc.skin);
        } else if (skin.startsWith("http")) {
            npc.skin.url = skin;
        } else {
            npc.skin.name = skin;
            // Look the skin up now, so it's saved signed (and works even if the player changes it later).
            PlayerProfile profile = Bukkit.createProfile(skin);
            profile.update().thenAccept(found -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!found.hasTextures()) return;
                copySkin(found, npc.skin);
                npcs.changed(npc);
            }));
        }
        npcs.changed(npc);
        done(context, npc, "npc.saved");
    }

    private int list(CommandSender sender, boolean near) {
        messages().send(sender, "npc.list-header", Messages.arg("count", npcs.all().size()));
        for (Npc npc : npcs.all()) {
            String where;
            if (npc.location == null) where = "?";
            else if (near && sender instanceof Player player) {
                if (!player.getWorld().equals(npc.location.getWorld()) || player.getLocation().distance(npc.location) > 30) continue;
                where = Math.round(player.getLocation().distance(npc.location)) + " blocks away";
            } else {
                where = String.format(Locale.ROOT, "%.1f %.1f %.1f", npc.location.getX(), npc.location.getY(), npc.location.getZ());
            }
            messages().send(sender, "npc.list-line", Messages.arg("name", npc.name), Messages.arg("where", where));
        }
        return 1;
    }

    private void info(CommandSender sender, Npc npc) {
        messages().send(sender, "npc.info", Messages.arg("name", npc.name),
                Messages.arg("skin", !npc.skin.name.isBlank() ? npc.skin.name : !npc.skin.url.isBlank() ? "texture link" : "default"),
                Messages.arg("actions", npc.actions.isEmpty() ? "none" : npc.actions.toString()));
        int n = 1;
        for (Hologram.Line line : npc.lines) {
            sender.sendMessage(Component.text(n++ + ". ", NamedTextColor.DARK_GRAY).append(HologramText.render(line.content, 0)));
        }
    }

    private int help(CommandSender sender) {
        plugin.help().show(sender, "npcs");
        return 1;
    }
}
