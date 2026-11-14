/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Keeps the lobby the way it was built, on every world of this server, so no region plugin is needed.
 * Every flag is under protection: in config.yml. Players with the build permission (and ops, if allowed)
 * skip the player rules (building, using blocks and entities, picking up items, projectiles).
 */
public final class ProtectionListener implements Listener {

    /** Mobs from these may still appear (commands, spawn eggs, plugins such as NPCs). */
    private static final Set<CreatureSpawnEvent.SpawnReason> ALLOWED_SPAWNS = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.CUSTOM, CreatureSpawnEvent.SpawnReason.COMMAND,
            CreatureSpawnEvent.SpawnReason.SPAWNER_EGG, CreatureSpawnEvent.SpawnReason.DEFAULT,
            CreatureSpawnEvent.SpawnReason.SHOULDER_ENTITY, CreatureSpawnEvent.SpawnReason.MOUNT);

    /** Blocks that break when walked or jumped on. */
    private static final Set<Material> TRAMPLED = EnumSet.of(Material.FARMLAND, Material.TURTLE_EGG, Material.SNIFFER_EGG);

    private final Lobby plugin;

    public ProtectionListener(Lobby plugin) {
        this.plugin = plugin;
    }

    private LobbyConfig.Protection rules() {
        return plugin.lobbyConfig().protection();
    }

    /** Whether a player may place and break blocks and use things: the build permission, or being an op if allowed. */
    public boolean canBuild(Player player) {
        LobbyConfig.Protection rules = rules();
        return player.hasPermission(rules.buildPermission()) || (rules.allowOpsToBuild() && player.isOp());
    }

    /** Cancels when the flag is on and the player can't build. */
    private void guard(boolean flag, Player player, Cancellable event) {
        if (flag && player != null && !canBuild(player)) event.setCancelled(true);
    }

    /** The player behind an action: the entity itself, or whoever shot the projectile. */
    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    // ---------------------------------------------------------------- building

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        guard(rules().disableBuilding(), event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        guard(rules().disableBuilding(), event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        guard(rules().disableBuilding(), event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        guard(rules().disableBuilding(), event.getPlayer(), event);
    }

    /** Boats, minecarts, armor stands and end crystals. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        guard(rules().disableBuilding(), event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        guard(rules().disableBuilding(), event.getPlayer(), event);
    }

    /** Paintings and item frames count as building too. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        guard(rules().disableBuilding(), attacker(event.getRemover()), event);
    }

    /** Boats and minecarts (flying minecarts have their own rule). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        guard(rules().disableBuilding(), attacker(event.getAttacker()), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignOpen(PlayerOpenSignEvent event) {
        guard(rules().disableBuilding(), event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        guard(rules().disableBuilding(), event.getPlayer(), event);
    }

    /** Bone meal. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        guard(rules().disableBuilding(), event.getPlayer(), event);
    }

    // ---------------------------------------------------------------- using blocks and entities

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        LobbyConfig.Protection rules = rules();

        if (event.getAction() == Action.PHYSICAL) {
            if (rules.disableTrampling() && TRAMPLED.contains(block.getType())) event.setUseInteractedBlock(Event.Result.DENY);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !rules.disableBlockInteract()) return;
        if (canBuild(event.getPlayer()) || rules.interactAllowed(block.getType()) || !interactable(block.getType())) return;
        // Only the block is blocked: the item in hand (e.g. the join items) still works.
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    @SuppressWarnings("deprecation") // Material#isInteractable is the simplest complete list of usable blocks
    private static boolean interactable(Material block) {
        return block.isInteractable();
    }

    /** Mobs trampling farmland and eggs. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityTrample(EntityInteractEvent event) {
        if (rules().disableTrampling() && TRAMPLED.contains(event.getBlock().getType())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLecternBook(PlayerTakeLecternBookEvent event) {
        guard(rules().disableBlockInteract(), event.getPlayer(), event);
    }

    /** Item frames, mobs (trading, feeding, shearing, mounting), leads. Players and minecarts can still be used. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        boolean protectedEntity = entity instanceof Hanging || (entity instanceof LivingEntity && !(entity instanceof Player));
        if (protectedEntity) guard(rules().disableEntityInteract(), event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        guard(rules().disableEntityInteract(), event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        guard(rules().disableEntityInteract(), event.getPlayer(), event);
    }

    /** Scooping fish and axolotls into buckets. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        guard(rules().disableEntityInteract(), event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) guard(rules().disableItemPickup(), player, event);
    }

    /** Bows, snowballs, eggs, ender pearls, tridents, fishing rods. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) guard(rules().disableProjectiles(), player, event);
    }

    // ---------------------------------------------------------------- damage

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        LobbyConfig.Protection rules = rules();
        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean explosion = cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
        if (event.getEntity() instanceof ArmorStand && explosion && rules.disableExplosions()) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player)) return;

        if (cause == EntityDamageEvent.DamageCause.VOID) {
            // The void rescue brings them back instead; without it, the void works as normal.
            if (plugin.lobbyConfig().voidRescue().enabled()) event.setCancelled(true);
        } else if (cause == EntityDamageEvent.DamageCause.KILL) {
            return; // /kill still works
        } else if (rules.disableDamage() || (cause == EntityDamageEvent.DamageCause.FALL && rules.disableFallDamage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event.getDamager());
        if (attacker == null) return;
        Entity victim = event.getEntity();
        if (victim instanceof Player) {
            if (rules().disablePvp()) event.setCancelled(true);
            return;
        }
        // Item frames, armor stands and the like: part of the build, so builders may still edit them.
        boolean decoration = victim instanceof Hanging || victim instanceof ArmorStand;
        if (decoration) guard(rules().disableBuilding() || rules().disableMobAttacks(), attacker, event);
        else if (rules().disableMobAttacks()) event.setCancelled(true);
    }

    // ---------------------------------------------------------------- hunger

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!rules().disableHunger() || !(event.getEntity() instanceof Player player)) return;
        event.setCancelled(true);
        fillHunger(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (rules().disableHunger()) fillHunger(event.getPlayer());
    }

    private static void fillHunger(Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
    }

    // ---------------------------------------------------------------- the world

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (rules().disableExplosions()) event.blockList().clear();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (rules().disableExplosions()) event.blockList().clear();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingExplode(HangingBreakEvent event) {
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION && rules().disableExplosions()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (rules().disableFireSpread()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() != null) {
            guard(rules().disableBuilding(), event.getPlayer(), event); // flint and steel, fire charges
        } else if (rules().disableFireSpread() && event.getCause() != BlockIgniteEvent.IgniteCause.ARROW) {
            event.setCancelled(true); // spreading, lava, lightning
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        boolean fire = event.getSource().getType() == Material.FIRE || event.getSource().getType() == Material.SOUL_FIRE;
        if (fire ? rules().disableFireSpread() : rules().freezeBlocks()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!rules().disableMobSpawning() || event.getEntity() instanceof ArmorStand) return;
        if (!ALLOWED_SPAWNS.contains(event.getSpawnReason())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        if (rules().freezeBlocks()) event.setCancelled(true);
    }

    /** Ice and snow melting, coral dying, fire burning out, grass turning to dirt. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (rules().freezeBlocks()) event.setCancelled(true);
    }

    /** Snow and ice forming, concrete hardening, copper ageing. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (rules().freezeBlocks()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (rules().freezeBlocks()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMoisture(MoistureChangeEvent event) {
        if (rules().freezeBlocks()) event.setCancelled(true);
    }

    /** Endermen, ravagers, sheep eating grass, sand and gravel starting to fall. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!rules().freezeBlocks() || event.getEntity() instanceof Player) return;
        boolean landing = event.getEntity() instanceof FallingBlock && !event.getTo().isAir();
        if (!landing) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (rules().disableLiquidFlow() && event.getBlock().isLiquid()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (rules().disableWeather() && event.toWeatherState()) event.setCancelled(true);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        applyWorld(event.getWorld());
    }

    /** Clears the weather and locks the time, on startup and /lobby reload. */
    public void applyWorlds() {
        for (World world : plugin.getServer().getWorlds()) applyWorld(world);
    }

    private void applyWorld(World world) {
        LobbyConfig.Protection rules = rules();
        if (rules.disableWeather()) {
            world.setStorm(false);
            world.setThundering(false);
        }
        if (rules.lockTime() >= 0) {
            world.setTime(rules.lockTime());
            setDaylightCycle(world, false);
        }
    }

    @SuppressWarnings("removal") // the only daylight game rule this API version has
    private void setDaylightCycle(World world, boolean on) {
        try {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, on);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Couldn't stop the day/night cycle in " + world.getName() + ": " + e.getMessage());
        }
    }
}
