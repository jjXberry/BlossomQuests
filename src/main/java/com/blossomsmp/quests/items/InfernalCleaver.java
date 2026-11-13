package com.blossomsmp.quests.items;

import com.blossomsmp.quests.BlossomQuests;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.List;

/** ✹ Infernal Cleaver - dropped by the Ember Tyrant. Nothing it does sets blocks on fire. */
public class InfernalCleaver extends Weapon {

    public InfernalCleaver(BlossomQuests plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "infernal_cleaver";
    }

    @Override
    public String displayName() {
        return "&#FF3D00&l✹ &#FF4500&lI&#FF5500&ln&#FF6600&lf&#FF7700&le&#FF8800&lr&#FF9900&ln&#FFAA00&la&#FFBB00&ll "
                + "&#FFBB00&lC&#FFAA00&ll&#FF9900&le&#FF8800&la&#FF7700&lv&#FF6600&le&#FF5500&lr &#FF3D00&l✹";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_AXE;
    }

    @Override
    public String color() {
        return "&#FF6A00";
    }

    @Override
    public List<String> flavor() {
        return List.of("The Ember Tyrant's axe. It's still", "warm, and it always will be.");
    }

    @Override
    public Ability right() {
        return new Ability("fireball", "Hellfire Bolt", List.of("Launch a blast of fire that burns", "and hurts the first enemy it hits."), 8);
    }

    @Override
    public Ability shift() {
        return new Ability("ring", "Flame Ring", List.of("A ring of fire bursts out from you,", "burning and pushing enemies away."), 18);
    }

    @Override
    public Ability left() {
        return new Ability("slash", "Ember Slash", List.of("Swing a wave of flame that hits", "every enemy in front of you."), 7);
    }

    @Override
    public String passiveName() {
        return "Burning Blood";
    }

    @Override
    public List<String> passiveLore() {
        return List.of("You can't be hurt by fire while holding it", "(lava still hurts). Your hits set enemies on fire.");
    }

    @Override
    protected void enchant(ItemMeta meta) {
        meta.addEnchant(Enchantment.SHARPNESS, (int) value("sharpness", 5), true);
        meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
    }

    @Override
    public void useRight(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
        LivingEntity hit = beam(player, value("fireball-range", 22), Particle.FLAME);
        if (hit != null) {
            hit.damage(value("fireball-damage", 7), player);
            hit.setFireTicks(80);
            hit.getWorld().spawnParticle(Particle.EXPLOSION, hit.getLocation().add(0, 1, 0), 1);
            hit.getWorld().playSound(hit.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        }
    }

    @Override
    public void useShift(Player player) {
        Location at = player.getLocation();
        player.getWorld().playSound(at, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.6f);
        for (int r = 1; r <= 6; r++) {
            final double radius = r;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (int a = 0; a < 24; a++) {
                    double angle = Math.PI * 2 * a / 24;
                    at.getWorld().spawnParticle(Particle.FLAME,
                            at.clone().add(Math.cos(angle) * radius, 0.3, Math.sin(angle) * radius), 1, 0.05, 0.05, 0.05, 0.01);
                }
            }, r);
        }
        double dmg = value("ring-damage", 6);
        for (LivingEntity e : around(player, at, 6)) {
            e.damage(dmg, player);
            e.setFireTicks(100);
            Vector push = e.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (push.lengthSquared() > 0.01) {
                e.setVelocity(push.normalize().multiply(1.1).setY(0.4));
            }
        }
    }

    @Override
    public void useLeft(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        player.getWorld().playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        if (side.lengthSquared() < 0.01) {
            side = new Vector(1, 0, 0);
        }
        side.normalize();
        for (double d = 1; d <= 5; d += 0.5) {
            for (double s = -d * 0.6; s <= d * 0.6; s += 0.5) {
                Location spot = eye.clone().add(dir.clone().multiply(d)).add(side.clone().multiply(s)).add(0, -0.4, 0);
                player.getWorld().spawnParticle(Particle.FLAME, spot, 1, 0, 0, 0, 0.01);
            }
        }
        double dmg = value("slash-damage", 6);
        for (LivingEntity e : cone(player, 5, 50)) {
            e.damage(dmg, player);
            e.setFireTicks(60);
        }
    }

    @Override
    public void onHit(Player attacker, LivingEntity target) {
        target.setFireTicks(Math.max(target.getFireTicks(), 60));
    }

    @Override
    public boolean immuneTo(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR;
    }
}
