package com.blossomsmp.quests.listeners;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Petal Reaper abilities and the Sakura Sigil. */
public class ItemListener implements Listener {

    private final BlossomQuests plugin;
    private final Map<UUID, Long> dashCooldown = new HashMap<>();
    /** Players mid-dash don't take fall damage when they land */
    private final Set<UUID> dashing = new HashSet<>();

    public ItemListener(BlossomQuests plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (plugin.getItems().isReaper(hand)) {
            dash(player);
            return;
        }
        if (plugin.getItems().isSigil(hand)) {
            event.setCancelled(true);
            Block clicked = event.getClickedBlock();
            if (clicked == null) {
                player.sendMessage(plugin.msg("&7Right-click the &fground &7to summon the Sakura Warden."));
                return;
            }
            Location spot = clicked.getLocation().add(0.5, 1, 0.5);
            if (plugin.getBoss().trySummon(player, spot)) {
                hand.setAmount(hand.getAmount() - 1);
            }
        }
    }

    private void dash(Player player) {
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("items.reaper.dash-cooldown-seconds", 12) * 1000L;
        Long last = dashCooldown.get(player.getUniqueId());
        if (last != null && now - last < cooldown) {
            long left = (cooldown - (now - last) + 999) / 1000;
            player.sendActionBar(Text.component("&d✿ Blossom Dash &7ready in &f" + left + "s"));
            return;
        }
        dashCooldown.put(player.getUniqueId(), now);
        double power = plugin.getConfig().getDouble("items.reaper.dash-power", 1.6);
        double damage = plugin.getConfig().getDouble("items.reaper.dash-damage", 6);
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 0.01) {
            return;
        }
        direction.normalize().multiply(power).setY(0.35);
        player.setVelocity(direction);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.3f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.5f);
        dashing.add(player.getUniqueId());
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks++ > 12) {
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> dashing.remove(player.getUniqueId()), 40L);
                    cancel();
                    return;
                }
                Location at = player.getLocation().add(0, 1, 0);
                player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, at, 12, 0.4, 0.4, 0.4, 0);
                for (Entity entity : player.getNearbyEntities(1.8, 1.5, 1.8)) {
                    if (entity instanceof LivingEntity target && !entity.equals(player)
                            && hit.add(entity.getUniqueId())) {
                        if (target instanceof Player p && p.getGameMode() != GameMode.SURVIVAL
                                && p.getGameMode() != GameMode.ADVENTURE) {
                            continue;
                        }
                        target.damage(damage, player);
                        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (!plugin.getItems().isReaper(player.getInventory().getItemInMainHand())) {
            return;
        }
        int chance = plugin.getConfig().getInt("items.reaper.wither-chance", 15);
        if (ThreadLocalRandom.current().nextInt(100) < chance) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
            target.getWorld().spawnParticle(Particle.CHERRY_LEAVES, target.getLocation().add(0, 1, 0), 20,
                    0.4, 0.6, 0.4, 0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player
                && dashing.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
