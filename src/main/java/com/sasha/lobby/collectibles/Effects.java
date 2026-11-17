/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.collectibles;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.collectibles.Catalog.Item;
import com.sasha.lobby.items.MenuStyle;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Display;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What collectibles do: gadgets, suit abilities, cloaks (particles around the wearer, every 2 ticks), emotes (a big
 * emoji over the head) and gestures (the player waves, claps, spins...). Nothing here can hurt anyone or change the
 * world: thrown things are cancelled on hit, paint is only shown to players for a few seconds, the TNT is fake.
 */
final class Effects implements Listener {

    private static final String TAG = "lobby_collectible";
    private final Lobby plugin;
    private final Collectibles collectibles;
    private final NamespacedKey effectKey;
    private BukkitTask task;
    private long tick;
    /** Each player's emote display (one at a time). */
    private final Map<UUID, TextDisplay> emotes = new HashMap<>();
    private static final Color[] PARTY = {Color.fromRGB(0xFF7BE5), Color.fromRGB(0xB266FF), Color.fromRGB(0x7FA8FF),
            Color.fromRGB(0x5BE37C), Color.fromRGB(0xFFD27F), Color.fromRGB(0xFF7590)};

    Effects(Lobby plugin, Collectibles collectibles) {
        this.plugin = plugin;
        this.collectibles = collectibles;
        this.effectKey = new NamespacedKey(plugin, "collectible_effect");
    }

