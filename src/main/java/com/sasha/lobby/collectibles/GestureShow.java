/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.collectibles;

import com.sasha.lobby.Lobby;
import io.papermc.paper.entity.LookAnchor;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestures: a full-size armor stand of the player (their head, their armour or a purple outfit) appears in front of them
 * and acts the gesture out pose by pose (a real dab, a wave, a floss...). While it plays, the player is held in place
 * and their camera is turned to watch it; it all ends in a puff of smoke.
 */
final class GestureShow implements Listener {

    /** Where each part of the stand points, in degrees: head, body, left arm, right arm, left leg, right leg (x, y, z). */
    record Pose(double[] a) {
        static Pose of(double... angles) {
            if (angles.length != 18) throw new IllegalArgumentException("a pose has 18 angles");
            return new Pose(angles);
        }

        Pose lerp(Pose to, double t) {
            double k = Math.max(0, Math.min(1, t));
            double[] out = new double[18];
            for (int i = 0; i < 18; i++) out[i] = a[i] + (to.a[i] - a[i]) * k;
            return new Pose(out);
        }

        /** A copy with one part changed (0 head, 1 body, 2 left arm, 3 right arm, 4 left leg, 5 right leg). */
        Pose with(int part, double x, double y, double z) {
            double[] out = a.clone();
            out[part * 3] = x;
            out[part * 3 + 1] = y;
            out[part * 3 + 2] = z;
            return new Pose(out);
        }

        void apply(ArmorStand stand) {
            stand.setHeadPose(angle(0));
            stand.setBodyPose(angle(1));
            stand.setLeftArmPose(angle(2));
            stand.setRightArmPose(angle(3));
            stand.setLeftLegPose(angle(4));
            stand.setRightLegPose(angle(5));
        }

        private EulerAngle angle(int part) {
            return new EulerAngle(Math.toRadians(a[part * 3]), Math.toRadians(a[part * 3 + 1]), Math.toRadians(a[part * 3 + 2]));
        }
    }

    static final int HEAD = 0, BODY = 1, LEFT_ARM = 2, RIGHT_ARM = 3, LEFT_LEG = 4, RIGHT_LEG = 5;
    static final Pose STAND = Pose.of(0, 0, 0, 0, 0, 0, -10, 0, -10, -10, 0, 10, -1, 0, -1, 1, 0, 1);
    private static final Color OUTFIT = Color.fromRGB(0xB266FF);

    private final Lobby plugin;
    private final Effects fx;
    private final Map<UUID, Show> shows = new HashMap<>();

    private static final class Show {
        ArmorStand stand;
        Location lock;
        BukkitTask task;
    }

    GestureShow(Lobby plugin, Effects fx) {
        this.plugin = plugin;
        this.fx = fx;
    }

    /** How long each gesture plays, in ticks. */
    static int length(String key) {
        return switch (key) {
            case "moonwalk", "levitate", "giant", "sleep", "magic_trick", "zombie_walk", "swim" -> 62;
            default -> 46;
        };
    }

