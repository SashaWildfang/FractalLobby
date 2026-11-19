/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.collectibles;

import com.sasha.lobby.Lobby;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * The gadgets added in 1.41: toys to throw, fire and place. Placed things (a tetherball, a piñata, a swing, a diving
 * board, a tic-tac-toe board, a DJ booth...) are one per player and go away on their own; anyone can play with them.
 * Like every gadget, nothing here hurts anyone or changes the world for real.
 */
final class MoreGadgets implements Listener {

    private final Lobby plugin;
    private final Effects fx;
    /** Each player's placed thing: its entities and its task. */
    private final Map<UUID, Placed> placed = new HashMap<>();
    /** Interaction boxes of placed things: what a right-click on them does. */
    private final Map<UUID, Consumer<Player>> clicks = new HashMap<>();

    private static final class Placed {
        final List<Entity> parts = new ArrayList<>();
        BukkitTask task;
        Runnable end;
    }

    MoreGadgets(Lobby plugin, Effects fx) {
        this.plugin = plugin;
        this.fx = fx;
    }

    void stop() {
        for (UUID id : new ArrayList<>(placed.keySet())) clear(id);
        clicks.clear();
    }

    void quit(Player player) {
        clear(player.getUniqueId());
    }

    private void clear(UUID id) {
        Placed old = placed.remove(id);
        if (old == null) return;
        if (old.task != null) old.task.cancel();
        if (old.end != null) old.end.run();
        for (Entity part : old.parts) {
            clicks.remove(part.getUniqueId());
            part.remove();
        }
    }

    /** A new placed thing for the player (their old one goes). */
    private Placed place(Player player) {
        clear(player.getUniqueId());
        Placed p = new Placed();
        placed.put(player.getUniqueId(), p);
        return p;
    }

    private <T extends Entity> T keep(Placed p, T entity) {
        fx.tagged(entity, "placed");
        p.parts.add(entity);
        return entity;
    }

