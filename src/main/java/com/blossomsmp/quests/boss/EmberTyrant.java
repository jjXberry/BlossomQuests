package com.blossomsmp.quests.boss;

import com.blossomsmp.quests.BlossomQuests;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** ✹ The Ember Tyrant: a burning piglin brute in gold-trimmed netherite. No attack breaks blocks. */
public class EmberTyrant extends BossType {

    public EmberTyrant(BlossomQuests plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "ember";
    }

    @Override
    public String name() {
        return "&#FF6A00&l✹ Ember Tyrant ✹";
    }

    @Override
    public String plainName() {
        return "Ember Tyrant";
    }

    @Override
    public String color() {
        return "&#FF6A00";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.YELLOW;
    }

    @Override
    public NamedTextColor glowColor() {
        return NamedTextColor.GOLD;
    }

    @Override
    public String weaponId() {
        return "infernal_cleaver";
    }

    @Override
    public String sigilId() {
        return "ember_sigil";
    }

    @Override
    public Material icon() {
        return Material.PIGLIN_HEAD;
    }

    @Override
    public Material orbitItem() {
        return Material.BLAZE_POWDER;
    }

    @Override
    public Particle ambientParticle() {
        return Particle.FLAME;
    }

    @Override
    public List<String> attackLore() {
        return List.of(
                "&6• Meteor Strike &7- fire rains down on marked spots",
                "&6• Flame Ring &7- a ring of fire bursts out from him",
                "&6• Magma Spawn &7- calls magma cube helpers",
                "&6• Inferno Charge &7- charges at a player and explodes",
                "&c• Eruption &7- enraged only: launches everyone nearby");
    }

    @Override
    public LivingEntity spawn(Location location, Consumer<LivingEntity> tag) {
        return location.getWorld().spawn(location, PiglinBrute.class, brute -> {
            tag.accept(brute);
            brute.setImmuneToZombification(true);
            baseStats(brute, 1.7, 14, 0.3);
            dress(brute,
                    armor(Material.NETHERITE_HELMET, null, TrimMaterial.GOLD, TrimPattern.RIB),
                    armor(Material.NETHERITE_CHESTPLATE, null, TrimMaterial.GOLD, TrimPattern.SNOUT),
                    armor(Material.NETHERITE_LEGGINGS, null, TrimMaterial.REDSTONE, TrimPattern.RIB),
                    armor(Material.NETHERITE_BOOTS, null, TrimMaterial.GOLD, TrimPattern.RIB),
                    plugin.getItems().weapon("infernal_cleaver"));
            brute.setVisualFire(true);
        });
    }

    @Override
    public void attack(ActiveBoss boss, List<Player> near) {
        int pick = ThreadLocalRandom.current().nextInt(boss.enraged ? 5 : 4);
        switch (pick) {
            case 0 -> meteors(boss, near);
            case 1 -> flameRing(boss);
            case 2 -> magma(boss);
            case 3 -> charge(boss, near);
            default -> eruption(boss);
        }
    }

