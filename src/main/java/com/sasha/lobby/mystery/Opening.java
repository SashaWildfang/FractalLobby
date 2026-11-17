/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.mystery;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.Messages;
import com.sasha.lobby.hologram.HologramText;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One box being opened, as a show everyone nearby can watch (about 10 seconds):
 * <ol>
 *   <li><b>Rise</b>: the box grows out of the vault with a whoosh of portal particles;</li>
 *   <li><b>Charge</b>: it floats up, spinning faster and faster, its stars orbiting it as glowing orbs, with a
 *   spiral of sparks and a chime climbing in pitch;</li>
 *   <li><b>Shake</b>: it trembles and pulses as the orbs are pulled into it;</li>
 *   <li><b>Burst</b>: it bursts in a ring and a sphere of the prize's rarity colour (legendary adds totem
 *   sparkles), with a sound to match the rarity;</li>
 *   <li><b>Reveal</b>: the prize pops out, glowing in its rarity colour and turning slowly, with a hologram
 *   above it: the rarity (shimmering), what it is, whether it's new or turned into Mystery Dust, and who opened it;</li>
 *   <li><b>Fade</b>: it shrinks away in a puff.</li>
 * </ol>
 */
final class Opening {

    static final String TAG = "lobby_mystery_opening";
    private static final int RISE = 12, CHARGE = 62, SHAKE = 84, REVEAL_END = 190, END = 202;

    private final Lobby plugin;
    private final MysteryBoxes boxes;
    private final Player player;
    private final Location base;
    private final int stars;
    private final Reward reward;
    private final boolean duplicate;
    private final long dust;
    private final Vault.Spot spot;
    private final Color colour;

    private ItemDisplay box, prize;
    private final List<ItemDisplay> orbs = new ArrayList<>();
    private TextDisplay text;
    private BukkitTask task;
    private int tick;
    private float spin;
    private float spinSpeed = 4f;

    Opening(Lobby plugin, MysteryBoxes boxes, Player player, Vault.Place place, int stars, Reward reward, boolean duplicate, long dust) {
        this.plugin = plugin;
        this.boxes = boxes;
        this.player = player;
        this.spot = place.spot();
        this.base = place.at().clone();
        this.base.setYaw(0);
        this.base.setPitch(0);
        this.stars = stars;
        this.reward = reward;
        this.duplicate = duplicate;
        this.dust = dust;
        this.colour = boxes.rarityColor(reward.rarity());
    }

