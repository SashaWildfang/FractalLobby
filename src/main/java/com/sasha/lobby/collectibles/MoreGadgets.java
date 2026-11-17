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

import java.util.ArrayList;
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
            case "hot_potato" -> hotPotato(player);
            case "magic_8_ball" -> {
                String[] answers = {"It is certain.", "Without a doubt!", "Yes, definitely.", "Ask again later...", "Cannot predict now.",
                        "Don't count on it.", "My sources say no.", "Very doubtful.", "Outlook good!", "Signs point to yes!"};
                String answer = answers[ThreadLocalRandom.current().nextInt(answers.length)];
                TextDisplay d = label(player.getLocation().add(0, 2.6, 0), text(answer, 0xE2D0FF), 60);
                player.addPassenger(d);
                world.spawnParticle(Particle.WITCH, eye, 15, 0.3, 0.3, 0.3, 0.05);
                fx.sound(eye, "block.amethyst_block.chime", 1f, 0.8f);
            }
            case "fortune_cookie" -> {
                String[] fortunes = {"A big win is in your future.", "Today is a lucky day.", "Someone will GG you soon.",
                        "You will find something legendary.", "A friend is closer than you think.", "Adventure awaits on another server.",
                        "Your next box holds a surprise.", "Patience brings great dust."};
                String fortune = fortunes[ThreadLocalRandom.current().nextInt(fortunes.length)];
                TextDisplay d = label(player.getLocation().add(0, 2.6, 0), text("\"" + fortune + "\"", 0xFFE8A0), 70);
                player.addPassenger(d);
                world.spawnParticle(Particle.ITEM, eye.clone().add(look.clone().multiply(0.6)), 14, 0.15, 0.15, 0.15, 0.08,
                        new ItemStack(Material.COOKIE));
                fx.sound(eye, "entity.generic.eat", 1f, 1.2f);
            }
            case "tetherball" -> tetherball(player);
            case "rock_paper_shears" -> rockPaperShears(player);
            case "catapult" -> {
                fx.sound(eye, "entity.cat.ambient", 1f, 1.2f);
                for (int i = 0; i < 3; i++) {
                    int n = i;
                    fx.later(i * 4, () -> launch(player, Cat.class, 1.3, "entity.cat.hiss", e -> {
                        Cat.Type[] types = {Cat.Type.TABBY, Cat.Type.BLACK, Cat.Type.RED, Cat.Type.SIAMESE, Cat.Type.CALICO};
                        ((Cat) e).setCatType(types[(n + ThreadLocalRandom.current().nextInt(5)) % types.length]);
                    }));
                }
            }
            case "pigs_fly" -> pigsFly(player);
            case "creeper_astronaut" -> creeperAstronaut(player);
            case "exploding_sheep" -> explodingSheep(player);
            case "duckling" -> ducklings(player);
            case "pinata" -> pinata(player);
            case "jetpack" -> jetpack(player);
            case "mob_gun" -> {
                EntityType[] mobs = {EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN, EntityType.RABBIT};
                EntityType type = mobs[ThreadLocalRandom.current().nextInt(mobs.length)];
                fx.sound(eye, "entity.chicken.egg", 1f, 0.6f);
                launch(player, type.getEntityClass().asSubclass(LivingEntity.class), 1.6, null, e -> {
                    if (e instanceof Ageable baby) baby.setBaby();
                });
            }
            case "railgun" -> railgun(player);
            case "explosive_bow" -> {
                Arrow arrow = player.launchProjectile(Arrow.class, look.clone().multiply(2.6));
                arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                arrow.setCritical(true);
                fx.tagged(arrow, "boom");
                fx.sound(eye, "entity.arrow.shoot", 1f, 0.8f);
            }
            case "pong" -> bouncyBall(player, "gadget/pong_ball", Material.SLIME_BALL, 0.5f, 0.55, "block.note_block.pling");
            case "beach_ball" -> bouncyBall(player, "gadget/beach_ball_3d", Material.SNOWBALL, 1.1f, 0.42, "entity.slime.jump_small");
            case "rush_pearl" -> {
                if (fx.parkour(player)) return true;
                EnderPearl pearl = player.launchProjectile(EnderPearl.class, look.clone().multiply(1.7));
                fx.tagged(pearl, "rush");
                pearl.addPassenger(player);
                fx.sound(eye, "entity.ender_pearl.throw", 1f, 0.8f);
            }
            case "diamond_shower" -> {
                fx.sound(eye, "entity.player.levelup", 0.8f, 1.6f);
                fx.every(40, t -> {
                    if (t % 2 == 0) fx.burstItems(player.getLocation().add(0, 2.4, 0), new ItemStack(Material.DIAMOND), 2, 0.22, 40);
                    if (t % 8 == 0) fx.sound(player.getLocation(), "block.amethyst_block.chime", 0.6f, 1.4f + t * 0.01f);
                }, null);
            }
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

    private void pop(Entity e, String sound) {
        if (!e.isValid()) return;
        Location at = e.getLocation().add(0, 0.4, 0);
        at.getWorld().spawnParticle(Particle.POOF, at, 12, 0.2, 0.2, 0.2, 0.03);
        fx.confetti(at, 15, 0.4);
        fx.sound(at, sound != null ? sound : "entity.chicken.egg", 0.8f, 1.2f);
        e.remove();
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

    /** A ball anyone can hit (right-click it): it flies, bounces off walls and the ground, and slows down. */
    private void bouncyBall(Player player, String modelPath, Material fallback, float scale, double bounciness, String hitSound) {
        Placed p = place(player);
        Location at = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1.5));
        ItemDisplay ball = keep(p, model(at, modelPath, fallback, scale, 0));
        Interaction hitbox = box(p, at.clone().add(0, -scale / 2, 0), scale + 0.3f, scale + 0.3f, null);
        Vector[] velocity = {player.getEyeLocation().getDirection().multiply(0.35)};
        clicks.put(hitbox.getUniqueId(), hitter -> {
            velocity[0] = hitter.getEyeLocation().getDirection().multiply(0.5).add(new Vector(0, 0.25, 0));
            fx.sound(ball.getLocation(), hitSound, 1f, 1.2f);
            ball.getWorld().spawnParticle(Particle.CRIT, ball.getLocation(), 8, 0.2, 0.2, 0.2, 0.2);
        });
        float[] spin = {0};
        p.task = fx.every(600, t -> {
            Location pos = ball.getLocation();
            Vector v = velocity[0];
            v.setY(v.getY() - 0.04);
            Location next = pos.clone().add(v);
            Block block = next.getBlock();
            if (!block.isPassable()) {
                // bounce off whichever way we hit it
                if (!pos.clone().add(v.getX(), 0, 0).getBlock().isPassable()) v.setX(-v.getX() * bounciness * 1.4);
                if (!pos.clone().add(0, v.getY(), 0).getBlock().isPassable()) {
                    v.setY(Math.abs(v.getY()) > 0.12 ? -v.getY() * bounciness * 1.5 : 0);
                    v.setX(v.getX() * 0.9);
                    v.setZ(v.getZ() * 0.9);
                }
                if (!pos.clone().add(0, 0, v.getZ()).getBlock().isPassable()) v.setZ(-v.getZ() * bounciness * 1.4);
                next = pos.clone().add(v);
                if (!next.getBlock().isPassable()) next = pos;
                if (v.length() > 0.15) fx.sound(pos, "entity.slime.squish_small", 0.5f, 1.4f);
            }
            v.setX(v.getX() * 0.97).setZ(v.getZ() * 0.97);
            ball.teleport(next);
            hitbox.teleport(next.clone().add(0, -scale / 2, 0));
            spin[0] += (float) (v.length() * 0.6);
            ball.setInterpolationDelay(0);
            ball.setInterpolationDuration(2);
            ball.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(spin[0], 1, 0, 0), new Vector3f(scale), new AxisAngle4f()));
            // bumping into it hits it too
            for (Player near : next.getWorld().getPlayers()) {
                if (near.getLocation().add(0, 1, 0).distanceSquared(next) < 0.8 && v.lengthSquared() < 0.09) {
                    velocity[0] = near.getLocation().getDirection().setY(0).normalize().multiply(0.35).setY(0.25);
                }
            }
        }, () -> clear(player.getUniqueId()));
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

    private void rainbow(Player player) {
        Location base = player.getLocation();
        Vector side = new Vector(-base.getDirection().getZ(), 0, base.getDirection().getX());
        if (side.lengthSquared() < 0.01) side = new Vector(1, 0, 0);
        side.normalize();
        Location centre = base.clone().add(base.getDirection().setY(0).normalize().multiply(4));
        Color[] bands = {Color.fromRGB(0xFF5A5A), Color.fromRGB(0xFF9A2E), Color.fromRGB(0xFFE14D), Color.fromRGB(0x5BE37C),
                Color.fromRGB(0x5B9DFF), Color.fromRGB(0x9B5CFF)};
        Vector across = side.clone();
        fx.sound(centre, "block.amethyst_block.resonate", 1f, 1.4f);
        fx.every(100, t -> {
            if (t % 3 != 0) return;
            double grow = Math.min(1, t / 25.0);
            for (int b = 0; b < bands.length; b++) {
                double r = 3.6 - b * 0.28;
                for (int k = 0; k <= 28 * grow; k++) {
                    double a = Math.PI * k / 28;
                    Location at = centre.clone().add(across.clone().multiply(Math.cos(a) * r)).add(0, Math.sin(a) * r, 0);
                    centre.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(bands[b], 1.1f));
                }
            }
            for (int k = -1; k <= 1; k += 2) centre.getWorld().spawnParticle(Particle.CLOUD,
                    centre.clone().add(across.clone().multiply(k * 3.4)), 3, 0.3, 0.1, 0.3, 0.01);
        }, null);
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

    private void tetherball(Player player) {
        Placed p = place(player);
        Location base = fx.ground(player);
        keep(p, model(base.clone(), "gadget/tetherball_pole", Material.IRON_BARS, 1.4f, 1.0f));
        Location top = base.clone().add(0, 1.95, 0);
        ItemDisplay ball = keep(p, model(top, "gadget/ball_orange", Material.ORANGE_WOOL, 0.6f, 0));
        double[] angle = {0}, speed = {0}, length = {1.1};
        Interaction hit = box(p, top.clone(), 0.7f, 0.7f, null);
        clicks.put(hit.getUniqueId(), hitter -> {
            Vector toBall = ball.getLocation().toVector().subtract(base.toVector()).setY(0);
            Vector push = hitter.getLocation().getDirection().setY(0);
            double cross = toBall.getX() * push.getZ() - toBall.getZ() * push.getX();
            speed[0] += (cross >= 0 ? 1 : -1) * 0.35;
            fx.sound(ball.getLocation(), "entity.player.attack.strong", 0.8f, 1.4f);
        });
        fx.sound(base, "block.chain.place", 1f, 1f);
        p.task = fx.every(900, t -> {
            angle[0] += speed[0];
            speed[0] *= 0.985;
            double spinning = Math.min(1, Math.abs(speed[0]) / 0.35);
            length[0] = Math.max(0.25, length[0] - Math.abs(speed[0]) * 0.004);   // winds up around the pole
            double r = length[0] * (0.25 + spinning * 0.75);
            double y = 1.95 - (1 - spinning) * length[0] * 0.9;
            Location at = base.clone().add(Math.cos(angle[0]) * r, y, Math.sin(angle[0]) * r);
            ball.teleport(at);
            hit.teleport(at.clone().add(0, -0.35, 0));
            if (t % 3 == 0) {
                Vector line = at.toVector().subtract(top.toVector());
                for (int k = 1; k < 6; k++) at.getWorld().spawnParticle(Particle.DUST, top.clone().add(line.clone().multiply(k / 6.0)), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(0xE8E8F0), 0.4f));
            }
        }, () -> clear(player.getUniqueId()));
    }

    private void rockPaperShears(Player player) {
        Player rival = nearest(player, 8);
        String[] picks = {"✊ Rock", "✋ Paper", "✌ Shears"};
        int mine = ThreadLocalRandom.current().nextInt(3), theirs = ThreadLocalRandom.current().nextInt(3);
        String rivalName = rival == null ? "the Lobby" : rival.getName();
        String[] count = {"Rock...", "Paper...", "Shears..."};
        for (int i = 0; i < 3; i++) {
            int n = i;
            fx.later(i * 12, () -> {
                TextDisplay c = label(player.getLocation().add(0, 2.6, 0), text(count[n], 0xFFFFFF), 11);
                player.addPassenger(c);
                if (rival != null) {
                    TextDisplay r = label(rival.getLocation().add(0, 2.6, 0), text(count[n], 0xFFFFFF), 11);
                    rival.addPassenger(r);
                }
                fx.sound(player.getLocation(), "block.note_block.hat", 1f, 1f + n * 0.2f);
            });
        }
        fx.later(36, () -> {
            int result = (mine - theirs + 3) % 3;      // 0 draw, 1 I win, 2 they win
            TextDisplay a = label(player.getLocation().add(0, 2.6, 0), text(picks[mine], 0xFFE14D), 60);
            player.addPassenger(a);
            if (rival != null) {
                TextDisplay b = label(rival.getLocation().add(0, 2.6, 0), text(picks[theirs], 0xFFE14D), 60);
                rival.addPassenger(b);
            }
            String key = result == 0 ? "collectibles.gadget.rps-draw" : result == 1 ? "collectibles.gadget.rps-win" : "collectibles.gadget.rps-lose";
            var tags = net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("player", player.getName()),
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("rival", rivalName),
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("mine", picks[mine]),
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("theirs", picks[theirs]));
            plugin.messages().send(player, key, tags);
            if (rival != null) plugin.messages().send(rival, key, tags);
            if (result != 0) {
                Player winner = result == 1 ? player : rival;
                if (winner != null) fx.confetti(winner.getLocation().add(0, 2.2, 0), 25, 0.5);
            }
            fx.sound(player.getLocation(), result == 1 ? "entity.player.levelup" : "entity.villager.no", 0.8f, 1.4f);
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEntityEvent event) {
        Consumer<Player> click = clicks.get(event.getRightClicked().getUniqueId());
        if (click == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        click.accept(event.getPlayer());
    }
}
