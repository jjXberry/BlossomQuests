package com.blossomsmp.quests.boss;

import com.blossomsmp.quests.BlossomQuests;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Puts a BetterModel 3D model on the boss, if BetterModel is installed.
 * Uses reflection so BlossomQuests still works (as a normal-looking boss) without BetterModel,
 * and doesn't break when BetterModel updates.
 */
public class ModelHook {

    private final BlossomQuests plugin;
    private Object tracker;

    public ModelHook(BlossomQuests plugin) {
        this.plugin = plugin;
    }

    public boolean available() {
        return plugin.getConfig().getBoolean("bosses.sakura.model.enabled", true)
                && Bukkit.getPluginManager().getPlugin("BetterModel") != null
                && Bukkit.getPluginManager().getPlugin("BetterModel").isEnabled();
    }

    /** Returns true if the model was put on the entity. */
    public boolean apply(Entity entity) {
        tracker = null;
        if (!available()) {
            return false;
        }
        String name = plugin.getConfig().getString("bosses.sakura.model.name", "sakura_queen");
        try {
            Class<?> api = Class.forName("kr.toxicity.model.api.BetterModel");
            Object result = api.getMethod("model", String.class).invoke(null, name);
            Object renderer = result instanceof Optional<?> opt ? opt.orElse(null) : result;
            if (renderer == null) {
                plugin.getLogger().warning("BetterModel has no model called '" + name + "'. Put " + name
                        + ".bbmodel in plugins/BetterModel/models/ and run /bettermodel reload.");
                return false;
            }
            Object base = null;
            try {
                Class<?> baseClass = Class.forName("kr.toxicity.model.api.entity.BaseEntity");
                for (Method m : baseClass.getMethods()) {
                    if (m.getName().equals("of") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isInstance(entity)) {
                        base = m.invoke(null, entity);
                        break;
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // older/newer API: try the entity directly below
            }
            for (String methodName : new String[]{"getOrCreate", "create"}) {
                for (Method m : renderer.getClass().getMethods()) {
                    if (!m.getName().equals(methodName) || m.getParameterCount() != 1) {
                        continue;
                    }
                    Class<?> param = m.getParameterTypes()[0];
                    if (base != null && param.isInstance(base)) {
                        tracker = m.invoke(renderer, base);
                    } else if (param.isInstance(entity)) {
                        tracker = m.invoke(renderer, entity);
                    }
                    if (tracker != null) {
                        plugin.getLogger().info("Sakura Warden is using the 3D model '" + name + "'.");
                        animate("idle");
                        return true;
                    }
                }
            }
            plugin.getLogger().warning("Could not attach the 3D model (this BetterModel version works differently)."
                    + " The boss will use her normal look. Send the console text to get it fixed.");
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not attach the 3D model: " + t);
        }
        return false;
    }

    public boolean active() {
        return tracker != null;
    }

    /** Plays an animation from the .bbmodel (idle, walk, attack, cast, leap). */
    public void animate(String animation) {
        if (tracker == null) {
            return;
        }
        try {
            for (Method m : tracker.getClass().getMethods()) {
                if (m.getName().equals("animate") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == String.class) {
                    m.invoke(tracker, animation);
                    return;
                }
            }
        } catch (Throwable ignored) {
            // an animation failing should never break the fight
        }
    }

    /** Removes the model (when the boss dies or leaves). */
    public void remove() {
        if (tracker == null) {
            return;
        }
        try {
            for (String name : new String[]{"close", "despawn", "remove"}) {
                for (Method m : tracker.getClass().getMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == 0) {
                        m.invoke(tracker);
                        tracker = null;
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
            // the model goes away with the entity anyway
        }
        tracker = null;
    }
}
