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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            case "pinata" -> {
                if (pinataUp.contains(player.getUniqueId())) {          // it's up: right-click just swings the bat
                    fx.sound(player.getLocation(), "fractal:gadget.bat_swing", 0.8f, 1f);
                    player.swingMainHand();
                } else {
                    pinata(player);
                }
            }
            case "jetpack" -> jetpack(player);
            case "mob_gun" -> mobGun(player);
            case "railgun" -> railgun(player);
            case "explosive_bow" -> boomerang(player);                 // (the Boomerang: it kept the old key, so owners keep it)
            case "pong" -> pong(player);
            case "beach_ball" -> bouncyBall(player, "gadget/beach_ball_3d", Material.SNOWBALL, 1.1f, 0.45, 0.62, "entity.slime.jump_small");
            case "rush_pearl" -> {
                if (fx.parkour(player)) return true;
                EnderPearl pearl = player.launchProjectile(EnderPearl.class, look.clone().multiply(1.7));
                pearl.setItem(fx.modelled(Material.ENDER_PEARL, "gadget/rush_pearl"));   // the teal Rush Pearl, not a plain ender pearl
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

    /**
     * When Pigs Fly: a pig with a pair of wings sprouting from its back pops up under the player, they're on it, and
     * off it flies: up and round two wide loops with a swoop or two, wings flapping harder on the climbs, and back
     * down to land where it started about twelve seconds later. Sneaking off ends the flight early (the pig flies off).
     */
    private void pigsFly(Player player) {
        if (fx.parkour(player) || player.isInsideVehicle()) return;
        World world = player.getWorld();
        Location home = player.getLocation().clone();
        Pig pig = world.spawn(home, Pig.class, p -> {
            p.setInvulnerable(true);
            p.setAware(false);
            p.setGravity(false);
            p.setPersistent(false);
            p.setSilent(true);
        });
        fx.tagged(pig, "pig");
        ItemDisplay[] wings = new ItemDisplay[2];
        for (int i = 0; i < 2; i++) {
            String model = i == 0 ? "cloak/part/wing_left" : "cloak/part/wing_right";
            wings[i] = world.spawn(home, ItemDisplay.class, d -> {
                d.setItemStack(fx.modelled(Material.FEATHER, model));
                d.setPersistent(false);
                d.setTeleportDuration(1);
            });
            fx.tagged(wings[i], "wing");
        }
        pig.addPassenger(player);
        fx.sound(home, "entity.pig.ambient", 1f, 1.4f);
        fx.sound(home, "entity.ender_dragon.flap", 0.6f, 1.6f);
        final int FLIGHT = 240;
        float startYaw = home.getYaw();
        Location[] last = {home.clone()};
        boolean[] gone = {false};
        fx.every(FLIGHT, t -> {
            if (!pig.isValid() || gone[0]) return;
            if (!player.isOnline() || !pig.getPassengers().contains(player)) {   // hopped off: the pig flies away
                gone[0] = true;
                Location up = pig.getLocation();
                fx.every(30, k -> {
                    if (!pig.isValid()) return;
                    Location next = pig.getLocation().add(0, 0.25, 0);
                    pig.teleport(next);
                    placeWings(wings, next, k);
                }, () -> {
                    for (ItemDisplay w : wings) w.remove();
                    pop(pig, "entity.pig.ambient");
                });
                fx.sound(up, "entity.pig.ambient", 1f, 1.8f);
                return;
            }
            // the flight: two smooth loops round a circle that starts and ends where the player stood, setting off the
            // way they faced (turning right). It climbs as it sets off and glides back down to land on the same spot,
            // always moving forward and turning at the same gentle rate, so its body (which the game turns to face the
            // way it's moving) and its wings stay lined up the whole way, landing included.
            double k = t / (double) FLIGHT;
            double phi = k * Math.PI * 4;
            final double R = 5;
            Vector facing = new Location(world, 0, 0, 0, startYaw, 0).getDirection().setY(0).normalize();
            Vector toRight = new Vector(-facing.getZ(), 0, facing.getX());
            Vector centre = home.toVector().add(toRight.clone().multiply(R));
            double height = 5.5 * Math.sin(Math.PI * k) + 0.8 * Math.sin(Math.PI * 6 * k) * Math.sin(Math.PI * k);
            Location go = centre.clone().add(toRight.clone().multiply(-R * Math.cos(phi))).add(facing.clone().multiply(R * Math.sin(phi)))
                    .toLocation(world).add(0, Math.max(0, height), 0);
            for (int lift = 0; lift < 8 && !(go.getBlock().isPassable() && go.clone().add(0, 1, 0).getBlock().isPassable()
                    && go.clone().add(0, 2, 0).getBlock().isPassable()); lift++) go.add(0, 1, 0);   // never into a block
            Vector move = go.toVector().subtract(last[0].toVector());
            Vector heading = toRight.clone().multiply(Math.sin(phi)).add(facing.clone().multiply(Math.cos(phi)));
            float yawNow = (float) Math.toDegrees(Math.atan2(-heading.getX(), heading.getZ()));
            go.setYaw(yawNow);
            go.setPitch(0);
            pig.teleport(go, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
            pig.setRotation(yawNow, 0);                                // (its head too, so nothing turns it back)
            pig.setBodyYaw(yawNow);
            boolean climbing = move.getY() > 0.02;
            placeWings(wings, go, climbing ? t * 2 : t);
            last[0] = go;
            if (t % (climbing ? 6 : 10) == 0) fx.sound(go, "entity.ender_dragon.flap", 0.35f, 1.8f);
            if (t % 40 == 0) fx.sound(go, "entity.pig.ambient", 0.8f, 1.6f);
            if (t % 3 == 0) world.spawnParticle(Particle.CLOUD, go.clone().add(0, 0.3, 0), 1, 0.2, 0.05, 0.2, 0.01);
        }, () -> {
            if (gone[0]) return;
            player.leaveVehicle();
            for (ItemDisplay w : wings) w.remove();
            world.spawnParticle(Particle.HEART, pig.getLocation().add(0, 1, 0), 4, 0.3, 0.2, 0.3, 0);
            pop(pig, "entity.pig.ambient");
        });
    }

    /**
     * The pig's wings. Each wing model is a flat sheet of feathers growing sideways out of the spine (model x = 8)
     * with the feathers hanging down from the bone; the display shows the model turned half round, so wing_left
     * reaches out to -x. Here each wing is laid flat (turned a quarter about its own side-to-side line, so the bone
     * leads and the feathers trail back), moved out to the pig's flank so none of it is inside the body, then flapped
     * up and down about the pig's front-to-back line, and finally turned with the pig.
     */
    private static void placeWings(ItemDisplay[] wings, Location pig, int t) {
        float yaw = (float) Math.toRadians(pig.getYaw());
        float flap = (float) Math.toRadians(10 + 32 * Math.sin(t * 0.5));   // from a little below level to well up
        Location root = pig.clone().add(0, 0.78, 0);                  // up on its back...
        root.setYaw(0);
        root.setPitch(0);
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? -1 : 1;
            wings[i].teleport(root);
            wings[i].setInterpolationDelay(0);
            wings[i].setInterpolationDuration(1);
            // ...at the shoulder blades: either side of the spine; the sheet reaches forward from its root, so it sits
            // back a little to put the leading edge just behind the middle of the back
            wings[i].setTransformationMatrix(new Matrix4f().rotateY(-yaw).translate(side * 0.3f, 0, -0.3f)
                    .rotateZ(side * flap).rotateX((float) (Math.PI / 2)).scale(0.7f));
        }
    }

    /**
     * Creeper Astronaut: a creeper in a glass space helmet stands on a little launch pad. A countdown floats over it
     * (3... 2... 1... LIFTOFF!) with beeps and steam; then it blasts off on a roaring column of flame and smoke,
     * slowly spinning, faster and faster, a ring of smoke rolling out across the ground. High up it starts to hiss...
     * and goes off as a giant green creeper-face firework, its helmet shattering, a shower of green sparks drifting down.
     */
    private void creeperAstronaut(Player player) {
        Location pad = fx.ground(player);
        World world = pad.getWorld();
        float facing = player.getLocation().getYaw() + 180;
        pad.setYaw(facing);
        Creeper creeper = world.spawn(pad, Creeper.class, c -> {
            c.setInvulnerable(true);
            c.setAware(false);
            c.setGravity(false);
            c.setSilent(true);
            c.setPersistent(false);
            c.setMaxFuseTicks(Integer.MAX_VALUE);
            c.setRotation(facing, 0);
        });
        fx.tagged(creeper, "creeper");
        // the helmet: a glass block round its head (the head is the top half-block, centred 1.45 up), kept on it by
        // moving it with the creeper every tick rather than riding (riding put it too low)
        ItemDisplay helmet = world.spawn(pad.clone().add(0, 1.7, 0), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.GLASS));
            d.setPersistent(false);
            // riding the creeper, the display sits at the top of its head (1.7 up): the glass is moved down by a
            // quarter block so its middle is the head's middle (1.45 up)
            d.setTransformation(new Transformation(new Vector3f(0, -0.25f, 0), new AxisAngle4f(), new Vector3f(0.6f), new AxisAngle4f()));
        });
        fx.tagged(helmet, "helmet");
        creeper.addPassenger(helmet);                                  // riding: it moves exactly with the creeper
        BlockDisplay base = world.spawn(pad.clone().add(0, 0.01, 0), BlockDisplay.class, d -> {   // the launch pad
            d.setBlock(Material.IRON_TRAPDOOR.createBlockData());
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(-0.6f, 0, -0.6f), new AxisAngle4f(), new Vector3f(1.2f, 1f, 1.2f), new AxisAngle4f()));
        });
        fx.tagged(base, "pad");
        TextDisplay count = label(pad.clone().add(0, 2.6, 0), text("3", 0xFF5A5A), 0);
        fx.tagged(count, "count");
        final int COUNT = 60, FLY = 70;
        float[] spin = {facing};
        double[] height = {0}, speed = {0};
        fx.every(COUNT + FLY, t -> {
            if (!creeper.isValid()) return;
            Location at = pad.clone().add(0, height[0], 0);
            if (t < COUNT) {                                           // the countdown
                if (t % 20 == 0) {
                    int n = 3 - t / 20;
                    count.text(text(String.valueOf(n), n == 1 ? 0x5CFF7A : n == 2 ? 0xFFD23A : 0xFF5A5A));
                    fx.sound(pad, "block.note_block.bit", 1f, n == 1 ? 2f : 1.4f);
                }
                if (t % 4 == 0) world.spawnParticle(Particle.CLOUD, pad.clone().add(0, 0.1, 0), 2, 0.4, 0.02, 0.4, 0.02);
                if (t > COUNT - 20 && t % 2 == 0) world.spawnParticle(Particle.SMALL_FLAME, pad.clone().add(0, 0.15, 0), 3, 0.15, 0, 0.15, 0.01);
                return;
            }
            if (t == COUNT) {                                          // LIFTOFF!
                count.text(text("LIFTOFF!", 0x5CFF7A));
                fx.sound(pad, "entity.firework_rocket.launch", 1f, 0.5f);
                fx.sound(pad, "entity.generic.explode", 0.4f, 1.8f);
                for (int i = 0; i < 24; i++) {                         // a ring of smoke rolling out across the ground
                    double a = i * Math.PI * 2 / 24;
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, pad.clone().add(0, 0.2, 0), 0, Math.cos(a), 0.02, Math.sin(a), 0.12);
                    world.spawnParticle(Particle.CLOUD, pad.clone().add(0, 0.2, 0), 0, Math.cos(a), 0, Math.sin(a), 0.3);
                }
                fx.later(30, count::remove);
            }
            int f = t - COUNT;
            speed[0] = Math.min(0.9, speed[0] + 0.022);
            height[0] += speed[0];
            spin[0] += 4 + f * 0.3f;                                   // spinning up as it climbs
            Location up = pad.clone().add(0, height[0], 0);
            if (!up.clone().add(0, 2, 0).getBlock().isPassable()) {    // hit a ceiling: go off now
                height[0] = Math.max(0, height[0] - speed[0]);
                creeper.setMaxFuseTicks(1);
                return;
            }
            up.setYaw(spin[0]);
            creeper.teleport(up);
            creeper.setRotation(spin[0], 0);
            Location head = up.clone().add(0, 1.45, 0);
            helmet.setInterpolationDelay(0);                           // turn the glass with the creeper as it spins
            helmet.setInterpolationDuration(1);
            helmet.setTransformationMatrix(new Matrix4f().translate(0, -0.25f, 0).rotateY((float) Math.toRadians(-spin[0])).scale(0.6f));
            // the engine: a roaring flame column and smoke pouring out below
            world.spawnParticle(Particle.FLAME, up, 0, 0, -1, 0, 0.35);
            world.spawnParticle(Particle.FLAME, up, 6, 0.12, 0.05, 0.12, 0.04);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, up.clone().add(0, -0.3, 0), 2, 0.15, 0.1, 0.15, 0.02);
            world.spawnParticle(Particle.LAVA, up, 1, 0.05, 0, 0.05, 0);
            if (f % 5 == 0) fx.sound(up, "entity.blaze.shoot", 0.5f, 0.6f);
            if (f == FLY - 20) fx.sound(up, "entity.creeper.primed", 1.4f, 0.8f);   // ...hiss
            if (f > FLY - 20) world.spawnParticle(Particle.DUST, head, 3, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(Color.WHITE, 1.2f));
        }, () -> {
            Location boom = creeper.getLocation().add(0, 1, 0);
            helmet.remove();
            creeper.remove();
            base.remove();
            count.remove();
            fx.sound(boom, "block.glass.break", 1.2f, 0.9f);           // the helmet shatters
            world.spawnParticle(Particle.BLOCK, boom.clone().add(0, 0.45, 0), 30, 0.3, 0.3, 0.3, 0.1, Material.GLASS.createBlockData());
            for (int i = 0; i < 2; i++) {                              // the big creeper-face firework, twice for size
                Firework fw = world.spawn(boom, Firework.class, fwk -> {
                    FireworkMeta meta = fwk.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.CREEPER).withColor(Color.LIME, Color.GREEN)
                            .withFade(Color.fromRGB(0xB8FFB0)).withFlicker().withTrail().build());
                    fwk.setFireworkMeta(meta);
                });
                fx.tagged(fw, "firework");
                fw.detonate();
            }
            world.spawnParticle(Particle.FIREWORK, boom, 60, 0.4, 0.4, 0.4, 0.25);
            for (int i = 0; i < 14; i++) {                             // green sparks drifting down
                Vector drift = new Vector(fx.rand(0.08), -0.02 - Math.random() * 0.03, fx.rand(0.08));
                fx.driftFx(boom.clone().add(fx.rand(1.5), fx.rand(1), fx.rand(1.5)), "gadget/fx_sparkle", 0.3f, drift, 40);
            }
        });
    }

    /**
     * Exploding Sheep: a sheep pops out and starts to swell up like a balloon, flashing through the rainbow faster and
     * faster, wobbling, bleating higher and higher, with a fizzing fuse... then POP: its wool bursts off in a cloud of
     * rainbow tufts that drift down, and what's left is one very bald, very confused sheep, who bleats and poofs away.
     */
    private void explodingSheep(Player player) {
        Location at = fx.ground(player);
        World world = at.getWorld();
        Sheep sheep = world.spawn(at, Sheep.class, s -> {
            s.setInvulnerable(true);
            s.setAware(false);
            s.setPersistent(false);
            s.setRotation(player.getLocation().getYaw() + 180, 0);   // facing the player
        });
        fx.tagged(sheep, "sheep");
        org.bukkit.attribute.AttributeInstance size = sheep.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        DyeColor[] colours = {DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.LIGHT_BLUE, DyeColor.BLUE,
                DyeColor.PURPLE, DyeColor.MAGENTA, DyeColor.PINK};
        float facing = player.getLocation().getYaw() + 180;
        fx.sound(at, "entity.sheep.ambient", 1f, 1f);
        final int SWELL = 80;
        fx.every(SWELL, t -> {
            if (!sheep.isValid()) return;
            double k = t / (double) SWELL;
            if (size != null) size.setBaseValue(1 + 1.6 * k * k);      // swelling up, faster at the end
            int every = Math.max(1, 8 - t / 10);
            if (t % every == 0) sheep.setColor(colours[(t / every) % colours.length]);
            sheep.setRotation(facing + (float) Math.sin(t * (0.3 + k)) * 12 * (float) k, 0);   // wobbling
            if (t % 12 == 0) fx.sound(sheep.getLocation(), "entity.sheep.ambient", 0.8f, (float) (0.9 + k * 1.1));
            if (t % 4 == 0) fx.sound(sheep.getLocation(), "block.note_block.hat", 0.4f, (float) (0.8 + k * 1.2));
            if (t == SWELL - 30) fx.sound(sheep.getLocation(), "entity.creeper.primed", 0.8f, 1.6f);   // the fuse
            Location top = sheep.getLocation().add(0, 1.0 + 1.2 * k, 0);
            if (t > SWELL - 30) world.spawnParticle(Particle.SMALL_FLAME, top, 1, 0.05, 0.05, 0.05, 0.01);
            if (t % 3 == 0) world.spawnParticle(Particle.DUST, sheep.getLocation().add(0, 0.8 + k, 0), 2, 0.4 + k * 0.6, 0.3 + k * 0.4,
                    0.4 + k * 0.6, 0, new Particle.DustOptions(colours[ThreadLocalRandom.current().nextInt(colours.length)].getColor(), 1.2f));
        }, () -> {
            if (!sheep.isValid()) return;
            Location boom = sheep.getLocation().add(0, 1.3, 0);
            // POP!
            fx.sound(boom, "fractal:gadget.party_popper", 1f, 0.8f);
            fx.sound(boom, "entity.generic.explode", 0.6f, 1.6f);
            fx.sound(boom, "entity.sheep.shear", 1f, 0.8f);
            world.spawnParticle(Particle.EXPLOSION, boom, 1);
            world.spawnParticle(Particle.CLOUD, boom, 30, 0.6, 0.5, 0.6, 0.08);
            fx.confetti(boom, 70, 1);
            for (Player near : world.getPlayers()) {                   // a puff of air
                if (fx.parkour(near) || near.getLocation().distanceSquared(boom) > 16) continue;
                Vector away = near.getLocation().toVector().subtract(boom.toVector()).setY(0);
                if (away.lengthSquared() < 1e-4) away = new Vector(0.1, 0, 0);
                near.setVelocity(near.getVelocity().add(away.normalize().multiply(0.45).setY(0.3)));
            }
            // the wool: rainbow tufts that burst off and drift down like snow, then melt away
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < 26; i++) {
                Material wool = Material.valueOf(colours[i % colours.length].name() + "_WOOL");
                Vector vel = new Vector(r.nextDouble(-0.3, 0.3), r.nextDouble(0.15, 0.45), r.nextDouble(-0.3, 0.3));
                float tuft = (float) r.nextDouble(0.18, 0.32);
                BlockDisplay piece = world.spawn(boom, BlockDisplay.class, d -> {
                    d.setBlock(wool.createBlockData());
                    d.setPersistent(false);
                    d.setTeleportDuration(1);
                    d.setTransformation(new Transformation(new Vector3f(-tuft / 2), new AxisAngle4f(), new Vector3f(tuft), new AxisAngle4f()));
                });
                fx.tagged(piece, "wool");
                Location[] where = {boom.clone()};
                float spin = (float) r.nextDouble(-20, 20);
                fx.every(70, t -> {
                    if (!piece.isValid()) return;
                    vel.multiply(0.9).setY(Math.max(-0.06, vel.getY() - 0.025));   // floaty: wool drifts
                    Location next = where[0].clone().add(vel);
                    if (next.getBlock().isPassable()) where[0] = next;
                    else vel.multiply(0);
                    where[0].setYaw(t * spin);
                    piece.teleport(where[0]);
                    if (t == 55) {
                        piece.setInterpolationDelay(0);
                        piece.setInterpolationDuration(14);
                        piece.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0), new AxisAngle4f()));
                    }
                }, piece::remove);
            }
            // and the sheep: back to size, bald as anything, looking about, embarrassed
            if (size != null) size.setBaseValue(1);
            sheep.setSheared(true);
            sheep.setColor(DyeColor.WHITE);
            fx.every(50, t -> {
                if (!sheep.isValid()) return;
                if (t % 10 == 0) sheep.setRotation(facing + (t % 20 == 0 ? 35 : -35), 0);
                if (t == 12) fx.sound(sheep.getLocation(), "entity.sheep.ambient", 1f, 0.6f);
                if (t == 30) world.spawnParticle(Particle.FALLING_WATER, sheep.getLocation().add(0, 1.1, 0), 4, 0.2, 0.05, 0.2, 0);
            }, () -> pop(sheep, "entity.sheep.ambient"));
        });
    }

    /**
     * Ducklings: a mother duck and four little ducklings waddle along behind the player in a proper line, each one
     * walking exactly where the one in front walked (they follow the player's real path, so round corners too, not a
     * straight line dragged behind). They waddle side to side as they go, quack down the line one after another,
     * hop in turn when the player jumps, and when the player stops for a moment they gather round in a little ring
     * and look up at them. After fifteen seconds they waddle off with a final quack.
     */
    private void ducklings(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        List<Chicken> line = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int idx = i;
            Chicken duck = world.spawn(at, Chicken.class, c -> {
                c.setInvulnerable(true);
                c.setAware(false);
                c.setPersistent(false);
                c.setSilent(true);
                if (idx > 0) {
                    c.setBaby();
                    c.setAgeLock(true);
                }
            });
            fx.tagged(duck, "duck");
            line.add(duck);
        }
        world.spawnParticle(Particle.HEART, at.clone().add(0, 0.6, 0), 4, 0.4, 0.2, 0.4, 0);
        fx.sound(at, "entity.chicken.egg", 1f, 1.4f);
        java.util.ArrayDeque<Location> path = new java.util.ArrayDeque<>();   // where the player has walked, newest first
        path.addFirst(at.clone());
        int[] still = {0}, hop = {-1};
        double[] wasY = {at.getY()};
        fx.every(300, t -> {
            if (!player.isOnline() || player.getWorld() != world) return;
            Location lead = player.getLocation();
            if (lead.distanceSquared(path.peekFirst()) > 0.04) {        // a new breadcrumb every few centimetres walked
                path.addFirst(lead.clone());
                while (path.size() > 200) path.removeLast();
                still[0] = 0;
            } else {
                still[0]++;
            }
            if (player.isOnGround() == false && lead.getY() - wasY[0] > 0.3 && hop[0] < 0) hop[0] = t;   // the player jumped
            if (player.isOnGround()) wasY[0] = lead.getY();
            boolean gather = still[0] > 40;
            for (int i = 0; i < line.size(); i++) {
                Chicken duck = line.get(i);
                if (!duck.isValid()) continue;
                Location want;
                if (gather) {                                          // stopped: gather round in a little ring
                    double a = i * Math.PI * 2 / line.size() + t * 0.01;
                    want = lead.clone().add(Math.cos(a) * 1.3, 0, Math.sin(a) * 1.3);
                } else {                                               // walk exactly where the one in front walked
                    want = alongPath(path, 1.1 + i * 0.75);
                }
                Location from = duck.getLocation();
                Vector move = want.toVector().subtract(from.toVector()).setY(0);
                double dist = move.length();
                if (dist > 8) {
                    duck.teleport(want);
                    continue;
                }
                double speed = Math.min(0.32, dist * 0.35);
                Vector vel = dist > 0.05 ? move.normalize().multiply(speed) : new Vector();
                double vy = duck.getVelocity().getY();
                if (duck.isOnGround() && want.getY() - from.getY() > 0.4) vy = 0.42;   // up a step
                if (hop[0] >= 0 && t - hop[0] == 2 + i * 3 && duck.isOnGround()) {    // hopping in turn after the player
                    vy = 0.38;
                    fx.sound(from, "entity.chicken.step", 0.6f, 1.8f);
                }
                duck.setVelocity(vel.setY(vy));
                // facing: where it's going (or up at the player when gathered), waddling side to side as it walks
                Vector look = gather ? lead.toVector().subtract(from.toVector()) : move;
                float face = (float) Math.toDegrees(Math.atan2(-look.getX(), look.getZ()));
                float waddle = speed > 0.04 ? (float) Math.sin(t * 0.9 + i) * 14 : 0;
                duck.setRotation(face + waddle, 0);
                duck.setBodyYaw(face + waddle);
            }
            if (hop[0] >= 0 && t - hop[0] > 2 + line.size() * 3) hop[0] = -1;
            if (t % 50 == 0) {                                         // quacks running down the line
                for (int i = 0; i < line.size(); i++) {
                    Chicken duck = line.get(i);
                    fx.later(i * 3, () -> {
                        if (duck.isValid()) fx.sound(duck.getLocation(), "entity.chicken.ambient", 0.5f, duck.isAdult() ? 1.3f : 1.9f);
                    });
                }
            }
            if (gather && still[0] == 41) world.spawnParticle(Particle.HEART, lead.clone().add(0, 0.5, 0), 3, 0.6, 0.2, 0.6, 0);
        }, () -> {
            for (int i = 0; i < line.size(); i++) {
                Chicken duck = line.get(i);
                fx.later(i * 3, () -> pop(duck, "entity.chicken.ambient"));
            }
        });
    }

    /** The point `back` blocks back along a walked path (newest point first), or its oldest point if it's shorter. */
    private static Location alongPath(java.util.ArrayDeque<Location> path, double back) {
        Location prev = null;
        double gone = 0;
        for (Location here : path) {
            if (prev != null) {
                double step = prev.distance(here);
                if (gone + step >= back) {
                    double f = (back - gone) / step;
                    return prev.clone().add(here.toVector().subtract(prev.toVector()).multiply(f));
                }
                gone += step;
            }
            prev = here;
        }
        return prev.clone();
    }

    /**
     * The jetpack: a pair of silver tanks on the player's back. It fires up with a burst of flame and smoke, then two
     * flickering flames roar out of the nozzles (real flame models riding with the pack, plus jets of fire and smoke
     * pouring down) while it lifts them, the pack rocking a little with the thrust. Near the end it sputters, the
     * flames shrinking and coughing smoke, and it lets them float down gently. Sneak to drop.
     */
    private void jetpack(Player player) {
        if (fx.parkour(player)) return;
        World world = player.getWorld();
        ItemDisplay pack = fx.rider(player, "gadget/jetpack_3d");
        ItemDisplay[] flames = {fx.rider(player, "gadget/jet_flame"), fx.rider(player, "gadget/jet_flame")};
        for (ItemDisplay flame : flames) flame.setBrightness(new Display.Brightness(15, 15));
        Location feet = player.getLocation();
        fx.sound(feet, "entity.firework_rocket.launch", 1f, 0.5f);
        fx.sound(feet, "entity.blaze.shoot", 0.8f, 0.6f);
        world.spawnParticle(Particle.FLAME, feet, 20, 0.3, 0.05, 0.3, 0.08);
        world.spawnParticle(Particle.CLOUD, feet, 16, 0.4, 0.05, 0.4, 0.06);
        final int FUEL = 70;
        fx.every(FUEL, t -> {
            if (!player.isOnline()) return;
            float yaw = (float) Math.toRadians(player.getLocation().getYaw());
            boolean sputter = t > FUEL - 14;
            boolean thrust = !player.isSneaking() && !sputter;
            float rock = (float) Math.sin(t * 0.5) * (thrust ? 0.06f : 0.02f);
            pack.setInterpolationDelay(0);
            pack.setInterpolationDuration(2);
            pack.setTransformationMatrix(new Matrix4f().rotateY(-yaw).translate(0, -0.65f, -0.28f).rotateZ(rock).scale(0.6f));
            // the flames, hanging from each nozzle (model x = 5 and 11, bottom y = 1.5 at size 0.6: 0.11 out and 0.24
            // below the pack's middle), flickering and shrinking as it sputters
            float size = sputter ? Math.max(0.05f, 0.5f * (FUEL - t) / 14f) : thrust ? 0.5f + (float) Math.random() * 0.12f : 0.25f;
            for (int i = 0; i < 2; i++) {
                float side = i == 0 ? -0.11f : 0.11f;
                flames[i].setInterpolationDelay(0);
                flames[i].setInterpolationDuration(2);
                flames[i].setTransformationMatrix(new Matrix4f().rotateY(-yaw).translate(0, -0.65f, -0.28f).rotateZ(rock)
                        .translate(side, -0.25f, 0).scale(size).translate(0, -0.5f, 0));
            }
            if (thrust) {
                Vector v = player.getVelocity();
                Vector look = player.getLocation().getDirection();
                player.setVelocity(new Vector(v.getX() + look.getX() * 0.05, Math.min(0.55, v.getY() + 0.18), v.getZ() + look.getZ() * 0.05));
            }
            player.setFallDistance(0);
            // jets of fire and smoke pouring down out of the nozzles
            Vector back = player.getLocation().getDirection().setY(0);
            if (back.lengthSquared() < 1e-4) back = new Vector(0, 0, 1);
            back.normalize().multiply(-0.28);
            Vector right = new Vector(-back.getZ(), 0, back.getX()).normalize().multiply(0.11);
            for (int i = 0; i < 2; i++) {
                Location nozzle = player.getLocation().add(back).add(right.clone().multiply(i == 0 ? 1 : -1)).add(0, 0.9, 0);
                if (thrust) {
                    world.spawnParticle(Particle.FLAME, nozzle.clone().add(0, -0.35, 0), 0, 0, -1, 0, 0.25);
                    world.spawnParticle(Particle.SMALL_FLAME, nozzle.clone().add(0, -0.4, 0), 2, 0.04, 0.05, 0.04, 0.02);
                    if (t % 2 == 0) world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, nozzle.clone().add(0, -0.7, 0), 0, 0, -1, 0, 0.06);
                } else if (sputter && t % 3 == 0) {
                    world.spawnParticle(Particle.LARGE_SMOKE, nozzle.clone().add(0, -0.3, 0), 2, 0.05, 0.05, 0.05, 0.02);
                }
            }
            if (thrust && t % 5 == 0) fx.sound(player.getLocation(), "entity.blaze.burn", 0.5f, 0.7f);
            if (sputter && t % 4 == 0) fx.sound(player.getLocation(), "block.fire.extinguish", 0.4f, 1.6f);
        }, () -> {
            pack.remove();
            for (ItemDisplay flame : flames) flame.remove();
            player.setFallDistance(0);
            if (player.isOnline()) player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING, 60, 0, false, false, false));
        });
    }

    private final Set<UUID> boomerangs = new HashSet<>();

    /**
     * The Boomerang: thrown, it spins out flat in a wide curving loop, whooshing as it goes, and comes back round to
     * the thrower's hand. Anyone in its path gets a cartoon BONK (a little hop, a boing and some stars); hitting a wall
     * sends it straight back. One in the air at a time.
     */
    private void boomerang(Player player) {
        if (!boomerangs.add(player.getUniqueId())) return;            // it's still out there
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector flat = eye.getDirection().setY(0);
        final Vector fwd = flat.lengthSquared() < 1e-4 ? new Vector(0, 0, 1) : flat.normalize();
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
        Location origin = eye.clone().add(0, -0.4, 0);
        double rise = Math.max(-0.6, Math.min(0.6, eye.getDirection().getY())) * 3;   // throwing up or down tilts the loop
        final double REACH = 9, WIDE = 3.4;
        final int OUT = 44;                                           // ticks for the loop
        Location start = origin.clone().add(fwd.clone().multiply(0.8));
        start.setYaw(0);
        start.setPitch(0);
        ItemDisplay rang = world.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled(Material.STICK, "gadget/boomerang_fly"));
            d.setPersistent(false);
            d.setTeleportDuration(1);
        });
        fx.tagged(rang, "boomerang");
        fx.sound(origin, "entity.player.attack.sweep", 0.8f, 1.4f);
        Set<UUID> bonked = new HashSet<>();
        boolean[] homing = {false};
        Location[] at = {start.clone()};
        float[] spin = {0};
        fx.every(140, t -> {
            if (!rang.isValid()) return;
            if (!player.isOnline() || player.getWorld() != world) {
                rang.remove();
                return;
            }
            Location hand = player.getEyeLocation().add(0, -0.4, 0);
            Location next;
            if (!homing[0] && t < OUT) {                               // the loop: out, round, and back
                double phi = Math.PI * 2 * t / OUT;
                Vector off = fwd.clone().multiply(REACH / 2 * (1 - Math.cos(phi))).add(right.clone().multiply(WIDE / 2 * Math.sin(phi)))
                        .add(new Vector(0, Math.sin(phi / 2) * (0.6 + rise) , 0));
                next = origin.clone().add(off);
                double pull = Math.pow(t / (double) OUT, 4);           // drawn back to the hand as it finishes
                next = next.clone().add(hand.toVector().subtract(next.toVector()).multiply(pull));
            } else {                                                   // straight back to the hand
                homing[0] = true;
                Vector to = hand.toVector().subtract(at[0].toVector());
                double dist = to.length();
                next = dist < 0.7 ? hand : at[0].clone().add(to.multiply(Math.min(0.75, dist) / dist));
            }
            if (!homing[0] && !next.getBlock().isPassable()) {         // hit a wall: thunk, and back it comes
                homing[0] = true;
                fx.sound(at[0], "block.wood.hit", 1f, 1.2f);
                world.spawnParticle(Particle.CRIT, at[0], 6, 0.1, 0.1, 0.1, 0.1);
                return;
            }
            if (next.distanceSquared(hand) < 0.6 * 0.6 && t > 8) {      // caught!
                fx.sound(hand, "item.armor.equip_leather", 1f, 1.4f);
                fx.sound(hand, "block.note_block.hat", 0.6f, 1.8f);
                rang.remove();
                return;
            }
            next.setYaw(0);
            next.setPitch(0);
            at[0] = next;
            rang.teleport(next);
            spin[0] += 0.95f;                                          // spinning flat, fast
            rang.setInterpolationDelay(0);
            rang.setInterpolationDuration(1);
            rang.setTransformationMatrix(new Matrix4f().rotateY(spin[0]).rotateX((float) (-Math.PI / 2)).scale(0.9f));
            if (t % 4 == 0) fx.sound(next, "entity.player.attack.sweep", 0.35f, (float) (1.3 + 0.3 * Math.sin(t)));
            world.spawnParticle(Particle.DUST, next, 1, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(Color.fromRGB(0xE2AD6A), 0.7f));
            for (Player who : world.getPlayers()) {                    // BONK
                if (who.equals(player) || fx.parkour(who) || bonked.contains(who.getUniqueId())) continue;
                Location body = who.getLocation().add(0, 1, 0);
                if (body.distanceSquared(next) > 1.3 * 1.3) continue;
                bonked.add(who.getUniqueId());
                Vector push = who.getLocation().toVector().subtract(next.toVector()).setY(0);
                if (push.lengthSquared() < 1e-4) push = fwd.clone();
                who.setVelocity(who.getVelocity().add(push.normalize().multiply(0.35).setY(0.32)));
                fx.sound(body, "fractal:gadget.trampoline_boing", 0.9f, 1.6f);
                fx.sound(body, "block.wood.hit", 1f, 1.4f);
                world.spawnParticle(Particle.CRIT, body.clone().add(0, 0.8, 0), 10, 0.2, 0.1, 0.2, 0.1);
                for (int k = 0; k < 3; k++) {
                    double a2 = k * Math.PI * 2 / 3;
                    fx.driftFx(who.getEyeLocation().add(Math.cos(a2) * 0.35, 0.45, Math.sin(a2) * 0.35), "gadget/fx_star", 0.18f,
                            new Vector(-Math.sin(a2) * 0.03, 0.01, Math.cos(a2) * 0.03), 16);
                }
            }
        }, () -> {
            rang.remove();
            boomerangs.remove(player.getUniqueId());
        });
    }

    /**
     * The railgun: a quick charge-up (a rising whine, sparks of light pulled into the muzzle), then it fires a solid
     * beam of light, a white-hot core in a cyan glow, with energy rings rippling out along it and a spiral of light
     * round it. Where it hits: a flash, a shockwave ring on the surface and a shower of sparks. It kicks back a bit too.
     */
    private void railgun(Player player) {
        World world = player.getWorld();
        Color cyan = Color.fromRGB(0x3FE0FF);
        // ---- the charge (about half a second)
        fx.every(10, t -> {
            if (!player.isOnline()) return;
            Location eye = player.getEyeLocation();
            Vector look = eye.getDirection();
            Location muzzle = eye.clone().add(look.clone().multiply(0.9)).add(0, -0.15, 0);
            fx.sound(muzzle, "block.note_block.bit", 0.5f, (float) (0.6 + t * 0.14));
            for (int i = 0; i < 3; i++) {                              // light pulled in to the muzzle
                Vector off = new Vector(fx.rand(1), fx.rand(1), fx.rand(1)).normalize().multiply(0.8);
                world.spawnParticle(Particle.END_ROD, muzzle.clone().add(off), 0, -off.getX(), -off.getY(), -off.getZ(), 0.08);
            }
            world.spawnParticle(Particle.DUST, muzzle, 2, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(cyan, 0.6f + t * 0.08f));
        }, () -> {
            if (!player.isOnline()) return;
            Location eye = player.getEyeLocation();
            Vector look = eye.getDirection();
            Location muzzle = eye.clone().add(look.clone().multiply(0.9)).add(0, -0.15, 0);
            RayTraceResult hit = world.rayTraceBlocks(muzzle, look, 45);
            double length = hit == null ? 45 : hit.getHitPosition().distance(muzzle.toVector());
            Location end = muzzle.clone().add(look.clone().multiply(length));
            // ---- BOOM
            fx.sound(muzzle, "entity.warden.sonic_boom", 0.5f, 1.7f);
            fx.sound(muzzle, "item.trident.thunder", 0.4f, 2f);
            fx.sound(muzzle, "block.beacon.deactivate", 1f, 2f);
            player.setVelocity(player.getVelocity().add(look.clone().multiply(-0.35)));   // the kick
            // the beam: a white core and a cyan glow, stretched from the muzzle to the hit, then thinning away
            Location from = muzzle.clone();
            from.setYaw(0);
            from.setPitch(0);
            org.joml.Quaternionf along = new org.joml.Quaternionf().rotationTo(0, 0, 1, (float) look.getX(), (float) look.getY(), (float) look.getZ());
            BlockDisplay[] beam = new BlockDisplay[2];
            float[] width = {0.07f, 0.26f};
            Material[] stuff = {Material.WHITE_CONCRETE, Material.LIGHT_BLUE_STAINED_GLASS};
            for (int i = 0; i < 2; i++) {
                float w = width[i];
                Material m = stuff[i];
                beam[i] = world.spawn(from, BlockDisplay.class, d -> {
                    d.setBlock(m.createBlockData());
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setPersistent(false);
                    d.setTransformationMatrix(new Matrix4f().rotate(along).translate(-w / 2, -w / 2, 0).scale(w, w, (float) length));
                });
                fx.tagged(beam[i], "beam");
            }
            beam[0].setGlowing(true);
            beam[0].setGlowColorOverride(cyan);
            // energy rings rippling out along it
            List<ItemDisplay> rings = new ArrayList<>();
            for (double d = 1.5; d < length; d += 3) {
                Location on = muzzle.clone().add(look.clone().multiply(d));
                on.setYaw(0);
                on.setPitch(0);
                ItemDisplay ring = world.spawn(on, ItemDisplay.class, e -> {
                    e.setItemStack(fx.modelled(Material.PAPER, "gadget/fx_circle"));
                    e.setBrightness(new Display.Brightness(15, 15));
                    e.setPersistent(false);
                    e.setTransformationMatrix(new Matrix4f().rotate(along).scale(0.3f));
                });
                fx.tagged(ring, "beam");
                rings.add(ring);
            }
            // a spiral of light round the beam
            Vector up = Math.abs(look.getY()) > 0.95 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
            Vector sideA = look.clone().crossProduct(up).normalize(), sideB = look.clone().crossProduct(sideA).normalize();
            for (double d = 0.5; d < length; d += 0.25) {
                double a = d * 2.2;
                Location on = muzzle.clone().add(look.clone().multiply(d)).add(sideA.clone().multiply(Math.cos(a) * 0.3)).add(sideB.clone().multiply(Math.sin(a) * 0.3));
                world.spawnParticle(Particle.DUST, on, 1, 0, 0, 0, 0, new Particle.DustOptions(cyan, 0.7f));
            }
            // the hit
            world.spawnParticle(Particle.FLASH, end, 1, 0, 0, 0, 0, Color.fromRGB(0x9FE8FF));
            world.spawnParticle(Particle.ELECTRIC_SPARK, end, 40, 0.3, 0.3, 0.3, 0.4);
            world.spawnParticle(Particle.END_ROD, end, 20, 0.1, 0.1, 0.1, 0.15);
            fx.sound(end, "entity.lightning_bolt.impact", 0.6f, 1.8f);
            if (hit != null && hit.getHitBlockFace() != null) {        // a shockwave ring flat on what it hit
                Vector n = hit.getHitBlockFace().getDirection();
                Location on = end.clone().add(n.clone().multiply(0.03));
                on.setYaw(0);
                on.setPitch(0);
                org.joml.Quaternionf flat = new org.joml.Quaternionf().rotationTo(0, 0, 1, (float) n.getX(), (float) n.getY(), (float) n.getZ());
                ItemDisplay wave = world.spawn(on, ItemDisplay.class, e -> {
                    e.setItemStack(fx.modelled(Material.PAPER, "gadget/fx_circle"));
                    e.setBrightness(new Display.Brightness(15, 15));
                    e.setPersistent(false);
                    e.setTransformationMatrix(new Matrix4f().rotate(flat).scale(0.2f));
                });
                fx.tagged(wave, "beam");
                fx.later(1, () -> {
                    wave.setInterpolationDelay(0);
                    wave.setInterpolationDuration(10);
                    wave.setTransformationMatrix(new Matrix4f().rotate(flat).scale(2.4f));
                });
                fx.later(12, wave::remove);
            }
            fx.later(1, () -> {                                        // the rings ripple outwards, the beam thins away
                for (ItemDisplay ring : rings) {
                    ring.setInterpolationDelay(0);
                    ring.setInterpolationDuration(8);
                    ring.setTransformationMatrix(new Matrix4f().rotate(along).scale(1.3f));
                }
                for (int i = 0; i < 2; i++) {
                    beam[i].setInterpolationDelay(2);
                    beam[i].setInterpolationDuration(8);
                    beam[i].setTransformationMatrix(new Matrix4f().rotate(along).scale(0.001f, 0.001f, (float) length));
                }
            });
            fx.later(11, () -> {
                for (ItemDisplay ring : rings) ring.remove();
                for (BlockDisplay piece : beam) piece.remove();
            });
        });
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
        float cloudSize = 2.8f;                                        // the sprite's cloud sits in its lower part; big enough to hide the arc's feet
        ItemDisplay[] clouds = new ItemDisplay[2];
        for (int i = 0; i < 2; i++) {
            int side = i == 0 ? -1 : 1;
            Location at = base.clone().add(right.clone().multiply(side * 2.35)).add(0, cloudSize / 2 - 0.05, 0);
            clouds[i] = world.spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(fx.modelled(Material.WHITE_WOOL, "gadget/rainbow_cloud"));
                d.setRotation(yaw, 0);                                 // set in the rainbow's own plane: it doesn't turn to follow anyone
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
     * Tetherball, with real physics: the ball is a weight on a rope tied to the top of the pole. It hangs, swings and
     * circles under gravity; a hit (left or right click, or running into it) knocks it round the way it was hit. As it
     * goes round, the rope winds onto the pole (shorter rope, so it spins faster and higher, like the real thing), and
     * going back the other way unwinds it. Wound all the way up, it dings and unwinds itself back down. The rope is a
     * chain of weighted points, so it hangs straight when pulled and droops naturally when slack.
     */
    private void tetherball(Player player) {
        Placed p = place(player);
        Location base = fx.ground(player);
        World world = base.getWorld();
        keep(p, model(base.clone(), "gadget/tetherball_pole", Material.IRON_BARS, 1.4f, 0.7f));
        final double TOP = 2.7, ROPE = 1.95, POLE = 0.1, WRAP = POLE + 0.03, PITCH = 0.19, BALL = 0.3;
        final double SHORTEST = 0.35, PER_RADIAN = 0.07;               // rope wound up per radian: about 3.5 turns in all
        final double MAX_ANGLE = (ROPE - SHORTEST) / PER_RADIAN;
        final double GRAVITY = 0.035;
        final int NODES = 16;                                          // the free rope's weighted points
        final int COILS = (int) Math.ceil(MAX_ANGLE / (Math.PI / 4)) + 1;   // pieces kept just for the coils
        Location top = base.clone().add(0, TOP, 0);
        top.setYaw(0);                                                 // everything below is in plain world directions from here
        top.setPitch(0);
        ItemDisplay ball = keep(p, model(top.clone().add(0, -ROPE - BALL, 0), "gadget/tetherball_3d", Material.YELLOW_WOOL, 0.75f, 0));
        ball.setTeleportDuration(1);
        java.util.function.Supplier<BlockDisplay> cord = () -> keep(p, world.spawn(top, BlockDisplay.class, d -> {
            d.setBlock(Material.WHITE_CONCRETE.createBlockData());
            d.setBrightness(new Display.Brightness(14, 14));
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0), new AxisAngle4f()));
        }));
        // two separate sets of pieces, so a piece never jumps between the coils and the hanging rope
        BlockDisplay[] coil = new BlockDisplay[COILS], hang = new BlockDisplay[NODES + 1];
        for (int i = 0; i < COILS; i++) coil[i] = cord.get();
        for (int i = 0; i <= NODES; i++) hang[i] = cord.get();
        boolean[] coilShown = new boolean[COILS], hangShown = new boolean[NODES + 1];

        // the ball (where it is and where it was: its speed is the difference) and the rope's points, from the top
        double start = Math.toRadians(player.getLocation().getYaw() + 90);
        Vector[] b = {new Vector(Math.cos(start) * 0.5, -ROPE - 0.05, Math.sin(start) * 0.5), null};
        b[1] = b[0].clone();
        Vector[] node = new Vector[NODES], prev = new Vector[NODES];
        for (int k = 0; k < NODES; k++) {
            node[k] = b[0].clone().multiply((k + 1) / (double) (NODES + 1));
            prev[k] = node[k].clone();
        }
        double[] wind = {0, Math.atan2(b[0].getZ(), b[0].getX())};    // how far wound (radians, signed), and the last angle
        int[] reset = {0};
        Map<UUID, Integer> lastHit = new HashMap<>();
        int[] tick = {0};

        java.util.function.BiConsumer<Player, Double> hitBall = (hitter, power) -> {
            if (reset[0] > 0) return;
            Integer last = lastHit.get(hitter.getUniqueId());
            if (last != null && tick[0] - last < 6) return;
            lastHit.put(hitter.getUniqueId(), tick[0]);
            Vector radial = b[0].clone().setY(0);
            if (radial.lengthSquared() < 1e-4) radial = new Vector(1, 0, 0);
            radial.normalize();
            Vector push = hitter.getLocation().getDirection().setY(0);
            double cross = radial.getX() * push.getZ() - radial.getZ() * push.getX();
            Vector tangent = new Vector(-radial.getZ(), 0, radial.getX()).multiply(cross >= 0 ? 1 : -1);
            Vector v = b[0].clone().subtract(b[1]).add(tangent.multiply(power)).add(new Vector(0, 0.06, 0));
            if (v.length() > 0.5) v.multiply(0.5 / v.length());
            b[1] = b[0].clone().subtract(v);                           // (a verlet point's speed is now minus before)
            Location at = top.clone().add(b[0]);
            fx.sound(at, "entity.player.attack.strong", 0.9f, 1.3f);
            fx.sound(at, "block.note_block.basedrum", 0.6f, 1.4f);
            world.spawnParticle(Particle.CRIT, at, 6, 0.15, 0.15, 0.15, 0.1);
        };
        Interaction hit = box(p, top.clone().add(b[0]).add(0, -0.4, 0), 0.8f, 0.8f, null);
        clicks.put(hit.getUniqueId(), hitter -> hitBall.accept(hitter, hitter.isSprinting() ? 0.3 : 0.22));
        fx.sound(base, "block.chain.place", 1f, 1f);

        p.task = fx.every(1200, t -> {
            tick[0] = t;
            // ---- the ball: a weight on a rope, with gravity
            Vector v = b[0].clone().subtract(b[1]).multiply(0.995);
            b[1] = b[0].clone();
            b[0].add(v).add(new Vector(0, -GRAVITY, 0));
            if (reset[0] > 0) {                                        // unwinding itself: turn it back round the pole
                reset[0]--;
                double back = -Math.signum(wind[0]) * Math.min(0.3, Math.abs(wind[0]));
                double c = Math.cos(back), sn = Math.sin(back);
                Vector r = new Vector(b[0].getX() * c - b[0].getZ() * sn, b[0].getY(), b[0].getX() * sn + b[0].getZ() * c);
                b[0] = r;
                b[1] = r.clone().add(new Vector(0, 0.0, 0));
                if (reset[0] == 0) b[1] = b[0].clone();
            }
            // how far round the pole it's gone this tick winds (or unwinds) the rope
            double angle = Math.atan2(b[0].getZ(), b[0].getX());
            double turn = Math.atan2(Math.sin(angle - wind[1]), Math.cos(angle - wind[1]));
            wind[1] = angle;
            wind[0] = Math.max(-MAX_ANGLE, Math.min(MAX_ANGLE, wind[0] + turn));
            double length = ROPE - Math.abs(wind[0]) * PER_RADIAN;
            double drop = Math.abs(wind[0]) / (Math.PI * 2) * PITCH;
            Vector anchor = new Vector(Math.cos(angle) * WRAP, -drop, Math.sin(angle) * WRAP);
            // the rope can't stretch: the ball's centre stays within rope + its radius of where the rope leaves the pole
            Vector off = b[0].clone().subtract(anchor);
            double reach = length + BALL;
            if (off.length() > reach) b[0] = anchor.clone().add(off.multiply(reach / off.length()));
            // and it can't go through the pole or the ground
            double h = Math.hypot(b[0].getX(), b[0].getZ());
            if (h < POLE + BALL) {
                double k = (POLE + BALL) / Math.max(h, 1e-6);
                b[0].setX(h < 1e-6 ? POLE + BALL : b[0].getX() * k);
                b[0].setZ(h < 1e-6 ? 0 : b[0].getZ() * k);
            }
            if (b[0].getY() < -TOP + BALL) b[0].setY(-TOP + BALL);
            if (reset[0] == 0 && Math.abs(wind[0]) >= MAX_ANGLE - 1e-6) {   // all the way up: ding!
                Location at = top.clone().add(b[0]);
                fx.sound(at, "block.note_block.bell", 1f, 1.4f);
                fx.sound(at, "block.anvil.land", 0.3f, 2f);
                world.spawnParticle(Particle.FIREWORK, at, 18, 0.2, 0.2, 0.2, 0.08);
                fx.confetti(at, 20, 0.4);
                reset[0] = 60;
            }
            Location at = top.clone().add(b[0]);
            ball.teleport(at);
            hit.teleport(at.clone().add(0, -0.4, 0));

            // ---- the hanging rope: weighted points, links that go slack but don't stretch, outside the pole
            Vector attach = b[0].clone().add(anchor.clone().subtract(b[0]).normalize().multiply(BALL * 0.9));
            double link = length / (NODES + 1);
            for (int k = 0; k < NODES; k++) {
                Vector nv = node[k].clone().subtract(prev[k]).multiply(0.9);
                prev[k] = node[k].clone();
                node[k].add(nv).add(new Vector(0, -GRAVITY, 0));
            }
            for (int pass = 0; pass < 20; pass++) {
                for (int k = -1; k < NODES; k++) {
                    Vector p0 = k < 0 ? anchor : node[k], p1 = k + 1 < NODES ? node[k + 1] : attach;
                    Vector gap = p1.clone().subtract(p0);
                    double dist = gap.length();
                    if (dist <= link || dist < 1e-6) continue;
                    Vector fix = gap.multiply((dist - link) / dist);
                    if (k >= 0 && k + 1 < NODES) {
                        p0.add(fix.clone().multiply(0.5));
                        p1.subtract(fix.multiply(0.5));
                    } else if (k >= 0) {
                        p0.add(fix);
                    } else if (k + 1 < NODES) {
                        p1.subtract(fix);
                    }
                }
                for (Vector q : node) {
                    double qh = Math.hypot(q.getX(), q.getZ());
                    if (qh < WRAP && q.getY() < 0.05) {
                        double k = WRAP / Math.max(qh, 1e-6);
                        q.setX(qh < 1e-6 ? WRAP : q.getX() * k);
                        q.setZ(qh < 1e-6 ? 0 : q.getZ() * k);
                    }
                    if (q.getY() < -TOP + 0.03) q.setY(-TOP + 0.03);
                }
            }
            List<Vector> line = new ArrayList<>();
            line.add(anchor);
            for (Vector q : node) line.add(q);
            line.add(attach);
            for (int i = 0; i <= NODES; i++) hangShown[i] = drawCord(hang[i], line.get(i), line.get(i + 1), hangShown[i]);

            // ---- the coils on the pole, from the top down to where the rope leaves it
            int pieces = Math.min(COILS, (int) Math.ceil(Math.abs(wind[0]) / (Math.PI / 4)));
            for (int i = 0; i < COILS; i++) {
                if (i >= pieces) {
                    coilShown[i] = drawCord(coil[i], null, null, coilShown[i]);
                    continue;
                }
                double f0 = i / (double) pieces, f1 = (i + 1) / (double) pieces;
                double a0 = angle - wind[0] * (1 - f0), a1 = angle - wind[0] * (1 - f1);
                Vector q0 = new Vector(Math.cos(a0) * WRAP, -drop * f0, Math.sin(a0) * WRAP);
                Vector q1 = new Vector(Math.cos(a1) * WRAP, -drop * f1, Math.sin(a1) * WRAP);
                coilShown[i] = drawCord(coil[i], q0, q1, coilShown[i]);
            }
            // running into it knocks it along too
            for (Player who : world.getPlayers()) {
                if (fx.parkour(who) || who.getLocation().add(0, 1, 0).distanceSquared(at) > 0.6) continue;
                hitBall.accept(who, 0.18);
            }
        }, () -> clear(player.getUniqueId()));
    }

    /**
     * Lays one piece of cord from a to b (positions from the display's own spot), or hides it when a is null. A piece
     * that's just appearing (or disappearing) snaps straight there instead of sliding in from where it last was.
     */
    private static boolean drawCord(BlockDisplay piece, Vector a, Vector b, boolean shown) {
        if (a == null || b == null || a.distanceSquared(b) < 1e-8) {
            if (shown) {
                piece.setInterpolationDuration(0);
                piece.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0), new AxisAngle4f()));
            }
            return false;
        }
        Vector d = b.clone().subtract(a);
        float len = (float) d.length();
        Vector n = d.multiply(1 / len);
        // pointing (nearly) straight down, "turn up to this" has no one answer and can flip about from tick to tick: so
        // a piece is laid upward from its lower end instead, which is the same cord and always well defined
        Vector from = a;
        if (n.getY() < 0) {
            from = b;
            n.multiply(-1);
        }
        piece.setInterpolationDelay(0);
        piece.setInterpolationDuration(shown ? 1 : 0);
        piece.setTransformationMatrix(new Matrix4f().translate((float) from.getX(), (float) from.getY(), (float) from.getZ())
                .rotate(new org.joml.Quaternionf().rotationTo(0, 1, 0, (float) n.getX(), (float) n.getY(), (float) n.getZ()))
                .translate(-0.022f, -0.01f, -0.022f).scale(0.044f, len + 0.02f, 0.044f));
        return true;
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

    private final Set<UUID> pinataUp = new HashSet<>();

    /**
     * The piñata: a wooden stand goes up in front of the player with a striped donkey piñata hanging from it on a rope,
     * and their gadget turns into a baseball bat. Whack it (anyone can join in): it swings away on its rope with a
     * hollow papery THOCK, shedding confetti and the odd sweet, until enough hits burst it open in a shower of candy.
     */
    private void pinata(Player player) {
        float yaw = Math.round(player.getLocation().getYaw() / 90f) * 90f;
        Vector fwd = new Location(player.getWorld(), 0, 0, 0, yaw, 0).getDirection().setY(0).normalize();
        fwd = new Vector(Math.round(fwd.getX()), 0, Math.round(fwd.getZ()));
        Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
        Location feet = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        Location ground = feet.clone().add(fwd.clone().multiply(2));
        for (int u = -1; u <= 1; u++) {                                // room for the stand: 3 wide, 4 high, on a floor
            for (int y = 0; y < 4; y++) {
                Block cell = ground.clone().add(side.clone().multiply(u)).add(0, y, 0).getBlock();
                boolean ok = cell.isPassable() && (y > 0 || cell.getRelative(0, -1, 0).getType().isSolid());
                if (!ok) {
                    plugin.messages().send(player, "collectibles.gadget.pinata-room");
                    fx.sound(player.getLocation(), "entity.villager.no", 0.8f, 1.2f);
                    return;
                }
            }
        }
        Placed p = place(player);
        World world = ground.getWorld();
        pinataUp.add(player.getUniqueId());
        ground.setYaw(yaw);
        ground.setPitch(0);
        ItemDisplay stand = keep(p, world.spawn(ground, ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled(Material.OAK_FENCE, "gadget/pinata_stand"));
            d.setPersistent(false);
            d.setRotation(yaw, 0);
            d.setTransformation(new Transformation(new Vector3f(0, 1f, 0), new AxisAngle4f(), new Vector3f(2f), new AxisAngle4f()));
        }));
        Location hook = ground.clone().add(0, 3.0, 0);
        hook.setYaw(0);
        final float S = 1.6f;                                          // the piñata's size
        final double ROPE = 0.85, GRAVITY = 0.04;
        // the knot on its back, from the model's middle (the model is turned half round inside the display)
        final Vector3f knot = new Vector3f(0.5f / 16 * S, 6f / 16 * S, 0);
        final float turn = (float) Math.atan2(-side.getZ(), side.getX()); // its long side faces the player
        ItemDisplay pinata = keep(p, world.spawn(hook.clone().add(0, -ROPE, 0), ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled(Material.PINK_WOOL, "gadget/pinata_3d"));
            d.setPersistent(false);
            d.setTeleportDuration(1);
        }));
        BlockDisplay rope = keep(p, world.spawn(hook, BlockDisplay.class, d -> {
            d.setBlock(Material.WHITE_CONCRETE.createBlockData());
            d.setBrightness(new Display.Brightness(14, 14));
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0), new AxisAngle4f()));
        }));
        boolean[] ropeShown = {false};
        Interaction box = box(p, hook.clone().add(0, -ROPE - 0.9, 0), 1.3f, 1.0f, null);
        Vector[] b = {new Vector(0, -ROPE, 0), new Vector(0, -ROPE, 0)}; // the knot: where it is, where it was
        int[] hits = {0};
        int needed = 8 + ThreadLocalRandom.current().nextInt(5);
        boolean[] burst = {false};
        Map<UUID, Integer> lastHit = new HashMap<>();
        int[] tick = {0};

        // the gadget becomes a bat while the piñata's up
        ItemStack[] lent = {null};
        org.bukkit.NamespacedKey[] oldModel = {null};
        Component[] oldName = {null};
        for (ItemStack it : player.getInventory().getContents()) {
            if (it == null || !it.hasItemMeta()) continue;
            String what = it.getItemMeta().getPersistentDataContainer().get(fx.itemKey(), org.bukkit.persistence.PersistentDataType.STRING);
            if (!"gadget:pinata".equals(what)) continue;
            lent[0] = it;
            oldModel[0] = it.getItemMeta().getItemModel();
            oldName[0] = it.getItemMeta().displayName();
            it.editMeta(meta -> {
                meta.setItemModel(new org.bukkit.NamespacedKey("fractal", "gadget/pinata_bat"));
                meta.displayName(text("Piñata Bat", 0xFFD27F).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            });
            break;
        }
        p.end = () -> {
            pinataUp.remove(player.getUniqueId());
            for (ItemStack it : player.getInventory().getContents()) {  // and back to the piñata when it's done
                if (it == null || !it.hasItemMeta()) continue;
                String what = it.getItemMeta().getPersistentDataContainer().get(fx.itemKey(), org.bukkit.persistence.PersistentDataType.STRING);
                if (!"gadget:pinata".equals(what)) continue;
                it.editMeta(meta -> {
                    meta.setItemModel(oldModel[0]);
                    meta.displayName(oldName[0]);
                });
            }
        };

        Runnable pop = () -> {                                         // BURST!
            burst[0] = true;
            Location at = hook.clone().add(b[0]).add(0, -0.4, 0);
            pinata.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0), new AxisAngle4f()));
            box.remove();
            fx.sound(at, "fractal:gadget.pinata_burst", 1f, 1f);
            fx.sound(at, "entity.player.levelup", 0.6f, 1.6f);
            world.spawnParticle(Particle.FIREWORK, at, 40, 0.3, 0.3, 0.3, 0.2);
            fx.confetti(at, 110, 1.2);
            for (Material candy : new Material[]{Material.COOKIE, Material.SWEET_BERRIES, Material.GLOW_BERRIES, Material.CAKE,
                    Material.GOLDEN_CARROT, Material.HONEY_BOTTLE, Material.PUMPKIN_PIE}) {
                fx.burstItems(at, new ItemStack(candy), 3, 0.35, 60);
            }
            // the piñata in bits: bright scraps of papier-mache tumbling down
            Material[] scraps = {Material.PINK_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.ORANGE_CONCRETE};
            for (int i = 0; i < 12; i++) {
                Material m = scraps[i % scraps.length];
                ThreadLocalRandom r = ThreadLocalRandom.current();
                Vector vel = new Vector(r.nextDouble(-0.18, 0.18), r.nextDouble(0.08, 0.3), r.nextDouble(-0.18, 0.18));
                float size = (float) r.nextDouble(0.12, 0.22);
                BlockDisplay scrap = keep(p, world.spawn(at, BlockDisplay.class, d -> {
                    d.setBlock(m.createBlockData());
                    d.setPersistent(false);
                    d.setTeleportDuration(1);
                    d.setTransformation(new Transformation(new Vector3f(-size / 2), new AxisAngle4f(), new Vector3f(size, size * 0.4f, size), new AxisAngle4f()));
                }));
                Location[] where = {at.clone()};
                fx.every(40, t -> {
                    if (!scrap.isValid()) return;
                    vel.setY(vel.getY() - 0.04);
                    Location next = where[0].clone().add(vel);
                    if (!next.getBlock().isPassable()) {
                        vel.multiply(0);
                        return;
                    }
                    where[0] = next;
                    next.setYaw(t * 25f);
                    scrap.teleport(next);
                }, scrap::remove);
            }
            plugin.messages().send(player, "collectibles.gadget.pinata-burst");
            fx.later(70, () -> clear(player.getUniqueId()));
        };

        clicks.put(box.getUniqueId(), hitter -> {
            if (burst[0]) return;
            Integer last = lastHit.get(hitter.getUniqueId());
            if (last != null && tick[0] - last < 5) return;
            lastHit.put(hitter.getUniqueId(), tick[0]);
            boolean bat = hitter.equals(player);                       // the bat hits harder than a fist
            Vector push = hitter.getLocation().getDirection().setY(0).normalize().multiply(bat ? 0.22 : 0.14).add(new Vector(0, 0.05, 0));
            b[1] = b[0].clone().subtract(b[0].clone().subtract(b[1]).add(push));
            hits[0] += bat ? 1 : 0;
            if (!bat && ThreadLocalRandom.current().nextBoolean()) hits[0]++;
            Location at = hook.clone().add(b[0]).add(0, -0.4, 0);
            fx.sound(at, "fractal:gadget.pinata_whack", 1f, (float) ThreadLocalRandom.current().nextDouble(0.9, 1.12));
            world.spawnParticle(Particle.CRIT, at, 8, 0.25, 0.2, 0.25, 0.15);
            fx.confetti(at, 8, 0.3);
            if (ThreadLocalRandom.current().nextInt(3) == 0) {
                fx.burstItems(at, new ItemStack(ThreadLocalRandom.current().nextBoolean() ? Material.COOKIE : Material.SWEET_BERRIES), 1, 0.2, 40);
            }
            if (hits[0] >= needed) pop.run();
        });
        fx.sound(ground, "block.wood.place", 1f, 0.9f);
        fx.sound(ground, "block.note_block.bell", 0.6f, 1.5f);

        p.task = fx.every(1200, t -> {
            tick[0] = t;
            if (!player.isOnline() || player.getWorld() != world || player.getLocation().distanceSquared(ground) > 20 * 20) {
                clear(player.getUniqueId());
                return;
            }
            if (burst[0]) {
                if (ropeShown[0]) {                                    // just a short frayed end left on the hook
                    rope.setInterpolationDuration(0);
                    drawCord(rope, new Vector(0, 0, 0), new Vector(0.001, -0.3, 0), false);
                    ropeShown[0] = false;
                }
                return;
            }
            // swinging on its rope: gravity, a little air, and the rope keeping it at length
            Vector v = b[0].clone().subtract(b[1]).multiply(0.985);
            b[1] = b[0].clone();
            b[0].add(v).add(new Vector(0, -GRAVITY, 0));
            if (b[0].length() > ROPE) b[0].multiply(ROPE / b[0].length());
            // it can swing out a long way, but never up level with (or through) the crossbar it hangs from
            double lowest = -Math.cos(Math.toRadians(65)) * ROPE;
            if (b[0].getY() > lowest) {
                Vector flat = b[0].clone().setY(0);
                double reach = Math.sin(Math.toRadians(65)) * ROPE;
                b[0] = (flat.lengthSquared() < 1e-6 ? new Vector(0, 0, 0) : flat.normalize().multiply(reach)).setY(lowest);
                b[1] = b[0].clone().subtract(b[0].clone().subtract(b[1]).multiply(0.3));   // and it loses its swing
            }
            Vector down = b[0].clone().normalize();
            Location knotAt = hook.clone().add(b[0]);
            pinata.teleport(knotAt);
            pinata.setInterpolationDelay(0);
            pinata.setInterpolationDuration(1);
            // tip it along the rope, turn it side-on, then hang it from its knot
            pinata.setTransformationMatrix(new Matrix4f()
                    .rotate(new org.joml.Quaternionf().rotationTo(0, -1, 0, (float) down.getX(), (float) down.getY(), (float) down.getZ()))
                    .rotateY(turn).translate(-knot.x, -knot.y, -knot.z).scale(S));
            box.teleport(knotAt.clone().add(0, -0.9, 0));
            ropeShown[0] = drawCord(rope, new Vector(0, 0, 0), b[0], ropeShown[0]);
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

    /**
     * The swing: a wooden frame with a red seat hanging from the crossbar on two ropes that are always tied on. Sit on
     * it (click the seat) and it swings forward and back like a real pendulum, building up to a good height; empty, it
     * just sways a little. Sneak to get off.
     */
    private void swing(Player player) {
        Placed p = place(player);
        Location base = fx.ground(player);
        World world = base.getWorld();
        float yaw = Math.round(player.getLocation().getYaw() / 90f) * 90f;
        Vector fwd = new Location(world, 0, 0, 0, yaw, 0).getDirection().setY(0).normalize();
        Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
        Location frameAt = base.clone().add(0, 0.5, 0);
        keep(p, world.spawn(frameAt, ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled(Material.OAK_FENCE, "gadget/swing_frame"));
            d.setRotation(yaw, 0);
            d.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(), new Vector3f(2f), new AxisAngle4f()));
        }));
        // the crossbar's underside (model y 30 at scale 2, on a frame whose middle is 1 block up) and the rope length
        final double BAR = 3.75, SEAT = 0.55, ROPE = BAR - SEAT, TIE = 0.44;
        Location pivot = base.clone().add(0, BAR, 0);
        pivot.setYaw(0);
        pivot.setPitch(0);
        final float turn = (float) Math.atan2(-side.getZ(), side.getX());
        ItemDisplay seat = keep(p, world.spawn(base.clone().add(0, SEAT, 0), ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled(Material.RED_CARPET, "gadget/swing_seat"));
            d.setPersistent(false);
            d.setTeleportDuration(1);
        }));
        BlockDisplay[] ropes = new BlockDisplay[2];
        boolean[] shown = {false, false};
        for (int i = 0; i < 2; i++) {
            ropes[i] = keep(p, world.spawn(pivot, BlockDisplay.class, d -> {
                d.setBlock(Material.WHITE_CONCRETE.createBlockData());
                d.setBrightness(new Display.Brightness(14, 14));
                d.setPersistent(false);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0), new AxisAngle4f()));
            }));
        }
        Interaction sit = box(p, base.clone().add(0, 0.2, 0), 1.0f, 0.7f, rider -> {
            if (fx.parkour(rider) || !seat.getPassengers().isEmpty()) return;
            seat.addPassenger(rider);
            plugin.messages().send(rider, "collectibles.gadget.swing-sit");
        });
        fx.sound(base, "block.wood.place", 1f, 0.8f);
        double omega = Math.sqrt(0.08 / ROPE);                         // a real pendulum's pace for this rope
        double[] amp = {0.06}, phase = {0};
        p.task = fx.every(1200, t -> {
            boolean riding = !seat.getPassengers().isEmpty();
            amp[0] += riding ? (0.8 - amp[0]) * 0.02 : (0.06 - amp[0]) * 0.03;   // pumps up with a rider, settles when empty
            phase[0] += omega;
            double angle = Math.sin(phase[0]) * amp[0];
            Vector down = fwd.clone().multiply(Math.sin(angle)).add(new Vector(0, -Math.cos(angle), 0));
            Location at = pivot.clone().add(down.clone().multiply(ROPE));
            seat.teleport(at, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
            seat.setInterpolationDelay(0);
            seat.setInterpolationDuration(1);
            // tipped along the ropes, plank across the frame (the plank sits 0.94 below the model's middle at size 2)
            seat.setTransformationMatrix(new Matrix4f()
                    .rotate(new org.joml.Quaternionf().rotationTo(0, -1, 0, (float) down.getX(), (float) down.getY(), (float) down.getZ()))
                    .rotateY(turn).translate(0, 0.94f, 0).scale(2f));
            for (int i = 0; i < 2; i++) {                              // the ropes: from the bar to each end of the seat
                Vector top = side.clone().multiply(i == 0 ? -TIE : TIE);
                shown[i] = drawCord(ropes[i], top, top.clone().add(down.clone().multiply(ROPE)), shown[i]);
            }
            sit.teleport(at.clone().add(0, -0.35, 0));
            if (riding && t % 40 == 0) fx.sound(pivot, "block.wood.step", 0.3f, 0.7f);    // a creak now and then
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
    /**
     * Tic tac toe, like Pong: the player is X against the Lobby (O), until someone else walks up to the board; then
     * they play O instead, and the Lobby takes back over if they walk off. Click a square to play it. The score floats
     * over the board; three in a row wins it. A draw replays with the letters swapped (X always goes first, so the starter alternates). The Lobby plays
     * sensibly (it takes a win, usually blocks yours) but can be beaten.
     */
    private void ticTacToe(Player player) {
        Placed p = place(player);
        World world = player.getWorld();
        float yaw = Math.round(player.getLocation().getYaw() / 90f) * 90f;
        Vector fwd = new Location(world, 0, 0, 0, yaw, 0).getDirection().setY(0).normalize();
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
        Location base = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5).add(fwd.clone().multiply(3));
        base.setYaw(0);
        base.setPitch(0);
        // the board stands up in the air facing the player, its middle 1.8 up, with the score floating just above it
        Location centre = base.clone().add(0, 1.8, 0);
        org.joml.Quaternionf face = new org.joml.Quaternionf().rotationTo(0, 1, 0, (float) -fwd.getX(), 0, (float) -fwd.getZ());
        org.joml.Quaternionf back = new org.joml.Quaternionf().rotationTo(0, 1, 0, (float) fwd.getX(), 0, (float) fwd.getZ());
        // stands a flat (lying) model up to face the player: its top turned towards them, its middle at the spot
        java.util.function.BiFunction<Location, Object[], ItemDisplay> upright = (spot, m) -> keep(p, world.spawn(spot, ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled((Material) m[1], (String) m[0]));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
            float size = (Float) m[2], mid = (Float) m[3];             // mid: the model's middle height, in model units
            boolean rear = m.length > 4 && (Boolean) m[4];               // the copy on the back, facing the other way
            d.setTransformationMatrix(new Matrix4f().rotate(rear ? back : face).translate(0, (8 - mid) / 16f * size, 0).scale(size));
        }));
        // the board is two boards back to back, so it (and every mark) reads from either side
        upright.apply(centre.clone(), new Object[]{"gadget/ttt_board", Material.OAK_PRESSURE_PLATE, 3f, 0.4f});
        upright.apply(centre.clone().add(fwd.clone().multiply(0.12)), new Object[]{"gadget/ttt_board", Material.OAK_PRESSURE_PLATE, 3f, 0.4f, true});
        char[] cells = new char[9];
        List<Entity> marks = new ArrayList<>();
        int[] turn = {0}, pts = {0, 0}, think = {0};
        char[] mine = {'X'};                                           // the player's letter; it swaps after a tie
        boolean[] over = {false}, roundOver = {false};
        Player[] rival = {null};
        TextDisplay score = keep(p, label(centre.clone().add(0, 1.75, 0), Component.empty(), 0));
        int[][] lines = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};
        // square i: columns left to right and rows top to bottom as the player sees it, a hair in front of the board
        java.util.function.IntFunction<Location> cellAt = i -> centre.clone().add(right.clone().multiply(((i % 3) - 1) * 0.98))
                .add(0, (1 - (i / 3)) * 0.98, 0).add(fwd.clone().multiply(-0.06));
        Runnable show = () -> {
            String o = rival[0] == null ? "Lobby" : rival[0].getName();
            String whose = roundOver[0] ? "" : turn[0] == 0 ? player.getName() + "'s turn" : (rival[0] == null ? "The Lobby is thinking..." : o + "'s turn");
            char theirs = mine[0] == 'X' ? 'O' : 'X';
            score.text(text(player.getName() + " (" + mine[0] + ")  ", mine[0] == 'X' ? 0xE03A3A : 0x3C6AE0).append(text("vs", 0xFFFFFF))
                    .append(text("  " + o + " (" + theirs + ")", theirs == 'X' ? 0xE03A3A : 0x3C6AE0)).append(Component.newline()).append(text(whose, 0xA9A3C2))
                    .append(Component.newline()).append(text(rival[0] == null ? "Walk up to the board to play O!" : "Three in a row wins", 0x7A7F96)));
        };
        Runnable newRound = () -> {
            for (Entity e : marks) e.remove();
            marks.clear();
            java.util.Arrays.fill(cells, (char) 0);
            roundOver[0] = false;
            turn[0] = mine[0] == 'X' ? 0 : 1;                          // X always goes first
            think[0] = 0;
            show.run();
        };
        java.util.function.IntConsumer play = cell -> {               // put the mover's mark down and see what happens
            char mark = turn[0] == 0 ? mine[0] : (mine[0] == 'X' ? 'O' : 'X');
            cells[cell] = mark;
            Location at = cellAt.apply(cell);
            marks.add(upright.apply(at.clone().add(fwd.clone().multiply(-0.04)), new Object[]{mark == 'X' ? "gadget/ttt_x" : "gadget/ttt_o",
                    mark == 'X' ? Material.RED_CARPET : Material.BLUE_CARPET, 0.85f, 0.5f}));
            marks.add(upright.apply(at.clone().add(fwd.clone().multiply(0.3)), new Object[]{mark == 'X' ? "gadget/ttt_x" : "gadget/ttt_o",
                    mark == 'X' ? Material.RED_CARPET : Material.BLUE_CARPET, 0.85f, 0.5f, true}));
            fx.sound(at, "block.wood.place", 1f, mark == 'X' ? 1.2f : 0.9f);
            for (int[] line : lines) {
                if (cells[line[0]] == mark && cells[line[1]] == mark && cells[line[2]] == mark) {
                    int winner = turn[0];
                    pts[winner]++;
                    roundOver[0] = true;
                    for (int k : line) world.spawnParticle(Particle.FIREWORK, cellAt.apply(k), 8, 0.2, 0.2, 0.2, 0.05);
                    fx.sound(base, winner == 0 ? "entity.player.levelup" : "block.note_block.bass", 0.8f, winner == 0 ? 1.6f : 0.8f);
                    show.run();
                    if (pts[winner] >= 1) {                            // one win and it's done (a draw replays)
                        over[0] = true;
                        String name = winner == 0 ? player.getName() : rival[0] == null ? "The Lobby" : rival[0].getName();
                        score.text(text(name + " wins!", mark == 'X' ? 0xE03A3A : 0x3C6AE0));
                        fx.confetti(centre.clone(), 60, 1);
                        fx.sound(base, "ui.toast.challenge_complete", 0.8f, 1.2f);
                        fx.later(80, () -> clear(player.getUniqueId()));
                    } else {
                        fx.later(40, newRound);
                    }
                    return;
                }
            }
            boolean full = true;
            for (char c : cells) if (c == 0) full = false;
            if (full) {                                                // a draw: swap letters and play it again
                roundOver[0] = true;
                mine[0] = mine[0] == 'X' ? 'O' : 'X';
                fx.sound(base, "entity.villager.no", 0.8f, 1f);
                show.run();
                fx.later(30, newRound);
                return;
            }
            turn[0] = 1 - turn[0];
            think[0] = 0;
            show.run();
        };
        for (int i = 0; i < 9; i++) {
            int cell = i;
            box(p, cellAt.apply(i).add(0, -0.45, 0), 0.9f, 0.9f, clicker -> {
                if (over[0] || roundOver[0] || cells[cell] != 0) return;
                Player mover = turn[0] == 0 ? player : rival[0];
                if (mover == null || !mover.equals(clicker)) return;   // not their turn (or the Lobby's): nothing happens
                play.accept(cell);
            });
        }
        fx.sound(base, "block.wood.place", 1f, 0.8f);
        newRound.run();
        p.task = fx.every(3600, t -> {
            if (over[0]) return;
            if (!player.isOnline() || player.getWorld() != world || player.getLocation().distanceSquared(base) > 9 * 9) {
                clear(player.getUniqueId());
                return;
            }
            // someone walking up to the board takes O; walking off hands it back to the Lobby
            if (rival[0] != null && (!rival[0].isOnline() || rival[0].getWorld() != world || rival[0].getLocation().distanceSquared(base) > 7 * 7)) {
                rival[0] = null;
                show.run();
            } else if (rival[0] == null) {
                for (Player other : world.getPlayers()) {
                    if (other.equals(player) || fx.parkour(other) || other.getLocation().distanceSquared(base) > 3.2 * 3.2) continue;
                    rival[0] = other;
                    fx.sound(other.getLocation(), "block.note_block.bit", 1f, 2f);
                    think[0] = 0;
                    show.run();
                    break;
                }
            }
            // the Lobby's move: after a little think
            if (rival[0] == null && turn[0] == 1 && !roundOver[0] && ++think[0] >= 20) {
                play.accept(lobbyMove(cells, lines, mine[0] == 'X' ? 'O' : 'X', mine[0]));
            }
        }, () -> clear(player.getUniqueId()));
    }

    /** The Lobby's tic tac toe move: win if it can, usually block, else the middle, a corner, or anywhere. */
    private static int lobbyMove(char[] cells, int[][] lines, char own, char yours) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (char want : new char[]{own, yours}) {
            if (want == yours && r.nextDouble() < 0.25) continue;    // sometimes it misses your threat
            for (int[] line : lines) {
                int count = 0, empty = -1;
                for (int k : line) {
                    if (cells[k] == want) count++;
                    else if (cells[k] == 0) empty = k;
                }
                if (count == 2 && empty >= 0) return empty;
            }
        }
        if (cells[4] == 0 && r.nextDouble() < 0.7) return 4;
        List<Integer> free = new ArrayList<>(), corners = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (cells[i] != 0) continue;
            free.add(i);
            if (i == 0 || i == 2 || i == 6 || i == 8) corners.add(i);
        }
        if (!corners.isEmpty() && r.nextBoolean()) return corners.get(r.nextInt(corners.size()));
        return free.get(r.nextInt(free.size()));
    }

    /**
     * The DJ booth: a booth with decks a few blocks in front, and a light-up dance floor (5 by 5) laid out under the
     * player in front of it. A bouncy beat plays (bass on the beat, snare, hats, a melody), the floor's tiles flash
     * new colours on every beat in moving patterns, notes float off the decks and light beams sweep round.
     */
    private void djBooth(Player player) {
        Placed p = place(player);
        World world = player.getWorld();
        float yaw = Math.round(player.getLocation().getYaw() / 90f) * 90f;
        Vector fwd = new Location(world, 0, 0, 0, yaw, 0).getDirection().setY(0).normalize();
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
        Location feet = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        Location base = feet.clone().add(fwd.clone().multiply(3.5));
        base.setYaw(0);
        base.setPitch(0);
        keep(p, world.spawn(base.clone().add(0, 0.5, 0), ItemDisplay.class, d -> {
            d.setItemStack(fx.modelled(Material.JUKEBOX, "gadget/dj_booth_3d"));
            d.setRotation(yaw + 180, 0);
            d.setTransformation(new Transformation(new Vector3f(0, 0.25f, 0), new AxisAngle4f(), new Vector3f(1.6f), new AxisAngle4f()));
        }));
        // the dance floor: 5 by 5 tiles centred just in front of the player, over whatever's on the ground
        Material[] lights = {Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.LIME_CONCRETE, Material.YELLOW_CONCRETE,
                Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE, Material.PINK_CONCRETE, Material.CYAN_CONCRETE};
        Location floorMid = feet.clone().add(fwd.clone().multiply(0.5));
        BlockDisplay[][] tiles = new BlockDisplay[5][5];
        for (int u = 0; u < 5; u++) {
            for (int v = 0; v < 5; v++) {
                Location at = floorMid.clone().add(right.clone().multiply(u - 2)).add(fwd.clone().multiply(v - 2));
                at.setYaw(0);
                at.setPitch(0);
                int uu = u, vv = v;
                tiles[u][v] = keep(p, world.spawn(at, BlockDisplay.class, d -> {
                    d.setBlock(lights[(uu + vv) % lights.length].createBlockData());
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setPersistent(false);
                    d.setTransformation(new Transformation(new Vector3f(-0.47f, 0.01f, -0.47f), new AxisAngle4f(), new Vector3f(0.94f, 0.03f, 0.94f), new AxisAngle4f()));
                }));
            }
        }
        fx.sound(base, "block.note_block.chime", 1f, 1.5f);
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
                world.spawnParticle(Particle.NOTE, top.clone().add(fx.rand(0.6), 0.4, fx.rand(0.6)), 0, note / 24.0, 0, 0, 1);
            }
            if (t % 8 == 0) {                                          // the floor: a new pattern on every beat
                int beat = t / 8, mode = (beat / 8) % 4;
                for (int u = 0; u < 5; u++) {
                    for (int v = 0; v < 5; v++) {
                        int pick = switch (mode) {
                            case 0 -> (u + v + beat) % lights.length;                                // diagonal stripes, moving
                            case 1 -> (Math.max(Math.abs(u - 2), Math.abs(v - 2)) + beat) % lights.length;   // rings from the middle
                            case 2 -> ((u + v) % 2 == beat % 2) ? beat % lights.length : (beat + 4) % lights.length;   // checkerboard
                            default -> ThreadLocalRandom.current().nextInt(lights.length);          // sparkle
                        };
                        tiles[u][v].setBlock(lights[pick].createBlockData());
                    }
                }
                for (int k = 0; k < 6; k++) {                          // light beams sweeping round
                    double a = t * 0.1 + k * Math.PI / 3;
                    Location beam = floorMid.clone().add(Math.cos(a) * 2.2, 0.3, Math.sin(a) * 2.2);
                    world.spawnParticle(Particle.DUST, beam, 4, 0.1, 0.6, 0.1, 0, new Particle.DustOptions(fx.party(), 1.3f));
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