    private void meteors(ActiveBoss boss, List<Player> near) {
        tell(boss, "&#FF6A00Meteor Strike! &7Move off the burning circles!");
        List<Player> targets = new ArrayList<>(near);
        Collections.shuffle(targets);
        double dmg = cfg("meteor-damage", 8);
        for (int i = 0; i < Math.min(boss.enraged ? 5 : 3, targets.size()); i++) {
            Location spot = targets.get(i).getLocation().clone();
            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (!boss.alive()) {
                        cancel();
                        return;
                    }
                    if (ticks < 30) {
                        for (int a = 0; a < 16; a++) {
                            double angle = Math.PI * 2 * a / 16;
                            spot.getWorld().spawnParticle(Particle.FLAME,
                                    spot.clone().add(Math.cos(angle) * 2.5, 0.2, Math.sin(angle) * 2.5), 1, 0, 0, 0, 0);
                        }
                        spot.getWorld().spawnParticle(Particle.LAVA, spot.clone().add(0, 8 - ticks / 4.0, 0), 2, 0.2, 0.2, 0.2, 0);
                        ticks += 5;
                        return;
                    }
                    spot.getWorld().spawnParticle(Particle.EXPLOSION, spot, 2, 0.5, 0.2, 0.5, 0);
                    spot.getWorld().spawnParticle(Particle.FLAME, spot.clone().add(0, 0.5, 0), 80, 1.5, 0.5, 1.5, 0.05);
                    spot.getWorld().playSound(spot, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
                    for (Player p : playersNear(spot, 2.8)) {
                        p.damage(dmg, boss.entity);
                        p.setFireTicks(80);
                    }
                    cancel();
                }
            }.runTaskTimer(plugin, 0L, 5L);
        }
    }

    private void flameRing(ActiveBoss boss) {
        Location at = boss.entity.getLocation();
        at.getWorld().playSound(at, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.6f);
        tell(boss, "&#FF6A00Flame Ring!");
        for (int r = 1; r <= 7; r++) {
            final double radius = r;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!boss.alive()) {
                    return;
                }
                Location c = boss.entity.getLocation();
                for (int a = 0; a < 24; a++) {
                    double angle = Math.PI * 2 * a / 24;
                    c.getWorld().spawnParticle(Particle.FLAME,
                            c.clone().add(Math.cos(angle) * radius, 0.3, Math.sin(angle) * radius), 2, 0.1, 0.1, 0.1, 0.01);
                }
            }, r * 2L);
        }
        double dmg = cfg("ring-damage", 6);
        for (Player p : fighters(boss, 7)) {
            p.damage(dmg, boss.entity);
            p.setFireTicks(100);
            Vector push = p.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (push.lengthSquared() > 0.01) {
                p.setVelocity(push.normalize().multiply(1.2).setY(0.4));
            }
        }
    }

    private void magma(ActiveBoss boss) {
        if (livingMinions(boss) >= 8) {
            flameRing(boss);
            return;
        }
        Location at = boss.entity.getLocation();
        at.getWorld().playSound(at, Sound.ENTITY_MAGMA_CUBE_SQUISH, 1.5f, 0.5f);
        tell(boss, "&#FF6A00The Tyrant calls his magma spawn!");
        for (int i = 0; i < (boss.enraged ? 4 : 3); i++) {
            double angle = Math.PI * 2 * i / 3;
            Location spot = at.clone().add(Math.cos(angle) * 2, 0.5, Math.sin(angle) * 2);
            minion(boss, spot, MagmaCube.class, "&#FF6A00Magma Spawn", cube -> cube.setSize(2));
            at.getWorld().spawnParticle(Particle.LAVA, spot, 10, 0.3, 0.3, 0.3, 0);
        }
    }

    private void charge(ActiveBoss boss, List<Player> near) {
        Player target = near.get(ThreadLocalRandom.current().nextInt(near.size()));
        Location at = boss.entity.getLocation();
        Vector dash = target.getLocation().toVector().subtract(at.toVector()).setY(0);
        if (dash.lengthSquared() < 1) {
            flameRing(boss);
            return;
        }
        dash.normalize().multiply(1.6).setY(0.3);
        boss.entity.setVelocity(dash);
        at.getWorld().playSound(at, Sound.ENTITY_HOGLIN_ANGRY, 1.5f, 0.6f);
        tell(boss, "&#FF6A00The Tyrant charges at &f" + target.getName() + "&#FF6A00!");
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!boss.alive() || ticks++ > 14) {
                    if (boss.alive()) {
                        Location land = boss.entity.getLocation();
                        land.getWorld().spawnParticle(Particle.EXPLOSION, land, 2, 0.5, 0.2, 0.5, 0);
                        land.getWorld().playSound(land, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                        for (Player p : playersNear(land, 3)) {
                            p.damage(cfg("charge-damage", 8), boss.entity);
                            p.setFireTicks(60);
                        }
                    }
                    cancel();
                    return;
                }
                Location l = boss.entity.getLocation();
                l.getWorld().spawnParticle(Particle.FLAME, l.clone().add(0, 0.5, 0), 15, 0.4, 0.4, 0.4, 0.02);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void eruption(ActiveBoss boss) {
        Location at = boss.entity.getLocation();
        tell(boss, "&c&lERUPTION!");
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
        at.getWorld().spawnParticle(Particle.LAVA, at.clone().add(0, 1, 0), 120, 6, 1, 6, 0);
        at.getWorld().spawnParticle(Particle.FLAME, at.clone().add(0, 1, 0), 200, 6, 2, 6, 0.08);
        for (Player p : fighters(boss, 12)) {
            p.damage(cfg("eruption-damage", 6), boss.entity);
            p.setFireTicks(80);
            p.setVelocity(p.getVelocity().setY(1.1));
        }
    }
}
