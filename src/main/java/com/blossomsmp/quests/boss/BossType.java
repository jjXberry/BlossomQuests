package com.blossomsmp.quests.boss;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A kind of boss. Each boss (Sakura Warden, Ember Tyrant, Frostbound King) is one of these. */
public abstract class BossType {

    protected final BlossomQuests plugin;

    protected BossType(BlossomQuests plugin) {
        this.plugin = plugin;
    }

    /** "sakura", "ember", "frost" */
    public abstract String id();

    /** Coloured name for the boss bar and name tag */
    public abstract String name();

    /** "Sakura Warden" */
    public abstract String plainName();

    /** Main colour, like "&#FF1493" */
    public abstract String color();

    public abstract BossBar.Color barColor();

    public abstract NamedTextColor glowColor();

    /** Custom item id of the legendary weapon she/he drops */
    public abstract String weaponId();

    /** Custom item id of the summoning sigil */
    public abstract String sigilId();

    public abstract Material icon();

    /** Lines for the menu: one per attack */
    public abstract List<String> attackLore();

    /** Items that circle around the boss */
    public abstract Material orbitItem();

    public abstract Particle ambientParticle();

    /** Spawns and dresses the entity. */
    public abstract LivingEntity spawn(Location location, Consumer<LivingEntity> tag);

    /** One random attack. */
    public abstract void attack(ActiveBoss boss, List<Player> near);

    /** Extra things when dropping below half health (speed is handled for every boss). */
    public void onEnrage(ActiveBoss boss) {
    }

    // ------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------

    public double cfg(String path, double def) {
        return plugin.getConfig().getDouble("bosses." + id() + "." + path, def);
    }

    public int cfgInt(String path, int def) {
        return plugin.getConfig().getInt("bosses." + id() + "." + path, def);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("bosses." + id() + ".enabled", true);
    }

    public double maxHealth() {
        return Math.max(20, Math.min(1024, cfg("health", 750)));
    }

    // ------------------------------------------------------------------
    // Helpers for the bosses
    // ------------------------------------------------------------------

    protected void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** Standard boss stats from config. */
    protected void baseStats(LivingEntity e, double defaultSize, double defaultDamage, double speed) {
        double health = maxHealth();
        e.customName(Text.component(name()));
        e.setCustomNameVisible(true);
        e.setPersistent(true);
        e.setRemoveWhenFarAway(false);
        e.setCanPickupItems(false);
        e.setGlowing(true);
        setAttribute(e, Attribute.MAX_HEALTH, health);
        setAttribute(e, Attribute.SCALE, cfg("size", defaultSize));
        setAttribute(e, Attribute.ATTACK_DAMAGE, cfg("damage", defaultDamage));
        setAttribute(e, Attribute.MOVEMENT_SPEED, speed);
        setAttribute(e, Attribute.KNOCKBACK_RESISTANCE, 0.8);
        setAttribute(e, Attribute.FOLLOW_RANGE, 40);
        e.setHealth(health);
    }

    /** Armour with an optional dye colour and a shiny trim. */
    protected ItemStack armor(Material material, Color dye, TrimMaterial trimMaterial, TrimPattern pattern) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather && dye != null) {
            leather.setColor(dye);
        }
        if (meta instanceof ArmorMeta armorMeta && trimMaterial != null && pattern != null) {
            armorMeta.setTrim(new ArmorTrim(trimMaterial, pattern));
        }
        item.setItemMeta(meta);
        return item;
    }

    protected void dress(LivingEntity e, ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots,
                         ItemStack hand) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return;
        }
        eq.setHelmet(helmet);
        eq.setChestplate(chest);
        eq.setLeggings(legs);
        eq.setBoots(boots);
        eq.setItemInMainHand(hand);
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
    }

    /** Survival/adventure players near the boss. */
    protected List<Player> fighters(ActiveBoss boss, double radius) {
        List<Player> list = new ArrayList<>();
        Location at = boss.entity.getLocation();
        for (Player p : at.getWorld().getPlayers()) {
            if ((p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                    && p.getLocation().distanceSquared(at) <= radius * radius) {
                list.add(p);
            }
        }
        return list;
    }

    protected List<Player> playersNear(Location at, double radius) {
        List<Player> list = new ArrayList<>();
        for (Player p : at.getWorld().getPlayers()) {
            if ((p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                    && p.getLocation().distanceSquared(at) <= radius * radius) {
                list.add(p);
            }
        }
        return list;
    }

    protected void tell(ActiveBoss boss, String message) {
        for (Player p : fighters(boss, 35)) {
            p.sendMessage(Text.component(color() + "❀ &f" + message));
        }
    }

    /** Spawns a helper mob that belongs to this boss. */
    protected <T extends LivingEntity> T minion(ActiveBoss boss, Location at, Class<T> type, String name,
                                                Consumer<T> setup) {
        T mob = at.getWorld().spawn(at, type, m -> {
            m.customName(Text.component(name));
            m.setPersistent(false);
            m.setCanPickupItems(false);
            m.getPersistentDataContainer().set(plugin.getBoss().minionKey(), PersistentDataType.BYTE, (byte) 1);
            setup.accept(m);
        });
        if (mob.isValid()) {
            boss.minions.add(mob.getUniqueId());
            if (mob instanceof Mob ai && !boss.entity.isDead()) {
                List<Player> near = fighters(boss, 20);
                if (!near.isEmpty()) {
                    ai.setTarget(near.get(0));
                }
            }
        }
        return mob;
    }

    /** Removes dead minions from the list and returns how many are left. */
    protected int livingMinions(ActiveBoss boss) {
        boss.minions.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || e.isDead();
        });
        return boss.minions.size();
    }
}
