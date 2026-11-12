package com.blossomsmp.quests.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything BlossomQuests remembers about one player. */
public class PlayerData {

    /** A quest a player has been given today / this week. */
    public static class ActiveQuest {
        public final String id;
        public int progress;
        public boolean claimed;

        public ActiveQuest(String id, int progress, boolean claimed) {
            this.id = id;
            this.progress = progress;
            this.claimed = claimed;
        }
    }

    public final UUID uuid;
    public String name;
    public String dailyKey = "";
    public final List<ActiveQuest> daily = new ArrayList<>();
    public String weeklyKey = "";
    public final List<ActiveQuest> weekly = new ArrayList<>();
    /** Lifetime counters used by achievements: mine, place, kill_mob, kill_player, fish, craft, breed, playtime, boss, quests */
    public final Map<String, Long> counters = new HashMap<>();
    /** Achievement ids already claimed */
    public final Set<String> achievements = new HashSet<>();
    public boolean dirty;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public long counter(String key) {
        return counters.getOrDefault(key, 0L);
    }

    public void addCounter(String key, long amount) {
        counters.merge(key, amount, Long::sum);
        dirty = true;
    }
}
