package com.blossomsmp.quests.quest;

import org.bukkit.Material;

/** A one-time goal like "Mine 10,000 blocks". counter = which lifetime stat to watch. */
public record AchievementDef(String id, String name, String description, Material icon,
                             String counter, long goal, Reward reward) {
}
