/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby;

import com.mojang.brigadier.Command;
import com.sasha.lobby.config.LobbyConfig;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.format.PlayerFormat;
import com.sasha.lobby.hook.LobbyExpansion;
import com.sasha.lobby.items.JoinItems;
import com.sasha.lobby.items.RealmSelector;
import com.sasha.lobby.listener.ChatListener;
import com.sasha.lobby.listener.GlowListener;
import com.sasha.lobby.listener.LaunchPadListener;
import com.sasha.lobby.listener.ProtectionListener;
import com.sasha.lobby.listener.SpawnListener;
import com.sasha.lobby.listener.VoidRescueListener;
import com.sasha.lobby.proxy.ProxyCounts;
import com.sasha.lobby.scoreboard.DisplayService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.Consumer;

/**
 * Lobby: the core of the Fractal Network lobby. Sidebar with live network player counts, tab list,
 * chat and nametag formats, join messages, protection, void rescue, launch pads, spawn, and locked join
 * items (realm selector, radio and player visibility toggle) and /glow.
 *
 * <p>Built by Sasha Wildfang for Fractal Network.
 */
public final class Lobby extends JavaPlugin implements Listener {

    private LobbyConfig config;
    private Messages messages;
    private ProxyCounts proxyCounts;
    private DisplayService display;
    private JoinItems joinItems;
    private SpawnListener spawn;
    private GlowListener glow;
    private PlayerFormat playerFormat;
    private boolean placeholderApi;
    private boolean luckPerms;
    /** Kept as Object so this class never loads PlaceholderAPI types when it isn't installed. */
    private Object placeholderExpansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        placeholderApi = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        luckPerms = getServer().getPluginManager().isPluginEnabled("LuckPerms");

        playerFormat = new PlayerFormat(this);
        proxyCounts = new ProxyCounts(this);
        proxyCounts.start();
        display = new DisplayService(this);
        RealmSelector selector = new RealmSelector(this);
        joinItems = new JoinItems(this, selector);
        spawn = new SpawnListener(this);
        glow = new GlowListener(this);

        for (Listener listener : List.of(this, new ProtectionListener(this), new VoidRescueListener(this),
                new LaunchPadListener(this), new ChatListener(this), spawn, glow, selector, joinItems)) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
        registerCommands();
        display.start();
        if (placeholderApi) {
            placeholderExpansion = new LobbyExpansion(this);
            ((LobbyExpansion) placeholderExpansion).register();
        }
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) ((LobbyExpansion) placeholderExpansion).unregister();
        if (display != null) display.stop();
        if (proxyCounts != null) proxyCounts.stop();
    }

    public LobbyConfig lobbyConfig() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public ProxyCounts proxyCounts() {
        return proxyCounts;
    }

    public PlayerFormat playerFormat() {
        return playerFormat;
    }

    public boolean placeholderApi() {
        return placeholderApi;
    }

    public boolean luckPerms() {
        return luckPerms;
    }

    /** Reloads config.yml and messages.yml and refreshes displays and join items for everyone online. */
    public void reload() {
        reloadConfig();
        loadSettings();
        proxyCounts.restartTask();
        display.start();
        for (Player player : getServer().getOnlinePlayers()) joinItems.giveItems(player);
    }

    private void loadSettings() {
        config = LobbyConfig.load(getConfig(), getLogger());
        messages = Messages.load(this);
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(Commands.literal("lobby")
                    .requires(source -> source.getSender().hasPermission("lobby.admin"))
                    .executes(context -> reply(context.getSource(), "general.unknown-command"))
                    .then(Commands.literal("reload").executes(context -> {
                        reload();
                        return reply(context.getSource(), "general.reloaded");
                    }))
                    .build(), "Lobby admin commands", List.of());

            commands.register(Commands.literal("spawn")
                    .requires(source -> source.getSender().hasPermission("lobby.spawn"))
                    .executes(context -> asPlayer(context.getSource(), player -> {
                        spawn.sendToSpawn(player);
                        messages.send(player, "spawn.teleported");
                    }))
                    .build(), "Go to the lobby spawn", List.of());

            commands.register(Commands.literal("glow")
                    .requires(source -> source.getSender().hasPermission(GlowListener.PERMISSION))
                    .executes(context -> asPlayer(context.getSource(), glow::toggle))
                    .build(), "Glow in your rank colour", List.of());

            commands.register(Commands.literal("setspawn")
                    .requires(source -> source.getSender().hasPermission("lobby.admin"))
                    .executes(context -> asPlayer(context.getSource(), player -> {
                        spawn.setSpawn(player);
                        messages.send(player, "spawn.set");
                    }))
                    .build(), "Set the lobby spawn to where you stand", List.of());
        });
    }

    private int reply(CommandSourceStack source, String key) {
        messages.send(source.getSender(), key);
        return Command.SINGLE_SUCCESS;
    }

    private int asPlayer(CommandSourceStack source, Consumer<Player> action) {
        CommandSender sender = source.getSender();
        if (sender instanceof Player player) action.accept(player);
        else messages.send(sender, "general.players-only");
        return Command.SINGLE_SUCCESS;
    }

    // Join/quit order: spawn and items are handled by their own listeners; the displays come last.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        display.add(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        display.remove(event.getPlayer());
    }
}
