package com.blossomsmp.quests.items;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.util.Text;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * A legendary boss weapon with 3 abilities and a passive:
 * RIGHT-CLICK, SHIFT + RIGHT-CLICK and LEFT-CLICK (swing at the air).
 */
public abstract class Weapon {

    /** key = config name, cooldown in seconds */
    public record Ability(String key, String name, List<String> description, int defaultCooldown) {
    }

    protected final BlossomQuests plugin;

    protected Weapon(BlossomQuests plugin) {
        this.plugin = plugin;
    }

    public abstract String id();

    /** Coloured name */
    public abstract String displayName();

    public abstract Material material();

    /** Main colour like "&#FF69B4" */
    public abstract String color();

    public abstract List<String> flavor();

    public abstract Ability right();

    public abstract Ability shift();

    public abstract Ability left();

    public abstract String passiveName();

    public abstract List<String> passiveLore();

    public abstract void useRight(Player player);

    public abstract void useShift(Player player);

    public abstract void useLeft(Player player);

    /** Called when the holder hits something with a normal attack. */
    public void onHit(Player attacker, LivingEntity target) {
    }

    /** Called when the holder kills something. */
    public void onKill(Player killer, LivingEntity victim) {
    }

    /** Damage the holder is immune to while holding the weapon. */
    public boolean immuneTo(EntityDamageEvent.DamageCause cause) {
        return false;
    }

    /** Enchantments on the item. */
    protected abstract void enchant(ItemMeta meta);

    // ------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------

    public int cooldown(Ability ability) {
        return Math.max(0, plugin.getConfig().getInt("items." + id() + "." + ability.key() + "-cooldown",
                ability.defaultCooldown()));
    }

    protected double value(String path, double def) {
        return plugin.getConfig().getDouble("items." + id() + "." + path, def);
    }

    // ------------------------------------------------------------------
    // The item
    // ------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    public ItemStack build(NamespacedKey key) {
        ItemStack item = new ItemStack(material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(displayName()));
        List<String> lines = new ArrayList<>();
        lines.add("&8Legendary Weapon");
        lines.add("");
        for (String line : flavor()) {
            lines.add("&7" + line);
        }
        addAbility(lines, "RIGHT-CLICK", right());
        addAbility(lines, "SHIFT + RIGHT-CLICK", shift());
        addAbility(lines, "LEFT-CLICK (air)", left());
        lines.add("");
        lines.add("&d&lPASSIVE: &f" + passiveName());
        for (String line : passiveLore()) {
            lines.add("&7" + line);
        }
        lines.add("");
        lines.add(color() + "&l✦ LEGENDARY ✦");
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(Text.item(line));
        }
        meta.lore(lore);
        enchant(meta);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id());
        int cmd = plugin.getConfig().getInt("items." + id() + ".custom-model-data", 0);
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void addAbility(List<String> lines, String button, Ability ability) {
        lines.add("");
        lines.add("&e&l" + button + ": &f" + ability.name());
        for (String line : ability.description()) {
            lines.add("&7" + line);
        }
        lines.add("&8Cooldown: " + cooldown(ability) + "s");
    }

    // ------------------------------------------------------------------
    // Helpers for abilities
    // ------------------------------------------------------------------

    /** Something the abilities are allowed to hit. */
    protected boolean target(Player user, Entity e) {
        if (!(e instanceof LivingEntity living) || e.equals(user) || e instanceof ArmorStand || living.isDead()) {
            return false;
        }
        if (e instanceof Player p) {
            return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
        }
        return true;
    }

    protected List<LivingEntity> around(Player user, Location center, double radius) {
        List<LivingEntity> list = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (target(user, e) && e.getLocation().distanceSquared(center) <= radius * radius) {
                list.add((LivingEntity) e);
            }
        }
        return list;
    }

    /** Enemies in front of the player within range, inside a cone (degrees each side). */
    protected List<LivingEntity> cone(Player user, double range, double degrees) {
        List<LivingEntity> list = new ArrayList<>();
        Location eye = user.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        for (LivingEntity e : around(user, user.getLocation(), range)) {
            Vector to = e.getLocation().add(0, e.getHeight() / 2, 0).toVector().subtract(eye.toVector());
            if (to.lengthSquared() < 0.01 || Math.toDegrees(look.angle(to)) <= degrees) {
                list.add(e);
            }
        }
        return list;
    }

    /** Fires a beam. Draws particles and returns the first enemy hit (or null). */
    protected LivingEntity beam(Player user, double range, Particle particle) {
        Location eye = user.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = user.getWorld();
        RayTraceResult hit = world.rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.6,
                e -> target(user, e));
        double length = range;
        LivingEntity result = null;
        if (hit != null) {
            length = hit.getHitPosition().distance(eye.toVector());
            if (hit.getHitEntity() instanceof LivingEntity living) {
                result = living;
            }
        }
        for (double d = 0.5; d < length; d += 0.4) {
            world.spawnParticle(particle, eye.clone().add(dir.clone().multiply(d)), 1, 0.02, 0.02, 0.02, 0);
        }
        return result;
    }
}
