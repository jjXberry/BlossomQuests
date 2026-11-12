package com.blossomsmp.quests.quest;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * One quest from quests.yml.
 * targets: empty or "ANY" = anything. For KILL, "HOSTILE" = any hostile mob.
 */
public record QuestDef(String id, String scope, String name, List<String> description, Material icon,
                       QuestType type, List<String> targets, int amount, Reward reward) {

    public boolean matches(QuestType happened, String target, boolean hostile) {
        if (happened != type) {
            return false;
        }
        if (targets.isEmpty() || targets.contains("ANY")) {
            return true;
        }
        if (hostile && targets.contains("HOSTILE")) {
            return true;
        }
        return target != null && targets.contains(target.toUpperCase(Locale.ROOT));
    }

    /** "Mine 64 Diamond Ore" when no description is written in the config. */
    public String autoDescription() {
        String what;
        if (type == QuestType.PLAYTIME) {
            return "Play for " + amount + " minutes";
        }
        if (type == QuestType.KILL_PLAYER) {
            return "Kill " + amount + " player" + (amount == 1 ? "" : "s");
        }
        if (type == QuestType.BOSS) {
            return "Defeat the Sakura Warden " + amount + " time" + (amount == 1 ? "" : "s");
        }
        if (targets.isEmpty() || targets.contains("ANY")) {
            what = switch (type) {
                case MINE, PLACE -> "blocks";
                case KILL -> "mobs";
                case FISH -> "fish";
                case CRAFT -> "items";
                case BREED -> "animals";
                default -> "";
            };
        } else if (targets.contains("HOSTILE")) {
            what = "hostile mobs";
        } else {
            what = pretty(targets.get(0)) + (targets.size() > 1 ? " (or similar)" : "");
        }
        return type.verb() + " " + amount + " " + what;
    }

    public static String pretty(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
