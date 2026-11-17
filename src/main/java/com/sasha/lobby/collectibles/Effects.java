/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.collectibles;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.collectibles.Catalog.Item;
import com.sasha.lobby.items.MenuStyle;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Bat;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What collectibles do: gadgets, suit bonuses (right-click or passive), cloaks (textured pieces riding the wearer, with
 * a few particles), the spinning propeller, emotes (a big emoji over the head) and gestures (a copy of the player that
 * does the gesture in front of them). Nothing here can hurt anyone or change the world: thrown things are cancelled on
 * hit, paint is only shown to players for a few seconds, the TNT is fake.
 */
final class Effects implements Listener {

    private static final String TAG = "lobby_collectible";
    private final Lobby plugin;
    private final Collectibles collectibles;
    private final NamespacedKey effectKey;
    private final NamespacedKey bonusKey;
    private BukkitTask task;
    private long tick;
    /** Each player's emote display (one at a time). */
    private final Map<UUID, TextDisplay> emotes = new HashMap<>();
    /** The pieces of each player's cloak. */
    private final Map<UUID, Rig> rigs = new HashMap<>();
    private final Map<UUID, ItemDisplay> propellers = new HashMap<>();
    private final Map<UUID, Mannequin> clones = new HashMap<>();
    private final Map<UUID, ItemDisplay> balloons = new HashMap<>();
    private final Map<UUID, Placed> placed = new HashMap<>();
    /** Frogs charging a leap: when they started. */
    private final Map<UUID, Long> charging = new HashMap<>();
    /** Which passive bonus each player has on now. */
    private final Map<UUID, String> passives = new HashMap<>();
    private static final Color[] PARTY = {Color.fromRGB(0xFF7BE5), Color.fromRGB(0xB266FF), Color.fromRGB(0x7FA8FF),
            Color.fromRGB(0x5BE37C), Color.fromRGB(0xFFD27F), Color.fromRGB(0xFF7590)};

    private static final class Rig {
        final String key;
        Double lastX, lastZ;
        /** Our own body turn (the server doesn't keep a player's body turn): follows the head like the game does. */
        Float body;
        final List<ItemDisplay> parts = new ArrayList<>();

        Rig(String key) {
            this.key = key;
        }
    }

    /** Something placed on the ground (a cookie fountain or trampoline): one per player. */
    private static final class Placed {
        final ItemDisplay display;
        final BukkitTask task;

        Placed(ItemDisplay display, BukkitTask task) {
            this.display = display;
            this.task = task;
        }
    }