    private ItemDisplay model(Location at, String model, Material fallback, float scale, float lift) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled(fallback, model));
            d.setTeleportDuration(2);
            d.setTransformation(new Transformation(new Vector3f(0, lift, 0), new AxisAngle4f(), new Vector3f(scale), new AxisAngle4f()));
        });
    }

    private Interaction box(Placed p, Location at, float width, float height, Consumer<Player> onClick) {
        Interaction box = keep(p, at.getWorld().spawn(at, Interaction.class, i -> {
            i.setInteractionWidth(width);
            i.setInteractionHeight(height);
            i.setResponsive(true);
        }));
        clicks.put(box.getUniqueId(), onClick);
        return box;
    }

    private TextDisplay label(Location at, Component text, int life) {
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.text(text);
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(150, 20, 16, 34));
            t.setShadowed(true);
            t.setTeleportDuration(3);
        });
        fx.tagged(d, "label");
        if (life > 0) fx.later(life, d::remove);
        return d;
    }

    private Player nearest(Player from, double range) {
        Player best = null;
        for (Player other : from.getWorld().getPlayers()) {
            if (other == from || !from.canSee(other)) continue;
            double d = other.getLocation().distanceSquared(from.getLocation());
            if (d <= range * range && (best == null || d < best.getLocation().distanceSquared(from.getLocation()))) best = other;
        }
        return best;
    }

    private static Component text(String s, int rgb) {
        return Component.text(s, TextColor.color(rgb));
    }

    // ---------------------------------------------------------------- use

    /** Runs a gadget added in 1.41; false if the key isn't one of them. */
    boolean use(Player player, String key) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        World world = player.getWorld();
        switch (key) {
            case "magic_8_ball" -> magic8(player);
            case "fortune_cookie" -> fortuneCookie(player);
            case "tetherball" -> tetherball(player);
            case "rock_paper_shears" -> rockPaperShears(player);
            case "catapult" -> catapult(player);
            case "pigs_fly" -> pigsFly(player);
            case "creeper_astronaut" -> creeperAstronaut(player);
            case "exploding_sheep" -> explodingSheep(player);
            case "duckling" -> ducklings(player);
            case "pinata" -> pinata(player);
            case "jetpack" -> jetpack(player);
            case "mob_gun" -> mobGun(player);
            case "railgun" -> railgun(player);
            case "explosive_bow" -> {
                Arrow arrow = player.launchProjectile(Arrow.class, look.clone().multiply(2.6));
                arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                arrow.setCritical(true);
                fx.tagged(arrow, "boom");
                fx.sound(eye, "entity.arrow.shoot", 1f, 0.8f);
            }
            case "pong" -> pong(player);
            case "beach_ball" -> bouncyBall(player, "gadget/beach_ball_3d", Material.SNOWBALL, 1.1f, 0.45, 0.62, "entity.slime.jump_small");
            case "rush_pearl" -> {
                if (fx.parkour(player)) return true;
                EnderPearl pearl = player.launchProjectile(EnderPearl.class, look.clone().multiply(1.7));
                fx.tagged(pearl, "rush");
                pearl.addPassenger(player);
                fx.sound(eye, "entity.ender_pearl.throw", 1f, 0.8f);
            }
            case "diamond_shower" -> diamondShower(player);
            case "ghost" -> ghost(player);
            case "rainbow" -> rainbow(player);
            case "diving_board" -> divingBoard(player);
            case "swing" -> swing(player);
            case "pumpkin_rain" -> pumpkinRain(player);
            case "scarecrow" -> scarecrow(player);
            case "witch_cauldron" -> cauldron(player);
            case "let_it_snow" -> letItSnow(player);
            case "ice_skate" -> iceSkate(player);
            case "frisbee" -> frisbee(player);
            case "sand_castle" -> sandCastle(player);
            case "tic_tac_toe" -> ticTacToe(player);
            case "dj_booth" -> djBooth(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- flying things

    /** Throws a harmless mob that poofs where it lands. */
    private <T extends LivingEntity> void launch(Player player, Class<T> type, double speed, String landSound, Consumer<Entity> setup) {
        if (!player.isOnline()) return;
        Location from = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1));
        T mob = player.getWorld().spawn(from, type, m -> {
            m.setInvulnerable(true);
            m.setSilent(false);
            if (m instanceof Mob ai) ai.setAware(false);
            setup.accept(m);
        });
        fx.tagged(mob, "thrown");
        mob.setVelocity(player.getEyeLocation().getDirection().multiply(speed).add(new Vector(0, 0.25, 0)));
        fx.every(60, t -> {
            if (!mob.isValid()) return;
            if (t % 2 == 0) mob.getWorld().spawnParticle(Particle.CLOUD, mob.getLocation(), 1, 0.05, 0.05, 0.05, 0);
            if (t > 6 && mob.isOnGround()) {
                pop(mob, landSound);
            }
        }, () -> pop(mob, landSound));
    }

    private final Map<UUID, Deque<Entity>> critters = new HashMap<>();

    /**
     * The mob gun: a puff and a pop, and a baby animal goes tumbling out in a sparkly arc. It lands with a bounce and a
     * happy noise, then trots about for fifteen seconds (hopping now and then, little hearts) before poofing away. Up to
     * six of a player's critters are about at once; the oldest goes when a seventh is fired.
     */
    private void mobGun(Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        World world = player.getWorld();
        EntityType[] kinds = {EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN, EntityType.RABBIT,
                EntityType.MOOSHROOM, EntityType.PANDA};
        EntityType type = kinds[ThreadLocalRandom.current().nextInt(kinds.length)];
        Location from = eye.clone().add(look.clone().multiply(1.1)).add(0, -0.25, 0);
        if (!from.getBlock().isPassable()) from = eye.clone().add(0, -0.25, 0);
        world.spawnParticle(Particle.POOF, from, 8, 0.08, 0.08, 0.08, 0.05);
        fx.confetti(from, 10, 0.2);
        fx.sound(from, "fractal:gadget.party_popper", 0.6f, 1.5f);
        fx.sound(from, "entity.chicken.egg", 1f, 0.7f);
        Entity critter = world.spawn(from, type.getEntityClass(), m -> {
            m.setInvulnerable(true);
            m.setPersistent(false);
            if (m instanceof Ageable baby) {
                baby.setBaby();
                baby.setAgeLock(true);
            }
            if (m instanceof Mob ai) ai.setAware(false);
            if (m instanceof Sheep sheep) sheep.setColor(DyeColor.values()[ThreadLocalRandom.current().nextInt(DyeColor.values().length)]);
            if (m instanceof LivingEntity living) living.setRemoveWhenFarAway(false);
        });
        fx.tagged(critter, "critter");
        critter.setVelocity(look.clone().multiply(1.2).add(new Vector(0, 0.22, 0)));
        Deque<Entity> mine = critters.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        mine.removeIf(e -> !e.isValid());
        while (mine.size() >= 6) critterPop(mine.pollFirst());
        mine.addLast(critter);
        Color trail = switch (type) {
            case PIG -> Color.fromRGB(0xF4A3B8);
            case COW -> Color.fromRGB(0x8A5A3A);
            case CHICKEN -> Color.fromRGB(0xFFE14D);
            case RABBIT -> Color.fromRGB(0xD8B48A);
            case MOOSHROOM -> Color.fromRGB(0xE0443A);
            case PANDA -> Color.fromRGB(0x2C2A40);
            default -> critter instanceof Sheep sheep && sheep.getColor() != null ? sheep.getColor().getColor() : Color.WHITE;
        };
        String noise = switch (type) {
            case MOOSHROOM -> "entity.cow.ambient";
            default -> "entity." + type.getKey().getKey() + ".ambient";
        };
        int[] landed = {-1};
        float[] spin = {critter.getLocation().getYaw()};
        fx.every(360, t -> {
            if (!critter.isValid()) return;
            Location at = critter.getLocation();
            if (landed[0] < 0) {                                       // flying: tumbling round, a sparkly trail
                spin[0] += 40;
                critter.setRotation(spin[0], 0);
                world.spawnParticle(Particle.DUST, at.clone().add(0, 0.3, 0), 2, 0.08, 0.08, 0.08, 0, new Particle.DustOptions(trail, 1.1f));
                if (t % 3 == 0) fx.driftFx(at.clone().add(0, 0.3, 0), "gadget/fx_sparkle", 0.16f, new Vector(), 10);
                if (t > 3 && critter.isOnGround()) {
                    landed[0] = t;
                    critter.setVelocity(new Vector(0, 0.34, 0));       // a bounce on landing
                    world.spawnParticle(Particle.POOF, at, 6, 0.2, 0.05, 0.2, 0.02);
                    world.spawnParticle(Particle.HEART, at.clone().add(0, 0.7, 0), 2, 0.2, 0.1, 0.2, 0);
                    fx.sound(at, noise, 0.9f, 1.5f);
                    fx.sound(at, "entity.slime.jump_small", 0.6f, 1.4f);
                }
                if (t > 120) landed[0] = t;                            // stuck somewhere odd: just start trotting
                return;
            }
            int since = t - landed[0];
            if (since == 8 && critter instanceof Mob ai) ai.setAware(true);   // off it trots
            if (since > 8 && since % 45 == 0 && critter.isOnGround()) {      // a happy hop now and then
                critter.setVelocity(critter.getVelocity().setY(0.3));
                world.spawnParticle(Particle.HEART, at.clone().add(0, 0.7, 0), 1, 0.1, 0.1, 0.1, 0);
                if (ThreadLocalRandom.current().nextBoolean()) fx.sound(at, noise, 0.6f, 1.5f);
            }
            if (since > 300) critterPop(critter);                      // fifteen seconds of fun, then it poofs
        }, () -> {
            critterPop(critter);
            Deque<Entity> list = critters.get(player.getUniqueId());
            if (list != null) list.remove(critter);
        });
    }

    private void critterPop(Entity e) {
        if (e == null || !e.isValid()) {
            return;
        }
        Location at = e.getLocation().add(0, 0.4, 0);
        at.getWorld().spawnParticle(Particle.POOF, at, 10, 0.2, 0.2, 0.2, 0.03);
        fx.driftFx(at, "gadget/fx_star", 0.25f, new Vector(0, 0.04, 0), 12);
        fx.sound(at, "entity.chicken.egg", 0.8f, 1.6f);
        fx.sound(at, "fractal:gadget.party_popper", 0.4f, 1.8f);
        e.remove();
    }

    private void pop(Entity e, String sound) {
        if (!e.isValid()) return;
        Location at = e.getLocation().add(0, 0.4, 0);
        at.getWorld().spawnParticle(Particle.POOF, at, 12, 0.2, 0.2, 0.2, 0.03);
        fx.confetti(at, 15, 0.4);
        fx.sound(at, sound != null ? sound : "entity.chicken.egg", 0.8f, 1.2f);
        e.remove();
    }

    /**
     * The magic 8 ball: a real 8 ball appears floating in front of the player, gets a good shake while it thinks, then
     * its answer appears above it; after a few seconds it shrinks away.
     */
    private void magic8(Player player) {
        String[] answers = {"It is certain.", "Without a doubt!", "Yes, definitely.", "Ask again later...", "Cannot predict now.",
                "Don't count on it.", "My sources say no.", "Very doubtful.", "Outlook good!", "Signs point to yes!"};
        String answer = answers[ThreadLocalRandom.current().nextInt(answers.length)];
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().setY(0);
        if (look.lengthSquared() < 1e-4) look = new Vector(0, 0, 1);
        Location at = eye.clone().add(look.normalize().multiply(1.6)).add(0, -0.25, 0);
        if (!at.getBlock().isPassable()) at = eye.clone().add(0, 0.6, 0);
        World world = at.getWorld();
        Location spot = at;
        ItemDisplay ball = world.spawn(spot, ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled(Material.ENDER_EYE, "gadget/magic_8_ball_3d"));
            d.setBillboard(Display.Billboard.CENTER);
            d.setTeleportDuration(1);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.01f), new AxisAngle4f()));
        });
        fx.tagged(ball, "eight_ball");
        fx.sound(spot, "block.amethyst_block.chime", 1f, 0.6f);
        TextDisplay[] text = new TextDisplay[1];
        fx.every(90, t -> {
            if (!ball.isValid()) return;
            float size = t < 5 ? 0.55f * (t + 1) / 5f : t > 82 ? 0.55f * (90 - t) / 8f : 0.55f;
            float shake = t >= 5 && t < 28 ? (float) Math.sin(t * 1.9) * 0.35f : 0f;       // a good shake while it thinks
            float bob = (float) Math.sin(t * 0.15) * 0.04f;
            ball.setInterpolationDelay(0);
            ball.setInterpolationDuration(1);
            ball.setTransformation(new Transformation(new Vector3f(shake * 0.15f, bob, 0), new AxisAngle4f(shake, 0, 0, 1),
                    new Vector3f(Math.max(0.01f, size)), new AxisAngle4f()));
            if (t >= 5 && t < 28 && t % 4 == 0) fx.sound(spot, "block.bubble_column.bubble_pop", 0.6f, 0.7f + t * 0.02f);
            if (t == 28) {
                text[0] = label(spot.clone().add(0, 0.55, 0), text(answer, 0xE2D0FF), 0);
                world.spawnParticle(Particle.WITCH, spot, 12, 0.2, 0.2, 0.2, 0.05);
                fx.sound(spot, "block.amethyst_block.resonate", 0.9f, 1.4f);
            }
        }, () -> {
            ball.remove();
            if (text[0] != null) text[0].remove();
        });
    }

    /**
     * The fortune cookie: a fortune cookie appears floating in front of the player, wobbles while they crack it, then
     * snaps in two: the halves fall apart, crumbs fly, and the fortune slip rises out with the fortune above it.
     */
    private void fortuneCookie(Player player) {
        String[] fortunes = {"A big win is in your future.", "Today is a lucky day.", "Someone will GG you soon.",
                "You will find something legendary.", "A friend is closer than you think.", "Adventure awaits on another server.",
                "Your next box holds a surprise.", "Patience brings great dust."};
        String fortune = fortunes[ThreadLocalRandom.current().nextInt(fortunes.length)];
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().setY(0);
        if (look.lengthSquared() < 1e-4) look = new Vector(0, 0, 1);
        look.normalize();
        Vector right = new Vector(-look.getZ(), 0, look.getX());
        Location at = eye.clone().add(look.clone().multiply(1.6)).add(0, -0.25, 0);
        if (!at.getBlock().isPassable()) at = eye.clone().add(0, 0.6, 0);
        World world = at.getWorld();
        Location spot = at;
        float size = 0.7f;
        ItemDisplay whole = sprite(spot, "gadget/fc_whole", 0.01f);
        ItemDisplay[] halves = new ItemDisplay[2];
        ItemDisplay[] slip = new ItemDisplay[1];
        TextDisplay[] text = new TextDisplay[1];
        fx.sound(spot, "entity.item.pickup", 0.8f, 0.8f);
        fx.every(100, t -> {
            if (t < 5) {                                                   // pops in
                scale(whole, size * (t + 1) / 5f, 0, 0, 0);
            } else if (t < 26) {                                           // a wobble that grows as it's squeezed
                float wobble = (float) Math.sin(t * 1.7) * 0.06f * (t - 5) / 7f;
                scale(whole, size * (1 - (t - 5) * 0.006f), wobble, 0, 0);
                if (t % 5 == 0) fx.sound(spot, "block.bamboo_wood.step", 0.6f, 1.4f + t * 0.01f);
            } else if (t == 26) {                                          // snap!
                whole.remove();
                // (a picture turned to face you shows mirrored, so the half on your left is drawn as the right half)
                halves[0] = sprite(spot, "gadget/fc_right", size);
                halves[1] = sprite(spot, "gadget/fc_left", size);
                fx.sound(spot, "entity.generic.eat", 1f, 1.5f);
                fx.sound(spot, "block.bamboo_wood.break", 0.8f, 1.6f);
                world.spawnParticle(Particle.ITEM, spot, 14, 0.12, 0.12, 0.12, 0.08, new ItemStack(Material.COOKIE));
            } else if (t < 40) {                                           // the halves fall apart, the slip rises out
                float k = (t - 26) / 14f;
                for (int i = 0; i < 2; i++) {
                    int side = i == 0 ? -1 : 1;
                    halves[i].teleport(spot.clone().add(right.clone().multiply(side * 0.32 * k)).add(0, -0.12 * k * k, 0));
                    scale(halves[i], size, -side * 0.45f * k, 0, 0);
                }
                if (t == 28) slip[0] = sprite(spot, "gadget/fc_slip", 0.01f);
                if (slip[0] != null) {
                    slip[0].teleport(spot.clone().add(0, 0.28 * k, 0));
                    scale(slip[0], 0.75f * Math.min(1, k * 1.4f), 0, 0, 0);
                }
            } else if (t == 40) {
                text[0] = label(spot.clone().add(0, 0.7, 0), text("\"" + fortune + "\"", 0xFFE8A0), 0);
                fx.sound(spot, "block.amethyst_block.chime", 0.9f, 1.3f);
            } else if (t > 90) {                                           // and it all shrinks away
                float k = (100 - t) / 10f;
                for (ItemDisplay part : new ItemDisplay[]{halves[0], halves[1], slip[0]}) if (part != null) scale(part, size * k, 0, 0, 0);
            }
        }, () -> {
            whole.remove();
            for (ItemDisplay part : new ItemDisplay[]{halves[0], halves[1], slip[0]}) if (part != null) part.remove();
            if (text[0] != null) text[0].remove();
        });
    }

    /** A flat picture floating in the air, always facing whoever looks at it. */
    private ItemDisplay sprite(Location at, String model, float size) {
        ItemDisplay d = at.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setItemStack(fx.modelled(Material.PAPER, model));
            e.setBillboard(Display.Billboard.CENTER);
            e.setTeleportDuration(1);
            e.setPersistent(false);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(size), new AxisAngle4f()));
        });
        fx.tagged(d, "fortune");
        return d;
    }

    private static void scale(ItemDisplay d, float size, float tilt, float x, float y) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(1);
        d.setTransformation(new Transformation(new Vector3f(x, y, 0), new AxisAngle4f(tilt, 0, 0, 1), new Vector3f(Math.max(0.01f, size)), new AxisAngle4f()));
    }

    private void hotPotato(Player player) {
        Player target = nearest(player, 10);
        if (target == null) {
            plugin.messages().send(player, "collectibles.gadget.no-one-near");
            return;
        }
        List<Player> passes = new ArrayList<>(List.of(player, target));
        ItemDisplay potato = player.getWorld().spawn(player.getEyeLocation(), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.BAKED_POTATO));
            d.setTeleportDuration(3);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.6f), new AxisAngle4f()));
        });
        fx.tagged(potato, "potato");
        int[] holder = {1};
        Player[] last = {player};
        fx.every(100, t -> {
            Player now = passes.get(holder[0]);
            if (!now.isOnline()) return;
            Location over = now.getLocation().add(0, 2.4 + Math.abs(Math.sin(t * 0.3)) * 0.3, 0);
            potato.teleport(over);
            potato.getWorld().spawnParticle(Particle.SMOKE, over, 1, 0.05, 0.05, 0.05, 0.01);
            if (t % 10 == 0) fx.sound(over, "block.note_block.hat", 0.8f, 1f + t * 0.01f);
            if (t > 0 && t % 25 == 0) {           // pass it on to someone near the one holding it
                Player next = nearest(now, 10);
                if (next == null || next == last[0] && nearest(now, 10) == null) next = last[0];
                last[0] = now;
                if (!passes.contains(next)) passes.add(next);
                holder[0] = passes.indexOf(next);
                fx.sound(over, "entity.snowball.throw", 1f, 1.2f);
            }
        }, () -> {
            Player stuck = passes.get(holder[0]);
            Location at = potato.getLocation();
            potato.remove();
            at.getWorld().spawnParticle(Particle.EXPLOSION, at, 1);
            fx.confetti(at, 30, 0.6);
            fx.sound(at, "entity.generic.explode", 0.8f, 1.4f);
            for (Player p : passes) {
                if (p.isOnline()) plugin.messages().send(p, "collectibles.gadget.potato-boom", net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("player", stuck.getName()));
            }
        });
    }

    private void pigsFly(Player player) {
        Location at = player.getLocation().add(0, 2.5, 0);
        Pig pig = player.getWorld().spawn(at, Pig.class, p -> {
            p.setInvulnerable(true);
            p.setAware(false);
            p.setGravity(false);
        });
        fx.tagged(pig, "pig");
        ItemDisplay[] wings = new ItemDisplay[2];
        for (int i = 0; i < 2; i++) {
            String model = i == 0 ? "cloak/part/wing_left" : "cloak/part/wing_right";
            wings[i] = pig.getWorld().spawn(at, ItemDisplay.class, d -> d.setItemStack(fx.modelled(Material.FEATHER, model)));
            fx.tagged(wings[i], "wing");
            pig.addPassenger(wings[i]);
        }
        fx.sound(at, "entity.pig.ambient", 1f, 1.4f);
        fx.every(140, t -> {
            if (!pig.isValid() || !player.isOnline()) return;
            double a = t * 0.08;
            Location go = player.getLocation().add(Math.cos(a) * 2.5, 2.4 + Math.sin(t * 0.15) * 0.4, Math.sin(a) * 2.5);
            go.setYaw((float) Math.toDegrees(a) + 180);
            pig.teleport(go);
            float flap = (float) Math.toRadians(20 + 30 * Math.abs(Math.sin(t * 0.5)));
            for (int i = 0; i < 2; i++) {
                float side = i == 0 ? 1 : -1;
                wings[i].setInterpolationDelay(0);
                wings[i].setInterpolationDuration(2);
                wings[i].setTransformationMatrix(new Matrix4f().rotateY((float) Math.toRadians(-go.getYaw()))
                        .translate(0, -0.55f, -0.05f).rotateY(side * flap).scale(0.4f));
            }
            if (t % 30 == 0) fx.sound(go, "entity.pig.ambient", 0.8f, 1.6f);
        }, () -> {
            for (ItemDisplay w : wings) if (w != null) w.remove();
            pop(pig, "entity.pig.death");
        });
    }

    private void creeperAstronaut(Player player) {
        Location at = fx.ground(player);
        Creeper creeper = player.getWorld().spawn(at, Creeper.class, c -> {
            c.setInvulnerable(true);
            c.setAware(false);
            c.setGravity(false);
            c.setMaxFuseTicks(Integer.MAX_VALUE);
        });
        fx.tagged(creeper, "creeper");
        ItemDisplay helmet = player.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.GLASS));
            d.setTransformation(new Transformation(new Vector3f(0, -0.55f, 0), new AxisAngle4f(), new Vector3f(0.62f), new AxisAngle4f()));
        });
        fx.tagged(helmet, "helmet");
        creeper.addPassenger(helmet);
        fx.sound(at, "entity.creeper.primed", 1f, 0.6f);
        fx.every(70, t -> {
            if (!creeper.isValid()) return;
            if (t < 20) {
                creeper.getWorld().spawnParticle(Particle.SMOKE, at, 4, 0.3, 0.05, 0.3, 0.02);
                return;
            }
            Location up = at.clone().add(0, (t - 20) * (t - 20) * 0.008, 0);
            creeper.teleport(up);
            creeper.getWorld().spawnParticle(Particle.FLAME, up, 4, 0.1, 0, 0.1, 0.02);
            creeper.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, up, 1, 0.1, 0, 0.1, 0.01);
            if (t == 20) fx.sound(at, "entity.firework_rocket.launch", 1f, 0.6f);
        }, () -> {
            Location boom = creeper.getLocation().add(0, 1, 0);
            helmet.remove();
            creeper.remove();
            Firework fw = boom.getWorld().spawn(boom, Firework.class, f -> {
                FireworkMeta meta = f.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.CREEPER).withColor(Color.LIME, Color.GREEN).withFlicker().build());
                f.setFireworkMeta(meta);
            });
            fx.tagged(fw, "firework");
            fw.detonate();
        });
    }

    private void explodingSheep(Player player) {
        Location at = fx.ground(player);
        Sheep sheep = player.getWorld().spawn(at, Sheep.class, s -> {
            s.setInvulnerable(true);
            s.setAware(false);
        });
        fx.tagged(sheep, "sheep");
        DyeColor[] colours = {DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.LIGHT_BLUE, DyeColor.PURPLE, DyeColor.MAGENTA};
        fx.every(60, t -> {
            if (!sheep.isValid()) return;
            if (t % Math.max(1, 8 - t / 10) == 0) {
                sheep.setColor(colours[(t / 2) % colours.length]);
                fx.sound(sheep.getLocation(), "block.note_block.hat", 0.7f, 0.8f + t * 0.02f);
            }
        }, () -> {
            if (!sheep.isValid()) return;
            Location boom = sheep.getLocation().add(0, 0.6, 0);
            sheep.remove();
            boom.getWorld().spawnParticle(Particle.EXPLOSION, boom, 1);
            fx.confetti(boom, 60, 0.9);
            fx.sound(boom, "entity.generic.explode", 0.9f, 1.3f);
            for (int i = 0; i < 6; i++) {     // little lambs scatter
                Sheep lamb = boom.getWorld().spawn(boom, Sheep.class, l -> {
                    l.setBaby();
                    l.setInvulnerable(true);
                    l.setColor(colours[ThreadLocalRandom.current().nextInt(colours.length)]);
                });
                fx.tagged(lamb, "lamb");
                lamb.setVelocity(new Vector(fx.rand(0.5), 0.45, fx.rand(0.5)));
                fx.later(60, () -> pop(lamb, "entity.sheep.ambient"));
            }
        });
    }

    private void ducklings(Player player) {
        Location at = player.getLocation();
        List<Chicken> line = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Chicken duck = player.getWorld().spawn(at, Chicken.class, c -> {
                c.setInvulnerable(true);
                c.setAware(false);
            });
            if (i > 0) duck.setBaby();
            fx.tagged(duck, "duck");
            line.add(duck);
        }
        fx.every(300, t -> {
            if (!player.isOnline()) return;
            Location lead = player.getLocation();
            Vector back = lead.getDirection().setY(0);
            if (back.lengthSquared() > 0) back.normalize().multiply(-1);
            for (int i = 0; i < line.size(); i++) {
                Chicken duck = line.get(i);
                if (!duck.isValid()) continue;
                Location want = lead.clone().add(back.clone().multiply(1.2 + i * 0.9));
                Vector move = want.toVector().subtract(duck.getLocation().toVector());
                if (move.lengthSquared() > 64) duck.teleport(want);
                else duck.setVelocity(move.multiply(0.25).setY(duck.isOnGround() && move.getY() > 0.3 ? 0.4 : duck.getVelocity().getY()));
                duck.setRotation((float) Math.toDegrees(Math.atan2(-move.getX(), move.getZ())), 0);
            }
            if (t % 40 == 0) fx.sound(lead, "entity.chicken.ambient", 0.6f, 1.8f);
        }, () -> line.forEach(d -> pop(d, "entity.chicken.ambient")));
    }

    private void jetpack(Player player) {
        if (fx.parkour(player)) return;
        ItemDisplay pack = fx.rider(player, "gadget/jetpack_3d");
        fx.sound(player.getLocation(), "entity.firework_rocket.launch", 1f, 0.6f);
        fx.every(60, t -> {
            if (!player.isOnline()) return;
            float yaw = (float) Math.toRadians(player.getLocation().getYaw());
            pack.setInterpolationDelay(0);
            pack.setInterpolationDuration(2);
            pack.setTransformationMatrix(new Matrix4f().rotateY(-yaw).translate(0, -0.65f, -0.28f).scale(0.6f));
            if (!player.isSneaking()) {
                Vector v = player.getVelocity();
                player.setVelocity(new Vector(v.getX() + player.getLocation().getDirection().getX() * 0.05, Math.min(0.55, v.getY() + 0.18),
                        v.getZ() + player.getLocation().getDirection().getZ() * 0.05));
            }
            player.setFallDistance(0);
            Location behind = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(-0.35)).add(0, 0.8, 0);
            player.getWorld().spawnParticle(Particle.FLAME, behind, 3, 0.08, 0.05, 0.08, 0.02);
            player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, behind, 1, 0.05, 0.05, 0.05, 0.01);
            if (t % 6 == 0) fx.sound(behind, "block.fire.ambient", 0.6f, 1.6f);
        }, () -> {
            pack.remove();
            player.setFallDistance(0);
        });
    }

    private void railgun(Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, look, 40);
        double length = hit == null ? 40 : hit.getHitPosition().distance(eye.toVector());
        fx.sound(eye, "block.beacon.deactivate", 1f, 2f);
        fx.sound(eye, "entity.warden.sonic_boom", 0.4f, 1.8f);
        for (double d = 0.8; d < length; d += 0.3) {
            Location at = eye.clone().add(look.clone().multiply(d));
            player.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0x9FE8FF), 0.8f));
            if (((int) (d * 10)) % 6 == 0) player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 1, 0.05, 0.05, 0.05, 0.02);
        }
        Location end = eye.clone().add(look.clone().multiply(length));
        player.getWorld().spawnParticle(Particle.FLASH, end, 1, 0, 0, 0, 0, Color.fromRGB(0x9FE8FF));
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, end, 30, 0.3, 0.3, 0.3, 0.3);
        fx.sound(end, "entity.lightning_bolt.impact", 0.5f, 1.8f);
    }

    /**
     * A ball anyone can play with: hit it (left- or right-click) to send it where you look, harder when sprinting, or
     * just run into it. It starts in the air, falls, bounces off the ground and walls, rolls to a stop, and spins as it goes.
     */
    /**
     * Pong, for real: a dark court with glowing lines appears on the ground in front of the player, with a paddle at
     * each end and a square ball. The player's paddle follows them as they walk side to side along their end; the far
     * paddle is the Lobby's, until someone else walks into that end and takes it over. The ball speeds up with every
     * hit, the angle depends on where it hits the paddle, and first to 5 wins.
     */
    private void pong(Player player) {
        final double L = 10, W = 6, R = 0.175, PADDLE = 0.8;
        float yaw = Math.round(player.getLocation().getYaw() / 90f) * 90f;
        Vector f = new Location(player.getWorld(), 0, 0, 0, yaw, 0).getDirection().setY(0).normalize();
        f = new Vector(Math.round(f.getX()), 0, Math.round(f.getZ()));
        final Vector fwd = f;
        final Vector side = new Vector(-f.getZ(), 0, f.getX());
        Location feet = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        Location c = feet.clone().add(fwd.clone().multiply(L / 2 + 0.5));
        for (int v = (int) -(L / 2); v <= L / 2; v++) {                   // a flat, open court
            for (int u = (int) -(W / 2); u <= W / 2; u++) {
                Block cell = c.clone().add(side.clone().multiply(u)).add(fwd.clone().multiply(v)).getBlock();
                boolean open = cell.isPassable() || cell.getType() == Material.SNOW;     // snow is fine: it's hidden while we play
                Block above = cell.getRelative(0, 1, 0);
                if (!open || !(above.isPassable() || above.getType() == Material.SNOW) || !cell.getRelative(0, -1, 0).getType().isSolid()) {
                    plugin.messages().send(player, "collectibles.gadget.pong-room");
                    fx.sound(player.getLocation(), "entity.villager.no", 0.8f, 1.2f);
                    fx.refund(player, "gadget:pong");
                    return;
                }
            }
        }
        Placed p = place(player);
        World world = c.getWorld();
        // snow, grass, flowers and the like on the court are hidden while the game is up, and come back after
        List<Block> hidden = new ArrayList<>();
        for (int v = (int) -(L / 2); v <= L / 2; v++) {
            for (int u = (int) -(W / 2); u <= W / 2; u++) {
                Block cell = c.clone().add(side.clone().multiply(u)).add(fwd.clone().multiply(v)).getBlock();
                for (Block bl : new Block[]{cell, cell.getRelative(0, 1, 0)}) if (!bl.getType().isAir()) hidden.add(bl);
            }
        }
        BlockData air = Material.AIR.createBlockData();
        Runnable hide = () -> {
            for (Player viewer : world.getPlayers()) for (Block bl : hidden) viewer.sendBlockChange(bl.getLocation(), air);
        };
        hide.run();
        p.end = () -> {
            for (Player viewer : world.getPlayers()) for (Block bl : hidden) viewer.sendBlockChange(bl.getLocation(), bl.getBlockData());
        };
        java.util.function.BiFunction<Double, Double, Location> at = (u, v) -> c.clone().add(side.clone().multiply(u)).add(fwd.clone().multiply(v));
        // a flat box on the court: `along` the court, `across` it, `height` up, from `lift` above the ground
        interface Slab { BlockDisplay make(double u, double v, double along, double across, double height, double lift, Material m); }
        Slab slab = (u, v, along, across, height, lift, m) -> {
            double sx = Math.abs(fwd.getX()) * along + Math.abs(side.getX()) * across;
            double sz = Math.abs(fwd.getZ()) * along + Math.abs(side.getZ()) * across;
            return keep(p, world.spawn(at.apply(u, v), BlockDisplay.class, d -> {
                d.setBlock(m.createBlockData());
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
                d.setTeleportDuration(1);
                d.setTransformation(new Transformation(new Vector3f((float) (-sx / 2), (float) lift, (float) (-sz / 2)), new AxisAngle4f(),
                        new Vector3f((float) sx, (float) height, (float) sz), new AxisAngle4f()));
            }));
        };
        slab.make(0, 0, L, W, 0.02, 0.005, Material.BLACK_CONCRETE);                                 // the court
        // the lines sit just inside the court's edges, flush with them
        for (int s2 : new int[]{-1, 1}) slab.make(s2 * (W / 2 - 0.06), 0, L, 0.12, 0.03, 0.01, Material.WHITE_CONCRETE);
        slab.make(0, -(L / 2 - 0.06), 0.12, W - 0.24, 0.03, 0.01, Material.LIGHT_BLUE_CONCRETE);
        slab.make(0, L / 2 - 0.06, 0.12, W - 0.24, 0.03, 0.01, Material.PINK_CONCRETE);
        for (double u = -W / 2 + 0.5; u < W / 2; u += 1) slab.make(u, 0, 0.1, 0.5, 0.03, 0.01, Material.LIGHT_GRAY_CONCRETE);   // the dashed middle
        BlockDisplay[] paddles = {slab.make(0, -L / 2 + 0.6, 0.25, PADDLE * 2, 0.5, 0.03, Material.LIGHT_BLUE_CONCRETE),
                slab.make(0, L / 2 - 0.6, 0.25, PADDLE * 2, 0.5, 0.03, Material.PINK_CONCRETE)};
        paddles[0].setGlowing(true);
        paddles[0].setGlowColorOverride(Color.fromRGB(0x3FD8FF));
        paddles[1].setGlowing(true);
        paddles[1].setGlowColorOverride(Color.fromRGB(0xFF5AB4));
        BlockDisplay ball = slab.make(0, 0, 0.35, 0.35, 0.35, 0.1, Material.WHITE_CONCRETE);
        ball.setGlowing(true);
        ball.setGlowColorOverride(Color.WHITE);
        TextDisplay score = keep(p, label(at.apply(0d, 0d).add(0, 2.3, 0), Component.empty(), 0));
        fx.sound(c, "block.beacon.activate", 0.7f, 1.8f);

        double[] pu = {0, 0};                                          // where each paddle is, across the court
        double[] b = {0, 0, 0, 0};                                     // the ball: u, v, du, dv
        int[] pts = {0, 0};
        int[] wait = {40};                                             // ticks until the next serve
        int[] serveTo = {-1};                                          // the first serve goes to the player
        double[] speed = {0.2};
        double[] aim = {0};                                            // the Lobby's little mistake, new each rally
        Player[] far = {null};
        boolean[] over = {false};
        Runnable showScore = () -> score.text(text(player.getName() + "  ", 0x3FD8FF).append(text(pts[0] + " : " + pts[1], 0xFFFFFF))
                .append(text("  " + (far[0] == null ? "Lobby" : far[0].getName()), 0xFF5AB4))
                .append(Component.newline()).append(text(far[0] == null ? "Walk into the pink end to play!" : "First to 5", 0xA9A3C2))
                .append(Component.newline()).append(text("Look left and right to steer", 0x7A7F96)));
        showScore.run();
        p.task = fx.every(2400, t -> {
            if (t % 40 == 0) hide.run();                                // (again now and then, for anyone who came along)
            if (over[0]) return;
            // where a player is on the court: (u across, v along)
            java.util.function.Function<Player, double[]> local = who -> {
                Vector d = who.getLocation().toVector().subtract(c.toVector());
                return new double[]{d.dot(side), d.dot(fwd)};
            };
            // the player walking away from their end ends the game straight away
            double[] me = player.isOnline() && player.getWorld() == world ? local.apply(player) : null;
            if (me == null || me[1] < -L / 2 - 3.5 || me[1] > -L / 2 + 2 || Math.abs(me[0]) > W / 2 + 1.5) {
                if (player.isOnline()) fx.sound(player.getLocation(), "block.note_block.bass", 0.8f, 0.6f);
                clear(player.getUniqueId());
                return;
            }
            // the far end: whoever walks in takes over from the Lobby, and gives it back when they walk off
            if (far[0] != null) {
                double[] q = far[0].isOnline() && far[0].getWorld() == world ? local.apply(far[0]) : null;
                if (q == null || q[1] < 0 || q[1] > L / 2 + 3 || Math.abs(q[0]) > W / 2 + 2) {
                    far[0] = null;
                    showScore.run();
                }
            } else {
                for (Player other : world.getPlayers()) {
                    if (other.equals(player) || fx.parkour(other)) continue;
                    double[] q = local.apply(other);
                    if (q[1] > L / 2 - 2.5 && q[1] < L / 2 + 2 && Math.abs(q[0]) < W / 2 + 1) {
                        far[0] = other;
                        fx.sound(other.getLocation(), "block.note_block.bit", 1f, 2f);
                        showScore.run();
                        break;
                    }
                }
            }
            for (int i = 0; i < 2; i++) {                              // move the paddles
                Player who = i == 0 ? player : far[0];
                double paddleV = i == 0 ? -L / 2 + 0.6 : L / 2 - 0.6;
                double target;
                double step;
                if (who != null) {
                    // the paddle goes where the player is looking along their end line (just turn to steer), or, if
                    // they're looking away from the court, where they stand
                    double[] q = local.apply(who);
                    Vector eye = who.getEyeLocation().getDirection();
                    double lu = eye.dot(side), lv = eye.dot(fwd) * (i == 0 ? 1 : -1);
                    double gap = Math.abs(paddleV - q[1]);
                    target = lv > 0.15 ? q[0] + lu / lv * Math.max(2.5, gap) : q[0];   // (a quarter turn or so reaches either side)
                    step = 0.6;
                } else {                                               // the Lobby: follows the ball (a bit slowly)
                    boolean coming = (i == 0 ? b[3] < 0 : b[3] > 0) && wait[0] <= 0;
                    target = coming ? b[0] + aim[0] : 0;
                    step = 0.11;
                }
                target = Math.max(-W / 2 + PADDLE, Math.min(W / 2 - PADDLE, target));
                pu[i] += Math.max(-step, Math.min(step, target - pu[i]));
                paddles[i].teleport(at.apply(pu[i], paddleV));
            }
            if (wait[0] > 0) {                                         // waiting to serve: the ball sits in the middle
                if (--wait[0] == 0) {
                    double a = ThreadLocalRandom.current().nextDouble(-0.5, 0.5);
                    speed[0] = 0.2;
                    b[0] = 0;
                    b[1] = 0;
                    b[2] = Math.sin(a) * speed[0];
                    b[3] = Math.cos(a) * speed[0] * serveTo[0];
                    aim[0] = ThreadLocalRandom.current().nextDouble(-0.9, 0.9);
                    fx.sound(c, "block.note_block.bit", 1f, 1.2f);
                }
                ball.teleport(at.apply(0d, 0d));
                return;
            }
            b[0] += b[2];
            b[1] += b[3];
            if (Math.abs(b[0]) > W / 2 - R) {                          // off the side walls
                b[0] = Math.signum(b[0]) * (W / 2 - R);
                b[2] = -b[2];
                fx.sound(at.apply(b[0], b[1]), "block.note_block.bit", 0.8f, 0.9f);
            }
            for (int i = 0; i < 2; i++) {                              // off the paddles
                int dir = i == 0 ? -1 : 1;
                double face = dir * (L / 2 - 0.6 - 0.125);
                boolean moving = dir < 0 ? b[3] < 0 : b[3] > 0;
                if (moving && dir * (b[1] + dir * R) >= dir * face && dir * b[1] < dir * face + 0.5) {
                    double off = (b[0] - pu[i]) / (PADDLE + R);
                    if (Math.abs(off) <= 1) {
                        speed[0] = Math.min(0.5, speed[0] * 1.07);
                        double a = off * Math.toRadians(55);
                        b[2] = Math.sin(a) * speed[0];
                        b[3] = -dir * Math.cos(a) * speed[0];
                        b[1] = face - dir * R;
                        aim[0] = ThreadLocalRandom.current().nextDouble(-0.9, 0.9);
                        fx.sound(at.apply(b[0], b[1]), "block.note_block.bit", 1f, 1.6f);
                        world.spawnParticle(Particle.DUST, at.apply(b[0], b[1]).add(0, 0.3, 0), 4, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(Color.fromRGB(i == 0 ? 0x3FD8FF : 0xFF5AB4), 1f));
                    }
                }
            }
            ball.teleport(at.apply(b[0], b[1]));
            if (Math.abs(b[1]) > L / 2) {                              // a point!
                int winner = b[1] > 0 ? 0 : 1;
                pts[winner]++;
                Location goal = at.apply(b[0], Math.signum(b[1]) * L / 2).add(0, 0.3, 0);
                world.spawnParticle(Particle.FIREWORK, goal, 15, 0.2, 0.2, 0.2, 0.08);
                fx.sound(c, winner == 0 ? "entity.player.levelup" : "block.note_block.bass", 0.8f, winner == 0 ? 1.6f : 0.8f);
                showScore.run();
                serveTo[0] = winner == 0 ? 1 : -1;                     // serve to whoever lost the point
                wait[0] = 30;
                ball.teleport(at.apply(0d, 0d));
                if (pts[winner] >= 5) {
                    over[0] = true;
                    String name = winner == 0 ? player.getName() : far[0] == null ? "The Lobby" : far[0].getName();
                    score.text(text(name + " wins!", winner == 0 ? 0x3FD8FF : 0xFF5AB4).append(Component.newline())
                            .append(text(pts[0] + " : " + pts[1], 0xFFFFFF)));
                    fx.confetti(at.apply(0d, winner == 0 ? -L / 2 + 1 : L / 2 - 1).add(0, 1, 0), 60, 1);
                    fx.sound(c, "ui.toast.challenge_complete", 0.8f, 1.2f);
                    fx.later(80, () -> clear(player.getUniqueId()));
                }
            }
        }, () -> clear(player.getUniqueId()));
    }

    private void bouncyBall(Player player, String modelPath, Material fallback, float scale, double radius, double bounce, String hitSound) {
        Placed p = place(player);
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
        dir.normalize();
        Location start = eye.clone().add(dir.clone().multiply(1.5)).add(0, 0.4, 0);
        if (!start.getBlock().isPassable()) start = eye.clone().add(0, 0.6, 0);
        ItemDisplay ball = keep(p, model(start, modelPath, fallback, scale, 0));
        float size = (float) (radius * 2 + 0.35);
        Interaction hitbox = box(p, start.clone().add(0, -size / 2, 0), size, size, null);
        Vector[] velocity = {dir.clone().multiply(0.22).setY(0.32)};     // pops up out of your hands
        int[] now = {0}, lastHit = {-10};
        Map<UUID, Integer> bumped = new HashMap<>();
        Consumer<Player> hit = hitter -> {
            if (now[0] - lastHit[0] < 4) return;
            lastHit[0] = now[0];
            Vector look = hitter.getEyeLocation().getDirection();
            Vector flat = look.clone().setY(0);
            if (flat.lengthSquared() < 1e-4) flat = ball.getLocation().toVector().subtract(hitter.getLocation().toVector()).setY(0);
            if (flat.lengthSquared() < 1e-4) flat = new Vector(0, 0, 1);
            double power = hitter.isSprinting() ? 0.85 : 0.6;
            velocity[0] = flat.normalize().multiply(power).setY(Math.max(0.32, 0.3 + look.getY() * power));
            fx.sound(ball.getLocation(), hitSound, 1f, hitter.isSprinting() ? 0.9f : 1.2f);
            ball.getWorld().spawnParticle(Particle.CRIT, ball.getLocation(), 8, 0.2, 0.2, 0.2, 0.2);
        };
        clicks.put(hitbox.getUniqueId(), hit);
        org.joml.Quaternionf turn = new org.joml.Quaternionf();
        World world = start.getWorld();
        long[] seen = {0};
        p.task = fx.every(900, t -> {
            now[0] = t;
            // a left-click on the hitbox, in case the attack event didn't reach us
            Interaction.PreviousInteraction attack = hitbox.getLastAttack();
            if (attack != null && attack.getTimestamp() != seen[0]) {
                boolean fresh = seen[0] != 0 || t > 1;
                seen[0] = attack.getTimestamp();
                Player by = plugin.getServer().getPlayer(attack.getPlayer().getUniqueId());
                if (fresh && by != null) hit.accept(by);
            }
            Location pos = ball.getLocation();
            Vector v = velocity[0];
            v.setY(v.getY() - 0.045);
            v.multiply(0.99);
            double x = pos.getX(), y = pos.getY(), z = pos.getZ();
            boolean bonk = false;
            // across: stop at walls (the ball's edge, not its middle) and bounce back
            double nx = x + v.getX();
            if (solid(world, nx + Math.signum(v.getX()) * radius, y, z)) {
                v.setX(-v.getX() * bounce);
                nx = x;
                bonk = Math.abs(v.getX()) > 0.08;
            }
            double nz = z + v.getZ();
            if (solid(world, nx, y, nz + Math.signum(v.getZ()) * radius)) {
                v.setZ(-v.getZ() * bounce);
                nz = z;
                bonk |= Math.abs(v.getZ()) > 0.08;
            }
            // up and down: sit on top of the ground, never in it
            double ny = y + v.getY();
            boolean grounded = false;
            if (v.getY() <= 0) {
                Block under = world.getBlockAt((int) Math.floor(nx), (int) Math.floor(ny - radius), (int) Math.floor(nz));
                if (!under.isPassable()) {
                    double top = under.getBoundingBox().getHeight() > 0 ? under.getBoundingBox().getMaxY() : under.getY() + 1;
                    ny = top + radius;
                    if (v.getY() < -0.14) {
                        v.setY(-v.getY() * bounce);
                        bonk = true;
                    } else {
                        v.setY(0);
                        grounded = true;
                    }
                    v.setX(v.getX() * 0.9);
                    v.setZ(v.getZ() * 0.9);
                }
            } else if (solid(world, nx, ny + radius, nz)) {
                v.setY(-v.getY() * 0.4);
                ny = y;
            }
            if (grounded) {                       // rolling to a stop
                v.setX(v.getX() * 0.94);
                v.setZ(v.getZ() * 0.94);
                if (Math.abs(v.getX()) < 0.004 && Math.abs(v.getZ()) < 0.004) v.setX(0).setZ(0);
            }
            if (bonk) fx.sound(pos, "entity.slime.squish_small", 0.6f, 1.3f);
            Location next = new Location(world, nx, ny, nz);
            ball.teleport(next);
            hitbox.teleport(next.clone().add(0, -size / 2, 0));
            // spin the way it's going
            double moved = Math.hypot(v.getX(), v.getZ());
            if (moved > 0.002) turn.premul(new org.joml.Quaternionf().rotateAxis((float) (moved / radius), (float) v.getZ(), 0f, (float) -v.getX()));
            ball.setInterpolationDelay(0);
            ball.setInterpolationDuration(2);
            ball.setTransformation(new Transformation(new Vector3f(), new org.joml.Quaternionf(turn), new Vector3f(scale), new org.joml.Quaternionf()));
            // running into it kicks it along
            for (Player runner : world.getPlayers()) {
                Location feet = runner.getLocation();
                double dx = nx - feet.getX(), dz = nz - feet.getZ();
                if (dx * dx + dz * dz > (radius + 0.4) * (radius + 0.4) || ny < feet.getY() - radius || ny > feet.getY() + 1.9 + radius) continue;
                Integer last = bumped.get(runner.getUniqueId());
                if (last != null && t - last < 6) continue;
                bumped.put(runner.getUniqueId(), t);
                Vector away = new Vector(dx, 0, dz);
                if (away.lengthSquared() < 1e-4) away = feet.getDirection().setY(0);
                double power = runner.isSprinting() ? 0.65 : 0.4;
                velocity[0] = away.normalize().multiply(power).setY(0.25);
                fx.sound(next, hitSound, 0.7f, 1.4f);
            }
            if (player.isOnline() && player.getWorld() == world && player.getLocation().distanceSquared(next) > 64 * 64) fx.later(0, () -> clear(player.getUniqueId()));
        }, () -> clear(player.getUniqueId()));
    }

    private static boolean solid(World world, double x, double y, double z) {
        return !world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).isPassable();
    }

    private void ghost(Player player) {
        ItemDisplay ghost = fx.rider(player, "cloak/part/ghost");
        ghost.setBrightness(new Display.Brightness(15, 15));
        fx.sound(player.getLocation(), "entity.ghast.ambient", 0.5f, 1.6f);
        fx.every(160, t -> {
            double a = t * 0.12;
            float bob = (float) Math.sin(t * 0.2) * 0.15f;
            ghost.setInterpolationDelay(0);
            ghost.setInterpolationDuration(2);
            ghost.setTransformationMatrix(new Matrix4f().translate((float) Math.cos(a) * 1.3f, -0.3f + bob, (float) Math.sin(a) * 1.3f)
                    .rotateY((float) -(a + Math.PI / 2)).scale(0.7f));
            if (t % 40 == 20) {
                TextDisplay boo = label(player.getLocation().add(Math.cos(a) * 1.3, 2.4, Math.sin(a) * 1.3), text("BOO!", 0xE8EEFF), 20);
                boo.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                fx.sound(player.getLocation(), "entity.ghast.scream", 0.3f, 1.8f);
            }
            if (t % 3 == 0) fx.show(player, Particle.DUST, player.getLocation().add(Math.cos(a) * 1.3, 1.4 + bob, Math.sin(a) * 1.3), 1, 0.1, 0.1, 0.1, 0,
                    new Particle.DustOptions(Color.fromRGB(0xE8EEFF), 0.8f));
        }, ghost::remove);
    }

    /**
     * A real rainbow: a big arc of six soft, glowing colour bands rises up in front of the player between two little
     * clouds, to a run of rising magical chimes, with twinkles drifting off it; after a while it sinks away again.
     */
    /**
     * Diamond Shower: a burst of diamonds, then seven seconds of them raining down all round the player out of the
     * sky, with sparkles, glints and rising chimes, and a last big burst at the end. (They're fake: nobody can pick
     * them up.)
     */
    private void diamondShower(Player player) {
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        Particle.DustOptions cyan = new Particle.DustOptions(Color.fromRGB(0x5AE6E0), 0.9f);
        fx.sound(player.getLocation(), "entity.player.levelup", 0.8f, 1.6f);
        fx.burstItems(player.getLocation().add(0, 2.2, 0), diamond, 10, 0.25, 40);
        int[] scale = {0, 2, 4, 7, 9, 12, 14, 16};
        fx.every(150, t -> {
            if (!player.isOnline()) return;
            Location feet = player.getLocation();
            World world = feet.getWorld();
            ThreadLocalRandom r = ThreadLocalRandom.current();
            if (t < 140) {
                for (int k = 0; k < 1; k++) {                                                   // a diamond falling somewhere round them, out of the sky
                    double a = r.nextDouble(Math.PI * 2), d = 0.6 + r.nextDouble(2.2);
                    Location sky = feet.clone().add(Math.cos(a) * d, 3.6 + r.nextDouble(0.8), Math.sin(a) * d);
                    if (!sky.getBlock().isPassable()) continue;
                    org.bukkit.entity.Item drop = world.dropItem(sky, diamond.clone(), i -> {
                        i.setCanPlayerPickup(false);
                        i.setCanMobPickup(false);
                        i.setPickupDelay(Integer.MAX_VALUE);
                        i.setVelocity(new Vector(0, -0.15, 0));
                    });
                    fx.tagged(drop, "item");
                    fx.later(34, () -> {
                        if (!drop.isValid()) return;
                        world.spawnParticle(Particle.DUST, drop.getLocation().add(0, 0.15, 0), 3, 0.12, 0.05, 0.12, 0, cyan);
                        drop.remove();
                    });
                }
                if (t % 3 == 0) {                                      // sparkles drifting down with them
                    Location at = feet.clone().add(r.nextDouble(-2.4, 2.4), 1 + r.nextDouble(2.6), r.nextDouble(-2.4, 2.4));
                    fx.driftFx(at, t % 2 == 0 ? "gadget/fx_sparkle" : "gadget/fx_twinkle", 0.22f, new Vector(0, -0.02, 0), 14);
                }
                world.spawnParticle(Particle.DUST, feet.clone().add(0, 3.4, 0), 4, 1.6, 0.3, 1.6, 0, cyan);
                if (t % 4 == 0) world.spawnParticle(Particle.END_ROD, feet.clone().add(0, 3.2, 0), 1, 1.8, 0.4, 1.8, 0.01);
                if (t % 10 == 0) fx.sound(feet, "block.amethyst_block.chime", 0.7f,
                        (float) Math.pow(2, (scale[(t / 10) % scale.length] - 8) / 12.0));
            }
            if (t == 140) {                                            // and a big finish
                fx.burstItems(feet.clone().add(0, 2.2, 0), diamond, 14, 0.32, 40);
                world.spawnParticle(Particle.FIREWORK, feet.clone().add(0, 2, 0), 30, 0.4, 0.4, 0.4, 0.15);
                fx.sound(feet, "entity.player.levelup", 0.8f, 2f);
                fx.sound(feet, "block.amethyst_block.resonate", 1f, 1.4f);
            }
        }, null);
    }

    /**
     * A big rainbow standing on the ground with the player right in the middle of it (it stays where it was made): a
     * soft cloud at each end, rising chimes as it appears, and twinkles drifting off it for about ten seconds.
     */
    private void rainbow(Player player) {
        World world = player.getWorld();
        Location feet = player.getLocation().clone();                  // right where the player stands
        float yaw = feet.getYaw();
        Vector look = new Location(world, 0, 0, 0, yaw, 0).getDirection();
        Vector right = new Vector(-look.getZ(), 0, look.getX());
        Location base = feet.clone();
        base.setYaw(yaw);
        base.setPitch(0);
        float full = 2.4f;                                             // about 7 blocks across, the ends 2.3 blocks either side
        ItemDisplay arc = world.spawn(base, ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled(Material.PAPER, "gadget/rainbow_arc"));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.01f), new AxisAngle4f()));
        });
        fx.tagged(arc, "rainbow");
        float cloudSize = 2.2f;                                        // the sprite's cloud sits in its lower part
        ItemDisplay[] clouds = new ItemDisplay[2];
        for (int i = 0; i < 2; i++) {
            int side = i == 0 ? -1 : 1;
            Location at = base.clone().add(right.clone().multiply(side * 2.35)).add(0, cloudSize / 2 - 0.05, 0);
            clouds[i] = world.spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(fx.modelled(Material.WHITE_WOOL, "gadget/rainbow_cloud"));
                d.setBillboard(Display.Billboard.VERTICAL);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
                d.setTransformation(new Transformation(new Vector3f(0, -cloudSize / 2, 0), new AxisAngle4f(), new Vector3f(0.01f), new AxisAngle4f()));
            });
            fx.tagged(clouds[i], "rainbow");
        }
        // grow (and later shrink) in one smooth step each; the clouds keep their bottoms on the ground as they do
        java.util.function.Consumer<Float> size = k -> {
            float sz = Math.max(0.01f, full * k);
            arc.setInterpolationDelay(0);
            arc.setInterpolationDuration(14);
            arc.setTransformation(new Transformation(new Vector3f(0, sz * 0.5f, 0), new AxisAngle4f(), new Vector3f(sz), new AxisAngle4f()));
            float c = Math.max(0.01f, cloudSize * k);
            for (ItemDisplay cloud : clouds) {
                cloud.setInterpolationDelay(0);
                cloud.setInterpolationDuration(14);
                cloud.setTransformation(new Transformation(new Vector3f(0, (c - cloudSize) / 2, 0), new AxisAngle4f(), new Vector3f(c), new AxisAngle4f()));
            }
        };
        fx.later(2, () -> size.accept(1f));
        int[] scale = {0, 2, 4, 5, 7, 9, 11, 12};                        // a major scale, rising
        fx.every(200, t -> {
            if (!arc.isValid()) return;
            if (t < 24 && t % 3 == 0) {                                // the chimes as it rises
                int note = scale[(t / 3) % scale.length];
                fx.sound(base, "block.note_block.chime", 0.8f, (float) Math.pow(2, (note - 6) / 12.0));
            }
            if (t == 24) {
                fx.sound(base, "block.amethyst_block.resonate", 1f, 1.5f);
                fx.sound(base, "entity.player.levelup", 0.4f, 2f);
            }
            if (t > 10 && t < 180 && t % 3 == 0) {                     // twinkles drifting off the arc
                double a = ThreadLocalRandom.current().nextDouble(Math.PI);
                double r = 2.3 + ThreadLocalRandom.current().nextDouble(-0.9, 0.9);
                Location on = base.clone().add(right.clone().multiply(Math.cos(a) * r)).add(0, Math.sin(a) * r, 0);
                fx.driftFx(on, "gadget/fx_twinkle", 0.25f, new Vector(0, 0.01, 0), 14);
            }
            if (t == 184) {
                size.accept(0f);
                fx.sound(base, "block.amethyst_block.chime", 0.8f, 0.8f);
            }
        }, () -> {
            arc.remove();
            for (ItemDisplay cloud : clouds) cloud.remove();
        });
    }

    private static float easeOut(float k) {
        float c = 1.4f;                                                // overshoots a touch, then settles
        float x = k - 1;
        return 1 + (c + 1) * x * x * x + c * x * x;
    }

    /**
     * The CATapult: a little wooden catapult rolls up in front of the player and flings three cats, one after another:
     * the arm winds back, whips over with a thunk, and a cat sails off in a big arc, lands on its feet, sits for a moment,
     * then poofs away. (The cats are fine.) It needs a bit of open, solid ground in front of the player.
     */
    private void catapult(Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().setY(0);
        if (look.lengthSquared() < 1e-4) look = new Vector(0, 0, 1);
        look.normalize();
        Vector forward = look.clone();
        Location base = player.getLocation().add(look.clone().multiply(1.8));
        base = base.getBlock().getLocation().add(0.5, 0, 0.5);
        Block floor = base.clone().add(0, -1, 0).getBlock();
        if (!floor.getType().isSolid() || !base.getBlock().isPassable() || !base.clone().add(0, 1, 0).getBlock().isPassable()) {
            plugin.messages().send(player, "collectibles.gadget.catapult-room");
            fx.sound(player.getLocation(), "entity.villager.no", 0.8f, 1.2f);
            return;
        }
        base.setDirection(look);
        World world = base.getWorld();
        Location spot = base;
        float yaw = spot.getYaw();
        final float S = 1.4f;                                          // a good bit bigger than a block
        ItemDisplay frame = world.spawn(spot, ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled(Material.OAK_PLANKS, "gadget/catapult_base"));
            d.setPersistent(false);
            d.setRotation(yaw, 0);
            d.setTransformation(new Transformation(new Vector3f(0, 0.5f * S, 0), new AxisAngle4f(), new Vector3f(0.01f), new AxisAngle4f()));
        });
        // the arm's pivot: the winch, model (8, 5, 11) on the base, just behind the middle (the base is turned half round)
        Location pivot = spot.clone().add(0, 5 / 16.0 * S, 0).add(look.clone().multiply(-3 / 16.0 * S));
        ItemDisplay arm = world.spawn(pivot, ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled(Material.OAK_PLANKS, "gadget/catapult_arm"));
            d.setPersistent(false);
            d.setRotation(yaw, 0);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.01f), new AxisAngle4f()));
        });
        fx.tagged(frame, "catapult");
        fx.tagged(arm, "catapult");
        fx.sound(spot, "block.wood.place", 1f, 0.8f);
        fx.sound(spot, "entity.cat.ambient", 0.8f, 1.2f);
        Cat.Type[] types = {Cat.Type.RED, Cat.Type.BLACK, Cat.Type.TABBY, Cat.Type.SIAMESE, Cat.Type.CALICO, Cat.Type.BRITISH_SHORTHAIR};
        fx.every(150, t -> {
            if (!frame.isValid()) return;
            if (t <= 6) {                                              // rolls in (grows in)
                float k = t / 6f;
                frame.setInterpolationDelay(0);
                frame.setInterpolationDuration(2);
                frame.setTransformation(new Transformation(new Vector3f(0, 0.5f * S, 0), new AxisAngle4f(), new Vector3f(Math.max(0.01f, k * S)), new AxisAngle4f()));
            }
            // each throw takes 40 ticks: wind back slowly (0-20), whip over (20-25), rest (25-40)
            int phase = (t - 10) % 40, shot = (t - 10) / 40;
            float angle;
            if (t < 10 || shot >= 3) angle = 0;
            else if (phase < 20) angle = -0.25f * phase / 20f;               // creaks back a little
            else if (phase < 25) angle = -0.25f + 2.0f * (phase - 20) / 5f;  // and whips up and over
            else angle = 1.75f * (1 - (phase - 25) / 15f);                   // and settles back down
            float size = t < 6 ? Math.max(0.01f, t / 6f) : t > 140 ? Math.max(0.01f, (150 - t) / 10f) : 1f;
            arm.setInterpolationDelay(0);
            arm.setInterpolationDuration(1);
            arm.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(angle, 1, 0, 0), new Vector3f(size * S), new AxisAngle4f()));
            if (t > 140) {
                frame.setInterpolationDelay(0);
                frame.setInterpolationDuration(1);
                frame.setTransformation(new Transformation(new Vector3f(0, 0.5f * S, 0), new AxisAngle4f(), new Vector3f(size * S), new AxisAngle4f()));
            }
            if (t >= 10 && shot < 3) {
                if (phase == 2) fx.sound(spot, "block.wooden_trapdoor.close", 0.6f, 0.6f);     // creak
                if (phase == 22) {                                     // the throw
                    fx.sound(spot, "block.wood.hit", 1f, 0.7f);
                    fx.sound(spot, "entity.cat.hiss", 0.6f, 1.5f);
                    Location release = pivot.clone().add(0, 1.0 * S, 0);
                    Cat.Type type = types[(shot + ThreadLocalRandom.current().nextInt(types.length)) % types.length];
                    Cat cat = world.spawn(release, Cat.class, c -> {
                        c.setInvulnerable(true);
                        c.setCatType(type);
                        c.setAware(false);
                        c.setPersistent(false);
                    });
                    fx.tagged(cat, "thrown");
                    Vector toss = forward.clone().multiply(0.75 + ThreadLocalRandom.current().nextDouble() * 0.2)
                            .add(new Vector(fx.rand(0.12), 0.65, fx.rand(0.12)));
                    cat.setVelocity(toss);
                    fx.every(80, k -> {
                        if (!cat.isValid()) return;
                        if (k % 3 == 0 && !cat.isOnGround()) fx.driftFx(cat.getLocation().add(0, 0.3, 0), "gadget/fx_sparkle", 0.18f, new Vector(), 10);
                        if (k > 4 && cat.isOnGround() && !cat.isSitting()) {
                            cat.setSitting(true);
                            fx.sound(cat.getLocation(), "entity.cat.ambient", 0.8f, 1.1f + ThreadLocalRandom.current().nextFloat() * 0.3f);
                            world.spawnParticle(Particle.HEART, cat.getLocation().add(0, 0.8, 0), 2, 0.2, 0.1, 0.2, 0);
                        }
                    }, () -> pop(cat, "entity.cat.purreow"));
                }
            }
        }, () -> {
            frame.remove();
            arm.remove();
        });
    }

    private void pumpkinRain(Player player) {
        fx.sound(player.getLocation(), "entity.witch.celebrate", 1f, 1f);
        fx.every(120, t -> {
            if (t % 4 != 0 || !player.isOnline()) return;
            Location sky = player.getLocation().add(fx.rand(5), 7, fx.rand(5));
            ItemDisplay p = sky.getWorld().spawn(sky, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(ThreadLocalRandom.current().nextInt(3) == 0 ? Material.JACK_O_LANTERN : Material.CARVED_PUMPKIN));
                d.setTeleportDuration(2);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.6f), new AxisAngle4f()));
            });
            fx.tagged(p, "pumpkin");
            fx.every(40, s -> {
                Location now = p.getLocation();
                Location next = now.clone().add(0, -0.35, 0);
                if (!next.getBlock().isPassable()) {
                    now.getWorld().spawnParticle(Particle.BLOCK, now, 20, 0.2, 0.1, 0.2, 0, Material.PUMPKIN.createBlockData());
                    fx.sound(now, "block.wood.break", 0.6f, 0.8f);
                    p.remove();
                    return;
                }
                p.teleport(next);
            }, p::remove);
        }, null);
    }

    private void letItSnow(Player player) {
        ItemDisplay cloud = fx.rider(player, "cloak/part/cloud");
        cloud.setTransformation(new Transformation(new Vector3f(0, 1.3f, 0), new AxisAngle4f(), new Vector3f(2.2f, 1.2f, 2.2f), new AxisAngle4f()));
        List<Block> snowed = new ArrayList<>();
        BlockData snow = Material.SNOW.createBlockData();
        fx.sound(player.getLocation(), "block.snow.place", 1f, 0.8f);
        fx.every(200, t -> {
            if (!player.isOnline()) return;
            Location at = player.getLocation();
            at.getWorld().spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 3.0, 0), 4, 1.0, 0.1, 1.0, 0.01);
            if (t % 5 == 0) {
                Block ground = at.clone().add(fx.rand(2.5), 0, fx.rand(2.5)).getBlock();
                for (int k = 0; k < 3 && !ground.getRelative(0, -1, 0).getType().isSolid(); k++) ground = ground.getRelative(0, -1, 0);
                if (ground.getType().isAir() && ground.getRelative(0, -1, 0).getType().isOccluding()) {
                    snowed.add(ground);
                    for (Player viewer : at.getWorld().getPlayers()) viewer.sendBlockChange(ground.getLocation(), snow);
                }
            }
        }, () -> {
            cloud.remove();
            for (Player viewer : player.getWorld().getPlayers()) for (Block b : snowed) viewer.sendBlockChange(b.getLocation(), b.getBlockData());
        });
    }

    private void iceSkate(Player player) {
        BlockData ice = Material.PACKED_ICE.createBlockData();
        fx.sound(player.getLocation(), "block.glass.place", 1f, 1.6f);
        fx.every(200, t -> {
            if (!player.isOnline() || !player.isOnGround()) return;
            Block under = player.getLocation().add(0, -0.2, 0).getBlock();
            if (!under.getType().isOccluding()) return;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                Block b = under.getRelative(dx, 0, dz);
                if (!b.getType().isOccluding()) continue;
                for (Player viewer : b.getWorld().getPlayers()) viewer.sendBlockChange(b.getLocation(), ice);
                fx.later(40, () -> {
                    for (Player viewer : b.getWorld().getPlayers()) viewer.sendBlockChange(b.getLocation(), b.getBlockData());
                });
            }
            if (t % 4 == 0) player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 0.1, 0), 2, 0.2, 0, 0.2, 0.01);
            if (t % 12 == 0) fx.sound(player.getLocation(), "block.glass.step", 0.4f, 1.6f);
        }, null);
    }

    private void frisbee(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().setY(Math.max(-0.1, Math.min(0.2, eye.getDirection().getY()))).normalize();
        ItemDisplay disc = model(eye, "gadget/frisbee_3d", Material.RED_CARPET, 0.8f, 0);
        fx.tagged(disc, "frisbee");
        fx.sound(eye, "entity.snowball.throw", 1f, 0.6f);
        Vector right = new Vector(-dir.getZ(), 0, dir.getX());
        fx.every(60, t -> {
            double out = t < 30 ? t : 60 - t;            // out and back, curving
            Location at = eye.clone().add(dir.clone().multiply(out * 0.45)).add(right.clone().multiply(Math.sin(t / 60.0 * Math.PI) * 2.5))
                    .add(0, Math.sin(t / 60.0 * Math.PI) * 0.8, 0);
            if (!at.getBlock().isPassable()) return;
            disc.teleport(at);
            disc.setInterpolationDelay(0);
            disc.setInterpolationDuration(2);
            disc.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(t * 0.8f, 0, 1, 0), new Vector3f(0.8f), new AxisAngle4f()));
            if (t % 3 == 0) at.getWorld().spawnParticle(Particle.CLOUD, at, 1, 0, 0, 0, 0);
            for (Player catcher : at.getWorld().getPlayers()) {
                if (catcher != player && t > 8 && catcher.getEyeLocation().distanceSquared(at) < 1.2) {
                    plugin.messages().send(player, "collectibles.gadget.frisbee-caught",
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("player", catcher.getName()));
                    fx.sound(at, "entity.item.pickup", 1f, 1f);
                    disc.remove();
                    return;
                }
            }
        }, disc::remove);
    }

    // ---------------------------------------------------------------- placed things

    /**
     * Tetherball: a pole with a round ball on a slack rope. Hit it (left or right click, or run into it) and it swings
     * out and round; the rope winds up round the outside of the pole as it goes (and unwinds when it's hit back the
     * other way), sagging when it's slow and pulling tight when it's flying.
     */
    private void tetherball(Player player) {
        Placed p = place(player);
        Location base = fx.ground(player);
        World world = base.getWorld();
        keep(p, model(base.clone(), "gadget/tetherball_pole", Material.IRON_BARS, 1.4f, 0.7f));
        final double TOP = 2.7, ROPE = 1.9, POLE = 0.1, PITCH = 0.09, BALL = 0.3;
        final double MAX_TURNS = (ROPE - 0.35) / (Math.PI * 2 * POLE * 1.05);
        Location top = base.clone().add(0, TOP, 0);
        top.setYaw(0);                                                 // the rope's pieces are placed in plain world directions
        top.setPitch(0);
        ItemDisplay ball = keep(p, model(top.clone().add(0, -ROPE, 0), "gadget/tetherball_3d", Material.YELLOW_WOOL, 0.75f, 0));
        ball.setTeleportDuration(1);
        int pieces = 40;                                               // the rope, in short pieces, so it can bend and wind
        BlockDisplay[] rope = new BlockDisplay[pieces];
        for (int i = 0; i < pieces; i++) {
            rope[i] = keep(p, world.spawn(top, BlockDisplay.class, d -> {
                d.setBlock(Material.WHITE_WOOL.createBlockData());
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0), new AxisAngle4f()));
            }));
        }
        double[] st = {Math.toRadians(player.getLocation().getYaw() + 90), 0, 0};   // angle round the pole, speed, how far wound (radians)
        Map<UUID, Integer> lastHit = new HashMap<>();
        int[] tick = {0};
        java.util.function.BiConsumer<Player, Double> hitBall = (hitter, power) -> {
            Integer last = lastHit.get(hitter.getUniqueId());
            if (last != null && tick[0] - last < 6) return;
            lastHit.put(hitter.getUniqueId(), tick[0]);
            Location at = ball.getLocation();
            Vector toBall = at.toVector().subtract(base.toVector()).setY(0);
            Vector push = hitter.getLocation().getDirection().setY(0);
            double cross = toBall.getX() * push.getZ() - toBall.getZ() * push.getX();
            st[1] = Math.max(-0.32, Math.min(0.32, st[1] + (cross >= 0 ? 1 : -1) * power));
            fx.sound(at, "entity.player.attack.strong", 0.9f, 1.3f);
            world.spawnParticle(Particle.CRIT, at, 6, 0.15, 0.15, 0.15, 0.1);
        };
        Interaction hit = box(p, top.clone().add(0, -ROPE - 0.4, 0), 0.8f, 0.8f, null);
        clicks.put(hit.getUniqueId(), hitter -> hitBall.accept(hitter, hitter.isSprinting() ? 0.16 : 0.12));
        fx.sound(base, "block.chain.place", 1f, 1f);
        p.task = fx.every(1200, t -> {
            tick[0] = t;
            st[0] += st[1];
            st[2] += st[1];
            st[1] *= 0.99;
            double turns = Math.min(MAX_TURNS, Math.abs(st[2]) / (Math.PI * 2));
            // what's wound round the pole is used up: a little helix of rope going down from the top
            double wound = Math.min(ROPE - 0.35, turns * Math.PI * 2 * POLE * 1.05);
            double free = ROPE - wound;
            double taut = Math.min(1, Math.abs(st[1]) / 0.18);
            double fly = taut * Math.toRadians(72);                    // swings out the faster it goes
            double drop = turns * PITCH;                               // the wound part steps down the pole
            double out = Math.max(POLE + BALL + 0.05, POLE + free * Math.sin(fly));
            double y = TOP - drop - free * Math.cos(fly);
            Location at = base.clone().add(Math.cos(st[0]) * out, y, Math.sin(st[0]) * out);
            ball.teleport(at);
            hit.teleport(at.clone().add(0, -0.4, 0));

            // the rope's points: the helix round the pole (just outside it), then the slack bit down to the ball
            List<Vector> pts = new ArrayList<>();
            double woundAngle = Math.min(Math.abs(st[2]), (ROPE - 0.35) / (POLE * 1.05)) * Math.signum(st[2]);
            int helix = (int) Math.min(pieces - 10, Math.ceil(Math.abs(woundAngle) / (Math.PI / 5)));
            for (int k = 0; k <= helix; k++) {
                double f = helix == 0 ? 1 : k / (double) helix;
                double ang = st[0] - woundAngle * (1 - f);
                pts.add(new Vector(Math.cos(ang) * POLE * 1.05, -drop * f, Math.sin(ang) * POLE * 1.05));
            }
            Vector from = pts.get(pts.size() - 1);
            Vector to = at.toVector().subtract(top.toVector()).add(new Vector(0, 0.05, 0));
            Vector toward = to.clone().subtract(from);
            double towardLen = Math.max(0.01, toward.length());
            Vector mid = from.clone().add(toward.clone().multiply(0.5));
            mid.setY(mid.getY() - (0.06 + (1 - taut) * 0.22) * towardLen);   // slack: sags when it's slow
            int slack = pieces - 1 - helix;
            for (int k = 1; k <= slack; k++) {
                double f = k / (double) slack, g = 1 - f;
                pts.add(from.clone().multiply(g * g).add(mid.clone().multiply(2 * f * g)).add(to.clone().multiply(f * f)));
            }
            for (int i = 0; i < pieces; i++) {
                BlockDisplay piece = rope[i];
                piece.setInterpolationDelay(0);
                piece.setInterpolationDuration(1);
                if (i + 1 >= pts.size()) {
                    piece.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0), new AxisAngle4f()));
                    continue;
                }
                Vector a0 = pts.get(i), d = pts.get(i + 1).clone().subtract(a0);
                float len = (float) d.length() + 0.01f;
                Vector n = d.clone().normalize();
                piece.setTransformationMatrix(new Matrix4f().translate((float) a0.getX(), (float) a0.getY(), (float) a0.getZ())
                        .rotate(new org.joml.Quaternionf().rotationTo(0, 1, 0, (float) n.getX(), (float) n.getY(), (float) n.getZ()))
                        .translate(-0.025f, 0, -0.025f).scale(0.05f, len, 0.05f));
            }
            // running into it counts as a hit too
            for (Player who : world.getPlayers()) {
                if (fx.parkour(who) || who.getLocation().add(0, 1, 0).distanceSquared(at) > 0.6) continue;
                hitBall.accept(who, 0.1);
            }
        }, () -> clear(player.getUniqueId()));
    }

    /**
     * Rock paper shears against the nearest player (or the lobby): in front of each player a fist pumps three times with
     * "Rock... Paper... Shears..." over it, then turns into their pick, and the result shows in chat.
     */
    /**
     * Rock Paper Shears never drags anyone in: the player's fist floats in front of them with an open invite. Anyone
     * who wants to play clicks it and the two play; the player can click it themselves to play the Lobby straight
     * away, and if nobody joins in a few seconds they play the Lobby anyway.
     */
    private void rockPaperShears(Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().setY(0);
        if (look.lengthSquared() < 1e-4) look = new Vector(0, 0, 1);
        Location at = eye.clone().add(look.normalize().multiply(1.6)).add(0, -0.3, 0);
        if (!at.getBlock().isPassable()) at = eye.clone().add(0, 0.7, 0);
        final Location spot = at;
        int wait = 140;
        ItemDisplay fist = sprite(at, "gadget/rps_rock", 0.6f);
        TextDisplay invite = label(at.clone().add(0, 0.55, 0), inviteText(player, wait / 20), 0);
        Interaction box = at.getWorld().spawn(at.clone().add(0, -0.4, 0), Interaction.class, i -> {
            i.setInteractionWidth(0.9f);
            i.setInteractionHeight(0.9f);
            i.setResponsive(true);
            i.setPersistent(false);
        });
        fx.tagged(box, "rps");
        boolean[] started = {false};
        Runnable tidy = () -> {
            clicks.remove(box.getUniqueId());
            box.remove();
            fist.remove();
            invite.remove();
        };
        clicks.put(box.getUniqueId(), clicker -> {
            if (started[0] || !player.isOnline()) return;
            started[0] = true;
            tidy.run();
            if (clicker.equals(player)) {
                playRps(player, null);
            } else {
                fx.sound(spot, "entity.experience_orb.pickup", 0.8f, 1.4f);
                playRps(player, clicker);
            }
        });
        fx.sound(at, "block.note_block.bell", 0.8f, 1.2f);
        fx.every(wait, t -> {
            if (started[0]) return;
            if (!player.isOnline()) {
                started[0] = true;
                tidy.run();
                return;
            }
            float bob = (float) Math.sin(t * 0.25) * 0.05f;
            scale(fist, 0.6f, 0, 0, bob);
            if (t % 20 == 0) invite.text(inviteText(player, (wait - t) / 20));
            if (t == wait - 1) {                                       // nobody joined: play the Lobby
                started[0] = true;
                tidy.run();
                playRps(player, null);
            }
        }, () -> {
            if (!started[0]) {
                started[0] = true;
                tidy.run();
            }
        });
    }

    private static Component inviteText(Player player, int seconds) {
        return text("Rock Paper Shears?", 0xFFE14D)
                .append(Component.newline())
                .append(text("Click to play " + player.getName() + "!", 0xFFFFFF))
                .append(Component.newline())
                .append(text("(vs the Lobby in " + Math.max(0, seconds) + ")", 0xA9A3C2));
    }

    private void playRps(Player player, Player rival) {
        String[] picks = {"Rock", "Paper", "Shears"};
        String[] models = {"gadget/rps_rock", "gadget/rps_paper", "gadget/rps_shears"};
        int mine = ThreadLocalRandom.current().nextInt(3), theirs = ThreadLocalRandom.current().nextInt(3);
        List<Player> playing = new ArrayList<>(List.of(player));
        if (rival != null) playing.add(rival);
        List<ItemDisplay> hands = new ArrayList<>();
        List<Location> spots = new ArrayList<>();
        for (Player who : playing) {
            Location eye = who.getEyeLocation();
            Vector look = eye.getDirection().setY(0);
            if (look.lengthSquared() < 1e-4) look = new Vector(0, 0, 1);
            Location at = eye.clone().add(look.normalize().multiply(1.6)).add(0, -0.3, 0);
            if (!at.getBlock().isPassable()) at = eye.clone().add(0, 0.7, 0);
            spots.add(at);
            hands.add(sprite(at, "gadget/rps_rock", 0.6f));
        }
        String[] count = {"Rock...", "Paper...", "Shears..."};
        TextDisplay[] words = new TextDisplay[playing.size()];
        fx.every(110, t -> {
            if (t < 36) {                                                   // three pumps of the fist, a word on each
                float bob = (float) Math.abs(Math.sin(t * Math.PI / 12)) * 0.18f;
                for (ItemDisplay hand : hands) scale(hand, 0.6f, 0, 0, bob);
                if (t % 12 == 0) {
                    int n = t / 12;
                    for (int i = 0; i < playing.size(); i++) {
                        if (words[i] != null) words[i].remove();
                        words[i] = label(spots.get(i).clone().add(0, 0.6, 0), text(count[n], 0xFFFFFF), 0);
                    }
                    fx.sound(player.getLocation(), "block.note_block.hat", 1f, 1f + n * 0.2f);
                }
            } else if (t == 36) {                                           // the reveal
                int result = (mine - theirs + 3) % 3;                       // 0 draw, 1 I win, 2 they win
                for (int i = 0; i < playing.size(); i++) {
                    int pick = i == 0 ? mine : theirs;
                    hands.get(i).setItemStack(fx.modelled(Material.PAPER, models[pick]));
                    scale(hands.get(i), 0.7f, 0, 0, 0);
                    if (words[i] != null) words[i].remove();
                    boolean won = result == (i == 0 ? 1 : 2);
                    String shown = rival == null ? picks[mine] + " vs " + picks[theirs] : picks[pick];   // playing the Lobby: show its pick too
                    words[i] = label(spots.get(i).clone().add(0, 0.65, 0), text(shown + (result == 0 ? " - Draw!" : won ? " - Win!" : rival == null ? " - Lose!" : ""),
                            result == 0 ? 0xFFE14D : won ? 0x7FE39A : 0xFFFFFF), 0);
                    if (won) fx.confetti(spots.get(i).clone().add(0, 0.3, 0), 25, 0.4);
                }
                fx.sound(player.getLocation(), result == 1 ? "entity.player.levelup" : result == 0 ? "block.note_block.pling" : "entity.villager.no", 0.8f, 1.4f);
            } else if (t > 100) {
                for (ItemDisplay hand : hands) scale(hand, 0.7f * (110 - t) / 10f, 0, 0, 0);
            }
        }, () -> {
            hands.forEach(Entity::remove);
            for (TextDisplay word : words) if (word != null) word.remove();
        });
    }

    private void pinata(Player player) {
        Placed p = place(player);
        Location base = fx.ground(player).add(0, 1.4, 0);
        ItemDisplay pinata = keep(p, model(base.clone(), "gadget/pinata_3d", Material.PINK_WOOL, 1.2f, 0));
        int[] hits = {0};
        int needed = 8;
        Interaction box = box(p, base.clone().add(0, -0.6, 0), 1.0f, 1.0f, null);
        clicks.put(box.getUniqueId(), hitter -> {
            hits[0]++;
            pinata.setInterpolationDelay(0);
            pinata.setInterpolationDuration(3);
            pinata.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) Math.toRadians(hits[0] % 2 == 0 ? 20 : -20), 0, 0, 1),
                    new Vector3f(1.2f), new AxisAngle4f()));
            fx.sound(base, "entity.player.attack.knockback", 0.8f, 1.2f);
            base.getWorld().spawnParticle(Particle.DUST, base, 10, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(fx.party(), 1f));
            if (hits[0] >= needed) {
                fx.burstItems(base, new ItemStack(Material.COOKIE), 6, 0.3, 50);
                fx.burstItems(base, new ItemStack(Material.SWEET_BERRIES), 6, 0.3, 50);
                fx.burstItems(base, new ItemStack(Material.HONEY_BOTTLE), 3, 0.3, 50);
                fx.confetti(base, 80, 1);
                base.getWorld().spawnParticle(Particle.FIREWORK, base, 40, 0.3, 0.3, 0.3, 0.2);
                fx.sound(base, "entity.firework_rocket.blast", 1f, 1f);
                fx.sound(base, "entity.player.levelup", 0.8f, 1.6f);
                plugin.messages().send(hitter, "collectibles.gadget.pinata-burst");
                clear(player.getUniqueId());
            }
        });
        p.task = fx.every(600, t -> {
            if (t % 2 != 0) return;
            float sway = (float) Math.sin(t * 0.08) * 0.15f;
            if (hits[0] == 0 || t % 6 == 0) {
                pinata.setInterpolationDelay(0);
                pinata.setInterpolationDuration(4);
                pinata.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(sway, 0, 0, 1), new Vector3f(1.2f), new AxisAngle4f()));
            }
        }, () -> clear(player.getUniqueId()));
    }

    private void divingBoard(Player player) {
        Placed p = place(player);
        Location base = fx.ground(player);
        float yaw = Math.round(player.getLocation().getYaw() / 90f) * 90f;
        Location placedAt = base.clone();
        placedAt.setYaw(yaw);
        keep(p, placedAt.getWorld().spawn(placedAt.clone().add(0, 0.5, 0), ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled(Material.BIRCH_SLAB, "gadget/diving_board_3d"));
            d.setRotation(yaw - 90, 0);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.5f), new AxisAngle4f()));
        }));
        Vector out = placedAt.getDirection().setY(0).normalize();
        Location tip = base.clone().add(out.clone().multiply(1.4)).add(0, 0.45, 0);
        fx.sound(base, "block.wood.place", 1f, 1f);
        p.task = fx.every(400, t -> {
            for (Player jumper : base.getWorld().getPlayers()) {
                if (fx.parkour(jumper)) continue;
                Location feet = jumper.getLocation();
                if (feet.distanceSquared(tip) < 0.8 && jumper.getVelocity().getY() <= 0.05) {
                    jumper.setVelocity(out.clone().multiply(0.7).setY(1.15));
                    fx.sound(tip, "block.wood.step", 1f, 0.6f);
                    fx.sound(tip, "entity.player.splash", 0.8f, 1.4f);
                    tip.getWorld().spawnParticle(Particle.SPLASH, tip, 30, 0.4, 0.1, 0.4, 0.2);
                }
            }
        }, () -> clear(player.getUniqueId()));
    }

    private void swing(Player player) {
        Placed p = place(player);
        Location base = fx.ground(player);
        float yaw = Math.round(player.getLocation().getYaw() / 90f) * 90f;
        Location frameAt = base.clone().add(0, 0.5, 0);
        keep(p, frameAt.getWorld().spawn(frameAt, ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled(Material.OAK_FENCE, "gadget/swing_frame"));
            d.setRotation(yaw, 0);
            d.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(), new Vector3f(2f), new AxisAngle4f()));
        }));
        Location pivot = base.clone().add(0, 2.4, 0);
        ItemDisplay seat = keep(p, pivot.getWorld().spawn(pivot, ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled(Material.RED_CARPET, "gadget/swing_seat"));
            d.setRotation(yaw, 0);
        }));
        Interaction sit = box(p, base.clone().add(0, 0.4, 0), 0.9f, 0.6f, rider -> {
            if (fx.parkour(rider) || !seat.getPassengers().isEmpty()) return;
            seat.addPassenger(rider);
            plugin.messages().send(rider, "collectibles.gadget.swing-sit");
        });
        fx.sound(base, "block.wood.place", 1f, 0.8f);
        Vector forward = new Location(base.getWorld(), 0, 0, 0, yaw, 0).getDirection();
        p.task = fx.every(600, t -> {
            double swingAngle = Math.sin(t * 0.09) * (seat.getPassengers().isEmpty() ? 0.12 : 0.85);
            // the seat hangs 1.9 below the bar and swings forward and back
            Location at = pivot.clone().add(forward.clone().multiply(Math.sin(swingAngle) * 1.9)).add(0, -Math.cos(swingAngle) * 1.9, 0);
            seat.teleport(at);
            seat.setTeleportDuration(2);
            seat.setInterpolationDelay(0);
            seat.setInterpolationDuration(2);
            seat.setTransformation(new Transformation(new Vector3f(0, 0.95f, 0), new AxisAngle4f((float) -swingAngle, 1, 0, 0),
                    new Vector3f(2f), new AxisAngle4f()));
            sit.teleport(at.clone().add(0, -0.3, 0));
        }, () -> clear(player.getUniqueId()));
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && event.getPlayer().getVehicle() instanceof ItemDisplay seat && seat.getScoreboardTags().contains("lobby_collectible")) {
            seat.removePassenger(event.getPlayer());
        }
    }

    private void scarecrow(Player player) {
        Placed p = place(player);
        Location base = fx.ground(player);
        keep(p, model(base.clone().add(0, 0.5, 0), "gadget/scarecrow_3d", Material.HAY_BLOCK, 2f, 0.5f));
        fx.sound(base, "entity.witch.ambient", 1f, 0.8f);
        p.task = fx.every(600, t -> {
            for (int k = 0; k < 3; k++) {
                double a = t * 0.07 + k * Math.PI * 2 / 3;
                Location crow = base.clone().add(Math.cos(a) * 2.2, 3.4 + Math.sin(t * 0.1 + k) * 0.3, Math.sin(a) * 2.2);
                crow.getWorld().spawnParticle(Particle.DUST, crow, 2, 0.08, 0.04, 0.08, 0, new Particle.DustOptions(Color.fromRGB(0x1C1C24), 1.3f));
            }
            if (t % 50 == 0) fx.sound(base, "entity.parrot.imitate.witch", 0.6f, 0.6f);
            if (t % 6 == 0) base.getWorld().spawnParticle(Particle.FALLING_DUST, base.clone().add(0, 2.2, 0), 1, 0.3, 0.3, 0.3, 0,
                    Material.HAY_BLOCK.createBlockData());
        }, () -> clear(player.getUniqueId()));
    }

    private void cauldron(Player player) {
        Placed p = place(player);
        Location base = fx.ground(player);
        BlockDisplay pot = keep(p, base.getWorld().spawn(base, BlockDisplay.class, d -> {
            d.setBlock(Material.CAULDRON.createBlockData());
            d.setTransformation(new Transformation(new Vector3f(-0.5f, 0, -0.5f), new AxisAngle4f(), new Vector3f(1f), new AxisAngle4f()));
        }));
        BlockDisplay brew = keep(p, base.getWorld().spawn(base, BlockDisplay.class, d -> {
            d.setBlock(Material.LIME_STAINED_GLASS.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(-0.4f, 0.75f, -0.4f), new AxisAngle4f(), new Vector3f(0.8f, 0.05f, 0.8f), new AxisAngle4f()));
        }));
        fx.sound(base, "entity.witch.celebrate", 1f, 1f);
        Color[] brews = {Color.fromRGB(0x7FE35A), Color.fromRGB(0xB266FF), Color.fromRGB(0xFF6FD8), Color.fromRGB(0xFF8A1F)};
        p.task = fx.every(500, t -> {
            Location surface = base.clone().add(0, 0.85, 0);
            if (t % 2 == 0) surface.getWorld().spawnParticle(Particle.DUST, surface, 2, 0.25, 0.05, 0.25, 0,
                    new Particle.DustOptions(brews[(t / 80) % brews.length], 1.2f));
            if (t % 5 == 0) surface.getWorld().spawnParticle(Particle.BUBBLE_POP, surface, 2, 0.2, 0.05, 0.2, 0.02);
            if (t % 25 == 0) fx.sound(surface, "block.brewing_stand.brew", 0.6f, 0.8f + (t % 75) / 100f);
            if (t % 80 == 40) {   // a potion pops out and bursts in colour
                Location pop = surface.clone().add(fx.rand(0.6), 1.2, fx.rand(0.6));
                pop.getWorld().spawnParticle(Particle.WITCH, pop, 20, 0.3, 0.3, 0.3, 0.1);
                fx.sound(pop, "entity.splash_potion.break", 0.7f, 1f);
            }
        }, () -> clear(player.getUniqueId()));
    }

    private void sandCastle(Player player) {
        Placed p = place(player);
        Location base = fx.ground(player);
        ItemDisplay castle = keep(p, model(base.clone().add(0, 0.5, 0), "gadget/sand_castle_3d", Material.SAND, 0.01f, 0));
        fx.later(1, () -> {
            castle.setInterpolationDelay(0);
            castle.setInterpolationDuration(30);
            castle.setTransformation(new Transformation(new Vector3f(0, 0.4f, 0), new AxisAngle4f(), new Vector3f(1.8f), new AxisAngle4f()));
        });
        p.task = fx.every(400, t -> {
            if (t < 30 && t % 2 == 0) {
                base.getWorld().spawnParticle(Particle.BLOCK, base.clone().add(0, 0.3, 0), 6, 0.6, 0.2, 0.6, 0, Material.SAND.createBlockData());
                if (t % 6 == 0) fx.sound(base, "block.sand.place", 0.8f, 0.8f + t * 0.02f);
            }
            if (t == 380) {
                castle.setInterpolationDelay(0);
                castle.setInterpolationDuration(18);
                castle.setTransformation(new Transformation(new Vector3f(0, 0.05f, 0), new AxisAngle4f(), new Vector3f(1.8f, 0.05f, 1.8f), new AxisAngle4f()));
                fx.sound(base, "block.sand.break", 1f, 0.8f);
                base.getWorld().spawnParticle(Particle.BLOCK, base.clone().add(0, 0.5, 0), 40, 0.6, 0.3, 0.6, 0, Material.SAND.createBlockData());
            }
        }, () -> clear(player.getUniqueId()));
    }

    /** Tic-tac-toe on the ground: the owner plays X, the next person to click a square plays O. */
    private void ticTacToe(Player player) {
        Placed p = place(player);
        Location base = fx.ground(player);
        keep(p, model(base.clone().add(0, 0.5, 0), "gadget/ttt_board", Material.OAK_PRESSURE_PLATE, 3f, -0.48f));
        char[] cells = new char[9];
        UUID[] players = {player.getUniqueId(), null};
        int[] turn = {0};
        boolean[] over = {false};
        for (int i = 0; i < 9; i++) {
            int cell = i;
            double cx = ((i % 3) - 1) * 1.0, cz = ((i / 3) - 1) * 1.0;
            Location at = base.clone().add(cx, 0, cz);
            box(p, at, 0.95f, 0.2f, clicker -> {
                if (over[0] || cells[cell] != 0) return;
                if (players[1] == null && !clicker.getUniqueId().equals(players[0])) players[1] = clicker.getUniqueId();
                UUID mover = players[turn[0]];
                if (mover == null || !mover.equals(clicker.getUniqueId())) {
                    plugin.messages().send(clicker, "collectibles.gadget.ttt-wait");
                    return;
                }
                char mark = turn[0] == 0 ? 'X' : 'O';
                cells[cell] = mark;
                keep(p, model(at.clone().add(0, 0.5, 0), mark == 'X' ? "gadget/ttt_x" : "gadget/ttt_o", mark == 'X' ? Material.RED_CARPET : Material.BLUE_CARPET,
                        0.9f, -0.44f));
                fx.sound(at, "block.wood.place", 1f, mark == 'X' ? 1.2f : 0.9f);
                int[][] lines = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};
                for (int[] line : lines) {
                    if (cells[line[0]] == mark && cells[line[1]] == mark && cells[line[2]] == mark) {
                        over[0] = true;
                        fx.confetti(base.clone().add(0, 1, 0), 50, 1);
                        fx.sound(base, "ui.toast.challenge_complete", 0.8f, 1.2f);
                        for (UUID id : players) {
                            Player who = id == null ? null : plugin.getServer().getPlayer(id);
                            if (who != null) plugin.messages().send(who, "collectibles.gadget.ttt-win",
                                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("player", clicker.getName()));
                        }
                        fx.later(60, () -> clear(player.getUniqueId()));
                        return;
                    }
                }
                boolean full = true;
                for (char c : cells) if (c == 0) full = false;
                if (full) {
                    over[0] = true;
                    fx.sound(base, "entity.villager.no", 0.8f, 1f);
                    fx.later(40, () -> clear(player.getUniqueId()));
                    return;
                }
                turn[0] = 1 - turn[0];
            });
        }
        fx.sound(base, "block.wood.place", 1f, 0.8f);
        plugin.messages().send(player, "collectibles.gadget.ttt-start");
        p.task = fx.every(1200, t -> {
        }, () -> clear(player.getUniqueId()));
    }

    private void djBooth(Player player) {
        Placed p = place(player);
        Location base = fx.ground(player);
        float yaw = player.getLocation().getYaw() + 180;
        keep(p, base.getWorld().spawn(base.clone().add(0, 0.5, 0), ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled(Material.JUKEBOX, "gadget/dj_booth_3d"));
            d.setRotation(yaw, 0);
            d.setTransformation(new Transformation(new Vector3f(0, 0.25f, 0), new AxisAngle4f(), new Vector3f(1.6f), new AxisAngle4f()));
        }));
        // a bouncy beat: bass on the beat, hats between, a melody over the top
        int[] melody = {0, 3, 5, 7, 10, 7, 5, 3, 0, 5, 7, 12, 10, 7, 5, 3};
        p.task = fx.every(480, t -> {
            Location top = base.clone().add(0, 1.4, 0);
            if (t % 8 == 0) fx.sound(top, "block.note_block.basedrum", 1f, 0.8f);
            if (t % 8 == 4) fx.sound(top, "block.note_block.snare", 0.8f, 1f);
            if (t % 4 == 2) fx.sound(top, "block.note_block.hat", 0.6f, 1.2f);
            if (t % 4 == 0) {
                int note = melody[(t / 4) % melody.length];
                fx.sound(top, "block.note_block.bit", 0.7f, (float) Math.pow(2, (note - 6) / 12.0));
                top.getWorld().spawnParticle(Particle.NOTE, top.clone().add(fx.rand(0.6), 0.4, fx.rand(0.6)), 0, note / 24.0, 0, 0, 1);
            }
            if (t % 8 == 0) {    // disco lights sweeping around
                for (int k = 0; k < 6; k++) {
                    double a = t * 0.1 + k * Math.PI / 3;
                    Location beam = base.clone().add(Math.cos(a) * 2.5, 0.2, Math.sin(a) * 2.5);
                    beam.getWorld().spawnParticle(Particle.DUST, beam, 4, 0.2, 0.6, 0.2, 0, new Particle.DustOptions(fx.party(), 1.4f));
                }
            }
        }, () -> clear(player.getUniqueId()));
    }

    // ---------------------------------------------------------------- clicks on placed things

    /** Hitting (left-click) a placed thing does what a right-click does: hit the ball, swing at the piñata... */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        Consumer<Player> click = clicks.get(event.getAttacked().getUniqueId());
        if (click == null) return;
        event.setCancelled(true);
        click.accept(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEntityEvent event) {
        Consumer<Player> click = clicks.get(event.getRightClicked().getUniqueId());
        if (click == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        click.accept(event.getPlayer());
    }
}
