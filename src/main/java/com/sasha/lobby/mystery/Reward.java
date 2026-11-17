/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.mystery;

import com.sasha.lobby.Lobby;
import com.sasha.lobby.config.LobbyConfig;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Something a Mystery Box can give: a cosmetic (trail:ID, chatcolor:ID, glow) or a pile of Mystery Dust (dust:AMOUNT),
 * with its rarity. Written in config.yml as "trail:hearts rare".
 */
public record Reward(String id, String type, String value, Rarity rarity) {

    /** The rarities, rarest last. */
    public enum Rarity {
        COMMON, UNCOMMON, RARE, EPIC, LEGENDARY;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Rarity of(String text) {
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** Reads "type:value rarity"; null if it doesn't make sense. */
    static Reward parse(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length != 2) return null;
        Rarity rarity = Rarity.of(parts[1]);
        if (rarity == null) return null;
        String id = parts[0].toLowerCase(Locale.ROOT);
        int colon = id.indexOf(':');
        String type = colon < 0 ? id : id.substring(0, colon);
        String value = colon < 0 ? "" : id.substring(colon + 1);
        return switch (type) {
            case "trail", "chatcolor" -> value.isEmpty() ? null : new Reward(id, type, value, rarity);
            case "glow" -> new Reward("glow", "glow", "", rarity);
            case "dust" -> {
                try {
                    yield Integer.parseInt(value) > 0 ? new Reward(id, type, value, rarity) : null;
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    /** A hat, suit, gadget, cloak, emote or gesture (from collectibles.Catalog). */
    public boolean collectible() {
        return type.equals("collectible");
    }

    public com.sasha.lobby.collectibles.Catalog.Item item() {
        return com.sasha.lobby.collectibles.Catalog.get(value);
    }

    public boolean dust() {
        return type.equals("dust");
    }

    public int dustAmount() {
        return dust() ? Integer.parseInt(value) : 0;
    }

    /** Whether the cosmetic exists in config.yml (trails, chat colours). */
    boolean exists(Lobby plugin) {
        return switch (type) {
            case "trail" -> trail(plugin) != null;
            case "chatcolor" -> chatColor(plugin) != null;
            case "collectible" -> item() != null;
            default -> true;
        };
    }

    /** Whether everyone can already use it for free (then it's no prize). */
    boolean free(Lobby plugin) {
        return switch (type) {
            case "trail" -> trail(plugin).permission().isEmpty();
            case "chatcolor" -> chatColor(plugin).permission().isEmpty();
            default -> false;
        };
    }

    LobbyConfig.Trail trail(Lobby plugin) {
        for (LobbyConfig.Trail trail : plugin.lobbyConfig().trails().trails()) if (trail.id().equalsIgnoreCase(value)) return trail;
        return null;
    }

    LobbyConfig.ChatColor chatColor(Lobby plugin) {
        for (LobbyConfig.ChatColor colour : plugin.lobbyConfig().chatColors().colors()) if (colour.id().equalsIgnoreCase(value)) return colour;
        return null;
    }

    /** Its name as MiniMessage (the cosmetic's own name from config.yml). */
    String name(Lobby plugin) {
        return switch (type) {
            case "trail" -> trail(plugin).name() + " <gray>Trail";
            case "chatcolor" -> chatColor(plugin).name() + " <gray>Chat Colour";
            case "glow" -> plugin.messages().raw("mystery.glow-name");
            case "collectible" -> {
                String single = plugin.messages().raw("collectibles.category." + item().category().key + ".single");
                yield item().name().toLowerCase(java.util.Locale.ROOT).endsWith(single.toLowerCase(java.util.Locale.ROOT))
                        ? item().name() : item().name() + " <gray>" + single;
            }
            default -> plugin.messages().raw("mystery.dust-name").replace("<amount>", value);
        };
    }

    /** Its icon: the resource pack's picture of it (the plain item without the pack). */
    ItemStack icon(Lobby plugin) {
        var style = plugin.menuStyle();
        return switch (type) {
            case "trail" -> style.trailIcon(ItemStack.of(trail(plugin).item()), value);
            case "chatcolor" -> style.colorIcon(ItemStack.of(chatColor(plugin).item()), value);
            case "glow" -> style.icon(ItemStack.of(Material.GLOWSTONE_DUST), "glow");
            case "collectible" -> plugin.collectibles().icon(item());
            default -> model(ItemStack.of(Material.GLOWSTONE_DUST), "mystery/dust");
        };
    }

    private static ItemStack model(ItemStack item, String path) {
        item.editMeta(meta -> meta.setItemModel(new NamespacedKey("fractal", path)));
        return item;
    }
}
