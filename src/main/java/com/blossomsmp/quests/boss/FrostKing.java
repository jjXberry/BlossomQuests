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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Stray;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** ❄ The Frostbound King: a giant stray archer in quartz-trimmed diamond. Freezes, traps and shoots. */
public class FrostKing extends BossType {

    public FrostKing(BlossomQuests plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "frost";
    }

    @Override
    public String name() {
        return "&#7FDBFF&l❄ Frostbound King ❄";
    }

    @Override
    public String plainName() {
        return "Frostbound King";
    }

    @Override
    public String color() {
        return "&#7FDBFF";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.BLUE;
    }

    @Override
    public NamedTextColor glowColor() {
        return NamedTextColor.AQUA;
    }

    @Override
    public String weaponId() {
        return "frostfang";
    }

    @Override
    public String sigilId() {
        return "frost_sigil";
    }

    @Override
    public Material icon() {
        return Material.SKELETON_SKULL;
    }

    @Override
    public Material orbitItem() {
        return Material.BLUE_ICE;
    }

    @Override
    public Particle ambientParticle() {
        return Particle.SNOWFLAKE;
    }

    @Override
    public List<String> attackLore() {
        return List.of(
                "&b• Blizzard &7- freezes and slows everyone close",
                "&b• Ice Prison &7- traps a player in place",
                "&b• Frost Wisps &7- calls stray archer helpers",
                "&b• Icicle Rain &7- icicles fall on marked spots",
                "&3• Absolute Zero &7- enraged only: deep freeze for all",
                "&7He also shoots freezing arrows!");
    }

    @Override
    public LivingEntity spawn(Location location, Consumer<LivingEntity> tag) {
        return location.getWorld().spawn(location, Stray.class, stray -> {
            tag.accept(stray);
            baseStats(stray, 1.7, 10, 0.27);
            ItemStack bow = new ItemStack(Material.BOW);
            ItemMeta meta = bow.getItemMeta();
            meta.addEnchant(Enchantment.POWER, 4, true);
            meta.addEnchant(Enchantment.PUNCH, 1, true);
            bow.setItemMeta(meta);
            dress(stray,
                    armor(Material.DIAMOND_HELMET, null, TrimMaterial.QUARTZ, TrimPattern.WARD),
                    armor(Material.DIAMOND_CHESTPLATE, null, TrimMaterial.QUARTZ, TrimPattern.FLOW),
                    armor(Material.DIAMOND_LEGGINGS, null, TrimMaterial.DIAMOND, TrimPattern.WARD),
                    armor(Material.DIAMOND_BOOTS, null, TrimMaterial.QUARTZ, TrimPattern.WARD),
                    bow);
        });
    }

    @Override
    public void attack(ActiveBoss boss, List<Player> near) {
        int pick = ThreadLocalRandom.current().nextInt(boss.enraged ? 5 : 4);
        switch (pick) {
            case 0 -> blizzard(boss);
            case 1 -> prison(boss, near);
            case 2 -> wisps(boss);
            case 3 -> icicles(boss, near);
            default -> absoluteZero(boss);
        }
    }

    private void freeze(Player p, int ticks) {
        p.setFreezeTicks(Math.max(p.getFreezeTicks(), ticks));
    }

