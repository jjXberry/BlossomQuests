package com.blossomsmp.quests.boss;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One boss that is alive right now. */
public class ActiveBoss {

    public final BossType type;
    public final LivingEntity entity;
    public final BossBar bar;
    public final Map<UUID, Double> damage = new HashMap<>();
    public final Set<UUID> minions = new HashSet<>();
    public final Set<UUID> viewers = new HashSet<>();
    /** Floating display entities that circle around the boss */
    public final List<UUID> orbit = new ArrayList<>();
    public final long spawnedAt = System.currentTimeMillis();
    public final Location chunkTicket;
    public final ModelHook model;
    public boolean enraged;
    public int seconds;
    public double orbitAngle;

    public ActiveBoss(BossType type, LivingEntity entity, BossBar bar, Location chunkTicket, ModelHook model) {
        this.type = type;
        this.entity = entity;
        this.bar = bar;
        this.chunkTicket = chunkTicket;
        this.model = model;
    }

    public boolean alive() {
        return entity.isValid() && !entity.isDead();
    }

    public void animate(String animation) {
        if (model != null) {
            model.animate(animation);
        }
    }
}
