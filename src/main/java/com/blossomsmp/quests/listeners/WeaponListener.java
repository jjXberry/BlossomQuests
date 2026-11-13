package com.blossomsmp.quests.listeners;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.boss.BossType;
import com.blossomsmp.quests.items.Weapon;
import com.blossomsmp.quests.util.Text;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Weapon abilities (right-click, shift + right-click, left-click), passives and sigils. */
public class WeaponListener implements Listener {

    private final BlossomQuests plugin;
    /** player -> (weapon:ability -> time it's ready again) */
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    /** player -> time they stop being protected from fall damage */
    private final Map<UUID, Long> noFall = new HashMap<>();

    public WeaponListener(BlossomQuests plugin) {
        this.plugin = plugin;
    }

    public void protectFromFall(Player player, int ticks) {
        noFall.put(player.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
    }

    /** Gives an ability back (e.g. Blizzard Step had nowhere to go). */
    public void refund(Player player, String weapon, String ability) {
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map != null) {
            map.remove(weapon + ":" + ability);
        }
    }

    private boolean ready(Player player, Weapon weapon, Weapon.Ability ability) {
        Map<String, Long> map = cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        String key = weapon.id() + ":" + ability.key();
        long now = System.currentTimeMillis();
        Long readyAt = map.get(key);
        if (readyAt != null && readyAt > now) {
            long left = (readyAt - now + 999) / 1000;
            player.sendActionBar(Text.component(weapon.color() + "✦ " + ability.name() + " &7ready in &f" + left + "s"));
            return false;
        }
        map.put(key, now + weapon.cooldown(ability) * 1000L);
        player.sendActionBar(Text.component(weapon.color() + "&l✦ " + ability.name() + "!"));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;

        if (rightClick && plugin.getItems().isSigil(hand)) {
            event.setCancelled(true);
            Block clicked = event.getClickedBlock();
            BossType type = plugin.getBoss().typeForSigil(plugin.getItems().idOf(hand));
            if (type == null) {
                return;
            }
            if (clicked == null) {
                player.sendMessage(plugin.msg("&7Right-click the &fground &7to summon the " + type.plainName() + "."));
                return;
            }
            Location spot = clicked.getLocation().add(0.5, 1, 0.5);
            if (plugin.getBoss().trySummon(player, type, spot)) {
                hand.setAmount(hand.getAmount() - 1);
            }
            return;
        }

        Weapon weapon = plugin.getItems().weaponOf(hand);
        if (weapon == null) {
            return;
        }
        if (rightClick) {
            if (player.isSneaking()) {
                if (ready(player, weapon, weapon.shift())) {
                    weapon.useShift(player);
                }
            } else if (ready(player, weapon, weapon.right())) {
                weapon.useRight(player);
            }
        } else if (action == Action.LEFT_CLICK_AIR) {
            if (ready(player, weapon, weapon.left())) {
                weapon.useLeft(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        Weapon weapon = plugin.getItems().weaponOf(player.getInventory().getItemInMainHand());
        if (weapon != null) {
            weapon.onHit(player, target);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        Weapon weapon = plugin.getItems().weaponOf(killer.getInventory().getItemInMainHand());
        if (weapon != null) {
            weapon.onKill(killer, event.getEntity());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Long until = noFall.get(player.getUniqueId());
            if (until != null && until > System.currentTimeMillis()) {
                event.setCancelled(true);
                return;
            }
        }
        Weapon weapon = plugin.getItems().weaponOf(player.getInventory().getItemInMainHand());
        if (weapon != null && weapon.immuneTo(event.getCause())) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK) {
                player.setFireTicks(0);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
        noFall.remove(event.getPlayer().getUniqueId());
    }
}