    private void blizzard(ActiveBoss boss) {
        Location at = boss.entity.getLocation();
        at.getWorld().playSound(at, Sound.ITEM_ELYTRA_FLYING, 1f, 1.6f);
        at.getWorld().spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 2, 0), 400, 7, 3, 7, 0.05);
        tell(boss, "&#7FDBFFBlizzard! &7Get out of the snow!");
        for (Player p : fighters(boss, 10)) {
            p.damage(cfg("blizzard-damage", 4), boss.entity);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
            freeze(p, 220);
        }
    }

    private void prison(ActiveBoss boss, List<Player> near) {
        Player target = near.get(0);
        double best = Double.MAX_VALUE;
        Location at = boss.entity.getLocation();
        for (Player p : near) {
            double d = p.getLocation().distanceSquared(at);
            if (d < best) {
                best = d;
                target = p;
            }
        }
        Player trapped = target;
        tell(boss, "&#7FDBFF&f" + trapped.getName() + " &#7FDBFFis trapped in an Ice Prison!");
        trapped.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 6));
        trapped.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 2));
        trapped.playSound(trapped.getLocation(), Sound.BLOCK_GLASS_PLACE, 1f, 0.6f);
        freeze(trapped, 260);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!trapped.isOnline() || ticks > 60) {
                    if (trapped.isOnline() && boss.alive()) {
                        trapped.damage(cfg("prison-damage", 5), boss.entity);
                        trapped.getWorld().playSound(trapped.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
                    }
                    cancel();
                    return;
                }
                Location c = trapped.getLocation();
                for (int y = 0; y < 3; y++) {
                    for (int a = 0; a < 12; a++) {
                        double angle = Math.PI * 2 * a / 12;
                        c.getWorld().spawnParticle(Particle.SNOWFLAKE,
                                c.clone().add(Math.cos(angle) * 0.9, y * 0.8, Math.sin(angle) * 0.9), 1, 0, 0, 0, 0);
                    }
                }
                ticks += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void wisps(ActiveBoss boss) {
        if (livingMinions(boss) >= 6) {
            blizzard(boss);
            return;
        }
        Location at = boss.entity.getLocation();
        at.getWorld().playSound(at, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.5f, 1.6f);
        tell(boss, "&#7FDBFFThe King calls his Frost Wisps!");
        for (int i = 0; i < 3; i++) {
            double angle = Math.PI * 2 * i / 3;
            Location spot = at.clone().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
            minion(boss, spot, Stray.class, "&#7FDBFFFrost Wisp", s -> {
                setAttribute(s, Attribute.MAX_HEALTH, 20);
                s.setHealth(20);
                EntityEquipment eq = s.getEquipment();
                if (eq != null) {
                    eq.setHelmet(armor(Material.LEATHER_HELMET, Color.fromRGB(0x7FDBFF), null, null));
                    eq.setHelmetDropChance(0f);
                    eq.setItemInMainHand(new ItemStack(Material.BOW));
                    eq.setItemInMainHandDropChance(0f);
                }
            });
            at.getWorld().spawnParticle(Particle.SNOWFLAKE, spot.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.02);
        }
    }

    private void icicles(ActiveBoss boss, List<Player> near) {
        tell(boss, "&#7FDBFFIcicle Rain! &7Watch the ground!");
        List<Location> spots = new ArrayList<>();
        List<Player> targets = new ArrayList<>(near);
        Collections.shuffle(targets);
        for (Player p : targets) {
            spots.add(p.getLocation().clone());
            if (spots.size() >= (boss.enraged ? 6 : 4)) {
                break;
            }
        }
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!boss.alive()) {
                    cancel();
                    return;
                }
                if (ticks < 30) {
                    for (Location spot : spots) {
                        for (int a = 0; a < 12; a++) {
                            double angle = Math.PI * 2 * a / 12;
                            spot.getWorld().spawnParticle(Particle.SNOWFLAKE,
                                    spot.clone().add(Math.cos(angle) * 2, 0.2, Math.sin(angle) * 2), 1, 0, 0, 0, 0);
                        }
                    }
                    ticks += 5;
                    return;
                }
                for (Location spot : spots) {
                    spot.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, spot.clone().add(0, 1, 0), 60, 1, 1, 1, 0.1);
                    spot.getWorld().playSound(spot, Sound.BLOCK_GLASS_BREAK, 1.2f, 1.2f);
                    for (Player p : playersNear(spot, 2.5)) {
                        p.damage(cfg("icicle-damage", 7), boss.entity);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                        freeze(p, 180);
                    }
                }
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void absoluteZero(ActiveBoss boss) {
        Location at = boss.entity.getLocation();
        tell(boss, "&3&lABSOLUTE ZERO!");
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_HURT_FREEZE, 2f, 0.5f);
        at.getWorld().spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 1, 0), 600, 10, 3, 10, 0.02);
        for (Player p : fighters(boss, 15)) {
            freeze(p, 400);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0));
        }
    }
}
