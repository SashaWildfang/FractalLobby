/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.collectibles;

import com.sasha.lobby.mystery.Reward.Rarity;
import org.bukkit.Color;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.sasha.lobby.mystery.Reward.Rarity.COMMON;
import static com.sasha.lobby.mystery.Reward.Rarity.EPIC;
import static com.sasha.lobby.mystery.Reward.Rarity.LEGENDARY;
import static com.sasha.lobby.mystery.Reward.Rarity.RARE;

/**
 * Every collectible: hats, suits, gadgets, cloaks, emotes and gestures. Each has an id ("hat:top_hat"), a rarity
 * (which decides its Mystery Box odds and its price in Mystery Dust) and a season: Normal ones are always around;
 * Summer, Holiday, Easter and Halloween ones only drop from boxes and are sold during their season (owned ones can be
 * worn any time).
 */
public final class Catalog {

    public enum Category {
        HAT("hats"), SUIT("suits"), GADGET("gadgets"), CLOAK("cloaks"), EMOTE("emotes"), GESTURE("gestures");

        public final String key;

        Category(String key) {
            this.key = key;
        }

        /** Hats and suits are split into seasons in the menu. */
        public boolean seasonal() {
            return this == HAT || this == SUIT;
        }

        /** Emotes and gestures are done, not worn. */
        public boolean action() {
            return this == EMOTE || this == GESTURE;
        }