    Effects(Lobby plugin, Collectibles collectibles) {
        this.plugin = plugin;
        this.collectibles = collectibles;
        this.effectKey = new NamespacedKey(plugin, "collectible_effect");
        this.bonusKey = new NamespacedKey(plugin, "suit_bonus");
        this.more = new MoreGadgets(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(more, plugin);
    }

    /** The gadgets added in 1.41. */
    private final MoreGadgets more;

    void start() {
        stop();
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) if (entity.getScoreboardTags().contains(TAG)) entity.remove();
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) worn(player);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            tick += 2;
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                boolean shown = !player.isInvisible() && player.getGameMode() != GameMode.SPECTATOR;
                Item cloak = collectibles.cloak(player);
                if (cloak != null && shown) cloak(player, cloak.key());
                Rig rig = rigs.get(player.getUniqueId());
                if (rig != null) pose(player, rig, shown);
                ItemDisplay propeller = propellers.get(player.getUniqueId());
                if (propeller != null) spin(player, propeller, shown);
                ItemDisplay balloon = balloons.get(player.getUniqueId());
                if (balloon != null) floatBalloon(player, balloon);
                passive(player);
                charge(player);
                if (tick % 20 == 0) viewers(player);
            }
        }, 2L, 2L);
    }

    void stop() {
        if (task != null) task.cancel();
        task = null;
        if (more != null) more.stop();
        for (TextDisplay display : emotes.values()) display.remove();
        emotes.clear();
        for (UUID id : new ArrayList<>(rigs.keySet())) unrig(id);
        propellers.values().forEach(Entity::remove);
        propellers.clear();
        clones.values().forEach(Entity::remove);
        clones.clear();
        balloons.values().forEach(Entity::remove);
        balloons.clear();
        anchors.values().forEach(Entity::remove);
        anchors.clear();
        for (UUID id : new ArrayList<>(placed.keySet())) unplace(id);
        for (Player player : plugin.getServer().getOnlinePlayers()) bonus(player, null);
        passives.clear();
        charging.clear();
    }

    void quit(Player player) {
        UUID id = player.getUniqueId();
        more.quit(player);
        TextDisplay emote = emotes.remove(id);
        if (emote != null) emote.remove();
        unrig(id);
        ItemDisplay propeller = propellers.remove(id);
        if (propeller != null) propeller.remove();
        Mannequin clone = clones.remove(id);
        if (clone != null) clone.remove();
        popBalloon(id, false);
        unplace(id);
        bonus(player, null);
        stopGadget(player);
        passives.remove(id);
        charging.remove(id);
    }

    /** After the player's collectibles change: their cloak pieces and propeller. */
    void worn(Player player) {
        Item cloak = collectibles.cloak(player);
        Rig rig = rigs.get(player.getUniqueId());
        String want = cloak == null ? null : cloak.key();
        if (rig != null && !rig.key.equals(want)) {
            unrig(player.getUniqueId());
            rig = null;
        }
        if (rig == null && want != null && plugin.menuStyle().custom()) rig(player, want);
        Item hat = collectibles.hat(player);
        boolean propeller = hat != null && hat.key().equals("propeller_cap") && plugin.menuStyle().custom();
        ItemDisplay spinning = propellers.get(player.getUniqueId());
        if (propeller && (spinning == null || !spinning.isValid())) {
            ItemDisplay display = rider(player, "hat/propeller");
            propellers.put(player.getUniqueId(), display);
        } else if (!propeller && spinning != null) {
            spinning.remove();
            propellers.remove(player.getUniqueId());
        }
    }

    // ---------------------------------------------------------------- helpers

    void sound(Location at, String key, float volume, float pitch) {
        at.getWorld().playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch), at.getX(), at.getY(), at.getZ());
    }

    Particle.DustOptions dust(Color colour, float size) {
        return new Particle.DustOptions(colour, size);
    }

    Color party() {
        return PARTY[ThreadLocalRandom.current().nextInt(PARTY.length)];
    }

    /** Runs something every tick for a while. */
    BukkitTask every(int ticks, java.util.function.IntConsumer step, Runnable end) {
        int[] t = {0};
        BukkitTask[] self = new BukkitTask[1];
        Running run = new Running();
        run.end = end;
        self[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (t[0] >= ticks) {
                self[0].cancel();
                run.finish();
                return;
            }
            step.accept(t[0]++);
        }, 1L, 1L);
        run.task = self[0];
        if (owner != null) {
            List<Running> list = running.computeIfAbsent(owner, k -> new ArrayList<>());
            list.removeIf(r -> r.done);
            list.add(run);
        }
        return self[0];
    }

    /** A gadget's show that is still going: stopped (and tidied up) when the player swaps or takes off their gadget. */
    private static final class Running {
        BukkitTask task;
        Runnable end;
        boolean done;

        void finish() {
            if (done) return;
            done = true;
            if (end != null) end.run();
        }
    }

    /** Whose gadget is being used right now (the shows it starts belong to them). */
    private UUID owner;
    private final Map<UUID, List<Running>> running = new HashMap<>();

    /** Ends everything the player's gadgets are still doing: placed things go, flying things land, balloons pop. */
    void stopGadget(Player player) {
        UUID id = player.getUniqueId();
        List<Running> list = running.remove(id);
        if (list != null) {
            for (Running run : list) {
                if (run.done) continue;
                if (run.task != null) run.task.cancel();
                try {
                    run.finish();
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Stopping a gadget: " + e);
                }
            }
        }
        unplace(id);
        more.quit(player);
        popBalloon(id, false);
    }

    void later(int ticks, Runnable run) {
        plugin.getServer().getScheduler().runTaskLater(plugin, run, ticks);
    }

    <T extends Entity> T tagged(T entity, String effect) {
        entity.addScoreboardTag(TAG);
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING, effect);
        return entity;
    }

    ItemStack modelled(Material material, String model) {
        ItemStack item = ItemStack.of(material);
        if (plugin.menuStyle().custom() && model != null) item.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", model)));
        return item;
    }

    double rand(double spread) {
        return ThreadLocalRandom.current().nextDouble(-spread, spread);
    }

    boolean parkour(Player player) {
        return collectibles.inParkour(player);
    }

    /** Throws something that only makes a show where it lands. */
    Snowball toss(Player player, ItemStack look, String effect, double speed) {
        Snowball ball = player.launchProjectile(Snowball.class, player.getEyeLocation().getDirection().multiply(speed));
        ball.setItem(look);
        return tagged(ball, effect);
    }

    /** A few non-pickup items bursting out of a spot, gone after a moment. */
    void burstItems(Location at, ItemStack look, int count, double power, int lifeTicks) {
        for (int i = 0; i < count; i++) {
            org.bukkit.entity.Item item = at.getWorld().dropItem(at, look.clone(), drop -> {
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

    void confetti(Location at, int count, double spread) {
        for (int i = 0; i < count; i++) {
            at.getWorld().spawnParticle(Particle.DUST, at, 1, spread, spread, spread, 0, dust(party(), 1.1f));
        }
    }

    /** An item display riding the player (it sits on top of their head; transformations move it from there). */
    ItemDisplay rider(Player player, String model) {
        ItemDisplay display = player.getWorld().spawn(player.getLocation().add(0, 1.8, 0), ItemDisplay.class, d -> {
            d.setItemStack(modelled(Material.PAPER, model));
            d.setRotation(0, 0);
            d.setInterpolationDuration(3);
            d.setViewRange(0.6f);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0), new AxisAngle4f()));
        });
        tagged(display, "rider");
        player.addPassenger(display);
        return display;
    }

    private static void move(Display display, Matrix4f matrix) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(3);
        display.setTransformationMatrix(matrix);
    }

    /** Players who have the wearer hidden don't see what rides them either. */
    private void viewers(Player wearer) {
        List<Entity> riders = new ArrayList<>();
        Rig rig = rigs.get(wearer.getUniqueId());
        if (rig != null) riders.addAll(rig.parts);
        ItemDisplay propeller = propellers.get(wearer.getUniqueId());
        if (propeller != null) riders.add(propeller);
        ItemDisplay balloon = balloons.get(wearer.getUniqueId());
        if (balloon != null) riders.add(balloon);
        org.bukkit.entity.Chicken anchor = anchors.get(wearer.getUniqueId());
        if (anchor != null) riders.add(anchor);
        if (riders.isEmpty()) return;
        for (Player viewer : wearer.getWorld().getPlayers()) {
            if (viewer == wearer) continue;
            boolean see = viewer.canSee(wearer);
            for (Entity rider : riders) {
                if (see) viewer.showEntity(plugin, rider);
                else viewer.hideEntity(plugin, rider);
            }
        }
    }

    // ---------------------------------------------------------------- gadgets

    void gadget(Player player, String key) {
        owner = player.getUniqueId();
        try {
            runGadget(player, key);
        } finally {
            owner = null;
        }
    }

    private void runGadget(Player player, String key) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        World world = player.getWorld();
        switch (key) {
            case "party_popper" -> {
                Location from = eye.clone().add(look.clone().multiply(0.6));
                for (int i = 0; i < 90; i++) {
                    Vector v = look.clone().multiply(0.6).add(new Vector(rand(0.35), rand(0.35) + 0.15, rand(0.35)));
                    world.spawnParticle(Particle.DUST, from.clone().add(v.clone().multiply(ThreadLocalRandom.current().nextDouble(1, 5))),
                            1, 0, 0, 0, 0, dust(party(), 1.3f));
                }
                Location fall = from.clone().add(look.clone().multiply(3));
                every(30, t -> {
                    if (t % 2 == 0) world.spawnParticle(Particle.DUST_COLOR_TRANSITION, fall.clone().add(rand(1.4), 1 - t * 0.05, rand(1.4)), 2,
                            0.05, 0.05, 0.05, 0, new Particle.DustTransition(party(), party(), 1f));
                }, null);
                world.spawnParticle(Particle.FIREWORK, from, 15, 0.1, 0.1, 0.1, 0.12);
                sound(from, "entity.firework_rocket.blast", 0.8f, 1.6f);
                sound(from, "entity.player.levelup", 0.4f, 2f);
            }
            case "paintball_gun" -> {
                toss(player, modelled(Material.SNOWBALL, "gadget/paintball"), "paint", 2.2);
                sound(eye, "entity.chicken.egg", 0.6f, 1.6f);
            }
            case "bubble_wand" -> {
                sound(eye, "block.bubble_column.upwards_inside", 0.8f, 1.2f);
                every(36, t -> {
                    Location at = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1 + t * 0.14));
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 4, 0.2, 0.2, 0.2, 0,
                            new Particle.DustTransition(Color.fromRGB(0xBFEFFF), Color.fromRGB(0xFFB8F0), 1.6f));
                    world.spawnParticle(Particle.BUBBLE_POP, at, 3, 0.25, 0.25, 0.25, 0.02);
                    if (t % 6 == 0) sound(at, "block.bubble_column.bubble_pop", 0.5f, 1.4f);
                }, null);
            }
            case "firework_launcher" -> {
                sound(eye, "entity.firework_rocket.launch", 1f, 0.8f);
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, eye.clone().add(look.clone().multiply(-0.6)), 6, 0.1, 0.1, 0.1, 0.03);
                FireworkEffect.Type[] types = {FireworkEffect.Type.BALL_LARGE, FireworkEffect.Type.STAR, FireworkEffect.Type.BURST};
                for (int i = 0; i < 4; i++) {
                    int n = i;
                    later(i * 5, () -> {
                        if (!player.isOnline()) return;
                        Location from = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1));
                        Vector aim = player.getEyeLocation().getDirection().add(new Vector(rand(0.12), 0.25, rand(0.12))).normalize();
                        Firework rocket = world.spawn(from, Firework.class, fw -> {
                            FireworkMeta meta = fw.getFireworkMeta();
                            FireworkEffect.Type type = n == 3 ? FireworkEffect.Type.BALL_LARGE : types[n % types.length];
                            meta.addEffect(FireworkEffect.builder().with(type).withColor(party(), party(), party()).withFade(party())
                                    .flicker(n == 3).trail(true).build());
                            if (n == 3) meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.STAR).withColor(Color.WHITE, Color.YELLOW).build());
                            meta.setPower(1);
                            fw.setFireworkMeta(meta);
                            fw.setShotAtAngle(true);
                            fw.setVelocity(aim.multiply(n == 3 ? 1.1 : 0.9));
                        });
                        tagged(rocket, "firework");
                        later(n == 3 ? 22 : 16 + n * 2, () -> {
                            if (rocket.isValid()) rocket.detonate();
                        });
                        sound(from, "entity.firework_rocket.launch", 0.8f, 1f + n * 0.1f);
                        world.spawnParticle(Particle.FLAME, from, 6, 0.1, 0.1, 0.1, 0.03);
                    });
                }
            }
            case "cookie_fountain" -> cookieFountain(player);
            case "bat_blaster" -> {
                sound(eye, "entity.bat.takeoff", 1f, 0.8f);
                sound(eye, "entity.generic.explode", 0.5f, 1.6f);
                world.spawnParticle(Particle.EXPLOSION, eye.clone().add(look.clone().multiply(1.2)), 1);
                world.spawnParticle(Particle.LARGE_SMOKE, eye.clone().add(look.clone().multiply(1.2)), 12, 0.2, 0.2, 0.2, 0.06);
                for (int i = 0; i < 18; i++) {
                    int n = i;
                    later(i / 3, () -> {
                        if (!player.isOnline()) return;
                        Location from = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1.1));
                        Vector go = player.getEyeLocation().getDirection().multiply(1.9).add(new Vector(rand(0.45), rand(0.35) + 0.05, rand(0.45)));
                        Bat bat = world.spawn(from, Bat.class, b -> {
                            b.setAwake(true);
                            b.setInvulnerable(true);
                            b.setSilent(n % 3 != 0);
                        });
                        tagged(bat, "bat");
                        every(22 + n % 8, t -> {
                            if (!bat.isValid()) return;
                            bat.setVelocity(go.clone().multiply(1 - t * 0.02));
                            if (t % 3 == 0) world.spawnParticle(Particle.SMOKE, bat.getLocation(), 1, 0.05, 0.05, 0.05, 0.01);
                        }, () -> {
                            if (!bat.isValid()) return;
                            Location pop = bat.getLocation();
                            world.spawnParticle(Particle.LARGE_SMOKE, pop, 6, 0.2, 0.2, 0.2, 0.03);
                            world.spawnParticle(Particle.DUST, pop, 8, 0.3, 0.3, 0.3, 0, dust(Color.fromRGB(0x7A3FC4), 1.2f));
                            if (n % 4 == 0) {
                                world.spawnParticle(Particle.EXPLOSION, pop, 1);
                                sound(pop, "entity.firework_rocket.blast", 0.6f, 0.8f);
                            }
                            bat.remove();
                        });
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
            case "balloon" -> balloon(player);
            case "melody_maker" -> melody(player);
            case "magic_wand" -> magicWand(player);
            case "grappling_hook" -> {
                Snowball hook = toss(player, modelled(Material.SNOWBALL, "gadget/grappling_hook_head"), "hook", 2.4);
                sound(eye, "item.crossbow.shoot", 0.8f, 1.3f);
                every(30, t -> {
                    if (!hook.isValid() || !player.isOnline()) return;
                    Location hand = player.getLocation().add(0, 1.2, 0);
                    Vector line = hook.getLocation().toVector().subtract(hand.toVector());
                    int steps = (int) Math.min(30, line.length() * 3);
                    for (int i = 1; i < steps; i += 1) {
                        world.spawnParticle(Particle.DUST, hand.clone().add(line.clone().multiply(i / (double) steps)), 1, 0, 0, 0, 0,
                                dust(Color.fromRGB(0x8A6A45), 0.5f));
                    }
                }, () -> {
                    if (hook.isValid()) hook.remove();
                });
            }
            case "blink_stick" -> blink(player);
            case "trampoline" -> trampoline(player);
            default -> more.use(player, key);
        }
    }

    /** Where to put something on the ground: the block looked at (within 5), else just in front. */
    Location ground(Player player) {
        Block target = player.getTargetBlockExact(5);
        Location at;
        if (target != null && target.getType().isSolid() && target.getRelative(0, 1, 0).isPassable()) {
            at = target.getLocation().add(0.5, 1, 0.5);
        } else {
            at = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(2));
            at.setY(Math.floor(at.getY()));
        }
        at.setYaw(0);
        at.setPitch(0);
        return at;
    }

    private void unplace(UUID id) {
        Placed old = placed.remove(id);
        if (old == null) return;
        old.task.cancel();
        old.display.remove();
    }

    /** A cookie fountain on the ground, spraying cookies for eight seconds, then sinking away. */
    private void cookieFountain(Player player) {
        unplace(player.getUniqueId());
        Location at = ground(player);
        ItemDisplay fountain = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(modelled(Material.COOKIE, "gadget/cookie_fountain_3d"));
            d.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(), new Vector3f(0.01f), new AxisAngle4f()));
        });
        tagged(fountain, "fountain");
        later(1, () -> {
            fountain.setInterpolationDelay(0);
            fountain.setInterpolationDuration(8);
            fountain.setTransformation(new Transformation(new Vector3f(0, 0.75f, 0), new AxisAngle4f(), new Vector3f(1.5f), new AxisAngle4f()));
        });
        sound(at, "block.stone.place", 1f, 0.9f);
        sound(at, "entity.player.levelup", 0.4f, 1.8f);
        ItemStack cookie = modelled(Material.COOKIE, "gadget/cookie");
        Location top = at.clone().add(0, 1.45, 0);
        BukkitTask run = every(180, t -> {
            if (t < 10) return;
            if (t < 160) {
                if (t % 3 == 0) burstItems(top, cookie, 1, 0.16, 45);
                if (t % 4 == 0) at.getWorld().spawnParticle(Particle.DUST, top, 2, 0.15, 0.1, 0.15, 0, dust(Color.fromRGB(0xC98A4B), 1.1f));
                if (t % 12 == 0) sound(top, "entity.item.pickup", 0.5f, 0.8f + (t % 48) / 48f);
            } else if (t == 160) {
                fountain.setInterpolationDelay(0);
                fountain.setInterpolationDuration(15);
                fountain.setTransformation(new Transformation(new Vector3f(0, 0.4f, 0), new AxisAngle4f(), new Vector3f(0.01f), new AxisAngle4f()));
                sound(at, "block.stone.break", 0.8f, 1.1f);
            }
        }, () -> {
            at.getWorld().spawnParticle(Particle.POOF, at.clone().add(0, 0.4, 0), 12, 0.3, 0.2, 0.3, 0.02);
            fountain.remove();
            placed.remove(player.getUniqueId());
        });
        placed.put(player.getUniqueId(), new Placed(fountain, run));
    }

    /** A trampoline on the ground for fifteen seconds; anyone who lands on it bounces high. */
    private void trampoline(Player player) {
        unplace(player.getUniqueId());
        Location at = ground(player);
        ItemDisplay trampoline = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(modelled(Material.SLIME_BLOCK, "gadget/trampoline_3d"));
            d.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(), new Vector3f(0.01f), new AxisAngle4f()));
        });
        tagged(trampoline, "trampoline");
        later(1, () -> {
            trampoline.setInterpolationDelay(0);
            trampoline.setInterpolationDuration(6);
            trampoline.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(), new Vector3f(3f, 1f, 3f), new AxisAngle4f()));
        });
        sound(at, "block.wool.place", 1f, 0.8f);
        Map<UUID, Integer> bounced = new HashMap<>();
        BukkitTask run = every(300, t -> {
            if (t < 6 || t >= 290) {
                if (t == 290) {
                    trampoline.setInterpolationDelay(0);
                    trampoline.setInterpolationDuration(8);
                    trampoline.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(), new Vector3f(0.01f), new AxisAngle4f()));
                }
                return;
            }
            for (Player jumper : at.getWorld().getPlayers()) {
                Location feet = jumper.getLocation();
                double dx = feet.getX() - at.getX(), dz = feet.getZ() - at.getZ(), dy = feet.getY() - at.getY();
                if (dx * dx + dz * dz > 1.6 * 1.6 || dy < -0.1 || dy > 1.0 || parkour(jumper)) continue;
                if (jumper.getVelocity().getY() > 0.2) continue;
                Integer last = bounced.get(jumper.getUniqueId());
                if (last != null && t - last < 6) continue;
                bounced.put(jumper.getUniqueId(), t);
                jumper.setVelocity(jumper.getVelocity().setY(1.25));
                jumper.setFallDistance(0);
                sound(at, "entity.slime.jump", 1f, 0.8f + ThreadLocalRandom.current().nextFloat() * 0.3f);
                at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.8, 0), 18, 0.9, 0.05, 0.9, 0, dust(Color.fromRGB(0x5BC0FF), 1f));
                trampoline.setInterpolationDelay(0);
                trampoline.setInterpolationDuration(2);
                trampoline.setTransformation(new Transformation(new Vector3f(0, 0.45f, 0), new AxisAngle4f(), new Vector3f(3.15f, 0.65f, 3.15f), new AxisAngle4f()));
                later(3, () -> {
                    if (!trampoline.isValid()) return;
                    trampoline.setInterpolationDelay(0);
                    trampoline.setInterpolationDuration(5);
                    trampoline.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(), new Vector3f(3f, 1f, 3f), new AxisAngle4f()));
                });
            }
        }, () -> {
            at.getWorld().spawnParticle(Particle.POOF, at.clone().add(0, 0.3, 0), 20, 1.0, 0.1, 1.0, 0.02);
            trampoline.remove();
            placed.remove(player.getUniqueId());
        });
        placed.put(player.getUniqueId(), new Placed(trampoline, run));
    }

    /** A balloon tied to the player for thirty seconds (in a random colour). */
    /** The invisible little things the balloons are tied to (a real lead runs from the hand to them). */
    private final Map<UUID, org.bukkit.entity.Chicken> anchors = new HashMap<>();

    /** A balloon on a real lead: it bobs above the player, drifts behind when they move and tilts as it swings. */
    private void balloon(Player player) {
        popBalloon(player.getUniqueId(), false);
        if (!plugin.menuStyle().custom()) return;
        UUID id = player.getUniqueId();
        Location start = player.getLocation().add(0, 2.2, 0);
        org.bukkit.entity.Chicken anchor = player.getWorld().spawn(start, org.bukkit.entity.Chicken.class, c -> {
            c.setInvisible(true);
            c.setSilent(true);
            c.setInvulnerable(true);
            c.setAware(false);
            c.setGravity(false);
            c.setCollidable(false);
            c.setPersistent(false);
            c.setBaby();
            c.setAgeLock(true);
            c.setEggLayTime(Integer.MAX_VALUE);
        });
        tagged(anchor, "balloon_anchor");
        anchor.setLeashHolder(player);
        ItemDisplay balloon = anchor.getWorld().spawn(start, ItemDisplay.class, d -> {
            ItemStack look = modelled(Material.LEATHER_HORSE_ARMOR, "gadget/balloon_3d");
            look.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(party()));
            d.setItemStack(look);
            d.setPersistent(false);
            d.setInterpolationDuration(3);
            d.setTransformation(new Transformation(new Vector3f(0, 0.45f, 0), new AxisAngle4f(), new Vector3f(1f), new AxisAngle4f()));
        });
        tagged(balloon, "balloon");
        anchor.addPassenger(balloon);
        anchors.put(id, anchor);
        balloons.put(id, balloon);
        sound(player.getLocation(), "entity.chicken.egg", 0.5f, 0.6f);
        every(600, t -> {
        }, () -> popBalloon(id, true));
    }

    /** Takes the player's balloon away (with a pop if it burst). */
    private void popBalloon(UUID id, boolean pop) {
        ItemDisplay balloon = balloons.remove(id);
        org.bukkit.entity.Chicken anchor = anchors.remove(id);
        if (pop && balloon != null && balloon.isValid()) {
            Location at = balloon.getLocation().add(0, 1.1, 0);
            at.getWorld().spawnParticle(Particle.DUST, at, 20, 0.25, 0.25, 0.25, 0, dust(Color.WHITE, 1f));
            sound(at, "entity.firework_rocket.blast", 0.6f, 2f);
        }
        if (balloon != null) balloon.remove();
        if (anchor != null) anchor.remove();
    }

    private void floatBalloon(Player player, ItemDisplay balloon) {
        org.bukkit.entity.Chicken anchor = anchors.get(player.getUniqueId());
        if (!balloon.isValid() || anchor == null || !anchor.isValid() || anchor.getWorld() != player.getWorld()) {
            popBalloon(player.getUniqueId(), false);
            return;
        }
        double yaw = Math.toRadians(player.getLocation().getYaw());
        // above the player's right shoulder and a little behind, bobbing; it lags behind when they move
        Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw)).multiply(0.6);
        Vector back = new Vector(Math.sin(yaw), 0, -Math.cos(yaw)).multiply(0.45);
        Location want = player.getLocation().add(right).add(back).add(0, 2.15 + Math.sin(tick * 0.09) * 0.12, 0);
        Vector pull = want.toVector().subtract(anchor.getLocation().toVector());
        if (pull.lengthSquared() > 36) {
            anchor.teleport(want);
            anchor.setVelocity(new Vector());
        } else {
            anchor.setVelocity(anchor.getVelocity().multiply(0.6).add(pull.multiply(0.12)));
        }
        // tilt with the swing, and turn a little on its own
        Vector v = anchor.getVelocity();
        float tiltX = (float) Math.max(-0.5, Math.min(0.5, v.getZ() * 2.2));
        float tiltZ = (float) Math.max(-0.5, Math.min(0.5, -v.getX() * 2.2));
        float wobble = (float) Math.sin(tick * 0.07) * 0.08f;
        move(balloon, new Matrix4f().rotateX(tiltX).rotateZ(tiltZ + wobble).rotateY(tick * 0.02f).translate(0, 0.45f, 0));
    }

    @EventHandler(ignoreCancelled = true)
    public void onUnleash(org.bukkit.event.entity.EntityUnleashEvent event) {
        if (event.getEntity().getScoreboardTags().contains(TAG) || anchors.containsValue(event.getEntity())) event.setDropLeash(false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTouchAnchor(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (anchors.containsValue(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerUnleash(org.bukkit.event.player.PlayerUnleashEntityEvent event) {
        if (anchors.containsValue(event.getEntity())) event.setCancelled(true);
    }

    /** A little tune with notes floating up. */
    private void melody(Player player) {
        int[][] tunes = {{0, 4, 7, 12, 11, 7, 9, 12}, {7, 9, 7, 5, 4, 5, 7, 0}, {12, 7, 9, 4, 5, 7, 12, 16}};
        String[] instruments = {"block.note_block.harp", "block.note_block.bell", "block.note_block.flute", "block.note_block.chime"};
        int[] tune = tunes[ThreadLocalRandom.current().nextInt(tunes.length)];
        String instrument = instruments[ThreadLocalRandom.current().nextInt(instruments.length)];
        every(tune.length * 5, t -> {
            if (t % 5 != 0) return;
            int note = tune[t / 5];
            Location head = player.getLocation().add(0, 2.3, 0);
            sound(head, instrument, 0.9f, (float) Math.pow(2, (note - 12) / 12.0));
            player.getWorld().spawnParticle(Particle.NOTE, head.clone().add(rand(0.6), rand(0.2), rand(0.6)), 0, note / 24.0, 0, 0, 1);
        }, null);
    }

    /** A beam of sparkles to where the player looks, bursting into stars. */
    private void magicWand(Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, look, 16);
        double length = hit == null ? 16 : hit.getHitPosition().distance(eye.toVector());
        sound(eye, "block.amethyst_block.resonate", 1f, 1.6f);
        sound(eye, "entity.illusioner.cast_spell", 0.6f, 1.6f);
        every(8, t -> {
            for (double d = t * 2; d < Math.min(length, t * 2 + 2); d += 0.25) {
                Location at = eye.clone().add(look.clone().multiply(d));
                player.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0.03, 0.03, 0.03, 0,
                        new Particle.DustTransition(Color.fromRGB(0xC77DFF), Color.fromRGB(0xFFF3B0), 0.9f));
                if (ThreadLocalRandom.current().nextInt(4) == 0) player.getWorld().spawnParticle(Particle.END_ROD, at, 0, 0, 0, 0, 0);
            }
        }, () -> {
            Location end = eye.clone().add(look.clone().multiply(length - 0.3));
            player.getWorld().spawnParticle(Particle.FIREWORK, end, 25, 0.1, 0.1, 0.1, 0.12);
            player.getWorld().spawnParticle(Particle.ENCHANT, end, 40, 0.4, 0.4, 0.4, 0.6);
            player.getWorld().spawnParticle(Particle.DUST, end, 20, 0.4, 0.4, 0.4, 0, dust(Color.fromRGB(0xFFE680), 1.2f));
            sound(end, "entity.firework_rocket.twinkle", 0.8f, 1.4f);
        });
    }

    /** A short teleport forward (never into a wall). */
    private void blink(Player player) {
        Location from = player.getLocation();
        Vector look = from.getDirection().setY(0);
        if (look.lengthSquared() < 0.01) return;
        look.normalize();
        if (from.getPitch() < -35) look.setY(0.45).normalize(); // looking up: blink up a little too
        Location body = from.clone().add(0, 1, 0);
        RayTraceResult wall = player.getWorld().rayTraceBlocks(body, look, 7.5, org.bukkit.FluidCollisionMode.NEVER, true);
        double max = wall == null ? 7 : wall.getHitPosition().distance(body.toVector()) - 0.7;
        Location to = null;
        for (double d = max; d >= 1; d -= 0.5) {
            Location test = from.clone().add(look.clone().multiply(d));
            Block feet = test.getBlock(), head = feet.getRelative(0, 1, 0);
            if (feet.isPassable() && head.isPassable()) {
                to = test;
                break;
            }
        }
        if (to == null) {
            sound(from, "entity.enderman.hurt", 0.4f, 1.6f);
            return;
        }
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, from.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.05);
        Vector line = to.toVector().subtract(from.toVector());
        for (int i = 0; i < 12; i++) {
            player.getWorld().spawnParticle(Particle.DUST, from.clone().add(0, 1, 0).add(line.clone().multiply(i / 12.0)), 1, 0.05, 0.05, 0.05, 0,
                    dust(Color.fromRGB(0x9B5CFF), 0.9f));
        }
        Vector velocity = player.getVelocity();
        player.teleport(to);
        player.setVelocity(velocity);
        player.setFallDistance(0);
        player.getWorld().spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.4);
        sound(to, "entity.enderman.teleport", 0.7f, 1.4f);
    }

    /** A TNT block that hisses and flashes, then "explodes" (just particles and sound). */
    private void fakeTnt(Player player) {
        Location at = ground(player);
        Location corner = at.clone();
        BlockDisplay tnt = at.getWorld().spawn(corner, BlockDisplay.class, d -> {
            d.setBlock(Material.TNT.createBlockData());
            d.setTransformation(new Transformation(new Vector3f(-0.5f, 0, -0.5f), new AxisAngle4f(), new Vector3f(1f), new AxisAngle4f()));
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
                // grows from its centre, so it never leans
                tnt.setTransformation(new Transformation(new Vector3f(-s / 2, (1 - s) / 2, -s / 2), new AxisAngle4f(), new Vector3f(s), new AxisAngle4f()));
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

    // ---------------------------------------------------------------- suit bonuses

    double abilityCooldown(String key) {
        return switch (key) {
            case "bat_swarm", "raise_dead" -> 15;
            case "disco_floor", "bee_swarm" -> 12;
            case "thunder_strike", "battle_cry" -> 5;
            case "web_swing" -> 1.5;
            case "bake_sale" -> 3;
            case "smoke_bomb", "bone_rattle" -> 6;
            case "water_splash" -> 5;
            case "super_leap" -> 1.5;
            default -> 1.5;
        };
    }

    /** A right-click bonus. */
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
                burstItems(at, ItemStack.of(Material.BONE), 8, 0.25, 40);
                world.spawnParticle(Particle.DUST, at, 30, 0.5, 0.6, 0.5, 0, dust(Color.fromRGB(0xE8E8E0), 1.2f));
            }
            case "disco_floor" -> discoFloor(player);
            case "web_swing" -> {
                if (parkour(player)) return;
                RayTraceResult hit = world.rayTraceBlocks(eye, look, 30);
                if (hit == null) {
                    sound(eye, "entity.spider.ambient", 0.6f, 1.6f);
                    return;
                }
                Location to = hit.getHitPosition().toLocation(world);
                Vector line = to.toVector().subtract(eye.toVector());
                for (double d = 0; d < line.length(); d += 0.4) {
                    world.spawnParticle(Particle.DUST, eye.clone().add(line.clone().normalize().multiply(d)), 1, 0, 0, 0, 0, dust(Color.WHITE, 0.6f));
                }
                world.spawnParticle(Particle.ITEM, to, 10, 0.2, 0.2, 0.2, 0.05, new ItemStack(Material.COBWEB));
                player.setVelocity(line.normalize().multiply(Math.min(2.2, 0.8 + line.length() * 0.06)).add(new Vector(0, 0.3, 0)));
                player.setFallDistance(0);
                sound(eye, "entity.spider.step", 1f, 1.6f);
                sound(eye, "item.crossbow.shoot", 0.6f, 1.8f);
            }
            case "battle_cry" -> {
                sound(eye, "event.raid.horn", 1f, 1.2f);
                Location at = player.getLocation().add(0, 1, 0);
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2 * i / 24;
                    world.spawnParticle(Particle.SWEEP_ATTACK, at.clone().add(Math.cos(a) * 1.6, 0, Math.sin(a) * 1.6), 1);
                }
                world.spawnParticle(Particle.ANGRY_VILLAGER, at.clone().add(0, 1.2, 0), 5, 0.4, 0.2, 0.4, 0);
                if (!parkour(player)) player.setVelocity(look.clone().setY(0).normalize().multiply(0.9).setY(0.45));
            }
            case "raise_dead" -> raiseDead(player);
            case "thunder_strike" -> {
                RayTraceResult hit = world.rayTraceBlocks(eye, look, 25);
                Location strike = hit == null ? eye.clone().add(look.clone().multiply(12)) : hit.getHitPosition().toLocation(world);
                world.strikeLightningEffect(strike);
                world.spawnParticle(Particle.ELECTRIC_SPARK, strike, 40, 0.6, 0.6, 0.6, 0.3);
                world.spawnParticle(Particle.FLASH, strike, 1, 0, 0, 0, 0, Color.WHITE);
            }
            case "bake_sale" -> {
                sound(eye, "entity.generic.eat", 1f, 1.4f);
                Location at = eye.clone().add(look.clone().multiply(0.8));
                burstItems(at, new ItemStack(Material.CAKE), 2, 0.25, 40);
                burstItems(at, new ItemStack(Material.BREAD), 3, 0.25, 40);
                burstItems(at, new ItemStack(Material.PUMPKIN_PIE), 2, 0.25, 40);
                burstItems(at, modelled(Material.COOKIE, "gadget/cookie"), 4, 0.25, 40);
                world.spawnParticle(Particle.WHITE_ASH, at, 30, 0.4, 0.4, 0.4, 0.02);
            }
            case "bee_swarm" -> beeSwarm(player);
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

    /** Disco: the floor around you lights up in colours for a few seconds (only shown, nothing changes). */
    private void discoFloor(Player player) {
        Material[] floors = {Material.RED_STAINED_GLASS, Material.LIME_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS,
                Material.YELLOW_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS, Material.ORANGE_STAINED_GLASS};
        Location centre = player.getLocation();
        List<Block> tiles = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (dx * dx + dz * dz > 10) continue;
            Block b = centre.clone().add(dx, -0.5, dz).getBlock();
            if (b.getType().isOccluding()) tiles.add(b);
        }
        int[] tune = {0, 4, 7, 12, 7, 4};
        every(120, t -> {
            if (t % 5 == 0) {
                for (int i = 0; i < tiles.size(); i++) {
                    Block b = tiles.get(i);
                    BlockData tile = floors[(i + t / 5) % floors.length].createBlockData();
                    for (Player viewer : b.getWorld().getPlayers()) viewer.sendBlockChange(b.getLocation(), tile);
                }
                sound(centre, "block.note_block.bit", 0.6f, (float) Math.pow(2, (tune[(t / 5) % tune.length] - 6) / 12.0));
                centre.getWorld().spawnParticle(Particle.NOTE, centre.clone().add(rand(2), 2.2, rand(2)), 0, ThreadLocalRandom.current().nextDouble(), 0, 0, 1);
            }
            if (t % 10 == 0) sound(centre, "block.note_block.basedrum", 0.8f, 0.8f);
        }, () -> {
            for (Player viewer : centre.getWorld().getPlayers()) for (Block b : tiles) viewer.sendBlockChange(b.getLocation(), b.getBlockData());
        });
    }

    /** Necromancer: three of the dead climb out of the ground around you, then crumble. */
    private void raiseDead(Player player) {
        String[] skins = {"MHF_Skeleton", "MHF_Zombie", "MHF_Skeleton"};
        sound(player.getLocation(), "entity.zombie_villager.converted", 0.8f, 0.6f);
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(player.getLocation().getYaw() + 90 + (i - 1) * 50);
            Location at = player.getLocation().add(-Math.sin(a) * 2.2, -1.9, Math.cos(a) * 2.2);
            at.setYaw(player.getLocation().getYaw() + 180);
            String skin = skins[i];
            Mannequin dead = player.getWorld().spawn(at, Mannequin.class, m -> {
                m.setProfile(ResolvableProfile.resolvableProfile().name(skin).build());
                m.setDescription(null);
                m.setImmovable(true);
                m.setInvulnerable(true);
                m.setGravity(false);
                m.setCollidable(false);
            });
            tagged(dead, "dead");
            Location rise = at.clone();
            every(80, t -> {
                if (!dead.isValid()) return;
                if (t < 20) {
                    dead.teleport(rise.clone().add(0, t * 0.095, 0));
                    dead.getWorld().spawnParticle(Particle.BLOCK, rise.clone().add(0, 1.9, 0), 4, 0.3, 0.05, 0.3, 0,
                            rise.clone().add(0, 1.5, 0).getBlock().getBlockData().getMaterial().isSolid()
                                    ? rise.clone().add(0, 1.5, 0).getBlock().getBlockData() : Material.DIRT.createBlockData());
                } else if (t % 10 == 0) {
                    dead.swingMainHand();
                    dead.getWorld().spawnParticle(Particle.SOUL, dead.getLocation().add(0, 1.6, 0), 1, 0.2, 0.2, 0.2, 0.01);
                }
            }, () -> {
                if (!dead.isValid()) return;
                Location end = dead.getLocation().add(0, 1, 0);
                end.getWorld().spawnParticle(Particle.ITEM, end, 16, 0.3, 0.5, 0.3, 0.08, new ItemStack(Material.BONE));
                end.getWorld().spawnParticle(Particle.LARGE_SMOKE, end, 8, 0.3, 0.5, 0.3, 0.02);
                sound(end, "entity.skeleton.death", 0.6f, 1.2f);
                dead.remove();
            });
        }
    }

    /** Bumblebee: a swarm of bees buzzes around you for a few seconds. */
    private void beeSwarm(Player player) {
        List<org.bukkit.entity.Bee> bees = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            org.bukkit.entity.Bee bee = player.getWorld().spawn(player.getLocation().add(0, 1.5, 0), org.bukkit.entity.Bee.class, b -> {
                b.setInvulnerable(true);
                b.setAware(false);
                b.setGravity(false);
            });
            tagged(bee, "bee");
            bees.add(bee);
        }
        sound(player.getLocation(), "entity.bee.loop_aggressive", 1f, 1.2f);
        every(100, t -> {
            if (!player.isOnline()) return;
            for (int i = 0; i < bees.size(); i++) {
                double a = t * 0.18 + i * Math.PI / 3;
                Location at = player.getLocation().add(Math.cos(a) * 1.4, 1.2 + Math.sin(t * 0.3 + i) * 0.4, Math.sin(a) * 1.4);
                at.setYaw((float) Math.toDegrees(a));
                bees.get(i).teleport(at);
            }
            if (t % 20 == 0) sound(player.getLocation(), "entity.bee.loop", 0.6f, 1.2f);
        }, () -> bees.forEach(b -> {
            b.getWorld().spawnParticle(Particle.FALLING_HONEY, b.getLocation(), 2, 0.1, 0.1, 0.1, 0);
            b.remove();
        }));
    }

    /** The passive bonus of the full suit the player wears (astronaut, elf, bunny), kept on while they wear it. */
    private void passive(Player player) {
        Catalog.SuitSet set = collectibles.enabled() && !parkour(player) ? collectibles.fullSet(player) : null;
        String want = set != null && set.suit().passive() ? set.suit().ability() : null;
        String now = passives.get(player.getUniqueId());
        if (want == null ? now != null : !want.equals(now)) {
            bonus(player, want);
            if (want == null) passives.remove(player.getUniqueId());
            else passives.put(player.getUniqueId(), want);
        }
        if (want == null) return;
        Location feet = player.getLocation();
        boolean ground = ((Entity) player).isOnGround();
        switch (want) {
            case "low_gravity" -> {
                if (!ground && tick % 4 == 0) {
                    show(player, Particle.END_ROD, feet.clone().add(0, 0.1, 0), 1, 0.15, 0, 0.15, 0.005, null);
                    show(player, Particle.DUST, feet.clone().add(0, 0.2, 0), 1, 0.2, 0.05, 0.2, 0, dust(Color.fromRGB(0xBFD7FF), 0.8f));
                }
            }
            case "jingle_dash" -> {
                boolean moving = player.getVelocity().setY(0).lengthSquared() > 0.003 || player.isSprinting();
                if (moving && ground && tick % 8 == 0) {
                    sound(feet, "block.note_block.chime", 0.35f, 1.4f + (tick % 16 == 0 ? 0.3f : 0));
                    show(player, Particle.SNOWFLAKE, feet.clone().add(0, 0.2, 0), 2, 0.2, 0.05, 0.2, 0.01, null);
                }
            }
            case "speed_force" -> {
                if (tick % 2 == 0 && (player.isSprinting() || player.getVelocity().setY(0).lengthSquared() > 0.01)) {
                    show(player, Particle.ELECTRIC_SPARK, feet.clone().add(0, 0.6, 0), 2, 0.2, 0.3, 0.2, 0.02, null);
                    show(player, Particle.DUST, feet.clone().add(0, 0.2, 0), 1, 0.1, 0.05, 0.1, 0, dust(Color.fromRGB(0xFFCF3A), 0.8f));
                }
            }
            case "mermaid_grace" -> {
                if (tick % 4 == 0) show(player, Particle.BUBBLE_POP, feet.clone().add(0, 0.6, 0), 2, 0.25, 0.4, 0.25, 0.01, null);
                if (player.isInWater()) player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, 0, false, false, false));
            }
            case "bunny_hop" -> {
                if (!ground && player.getVelocity().getY() > 0.35 && tick % 4 == 0) {
                    show(player, Particle.DUST, feet.clone().add(0, 0.1, 0), 4, 0.2, 0.05, 0.2, 0, dust(Color.fromRGB(0xF2C4D8), 1f));
                }
            }
            default -> {
            }
        }
    }

    /** Puts the attribute changes of a passive bonus on the player (or takes them all off for null). */
    private void bonus(Player player, String ability) {
        for (Attribute attribute : List.of(Attribute.GRAVITY, Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH, Attribute.SAFE_FALL_DISTANCE,
                Attribute.WATER_MOVEMENT_EFFICIENCY)) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) instance.removeModifier(bonusKey);
        }
        if (ability == null) return;
        switch (ability) {
            case "low_gravity" -> {
                add(player, Attribute.GRAVITY, -0.62, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
                add(player, Attribute.SAFE_FALL_DISTANCE, 20, AttributeModifier.Operation.ADD_NUMBER);
            }
            case "jingle_dash" -> add(player, Attribute.MOVEMENT_SPEED, 0.3, AttributeModifier.Operation.ADD_SCALAR);
            case "speed_force" -> add(player, Attribute.MOVEMENT_SPEED, 0.45, AttributeModifier.Operation.ADD_SCALAR);
            case "mermaid_grace" -> {
                add(player, Attribute.JUMP_STRENGTH, 0.1, AttributeModifier.Operation.ADD_NUMBER);
                add(player, Attribute.WATER_MOVEMENT_EFFICIENCY, 1.0, AttributeModifier.Operation.ADD_NUMBER);
            }
            case "bunny_hop" -> {
                add(player, Attribute.JUMP_STRENGTH, 0.25, AttributeModifier.Operation.ADD_NUMBER);
                add(player, Attribute.SAFE_FALL_DISTANCE, 4, AttributeModifier.Operation.ADD_NUMBER);
            }
            default -> {
            }
        }
    }

    private void add(Player player, Attribute attribute, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.addTransientModifier(new AttributeModifier(bonusKey, amount, operation, EquipmentSlotGroup.ANY));
    }

    private boolean frog(Player player) {
        Catalog.SuitSet set = collectibles.enabled() && !parkour(player) ? collectibles.fullSet(player) : null;
        return set != null && set.key().equals("frog");
    }

    /** Frogs: hold sneak on the ground to charge a leap, let go to jump. */
    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking()) {
            if (frog(player) && ((Entity) player).isOnGround()) charging.put(player.getUniqueId(), tick);
            return;
        }
        Long started = charging.remove(player.getUniqueId());
        if (started == null || !frog(player)) return;
        double power = Math.min(1, (tick - started) / 24.0);
        player.sendActionBar(Component.empty());
        if (power < 0.25 || !collectibles.ready(player, "ability:super_leap", abilityCooldown("super_leap"))) return;
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() > 0) look.normalize();
        player.setVelocity(look.multiply(0.5 + 1.25 * power).setY(0.55 + 0.75 * power));
        player.setFallDistance(0);
        player.getWorld().spawnParticle(Particle.ITEM_SLIME, player.getLocation(), (int) (15 + 25 * power), 0.4, 0.1, 0.4, 0.1);
        sound(player.getLocation(), "entity.frog.long_jump", 1f, 1.2f - (float) power * 0.4f);
    }

    /** The charge bar of a frog's leap. */
    private void charge(Player player) {
        Long started = charging.get(player.getUniqueId());
        if (started == null) return;
        if (!player.isSneaking() || !frog(player)) {
            charging.remove(player.getUniqueId());
            return;
        }
        double power = Math.min(1, (tick - started) / 24.0);
        int filled = (int) Math.round(power * 10);
        String bar = "<#5BC24A>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(10 - filled);
        player.sendActionBar(plugin.messages().component("collectibles.ability.super_leap.charge", Placeholder.parsed("bar", bar)));
        if (tick % 4 == 0) {
            player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 0.1, 0), 3, 0.3, 0, 0.3, 0,
                    dust(Color.fromRGB(0x5BC24A), 0.6f + (float) power));
            if (filled == 10 && tick % 8 == 0) sound(player.getLocation(), "entity.frog.ambient", 0.6f, 1.4f);
        }
    }

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
            case "rush" -> {
                world.spawnParticle(Particle.PORTAL, at, 30, 0.3, 0.5, 0.3, 0.5);
                sound(at, "entity.enderman.teleport", 0.6f, 1.4f);
            }
            case "boom" -> {
                world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
                world.spawnParticle(Particle.FIREWORK, at, 40, 0.2, 0.2, 0.2, 0.3);
                confetti(at, 40, 0.8);
                sound(at, "entity.generic.explode", 1f, 1.2f);
            }
            case "hook" -> {
                if (!(projectile.getShooter() instanceof Player player) || !player.isOnline() || parkour(player)) return;
                Vector pull = at.toVector().subtract(player.getLocation().toVector());
                double distance = pull.length();
                if (distance < 1.5 || distance > 40) return;
                player.setVelocity(pull.normalize().multiply(Math.min(2.4, 0.6 + distance * 0.11)).add(new Vector(0, 0.35, 0)));
                player.setFallDistance(0);
                world.spawnParticle(Particle.CRIT, at, 12, 0.1, 0.1, 0.1, 0.2);
                sound(at, "block.chain.place", 1f, 1.2f);
                sound(player.getLocation(), "entity.fishing_bobber.retrieve", 1f, 0.9f);
            }
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
        int radius = Math.max(1, plugin.getConfig().getInt("collectibles.paintball-radius", 3));
        for (int x = -radius; x <= radius; x++) for (int y = -radius; y <= radius; y++) for (int z = -radius; z <= radius; z++) {
            double distance = Math.sqrt(x * x + y * y + z * z);
            if (distance > radius + 0.3) continue;
            // the edge is ragged, like a real splat
            if (distance > radius - 0.7 && ThreadLocalRandom.current().nextInt(3) == 0) continue;
            Block block = hit.getRelative(x, y, z);
            if (!block.getType().isSolid() || block.getType().isInteractable()) continue;
            if (exposed(block)) splat.add(block);
        }
        List<Player> viewers = at.getWorld().getPlayers().stream().filter(p -> p.getLocation().distanceSquared(at) < 64 * 64).toList();
        for (Player viewer : viewers) for (Block block : splat) viewer.sendBlockChange(block.getLocation(), paint);
        at.getWorld().spawnParticle(Particle.BLOCK, at, 45, 0.6, 0.6, 0.6, 0, paint);
        at.getWorld().spawnParticle(Particle.DUST, at, 30, 0.8, 0.8, 0.8, 0,
                dust(paint.getMaterial() == Material.PINK_CONCRETE ? Color.fromRGB(0xFF7BE5) : party(), 1.4f));
        sound(at, "entity.slime.squish", 1f, 1.3f);
        sound(at, "entity.slime.squish_small", 1f, 0.9f);
        later(120, () -> {
            for (Player viewer : viewers) {
                if (!viewer.isOnline()) continue;
                for (Block block : splat) viewer.sendBlockChange(block.getLocation(), block.getBlockData());
            }
        });
    }

    private static boolean exposed(Block block) {
        for (org.bukkit.block.BlockFace face : List.of(org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN, org.bukkit.block.BlockFace.NORTH,
                org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST)) {
            if (!block.getRelative(face).getType().isOccluding()) return true;
        }
        return false;
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
    void show(Player wearer, Particle particle, Location at, int count, double dx, double dy, double dz, double speed, Object data) {
        for (Player viewer : wearer.getWorld().getPlayers()) {
            if (viewer != wearer && !viewer.canSee(wearer)) continue;
            if (viewer.getLocation().distanceSquared(at) > 48 * 48) continue;
            viewer.spawnParticle(particle, at, count, dx, dy, dz, speed, data);
        }
    }

    /** The textured pieces of each cloak (models under fractal:cloak/part/). */
    private static List<String> parts(String key) {
        return switch (key) {
            case "sparkle_aura" -> List.of("sparkle", "sparkle", "sparkle", "sparkle", "sparkle");
            case "halo" -> List.of("halo");
            case "rain_cloud" -> List.of("cloud");
            case "orbit" -> List.of("orb_blue", "orb_pink", "orb_gold");
            case "flame_ring" -> List.of("flame", "flame", "flame", "flame", "flame", "flame");
            case "angel_wings" -> List.of("wing_left", "wing_right");
            case "sun_rays" -> List.of("sun3d", "sun_ring");
            case "snow_globe" -> List.of("snowflake", "snowflake", "snowflake", "snowflake", "snowflake", "snowflake");
            case "blossom_aura" -> List.of("petal", "petal", "petal", "petal", "petal", "petal");
            case "spooky_aura" -> List.of("bat", "bat", "bat");
            default -> List.of();
        };
    }

    private void rig(Player player, String key) {
        Rig rig = new Rig(key);
        for (String part : parts(key)) {
            ItemDisplay display = rider(player, "cloak/part/" + part);
            display.setBrightness(new Display.Brightness(15, 15));
            if (part.equals("sun_ring")) display.setBillboard(Display.Billboard.CENTER);   // the rays always face you
            rig.parts.add(display);
        }
        rigs.put(player.getUniqueId(), rig);
    }

    private void unrig(UUID id) {
        Rig rig = rigs.remove(id);
        if (rig != null) rig.parts.forEach(Entity::remove);
    }

    /**
     * Moves each cloak piece. Riders sit on top of the head: (0, 0, 0) is the head's top, (0, -1.8, 0) the feet.
     * Pieces that belong to the body (wings, the sun) turn with the body.
     */
    private void pose(Player player, Rig rig, boolean shown) {
        for (ItemDisplay part : rig.parts) {
            if (!part.isValid()) {
                unrig(player.getUniqueId());
                rig(player, rig.key);
                return;
            }
        }
        double t = tick;
        float head = player.getLocation().getYaw();
        boolean walking = rig.lastX != null && Math.hypot(player.getX() - rig.lastX, player.getZ() - rig.lastZ) > 0.05;
        if (rig.body == null) rig.body = head;
        float diff = wrap(head - rig.body);
        if (walking) rig.body += diff * 0.5f;                     // walking: the body turns to face where you go
        else if (Math.abs(diff) > 45) rig.body += diff - Math.signum(diff) * 45; // standing: it lags up to 45 degrees
        float yaw = (float) Math.toRadians(rig.body);
        float sneak = player.isSneaking() ? 0.3f : 0;
        int n = rig.parts.size();
        for (int i = 0; i < n; i++) {
            Matrix4f m = new Matrix4f();
            double phase = Math.PI * 2 * i / n;
            switch (rig.key) {
                case "sparkle_aura" -> {
                    double a = t * 0.05 + phase;
                    float y = -1.2f + (float) ((i * 0.37 + t * 0.02) % 1.4) + sneak;
                    float s = 0.18f + 0.1f * (float) Math.abs(Math.sin(t * 0.15 + i));
                    m.translate((float) Math.cos(a) * 0.7f, y, (float) Math.sin(a) * 0.7f).rotateY((float) (t * 0.1)).scale(s);
                }
                case "halo" -> m.translate(0, 0.28f + (float) Math.sin(t * 0.08) * 0.04f, 0).rotateY((float) (t * 0.03)).scale(0.62f);
                case "rain_cloud" -> m.translate(0, 0.85f + (float) Math.sin(t * 0.06) * 0.06f, 0).rotateY(-yaw).scale(1.15f);
                case "orbit" -> {
                    double a = t * 0.11 + phase;
                    float tilt = (float) Math.sin(a + i) * 0.35f;
                    m.translate((float) Math.cos(a) * 0.95f, -0.85f + tilt + sneak, (float) Math.sin(a) * 0.95f).rotateY((float) (t * 0.2)).scale(0.45f);
                }
                case "flame_ring" -> {
                    double a = t * 0.07 + phase;
                    float flicker = 0.38f + 0.06f * (float) Math.sin(t * 0.5 + i * 2);
                    m.translate((float) Math.cos(a) * 0.85f, -1.6f + flicker / 2 + sneak, (float) Math.sin(a) * 0.85f).rotateY((float) -a).scale(flicker);
                }
                case "angel_wings" -> {
                    float side = i == 0 ? 1 : -1; // left, right
                    // the model's spine is its centre; the wing grows out to the side and folds back as it flaps
                    double moved = rig.lastX == null ? 0 : Math.hypot(player.getX() - rig.lastX, player.getZ() - rig.lastZ);
                    boolean air = !((Entity) player).isOnGround() && !player.isFlying() && player.getVehicle() == null;
                    double deg;
                    if (air) deg = 15 + 45 * Math.abs(Math.sin(t * 0.32));              // flapping hard
                    else if (moved > 0.12) deg = 58 + 6 * Math.sin(t * (moved > 0.5 ? 0.45 : 0.28)); // swept back
                    else deg = 28 + 12 * Math.sin(t * 0.08);                              // open, breathing
                    float flap = (float) Math.toRadians(deg);
                    // pivots on the shoulder blades, a little behind the back, so they stay out of your own view
                    m.rotateY(-yaw).translate(0, -0.72f + sneak * 0.5f, -0.3f).rotateX(sneak * 0.8f)
                            .rotateY(-side * flap).scale(0.8f);   // an item display shows its model turned half round
                }
                case "sun_rays" -> {
                    // a little sun floating over the head, pulsing, its rays turning around it
                    float pulse = (float) Math.sin(t * 0.25) * 0.05f;
                    float y = 0.95f + (float) Math.sin(t * 0.07) * 0.06f;
                    if (i == 0) m.translate(0, y, 0).rotateY((float) (t * 0.04)).scale(0.6f + pulse);
                    else m.translate(0, y, 0).rotateZ((float) (t * 0.03)).scale(0.95f + pulse * 2);
                }
                case "snow_globe" -> {
                    double a = t * 0.03 + phase;
                    float fall = (float) ((t * 0.035 + i * 0.41) % 2.4);
                    float r = 0.65f + (i % 3) * 0.22f;
                    m.translate((float) Math.cos(a) * r, 0.4f - fall, (float) Math.sin(a) * r).rotateY((float) (t * 0.08 + i)).scale(0.22f);
                }
                case "blossom_aura" -> {
                    double a = t * 0.045 + phase;
                    float y = -0.3f - (float) ((t * 0.02 + i * 0.29) % 1.6);
                    m.translate((float) Math.cos(a) * 0.8f, y + sneak, (float) Math.sin(a) * 0.8f)
                            .rotateY((float) (t * 0.12 + i)).rotateX((float) Math.sin(t * 0.1 + i) * 0.6f).scale(0.2f);
                }
                case "spooky_aura" -> {
                    // bats circling, flapping, at different heights
                    double a = t * 0.09 + phase;
                    float r = 0.8f + 0.12f * (float) Math.sin(t * 0.05 + i);
                    float y = -0.35f - i * 0.35f + (float) Math.sin(t * 0.2 + i * 2) * 0.12f + sneak;
                    float wings = 0.5f * (0.45f + 0.55f * (float) Math.abs(Math.sin(t * 0.7 + i)));
                    m.translate((float) Math.cos(a) * r, y, (float) Math.sin(a) * r).rotateY((float) (Math.PI - a)).scale(wings, 0.5f, 0.5f);
                }
                default -> m.scale(0);
            }
            if (!shown) m = new Matrix4f().scale(0);
            ItemDisplay part = rig.parts.get(i);
            if (part.getYaw() != 0 || part.getPitch() != 0) part.setRotation(0, 0); // only our matrix turns it
            move(part, m);
        }
        rig.lastX = player.getX();
        rig.lastZ = player.getZ();
    }

    private static float wrap(float degrees) {
        degrees %= 360;
        if (degrees >= 180) degrees -= 360;
        if (degrees < -180) degrees += 360;
        return degrees;
    }

    /** The propeller on the propeller cap: on top of the head, turning with it, spinning. */
    private void spin(Player player, ItemDisplay propeller, boolean shown) {
        if (!propeller.isValid()) {
            propellers.remove(player.getUniqueId());
            worn(player);
            return;
        }
        float yaw = (float) Math.toRadians(player.getLocation().getYaw());
        float pitch = (float) Math.toRadians(player.getLocation().getPitch());
        float speed = player.getVelocity().lengthSquared() > 0.01 ? 1.6f : 0.9f;
        Matrix4f m = new Matrix4f().translate(0, -0.32f, 0).rotateY(-yaw).rotateX(pitch).translate(0, 0.66f, 0)
                .rotateY(tick * speed % (float) (Math.PI * 2)).scale(shown ? 0.62f : 0);
        propeller.setInterpolationDelay(0);
        propeller.setInterpolationDuration(2);
        propeller.setTransformationMatrix(m);
    }

    /** A few particles to go with the pieces (or, without the pack, instead of them). */
    private void cloak(Player player, String key) {
        Location base = player.getLocation();
        double t = tick;
        boolean pieces = plugin.menuStyle().custom();
        switch (key) {
            case "sparkle_aura" -> {
                if (tick % 4 != 0) return;
                Location at = base.clone().add(rand(0.7), ThreadLocalRandom.current().nextDouble(0.1, 2), rand(0.7));
                show(player, Particle.DUST, at, 1, 0, 0, 0, 0, dust(ThreadLocalRandom.current().nextBoolean() ? Color.WHITE : Color.fromRGB(0xFF7BE5), 0.8f));
                if (!pieces) show(player, Particle.END_ROD, at, 0, 0, 0, 0, 0, null);
            }
            case "halo" -> {
                if (pieces) {
                    if (tick % 10 == 0) show(player, Particle.END_ROD, base.clone().add(rand(0.3), 2.2, rand(0.3)), 0, 0, -0.02, 0, 1, null);
                    return;
                }
                Location centre = base.clone().add(0, 2.25, 0);
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI * 2 * i / 10 + t * 0.05;
                    show(player, Particle.DUST, centre.clone().add(Math.cos(a) * 0.32, 0, Math.sin(a) * 0.32), 1, 0, 0, 0, 0,
                            dust(Color.fromRGB(0xFFE680), 0.55f));
                }
            }
            case "rain_cloud" -> {
                Location cloud = base.clone().add(0, 2.55, 0);
                if (!pieces) show(player, Particle.CLOUD, cloud, 3, 0.35, 0.05, 0.35, 0, null);
                if (tick % 4 == 0) show(player, Particle.FALLING_WATER, cloud, 1, 0.25, 0, 0.25, 0, null);   // a light drizzle
            }
            case "orbit" -> {
                if (pieces && tick % 4 != 0) return;
                Location centre = base.clone().add(0, 1, 0);
                double a = t * 0.11;
                show(player, Particle.END_ROD, centre.clone().add(Math.cos(a) * 0.95, 0, Math.sin(a) * 0.95), 0, 0, 0, 0, 0, null);
            }
            case "flame_ring" -> {
                if (tick % 4 == 0) show(player, Particle.FLAME, base.clone().add(rand(0.8), 0.15, rand(0.8)), 1, 0, 0.02, 0, 0.01, null);
                if (tick % 8 == 0) show(player, Particle.SMOKE, base.clone().add(0, 0.2, 0), 2, 0.5, 0, 0.5, 0, null);
            }
            case "angel_wings" -> {
                if (pieces) {
                    if (tick % 8 == 0) show(player, Particle.END_ROD, base.clone().add(rand(0.5), 1.3, rand(0.5)), 0, 0, -0.03, 0, 1, null);
                    return;
                }
                if (tick % 4 == 0) wings(player);
            }
            case "sun_rays" -> {
                if (tick % 4 == 0) {
                    double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                    Location sun = base.clone().add(0, 2.75, 0);
                    for (int k = 1; k <= 5; k++) show(player, Particle.DUST, sun.clone().add(Math.cos(a) * k * 0.28, -k * 0.12, Math.sin(a) * k * 0.28),
                            1, 0, 0, 0, 0, dust(Color.fromRGB(k < 3 ? 0xFFF2A0 : 0xFFC83D), 0.7f));
                }
                if (tick % 6 == 0) show(player, Particle.DUST, base.clone().add(rand(0.6), 1.9 + rand(0.3), rand(0.6)), 1, 0, 0, 0, 0,
                        dust(Color.fromRGB(0xFFC83D), 0.8f));
            }
            case "snow_globe" -> {
                if (tick % 2 != 0) return;
                double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2), r = ThreadLocalRandom.current().nextDouble(0.4, 1.3);
                show(player, Particle.SNOWFLAKE, base.clone().add(Math.cos(a) * r, 2.4, Math.sin(a) * r), 0, 0, -0.08, 0, 1, null);
            }
            case "blossom_aura" -> {
                if (tick % 6 != 0) return;
                show(player, Particle.CHERRY_LEAVES, base.clone().add(0, 2, 0), 1, 0.6, 0.2, 0.6, 0, null);
            }
            case "spooky_aura" -> {
                if (tick % 4 == 0) show(player, Particle.DUST, base.clone().add(rand(0.7), ThreadLocalRandom.current().nextDouble(0.1, 1.9), rand(0.7)),
                        1, 0, 0, 0, 0, dust(ThreadLocalRandom.current().nextBoolean() ? Color.fromRGB(0xFF8A1F) : Color.fromRGB(0xFFB347), 0.9f));
                if (tick % 10 == 0) show(player, Particle.SMALL_FLAME, base.clone().add(rand(0.6), 0.1, rand(0.6)), 0, 0, 0.02, 0, 1, null);
            }
            default -> {
            }
        }
    }

    /** Wings of light behind the player (only without the resource pack). */
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
        Vector back = new Vector(Math.sin(yaw), 0, -Math.cos(yaw)).multiply(0.32);
        Vector side = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
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
        TextDisplay old = emotes.remove(player.getUniqueId());
        if (old != null) old.remove();
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

    /**
     * A gesture: a copy of the player (their skin and outfit) appears in front of them, facing them, does the gesture
     * and vanishes in a puff. The player themselves can keep moving.
     */
    void gesture(Player player, String key) {
        Mannequin old = clones.remove(player.getUniqueId());
        if (old != null) old.remove();
        Location from = player.getLocation();
        Vector ahead = from.getDirection().setY(0);
        if (ahead.lengthSquared() < 0.01) ahead = new Vector(0, 0, 1);
        ahead.normalize();
        Location at = from.clone().add(ahead.clone().multiply(2.2));
        // stand it on the ground in front (or level with the player)
        for (int down = 0; down < 3 && at.getBlock().getRelative(0, -1, 0).isPassable(); down++) at.add(0, -1, 0);
        while (!at.getBlock().isPassable() && at.getY() < from.getY() + 2) at.add(0, 1, 0);
        float yaw = from.getYaw() + 180;
        at.setYaw(yaw);
        at.setPitch(0);
        World world = player.getWorld();
        Mannequin clone = world.spawn(at, Mannequin.class, m -> {
            m.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            m.setDescription(null);
            m.setImmovable(true);
            m.setInvulnerable(true);
            m.setGravity(false);
            m.setSilent(true);
            m.setCollidable(false);
            m.setCustomNameVisible(false);
            var mine = player.getEquipment();
            var its = m.getEquipment();
            its.setHelmet(mine.getHelmet());
            its.setChestplate(mine.getChestplate());
            its.setLeggings(mine.getLeggings());
            its.setBoots(mine.getBoots());
        });
        tagged(clone, "clone");
        clones.put(player.getUniqueId(), clone);
        int length = switch (key) {
            case "moonwalk", "levitate", "giant", "sleep", "magic_trick", "zombie_walk", "swim" -> 62;
            default -> 46;
        };
        say(clone, key, length);
        world.spawnParticle(Particle.POOF, at.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
        world.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0, dust(Color.fromRGB(0xB266FF), 1f));
        sound(at, "entity.illusioner.mirror_move", 0.7f, 1.4f);
        Location centre = at.clone();
        Vector away = ahead.clone();                     // from the player towards the copy
        AttributeInstance size = clone.getAttribute(Attribute.SCALE);
        every(length, t -> {
            if (!clone.isValid()) return;
            switch (key) {
                case "wave" -> {
                    if (t % 5 == 0) clone.swingMainHand();
                    look(clone, yaw + (float) Math.sin(t * 0.3) * 12, -8);
                    if (t == 2) sound(centre, "entity.villager.ambient", 0.6f, 1.6f);
                }
                case "clap" -> {
                    if (t % 5 == 0) {
                        if ((t / 5) % 2 == 0) clone.swingMainHand();
                        else clone.swingOffHand();
                        sound(centre, "block.note_block.snare", 0.7f, 1.4f);
                    }
                }
                case "bow" -> {
                    float p = t < 10 ? t * 7f : t < 30 ? 70f : Math.max(0, 70f - (t - 30) * 7f);
                    look(clone, yaw, p);
                    if (t == 6) clone.setPose(Pose.SNEAKING, true);
                    if (t == 32) clone.setPose(Pose.STANDING, true);
                    if (t == 6) sound(centre, "block.note_block.harp", 0.6f, 0.8f);
                }
                case "spin" -> {
                    look(clone, yaw + (t + 1) * 16f, 0);
                    if (t % 11 == 0) sound(centre, "entity.player.attack.sweep", 0.5f, 1.4f);
                }
                case "facepalm" -> {
                    if (t == 2) {
                        clone.swingMainHand();
                        sound(centre, "entity.villager.no", 0.8f, 0.9f);
                    }
                    look(clone, yaw + (float) Math.sin(t * 0.25) * 18, t < 4 ? t * 14f : 55f);
                }
                case "headbang" -> {
                    look(clone, yaw, (t / 4) % 2 == 0 ? 50 : -15);
                    if (t % 8 == 0) sound(centre, "block.note_block.basedrum", 0.8f, 1f);
                    if (t % 8 == 4) sound(centre, "block.note_block.snare", 0.6f, 1f);
                }
                case "celebrate" -> {
                    if (t % 5 == 0) {
                        clone.swingMainHand();
                        clone.swingOffHand();
                    }
                    double hop = Math.abs(Math.sin(t * Math.PI / 12)) * 0.6;
                    Location up = centre.clone().add(0, hop, 0);
                    up.setYaw(yaw);
                    clone.teleport(up);
                    if (t == 6 || t == 18 || t == 30) {
                        Location top = up.clone().add(0, 2.2, 0);
                        world.spawnParticle(Particle.FIREWORK, top, 20, 0.3, 0.3, 0.3, 0.12);
                        confetti(top, 30, 0.7);
                        sound(top, "entity.firework_rocket.twinkle", 0.6f, 1f + t * 0.01f);
                    }
                }
                case "dab" -> {
                    if (t == 6) {
                        clone.swingMainHand();
                        clone.swingOffHand();
                        sound(centre, "entity.player.attack.sweep", 0.7f, 1.6f);
                        world.spawnParticle(Particle.CRIT, centre.clone().add(0, 1.6, 0), 25, 0.4, 0.3, 0.4, 0.3);
                    }
                    if (t >= 6 && t < 34) look(clone, yaw - 35, 50);
                    else look(clone, yaw, 0);
                    if (t == 8) confetti(centre.clone().add(0, 2, 0), 25, 0.6);
                }
                case "floss" -> {
                    if (t % 3 == 0) {
                        if ((t / 3) % 2 == 0) clone.swingMainHand();
                        else clone.swingOffHand();
                    }
                    look(clone, yaw + ((t / 3) % 2 == 0 ? 18 : -18), 0);
                    if (t % 6 == 0) sound(centre, "block.note_block.hat", 0.6f, 1.2f + (t % 12) / 24f);
                }
                case "moonwalk" -> {
                    // glides backwards (away from the player) while facing them
                    double back = Math.min(t, 50) * 0.05;
                    Location step = centre.clone().add(away.clone().multiply(back));
                    step.setYaw(yaw);
                    clone.teleport(step);
                    look(clone, yaw, 0);
                    if (t % 8 == 0) {
                        sound(step, "block.note_block.bass", 0.8f, 0.8f + (t % 24) / 24f);
                        world.spawnParticle(Particle.NOTE, step.clone().add(0, 2.3, 0), 0, (t % 24) / 24.0, 0, 0, 1);
                    }
                }
                case "twirl_jump" -> {
                    double h = t < 30 ? Math.sin(t / 30.0 * Math.PI) * 2.2 : 0;
                    Location up = centre.clone().add(0, h, 0);
                    up.setYaw(yaw + t * 24f);
                    clone.teleport(up);
                    look(clone, yaw + t * 24f, 0);
                    if (t == 0) sound(centre, "entity.player.attack.sweep", 0.8f, 0.8f);
                    if (t == 30) {
                        world.spawnParticle(Particle.CLOUD, centre.clone().add(0, 0.1, 0), 20, 0.5, 0.05, 0.5, 0.05);
                        sound(centre, "entity.generic.big_fall", 0.8f, 1f);
                    }
                }
                case "levitate" -> {
                    double h = t < 50 ? Math.min(1.6, t * 0.05) + Math.sin(t * 0.2) * 0.08 : Math.max(0, 1.6 - (t - 50) * 0.3);
                    Location up = centre.clone().add(0, h, 0);
                    up.setYaw(yaw);
                    clone.teleport(up);
                    look(clone, yaw, -20);
                    if (t % 3 == 0) world.spawnParticle(Particle.DUST, centre.clone().add(rand(0.5), 0.1, rand(0.5)), 2, 0.1, 0, 0.1, 0,
                            dust(Color.fromRGB(0x9B5CFF), 1.1f));
                    if (t % 5 == 0) world.spawnParticle(Particle.ENCHANT, up.clone().add(0, 1, 0), 8, 0.4, 0.6, 0.4, 0.5);
                    if (t == 2) sound(centre, "block.beacon.activate", 0.6f, 1.6f);
                }
                case "zombie_walk" -> {
                    double fwd = Math.min(t, 55) * 0.025;
                    Location step = centre.clone().add(away.clone().multiply(-fwd));
                    step.setYaw(yaw);
                    clone.teleport(step);
                    look(clone, yaw + (float) Math.sin(t * 0.2) * 8, 15);
                    if (t % 14 == 0) sound(step, "entity.zombie.ambient", 0.7f, 1.1f);
                    if (t % 7 == 0) clone.swingMainHand();
                }
                case "explode" -> {
                    if (size != null && t < 30) size.setBaseValue(1 + t * 0.025);
                    if (t < 30 && t % 6 == 0) sound(centre, "entity.creeper.primed", 0.8f, 1f + t * 0.02f);
                    if (t % 4 < 2 && t < 30) clone.setGlowing(true);
                    else clone.setGlowing(false);
                    if (t == 30) {
                        world.spawnParticle(Particle.EXPLOSION_EMITTER, centre.clone().add(0, 1, 0), 1);
                        world.spawnParticle(Particle.FIREWORK, centre.clone().add(0, 1, 0), 40, 0.3, 0.3, 0.3, 0.3);
                        confetti(centre.clone().add(0, 1, 0), 60, 1.2);
                        sound(centre, "entity.generic.explode", 1f, 1.2f);
                        clone.remove();
                    }
                }
                case "sleep" -> {
                    if (t == 2) clone.setPose(Pose.SLEEPING, true);
                    if (t % 14 == 0) {
                        sound(centre, "entity.player.breath", 0.5f, 0.8f);
                        TextDisplay z = world.spawn(centre.clone().add(0.3, 1.0, 0), TextDisplay.class, d -> {
                            d.text(Component.text("Z", net.kyori.adventure.text.format.NamedTextColor.AQUA));
                            d.setBillboard(Display.Billboard.CENTER);
                            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                            d.setTeleportDuration(20);
                        });
                        tagged(z, "z");
                        later(1, () -> z.teleport(z.getLocation().add(0.5, 1.2, 0)));
                        later(20, z::remove);
                    }
                }
                case "swim" -> {
                    if (t == 2) clone.setPose(Pose.SWIMMING, true);
                    Location up = centre.clone().add(0, 0.6 + Math.sin(t * 0.25) * 0.15, 0);
                    up.setYaw(yaw);
                    clone.teleport(up);
                    if (t % 3 == 0) world.spawnParticle(Particle.BUBBLE_POP, up.clone().add(0, 0.5, 0), 3, 0.4, 0.2, 0.4, 0.02);
                    if (t % 10 == 0) sound(up, "entity.player.swim", 0.5f, 1.2f);
                }
                case "giant" -> {
                    if (size != null) size.setBaseValue(t < 20 ? 1 + t * 0.075 : t < 48 ? 2.5 : Math.max(1, 2.5 - (t - 48) * 0.12));
                    if (t >= 20 && t < 48 && t % 9 == 0) {
                        world.spawnParticle(Particle.BLOCK, centre.clone().add(0, 0.1, 0), 30, 0.8, 0.05, 0.8, 0,
                                centre.clone().add(0, -1, 0).getBlock().getBlockData());
                        sound(centre, "entity.ravager.step", 1f, 0.7f);
                        clone.swingMainHand();
                    }
                }
                case "tiny" -> {
                    if (size != null) size.setBaseValue(t < 10 ? 1 - t * 0.07 : t < 36 ? 0.3 : Math.min(1, 0.3 + (t - 36) * 0.07));
                    if (t == 10) sound(centre, "entity.bat.ambient", 0.8f, 2f);
                    if (t > 10 && t < 36) {
                        Location hop = centre.clone().add(0, Math.abs(Math.sin(t * 0.5)) * 0.25, 0);
                        hop.setYaw(yaw + (float) Math.sin(t * 0.4) * 30);
                        clone.teleport(hop);
                    }
                }
                case "magic_trick" -> {
                    if (t < 20) look(clone, yaw + t * 18f, 0);
                    if (t == 20) {
                        world.spawnParticle(Particle.CLOUD, centre.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.03);
                        world.spawnParticle(Particle.WITCH, centre.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.1);
                        sound(centre, "entity.illusioner.prepare_mirror", 1f, 1.4f);
                        clone.setInvisible(true);
                    }
                    if (t == 44) {
                        Location pop = centre.clone().add(away.clone().multiply(-1.2));
                        pop.setYaw(yaw);
                        clone.teleport(pop);
                        clone.setInvisible(false);
                        world.spawnParticle(Particle.FIREWORK, pop.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.1);
                        sound(pop, "entity.player.levelup", 0.6f, 1.8f);
                    }
                }
                default -> {
                }
            }
        }, () -> {
            if (!clone.isValid()) return;
            Location end = clone.getLocation().add(0, 1, 0);
            world.spawnParticle(Particle.POOF, end, 20, 0.3, 0.5, 0.3, 0.03);
            world.spawnParticle(Particle.DUST, end, 20, 0.4, 0.8, 0.4, 0, dust(Color.fromRGB(0xB266FF), 1f));
            sound(end, "entity.illusioner.mirror_move", 0.6f, 1.8f);
            clone.remove();
            clones.remove(player.getUniqueId(), clone);
        });
    }

    /** A speech bubble over the copy (a random line from messages.yml collectibles.gesture-say.KEY). */
    private void say(Mannequin clone, String key, int length) {
        List<Component> lines = plugin.messages().lore("collectibles.gesture-say." + key);
        if (lines.isEmpty()) return;
        Component line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        TextDisplay bubble = clone.getWorld().spawn(clone.getLocation().add(0, 2.2, 0), TextDisplay.class, d -> {
            d.text(line);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(190, 255, 255, 255));
            d.setShadowed(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(0, 0.35f, 0), new AxisAngle4f(), new Vector3f(0.01f), new AxisAngle4f()));
        });
        tagged(bubble, "bubble");
        clone.addPassenger(bubble);
        later(2, () -> {
            bubble.setInterpolationDelay(0);
            bubble.setInterpolationDuration(4);
            bubble.setTransformation(new Transformation(new Vector3f(0, 0.45f, 0), new AxisAngle4f(), new Vector3f(1.1f), new AxisAngle4f()));
        });
        later(6, () -> {
            bubble.setInterpolationDelay(0);
            bubble.setInterpolationDuration(3);
            bubble.setTransformation(new Transformation(new Vector3f(0, 0.42f, 0), new AxisAngle4f(), new Vector3f(1f), new AxisAngle4f()));
        });
        later(length - 2, bubble::remove);
    }

    private static void look(Mannequin clone, float yaw, float pitch) {
        clone.setRotation(yaw, Math.max(-90, Math.min(90, pitch)));
        clone.setBodyYaw(yaw);
    }
}