    void play(Player player, String key) {
        stop(player.getUniqueId(), false);
        Location from = player.getLocation();
        Vector ahead = from.getDirection().setY(0);
        if (ahead.lengthSquared() < 0.01) ahead = new Vector(0, 0, 1);
        ahead.normalize();
        Location at = from.clone().add(ahead.clone().multiply(2.6));
        for (int down = 0; down < 3 && at.getBlock().getRelative(0, -1, 0).isPassable(); down++) at.add(0, -1, 0);
        while (!at.getBlock().isPassable() && at.getY() < from.getY() + 2) at.add(0, 1, 0);
        float yaw = from.getYaw() + 180;
        at.setYaw(yaw);
        at.setPitch(0);
        World world = player.getWorld();
        ArmorStand stand = world.spawn(at, ArmorStand.class, s -> {
            s.setSmall(false); // full size, so its hands and the moves are easy to see
            s.setArms(true);
            s.setBasePlate(false);
            s.setGravity(false);
            s.setInvulnerable(true);
            s.setSilent(true);
            s.setCollidable(false);
            s.setDisabledSlots(EquipmentSlot.values());
            s.setCustomNameVisible(false);
            dress(s, player);
            STAND.apply(s);
        });
        fx.tagged(stand, "gesture");

        Show show = new Show();
        show.stand = stand;
        show.lock = from.clone();
        shows.put(player.getUniqueId(), show);

        int length = length(key);
        fx.say(stand, key, length);
        world.spawnParticle(Particle.POOF, at.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
        world.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0, fx.dust(OUTFIT, 1f));
        fx.sound(at, "entity.illusioner.mirror_move", 0.7f, 1.4f);

        Location centre = at.clone();
        Vector away = ahead.clone();                     // from the player towards the stand
        AttributeInstance size = stand.getAttribute(Attribute.SCALE);
        show.task = fx.every(length, t -> {
            if (!stand.isValid()) return;
            if (t == 0) watch(player, stand); // turn them to the show once; after that they can look around
            Location place = centre.clone();
            float turn = yaw;
            Pose pose = STAND;
            double wave;
            switch (key) {
                case "wave" -> {
                    wave = Math.sin(t * 0.6);
                    pose = STAND.with(RIGHT_ARM, -165, 0, 25 + 22 * wave).with(HEAD, 0, 0, 6 * wave);
                    if (t == 2) fx.sound(centre, "entity.villager.ambient", 0.6f, 1.6f);
                }
                case "clap" -> {
                    boolean open = (t / 4) % 2 == 0;
                    pose = STAND.with(LEFT_ARM, -80, 0, open ? -30 : 12).with(RIGHT_ARM, -80, 0, open ? 30 : -12).with(HEAD, -8, 0, 0);
                    if (t % 8 == 4) fx.sound(centre, "block.note_block.snare", 0.7f, 1.4f);
                }
                case "bow" -> {
                    double k = t < 10 ? t / 10.0 : t < 32 ? 1 : Math.max(0, 1 - (t - 32) / 10.0);
                    pose = STAND.with(BODY, 40 * k, 0, 0).with(HEAD, 35 * k, 0, 0)
                            .with(RIGHT_ARM, -10 - 55 * k, -35 * k, 10).with(LEFT_ARM, -10 + 30 * k, 0, -10);
                    if (t == 6) fx.sound(centre, "block.note_block.harp", 0.6f, 0.8f);
                }
                case "spin" -> {
                    turn = yaw + (t + 1) * 16f;
                    pose = STAND.with(LEFT_ARM, 0, 0, -90).with(RIGHT_ARM, 0, 0, 90).with(HEAD, -10, 0, 0);
                    if (t % 11 == 0) fx.sound(centre, "entity.player.attack.sweep", 0.5f, 1.4f);
                }
                case "facepalm" -> {
                    double k = Math.min(1, t / 6.0);
                    pose = STAND.with(RIGHT_ARM, -10 - 115 * k, -45 * k, 10).with(HEAD, 28 * k, 12 * Math.sin(t * 0.35) * k, 0);
                    if (t == 2) fx.sound(centre, "entity.villager.no", 0.8f, 0.9f);
                }
                case "headbang" -> {
                    boolean down = (t / 4) % 2 == 0;
                    pose = STAND.with(HEAD, down ? 45 : -20, 0, 0).with(BODY, down ? 10 : 0, 0, 0)
                            .with(LEFT_ARM, -170, 0, -18).with(RIGHT_ARM, -170, 0, 18);
                    if (t % 8 == 0) fx.sound(centre, "block.note_block.basedrum", 0.8f, 1f);
                    if (t % 8 == 4) fx.sound(centre, "block.note_block.snare", 0.6f, 1f);
                }
                case "celebrate" -> {
                    wave = Math.sin(t * 0.5);
                    pose = STAND.with(LEFT_ARM, -170, 0, -30 - 10 * wave).with(RIGHT_ARM, -170, 0, 30 + 10 * wave).with(HEAD, -15, 0, 0);
                    place.add(0, Math.abs(Math.sin(t * Math.PI / 12)) * 0.6, 0);
                    if (t == 6 || t == 18 || t == 30) {
                        Location top = place.clone().add(0, 2.4, 0);
                        world.spawnParticle(Particle.FIREWORK, top, 20, 0.3, 0.3, 0.3, 0.12);
                        fx.confetti(top, 30, 0.7);
                        fx.sound(top, "entity.firework_rocket.twinkle", 0.6f, 1f + t * 0.01f);
                    }
                }
                case "dab" -> {
                    // head tucked into one elbow, the other arm thrown up and out
                    Pose dab = STAND.with(HEAD, 38, 38, 0).with(LEFT_ARM, -105, 48, 0).with(RIGHT_ARM, -140, 0, 78).with(BODY, 6, 10, 0);
                    double k = t < 4 ? 0 : t < 8 ? (t - 4) / 4.0 : t < 34 ? 1 : Math.max(0, 1 - (t - 34) / 4.0);
                    pose = STAND.lerp(dab, k);
                    if (t == 7) {
                        fx.sound(centre, "entity.player.attack.sweep", 0.7f, 1.6f);
                        world.spawnParticle(Particle.CRIT, centre.clone().add(0, 1.6, 0), 25, 0.4, 0.3, 0.4, 0.3);
                        fx.confetti(centre.clone().add(0, 2.2, 0), 25, 0.6);
                    }
                }
                case "floss" -> {
                    wave = Math.sin(t * 0.6);
                    pose = STAND.with(BODY, 0, 0, 10 * wave).with(LEFT_ARM, -12, 0, -10 + 38 * wave).with(RIGHT_ARM, -12, 0, 10 + 38 * wave)
                            .with(LEFT_LEG, 0, 0, -8 * wave).with(RIGHT_LEG, 0, 0, -8 * wave);
                    if (t % 6 == 0) fx.sound(centre, "block.note_block.hat", 0.6f, 1.2f + (t % 12) / 24f);
                }
                case "moonwalk" -> {
                    wave = Math.sin(t * 0.4);
                    pose = STAND.with(LEFT_LEG, 22 * wave, 0, -1).with(RIGHT_LEG, -22 * wave, 0, 1)
                            .with(LEFT_ARM, -22 * wave - 10, 0, -10).with(RIGHT_ARM, 22 * wave - 10, 0, 10);
                    place.add(away.clone().multiply(Math.min(t, 50) * 0.05));
                    if (t % 8 == 0) {
                        fx.sound(place, "block.note_block.bass", 0.8f, 0.8f + (t % 24) / 24f);
                        world.spawnParticle(Particle.NOTE, place.clone().add(0, 2.3, 0), 0, (t % 24) / 24.0, 0, 0, 1);
                    }
                }
                case "zombie_walk" -> {
                    wave = Math.sin(t * 0.3);
                    pose = STAND.with(LEFT_ARM, -90, 0, -4).with(RIGHT_ARM, -90, 0, 4).with(HEAD, 10, 0, 14 * wave)
                            .with(LEFT_LEG, 18 * wave, 0, -1).with(RIGHT_LEG, -18 * wave, 0, 1);
                    place.add(away.clone().multiply(-Math.min(t, 55) * 0.018));
                    if (t % 14 == 0) fx.sound(place, "entity.zombie.ambient", 0.7f, 1.4f);
                }
                case "sleep" -> {
                    // sat down, nodding off
                    pose = STAND.with(LEFT_LEG, -90, -8, 0).with(RIGHT_LEG, -90, 8, 0).with(HEAD, 32 + 4 * Math.sin(t * 0.15), 0, 12)
                            .with(LEFT_ARM, -35, 0, -12).with(RIGHT_ARM, -35, 0, 12);
                    place.add(0, -0.7, 0);
                    if (t % 14 == 0) {
                        fx.sound(centre, "entity.player.breath", 0.5f, 0.8f);
                        TextDisplay z = world.spawn(centre.clone().add(0.3, 1.2, 0), TextDisplay.class, d -> {
                            d.text(Component.text("Z", net.kyori.adventure.text.format.NamedTextColor.AQUA));
                            d.setBillboard(Display.Billboard.CENTER);
                            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                            d.setTeleportDuration(20);
                        });
                        fx.tagged(z, "z");
                        fx.later(1, () -> z.teleport(z.getLocation().add(0.4, 1, 0)));
                        fx.later(20, z::remove);
                    }
                }
                case "swim" -> {
                    wave = Math.sin(t * 0.35);
                    pose = STAND.with(LEFT_ARM, -90 + 80 * wave, 0, -15).with(RIGHT_ARM, -90 - 80 * wave, 0, 15).with(HEAD, -20, 0, 0)
                            .with(LEFT_LEG, 20 * Math.sin(t * 0.8), 0, -1).with(RIGHT_LEG, -20 * Math.sin(t * 0.8), 0, 1);
                    place.add(0, 0.6 + Math.sin(t * 0.25) * 0.15, 0);
                    if (t % 3 == 0) world.spawnParticle(Particle.BUBBLE_POP, place.clone().add(0, 0.8, 0), 3, 0.4, 0.2, 0.4, 0.02);
                    if (t % 10 == 0) fx.sound(place, "entity.player.swim", 0.5f, 1.2f);
                }
                case "twirl_jump" -> {
                    pose = STAND.with(LEFT_ARM, -170, 0, -20).with(RIGHT_ARM, -170, 0, 20);
                    place.add(0, t < 30 ? Math.sin(t / 30.0 * Math.PI) * 2.2 : 0, 0);
                    turn = yaw + t * 24f;
                    if (t == 0) fx.sound(centre, "entity.player.attack.sweep", 0.8f, 0.8f);
                    if (t == 30) {
                        world.spawnParticle(Particle.CLOUD, centre.clone().add(0, 0.1, 0), 20, 0.4, 0.05, 0.4, 0.05);
                        fx.sound(centre, "entity.generic.big_fall", 0.8f, 1f);
                    }
                }
                case "levitate" -> {
                    pose = STAND.with(LEFT_ARM, -25, 0, -55).with(RIGHT_ARM, -25, 0, 55).with(HEAD, -20, 0, 0)
                            .with(LEFT_LEG, 0, 0, 0).with(RIGHT_LEG, 0, 0, 0);
                    place.add(0, t < 50 ? Math.min(1.6, t * 0.05) + Math.sin(t * 0.2) * 0.08 : Math.max(0, 1.6 - (t - 50) * 0.3), 0);
                    if (t % 3 == 0) world.spawnParticle(Particle.DUST, centre.clone().add(fx.rand(0.4), 0.1, fx.rand(0.4)), 2, 0.1, 0, 0.1, 0,
                            fx.dust(Color.fromRGB(0x9B5CFF), 1.1f));
                    if (t % 5 == 0) world.spawnParticle(Particle.ENCHANT, place.clone().add(0, 1, 0), 8, 0.4, 0.6, 0.4, 0.5);
                    if (t == 2) fx.sound(centre, "block.beacon.activate", 0.6f, 1.6f);
                }
                case "tiny" -> {
                    if (size != null) size.setBaseValue(t < 10 ? 1 - t * 0.07 : t < 36 ? 0.3 : Math.min(1, 0.3 + (t - 36) * 0.07));
                    wave = Math.sin(t * 0.5);
                    pose = STAND.with(LEFT_ARM, -150, 0, -20 - 15 * wave).with(RIGHT_ARM, -150, 0, 20 + 15 * wave);
                    if (t > 10 && t < 36) {
                        place.add(0, Math.abs(wave) * 0.25, 0);
                        turn = yaw + (float) Math.sin(t * 0.4) * 30;
                    }
                    if (t == 10) fx.sound(centre, "entity.bat.ambient", 0.8f, 2f);
                }
                case "magic_trick" -> {
                    pose = STAND.with(LEFT_ARM, -150, 0, -35).with(RIGHT_ARM, -150, 0, 35);
                    if (t < 20) turn = yaw + t * 18f;
                    if (t == 20) {
                        world.spawnParticle(Particle.CLOUD, centre.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.03);
                        world.spawnParticle(Particle.WITCH, centre.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.1);
                        fx.sound(centre, "entity.illusioner.prepare_mirror", 1f, 1.4f);
                        hide(stand, true);
                    }
                    if (t >= 44) {
                        place.add(away.clone().multiply(-1.2));
                        pose = STAND.with(LEFT_ARM, -120, 0, -60).with(RIGHT_ARM, -120, 0, 60).with(HEAD, -15, 0, 0); // ta-da!
                    }
                    if (t == 44) {
                        hide(stand, false);
                        dress(stand, player);
                        world.spawnParticle(Particle.FIREWORK, place.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.1);
                        fx.sound(place, "entity.player.levelup", 0.6f, 1.8f);
                    }
                }
                case "giant" -> {
                    if (size != null) size.setBaseValue(t < 20 ? 1 + t * 0.075 : t < 48 ? 2.5 : Math.max(1, 2.5 - (t - 48) * 0.12));
                    boolean left = (t / 9) % 2 == 0;
                    pose = STAND.with(LEFT_ARM, -30, 0, -40).with(RIGHT_ARM, -30, 0, 40)
                            .with(LEFT_LEG, left ? -25 : 0, 0, -1).with(RIGHT_LEG, left ? 0 : -25, 0, 1);
                    if (t >= 20 && t < 48 && t % 9 == 0) {
                        world.spawnParticle(Particle.BLOCK, centre.clone().add(0, 0.1, 0), 30, 0.8, 0.05, 0.8, 0,
                                centre.clone().add(0, -1, 0).getBlock().getBlockData());
                        fx.sound(centre, "entity.ravager.step", 1f, 0.7f);
                    }
                }
                case "explode" -> {
                    if (size != null && t < 30) size.setBaseValue(1 + t * 0.025);
                    double shake = t < 30 ? Math.sin(t * 2.1) * (2 + t * 0.3) : 0;
                    pose = STAND.with(LEFT_ARM, -20, 0, -60 + shake).with(RIGHT_ARM, -20, 0, 60 - shake).with(HEAD, -10, shake, 0);
                    if (t < 30 && t % 6 == 0) fx.sound(centre, "entity.creeper.primed", 0.8f, 1f + t * 0.02f);
                    stand.setGlowing(t < 30 && t % 4 < 2);
                    if (t == 30) {
                        world.spawnParticle(Particle.EXPLOSION_EMITTER, centre.clone().add(0, 1, 0), 1);
                        world.spawnParticle(Particle.FIREWORK, centre.clone().add(0, 1, 0), 40, 0.3, 0.3, 0.3, 0.3);
                        fx.confetti(centre.clone().add(0, 1, 0), 60, 1.2);
                        fx.sound(centre, "entity.generic.explode", 1f, 1.2f);
                        hide(stand, true);
                    }
                }
                default -> {
                }
            }
            place.setYaw(turn);
            place.setPitch(0);
            stand.teleport(place);
            pose.apply(stand);
        }, () -> stop(player.getUniqueId(), true));
    }

