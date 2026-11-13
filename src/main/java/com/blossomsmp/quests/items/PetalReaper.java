package com.blossomsmp.quests.items;

import com.blossomsmp.quests.BlossomQuests;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** ❀ Petal Reaper - dropped by the Sakura Warden. */
public class PetalReaper extends Weapon {

    public PetalReaper(BlossomQuests plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "petal_reaper";
    }

    @Override
    public String displayName() {
        return "&#FF1493&l✿ &#FF69B4&lP&#FF7EBF&le&#FF93CA&lt&#FFA8D5&la&#FFBDE0&ll "
                + "&#FFBDE0&lR&#FFA8D5&le&#FF93CA&la&#FF7EBF&lp&#FF69B4&le&#FF1493&lr &#FF1493&l✿";
    }

    @Override
    public Material material() {
        return Material.NETHERITE_SWORD;
    }

    @Override
    public String color() {
        return "&#FF69B4";
    }

    @Override
    public List<String> flavor() {
        return List.of("Torn from the hands of the", "&dSakura Warden&7. Its blade still", "sheds petals when it swings.");
    }

    @Override
    public Ability right() {
        return new Ability("dash", "Blossom Dash", List.of("Dash forward through a storm of", "petals, hitting everyone you pass."), 12);
    }

    @Override
    public Ability shift() {
        return new Ability("storm", "Petal Storm", List.of("Petals explode around you, hurting and", "slowing enemies. You get Regeneration."), 20);
    }

    @Override
    public Ability left() {
        return new Ability("bolt", "Petal Bolt", List.of("Shoot a beam of cursed petals that", "hits and withers the first enemy."), 6);
    }

    @Override
    public String passiveName() {
        return "Thorns of Sakura";
    }

    @Override
    public List<String> passiveLore() {
        return List.of((int) value("wither-chance", 15) + "% chance on hit to wither your target.",
                "Killing something heals you 3 hearts.");
    }

    @Override
    protected void enchant(ItemMeta meta) {
        meta.addEnchant(Enchantment.SHARPNESS, (int) value("sharpness", 5), true);
        meta.addEnchant(Enchantment.LOOTING, 3, true);
    }

    @Override
    public void useRight(Player player) {
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 0.01) {
            return;
        }
        direction.normalize().multiply(value("dash-power", 1.6)).setY(0.35);
        player.setVelocity(direction);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.3f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.5f);
        plugin.getWeaponListener().protectFromFall(player, 60);
        double damage = value("dash-damage", 6);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks++ > 12) {
                    cancel();
                    return;
                }
                Location at = player.getLocation().add(0, 1, 0);
                player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, at, 12, 0.4, 0.4, 0.4, 0);
                for (Entity entity : player.getNearbyEntities(1.8, 1.5, 1.8)) {
                    if (target(player, entity) && hit.add(entity.getUniqueId())) {
                        ((LivingEntity) entity).damage(damage, player);
                        entity.getWorld().spawnParticle(Particle.SWEEP_ATTACK, entity.getLocation().add(0, 1, 0), 1);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void useShift(Player player) {
        Location at = player.getLocation();
        player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, at.clone().add(0, 1, 0), 250, 3, 1.2, 3, 0.05);
        player.getWorld().playSound(at, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 1.2f);
        double dmg = value("storm-damage", 5);
        for (LivingEntity e : around(player, at, 5)) {
            e.damage(dmg, player);
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            Vector push = e.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (push.lengthSquared() > 0.01) {
                e.setVelocity(push.normalize().multiply(0.8).setY(0.4));
            }
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 1));
    }

    @Override
    public void useLeft(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ALLAY_ITEM_THROWN, 1f, 0.8f);
        LivingEntity hit = beam(player, value("bolt-range", 20), Particle.CHERRY_LEAVES);
        if (hit != null) {
            hit.damage(value("bolt-damage", 7), player);
            hit.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 50, 1));
            hit.getWorld().spawnParticle(Particle.CHERRY_LEAVES, hit.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.02);
        }
    }

    @Override
    public void onHit(Player attacker, LivingEntity target) {
        if (ThreadLocalRandom.current().nextInt(100) < value("wither-chance", 15)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
            target.getWorld().spawnParticle(Particle.CHERRY_LEAVES, target.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0);
        }
    }

    @Override
    public void onKill(Player killer, LivingEntity victim) {
        AttributeInstance max = killer.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        killer.setHealth(Math.min(cap, killer.getHealth() + 6));
        killer.getWorld().spawnParticle(Particle.HEART, killer.getLocation().add(0, 2, 0), 3, 0.3, 0.2, 0.3, 0);
    }
}
