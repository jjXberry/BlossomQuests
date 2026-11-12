package com.blossomsmp.quests.quest;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/** Money, Petals, Blossom Points, items and commands given for a quest, achievement or boss kill. */
public record Reward(double money, long petals, long points, List<String> items, List<String> commands) {

    public static final Reward NONE = new Reward(0, 0, 0, List.of(), List.of());

    /** Reads a "rewards:" section. Items look like "sigil:1", "reaper:1" or "DIAMOND:5". */
    public static Reward from(ConfigurationSection section) {
        if (section == null) {
            return NONE;
        }
        return new Reward(
                Math.max(0, section.getDouble("money", 0)),
                Math.max(0, section.getLong("petals", 0)),
                Math.max(0, section.getLong("points", 0)),
                new ArrayList<>(section.getStringList("items")),
                new ArrayList<>(section.getStringList("commands")));
    }

    public boolean isEmpty() {
        return money <= 0 && petals <= 0 && points <= 0 && items.isEmpty() && commands.isEmpty();
    }
}