    /** Turns the player's camera to the stand (they're held in place by onMove). */
    private void watch(Player player, ArmorStand stand) {
        if (!player.isOnline()) return;
        Location eye = stand.getEyeLocation();
        player.lookAt(eye.getX(), eye.getY(), eye.getZ(), LookAnchor.EYES);
    }

    /** The player's head and armour; without armour, a purple outfit. */
    private static void dress(ArmorStand stand, Player player) {
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(player.getPlayerProfile()));
        var mine = player.getEquipment();
        var its = stand.getEquipment();
        its.setHelmet(head);
        its.setChestplate(worn(mine.getChestplate(), Material.LEATHER_CHESTPLATE));
        its.setLeggings(worn(mine.getLeggings(), Material.LEATHER_LEGGINGS));
        its.setBoots(worn(mine.getBoots(), Material.LEATHER_BOOTS));
    }

    private static ItemStack worn(ItemStack item, Material fallback) {
        if (item != null && !item.getType().isAir()) return item.clone();
        ItemStack outfit = ItemStack.of(fallback);
        outfit.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(OUTFIT));
        return outfit;
    }

    /** Hides the stand (and what it wears) for the magic trick and the explosion. */
    private static void hide(ArmorStand stand, boolean hidden) {
        stand.setInvisible(hidden);
        if (hidden) stand.getEquipment().clear();
    }

    /** Ends a player's show: the stand vanishes in a puff (unless it's being replaced) and they can move again. */
    void stop(UUID id, boolean puff) {
        Show show = shows.remove(id);
        if (show == null) return;
        if (show.task != null) show.task.cancel();
        ArmorStand stand = show.stand;
        if (stand.isValid()) {
            if (puff && !stand.isInvisible()) {
                Location end = stand.getLocation().add(0, 1, 0);
                stand.getWorld().spawnParticle(Particle.POOF, end, 20, 0.3, 0.5, 0.3, 0.03);
                stand.getWorld().spawnParticle(Particle.DUST, end, 20, 0.4, 0.8, 0.4, 0, fx.dust(OUTFIT, 1f));
                fx.sound(end, "entity.illusioner.mirror_move", 0.6f, 1.8f);
            }
            stand.remove();
        }
    }

    void stopAll() {
        for (UUID id : new java.util.ArrayList<>(shows.keySet())) stop(id, false);
    }

    boolean watching(Player player) {
        return shows.containsKey(player.getUniqueId());
    }

    /** Held in place while watching; they're turned to face the show when it starts and can look around freely. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Show show = shows.get(event.getPlayer().getUniqueId());
        if (show == null) return;
        Location to = event.getTo();
        if (to.getX() == show.lock.getX() && to.getY() == show.lock.getY() && to.getZ() == show.lock.getZ()) return;
        Location held = show.lock.clone();
        held.setYaw(to.getYaw());
        held.setPitch(to.getPitch());
        event.setTo(held);
    }

    /** Teleported away (spawn, a realm, a launch pad...): the show ends. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Show show = shows.get(event.getPlayer().getUniqueId());
        if (show == null) return;
        if (event.getTo().getWorld() != show.lock.getWorld() || event.getTo().distanceSquared(show.lock) > 1) {
            stop(event.getPlayer().getUniqueId(), true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (event.getRightClicked().getScoreboardTags().contains("lobby_collectible")) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId(), false);
    }
}
