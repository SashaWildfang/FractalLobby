/*
 * Lobby - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.lobby.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The sidebar on one player's scoreboard. Uses the modern scoreboard features (1.20.3+): every line's text
 * is a custom display name and the score numbers on the right are hidden. Only lines that changed are
 * re-sent, so frequent updates cost almost nothing and never flicker.
 */
final class Sidebar {

    private static final int MAX_LINES = 15;

    private final Scoreboard board;
    private final Objective objective;
    private final List<Component> shown = new ArrayList<>();
    private Component title = Component.empty();

    Sidebar(Scoreboard board) {
        this.board = board;
        this.objective = board.registerNewObjective("lobby", Criteria.DUMMY, title);
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.objective.numberFormat(NumberFormat.blank());
    }

    void update(Component newTitle, List<Component> lines) {
        if (!Objects.equals(title, newTitle)) {
            objective.displayName(newTitle);
            title = newTitle;
        }
        int count = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < count; i++) {
            Component line = lines.get(i);
            boolean added = i >= shown.size();
            if (!added && Objects.equals(shown.get(i), line)) continue;
            var score = objective.getScore(entry(i));
            score.setScore(count - i); // higher scores sit higher in the sidebar
            score.customName(line);
            if (added) shown.add(line);
            else shown.set(i, line);
        }
        // Fewer lines than before: drop the extras, and renumber what's left so the order stays right.
        if (shown.size() > count) {
            for (int i = count; i < shown.size(); i++) board.resetScores(entry(i));
            shown.subList(count, shown.size()).clear();
            for (int i = 0; i < count; i++) objective.getScore(entry(i)).setScore(count - i);
        }
    }

    void remove() {
        objective.unregister();
    }

    /** Internal score holder names; never displayed because every line has a custom name. */
    private static String entry(int index) {
        return "lobby-line-" + index;
    }
}
