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
 * a few particles), the spinning propeller, emotes (a big emoji over the head) and gestures (an armor stand of
 * the player acts them out while they watch, in GestureShow). Nothing here can hurt anyone or change the world: thrown things are cancelled on
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
    /** Gestures, acted out by a mini armor stand while the player watches. */
    private final GestureShow gestures;
    /** Each player's balloons (up to three, each on its own lead). */
    private final Map<UUID, List<Balloon>> balloons = new HashMap<>();

    private static final class Balloon {
        final ItemDisplay display;
        final org.bukkit.entity.Chicken anchor;
        final int slot;
        final Color colour;
        BukkitTask life;

        Balloon(ItemDisplay display, org.bukkit.entity.Chicken anchor, int slot, Color colour) {
            this.display = display;
            this.anchor = anchor;
            this.slot = slot;
            this.colour = colour;
        }
    }
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
        final Entity[] extras;

        Placed(ItemDisplay display, BukkitTask task, Entity... extras) {
            this.display = display;
            this.task = task;
            this.extras = extras;
        }
    }

    Effects(Lobby plugin, Collectibles collectibles) {
        this.plugin = plugin;
        this.collectibles = collectibles;
        this.effectKey = new NamespacedKey(plugin, "collectible_effect");
        this.bonusKey = new NamespacedKey(plugin, "suit_bonus");
        this.more = new MoreGadgets(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(more, plugin);
        this.gestures = new GestureShow(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(gestures, plugin);
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
                List<Balloon> held = balloons.get(player.getUniqueId());
                if (held != null) floatBalloons(player, new ArrayList<>(held));
                passive(player);
                charge(player);
                blinkMarker(player);
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
        for (UUID id : new ArrayList<>(grapples.keySet())) dropGrapple(id, false);
        blinkMarkers.values().forEach(Entity::remove);
        blinkMarkers.clear();
        propellers.values().forEach(Entity::remove);
        propellers.clear();
        gestures.stopAll();
        for (List<Balloon> list : balloons.values()) {
            for (Balloon b : list) {
                b.display.remove();
                b.anchor.remove();
            }
        }
        balloons.clear();
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
        gestures.stop(id, false);
        popBalloons(id);
        dropGrapple(id, false);
        BlockDisplay marker = blinkMarkers.remove(id);
        if (marker != null) marker.remove();
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
    void refund(Player player, String what) {
        collectibles.refund(player, what);
    }

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
        popBalloons(id);
        dropGrapple(id, false);
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
        for (Balloon b : balloons.getOrDefault(wearer.getUniqueId(), List.of())) {
            riders.add(b.display);
            riders.add(b.anchor);
        }
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
            case "party_popper" -> partyPopper(player);
            case "paintball_gun" -> paintball(player);
            case "bubble_wand" -> {
                sound(eye, "block.bubble_column.upwards_inside", 0.8f, 1.5f);
                for (int i = 0; i < 7; i++) {
                    float size = i == 6 ? 1.5f : 0.4f + ThreadLocalRandom.current().nextFloat() * 0.55f;   // the last one is a big one
                    later(i * 3, () -> blowBubble(player, size));
                }
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
            case "snow_cannon" -> snowCannon(player);
            case "pumpkin_launcher" -> {
                toss(player, ItemStack.of(Material.JACK_O_LANTERN), "pumpkin", 1.3);
                sound(eye, "entity.witch.throw", 1f, 1f);
            }
            case "balloon" -> balloon(player);
            case "magic_wand" -> magicWand(player);
            case "grappling_hook" -> grapple(player);
            case "blink_stick" -> blink(player);
            case "trampoline" -> trampoline(player);
            default -> more.use(player, key);
        }
    }

    // ---------------------------------------------------------------- the grappling hook

    /** A grappling hook out: its head, its rope, where it's stuck (null while flying) and its task. */
    private static final class Grapple {
        ItemDisplay head;
        final List<ItemDisplay> links = new ArrayList<>();
        Location stuck;
        BukkitTask task;
        boolean reeling;
    }

    private final Map<UUID, Grapple> grapples = new HashMap<>();

    /**
     * The grappling hook: one click fires the hook out on a chain (up to 24 blocks). If it bites into a block it pulls you
     * straight in to it; if it finds nothing it winds back to your hand. The chain is a real chain of links that hangs a
     * little as it goes.
     */
    private void grapple(Player player) {
        UUID id = player.getUniqueId();
        if (grapples.containsKey(id) || parkour(player)) return;
        Grapple fresh = new Grapple();
        grapples.put(id, fresh);
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        World world = eye.getWorld();
        Location start = ropeHand(player);
        fresh.head = world.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(modelled(Material.TRIPWIRE_HOOK, "gadget/grappling_hook_head"));
            d.setTeleportDuration(1);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.55f), new AxisAngle4f()));
        });
        tagged(fresh.head, "grapple");
        sound(eye, "item.crossbow.shoot", 0.8f, 1.3f);
        sound(eye, "block.chain.fall", 0.8f, 1.2f);
        Location[] tip = {eye.clone().add(look.clone().multiply(0.8))};
        double[] out = {0};
        fresh.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getWorld() != world || !fresh.head.isValid()) {
                dropGrapple(id, false);
                return;
            }
            if (fresh.stuck == null) {                           // flying out
                RayTraceResult hit = world.rayTraceBlocks(tip[0], look, 1.6);
                if (hit != null) {
                    fresh.stuck = hit.getHitPosition().toLocation(world);
                    tip[0] = fresh.stuck.clone();
                    sound(tip[0], "block.chain.place", 1f, 1.2f);
                    sound(tip[0], "entity.arrow.hit", 0.6f, 1.4f);
                    world.spawnParticle(Particle.BLOCK, tip[0], 8, 0.1, 0.1, 0.1, 0, hit.getHitBlock().getBlockData());
                    later(2, () -> {
                        if (grapples.get(id) == fresh && !parkour(player)) reel(player, fresh);
                    });
                } else {
                    tip[0].add(look.clone().multiply(1.6));
                    out[0] += 1.6;
                    if (out[0] > 24) {                           // nothing to bite: wind it back in
                        dropGrapple(id, true);
                        return;
                    }
                }
            }
            Location head = tip[0].clone();
            head.setDirection(look);
            fresh.head.teleport(head);
            layChain(fresh, ropeHand(player), tip[0], fresh.reeling ? 0.02 : 0.06);
        }, 1L, 1L);
    }

    /** Reels the player in to where the hook is stuck, over a few ticks, then lets go. */
    private void reel(Player player, Grapple g) {
        g.reeling = true;
        UUID id = player.getUniqueId();
        sound(player.getLocation(), "entity.fishing_bobber.retrieve", 1f, 0.8f);
        sound(player.getLocation(), "block.chain.step", 1f, 1.4f);
        int[] t = {0};
        BukkitTask[] pull = new BukkitTask[1];
        pull[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (grapples.get(id) != g || !player.isOnline()) {
                pull[0].cancel();
                return;
            }
            Vector line = g.stuck.toVector().subtract(player.getLocation().add(0, 1, 0).toVector());
            double d = line.length();
            if (d < 1.6 || t[0]++ > 30) {
                player.setVelocity(player.getVelocity().multiply(0.5).add(new Vector(0, 0.3, 0)));
                player.setFallDistance(0);
                pull[0].cancel();
                dropGrapple(id, false);
                return;
            }
            double speed = Math.min(1.5, 0.45 + d * 0.06);
            player.setVelocity(line.normalize().multiply(speed).add(new Vector(0, 0.06, 0)));
            player.setFallDistance(0);
        }, 0L, 1L);
    }

    /** Takes the hook away; when it's pulled back, the rope flies back to the hand first. */
    private void dropGrapple(UUID id, boolean pullBack) {
        Grapple g = grapples.remove(id);
        if (g == null) return;
        if (g.task != null) g.task.cancel();
        Player player = plugin.getServer().getPlayer(id);
        if (!pullBack || player == null || !g.head.isValid()) {
            g.head.remove();
            g.links.forEach(Entity::remove);
            return;
        }
        sound(player.getLocation(), "entity.fishing_bobber.retrieve", 0.7f, 1.4f);
        Location from = g.head.getLocation();
        every(6, t -> {
            if (!g.head.isValid() || !player.isOnline()) return;
            Location hand = ropeHand(player);
            Location at = from.clone().add(hand.toVector().subtract(from.toVector()).multiply((t + 1) / 6.0));
            g.head.teleport(at);
            layChain(g, hand, at, 0.05);
        }, () -> {
            g.head.remove();
            g.links.forEach(Entity::remove);
        });
    }

    /** Where the chain leaves the player: low and to the right of where they look (where the hook is held). */
    private Location ropeHand(Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        Vector right = new Vector(-look.getZ(), 0, look.getX());
        if (right.lengthSquared() < 1e-4) right = new Vector(1, 0, 0);
        right.normalize();
        return eye.add(look.multiply(0.45)).add(right.multiply(0.3)).add(0, -0.4, 0);
    }

    /**
     * Lays the chain from the hand to the hook along a gently hanging curve: links placed evenly along it, each pointing
     * along the curve, every other one turned a quarter so they look linked, all kept upright the same way so the
     * chain doesn't twist when the player turns.
     */
    private void layChain(Grapple g, Location from, Location to, double sagPerBlock) {
        Vector a = from.toVector(), c = to.toVector();
        double straight = c.distance(a);
        if (straight < 1e-3) return;
        Vector control = a.clone().add(c).multiply(0.5).add(new Vector(0, -2 * Math.min(1.2, straight * sagPerBlock), 0));
        int samples = 40;
        Vector[] pts = new Vector[samples + 1];
        double[] along = new double[samples + 1];
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples, u = 1 - t;
            pts[i] = a.clone().multiply(u * u).add(control.clone().multiply(2 * u * t)).add(c.clone().multiply(t * t));
            if (i > 0) along[i] = along[i - 1] + pts[i].distance(pts[i - 1]);
        }
        double total = along[samples], spacing = 0.38;
        int needed = Math.max(1, (int) Math.floor(total / spacing));
        World world = from.getWorld();
        while (g.links.size() < needed) {
            ItemDisplay link = world.spawn(from, ItemDisplay.class, d -> {
                d.setItemStack(modelled(Material.IRON_NUGGET, "gadget/chain_link"));
                d.setTeleportDuration(1);
                d.setPersistent(false);
            });
            tagged(link, "grapple");
            g.links.add(link);
        }
        while (g.links.size() > needed) g.links.remove(g.links.size() - 1).remove();
        int seg = 1;
        for (int k = 0; k < needed; k++) {
            double want = (k + 0.5) * spacing;
            while (seg < samples && along[seg] < want) seg++;
            double f = (want - along[seg - 1]) / Math.max(1e-6, along[seg] - along[seg - 1]);
            Vector pos = pts[seg - 1].clone().add(pts[seg].clone().subtract(pts[seg - 1]).multiply(f));
            Vector tan = pts[seg].clone().subtract(pts[seg - 1]).normalize();
            Vector3f fwd = new Vector3f((float) tan.getX(), (float) tan.getY(), (float) tan.getZ());
            Vector3f side = new Vector3f(0, 1, 0).cross(fwd);
            if (side.lengthSquared() < 1e-4) side.set(1, 0, 0);
            side.normalize();
            Vector3f normal = new Vector3f(side).cross(fwd).normalize();           // side, along, normal: a right-handed frame
            if (k % 2 == 1) {                               // every other link turned a quarter round the chain
                Vector3f swap = side;
                side = normal;
                normal = new Vector3f(swap).negate();
            }
            org.joml.Quaternionf turn = new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f(side, fwd, normal));
            ItemDisplay link = g.links.get(k);
            link.teleport(new Location(world, pos.getX(), pos.getY(), pos.getZ()));
            link.setInterpolationDelay(0);
            link.setInterpolationDuration(1);
            link.setTransformation(new Transformation(new Vector3f(), turn, new Vector3f(1f), new org.joml.Quaternionf()));
        }
    }

    /**
     * The snow cannon: one big packed snowball fired with a boom, spinning and shedding snow as it arcs; where it hits
     * it bursts into a blizzard of flakes and snow, a patch of snow settles on the ground around it (only shown; it
     * melts away bit by bit), and anyone caught in it is frosted over and knocked back a little. Nobody gets hurt.
     */
    private void snowCannon(Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        World world = eye.getWorld();
        Location start = eye.clone().add(look.clone().multiply(1.0)).add(0, -0.2, 0);
        sound(eye, "entity.snow_golem.shoot", 1f, 0.6f);
        sound(eye, "entity.generic.explode", 0.35f, 1.8f);
        world.spawnParticle(Particle.SNOWFLAKE, start, 20, 0.15, 0.15, 0.15, 0.08);
        world.spawnParticle(Particle.CLOUD, start, 6, 0.1, 0.1, 0.1, 0.03);
        ItemDisplay ball = world.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.SNOW_BLOCK));
            d.setTeleportDuration(1);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.55f), new AxisAngle4f()));
        });
        tagged(ball, "snowball");
        Vector velocity = look.clone().multiply(1.15);
        boolean[] done = {false};
        every(60, t -> {
            if (done[0] || !ball.isValid()) return;
            velocity.setY(velocity.getY() - 0.03);
            Location at = ball.getLocation();
            RayTraceResult hit = world.rayTraceBlocks(at, velocity.clone().normalize(), velocity.length() + 0.25);
            Player struck = null;
            for (Player other : world.getPlayers()) {
                if (other != player && t > 1 && other.getLocation().add(0, 1, 0).distanceSquared(at) < 1.2) struck = other;
            }
            if (hit != null || struck != null) {
                done[0] = true;
                Location boom = hit != null ? hit.getHitPosition().toLocation(world) : at;
                ball.remove();
                snowBlast(player, boom);
                return;
            }
            ball.teleport(at.add(velocity));
            ball.setInterpolationDelay(0);
            ball.setInterpolationDuration(1);
            ball.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(t * 0.6f, 1, 0.3f, 0), new Vector3f(0.55f), new AxisAngle4f()));
            world.spawnParticle(Particle.SNOWFLAKE, at, 3, 0.12, 0.12, 0.12, 0.01);
            if (t % 2 == 0) world.spawnParticle(Particle.ITEM, at, 2, 0.1, 0.1, 0.1, 0.02, new ItemStack(Material.SNOWBALL));
        }, () -> {
            if (!done[0] && ball.isValid()) {
                Location at = ball.getLocation();
                ball.remove();
                snowBlast(player, at);
            }
        });
    }

    private void snowBlast(Player shooter, Location at) {
        World world = at.getWorld();
        world.spawnParticle(Particle.SNOWFLAKE, at, 90, 0.8, 0.6, 0.8, 0.12);
        world.spawnParticle(Particle.ITEM, at, 40, 0.3, 0.3, 0.3, 0.25, new ItemStack(Material.SNOWBALL));
        world.spawnParticle(Particle.BLOCK, at, 50, 0.6, 0.4, 0.6, 0, Material.SNOW_BLOCK.createBlockData());
        world.spawnParticle(Particle.CLOUD, at, 25, 0.6, 0.3, 0.6, 0.05);
        world.spawnParticle(Particle.WHITE_ASH, at, 60, 1.5, 1, 1.5, 0);
        sound(at, "block.powder_snow.break", 1.2f, 0.8f);
        sound(at, "block.snow.break", 1f, 0.6f);
        sound(at, "entity.player.hurt_freeze", 0.6f, 1.3f);
        // a patch of snow settles round where it hit, then melts away a bit at a time (only shown: nothing is placed)
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Block centre = at.getBlock();
        List<Block> patch = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 7 + r.nextInt(3)) continue;
                for (int dy = 2; dy >= -2; dy--) {
                    Block spot = centre.getRelative(dx, dy, dz);
                    if (spot.getType().isAir() && spot.getRelative(0, -1, 0).getType().isOccluding()) {
                        patch.add(spot);
                        int layers = Math.max(1, 3 - (int) Math.sqrt(dx * dx + dz * dz));
                        org.bukkit.block.data.type.Snow snow = (org.bukkit.block.data.type.Snow) Material.SNOW.createBlockData();
                        snow.setLayers(layers);
                        for (Player viewer : world.getPlayers()) viewer.sendBlockChange(spot.getLocation(), snow);
                        break;
                    }
                }
            }
        }
        for (Block spot : patch) {
            later(70 + r.nextInt(50), () -> {
                for (Player viewer : world.getPlayers()) viewer.sendBlockChange(spot.getLocation(), spot.getBlockData());
            });
        }
        // anyone caught in it is frosted over (the frosty screen, no damage) and pushed back a little
        for (Player near : world.getPlayers()) {
            double d = near.getLocation().add(0, 1, 0).distance(at);
            if (d > 3) continue;
            near.setFreezeTicks(Math.max(near.getFreezeTicks(), 80));
            near.getWorld().spawnParticle(Particle.SNOWFLAKE, near.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
            if (near != shooter && !parkour(near)) {
                Vector push = near.getLocation().toVector().subtract(at.toVector()).setY(0);
                if (push.lengthSquared() > 1e-4) near.setVelocity(near.getVelocity().add(push.normalize().multiply(0.45).setY(0.3)));
            }
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
        for (Entity extra : old.extras) extra.remove();
    }

    /** A cookie fountain on the ground, spraying cookies for eight seconds, then sinking away. */
    private void cookieFountain(Player player) {
        unplace(player.getUniqueId());
        Location at = ground(player);
        World world = at.getWorld();
        // the fountain and its chocolate milk are two models on top of each other; both rise out of the ground
        ItemDisplay[] parts = new ItemDisplay[2];
        String[] models = {"gadget/cookie_fountain_3d", "gadget/cookie_fountain_flow"};
        for (int i = 0; i < 2; i++) {
            String model = models[i];
            parts[i] = world.spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(modelled(Material.COOKIE, model));
                d.setPersistent(false);
                d.setTransformation(new Transformation(new Vector3f(0, 0.05f, 0), new AxisAngle4f(), new Vector3f(2f, 0.01f, 2f), new AxisAngle4f()));
            });
            tagged(parts[i], "fountain");
        }
        ItemDisplay fountain = parts[0], milk = parts[1];
        later(1, () -> {
            for (ItemDisplay part : parts) {
                if (!part.isValid()) continue;
                part.setInterpolationDelay(0);
                part.setInterpolationDuration(12);
                part.setTransformation(new Transformation(new Vector3f(0, 1f, 0), new AxisAngle4f(), new Vector3f(2f), new AxisAngle4f()));
            }
        });
        sound(at, "block.stone.place", 1f, 0.8f);
        sound(at, "entity.player.levelup", 0.4f, 1.8f);
        ItemStack cookie = modelled(Material.COOKIE, "gadget/cookie");
        Location top = at.clone().add(0, 2.45, 0);
        Color brown = Color.fromRGB(0x8A5530), cream = Color.fromRGB(0xE2C4A4);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        BukkitTask run = every(260, t -> {
            if (t < 14) return;
            if (t < 236) {
                // cookies spring out of the top and fly out over the edge
                if (t % 3 == 0) burstItems(top, cookie, 1, 0.24, 50);
                // the spring of milk at the top
                if (t % 2 == 0) world.spawnParticle(Particle.DUST, top.clone().add(rand(0.12), rand(0.08), rand(0.12)), 2, 0.05, 0.05, 0.05, 0,
                        dust(t % 6 == 0 ? cream : brown, 0.8f));
                // splashes where the milk pours into the pools below
                for (int k = 0; k < 2; k++) {
                    double a = r.nextDouble(Math.PI * 2);
                    boolean basin = r.nextBoolean();
                    double radius = basin ? 0.84 : 0.53, y = basin ? 0.36 : 1.22;
                    world.spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * radius, y, Math.sin(a) * radius), 1, 0.03, 0.03, 0.03, 0,
                            dust(r.nextInt(4) == 0 ? cream : brown, 0.6f));
                }
                if (t % 30 == 14) sound(at.clone().add(0, 1, 0), "block.water.ambient", 0.5f, 1.4f);
                if (t % 9 == 0) sound(top, "block.pointed_dripstone.drip_water_into_cauldron", 0.4f, 0.8f + r.nextFloat() * 0.4f);
                if (t % 12 == 0) sound(top, "entity.item.pickup", 0.4f, 0.8f + (t % 48) / 48f);
            } else if (t == 236) {
                for (ItemDisplay part : parts) {
                    part.setInterpolationDelay(0);
                    part.setInterpolationDuration(18);
                    part.setTransformation(new Transformation(new Vector3f(0, 0.05f, 0), new AxisAngle4f(), new Vector3f(2f, 0.01f, 2f), new AxisAngle4f()));
                }
                sound(at, "block.stone.break", 0.8f, 1.1f);
            }
        }, () -> {
            world.spawnParticle(Particle.POOF, at.clone().add(0, 0.4, 0), 16, 0.6, 0.2, 0.6, 0.02);
            fountain.remove();
            milk.remove();
            placed.remove(player.getUniqueId());
        });
        placed.put(player.getUniqueId(), new Placed(fountain, run, milk));
    }

    /** A trampoline on the ground for fifteen seconds; anyone who lands on it bounces high. */
    /**
     * A big trampoline (about four blocks across) on the ground: anyone landing on it bounces high with a springy boing.
     * It needs a flat open spot: solid ground under all of it, room above for jumping, and no other trampoline or disco
     * right there; if not, the player is told and gets their cooldown back.
     */
    private void trampoline(Player player) {
        Location at = ground(player);
        World world = at.getWorld();
        Location centre = at.getBlock().getLocation().add(0.5, 0, 0.5);
        String why = null;
        for (int dx = -1; dx <= 1 && why == null; dx++) {
            for (int dz = -1; dz <= 1 && why == null; dz++) {
                if (!centre.clone().add(dx, -1, dz).getBlock().getType().isOccluding()) why = "floor";
                for (int dy = 0; dy < 4 && why == null; dy++) {
                    if (!centre.clone().add(dx, dy, dz).getBlock().isPassable()) why = "room";
                }
            }
        }
        if (why == null) {
            for (Entity near : world.getNearbyEntities(centre, 4, 3, 4)) {
                if (near.getScoreboardTags().contains("lobby_trampoline") || near.getScoreboardTags().contains("lobby_disco_floor")) why = "taken";
            }
        }
        if (why != null) {
            plugin.messages().send(player, "collectibles.gadget.trampoline-" + why);
            sound(player.getLocation(), "entity.villager.no", 0.8f, 1.2f);
            collectibles.refund(player, "gadget:trampoline");
            return;
        }
        unplace(player.getUniqueId());
        float size = 3.6f;
        ItemDisplay trampoline = world.spawn(centre, ItemDisplay.class, d -> {
            d.setItemStack(modelled(Material.SLIME_BLOCK, "gadget/trampoline_3d"));
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(), new Vector3f(0.01f), new AxisAngle4f()));
        });
        trampoline.addScoreboardTag("lobby_trampoline");
        tagged(trampoline, "trampoline");
        later(1, () -> {
            trampoline.setInterpolationDelay(0);
            trampoline.setInterpolationDuration(6);
            trampoline.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(), new Vector3f(size, 1.1f, size), new AxisAngle4f()));
        });
        sound(centre, "block.wool.place", 1f, 0.8f);
        double reach = 1.9;
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
            for (Player jumper : world.getPlayers()) {
                Location feet = jumper.getLocation();
                double dx = feet.getX() - centre.getX(), dz = feet.getZ() - centre.getZ(), dy = feet.getY() - centre.getY();
                if (dx * dx + dz * dz > reach * reach || dy < -0.1 || dy > 1.1 || parkour(jumper)) continue;
                if (jumper.getVelocity().getY() > 0.2) continue;
                Integer last = bounced.get(jumper.getUniqueId());
                if (last != null && t - last < 6) continue;
                bounced.put(jumper.getUniqueId(), t);
                jumper.setVelocity(jumper.getVelocity().setY(1.3));
                jumper.setFallDistance(0);
                sound(centre, "fractal:gadget.trampoline_boing", 1f, 0.9f + ThreadLocalRandom.current().nextFloat() * 0.25f);
                world.spawnParticle(Particle.DUST, centre.clone().add(0, 0.9, 0), 22, 1.1, 0.05, 1.1, 0, dust(Color.fromRGB(0x5BC0FF), 1f));
                trampoline.setInterpolationDelay(0);
                trampoline.setInterpolationDuration(2);
                trampoline.setTransformation(new Transformation(new Vector3f(0, 0.45f, 0), new AxisAngle4f(), new Vector3f(size * 1.05f, 0.7f, size * 1.05f), new AxisAngle4f()));
                later(3, () -> {
                    if (!trampoline.isValid()) return;
                    trampoline.setInterpolationDelay(0);
                    trampoline.setInterpolationDuration(5);
                    trampoline.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(), new Vector3f(size, 1.1f, size), new AxisAngle4f()));
                });
            }
        }, () -> {
            world.spawnParticle(Particle.POOF, centre.clone().add(0, 0.3, 0), 20, 1.2, 0.1, 1.2, 0.02);
            trampoline.remove();
            placed.remove(player.getUniqueId());
        });
        placed.put(player.getUniqueId(), new Placed(trampoline, run));
    }

    /** The balloon colours: each balloon a player holds is a different one. */
    private static final Color[] BALLOON_COLOURS = {Color.fromRGB(0xFF4D6D), Color.fromRGB(0x3F8BFF), Color.fromRGB(0xFFD23A),
            Color.fromRGB(0x5BE37C), Color.fromRGB(0xB266FF), Color.fromRGB(0xFF8A1F), Color.fromRGB(0xFF6FD8), Color.fromRGB(0x2FC8C8)};

    /** How far above the lead's end the balloon model sits (its knot is right at the lead). */
    private static final float BALLOON_LIFT = 0.23f;

    /** Is this one of the invisible things a balloon's lead is tied to? */
    private boolean isAnchor(Entity entity) {
        for (List<Balloon> list : balloons.values()) for (Balloon b : list) if (b.anchor.equals(entity)) return true;
        return false;
    }

    /**
     * A balloon on a real lead: it bobs above the player, drifts behind when they move and tilts as it swings. Each
     * right-click adds another (up to three, each its own colour, one over each shoulder and one behind); each lasts
     * thirty seconds and pops.
     */
    private void balloon(Player player) {
        if (!plugin.menuStyle().custom()) return;
        UUID id = player.getUniqueId();
        List<Balloon> held = balloons.computeIfAbsent(id, k -> new ArrayList<>());
        if (held.size() >= 3) {
            plugin.messages().send(player, "collectibles.gadget.balloon-full");
            collectibles.refund(player, "gadget:balloon");
            return;
        }
        int slot = 0;
        while (true) {
            int s = slot;
            if (held.stream().noneMatch(b -> b.slot == s)) break;
            slot++;
        }
        List<Color> free = new ArrayList<>(List.of(BALLOON_COLOURS));
        for (Balloon b : held) free.remove(b.colour);
        Color colour = free.get(ThreadLocalRandom.current().nextInt(free.size()));
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
        ItemDisplay display = anchor.getWorld().spawn(start, ItemDisplay.class, d -> {
            ItemStack look = modelled(Material.LEATHER_HORSE_ARMOR, "gadget/balloon_3d");
            look.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(colour));
            d.setItemStack(look);
            d.setPersistent(false);
            d.setInterpolationDuration(3);
            d.setTransformation(new Transformation(new Vector3f(0, BALLOON_LIFT, 0), new AxisAngle4f(), new Vector3f(1f), new AxisAngle4f()));
        });
        tagged(display, "balloon");
        anchor.addPassenger(display);
        Balloon balloon = new Balloon(display, anchor, slot, colour);
        held.add(balloon);
        sound(player.getLocation(), "entity.chicken.egg", 0.5f, 0.6f + held.size() * 0.2f);
        balloon.life = plugin.getServer().getScheduler().runTaskLater(plugin, () -> popOne(id, balloon, true), 600L);
    }

    /**
     * One soap bubble from the wand: it swells out of the ring, drifts off and slowly up, wobbling and squishing as it
     * goes, and pops (on a wall, on someone, or when its time is up) with a splash of droplets.
     */
    private void blowBubble(Player player, float size) {
        if (!player.isOnline()) return;
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        Vector right = new Vector(-look.getZ(), 0, look.getX());
        if (right.lengthSquared() < 1e-4) right = new Vector(1, 0, 0);
        right.normalize();
        Location from = eye.clone().add(look.clone().multiply(1.1)).add(right.clone().multiply(0.3)).add(0, -0.2, 0);
        World world = from.getWorld();
        ItemDisplay bubble = world.spawn(from, ItemDisplay.class, d -> {
            d.setItemStack(modelled(Material.GLASS_PANE, "gadget/bubble"));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(2);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.05f), new AxisAngle4f()));
        });
        tagged(bubble, "bubble");
        later(1, () -> {
            if (!bubble.isValid()) return;
            bubble.setInterpolationDelay(0);
            bubble.setInterpolationDuration(5);
            bubble.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(size), new AxisAngle4f()));
        });
        sound(from, "entity.axolotl.splash", 0.25f, 1.9f - size * 0.3f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector velocity = look.clone().multiply(0.13 + r.nextDouble() * 0.07).add(new Vector(rand(0.03), 0.015 + r.nextDouble() * 0.02, rand(0.03)));
        double phase = r.nextDouble() * Math.PI * 2;
        int life = (int) (45 + r.nextInt(45) + size * 30);
        Vector side = right;
        every(life, t -> {
            if (!bubble.isValid()) return;
            Location at = bubble.getLocation();
            velocity.multiply(0.955).add(new Vector(0, 0.0035, 0));          // slows down and floats up
            Location next = at.clone().add(velocity).add(side.clone().multiply(Math.sin(t * 0.22 + phase) * 0.025))
                    .add(0, Math.sin(t * 0.17 + phase) * 0.01, 0);
            if (!next.getBlock().isPassable()) {
                popBubble(bubble, size);
                return;
            }
            for (Player near : world.getPlayers()) {
                if (near == player && t < 15) continue;
                Location body = near.getLocation().add(0, 1, 0);
                if (Math.abs(body.getY() - next.getY()) < 1.1 && Math.hypot(body.getX() - next.getX(), body.getZ() - next.getZ()) < 0.35 + size * 0.4) {
                    popBubble(bubble, size);
                    return;
                }
            }
            bubble.teleport(next);
            if (t > 5 && t % 3 == 0) {                                        // a gentle squish as it wobbles
                float squish = (float) Math.sin(t * 0.35 + phase) * 0.07f;
                bubble.setInterpolationDelay(0);
                bubble.setInterpolationDuration(3);
                bubble.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(size * (1 + squish), size * (1 - squish), size), new AxisAngle4f()));
            }
        }, () -> popBubble(bubble, size));
    }

    private void popBubble(ItemDisplay bubble, float size) {
        if (!bubble.isValid()) return;
        Location at = bubble.getLocation();
        bubble.setInterpolationDelay(0);
        bubble.setInterpolationDuration(2);
        bubble.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(size * 1.25f), new AxisAngle4f()));
        later(2, bubble::remove);
        World world = at.getWorld();
        world.spawnParticle(Particle.BUBBLE_POP, at, (int) (6 + size * 10), size * 0.3, size * 0.3, size * 0.3, 0.04);
        world.spawnParticle(Particle.SPLASH, at, (int) (4 + size * 8), size * 0.25, size * 0.25, size * 0.25, 0);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 3, size * 0.2, size * 0.2, size * 0.2, 0,
                new Particle.DustTransition(Color.fromRGB(0xBFEFFF), Color.fromRGB(0xFFB8F0), 0.7f));
        sound(at, "block.bubble_column.bubble_pop", 0.7f, 1.9f - size * 0.5f);
    }

    private static final NamespacedKey POPPER = new NamespacedKey("fractal", "gadget/party_popper"),
            POPPER_OPEN = new NamespacedKey("fractal", "gadget/party_popper_open");

    /**
     * The party popper: the sealed popper in your hand bursts open (streamers out) for a moment, with its own POP, and
     * shoots real paper confetti and curly streamers that flutter down, land and fade.
     */
    private void partyPopper(Player player) {
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack held = player.getInventory().getItem(slot);
        if (held != null && held.hasItemMeta() && POPPER.equals(held.getItemMeta().getItemModel())) {
            held.editMeta(meta -> meta.setItemModel(POPPER_OPEN));
            player.getInventory().setItem(slot, held);
            later(30, () -> {
                ItemStack now = player.getInventory().getItem(slot);
                if (now == null || !now.hasItemMeta() || !POPPER_OPEN.equals(now.getItemMeta().getItemModel())) return;
                now.editMeta(meta -> meta.setItemModel(POPPER));
                player.getInventory().setItem(slot, now);
            });
        }
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        Vector right = new Vector(-look.getZ(), 0, look.getX());
        if (right.lengthSquared() < 1e-4) right = new Vector(1, 0, 0);
        right.normalize();
        Location mouth = eye.clone().add(look.clone().multiply(0.8)).add(right.clone().multiply(0.3)).add(0, -0.2, 0);
        World world = mouth.getWorld();
        sound(mouth, "fractal:gadget.party_popper", 1f, 0.95f + ThreadLocalRandom.current().nextFloat() * 0.1f);
        world.spawnParticle(Particle.POOF, mouth, 3, 0.05, 0.05, 0.05, 0.02);
        for (int i = 0; i < 22; i++) confetti(mouth, look, false);
        for (int i = 0; i < 6; i++) confetti(mouth, look, true);
    }

    /** One piece of paper from the popper: out in a cone, slowed by the air, fluttering and tumbling down, then resting. */
    private void confetti(Location mouth, Vector look, boolean streamer) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int colour = r.nextInt(6);
        World world = mouth.getWorld();
        float size = streamer ? 0.42f : 0.16f + r.nextFloat() * 0.06f;
        ItemDisplay piece = world.spawn(mouth, ItemDisplay.class, d -> {
            d.setItemStack(modelled(Material.PAPER, (streamer ? "gadget/streamer_" : "gadget/confetti_") + colour));
            d.setTeleportDuration(2);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(size), new AxisAngle4f()));
        });
        tagged(piece, "confetti");
        Vector velocity = look.clone().add(new Vector(rand(0.45), rand(0.35) + 0.25, rand(0.45))).normalize()
                .multiply((streamer ? 0.5 : 0.55) + r.nextDouble() * 0.3);
        org.joml.Quaternionf turn = new org.joml.Quaternionf().rotateXYZ(r.nextFloat() * 6.3f, r.nextFloat() * 6.3f, r.nextFloat() * 6.3f);
        Vector3f axis = new Vector3f(r.nextFloat() - 0.5f, r.nextFloat() - 0.5f, r.nextFloat() - 0.5f).normalize();
        float[] spin = {0.5f + r.nextFloat() * 0.5f};
        double phase = r.nextDouble() * Math.PI * 2;
        double fall = streamer ? -0.045 : -0.065;
        boolean[] landed = {false};
        int[] restFor = {0};
        every(110 + r.nextInt(30), t -> {
            if (!piece.isValid()) return;
            if (landed[0]) {
                if (++restFor[0] == 30) {                                 // lie there a moment, then fade away
                    piece.setInterpolationDelay(0);
                    piece.setInterpolationDuration(8);
                    piece.setTransformation(new Transformation(new Vector3f(), new org.joml.Quaternionf().rotateX((float) Math.PI / 2),
                            new Vector3f(0), new org.joml.Quaternionf()));
                }
                if (restFor[0] == 40) piece.remove();
                return;
            }
            velocity.multiply(0.88);                                      // paper is stopped fast by the air
            velocity.setY(Math.max(fall, velocity.getY() - 0.02));        // then flutters down slowly
            Location at = piece.getLocation();
            Location next = at.clone().add(velocity).add(Math.sin(t * 0.3 + phase) * 0.025, 0, Math.cos(t * 0.27 + phase) * 0.025);
            if (!next.getBlock().isPassable()) {
                org.bukkit.block.Block ground = next.getBlock();
                double top = ground.getBoundingBox().getHeight() > 0 ? ground.getBoundingBox().getMaxY() : ground.getY() + 1;
                if (at.getY() >= top - 0.05) {                            // landed on top: lie flat
                    landed[0] = true;
                    piece.teleport(new Location(world, next.getX(), top + 0.02, next.getZ()));
                    piece.setInterpolationDelay(0);
                    piece.setInterpolationDuration(3);
                    piece.setTransformation(new Transformation(new Vector3f(), new org.joml.Quaternionf().rotateX((float) Math.PI / 2)
                            .rotateZ(r.nextFloat() * 6.3f), new Vector3f(size), new org.joml.Quaternionf()));
                    return;
                }
                velocity.setX(-velocity.getX() * 0.3).setZ(-velocity.getZ() * 0.3);   // hit a wall: bounce off a little
                return;
            }
            piece.teleport(next);
            spin[0] *= 0.985f;
            turn.premul(new org.joml.Quaternionf().rotateAxis(spin[0] * 0.5f, axis));
            if (t % 2 == 0) {
                piece.setInterpolationDelay(0);
                piece.setInterpolationDuration(2);
                piece.setTransformation(new Transformation(new Vector3f(), new org.joml.Quaternionf(turn), new Vector3f(size), new org.joml.Quaternionf()));
            }
        }, piece::remove);
    }

    /** Takes all of the player's balloons away. */
    private void popBalloons(UUID id) {
        List<Balloon> list = balloons.remove(id);
        if (list == null) return;
        for (Balloon b : list) {
            if (b.life != null) b.life.cancel();
            b.display.remove();
            b.anchor.remove();
        }
    }

    /** One balloon goes (with a pop if it burst). */
    private void popOne(UUID id, Balloon balloon, boolean pop) {
        List<Balloon> list = balloons.get(id);
        if (list != null) {
            list.remove(balloon);
            if (list.isEmpty()) balloons.remove(id);
        }
        if (balloon.life != null) balloon.life.cancel();
        if (pop && balloon.display.isValid()) {
            Location at = balloon.display.getLocation().add(0, 0.9, 0);
            at.getWorld().spawnParticle(Particle.DUST, at, 20, 0.25, 0.25, 0.25, 0, dust(balloon.colour, 1f));
            sound(at, "entity.firework_rocket.blast", 0.6f, 2f);
        }
        balloon.display.remove();
        balloon.anchor.remove();
    }

    /**
     * Moves a player's balloons like real ones: each is pulled toward a spot above the player on a springy lead (so they
     * lag and swing when the player moves), floats up a little, bumps the others out of its way, and leans along its lead.
     */
    private void floatBalloons(Player player, List<Balloon> list) {
        UUID id = player.getUniqueId();
        for (Balloon b : list) {
            if (!b.display.isValid() || !b.anchor.isValid() || b.anchor.getWorld() != player.getWorld()) popOne(id, b, false);
        }
        list.removeIf(b -> !b.display.isValid() || !b.anchor.isValid());
        if (list.isEmpty()) return;
        double yaw = Math.toRadians(player.getLocation().getYaw());
        Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vector back = new Vector(Math.sin(yaw), 0, -Math.cos(yaw));
        Location hand = player.getLocation().add(right.clone().multiply(0.35)).add(0, 1.0, 0);
        Vector[] push = new Vector[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Balloon b = list.get(i);
            // where it would like to be: a little bunch above and behind the shoulder
            double side = b.slot == 0 ? 0.25 : b.slot == 1 ? -0.25 : 0;
            Location want = player.getLocation().add(right.clone().multiply(side + 0.2)).add(back.clone().multiply(b.slot == 2 ? 0.55 : 0.3))
                    .add(0, 2.25 + (b.slot == 2 ? 0.25 : 0) + Math.sin(tick * 0.08 + b.slot * 2.1) * 0.08, 0);
            Vector pull = want.toVector().subtract(b.anchor.getLocation().toVector());
            if (pull.lengthSquared() > 36) {
                b.anchor.teleport(want);
                b.anchor.setVelocity(new Vector());
                push[i] = new Vector();
                continue;
            }
            push[i] = pull.multiply(0.07);
            // bump the others: balloons push apart when they get too close
            for (int k = 0; k < list.size(); k++) {
                if (k == i) continue;
                Vector apart = b.anchor.getLocation().toVector().subtract(list.get(k).anchor.getLocation().toVector());
                double d = apart.length();
                if (d < 0.75) {
                    if (d < 1e-3) apart = new Vector(i - k, 0, 0.1);
                    push[i].add(apart.normalize().multiply((0.75 - d) * 0.18));
                }
            }
        }
        for (int i = 0; i < list.size(); i++) {
            Balloon b = list.get(i);
            if (push[i] == null) continue;
            // springy and floaty: keeps most of its speed, so it swings past and settles back
            Vector v = b.anchor.getVelocity().multiply(0.82).add(push[i]).add(new Vector(0, 0.004, 0));
            if (v.length() > 0.6) v.normalize().multiply(0.6);
            b.anchor.setVelocity(v);
            // lean along the lead: the knot points back at the hand
            Vector lead = b.anchor.getLocation().toVector().subtract(hand.toVector());
            Vector3f up = lead.lengthSquared() < 1e-4 ? new Vector3f(0, 1, 0) : new Vector3f((float) lead.getX(), (float) lead.getY(), (float) lead.getZ()).normalize();
            if (up.y < 0.35f) up.set(up.x, 0.35f, up.z).normalize();          // never further over than a strong swing
            org.joml.Quaternionf lean = new org.joml.Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f(up.x * 0.7f, 1, up.z * 0.7f).normalize());
            move(b.display, new Matrix4f().rotate(lean).rotateY(tick * 0.015f + b.slot * 2f).translate(0, BALLOON_LIFT, 0));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUnleash(org.bukkit.event.entity.EntityUnleashEvent event) {
        if (event.getEntity().getScoreboardTags().contains(TAG) || isAnchor(event.getEntity())) event.setDropLeash(false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTouchAnchor(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (isAnchor(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerUnleash(org.bukkit.event.player.PlayerUnleashEntityEvent event) {
        if (isAnchor(event.getEntity())) event.setCancelled(true);
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
    /**
     * The magic wand: a spell bolt. A glowing star flies out of the wand leaving a trail of twinkles and sparkles; where
     * it lands it bursts into a shower of stars and a star circle glows on the ground, spins and fades. All the
     * particles are the wand's own little pictures (gadget/fx_*), not the game's.
     */
    private void magicWand(Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        World world = eye.getWorld();
        Vector right = new Vector(-look.getZ(), 0, look.getX());
        if (right.lengthSquared() < 1e-4) right = new Vector(1, 0, 0);
        right.normalize();
        Location tip = eye.clone().add(look.clone().multiply(0.9)).add(right.clone().multiply(0.3)).add(0, -0.15, 0);
        RayTraceResult hit = world.rayTraceBlocks(eye, look, 24);
        Location end = hit == null ? eye.clone().add(look.clone().multiply(24)) : hit.getHitPosition().toLocation(world);
        org.bukkit.block.BlockFace face = hit == null ? null : hit.getHitBlockFace();
        sound(eye, "block.amethyst_block.resonate", 1f, 1.7f);
        sound(eye, "block.beacon.power_select", 0.5f, 2f);
        ItemDisplay orb = fx(tip, "gadget/fx_orb", 0.5f);
        Vector path = end.toVector().subtract(tip.toVector());
        double length = path.length();
        Vector step = path.clone().normalize().multiply(0.9);
        int flight = Math.max(2, (int) Math.ceil(length / 0.9));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        every(flight, t -> {
            if (!orb.isValid()) return;
            Location at = orb.getLocation().add(step);
            orb.teleport(at);
            float pulse = 0.5f + (float) Math.sin(t * 0.9) * 0.06f;
            orb.setInterpolationDelay(0);
            orb.setInterpolationDuration(1);
            orb.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(t * 0.5f, 0, 0, 1), new Vector3f(pulse), new AxisAngle4f()));
            // the trail: twinkles and sparkles left hanging in the air, drifting and shrinking away
            for (int i = 0; i < 2; i++) {
                Location spot = at.clone().subtract(step.clone().multiply(r.nextDouble())).add(rand(0.18), rand(0.18), rand(0.18));
                boolean big = r.nextInt(3) == 0;
                driftFx(spot, big ? "gadget/fx_twinkle" : "gadget/fx_sparkle", big ? 0.32f : 0.2f,
                        new Vector(rand(0.01), 0.008 + r.nextDouble() * 0.01, rand(0.01)), 12 + r.nextInt(10));
            }
            if (t % 4 == 0) sound(at, "block.amethyst_block.chime", 0.35f, 1.6f + r.nextFloat() * 0.4f);
        }, () -> {
            orb.remove();
            Location at = end.clone();
            if (face != null) at.add(face.getDirection().multiply(0.25));
            sound(at, "entity.firework_rocket.twinkle", 1f, 1.2f);
            sound(at, "block.amethyst_block.resonate", 0.8f, 1.2f);
            // the burst: stars flying out every way, slowing, spinning and shrinking
            for (int i = 0; i < 12; i++) {
                Vector out = new Vector(r.nextGaussian(), r.nextGaussian() + 0.4, r.nextGaussian()).normalize().multiply(0.18 + r.nextDouble() * 0.12);
                driftFx(at, i % 3 == 0 ? "gadget/fx_twinkle" : "gadget/fx_star", 0.28f + r.nextFloat() * 0.14f, out, 18 + r.nextInt(10));
            }
            // the star circle, laid flat on what it hit (the ground, a wall or a ceiling), growing in and spinning
            if (face != null) starCircle(end.clone().add(face.getDirection().multiply(0.08)), face);
        });
    }

    /** One of the wand's particle pictures, facing whoever looks at it. */
    private ItemDisplay fx(Location at, String model, float size) {
        ItemDisplay d = at.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setItemStack(modelled(Material.NETHER_STAR, model));
            e.setBillboard(Display.Billboard.CENTER);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTeleportDuration(1);
            e.setPersistent(false);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(size), new AxisAngle4f()));
        });
        tagged(d, "wand_fx");
        return d;
    }

    /** A particle picture that drifts (slowing down), turns, and shrinks away over its life. */
    void driftFx(Location at, String model, float size, Vector velocity, int life) {
        ItemDisplay d = fx(at, model, size);
        float spin = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.5f;
        every(life, t -> {
            if (!d.isValid()) return;
            velocity.multiply(0.88);
            d.teleport(d.getLocation().add(velocity));
            float k = size * (t < 3 ? 0.6f + t * 0.15f : (float) (life - t) / (life - 3));     // pops in, then shrinks away
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(1);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(t * spin, 0, 0, 1), new Vector3f(Math.max(0.01f, k)), new AxisAngle4f()));
        }, d::remove);
    }

    /** The star circle where the spell lands: lies flat on the face it hit, grows in turning, glows, then shrinks away. */
    private void starCircle(Location at, org.bukkit.block.BlockFace face) {
        org.joml.Quaternionf lay = switch (face) {                 // the picture is upright (facing +z): turn it onto the face
            case UP -> new org.joml.Quaternionf().rotateX((float) -Math.PI / 2);
            case DOWN -> new org.joml.Quaternionf().rotateX((float) Math.PI / 2);
            case NORTH -> new org.joml.Quaternionf().rotateY((float) Math.PI);
            case EAST -> new org.joml.Quaternionf().rotateY((float) Math.PI / 2);
            case WEST -> new org.joml.Quaternionf().rotateY((float) -Math.PI / 2);
            default -> new org.joml.Quaternionf();
        };
        Vector3f normal = new Vector3f(0, 0, 1).rotate(lay);
        ItemDisplay circle = at.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setItemStack(modelled(Material.NETHER_STAR, "gadget/fx_circle"));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setPersistent(false);
            e.setTransformation(new Transformation(new Vector3f(), lay, new Vector3f(0.01f), new org.joml.Quaternionf()));
        });
        tagged(circle, "wand_fx");
        int life = 50;
        every(life, t -> {
            if (!circle.isValid()) return;
            float size = t < 8 ? 2.2f * (t + 1) / 8f : t > life - 10 ? 2.2f * (life - t) / 10f : 2.2f;
            org.joml.Quaternionf turn = new org.joml.Quaternionf().rotateAxis(t * 0.06f, normal).mul(lay);
            circle.setInterpolationDelay(0);
            circle.setInterpolationDuration(1);
            circle.setTransformation(new Transformation(new Vector3f(), turn, new Vector3f(Math.max(0.01f, size)), new org.joml.Quaternionf()));
            if (t % 6 == 0 && t < life - 10) {                    // a few twinkles rising off it
                Vector off = new Vector(rand(0.8), rand(0.8), rand(0.8));
                driftFx(circle.getLocation().add(off), "gadget/fx_twinkle", 0.22f,
                        face.getDirection().multiply(0.03 + ThreadLocalRandom.current().nextDouble() * 0.02), 16);
            }
        }, circle::remove);
    }

    /** A short teleport forward (never into a wall). */
    /** Where the blink stick would take the player right now (null if nowhere): never into a wall. */
    private Location blinkTarget(Player player) {
        Location from = player.getLocation();
        Vector look = from.getDirection().setY(0);
        if (look.lengthSquared() < 0.01) return null;
        look.normalize();
        if (from.getPitch() < -35) look.setY(0.45).normalize(); // looking up: blink up a little too
        Location body = from.clone().add(0, 1, 0);
        RayTraceResult wall = player.getWorld().rayTraceBlocks(body, look, 7.5, org.bukkit.FluidCollisionMode.NEVER, true);
        double max = wall == null ? 7 : wall.getHitPosition().distance(body.toVector()) - 0.7;
        for (double d = max; d >= 1; d -= 0.5) {
            Location test = from.clone().add(look.clone().multiply(d));
            Block feet = test.getBlock(), head = feet.getRelative(0, 1, 0);
            if (feet.isPassable() && head.isPassable()) return test;
        }
        return null;
    }

    /** Each player's blink marker: a glowing square (only they can see) on the spot the blink stick would take them. */
    private final Map<UUID, BlockDisplay> blinkMarkers = new HashMap<>();
    private static final NamespacedKey BLINK_MODEL = new NamespacedKey("fractal", "gadget/blink_stick");

    private void blinkMarker(Player player) {
        UUID id = player.getUniqueId();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean holding = hand.hasItemMeta() && BLINK_MODEL.equals(hand.getItemMeta().getItemModel()) && !parkour(player);
        Location to = holding ? blinkTarget(player) : null;
        BlockDisplay marker = blinkMarkers.get(id);
        if (to == null) {
            if (marker != null) {
                marker.remove();
                blinkMarkers.remove(id);
            }
            return;
        }
        // stand it on whatever is under the landing spot (if it's in the air, right where the feet would be)
        Location spot = to.clone();
        Block below = spot.clone().add(0, -0.05, 0).getBlock();
        double floor = below.isPassable() ? spot.getY() : below.getBoundingBox().getMaxY();
        Location at = new Location(spot.getWorld(), spot.getX() - 0.45, floor + 0.02, spot.getZ() - 0.45);
        if (marker == null || !marker.isValid() || marker.getWorld() != at.getWorld()) {
            if (marker != null) marker.remove();
            marker = at.getWorld().spawn(at, BlockDisplay.class, d -> {
                d.setBlock(Material.PURPLE_STAINED_GLASS.createBlockData());
                d.setVisibleByDefault(false);
                d.setPersistent(false);
                d.setGlowing(true);
                d.setGlowColorOverride(Color.fromRGB(0xC77DFF));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(2);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9f, 0.05f, 0.9f), new AxisAngle4f()));
            });
            tagged(marker, "blink_marker");
            player.showEntity(plugin, marker);
            blinkMarkers.put(id, marker);
        } else {
            marker.teleport(at);
        }
    }

    private void blink(Player player) {
        Location from = player.getLocation();
        Location to = blinkTarget(player);
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
    /**
     * TNT Throw: toss a lit TNT. It tumbles through the air, bounces and skids along the ground hissing and flashing
     * faster and faster, swells up, then goes off with a big (harmless) bang: a flash, a shockwave, smoke, sparks and
     * bits flying out, and anyone close is bounced up and away. Nothing breaks and nobody gets hurt.
     */
    private void fakeTnt(Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        World world = eye.getWorld();
        Location start = eye.clone().add(look.clone().multiply(0.8)).add(0, -0.2, 0);
        if (!start.getBlock().isPassable()) start = eye.clone();
        float size = 0.7f;
        BlockDisplay tnt = world.spawn(start, BlockDisplay.class, d -> {
            d.setBlock(Material.TNT.createBlockData());
            d.setTeleportDuration(2);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(-size / 2), new org.joml.Quaternionf(), new Vector3f(size), new org.joml.Quaternionf()));
        });
        tagged(tnt, "tnt");
        sound(eye, "entity.snowball.throw", 1f, 0.6f);
        sound(eye, "entity.tnt.primed", 1f, 1f);
        Vector velocity = look.clone().multiply(0.42).add(new Vector(0, 0.28, 0));
        org.joml.Quaternionf turn = new org.joml.Quaternionf();
        Vector3f axis = new Vector3f((float) look.getZ(), 0, (float) -look.getX());
        if (axis.lengthSquared() < 1e-4) axis.set(1, 0, 0);
        axis.normalize();
        float[] spin = {0.35f};
        int fuse = 48;
        double half = size / 2;
        every(fuse, t -> {
            if (!tnt.isValid()) return;
            Location at = tnt.getLocation();
            velocity.setY(velocity.getY() - 0.04);
            velocity.multiply(0.985);
            double x = at.getX(), y = at.getY(), z = at.getZ();
            double nx = x + velocity.getX();
            if (!world.getBlockAt((int) Math.floor(nx + Math.signum(velocity.getX()) * half), (int) Math.floor(y), (int) Math.floor(z)).isPassable()) {
                velocity.setX(-velocity.getX() * 0.4);
                nx = x;
            }
            double nz = z + velocity.getZ();
            if (!world.getBlockAt((int) Math.floor(nx), (int) Math.floor(y), (int) Math.floor(nz + Math.signum(velocity.getZ()) * half)).isPassable()) {
                velocity.setZ(-velocity.getZ() * 0.4);
                nz = z;
            }
            double ny = y + velocity.getY();
            boolean grounded = false;
            org.bukkit.block.Block under = world.getBlockAt((int) Math.floor(nx), (int) Math.floor(ny - half), (int) Math.floor(nz));
            if (velocity.getY() <= 0 && !under.isPassable()) {
                double top = under.getBoundingBox().getHeight() > 0 ? under.getBoundingBox().getMaxY() : under.getY() + 1;
                ny = top + half;
                if (velocity.getY() < -0.15) {                       // a heavy little bounce
                    velocity.setY(-velocity.getY() * 0.3);
                    sound(at, "block.wood.place", 0.8f, 0.7f);
                    world.spawnParticle(Particle.BLOCK, at.clone().add(0, -half, 0), 6, 0.2, 0.05, 0.2, 0, under.getBlockData());
                } else {
                    velocity.setY(0);
                    grounded = true;
                }
                velocity.setX(velocity.getX() * 0.75).setZ(velocity.getZ() * 0.75);   // skids to a stop
            }
            tnt.teleport(new Location(world, nx, ny, nz));
            // tumbles in the air, settles flat on the ground
            spin[0] = grounded ? spin[0] * 0.6f : spin[0];
            if (spin[0] > 0.01f) turn.premul(new org.joml.Quaternionf().rotateAxis(spin[0], axis));
            if (grounded && spin[0] <= 0.01f) {
                turn.set(new org.joml.Quaternionf());
            }
            // flashes faster and faster, and swells up just before it goes
            int left = fuse - t;
            boolean flash = left > 20 ? (t / 6) % 2 == 0 : left > 8 ? (t / 3) % 2 == 0 : t % 2 == 0;
            tnt.setBrightness(flash ? new Display.Brightness(15, 15) : null);
            float swell = size * (left < 10 ? 1f + (10 - left) * 0.045f : 1f);
            Vector3f corner = new Vector3f(-swell / 2).rotate(turn);
            tnt.setInterpolationDelay(0);
            tnt.setInterpolationDuration(2);
            tnt.setTransformation(new Transformation(corner, new org.joml.Quaternionf(turn), new Vector3f(swell), new org.joml.Quaternionf()));
            Location fuseTop = tnt.getLocation().add(0, half + 0.1, 0);
            world.spawnParticle(Particle.SMOKE, fuseTop, 1, 0.03, 0, 0.03, 0.01);
            if (t % 3 == 0) world.spawnParticle(Particle.SMALL_FLAME, fuseTop, 1, 0.02, 0, 0.02, 0.005);
            if (left == 10) sound(fuseTop, "entity.creeper.primed", 1f, 1.4f);
        }, () -> {
            Location centre = tnt.getLocation();
            org.bukkit.block.Block floor = centre.clone().add(0, -1, 0).getBlock();
            BlockData debris = floor.getType().isSolid() ? floor.getBlockData() : Material.TNT.createBlockData();
            tnt.remove();
            // the bang
            world.spawnParticle(Particle.EXPLOSION_EMITTER, centre, 1);
            world.spawnParticle(Particle.FLASH, centre, 1, 0, 0, 0, 0, Color.WHITE);
            world.spawnParticle(Particle.LARGE_SMOKE, centre, 30, 0.6, 0.4, 0.6, 0.06);
            world.spawnParticle(Particle.LAVA, centre, 12, 0.3, 0.2, 0.3, 0);
            world.spawnParticle(Particle.FIREWORK, centre, 25, 0.1, 0.1, 0.1, 0.25);
            sound(centre, "entity.generic.explode", 1.2f, 0.9f);
            sound(centre, "entity.dragon_fireball.explode", 0.5f, 1.2f);
            // a shockwave ring rolling out along the ground
            every(8, k -> {
                double r = 0.6 + k * 0.45;
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2 * i / 24;
                    world.spawnParticle(Particle.CLOUD, centre.clone().add(Math.cos(a) * r, -0.2, Math.sin(a) * r), 1, 0, 0, 0, 0.01);
                }
            }, null);
            // bits flying out (only pictures of blocks: nothing breaks)
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < 10; i++) {
                BlockData bit = i % 3 == 0 ? Material.TNT.createBlockData() : debris;
                float s = 0.18f + r.nextFloat() * 0.14f;
                BlockDisplay piece = world.spawn(centre, BlockDisplay.class, d -> {
                    d.setBlock(bit);
                    d.setTeleportDuration(2);
                    d.setPersistent(false);
                    d.setTransformation(new Transformation(new Vector3f(-s / 2), new org.joml.Quaternionf(), new Vector3f(s), new org.joml.Quaternionf()));
                });
                tagged(piece, "debris");
                Vector fly = new Vector(rand(0.5), 0.35 + r.nextDouble() * 0.35, rand(0.5));
                org.joml.Quaternionf roll = new org.joml.Quaternionf();
                Vector3f rollAxis = new Vector3f(r.nextFloat() - 0.5f, r.nextFloat() - 0.5f, r.nextFloat() - 0.5f).normalize();
                every(30, k -> {
                    if (!piece.isValid()) return;
                    fly.setY(fly.getY() - 0.045);
                    Location next = piece.getLocation().add(fly);
                    if (!next.getBlock().isPassable()) {
                        fly.multiply(0);
                        return;
                    }
                    piece.teleport(next);
                    roll.premul(new org.joml.Quaternionf().rotateAxis(0.4f, rollAxis));
                    float shrink = k > 20 ? s * (30 - k) / 10f : s;
                    piece.setInterpolationDelay(0);
                    piece.setInterpolationDuration(2);
                    piece.setTransformation(new Transformation(new Vector3f(-shrink / 2).rotate(roll), new org.joml.Quaternionf(roll),
                            new Vector3f(shrink), new org.joml.Quaternionf()));
                }, piece::remove);
            }
            // anyone close is bounced up and away (no damage)
            for (Player near : world.getPlayers()) {
                double d = near.getLocation().distance(centre);
                if (d > 4 || parkour(near)) continue;
                Vector push = near.getLocation().toVector().subtract(centre.toVector()).setY(0);
                if (push.lengthSquared() < 1e-4) push = new Vector(0, 0, 0);
                else push.normalize().multiply(0.6 * (1 - d / 4));
                near.setVelocity(near.getVelocity().add(push.setY(0.55 * (1 - d / 5))));
                near.setFallDistance(0);
            }
        });
    }

    /** A disco ball over the player, spinning, throwing coloured light and playing a little tune. */
    /**
     * The disco: a mirror ball hanging from a chain on a little lighting rig, over a 5 by 5 dance floor of glowing
     * tiles that change colour to the beat (in waves, checks and rings), with beams of light and a tune. It needs a
     * flat, open spot: solid ground under all of the floor, room above it, and no other disco right there; if the spot
     * isn't right the player is told and gets their cooldown back.
     */
    private void discoBall(Player player) {
        Location centre = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        World world = centre.getWorld();
        String why = null;
        if (!player.isOnGround()) why = "air";
        else {
            for (int dx = -2; dx <= 2 && why == null; dx++) {
                for (int dz = -2; dz <= 2 && why == null; dz++) {
                    Block floor = centre.clone().add(dx, -1, dz).getBlock();
                    if (!floor.getType().isOccluding()) why = "floor";
                    for (int dy = 0; dy < 5 && why == null; dy++) {
                        if (!centre.clone().add(dx, dy, dz).getBlock().isPassable()) why = "room";
                    }
                }
            }
            for (Entity near : world.getNearbyEntities(centre, 6, 5, 6)) {
                if (near.getScoreboardTags().contains("lobby_disco_floor")) why = "taken";
            }
        }
        // the stand goes just off the floor: behind the player if there's room, else to a side, else in front
        Location foot = null;
        if (why == null) {
            for (float turn : new float[]{180, 90, -90, 0}) {
                double a = Math.toRadians(player.getLocation().getYaw() + turn);
                Location spot = centre.clone().add(-Math.sin(a) * 2.75, 0, Math.cos(a) * 2.75);
                boolean ok = spot.clone().add(0, -1, 0).getBlock().getType().isSolid();
                for (int dy = 0; dy < 5 && ok; dy++) ok = spot.clone().add(0, dy, 0).getBlock().isPassable();
                if (ok) {
                    foot = spot;
                    break;
                }
            }
            if (foot == null) why = "room";
        }
        if (why != null) {
            plugin.messages().send(player, "collectibles.gadget.disco-" + why);
            sound(player.getLocation(), "entity.villager.no", 0.8f, 1.2f);
            collectibles.refund(player, "gadget:disco_ball");
            return;
        }
        unplace(player.getUniqueId());
        List<Entity> parts = new ArrayList<>();
        // the dance floor: 25 glowing tiles laid just on top of the ground (pictures only: the ground isn't changed)
        BlockDisplay[][] tiles = new BlockDisplay[5][5];
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Location corner = centre.clone().add(dx - 0.5, 0.005, dz - 0.5);
                BlockDisplay tile = world.spawn(corner, BlockDisplay.class, d -> {
                    d.setBlock(Material.WHITE_CONCRETE.createBlockData());
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setPersistent(false);
                    d.setTransformation(new Transformation(new Vector3f(0.03f, 0, 0.03f), new AxisAngle4f(), new Vector3f(0.94f, 0.02f, 0.94f), new AxisAngle4f()));
                });
                tile.addScoreboardTag("lobby_disco_floor");
                tagged(tile, "disco_floor");
                tiles[dx + 2][dz + 2] = tile;
                parts.add(tile);
            }
        }
        // the rig: a dark stand at the back corner, an arm over the middle, and a chain the ball hangs from
        BlockData metal = Material.POLISHED_BLACKSTONE.createBlockData();
        Location stand = foot;
        BlockDisplay pole = world.spawn(stand, BlockDisplay.class, d -> {
            d.setBlock(metal);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(-0.09f, 0, -0.09f), new AxisAngle4f(), new Vector3f(0.18f, 4.1f, 0.18f), new AxisAngle4f()));
        });
        BlockDisplay base = world.spawn(stand, BlockDisplay.class, d -> {
            d.setBlock(metal);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(-0.3f, 0, -0.3f), new AxisAngle4f(), new Vector3f(0.6f, 0.12f, 0.6f), new AxisAngle4f()));
        });
        // the arm: from the top of the pole to above the middle of the floor
        Vector across = centre.toVector().subtract(stand.toVector()).setY(0);
        float armLength = (float) across.length();
        float armYaw = (float) Math.atan2(across.getX(), across.getZ());
        Location top = stand.clone().add(0, 4.0, 0);
        org.joml.Quaternionf armTurn = new org.joml.Quaternionf().rotateY(armYaw);
        BlockDisplay arm = world.spawn(top, BlockDisplay.class, d -> {
            d.setBlock(metal);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(-0.07f, 0, -0.07f).rotate(armTurn), armTurn,
                    new Vector3f(0.14f, 0.14f, armLength + 0.07f), new org.joml.Quaternionf()));
        });
        Location hook = centre.clone().add(0, 4.0, 0);
        BlockDisplay chain = world.spawn(hook, BlockDisplay.class, d -> {
            d.setBlock(Material.IRON_CHAIN.createBlockData());
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(-0.5f, -0.82f, -0.5f), new AxisAngle4f(), new Vector3f(1f, 0.82f, 1f), new AxisAngle4f()));
        });
        Location at = centre.clone().add(0, 2.85, 0);
        ItemDisplay ball = world.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(modelled(Material.GLASS, "gadget/disco_ball_3d"));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9f), new AxisAngle4f()));
        });
        for (Entity e : List.of(pole, base, arm, chain, ball)) {
            tagged(e, "disco");
            parts.add(e);
        }
        sound(centre, "block.anvil.place", 0.4f, 1.6f);
        sound(centre, "block.beacon.activate", 0.8f, 1.6f);
        Material[] colours = {Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.LIME_CONCRETE, Material.YELLOW_CONCRETE,
                Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE, Material.RED_CONCRETE, Material.CYAN_CONCRETE, Material.PINK_CONCRETE};
        int[] melody = {0, 4, 7, 12, 7, 4, 0, 5, 9, 12, 9, 5};
        BukkitTask run = every(320, t -> {
            if (t % 2 == 0) {
                ball.setInterpolationDelay(0);
                ball.setInterpolationDuration(2);
                ball.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) Math.toRadians(t * 9), 0, 1, 0),
                        new Vector3f(0.9f), new AxisAngle4f()));
            }
            if (t % 8 == 0) {                          // the floor changes with the beat: waves, then checks, then rings
                int beat = t / 8, mode = (beat / 8) % 3;
                for (int i = 0; i < 5; i++) {
                    for (int k = 0; k < 5; k++) {
                        int pick = switch (mode) {
                            case 0 -> i + beat;                                              // a wave sweeping across
                            case 1 -> ((i + k + beat) % 2) * 3 + beat;                       // flashing checks
                            default -> Math.max(Math.abs(i - 2), Math.abs(k - 2)) + beat;  // rings from the middle
                        };
                        tiles[i][k].setBlock(colours[Math.floorMod(pick, colours.length)].createBlockData());
                    }
                }
                int note = melody[beat % melody.length];
                sound(at, "block.note_block.bell", 0.7f, (float) Math.pow(2, (note - 12) / 12.0));
                if (beat % 2 == 0) sound(centre, "block.note_block.basedrum", 0.6f, 0.8f);
                world.spawnParticle(Particle.NOTE, at.clone().add(rand(1), -0.6, rand(1)), 0, note / 24.0, 0, 0, 1);
            }
            if (t % 3 == 0) {                          // a beam of coloured light from the ball down to the floor
                double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2), r = ThreadLocalRandom.current().nextDouble(0.5, 2.4);
                Location to = centre.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                Vector step = to.toVector().subtract(at.toVector()).multiply(1 / 10.0);
                Color colour = party();
                for (int i = 1; i <= 10; i++) {
                    world.spawnParticle(Particle.DUST, at.clone().add(step.clone().multiply(i)), 1, 0, 0, 0, 0, dust(colour, 0.8f));
                }
            }
        }, () -> {
            world.spawnParticle(Particle.FIREWORK, at, 30, 0.2, 0.2, 0.2, 0.2);
            parts.forEach(Entity::remove);
            placed.remove(player.getUniqueId());
        });
        placed.put(player.getUniqueId(), new Placed(ball, run, parts.toArray(new Entity[0])));
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
            case "paint" -> paint(event.getHitBlock(), at, effect.contains(":") ? Integer.parseInt(effect.substring(effect.indexOf(':') + 1)) : -1);
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
    /** The paints, in the pack's order (icons.PAINTS): the block it splats, and its colour for the droplets. */
    private static final Material[] PAINT_BLOCKS = {Material.PINK_CONCRETE, Material.PURPLE_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.LIME_CONCRETE, Material.YELLOW_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE, Material.CYAN_CONCRETE};
    private static final int[] PAINT_RGB = {0xFF6FD8, 0x9B5CFF, 0x5BC8FF, 0x7FE35A, 0xFFD23A, 0xFF8A1F, 0xE040C0, 0x2FC8C8};
    /** Fires a paintball of the colour the gun shows, then loads a different colour (and the gun changes to it). */
    private void paintball(Player player) {
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack held = player.getInventory().getItem(slot);
        NamespacedKey shown = held != null && held.hasItemMeta() ? held.getItemMeta().getItemModel() : null;
        boolean gun = shown != null && shown.getKey().startsWith("gadget/paintball_gun");
        int paint = 0;                                                     // the plain gun is pink, the first paint
        if (gun && shown.getKey().startsWith("gadget/paintball_gun_")) {
            try {
                paint = Integer.parseInt(shown.getKey().substring("gadget/paintball_gun_".length())) % PAINT_BLOCKS.length;
            } catch (NumberFormatException ignored) {
            }
        }
        Snowball ball = toss(player, modelled(Material.SNOWBALL, "gadget/paintball_" + paint), "paint:" + paint, 2.2);
        sound(player.getEyeLocation(), "entity.chicken.egg", 0.6f, 1.6f);
        sound(player.getEyeLocation(), "block.piston.contract", 0.25f, 2f);
        int next = (paint + 1 + ThreadLocalRandom.current().nextInt(PAINT_BLOCKS.length - 1)) % PAINT_BLOCKS.length;
        if (gun) {
            held.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", "gadget/paintball_gun_" + next)));
            player.getInventory().setItem(slot, held);
        }
        Color trail = Color.fromRGB(PAINT_RGB[paint]);
        every(20, t -> {
            if (ball.isValid() && t % 2 == 0) ball.getWorld().spawnParticle(Particle.DUST, ball.getLocation(), 1, 0, 0, 0, 0, dust(trail, 0.7f));
        }, null);
    }

    private void paint(Block hit, Location at, int colour) {
        if (hit == null) return;
        int index = colour >= 0 && colour < PAINT_BLOCKS.length ? colour : ThreadLocalRandom.current().nextInt(PAINT_BLOCKS.length);
        BlockData paint = PAINT_BLOCKS[index].createBlockData();
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
        at.getWorld().spawnParticle(Particle.DUST, at, 30, 0.8, 0.8, 0.8, 0, dust(Color.fromRGB(PAINT_RGB[index]), 1.4f));
        sound(at, "fractal:gadget.paint_splat", 1f, 0.9f + ThreadLocalRandom.current().nextFloat() * 0.2f);
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

    /** A gesture: a mini armor stand of the player acts it out while they watch (see GestureShow). */
    void gesture(Player player, String key) {
        gestures.play(player, key);
    }

    /** A speech bubble over the copy (a random line from messages.yml collectibles.gesture-say.KEY). */
    void say(org.bukkit.entity.LivingEntity clone, String key, int length) {
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

}