        public String prefix() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Season {
        NORMAL(null, null), SUMMER(MonthDay.of(6, 1), MonthDay.of(8, 31)), HOLIDAY(MonthDay.of(12, 1), MonthDay.of(1, 6)),
        EASTER(MonthDay.of(3, 15), MonthDay.of(4, 30)), HALLOWEEN(MonthDay.of(10, 1), MonthDay.of(11, 7));

        private final MonthDay from, to;

        Season(MonthDay from, MonthDay to) {
            this.from = from;
            this.to = to;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Whether it's this season on that day (Normal always is). */
        public boolean on(LocalDate day) {
            if (from == null) return true;
            MonthDay today = MonthDay.from(day);
            return from.isBefore(to) ? !today.isBefore(from) && !today.isAfter(to)
                    : !today.isBefore(from) || !today.isAfter(to); // across new year
        }
    }

    /** A suit: the colour of each piece (helmet, chestplate, leggings, boots) and its full-set ability. */
    public record Suit(Color helmet, Color chest, Color legs, Color boots, String ability) {
    }

    /**
     * One collectible. key is the part after the colon. emoji: the status emoji an emote shows; suit: a suit's pieces.
     */
    public record Item(String id, Category category, String key, Season season, Rarity rarity, String name, String description,
                       String emoji, Suit suit) {
    }

    public static final List<Item> ITEMS;
    private static final Map<String, Item> BY_ID = new LinkedHashMap<>();

    private static final List<Item> build = new ArrayList<>();

    private static void hat(String key, Season season, Rarity rarity, String name, String description) {
        add(Category.HAT, key, season, rarity, name, description, null, null);
    }

    private static void suit(String key, Season season, Rarity rarity, String name, String description, int helmet, int chest, int legs,
                             int boots, String ability) {
        add(Category.SUIT, key, season, rarity, name, description, null,
                new Suit(Color.fromRGB(helmet), Color.fromRGB(chest), Color.fromRGB(legs), Color.fromRGB(boots), ability));
    }

    private static void add(Category category, String key, Season season, Rarity rarity, String name, String description, String emoji,
                            Suit suit) {
        Item item = new Item(category.prefix() + ":" + key, category, key, season, rarity, name, description, emoji, suit);
        build.add(item);
    }

    static {
        Season N = Season.NORMAL, S = Season.SUMMER, H = Season.HOLIDAY, E = Season.EASTER, W = Season.HALLOWEEN;
        // ---- hats
        hat("top_hat", N, COMMON, "Top Hat", "Very fancy. Monocle not included.");
        hat("beanie", N, COMMON, "Cosy Beanie", "Warm ears, cool vibes.");
        hat("chef_hat", N, COMMON, "Chef's Hat", "Cooking up something good.");
        hat("headphones", N, RARE, "Headphones", "Tuned in to the Radio.");
        hat("cowboy_hat", N, RARE, "Cowboy Hat", "Yeehaw!");
        hat("wizard_hat", N, EPIC, "Wizard Hat", "Pointy, starry and slightly magic.");
        hat("viking_helmet", N, EPIC, "Viking Helmet", "Horns for the brave.");
        hat("propeller_cap", N, EPIC, "Propeller Cap", "Doesn't actually fly. Probably.");
        hat("crown", N, LEGENDARY, "Royal Crown", "For the ruler of the lobby.");
        hat("sun_hat", S, COMMON, "Sun Hat", "Shade for sunny days.");
        hat("flower_crown", S, RARE, "Flower Crown", "Fresh from the meadow.");
        hat("snorkel", S, RARE, "Snorkel Mask", "Ready for a dive.");
        hat("pineapple", S, EPIC, "Pineapple Head", "The sweetest summer look.");
        hat("santa_hat", H, COMMON, "Santa Hat", "Ho ho ho!");
        hat("elf_hat", H, RARE, "Elf Hat", "One of Santa's helpers.");
        hat("reindeer_antlers", H, RARE, "Reindeer Antlers", "Red nose sold separately.");
        hat("snowman_head", H, EPIC, "Snowman Head", "Cool as ice.");
        hat("bunny_ears", E, COMMON, "Bunny Ears", "Hop to it.");
        hat("flower_bonnet", E, RARE, "Spring Bonnet", "Pretty in pastel.");
        hat("chick_hat", E, RARE, "Chick Hat", "Cheep cheep!");
        hat("egg_shell", E, EPIC, "Cracked Egg", "Just hatched.");
        hat("witch_hat", W, COMMON, "Witch Hat", "Double, double, toil and trouble.");
        hat("devil_horns", W, RARE, "Devil Horns", "A little bit evil.");
        hat("cat_ears", W, RARE, "Black Cat Ears", "Bad luck? Never heard of it.");
        hat("pumpkin_head", W, EPIC, "Pumpkin Head", "A jack-o'-lantern with a grin.");
        // ---- suits (helmet, chest, legs, boots colours)
        suit("plumber", N, EPIC, "Plumber Suit", "It's-a you!", 0xE03A3A, 0xE03A3A, 0x2F5BD8, 0x6B3E1E, "fireball");
        suit("ninja", N, RARE, "Ninja Suit", "Silent and swift.", 0x1C1C24, 0x1C1C24, 0x1C1C24, 0x2A2A36, "smoke_bomb");
        suit("astronaut", N, EPIC, "Astronaut Suit", "One small step for a player.", 0xF2F2F2, 0xE6E6EA, 0xD0D0D8, 0x9A9AA6, "low_gravity");
        suit("frog", N, RARE, "Frog Suit", "Ribbit!", 0x5BC24A, 0x4AA83C, 0x4AA83C, 0x3A8A2E, "super_leap");
        suit("lifeguard", S, RARE, "Lifeguard Suit", "On duty at the beach.", 0xE03A3A, 0xE03A3A, 0xFFD23F, 0xFFD23F, "water_splash");
        suit("tourist", S, EPIC, "Tourist Suit", "Hawaiian shirt and all.", 0xFF9F3F, 0x3FD7E0, 0xF2E6C8, 0xC8A87A, "beach_ball");
        suit("santa", H, EPIC, "Santa Suit", "The big man himself.", 0xD62828, 0xD62828, 0xD62828, 0x1C1C24, "gift_toss");
        suit("elf", H, RARE, "Elf Suit", "Busy in the workshop.", 0x2E9E4A, 0x2E9E4A, 0xD62828, 0xD62828, "jingle_dash");
        suit("bunny", E, RARE, "Bunny Suit", "Fluffy from ears to toes.", 0xF5F0F0, 0xF5F0F0, 0xF2C4D8, 0xF2C4D8, "bunny_hop");
        suit("chick", E, EPIC, "Chick Suit", "Fresh out of the egg.", 0xFFE14D, 0xFFE14D, 0xFFE14D, 0xFF9F3F, "egg_toss");
        suit("skeleton", W, RARE, "Skeleton Suit", "Spooky, scary.", 0xE8E8E0, 0x2A2A2A, 0xE8E8E0, 0x2A2A2A, "bone_rattle");
        suit("vampire", W, LEGENDARY, "Vampire Suit", "Creature of the night.", 0x1C1C24, 0x8B0000, 0x1C1C24, 0x1C1C24, "bat_swarm");
        // ---- gadgets
        add(Category.GADGET, "party_popper", N, COMMON, "Party Popper", "Pop! Confetti everywhere.", null, null);
        add(Category.GADGET, "paintball_gun", N, COMMON, "Paintball Gun", "Splat colour on the lobby (for a moment).", null, null);
        add(Category.GADGET, "bubble_wand", N, RARE, "Bubble Wand", "Blow a stream of bubbles.", null, null);
        add(Category.GADGET, "firework_launcher", N, RARE, "Firework Launcher", "Light up the sky.", null, null);
        add(Category.GADGET, "cookie_fountain", N, RARE, "Cookie Fountain", "Cookies for everyone! (Look, don't touch.)", null, null);
        add(Category.GADGET, "bat_blaster", N, EPIC, "Bat Blaster", "Send a flock of bats flying.", null, null);
        add(Category.GADGET, "fake_tnt", N, EPIC, "Fake TNT", "It's fine. Probably.", null, null);
        add(Category.GADGET, "disco_ball", N, LEGENDARY, "Disco Ball", "Start a party wherever you are.", null, null);
        add(Category.GADGET, "snow_cannon", H, RARE, "Snow Cannon", "A volley of snowballs.", null, null);
        add(Category.GADGET, "pumpkin_launcher", W, RARE, "Pumpkin Launcher", "Lob a jack-o'-lantern.", null, null);
        // ---- cloaks (particles around you)
        add(Category.CLOAK, "sparkle_aura", N, COMMON, "Sparkle Aura", "A soft shimmer around you.", null, null);
        add(Category.CLOAK, "halo", N, RARE, "Halo", "A golden ring over your head.", null, null);
        add(Category.CLOAK, "rain_cloud", N, RARE, "Rain Cloud", "Your own little raincloud.", null, null);
        add(Category.CLOAK, "orbit", N, EPIC, "Orbit", "Two rings circling you.", null, null);
        add(Category.CLOAK, "flame_ring", N, EPIC, "Flame Ring", "Fire at your feet.", null, null);
        add(Category.CLOAK, "angel_wings", N, LEGENDARY, "Angel Wings", "Wings of light, gently flapping.", null, null);
        add(Category.CLOAK, "sun_rays", S, RARE, "Sun Rays", "Sunshine wherever you go.", null, null);
        add(Category.CLOAK, "snow_globe", H, EPIC, "Snow Globe", "Snow falling all around you.", null, null);
        add(Category.CLOAK, "blossom_aura", E, RARE, "Blossom Aura", "Petals drifting by.", null, null);
        add(Category.CLOAK, "spooky_aura", W, EPIC, "Spooky Aura", "Souls swirling around you.", null, null);
        // ---- emotes (a big emoji over your head)
        add(Category.EMOTE, "love", N, COMMON, "Love", "Heart eyes.", "love", null);
        add(Category.EMOTE, "joy", N, COMMON, "Laugh", "Tears of joy.", "joy", null);
        add(Category.EMOTE, "thumbsup", N, COMMON, "Thumbs Up", "Nice one!", "thumbsup", null);
        add(Category.EMOTE, "cool", N, COMMON, "Cool", "Too cool.", "cool", null);
        add(Category.EMOTE, "crying", N, COMMON, "Cry", "Sad times.", "crying", null);
        add(Category.EMOTE, "angry", N, RARE, "Rage", "Grr!", "angry", null);
        add(Category.EMOTE, "thinking", N, RARE, "Thinking", "Hmm...", "thinking", null);
        add(Category.EMOTE, "kiss", N, RARE, "Kiss", "Mwah!", "kiss", null);
        add(Category.EMOTE, "mindblown", N, EPIC, "Mind Blown", "Boom.", "mindblown", null);
        add(Category.EMOTE, "money", N, EPIC, "Money Eyes", "Ka-ching!", "money", null);
        add(Category.EMOTE, "skull", N, EPIC, "Dead", "I'm dead.", "skull", null);
        add(Category.EMOTE, "clown", N, LEGENDARY, "Clown", "Honk honk.", "clown", null);
        // ---- gestures
        add(Category.GESTURE, "wave", N, COMMON, "Wave", "Say hi!", null, null);
        add(Category.GESTURE, "clap", N, COMMON, "Clap", "Give a round of applause.", null, null);
        add(Category.GESTURE, "bow", N, RARE, "Bow", "A gracious bow.", null, null);
        add(Category.GESTURE, "spin", N, RARE, "Spin", "Twirl around.", null, null);
        add(Category.GESTURE, "facepalm", N, RARE, "Facepalm", "Really?", null, null);
        add(Category.GESTURE, "headbang", N, EPIC, "Headbang", "Rock out.", null, null);
        add(Category.GESTURE, "celebrate", N, EPIC, "Celebrate", "Victory!", null, null);

        ITEMS = List.copyOf(build);
        for (Item item : ITEMS) BY_ID.put(item.id(), item);
    }

    private Catalog() {
    }

    public static Item get(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static List<Item> of(Category category) {
        return ITEMS.stream().filter(item -> item.category() == category).toList();
    }

    public static List<Item> of(Category category, Season season) {
        return ITEMS.stream().filter(item -> item.category() == category && item.season() == season).toList();
    }
}
