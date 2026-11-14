/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sasha.lobby.items.ChatColors;
import com.sasha.lobby.items.Settings;
import com.sasha.lobby.config.LobbyConfig;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.format.PlayerFormat;
import com.sasha.lobby.hook.LobbyExpansion;
import com.sasha.lobby.items.JoinItems;
import com.sasha.lobby.items.RealmSelector;
import com.sasha.lobby.listener.BorderListener;
import com.sasha.lobby.listener.ChatListener;
import com.sasha.lobby.listener.DoubleJump;
import com.sasha.lobby.proxy.ServerStatus;
import com.sasha.lobby.listener.FlyingMinecarts;
import com.sasha.lobby.listener.GlowListener;
import com.sasha.lobby.listener.LaunchPadListener;
import com.sasha.lobby.listener.ProtectionListener;
import com.sasha.lobby.parkour.ParkourLobby;
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
 * items (realm selector, radio and player visibility toggle), /glow, a world border around spawn, flying minecarts and /chatcolor.
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
    private VoidRescueListener voidRescue;
    private BorderListener border;
    private FlyingMinecarts flyingMinecarts;
    private ProtectionListener protection;
    private ChatColors chatColors;
    private Settings settings;
    private ServerStatus serverStatus;
    private RealmSelector selector;
    private DoubleJump doubleJump;
    private ParkourLobby parkour;
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
        serverStatus = new ServerStatus(this);
        proxyCounts = new ProxyCounts(this);
        proxyCounts.start();
        serverStatus.start();
        display = new DisplayService(this);
        selector = new RealmSelector(this);
        joinItems = new JoinItems(this, selector);
        spawn = new SpawnListener(this);
        glow = new GlowListener(this);
        voidRescue = new VoidRescueListener(this);
        border = new BorderListener(this);
        flyingMinecarts = new FlyingMinecarts(this);
        protection = new ProtectionListener(this);
        chatColors = new ChatColors(this);
        settings = new Settings(this);
        parkour = new ParkourLobby(this);
        doubleJump = new DoubleJump(this);

        for (Listener listener : List.of(this, settings, protection, chatColors, voidRescue, border, flyingMinecarts,
                new LaunchPadListener(this), new ChatListener(this), spawn, glow, selector, joinItems, parkour, doubleJump)) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
        registerCommands();
        display.start();
        border.apply();
        protection.applyWorlds();
        flyingMinecarts.start();
        selector.start();
        doubleJump.start();
        for (Player player : getServer().getOnlinePlayers()) doubleJump.refresh(player);
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
        if (voidRescue != null) voidRescue.stopAll();
        if (flyingMinecarts != null) flyingMinecarts.stop();
        if (parkour != null) parkour.shutdown();
        if (serverStatus != null) serverStatus.stop();
        if (selector != null) selector.stop();
        if (doubleJump != null) doubleJump.stop();
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

    public ChatColors chatColors() {
        return chatColors;
    }

    public ServerStatus serverStatus() {
        return serverStatus;
    }

    public JoinItems joinItems() {
        return joinItems;
    }

    public DoubleJump doubleJump() {
        return doubleJump;
    }

    public Settings settings() {
        return settings;
    }

    public DisplayService display() {
        return display;
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
        serverStatus.start();
        display.start();
        for (Player player : getServer().getOnlinePlayers()) {
            joinItems.giveItems(player);
            doubleJump.refresh(player);
        }
        border.apply();
        protection.applyWorlds();
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

            // Keep this setup command outside /lobby so proxy command routing cannot capture it.
            commands.register(Commands.literal("lobbyparkour")
                    .requires(source -> source.getSender().hasPermission("lobby.admin"))
                    .then(Commands.literal("setup").executes(context -> asPlayer(context.getSource(), player -> parkour.setup(player))))
                    .then(Commands.literal("leaderboard")
                            .executes(context -> asPlayer(context.getSource(), player -> parkour.setupLeaderboard(player)))
                            .then(Commands.literal("remove")
                                    .executes(context -> asPlayer(context.getSource(), player -> parkour.removeLeaderboard(player)))))
                    .build(), "Set up the lobby parkour course and displays", List.of());

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

            commands.register(Commands.literal("chatcolor")
                    .requires(source -> source.getSender().hasPermission(ChatColors.PERMISSION)
                            && lobbyConfig().chatColors().enabled())
                    .executes(context -> asPlayer(context.getSource(), chatColors::open))
                    .then(Commands.argument("color", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                if (context.getSource().getSender() instanceof Player player) {
                                    String typed = builder.getRemainingLowerCase();
                                    for (String name : chatColors.names(player)) {
                                        if (name.toLowerCase(java.util.Locale.ROOT).startsWith(typed)) builder.suggest(name);
                                    }
                                }
                                return builder.buildFuture();
                            })
                            .executes(context -> asPlayer(context.getSource(),
                                    player -> chatColors.choose(player, StringArgumentType.getString(context, "color")))))
                    .build(), "Pick the colour of your chat messages", List.of("chatcolour"));

            commands.register(Commands.literal("flyingminecart")
                    .requires(source -> source.getSender().hasPermission(FlyingMinecarts.PERMISSION))
                    .executes(context -> asPlayer(context.getSource(), flyingMinecarts::summon))
                    .build(), "Summon a flying minecart anyone can ride", List.of("flycart"));

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
        proxyCounts.requestSoon();
        display.updateSoon();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        display.remove(event.getPlayer());
        proxyCounts.requestSoon();
        display.updateSoon(); // next tick, once they're gone from the online list
    }
}
