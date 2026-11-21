/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sasha.lobby.items.ChatColors;
import com.sasha.lobby.items.Profile;
import com.sasha.lobby.items.Trails;
import com.sasha.lobby.listener.PunchListener;
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
import com.sasha.lobby.proxy.CoreLink;
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
    private com.sasha.lobby.items.MenuStyle menuStyle;
    private CoreLink coreLink;
    private ServerStatus serverStatus;
    private RealmSelector selector;
    private DoubleJump doubleJump;
    private com.sasha.lobby.listener.FlyCommand fly;
    private Trails trails;
    private com.sasha.lobby.items.Statuses statuses;
    private com.sasha.lobby.listener.AfkListener afk;
    private com.sasha.lobby.hologram.Holograms holograms;
    private com.sasha.lobby.npc.Npcs npcs;
    private final com.sasha.lobby.help.CommandHelp help = new com.sasha.lobby.help.CommandHelp();
    private Profile profile;
    private com.sasha.lobby.items.ConfirmMenu confirm;
    /** FractalCosmetics, when it's installed. */
    private static volatile com.sasha.lobby.api.CosmeticsHook cosmetics;
    private com.sasha.lobby.items.FriendsMenu friendsMenu;
    private com.sasha.lobby.items.Skins skins;
    private ParkourLobby parkour;
    private PlayerFormat playerFormat;
    private boolean placeholderApi;
    private boolean luckPerms;
    /** Kept as Object so this class never loads PlaceholderAPI types when it isn't installed. */
    private Object placeholderExpansion;

    /**
     * FractalLobby keeps its files in plugins/FractalLobby. The first time it starts where Lobby ran before, it copies
     * plugins/Lobby there (config, messages, holograms, NPCs, parkour, boxes...), leaving the old folder as a backup.
     */
    private void copyOldFolder() {
        java.nio.file.Path folder = getDataFolder().toPath();
        java.nio.file.Path old = folder.resolveSibling("Lobby");
        if (java.nio.file.Files.exists(folder) || !java.nio.file.Files.isDirectory(old)) return;
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(old)) {
            for (java.nio.file.Path source : (Iterable<java.nio.file.Path>) files::iterator) {
                java.nio.file.Path target = folder.resolve(old.relativize(source).toString());
                if (java.nio.file.Files.isDirectory(source)) java.nio.file.Files.createDirectories(target);
                else java.nio.file.Files.copy(source, target, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
            }
            getLogger().info("Copied your old plugins/Lobby folder to plugins/FractalLobby (the old one is kept as a backup).");
        } catch (java.io.IOException e) {
            getLogger().severe("Could not copy plugins/Lobby to plugins/FractalLobby: " + e.getMessage());
        }
    }

    @Override
    public void onEnable() {
        copyOldFolder();
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
        menuStyle = new com.sasha.lobby.items.MenuStyle(this);
        settings = new Settings(this);
        coreLink = new CoreLink(this);
        coreLink.onUpdate(player -> {
            joinItems.refreshSeenBy(player);
            settings.refresh(player);
            if (friendsMenu != null) friendsMenu.refresh(player);
        });
        parkour = new ParkourLobby(this);
        doubleJump = new DoubleJump(this);
        fly = new com.sasha.lobby.listener.FlyCommand(this);
        trails = new Trails(this);
        statuses = new com.sasha.lobby.items.Statuses(this);
        afk = new com.sasha.lobby.listener.AfkListener(this);
        holograms = new com.sasha.lobby.hologram.Holograms(this);
        npcs = new com.sasha.lobby.npc.Npcs(this);
        profile = new Profile(this);
        skins = new com.sasha.lobby.items.Skins(this);
        friendsMenu = new com.sasha.lobby.items.FriendsMenu(this);
        confirm = new com.sasha.lobby.items.ConfirmMenu(this);

        for (Listener listener : List.of(this, settings, coreLink, protection, chatColors, voidRescue, border, flyingMinecarts,
                new LaunchPadListener(this), new ChatListener(this), spawn, glow, selector, joinItems, parkour, doubleJump, fly,
                trails, statuses, afk, parkour.game(), holograms, npcs, profile, friendsMenu, skins, confirm, new PunchListener(this), new com.sasha.lobby.listener.NoCrafting(this))) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
        registerCommands();
        coreLink.start();
        holograms.start();
        npcs.start();
        afk.start();
        display.start();
        border.apply();
        protection.applyWorlds();
        flyingMinecarts.start();
        selector.start();
        doubleJump.start();
        trails.start();
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
        if (coreLink != null) coreLink.stop();
        if (skins != null) skins.save();
        if (voidRescue != null) voidRescue.stopAll();
        if (flyingMinecarts != null) flyingMinecarts.stop();
        if (parkour != null) parkour.shutdown();
        if (npcs != null) npcs.stop();
        if (holograms != null) holograms.shutdown();
        if (serverStatus != null) serverStatus.stop();
        if (selector != null) selector.stop();
        if (doubleJump != null) doubleJump.stop();
        if (trails != null) trails.stop();
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

    public Trails trails() {
        return trails;
    }

    public com.sasha.lobby.hologram.Holograms holograms() {
        return holograms;
    }

    public com.sasha.lobby.npc.Npcs npcs() {
        return npcs;
    }

    public com.sasha.lobby.help.CommandHelp help() {
        return help;
    }

    public com.sasha.lobby.listener.AfkListener afk() {
        return afk;
    }

    public com.sasha.lobby.items.Statuses statuses() {
        return statuses;
    }

    /**
     * FractalCosmetics (Collectibles and Mystery Boxes) plugs in here when it starts, and passes null when it stops.
     */
    public void cosmetics(com.sasha.lobby.api.CosmeticsHook hook) {
        cosmetics = hook;
        if (joinItems != null) for (Player player : getServer().getOnlinePlayers()) joinItems.giveItems(player);
    }

    /** FractalCosmetics, or null when it isn't installed. */
    public static com.sasha.lobby.api.CosmeticsHook cosmeticsHook() {
        return cosmetics;
    }

    /** "Are you sure?" before buying, taking things off and opening boxes. */
    public com.sasha.lobby.items.ConfirmMenu confirm() {
        return confirm;
    }

    /** Everyone's skins, for heads of players who are offline. */
    public com.sasha.lobby.items.Skins skins() {
        return skins;
    }

    public com.sasha.lobby.items.FriendsMenu friendsMenu() {
        return friendsMenu;
    }

    public Profile profile() {
        return profile;
    }

    public GlowListener glow() {
        return glow;
    }

    public ParkourLobby parkour() {
        return parkour;
    }

    public RealmSelector realmSelector() {
        return selector;
    }

    public DoubleJump doubleJump() {
        return doubleJump;
    }

    public Settings settings() {
        return settings;
    }

    public com.sasha.lobby.items.MenuStyle menuStyle() {
        return menuStyle;
    }

    public CoreLink coreLink() {
        return coreLink;
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
    /**
     * Reloads config.yml, messages.yml and parkour.yml and applies them straight away: scoreboard, tab list,
     * proxy counts, realm status, join items, border, protection, double jump, parkour holograms. Chat
     * colours, trails, punching and the menus read the config live, so they follow too.
     */
    public void reload() {
        saveDefaultConfig(); // puts config.yml back if it was deleted (messages.yml does the same when loaded)
        reloadConfig();
        loadSettings();
        proxyCounts.restartTask();
        serverStatus.start();
        holograms.start();
        npcs.start();
        if (cosmetics != null) cosmetics.reload();
        afk.start();
        display.start();
        for (Player player : getServer().getOnlinePlayers()) {
            joinItems.giveItems(player);
            doubleJump.refresh(player);
            joinItems.applyVisibility(player);
        }
        border.apply();
        protection.applyWorlds();
        parkour.reload();
    }

    private void loadSettings() {
        config = LobbyConfig.load(getConfig(), getLogger());
        messages = Messages.load(this);
        messages.buttonIcons(getConfig().getBoolean("menus.custom-gui", getConfig().getBoolean("settings-menu.custom-gui", true)));
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            // A hub plugin on the proxy often owns /lobby (and /hub), so the same command is also /lobbycore,
            // which always reaches this server.
            for (String name : List.of("lobby", "lobbycore")) {
                commands.register(Commands.literal(name)
                        .requires(source -> source.getSender().hasPermission("lobby.admin"))
                        .executes(context -> reply(context.getSource(), "general.unknown-command"))
                        .then(Commands.literal("reload").executes(context -> {
                            reload();
                            return reply(context.getSource(), "general.reloaded");
                        }))
                        .then(Commands.literal("help").executes(context -> {
                            help.show(context.getSource().getSender(), "");
                            return 1;
                        }))
                        .build(), "Lobby admin commands", List.of());
            }

            // Keep this setup command outside /lobby so proxy command routing cannot capture it.
            commands.register(Commands.literal("lobbyparkour")
                    .requires(source -> source.getSender().hasPermission("lobby.admin"))
                    .then(Commands.literal("setup").executes(context -> asPlayer(context.getSource(), player -> parkour.setup(player))))
                    .then(Commands.literal("leaderboard")
                            .executes(context -> asPlayer(context.getSource(), player -> parkour.setupLeaderboard(player)))
                            .then(Commands.literal("move")
                                    .executes(context -> asPlayer(context.getSource(), player -> parkour.moveHologram(player, "leaderboard"))))
                            .then(Commands.literal("remove")
                                    .executes(context -> asPlayer(context.getSource(), player -> parkour.removeLeaderboard(player)))))
                    .then(Commands.literal("area")
                            .then(Commands.literal("pos1")
                                    .executes(context -> asPlayer(context.getSource(), player -> parkour.setArea(player, "pos1"))))
                            .then(Commands.literal("pos2")
                                    .executes(context -> asPlayer(context.getSource(), player -> parkour.setArea(player, "pos2"))))
                            .then(Commands.literal("return")
                                    .executes(context -> asPlayer(context.getSource(), player -> parkour.setArea(player, "return")))))
                    .build(), "Set up the lobby parkour course and displays", List.of());

            commands.register(new com.sasha.lobby.npc.NpcCommands(this, npcs).build(),
                    "Make and edit the lobby's NPCs", List.of("lobbynpc"));

            commands.register(Commands.literal("lobbyhelp")
                    .executes(context -> {
                        help.show(context.getSource().getSender(), "");
                        return 1;
                    })
                    .then(Commands.argument("query", StringArgumentType.greedyString())
                            .suggests((context, builder) -> {
                                for (var topic : com.sasha.lobby.help.CommandHelp.TOPICS) builder.suggest(topic.id());
                                return builder.buildFuture();
                            })
                            .executes(context -> {
                                help.show(context.getSource().getSender(), StringArgumentType.getString(context, "query"));
                                return 1;
                            }))
                    .build(), "Help for every Lobby command", List.of("lhelp"));

            commands.register(new com.sasha.lobby.hologram.HologramCommands(this, holograms).build(),
                    "Make and edit the lobby's holograms", List.of("lobbyholo", "hologram"));

            commands.register(Commands.literal("parkour")
                    .requires(source -> source.getSender().hasPermission("lobby.cmd.parkour"))
                    .executes(context -> asPlayer(context.getSource(), player -> parkour.game().toggle(player)))
                    .build(), "Start (or stop) a parkour run", List.of());

            commands.register(Commands.literal("lobbyplaytime")
                    .requires(source -> source.getSender().hasPermission("lobby.admin"))
                    .executes(context -> asPlayer(context.getSource(), player -> parkour.setupPlaytime(player)))
                    .then(Commands.literal("move")
                            .executes(context -> asPlayer(context.getSource(), player -> parkour.moveHologram(player, "playtime"))))
                    .then(Commands.literal("remove")
                            .executes(context -> asPlayer(context.getSource(), player -> parkour.removeHologram(player, "playtime"))))
                    .build(), "Place, move or remove the playtime leaderboard hologram", List.of());

            commands.register(Commands.literal("spawn")
                    .requires(source -> source.getSender().hasPermission("lobby.spawn"))
                    .executes(context -> asPlayer(context.getSource(), player -> {
                        spawn.sendToSpawn(player);
                        messages.send(player, "spawn.teleported");
                    }))
                    .build(), "Go to the lobby spawn", List.of());

            commands.register(Commands.literal("afk")
                    .requires(source -> source.getSender().hasPermission(com.sasha.lobby.listener.AfkListener.PERMISSION)
                            && afk.enabled())
                    .executes(context -> asPlayer(context.getSource(), afk::toggle))
                    .build(), "Show or stop showing that you're AFK", List.of());

            commands.register(Commands.literal("fly")
                    .requires(source -> source.getSender().hasPermission(com.sasha.lobby.listener.FlyCommand.PERMISSION))
                    .executes(context -> asPlayer(context.getSource(), fly::toggle))
                    .build(), "Fly in the lobby (or stop)", List.of());

            commands.register(Commands.literal("glow")
                    .requires(source -> source.getSender() instanceof Player p ? GlowListener.allowed(p)
                            : source.getSender().hasPermission(GlowListener.PERMISSION))
                    .executes(context -> asPlayer(context.getSource(), glow::toggle))
                    .build(), "Glow in your rank colour", List.of());

            commands.register(Commands.literal("chatcolor")
                    .requires(source -> (source.getSender() instanceof Player p ? ChatColors.canUse(p)
                            : source.getSender().hasPermission(ChatColors.PERMISSION)) && lobbyConfig().chatColors().enabled())
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
