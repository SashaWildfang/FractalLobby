/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.listener;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Building, PvP, hurting mobs, fall damage and hunger. Each is controlled under protection: in config.yml. */
public final class ProtectionListener implements Listener {

    private final Lobby plugin;

    public ProtectionListener(Lobby plugin) {
        this.plugin = plugin;
    }

    private LobbyConfig.Protection rules() {
        return plugin.lobbyConfig().protection();
    }

    // ---------------------------------------------------------------- building

    /** Whether a player may place and break blocks: the build permission, or being an op if allowed. */
    public boolean canBuild(Player player) {
        LobbyConfig.Protection rules = rules();
        return player.hasPermission(rules.buildPermission()) || (rules.allowOpsToBuild() && player.isOp());
    }

    private void guardBuild(Player player, Cancellable event) {
        if (!canBuild(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        guardBuild(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        guardBuild(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        guardBuild(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        guardBuild(event.getPlayer(), event);
    }

    /** Paintings and item frames count as building too. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = attacker(event.getRemover());
        if (player != null) guardBuild(player, event);
    }

    // ---------------------------------------------------------------- damage

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FALL && rules().disableFallDamage()) {
            event.setCancelled(true);
        } else if (cause == EntityDamageEvent.DamageCause.VOID && plugin.lobbyConfig().voidRescue().enabled()) {
            event.setCancelled(true); // the void rescue brings them back instead
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event.getDamager());
        if (attacker == null) return;
        boolean victimIsPlayer = event.getEntity() instanceof Player;
        if (victimIsPlayer ? rules().disablePvp() : rules().disableMobAttacks()) {
            // Item frames, armor stands and the like are "mobs" here, but builders may still edit them.
            if (!victimIsPlayer && canBuild(attacker) && !(event.getEntity() instanceof org.bukkit.entity.LivingEntity)) return;
            event.setCancelled(true);
        }
    }

    /** The player behind an action: the entity itself, or whoever shot the projectile. */
    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
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
}
