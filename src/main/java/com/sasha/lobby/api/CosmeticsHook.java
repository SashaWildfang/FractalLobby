/*
 * FractalLobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.api;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * What FractalLobby asks FractalCosmetics (Collectibles and Mystery Boxes) for. FractalCosmetics hands one over with
 * {@link com.sasha.lobby.Lobby#cosmetics(CosmeticsHook)} when it starts; without it the lobby runs without cosmetics.
 */
public interface CosmeticsHook {

    /** %lobby_dust%: the player's Mystery Dust, shortened (1.2k). */
    String dust(UUID player);

    /** %lobby_boxes%: the player's unopened Mystery Boxes, shortened. */
    String boxes(UUID player);

    /** Whether the player found this reward ("trail:rainbow", "chatcolor:pink") in a Mystery Box. */
    boolean unlocked(Player player, String reward);

    /** Whether the player found any chat colour in a Mystery Box. */
    boolean anyChatColor(Player player);

    /** An older config.yml whose hotbar still has the radio in slot 5 (the radio then goes in slot 3). */
    boolean oldHotbar();

    /** Gives the Collectibles hotbar item and puts the player's worn collectibles back on. */
    void applyJoinItems(Player player);

    /** The swap-hands key (F), which the gadgets use. */
    void swapKey(Player player);

    /** Whether a collectible stops double jump for now (astronauts float instead). */
    boolean blocksDoubleJump(Player player);

    /** A new parkour best, which can give a Mystery Box. */
    void parkourBest(Player player, int score);

    /** Gives Mystery Dust (online or not). False when this FractalCosmetics can't (an older version). */
    default boolean giveDust(UUID player, long amount) {
        return false;
    }

    /** Gives Mystery Boxes (online or not). False when this FractalCosmetics can't (an older version). */
    default boolean giveBoxes(UUID player, int stars, int amount) {
        return false;
    }

    /** /lobby reload: FractalCosmetics reloads too. */
    void reload();
}
