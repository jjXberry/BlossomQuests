package com.blossomsmp.quests.boss;

import com.blossomsmp.quests.BlossomQuests;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** ❀ The Sakura Warden: giant pink wither skeleton with petals, minions and a leap. */
public class SakuraWarden extends BossType {

    private static final Color PINK = Color.fromRGB(0xFF69B4);

    public SakuraWarden(BlossomQuests plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "sakura";
    }

    @Override
    public String name() {
        return "&#FF1493&l❀ Sakura Warden ❀";
    }

    @Override
    public String plainName() {
        return "Sakura Warden";
    }

    @Override
    public String color() {
        return "&#FF69B4";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.PINK;
    }

    @Override
    public NamedTextColor glowColor() {
        return NamedTextColor.LIGHT_PURPLE;
    }

    @Override
    public String weaponId() {
        return "petal_reaper";
    }

    @Override
    public String sigilId() {
        return "sakura_sigil";
    }

    @Override
    public Material icon() {
        return Material.WITHER_SKELETON_SKULL;
    }

    @Override
    public Material orbitItem() {
        return Material.PINK_PETALS;
    }

    @Override
    public Particle ambientParticle() {
        return Particle.CHERRY_LEAVES;
    }

    @Override
    public List<String> attackLore() {
        return List.of(
                "&d• Petal Storm &7- hurts and slows everyone close",
                "&d• Thornlings &7- calls pink zombie helpers",
                "&d• Blossom Leap &7- jumps at the furthest player",
                "&5• Wither Bloom &7- enraged only: wither + darkness");
    }

    @Override
    public LivingEntity spawn(Location location, Consumer<LivingEntity> tag) {
        return location.getWorld().spawn(location, WitherSkeleton.class, ws -> {
            tag.accept(ws);
            baseStats(ws, 1.6, 12, 0.28);
            dress(ws,
                    armor(Material.LEATHER_HELMET, PINK, TrimMaterial.AMETHYST, TrimPattern.SILENCE),
                    armor(Material.LEATHER_CHESTPLATE, PINK, TrimMaterial.GOLD, TrimPattern.SILENCE),
                    armor(Material.LEATHER_LEGGINGS, PINK, TrimMaterial.AMETHYST, TrimPattern.SILENCE),
                    armor(Material.LEATHER_BOOTS, PINK, TrimMaterial.GOLD, TrimPattern.SILENCE),
                    plugin.getItems().weapon("petal_reaper"));
        });
    }

    @Override
    public void attack(ActiveBoss boss, List<Player> near) {
        int pick = ThreadLocalRandom.current().nextInt(boss.enraged ? 4 : 3);
        switch (pick) {
            case 0 -> petalStorm(boss);
            case 1 -> thornlings(boss);
            case 2 -> leap(boss, near);
            default -> witherBloom(boss);
        }
    }

    private void petalStorm(ActiveBoss boss) {
        boss.animate("cast");
        Location at = boss.entity.getLocation();
        at.getWorld().spawnParticle(Particle.CHERRY_LEAVES, at.clone().add(0, 1, 0), 300, 4, 1.5, 4, 0.05);
        at.getWorld().playSound(at, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.5f, 0.8f);
        tell(boss, "&#FF69B4Petal Storm! &7Get away from the Warden!");
        double dmg = cfg("storm-damage", 6);
        for (Player p : fighters(boss, 7)) {
            p.damage(dmg, boss.entity);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            p.setVelocity(p.getVelocity().setY(0.6));
        }
    }

    private void thornlings(ActiveBoss boss) {
        if (livingMinions(boss) >= 8) {
            petalStorm(boss);
            return;
        }
        boss.animate("cast");
        Location at = boss.entity.getLocation();
        at.getWorld().playSound(at, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.5f, 1.2f);
        tell(boss, "&#FF69B4The Warden calls her Thornlings!");
        int count = boss.enraged ? 4 : 3;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            Location spot = at.clone().add(Math.cos(angle) * 2, 0, Math.sin(angle) * 2);
            minion(boss, spot, Zombie.class, "&#FF69B4Thornling", z -> {
                z.setAdult();
                setAttribute(z, Attribute.MAX_HEALTH, 24);
                z.setHealth(24);
                EntityEquipment eq = z.getEquipment();
                if (eq != null) {
                    eq.setHelmet(armor(Material.LEATHER_HELMET, PINK, null, null));
                    eq.setHelmetDropChance(0f);
                    eq.setItemInMainHand(new ItemStack(Material.STONE_SWORD));
                    eq.setItemInMainHandDropChance(0f);
                }
            });
            at.getWorld().spawnParticle(Particle.CHERRY_LEAVES, spot.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0);
        }
    }

    private void leap(ActiveBoss boss, List<Player> near) {
        Location at = boss.entity.getLocation();
        Player target = near.get(0);
        double best = -1;
        for (Player p : near) {
            double d = p.getLocation().distanceSquared(at);
            if (d > best) {
                best = d;
                target = p;
            }
        }
        Vector jump = target.getLocation().toVector().subtract(at.toVector()).setY(0);
        double distance = jump.length();
        if (distance < 0.5) {
            petalStorm(boss);
            return;
        }
        jump.normalize().multiply(Math.min(1.8, 0.4 + distance * 0.12)).setY(0.9);
        boss.animate("leap");
        boss.entity.setVelocity(jump);
        at.getWorld().playSound(at, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 1.3f);
        tell(boss, "&#FF69B4The Warden leaps at &f" + target.getName() + "&#FF69B4!");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!boss.alive()) {
                return;
            }
            Location land = boss.entity.getLocation();
            land.getWorld().spawnParticle(Particle.EXPLOSION, land, 3, 1, 0.2, 1, 0);
            land.getWorld().spawnParticle(Particle.CHERRY_LEAVES, land.clone().add(0, 0.5, 0), 150, 3, 0.3, 3, 0.1);
            land.getWorld().playSound(land, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
            double dmg = cfg("slam-damage", 9);
            for (Player p : fighters(boss, 4.5)) {
                p.damage(dmg, boss.entity);
                Vector push = p.getLocation().toVector().subtract(land.toVector()).setY(0);
                if (push.lengthSquared() > 0.01) {
                    p.setVelocity(push.normalize().multiply(1.1).setY(0.5));
                }
            }
        }, 25L);
    }

    private void witherBloom(ActiveBoss boss) {
        boss.animate("cast");
        Location at = boss.entity.getLocation();
        at.getWorld().playSound(at, Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.7f);
        at.getWorld().spawnParticle(Particle.SMOKE, at.clone().add(0, 1, 0), 120, 5, 1, 5, 0.02);
        tell(boss, "&5Wither Bloom! &7The petals turn black...");
        for (Player p : fighters(boss, 10)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0));
        }
    }
}