    void start() {
        boxes.vault().busy(player, spot);
        if (spot == null) player.lookAt(base.clone().add(0, 1.0, 0), io.papermc.paper.entity.LookAnchor.EYES);
        box = display(model("mystery/box_" + stars, Material.CHEST), base.clone().add(0, 0.35, 0), 0f);
        for (int i = 0; i < stars; i++) orbs.add(display(model("parkour/dot", Material.GLOWSTONE_DUST), base.clone().add(0, 0.6, 0), 0f));
        for (ItemDisplay orb : orbs) orb.setBillboard(Display.Billboard.CENTER);
        sound("block.ender_chest.open", 0.8f, 0.8f);
        sound("block.beacon.activate", 0.6f, 1.6f);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::step, 1L, 1L);
    }

    private void step() {
        tick++;
        try {
            if (tick <= RISE) rise();
            else if (tick <= CHARGE) charge();
            else if (tick <= SHAKE) shake();
            else if (tick == SHAKE + 1) burst();
            else if (tick <= REVEAL_END) reveal();
            else if (tick <= END) fade();
            else finish();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Mystery Box opening stopped: " + e.getMessage());
            finish();
        }
    }

    // ---------------------------------------------------------------- the stages

    private void rise() {
        if (tick == 1) set(box, 0.35f, 1.0f, 0, RISE);
        world().spawnParticle(Particle.REVERSE_PORTAL, base.clone().add(0, 0.4, 0), 6, 0.3, 0.2, 0.3, 0.05);
    }

    private void charge() {
        double progress = (tick - RISE) / (double) (CHARGE - RISE);   // 0..1
        spinSpeed = (float) (4 + progress * 26);                     // degrees per tick, speeding up
        if (tick % 2 == 0) {
            spin += spinSpeed * 2;
            float lift = (float) (0.35 + progress * 0.95 + Math.sin(tick * 0.3) * 0.05);
            set(box, lift, 1.0f, spin, 2);
            // The stars orbit the box, closer and faster as it charges.
            double radius = 0.95 - progress * 0.35;
            for (int i = 0; i < orbs.size(); i++) {
                double angle = Math.toRadians(spin * 0.8 + 360.0 / orbs.size() * i);
                Vector3f at = new Vector3f((float) (Math.cos(angle) * radius), lift - 0.25f + (float) Math.sin(angle * 2) * 0.1f,
                        (float) (Math.sin(angle) * radius));
                move(orbs.get(i), at, 0.22f, 2);
            }
        }
        // A spiral of sparks climbing around it.
        double a = tick * 0.6;
        double r = 0.9 - progress * 0.3;
        Location spark = base.clone().add(Math.cos(a) * r, 0.3 + progress * 1.2, Math.sin(a) * r);
        world().spawnParticle(Particle.END_ROD, spark, 1, 0, 0, 0, 0);
        world().spawnParticle(Particle.PORTAL, base.clone().add(0, 0.8 + progress, 0), 4, 0.4, 0.4, 0.4, 0.4);
        if (tick % 4 == 0) sound("block.note_block.chime", 0.5f, (float) (0.6 + progress * 1.4));
    }

    private void shake() {
        double progress = (tick - CHARGE) / (double) (SHAKE - CHARGE);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        spin += 40;
        float pulse = (tick % 4 < 2) ? 1.12f : 1.0f;
        float jitter = (float) (0.04 + progress * 0.06);
        Vector3f wobble = new Vector3f((float) random.nextDouble(-jitter, jitter), 1.3f + (float) random.nextDouble(-jitter, jitter),
                (float) random.nextDouble(-jitter, jitter));
        box.setInterpolationDelay(0);
        box.setInterpolationDuration(1);
        box.setTransformation(new Transformation(wobble, rotation(spin), new Vector3f(pulse), new AxisAngle4f()));
        // The orbs are pulled into the box.
        for (int i = 0; i < orbs.size(); i++) {
            double angle = Math.toRadians(spin + 360.0 / orbs.size() * i);
            double radius = 0.6 * (1 - progress);
            move(orbs.get(i), new Vector3f((float) (Math.cos(angle) * radius), 1.1f, (float) (Math.sin(angle) * radius)),
                    (float) (0.22 * (1 - progress) + 0.05), 1);
        }
        world().spawnParticle(Particle.ELECTRIC_SPARK, base.clone().add(0, 1.3, 0), 5, 0.35, 0.35, 0.35, 0.05);
        if (tick % 2 == 0) sound("block.amethyst_block.chime", 0.6f, (float) (1.0 + progress));
    }

    private void burst() {
        box.remove();
        for (ItemDisplay orb : orbs) orb.remove();
        orbs.clear();
        Location centre = base.clone().add(0, 1.3, 0);
        World world = world();
        Particle.DustOptions big = new Particle.DustOptions(colour, 1.6f), small = new Particle.DustOptions(colour, 0.9f);
        // A sphere and a flat ring in the rarity's colour, and white sparks flying out.
        for (int i = 0; i < 90; i++) {
            double u = ThreadLocalRandom.current().nextDouble(), v = ThreadLocalRandom.current().nextDouble();
            double theta = 2 * Math.PI * u, phi = Math.acos(2 * v - 1), r = 1.1;
            world.spawnParticle(Particle.DUST, centre.clone().add(r * Math.sin(phi) * Math.cos(theta), r * Math.cos(phi),
                    r * Math.sin(phi) * Math.sin(theta)), 1, 0, 0, 0, 0, big);
        }
        for (int i = 0; i < 48; i++) {
            double angle = 2 * Math.PI * i / 48;
            world.spawnParticle(Particle.DUST, centre.clone().add(Math.cos(angle) * 1.7, 0, Math.sin(angle) * 1.7), 1, 0, 0, 0, 0, small);
        }
        world.spawnParticle(Particle.FIREWORK, centre, 40, 0.1, 0.1, 0.1, 0.25);
        world.spawnParticle(Particle.EXPLOSION, centre, 1);
        if (reward.rarity().ordinal() >= Reward.Rarity.EPIC.ordinal()) world.spawnParticle(Particle.WITCH, centre, 40, 0.6, 0.6, 0.6, 0.1);
        if (reward.rarity() == Reward.Rarity.LEGENDARY) world.spawnParticle(Particle.TOTEM_OF_UNDYING, centre, 80, 0.4, 0.6, 0.4, 0.6);

        sound("entity.firework_rocket.large_blast", 0.9f, 1.0f);
        sound("entity.firework_rocket.twinkle", 0.7f, 1.2f);
        switch (reward.rarity()) {
            case COMMON -> sound("entity.player.levelup", 0.6f, 1.6f);
            case RARE -> {
                sound("entity.player.levelup", 0.7f, 1.2f);
                sound("block.amethyst_block.resonate", 0.8f, 1.2f);
            }
            case EPIC -> {
                sound("entity.player.levelup", 0.8f, 0.9f);
                sound("entity.illusioner.cast_spell", 0.8f, 1.1f);
            }
            case LEGENDARY -> {
                sound("ui.toast.challenge_complete", 0.9f, 1.0f);
                sound("block.beacon.power_select", 1f, 1.2f);
            }
        }

        // The prize and its hologram.
        prize = display(reward.icon(plugin), centre.clone(), 0f);
        prize.setGlowing(true);
        prize.setGlowColorOverride(colour);
        prize.setInterpolationDelay(0);
        prize.setInterpolationDuration(6);
        prize.setTransformation(new Transformation(new Vector3f(), rotation(0), new Vector3f(1.15f), new AxisAngle4f()));
        text = world.spawn(base.clone().add(0, 2.05, 0), TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setShadowed(true);
            display.setPersistent(false);
            display.addScoreboardTag(TAG);
            display.setBrightness(new Display.Brightness(15, 15));
            display.text(revealText(0));
        });
        tell();
    }

    private void reveal() {
        int since = tick - SHAKE;
        if (since == 7) set(prize, 0f, 0.85f, spin, 5); // settle after the pop
        if (since == 40) boxes.vault().release(player); // they've seen it: free to walk off
        if (since > 12 && tick % 2 == 0) {
            spin += 6;
            float bob = (float) Math.sin(since * 0.12) * 0.08f;
            set(prize, bob, 0.85f, spin, 2);
        }
        if (tick % 3 == 0) text.text(revealText(since));
        // A slow ring of the rarity's colour around the prize.
        double angle = since * 0.25;
        Location centre = base.clone().add(0, 1.3, 0);
        Particle.DustOptions ring = new Particle.DustOptions(colour, 0.8f);
        for (int k = 0; k < 2; k++) {
            double a = angle + Math.PI * k;
            world().spawnParticle(Particle.DUST, centre.clone().add(Math.cos(a) * 0.75, Math.sin(since * 0.1) * 0.15, Math.sin(a) * 0.75),
                    1, 0, 0, 0, 0, ring);
        }
        if (reward.rarity() == Reward.Rarity.LEGENDARY && tick % 3 == 0) {
            world().spawnParticle(Particle.END_ROD, centre, 1, 0.5, 0.5, 0.5, 0.02);
        }
    }

    private void fade() {
        if (tick == REVEAL_END + 1) {
            set(prize, 0.2f, 0f, spin + 90, END - REVEAL_END);
            text.remove();
        }
        if (tick == END) world().spawnParticle(Particle.CLOUD, base.clone().add(0, 1.4, 0), 12, 0.2, 0.2, 0.2, 0.02);
    }

    private void finish() {
        if (task != null) task.cancel();
        task = null;
        if (box != null) box.remove();
        if (prize != null) prize.remove();
        if (text != null) text.remove();
        for (ItemDisplay orb : orbs) orb.remove();
        boxes.vault().free(player, spot);
    }

    // ---------------------------------------------------------------- what's said

    /** The hologram over the prize: the rarity (shimmering), the prize, new or turned into dust, who opened it. */
    private Component revealText(int frame) {
        Messages messages = plugin.messages();
        String key = reward.dust() ? "mystery.opening.dust" : duplicate ? "mystery.opening.duplicate" : "mystery.opening.new";
        List<Component> lines = new ArrayList<>();
        lines.add(HologramText.render(messages.raw("mystery.opening.rarity." + reward.rarity().key()), frame));
        lines.add(messages.render(messages.raw("mystery.opening.reward"), Placeholder.parsed("reward", reward.name(plugin))));
        lines.add(messages.component(key, Messages.arg("dust", dust)));
        lines.add(messages.component("mystery.opening.opened-by", Messages.name("player", player.getName()), boxes.starsTag(stars)));
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    /** The player is told what they got; everyone, for rare enough prizes. */
    private void tell() {
        Messages messages = plugin.messages();
        TagResolver tags = TagResolver.resolver(Placeholder.parsed("reward", reward.name(plugin)),
                Placeholder.parsed("rarity", boxes.rarityName(reward.rarity())), Messages.arg("dust", dust),
                Messages.name("player", player.getName()), boxes.starsTag(stars));
        if (player.isOnline()) {
            messages.send(player, reward.dust() ? "mystery.found-dust" : duplicate ? "mystery.found-duplicate" : "mystery.found", tags);
        }
        if (!reward.dust() && boxes.announced(reward.rarity())) {
            for (Player everyone : plugin.getServer().getOnlinePlayers()) messages.send(everyone, "mystery.announce", tags);
        }
    }

    // ---------------------------------------------------------------- pieces

    private ItemDisplay display(ItemStack item, Location at, float scale) {
        return at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setPersistent(false);
            display.addScoreboardTag(TAG);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTeleportDuration(2);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale), new AxisAngle4f()));
        });
    }

    /** Sets a display's height above where it stands, size and turn, moving there over {@code ticks}. */
    private static void set(ItemDisplay display, float lift, float scale, float degrees, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(new Transformation(new Vector3f(0, lift, 0), rotation(degrees), new Vector3f(scale), new AxisAngle4f()));
    }

    private static void move(ItemDisplay display, Vector3f offset, float scale, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(new Transformation(offset, new AxisAngle4f(), new Vector3f(scale), new AxisAngle4f()));
    }

    private static AxisAngle4f rotation(float degrees) {
        return new AxisAngle4f((float) Math.toRadians(degrees), 0, 1, 0);
    }

    private ItemStack model(String path, Material fallback) {
        ItemStack item = ItemStack.of(fallback);
        if (plugin.menuStyle().custom()) item.setData(DataComponentTypes.ITEM_MODEL, Key.key("fractal", path));
        return item;
    }

    private World world() {
        return base.getWorld();
    }

    private void sound(String key, float volume, float pitch) {
        base.getWorld().playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch), base.getX(), base.getY(), base.getZ());
    }
}
