package com.blossomsmp.quests.items;

import com.blossomsmp.quests.BlossomQuests;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** ❄ Frostfang - dropped by the Frostbound King. */
public class Frostfang extends Weapon {

    public Frostfang(BlossomQuests plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "frostfang";
    }

    @Override
    public String displayName() {
        return "&#B3F0FF&l❄ &#9AE6FF&lF&#88DDFF&lr&#77D4FF&lo&#66CCFF&ls&#55C3FF&lt&#44BBFF&lf&#55C3FF&la&#66CCFF&ln&#77D4FF&lg &#B3F0FF&l❄";
    }

    @Override
    public Material material() {
        return Material.DIAMOND_SWORD;
    }

    @Override
    public String color() {
        return "&#7FDBFF";
    }

    @Override
    public List<String> flavor() {
        return List.of("Carved from the Frostbound King's", "crown. Frost creeps up your arm.");
    }

    @Override
    public Ability right() {
        return new Ability("shard", "Ice Shard", List.of("Fire a shard of ice that hurts, slows", "and freezes the first enemy hit."), 7);
    }

    @Override
    public Ability shift() {
        return new Ability("nova", "Frost Nova", List.of("Freeze everything around you", "and slow it to a crawl."), 18);
    }

    @Override
    public Ability left() {
        return new Ability("blink", "Blizzard Step", List.of("Teleport up to 8 blocks forward", "in a burst of snow."), 10);
    }

    @Override
    public String passiveName() {
        return "Permafrost";
    }

    @Override
    public List<String> passiveLore() {
        return List.of("Your hits chill enemies (Slowness).",
                (int) value("freeze-chance", 20) + "% chance to deep-freeze them.",
                "You can't freeze while holding it.");
    }

    @Override
    protected void enchant(ItemMeta meta) {
        meta.addEnchant(Enchantment.SHARPNESS, (int) value("sharpness", 5), true);
        meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
    }

    private void freeze(LivingEntity e, int ticks) {
        e.setFreezeTicks(Math.max(e.getFreezeTicks(), ticks));
    }

    @Override
    public void useRight(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.6f);
        LivingEntity hit = beam(player, value("shard-range", 22), Particle.SNOWFLAKE);
        if (hit != null) {
            hit.damage(value("shard-damage", 6), player);
            hit.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            freeze(hit, 200);
            hit.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, hit.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
        }
    }

    @Override
    public void useShift(Player player) {
        Location at = player.getLocation();
        player.getWorld().playSound(at, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.5f, 0.6f);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 1, 0), 300, 4, 1, 4, 0.05);
        double dmg = value("nova-damage", 4);
        for (LivingEntity e : around(player, at, 6)) {
            e.damage(dmg, player);
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
            freeze(e, 300);
        }
    }

    @Override
    public void useLeft(Player player) {
        Location start = player.getLocation();
        Vector dir = player.getEyeLocation().getDirection().normalize();
        double max = value("blink-distance", 8);
        RayTraceResult wall = player.getWorld().rayTraceBlocks(player.getEyeLocation(), dir, max,
                FluidCollisionMode.NEVER, true);
        double distance = wall == null ? max : Math.max(0, wall.getHitPosition().distance(player.getEyeLocation().toVector()) - 1);
        Location dest = null;
        for (double d = distance; d >= 1; d -= 0.5) {
            Location spot = start.clone().add(dir.clone().multiply(d));
            Block feet = spot.getBlock();
            Block head = feet.getRelative(0, 1, 0);
            if (feet.isPassable() && head.isPassable()) {
                dest = spot;
                break;
            }
        }
        if (dest == null) {
            player.sendActionBar(com.blossomsmp.quests.util.Text.component("&b❄ No room to teleport there."));
            plugin.getWeaponListener().refund(player, id(), left().key());
            return;
        }
        dest.setYaw(start.getYaw());
        dest.setPitch(start.getPitch());
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, start.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.05);
        player.teleport(dest);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, dest.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.05);
        player.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.6f);
        plugin.getWeaponListener().protectFromFall(player, 60);
    }

    @Override
    public void onHit(Player attacker, LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
        if (ThreadLocalRandom.current().nextInt(100) < value("freeze-chance", 20)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
            freeze(target, 220);
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
        }
    }

    @Override
    public boolean immuneTo(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FREEZE;
    }
}