    void start() {
        stop();
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) if (entity.getScoreboardTags().contains(TAG)) entity.remove();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            tick += 2;
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                Item cloak = collectibles.cloak(player);
                if (cloak != null && !player.isInvisible() && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) cloak(player, cloak.key());
            }
        }, 2L, 2L);
    }

    void stop() {
        if (task != null) task.cancel();
        task = null;
        for (TextDisplay display : emotes.values()) display.remove();
        emotes.clear();
    }

    void quit(Player player) {
        TextDisplay emote = emotes.remove(player.getUniqueId());
        if (emote != null) emote.remove();
    }

    // ---------------------------------------------------------------- helpers

    private void sound(Location at, String key, float volume, float pitch) {
        at.getWorld().playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch), at.getX(), at.getY(), at.getZ());
    }

    private Particle.DustOptions dust(Color colour, float size) {
        return new Particle.DustOptions(colour, size);
    }

    private Color party() {
        return PARTY[ThreadLocalRandom.current().nextInt(PARTY.length)];
    }

    /** Runs something every tick for a while. */
    private void every(int ticks, java.util.function.IntConsumer step, Runnable end) {
        int[] t = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (t[0] >= ticks) {
                task.cancel();
                if (end != null) end.run();
                return;
            }
            step.accept(t[0]++);
        }, 1L, 1L);
    }

    private void later(int ticks, Runnable run) {
        plugin.getServer().getScheduler().runTaskLater(plugin, run, ticks);
    }

    private <T extends Entity> T tagged(T entity, String effect) {
        entity.addScoreboardTag(TAG);
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING, effect);
        return entity;
    }

    private ItemStack modelled(Material material, String model) {
        ItemStack item = ItemStack.of(material);
        if (plugin.menuStyle().custom() && model != null) item.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", model)));
        return item;
    }

    /** Throws something that only makes a show where it lands. */
    private Snowball toss(Player player, ItemStack look, String effect, double speed) {
        Snowball ball = player.launchProjectile(Snowball.class, player.getEyeLocation().getDirection().multiply(speed));
        ball.setItem(look);
        return tagged(ball, effect);
    }

    /** A few non-pickup items bursting out of a spot, gone after a moment. */
    private void burstItems(Location at, Material material, int count, double power, int lifeTicks) {
        for (int i = 0; i < count; i++) {
            org.bukkit.entity.Item item = at.getWorld().dropItem(at, ItemStack.of(material), drop -> {
                drop.setCanPlayerPickup(false);
                drop.setCanMobPickup(false);
                drop.setPickupDelay(Integer.MAX_VALUE);
                ThreadLocalRandom r = ThreadLocalRandom.current();
                drop.setVelocity(new Vector(r.nextDouble(-power, power), r.nextDouble(power * 0.8, power * 1.6), r.nextDouble(-power, power)));
            });
            tagged(item, "item");
            later(lifeTicks, item::remove);
        }
    }

    private void confetti(Location at, int count, double spread) {
        for (int i = 0; i < count; i++) {
            at.getWorld().spawnParticle(Particle.DUST, at, 1, spread, spread, spread, 0, dust(party(), 1.1f));
        }
    }

    // ---------------------------------------------------------------- gadgets

    void gadget(Player player, String key) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        World world = player.getWorld();
        switch (key) {
            case "party_popper" -> {
                Location from = eye.clone().add(look.clone().multiply(0.6));
                for (int i = 0; i < 70; i++) {
                    Vector v = look.clone().multiply(0.6).add(new Vector(rand(0.35), rand(0.35) + 0.15, rand(0.35)));
                    world.spawnParticle(Particle.DUST, from.clone().add(v.clone().multiply(ThreadLocalRandom.current().nextDouble(1, 5))),
                            1, 0, 0, 0, 0, dust(party(), 1.2f));
                }
                world.spawnParticle(Particle.FIREWORK, from, 15, 0.1, 0.1, 0.1, 0.12);
                sound(from, "entity.firework_rocket.blast", 0.8f, 1.6f);
                sound(from, "entity.player.levelup", 0.4f, 2f);
            }
            case "paintball_gun" -> {
                toss(player, ItemStack.of(Material.SNOWBALL), "paint", 2.2);
                sound(eye, "entity.chicken.egg", 0.6f, 1.6f);
            }
            case "bubble_wand" -> {
                sound(eye, "block.bubble_column.upwards_inside", 0.8f, 1.2f);
                every(30, t -> {
                    Location at = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1 + t * 0.12));
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 3, 0.15, 0.15, 0.15, 0,
                            new Particle.DustTransition(Color.fromRGB(0xBFEFFF), Color.fromRGB(0xFFFFFF), 1.4f));
                    world.spawnParticle(Particle.BUBBLE_POP, at, 2, 0.2, 0.2, 0.2, 0.02);
                    if (t % 6 == 0) sound(at, "block.bubble_column.bubble_pop", 0.5f, 1.4f);
                }, null);
            }
            case "firework_launcher" -> {
                Firework firework = world.spawn(player.getLocation().add(0, 1, 0), Firework.class, fw -> {
                    FireworkMeta meta = fw.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.values()[ThreadLocalRandom.current().nextInt(4)])
                            .withColor(party(), party()).withFade(party()).flicker(true).trail(true).build());
                    meta.setPower(1);
                    fw.setFireworkMeta(meta);
                });
                tagged(firework, "firework");
            }
            case "cookie_fountain" -> {
                Location at = player.getLocation().add(0, 1.2, 0);
                sound(at, "entity.generic.eat", 0.8f, 1.2f);
                every(20, t -> {
                    if (t % 2 == 0) burstItems(at, Material.COOKIE, 2, 0.25, 50);
                    if (t % 5 == 0) sound(at, "entity.item.pickup", 0.6f, 1 + t * 0.03f);
                }, null);
            }
            case "bat_blaster" -> {
                sound(eye, "entity.bat.takeoff", 1f, 1f);
                for (int i = 0; i < 8; i++) {
                    Bat bat = world.spawn(eye.clone().add(look.clone().multiply(1)), Bat.class, b -> {
                        b.setAwake(true);
                        b.setInvulnerable(true);
                        b.setVelocity(look.clone().multiply(1.2).add(new Vector(rand(0.3), rand(0.3), rand(0.3))));
                    });
                    tagged(bat, "bat");
                    later(40 + i * 3, () -> {
                        world.spawnParticle(Particle.LARGE_SMOKE, bat.getLocation(), 4, 0.1, 0.1, 0.1, 0.02);
                        bat.remove();
                    });
                }
            }
            case "fake_tnt" -> fakeTnt(player);
            case "disco_ball" -> discoBall(player);
            case "snow_cannon" -> {
                sound(eye, "entity.snow_golem.shoot", 1f, 1f);
                for (int i = 0; i < 5; i++) {
                    int n = i;
                    later(i * 3, () -> {
                        Snowball ball = toss(player, ItemStack.of(Material.SNOWBALL), "snow", 1.6);
                        ball.setVelocity(ball.getVelocity().add(new Vector(rand(0.12), rand(0.08), rand(0.12))));
                        if (n == 0) world.spawnParticle(Particle.SNOWFLAKE, eye, 20, 0.3, 0.3, 0.3, 0.05);
                    });
                }
            }
            case "pumpkin_launcher" -> {
                toss(player, ItemStack.of(Material.JACK_O_LANTERN), "pumpkin", 1.3);
                sound(eye, "entity.witch.throw", 1f, 1f);
            }
            default -> {
            }
        }
    }

    private double rand(double spread) {
        return ThreadLocalRandom.current().nextDouble(-spread, spread);
    }

    /** A TNT block that hisses and flashes, then "explodes" (just particles and sound). */
    private void fakeTnt(Player player) {
        Block target = player.getTargetBlockExact(5);
        Location at = target != null ? target.getLocation().add(0.5, 1, 0.5) : player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(2));
        at.setY(Math.floor(at.getY()));
        ItemDisplay tnt = at.getWorld().spawn(at.clone().add(0, 0.5, 0), ItemDisplay.class, d -> {
            d.setItemStack(ItemStack.of(Material.TNT));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1f), new AxisAngle4f()));
        });
        tagged(tnt, "tnt");
        sound(at, "entity.tnt.primed", 1f, 1f);
        every(50, t -> {
            if (t % 8 == 0) {
                boolean flash = (t / 8) % 2 == 0;
                tnt.setBrightness(flash ? new Display.Brightness(15, 15) : null);
                tnt.setInterpolationDelay(0);
                tnt.setInterpolationDuration(4);
                float s = flash ? 1.08f + t * 0.004f : 1f;
                tnt.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s), new AxisAngle4f()));
            }
            at.getWorld().spawnParticle(Particle.SMOKE, at.clone().add(0, 1.05, 0), 1, 0.05, 0, 0.05, 0.01);
        }, () -> {
            tnt.remove();
            Location centre = at.clone().add(0, 0.5, 0);
            at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, centre, 1);
            at.getWorld().spawnParticle(Particle.FIREWORK, centre, 40, 0.2, 0.2, 0.2, 0.3);
            confetti(centre, 60, 1.2);
            sound(centre, "entity.generic.explode", 1f, 1.1f);
            sound(centre, "entity.firework_rocket.twinkle", 0.8f, 1f);
        });
    }

    /** A disco ball over the player, spinning, throwing coloured light and playing a little tune. */
    private void discoBall(Player player) {
        Location at = player.getLocation().add(0, 3.4, 0);
        ItemDisplay ball = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(modelled(Material.GLASS, "gadget/disco_ball_3d"));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9f), new AxisAngle4f()));
        });
        tagged(ball, "disco");
        int[] melody = {0, 4, 7, 12, 7, 4, 0, 5, 9, 12, 9, 5};
        every(160, t -> {
            if (t % 2 == 0) {
                ball.setInterpolationDelay(0);
                ball.setInterpolationDuration(2);
                ball.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) Math.toRadians(t * 9), 0, 1, 0),
                        new Vector3f(0.9f), new AxisAngle4f()));
            }
            if (t % 3 == 0) {
                // A beam of colour from the ball to a spot on the ground nearby.
                double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2), r = ThreadLocalRandom.current().nextDouble(1.5, 4.5);
                Location to = at.clone().add(Math.cos(a) * r, -3.3, Math.sin(a) * r);
                Vector step = to.toVector().subtract(at.toVector()).multiply(1 / 10.0);
                Color colour = party();
                for (int i = 1; i <= 10; i++) {
                    at.getWorld().spawnParticle(Particle.DUST, at.clone().add(step.clone().multiply(i)), 1, 0, 0, 0, 0, dust(colour, 0.9f));
                }
            }
            if (t % 8 == 0) {
                int note = melody[(t / 8) % melody.length];
                sound(at, "block.note_block.bell", 0.7f, (float) Math.pow(2, (note - 12) / 12.0));
                at.getWorld().spawnParticle(Particle.NOTE, at.clone().add(rand(1), -0.5, rand(1)), 0, note / 24.0, 0, 0, 1);
            }
        }, () -> {
            at.getWorld().spawnParticle(Particle.FIREWORK, at, 30, 0.2, 0.2, 0.2, 0.2);
            ball.remove();
        });
    }

    // ---------------------------------------------------------------- suit abilities

    double abilityCooldown(String key) {
        return switch (key) {
            case "low_gravity", "bat_swarm" -> 15;
            case "jingle_dash" -> 10;
            case "smoke_bomb", "bone_rattle" -> 6;
            case "water_splash" -> 5;
            case "super_leap" -> 4;
            case "bunny_hop" -> 1.2;
            default -> 1.5;
        };
    }

    /** The ability item put in the hotbar while a full suit is worn. */
    ItemStack abilityItem(Item suit, String tag) {
        String ability = suit.suit().ability();
        Material material = switch (ability) {
            case "fireball" -> Material.FIRE_CHARGE;
            case "smoke_bomb" -> Material.FIREWORK_STAR;
            case "low_gravity" -> Material.PHANTOM_MEMBRANE;
            case "super_leap" -> Material.SLIME_BALL;
            case "water_splash" -> Material.HEART_OF_THE_SEA;
            case "beach_ball" -> Material.SNOWBALL;
            case "gift_toss" -> Material.PAPER;
            case "jingle_dash" -> Material.GOLD_NUGGET;
            case "bunny_hop" -> Material.RABBIT_FOOT;
            case "egg_toss" -> Material.EGG;
            case "bone_rattle" -> Material.BONE;
            default -> Material.ECHO_SHARD; // bat swarm
        };
        ItemStack item = ability.equals("gift_toss") || ability.equals("beach_ball")
                ? modelled(material, ability.equals("gift_toss") ? "mystery/box_2" : "gadget/beach_ball") : ItemStack.of(material);
        var messages = plugin.messages();
        var tags = net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(collectibles.name(suit),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("ability", messages.raw("collectibles.ability." + ability + ".name")),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("description", messages.raw("collectibles.ability." + ability + ".description")));
        item.editMeta(meta -> {
            meta.displayName(messages.item("collectibles.ability-item.name", tags));
            meta.lore(messages.lore("collectibles.ability-item.lore", tags));
            meta.addItemFlags(ItemFlag.values());
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(collectibles.itemKey(), PersistentDataType.STRING, tag);
        });
        return item;
    }

    void ability(Player player, String key) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        World world = player.getWorld();
        switch (key) {
            case "fireball" -> {
                Snowball ball = toss(player, ItemStack.of(Material.FIRE_CHARGE), "fireball", 1.5);
                sound(eye, "entity.blaze.shoot", 0.8f, 1.3f);
                every(40, t -> {
                    if (!ball.isValid()) return;
                    world.spawnParticle(Particle.FLAME, ball.getLocation(), 3, 0.05, 0.05, 0.05, 0.01);
                    world.spawnParticle(Particle.SMOKE, ball.getLocation(), 1, 0.05, 0.05, 0.05, 0.01);
                }, null);
            }
            case "smoke_bomb" -> {
                Location at = player.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, 30, 0.6, 0.6, 0.6, 0.03);
                world.spawnParticle(Particle.LARGE_SMOKE, at, 40, 0.8, 0.6, 0.8, 0.05);
                sound(at, "entity.generic.extinguish_fire", 1f, 0.8f);
                player.setVelocity(look.clone().setY(0).normalize().multiply(1.4).setY(0.35));
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false, false));
            }
            case "low_gravity" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 160, 2, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0, false, false, true));
                sound(eye, "block.beacon.activate", 0.8f, 1.6f);
                every(160, t -> {
                    if (t % 4 == 0) world.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 0.2, 0), 2, 0.3, 0.1, 0.3, 0.01);
                }, null);
            }
            case "super_leap" -> {
                player.setVelocity(look.clone().setY(0).normalize().multiply(0.9).setY(1.05));
                world.spawnParticle(Particle.ITEM_SLIME, player.getLocation(), 25, 0.4, 0.1, 0.4, 0.1);
                sound(eye, "entity.frog.long_jump", 1f, 1f);
            }
            case "water_splash" -> {
                Location at = player.getLocation().add(0, 0.2, 0);
                sound(at, "entity.generic.splash", 1f, 1f);
                for (int wave = 0; wave < 3; wave++) {
                    int w = wave;
                    later(wave * 4, () -> {
                        double r = 1 + w * 1.1;
                        for (int i = 0; i < 28; i++) {
                            double a = Math.PI * 2 * i / 28;
                            world.spawnParticle(Particle.SPLASH, at.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 4, 0.05, 0.1, 0.05, 0.1);
                        }
                        world.spawnParticle(Particle.FALLING_WATER, at.clone().add(0, 2, 0), 15, r * 0.5, 0.3, r * 0.5, 0);
                    });
                }
            }
            case "beach_ball" -> {
                toss(player, modelled(Material.SNOWBALL, "gadget/beach_ball"), "beach_ball:2", 1.2);
                sound(eye, "entity.snowball.throw", 1f, 0.8f);
            }
            case "gift_toss" -> {
                toss(player, modelled(Material.PAPER, "mystery/box_2"), "gift", 1.1);
                sound(eye, "entity.snowball.throw", 1f, 1.2f);
            }
            case "jingle_dash" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 2, false, false, true));
                every(80, t -> {
                    if (t % 10 == 0) sound(player.getLocation(), "block.note_block.chime", 0.6f, 1.2f + (t % 20 == 0 ? 0.3f : 0));
                    world.spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 0.3, 0), 2, 0.2, 0.1, 0.2, 0.01);
                }, null);
            }
            case "bunny_hop" -> {
                player.setVelocity(look.clone().setY(0).normalize().multiply(0.65).setY(0.78));
                world.spawnParticle(Particle.HEART, player.getLocation().add(0, 0.4, 0), 3, 0.3, 0.1, 0.3, 0);
                sound(eye, "entity.rabbit.jump", 1f, 1.2f);
            }
            case "egg_toss" -> {
                Egg egg = player.launchProjectile(Egg.class, look.clone().multiply(1.4));
                tagged(egg, "egg");
                sound(eye, "entity.egg.throw", 1f, 1f);
            }
            case "bone_rattle" -> {
                Location at = player.getLocation().add(0, 1.2, 0);
                for (int i = 0; i < 3; i++) {
                    int n = i;
                    later(i * 5, () -> sound(at, "entity.skeleton.ambient", 1f, 0.9f + n * 0.15f));
                }
                burstItems(at, Material.BONE, 8, 0.25, 40);
                world.spawnParticle(Particle.DUST, at, 30, 0.5, 0.6, 0.5, 0, dust(Color.fromRGB(0xE8E8E0), 1.2f));
            }
            case "bat_swarm" -> {
                Location at = player.getLocation().add(0, 1, 0);
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false, false));
                sound(at, "entity.bat.takeoff", 1f, 0.8f);
                world.spawnParticle(Particle.LARGE_SMOKE, at, 30, 0.5, 0.6, 0.5, 0.03);
                for (int i = 0; i < 12; i++) {
                    Bat bat = world.spawn(at.clone().add(rand(0.5), rand(0.5), rand(0.5)), Bat.class, b -> {
                        b.setAwake(true);
                        b.setInvulnerable(true);
                    });
                    tagged(bat, "bat");
                    later(60, () -> {
                        world.spawnParticle(Particle.SMOKE, bat.getLocation(), 4, 0.1, 0.1, 0.1, 0.02);
                        bat.remove();
                    });
                }
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- things landing

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        String effect = projectile.getPersistentDataContainer().get(effectKey, PersistentDataType.STRING);
        if (effect == null) return;
        event.setCancelled(true);
        Location at = projectile.getLocation();
        World world = at.getWorld();
        projectile.remove();
        switch (effect.contains(":") ? effect.substring(0, effect.indexOf(':')) : effect) {
            case "paint" -> paint(event.getHitBlock(), at);
            case "fireball" -> {
                world.spawnParticle(Particle.FLAME, at, 30, 0.3, 0.3, 0.3, 0.06);
                world.spawnParticle(Particle.LAVA, at, 6, 0.2, 0.2, 0.2, 0);
                sound(at, "block.fire.extinguish", 0.7f, 1.4f);
            }
            case "gift" -> {
                confetti(at, 60, 0.8);
                world.spawnParticle(Particle.NOTE, at, 6, 0.6, 0.5, 0.6, 1);
                sound(at, "entity.player.levelup", 0.7f, 1.6f);
                sound(at, "block.note_block.chime", 0.7f, 1.4f);
            }
            case "snow" -> {
                world.spawnParticle(Particle.SNOWFLAKE, at, 12, 0.3, 0.3, 0.3, 0.05);
                world.spawnParticle(Particle.ITEM, at, 8, 0.2, 0.2, 0.2, 0.1, ItemStack.of(Material.SNOWBALL));
            }
            case "pumpkin" -> {
                world.spawnParticle(Particle.DUST, at, 40, 0.5, 0.5, 0.5, 0, dust(Color.fromRGB(0xFF8A1F), 1.4f));
                world.spawnParticle(Particle.SOUL, at, 10, 0.4, 0.4, 0.4, 0.03);
                sound(at, "entity.witch.celebrate", 0.8f, 1.2f);
                sound(at, "block.wood.break", 0.8f, 0.8f);
            }
            case "beach_ball" -> {
                int bounces = Integer.parseInt(effect.substring(effect.indexOf(':') + 1));
                confetti(at, 10, 0.3);
                sound(at, "entity.slime.jump", 0.8f, 1.4f);
                if (bounces > 0) {
                    Vector v = projectile.getVelocity().multiply(0.6);
                    v.setY(Math.abs(v.getY()) + 0.35);
                    Snowball again = world.spawn(at.clone().add(0, 0.2, 0), Snowball.class, s -> {
                        s.setItem(modelled(Material.SNOWBALL, "gadget/beach_ball"));
                        s.setVelocity(v);
                        if (projectile.getShooter() != null) s.setShooter(projectile.getShooter());
                    });
                    tagged(again, "beach_ball:" + (bounces - 1));
                }
            }
            case "egg" -> {
                world.spawnParticle(Particle.ITEM, at, 12, 0.2, 0.2, 0.2, 0.1, ItemStack.of(Material.EGG));
                world.spawnParticle(Particle.DUST, at, 15, 0.3, 0.3, 0.3, 0, dust(Color.fromRGB(0xFFE14D), 1f));
                sound(at, "entity.chicken.egg", 1f, 1.4f);
            }
            default -> {
            }
        }
    }

    /** Splats colour on the blocks around a paintball hit, only shown to players, gone after a few seconds. */
    private void paint(Block hit, Location at) {
        if (hit == null) return;
        Material[] colours = {Material.PINK_CONCRETE, Material.PURPLE_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.LIME_CONCRETE,
                Material.YELLOW_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE, Material.CYAN_CONCRETE};
        BlockData paint = colours[ThreadLocalRandom.current().nextInt(colours.length)].createBlockData();
        List<Block> splat = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            Block block = hit.getRelative(x, y, z);
            if (Math.abs(x) + Math.abs(y) + Math.abs(z) > 2 || !block.getType().isSolid() || block.getType().isInteractable()) continue;
            if (!block.getRelative(0, 1, 0).getType().isSolid() || x == 0 && y == 0 && z == 0) splat.add(block);
        }
        List<Player> viewers = at.getWorld().getPlayers().stream().filter(p -> p.getLocation().distanceSquared(at) < 64 * 64).toList();
        for (Player viewer : viewers) for (Block block : splat) viewer.sendBlockChange(block.getLocation(), paint);
        at.getWorld().spawnParticle(Particle.BLOCK, at, 20, 0.3, 0.3, 0.3, 0, paint);
        sound(at, "entity.slime.squish_small", 1f, 1.2f);
        later(100, () -> {
            for (Player viewer : viewers) {
                if (!viewer.isOnline()) continue;
                for (Block block : splat) viewer.sendBlockChange(block.getLocation(), block.getBlockData());
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager().getPersistentDataContainer().has(effectKey) || event.getEntity().getScoreboardTags().contains(TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEgg(PlayerEggThrowEvent event) {
        if (event.getEgg().getPersistentDataContainer().has(effectKey)) event.setHatching(false);
    }

    // ---------------------------------------------------------------- cloaks

    /** Particles shown to the players who can see the wearer. */
    private void show(Player wearer, Particle particle, Location at, int count, double dx, double dy, double dz, double speed, Object data) {
        for (Player viewer : wearer.getWorld().getPlayers()) {
            if (viewer != wearer && !viewer.canSee(wearer)) continue;
            if (viewer.getLocation().distanceSquared(at) > 48 * 48) continue;
            viewer.spawnParticle(particle, at, count, dx, dy, dz, speed, data);
        }
    }

    private void cloak(Player player, String key) {
        Location base = player.getLocation();
        double t = tick;
        boolean moving = player.getVelocity().setY(0).lengthSquared() > 0.003;
        switch (key) {
            case "sparkle_aura" -> {
                if (tick % 4 != 0) return;
                for (int i = 0; i < 2; i++) {
                    Location at = base.clone().add(rand(0.6), ThreadLocalRandom.current().nextDouble(0.1, 2), rand(0.6));
                    show(player, Particle.DUST, at, 1, 0, 0, 0, 0, dust(i == 0 ? Color.fromRGB(0xFFFFFF) : Color.fromRGB(0xFF7BE5), 0.7f));
                }
            }
            case "halo" -> {
                Location centre = base.clone().add(0, 2.25, 0);
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI * 2 * i / 10 + t * 0.05;
                    show(player, Particle.DUST, centre.clone().add(Math.cos(a) * 0.32, 0, Math.sin(a) * 0.32), 1, 0, 0, 0, 0,
                            dust(Color.fromRGB(0xFFE680), 0.55f));
                }
            }
            case "rain_cloud" -> {
                Location cloud = base.clone().add(0, 2.7, 0);
                show(player, Particle.CLOUD, cloud, 3, 0.35, 0.05, 0.35, 0, null);
                if (tick % 4 == 0) show(player, Particle.FALLING_WATER, cloud.clone().add(0, -0.1, 0), 2, 0.3, 0, 0.3, 0, null);
            }
            case "orbit" -> {
                Location centre = base.clone().add(0, 1, 0);
                for (int ring = 0; ring < 2; ring++) {
                    double a = t * 0.18 + ring * Math.PI;
                    double tilt = ring == 0 ? 0.35 : -0.35;
                    Location at = centre.clone().add(Math.cos(a) * 0.9, Math.sin(a) * tilt, Math.sin(a) * 0.9);
                    show(player, Particle.DUST, at, 1, 0, 0, 0, 0, dust(ring == 0 ? Color.fromRGB(0x7FA8FF) : Color.fromRGB(0xFF7BE5), 1.1f));
                    show(player, Particle.END_ROD, at, 0, 0, 0, 0, 0, null);
                }
            }
            case "flame_ring" -> {
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.12 + Math.PI * 2 * i / 3;
                    show(player, Particle.FLAME, base.clone().add(Math.cos(a) * 0.8, 0.1, Math.sin(a) * 0.8), 1, 0, 0, 0, 0, null);
                }
                if (tick % 6 == 0) show(player, Particle.SMOKE, base.clone().add(0, 0.2, 0), 2, 0.5, 0, 0.5, 0, null);
            }
            case "angel_wings" -> {
                if (moving || tick % 4 != 0) return;
                wings(player);
            }
            case "sun_rays" -> {
                Location centre = base.clone().add(0, 2.1, 0);
                for (int i = 0; i < 6; i++) {
                    double a = t * 0.04 + Math.PI * 2 * i / 6;
                    for (int s = 1; s <= 3; s++) {
                        show(player, Particle.DUST, centre.clone().add(Math.cos(a) * (0.35 + s * 0.18), 0, Math.sin(a) * (0.35 + s * 0.18)),
                                1, 0, 0, 0, 0, dust(s == 1 ? Color.fromRGB(0xFFF1A8) : Color.fromRGB(0xFFC83D), 0.7f));
                    }
                }
            }
            case "snow_globe" -> {
                if (tick % 2 != 0) return;
                for (int i = 0; i < 3; i++) {
                    double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2), r = ThreadLocalRandom.current().nextDouble(0.4, 1.3);
                    show(player, Particle.SNOWFLAKE, base.clone().add(Math.cos(a) * r, 2.4, Math.sin(a) * r), 0, 0, -0.08, 0, 1, null);
                }
            }
            case "blossom_aura" -> {
                if (tick % 4 != 0) return;
                show(player, Particle.CHERRY_LEAVES, base.clone().add(0, 2, 0), 2, 0.6, 0.2, 0.6, 0, null);
            }
            case "spooky_aura" -> {
                double a = t * 0.15;
                double y = (tick % 40) / 20.0;
                show(player, Particle.SOUL_FIRE_FLAME, base.clone().add(Math.cos(a) * 0.7, y, Math.sin(a) * 0.7), 1, 0, 0, 0, 0, null);
                show(player, Particle.DUST, base.clone().add(Math.cos(a + Math.PI) * 0.7, y, Math.sin(a + Math.PI) * 0.7), 1, 0, 0, 0, 0,
                        dust(Color.fromRGB(0xFF8A1F), 0.9f));
                if (tick % 10 == 0) show(player, Particle.SOUL, base.clone().add(0, 1, 0), 1, 0.4, 0.4, 0.4, 0.01, null);
            }
            default -> {
            }
        }
    }

    /** Wings of light behind the player, flapping slowly. */
    private void wings(Player player) {
        String[] shape = {
                "....xx.",
                "..xxxxx",
                ".xxxxxx",
                "xxxxxx.",
                "xxxxx..",
                ".xxx...",
                "..x...."};
        Location base = player.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        Vector back = new Vector(Math.sin(yaw), 0, -Math.cos(yaw)).multiply(0.32);   // behind the player
        Vector side = new Vector(Math.cos(yaw), 0, Math.sin(yaw));                     // to their left
        double flap = Math.toRadians(25 + 15 * Math.sin(tick * 0.12));
        Location spine = base.clone().add(0, 1.55, 0).add(back);
        for (int row = 0; row < shape.length; row++) {
            for (int col = 0; col < shape[row].length(); col++) {
                if (shape[row].charAt(col) != 'x') continue;
                double out = 0.12 + col * 0.12, up = 0.42 - row * 0.12;
                for (int s = -1; s <= 1; s += 2) {
                    Vector offset = side.clone().multiply(s * out * Math.cos(flap)).add(back.clone().normalize().multiply(out * Math.sin(flap)));
                    show(player, Particle.DUST, spine.clone().add(offset).add(0, up, 0), 1, 0, 0, 0, 0,
                            dust(row < 2 ? Color.fromRGB(0xFFFFFF) : Color.fromRGB(0xE3EBFF), 0.6f));
                }
            }
        }
    }

    // ---------------------------------------------------------------- emotes and gestures

    /** A big emoji pops up over the player's head for a few seconds. */
    void emote(Player player, Item item) {
        quit(player);
        char glyph = MenuStyle.emoji(item.emoji());
        Component face = glyph == 0 ? Component.text(item.name())
                : Component.text(String.valueOf(glyph)).font(Key.key("fractal", "lobby")).color(net.kyori.adventure.text.format.NamedTextColor.WHITE);
        TextDisplay display = player.getWorld().spawn(player.getLocation().add(0, 2.3, 0), TextDisplay.class, d -> {
            d.text(face);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(0, 0.55f, 0), new AxisAngle4f(), new Vector3f(0.1f), new AxisAngle4f()));
        });
        tagged(display, "emote");
        player.addPassenger(display);
        emotes.put(player.getUniqueId(), display);
        later(1, () -> {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(5);
            display.setTransformation(new Transformation(new Vector3f(0, 0.65f, 0), new AxisAngle4f(), new Vector3f(3.4f), new AxisAngle4f()));
        });
        later(7, () -> {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(4);
            display.setTransformation(new Transformation(new Vector3f(0, 0.6f, 0), new AxisAngle4f(), new Vector3f(3f), new AxisAngle4f()));
        });
        later(64, () -> {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(5);
            display.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(), new Vector3f(0f), new AxisAngle4f()));
        });
        later(70, () -> {
            display.remove();
            emotes.remove(player.getUniqueId(), display);
        });
        Location head = player.getLocation().add(0, 2.4, 0);
        switch (item.key()) {
            case "love", "kiss" -> {
                player.getWorld().spawnParticle(Particle.HEART, head, 6, 0.5, 0.3, 0.5, 0);
                sound(head, item.key().equals("kiss") ? "entity.player.levelup" : "entity.cat.purreow", 0.7f, 1.6f);
            }
            case "joy" -> sound(head, "entity.villager.celebrate", 0.8f, 1.4f);
            case "angry" -> {
                player.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, head, 4, 0.4, 0.2, 0.4, 0);
                sound(head, "entity.ravager.roar", 0.4f, 1.6f);
            }
            case "crying" -> {
                player.getWorld().spawnParticle(Particle.FALLING_WATER, head, 10, 0.3, 0.2, 0.3, 0);
                sound(head, "entity.ghast.ambient", 0.4f, 1.8f);
            }
            case "mindblown" -> {
                player.getWorld().spawnParticle(Particle.EXPLOSION, head.clone().add(0, 0.4, 0), 1);
                sound(head, "entity.generic.explode", 0.5f, 1.6f);
            }
            case "money" -> {
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, head, 10, 0.5, 0.3, 0.5, 0);
                sound(head, "entity.experience_orb.pickup", 0.8f, 0.8f);
            }
            case "skull" -> sound(head, "entity.wither_skeleton.ambient", 0.6f, 1.2f);
            case "clown" -> sound(head, "entity.goat.screaming.ambient", 0.6f, 1.4f);
            default -> sound(head, "entity.experience_orb.pickup", 0.7f, 1.3f);
        }
    }

    /** The player does a gesture: their arms and head move for a moment. */
    void gesture(Player player, String key) {
        switch (key) {
            case "wave" -> every(24, t -> {
                if (t % 4 == 0) player.swingMainHand();
                if (t == 0) sound(player.getLocation(), "entity.villager.ambient", 0.6f, 1.6f);
            }, null);
            case "clap" -> every(24, t -> {
                if (t % 4 == 0) {
                    if ((t / 4) % 2 == 0) player.swingMainHand();
                    else player.swingOffHand();
                    sound(player.getLocation(), "block.note_block.snare", 0.7f, 1.4f);
                }
            }, null);
            case "bow" -> {
                float yaw = player.getLocation().getYaw(), pitch = player.getLocation().getPitch();
                every(30, t -> {
                    float p = t < 10 ? t * 7f : t < 20 ? 70f : 70f - (t - 20) * 7f;
                    look(player, yaw, Math.min(90, p));
                }, () -> look(player, yaw, pitch));
                sound(player.getLocation(), "block.note_block.harp", 0.6f, 0.8f);
            }
            case "spin" -> {
                float yaw = player.getLocation().getYaw(), pitch = player.getLocation().getPitch();
                every(20, t -> look(player, yaw + (t + 1) * 18f, pitch), null);
                sound(player.getLocation(), "entity.player.attack.sweep", 0.6f, 1.4f);
            }
            case "facepalm" -> {
                float yaw = player.getLocation().getYaw(), pitch = player.getLocation().getPitch();
                look(player, yaw, 55);
                player.swingMainHand();
                sound(player.getLocation(), "entity.villager.no", 0.8f, 0.9f);
                later(30, () -> look(player, yaw, pitch));
            }
            case "headbang" -> {
                float yaw = player.getLocation().getYaw(), pitch = player.getLocation().getPitch();
                every(32, t -> {
                    look(player, yaw, (t / 4) % 2 == 0 ? 50 : -10);
                    if (t % 8 == 0) sound(player.getLocation(), "block.note_block.basedrum", 0.8f, 1f);
                    if (t % 8 == 4) sound(player.getLocation(), "block.note_block.snare", 0.6f, 1f);
                }, () -> look(player, yaw, pitch));
            }
            case "celebrate" -> {
                player.setVelocity(new Vector(0, 0.45, 0));
                every(20, t -> {
                    if (t % 4 == 0) {
                        player.swingMainHand();
                        player.swingOffHand();
                    }
                }, null);
                Location at = player.getLocation().add(0, 2, 0);
                player.getWorld().spawnParticle(Particle.FIREWORK, at, 30, 0.3, 0.3, 0.3, 0.15);
                confetti(at, 40, 0.8);
                sound(at, "entity.player.levelup", 0.7f, 1.2f);
                sound(at, "entity.firework_rocket.twinkle", 0.6f, 1f);
            }
            default -> {
            }
        }
    }

    /** Turns the player's head (and view). */
    private void look(Player player, float yaw, float pitch) {
        if (!player.isOnline()) return;
        Location at = player.getLocation();
        at.setYaw(yaw);
        at.setPitch(pitch);
        player.teleport(at, io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_X, io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_Y,
                io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_Z);
    }
}
