package com.blossomsmp.quests.listeners;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.quest.QuestType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Watches what players do and moves their quests forward. */
public class ProgressListener implements Listener {

    private static final int MAX_PLACED = 200_000;

    private final BlossomQuests plugin;
    /** Blocks placed by players since the last restart, so place-and-break doesn't count as mining. */
    private final Set<String> placed = new LinkedHashSet<>();
    /** killer -> (victim -> time) so killing the same player over and over doesn't count. */
    private final Map<UUID, Map<UUID, Long>> recentKills = new HashMap<>();
    /** Last time each player moved, for the AFK check on playtime quests. */
    private final Map<UUID, Long> lastMove = new HashMap<>();

    public ProgressListener(BlossomQuests plugin) {
        this.plugin = plugin;
        // Playtime: one minute for every minute played and not AFK
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::playtimeTick, 1200L, 1200L);
    }

    private boolean counts(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    private String key(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        placed.add(key(event.getBlockPlaced()));
        if (placed.size() > MAX_PLACED) {
            Iterator<String> it = placed.iterator();
            it.next();
            it.remove();
        }
        if (counts(player)) {
            plugin.getQuests().progress(player, QuestType.PLACE, event.getBlockPlaced().getType().name(), false, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (placed.remove(key(event.getBlock())) || !counts(player)) {
            return;
        }
        plugin.getQuests().progress(player, QuestType.MINE, event.getBlock().getType().name(), false, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || plugin.getBoss().isBossOrMinion(entity)) {
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null || !counts(killer)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("quests.count-spawner-mobs", false)
                && entity.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            return;
        }
        plugin.getQuests().progress(killer, QuestType.KILL, entity.getType().name(), entity instanceof Enemy, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        long now = System.currentTimeMillis();
        long wait = plugin.getConfig().getLong("quests.same-player-kill-minutes", 10) * 60_000L;
        Map<UUID, Long> mine = recentKills.computeIfAbsent(killer.getUniqueId(), k -> new HashMap<>());
        Long last = mine.get(victim.getUniqueId());
        if (last != null && now - last < wait) {
            return;
        }
        mine.put(victim.getUniqueId(), now);
        plugin.getQuests().progress(killer, QuestType.KILL_PLAYER, "PLAYER", false, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !counts(event.getPlayer())) {
            return;
        }
        Entity caught = event.getCaught();
        String target = "FISH";
        if (caught instanceof org.bukkit.entity.Item item) {
            target = item.getItemStack().getType().name();
        }
        plugin.getQuests().progress(event.getPlayer(), QuestType.FISH, target, false, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !counts(player)) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        int amount = result.getAmount();
        if (event.isShiftClick()) {
            // Shift-click crafts as many as possible: count the smallest ingredient stack
            int times = 64;
            for (ItemStack ingredient : event.getInventory().getMatrix()) {
                if (ingredient != null && !ingredient.getType().isAir()) {
                    times = Math.min(times, ingredient.getAmount());
                }
            }
            amount = amount * Math.max(1, times);
        }
        plugin.getQuests().progress(player, QuestType.CRAFT, result.getType().name(), false, amount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player && counts(player)) {
            plugin.getQuests().progress(player, QuestType.BREED, event.getEntity().getType().name(), false, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) {
            lastMove.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    private void playtimeTick() {
        long now = System.currentTimeMillis();
        long afk = plugin.getConfig().getLong("quests.afk-minutes", 5) * 60_000L;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Long moved = lastMove.get(player.getUniqueId());
            if (moved == null || now - moved > afk) {
                continue;
            }
            plugin.getQuests().progress(player, QuestType.PLAYTIME, "PLAYTIME", false, 1);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        lastMove.put(player.getUniqueId(), System.currentTimeMillis());
        var data = plugin.getData().get(player);
        int ready = plugin.getQuests().readyToClaim(data);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (ready > 0) {
                player.sendMessage(plugin.msg("&dYou have &f" + ready + " &dreward" + (ready == 1 ? "" : "s")
                        + " ready to claim! Type &f/quest&d."));
            }
            plugin.getBoard().notifyMailbox(player);
        }, 80L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastMove.remove(uuid);
        recentKills.remove(uuid);
        plugin.getData().unload(uuid);
    }
}
