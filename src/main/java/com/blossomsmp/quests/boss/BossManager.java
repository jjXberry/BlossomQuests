package com.blossomsmp.quests.boss;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.quest.QuestType;
import com.blossomsmp.quests.quest.Reward;
import com.blossomsmp.quests.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Sakura Warden: a giant pink wither skeleton with a boss bar, 4 attacks, a rage phase at half health,
 * and rewards for everyone who helped (the top damager can get the Petal Reaper).
 */
public class BossManager implements Listener {

    private static final String NAME = "&#FF1493&l❀ Sakura Warden ❀";

    private final BlossomQuests plugin;
    private final NamespacedKey bossKey;
    private final NamespacedKey minionKey;

    private LivingEntity boss;
    private BossBar bar;
    private BukkitTask task;
    private final Map<UUID, Double> damage = new HashMap<>();
    private final Set<UUID> minions = new HashSet<>();
    private final Set<UUID> viewers = new HashSet<>();
    private boolean enraged;
    private long spawnedAt;
    private int seconds;
    private Location chunkTicket;

    /** Automatic spawns */
    private long nextAutoSpawn;
    private boolean warned;

    public BossManager(BlossomQuests plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "boss");
        this.minionKey = new NamespacedKey(plugin, "minion");
        scheduleNextAuto();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::autoTick, 20L * 30, 20L * 30);
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    public boolean isAlive() {
        return boss != null && boss.isValid() && !boss.isDead();
    }

    public LivingEntity boss() {
        return isAlive() ? boss : null;
    }

    public boolean isBossOrMinion(Entity entity) {
        return entity.getPersistentDataContainer().has(bossKey, PersistentDataType.BYTE)
                || entity.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE);
    }

    private boolean isBoss(Entity entity) {
        return boss != null && entity.getUniqueId().equals(boss.getUniqueId());
    }

    private double maxHealth() {
        return Math.max(20, Math.min(1024, plugin.getConfig().getDouble("boss.health", 750)));
    }

    // ------------------------------------------------------------------
    // Summoning
    // ------------------------------------------------------------------

    /** Sakura Sigil. Returns true if the boss spawned (so the sigil is used up). */
    public boolean trySummon(Player player, Location spot) {
        if (isAlive()) {
            player.sendMessage(plugin.msg("&cThe Sakura Warden is already awake somewhere!"));
            return false;
        }
        World world = spot.getWorld();
        List<String> worlds = plugin.getConfig().getStringList("boss.summon-worlds");
        if (world == null || (!worlds.isEmpty() && !worlds.contains(world.getName()))) {
            player.sendMessage(plugin.msg("&cThe Warden can't be summoned in this world."));
            return false;
        }
        double minDistance = plugin.getConfig().getDouble("boss.min-distance-from-spawn", 150);
        if (spot.distance(world.getSpawnLocation()) < minDistance) {
            player.sendMessage(plugin.msg("&cToo close to spawn! Go at least " + (int) minDistance
                    + " blocks away."));
            return false;
        }
        return spawn(spot, player.getName());
    }

    public boolean spawn(Location location, String by) {
        if (isAlive()) {
            return false;
        }
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        double health = maxHealth();
        WitherSkeleton spawned = world.spawn(location, WitherSkeleton.class, ws -> {
            ws.customName(Text.component(NAME));
            ws.setCustomNameVisible(true);
            ws.setPersistent(true);
            ws.setRemoveWhenFarAway(false);
            ws.setCanPickupItems(false);
            ws.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
            setAttribute(ws, Attribute.MAX_HEALTH, health);
            setAttribute(ws, Attribute.SCALE, plugin.getConfig().getDouble("boss.size", 1.6));
            setAttribute(ws, Attribute.ATTACK_DAMAGE, plugin.getConfig().getDouble("boss.damage", 12));
            setAttribute(ws, Attribute.MOVEMENT_SPEED, 0.28);
            setAttribute(ws, Attribute.KNOCKBACK_RESISTANCE, 0.8);
            setAttribute(ws, Attribute.FOLLOW_RANGE, 40);
            ws.setHealth(health);
            EntityEquipment eq = ws.getEquipment();
            if (eq != null) {
                eq.setHelmet(plugin.getItems().pinkArmor(Material.LEATHER_HELMET));
                eq.setChestplate(plugin.getItems().pinkArmor(Material.LEATHER_CHESTPLATE));
                eq.setLeggings(plugin.getItems().pinkArmor(Material.LEATHER_LEGGINGS));
                eq.setBoots(plugin.getItems().pinkArmor(Material.LEATHER_BOOTS));
                eq.setItemInMainHand(plugin.getItems().reaper());
                eq.setHelmetDropChance(0f);
                eq.setChestplateDropChance(0f);
                eq.setLeggingsDropChance(0f);
                eq.setBootsDropChance(0f);
                eq.setItemInMainHandDropChance(0f);
            }
        });
        if (!spawned.isValid()) {
            plugin.getLogger().warning("Something stopped the Sakura Warden from spawning at " + where(location)
                    + " (a protection plugin or mob spawn setting?).");
            return false;
        }
        boss = spawned;
        damage.clear();
        minions.clear();
        enraged = false;
        seconds = 0;
        spawnedAt = System.currentTimeMillis();
        chunkTicket = location.clone();
        location.getChunk().addPluginChunkTicket(plugin);
        bar = BossBar.bossBar(Text.component(NAME), 1f, BossBar.Color.PINK, BossBar.Overlay.NOTCHED_10);

        world.strikeLightningEffect(location);
        world.spawnParticle(Particle.CHERRY_LEAVES, location.clone().add(0, 1.5, 0), 200, 2, 2, 2, 0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.3f);
            p.showTitle(Title.title(Text.component("&#FF1493&l❀ SAKURA WARDEN ❀"),
                    Text.component("&7has awoken at &f" + where(location))));
        }
        Bukkit.broadcast(plugin.msg("&#FF1493&lThe Sakura Warden has awoken! &7(" + where(location)
                + (by == null ? "" : ", summoned by " + by) + ")"));
        Bukkit.broadcast(plugin.msg("&7Everyone who helps gets rewards. The best fighter might get the &c&lPetal Reaper&7!"));
        if (task != null) {
            task.cancel();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return true;
    }

    private void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private String where(Location l) {
        return (l.getWorld() == null ? "" : l.getWorld().getName() + " ") + l.getBlockX() + ", " + l.getBlockY()
                + ", " + l.getBlockZ();
    }

    // ------------------------------------------------------------------
    // Every second while alive
    // ------------------------------------------------------------------

    private void tick() {
        if (!isAlive()) {
            if (boss != null && !boss.isDead()) {
                // Unloaded or removed by something else
                cleanup();
            }
            return;
        }
        seconds++;
        double health = boss.getHealth();
        double max = maxHealth();
        bar.progress((float) Math.max(0, Math.min(1, health / max)));
        bar.name(Text.component(NAME + " &f" + (int) Math.ceil(health) + "&7/&f" + (int) max + " ❤"
                + (enraged ? " &c&lENRAGED" : "")));
        updateViewers();

        Location at = boss.getLocation();
        at.getWorld().spawnParticle(Particle.CHERRY_LEAVES, at.clone().add(0, 1.5, 0), 12, 0.7, 1, 0.7, 0);

        if (!enraged && health <= max / 2) {
            enrage();
        }
        int every = enraged ? plugin.getConfig().getInt("boss.attack-seconds-enraged", 5)
                : plugin.getConfig().getInt("boss.attack-seconds", 8);
        if (seconds % Math.max(2, every) == 0) {
            attack();
        }
        long maxMinutes = plugin.getConfig().getLong("boss.escape-minutes", 20);
        if (System.currentTimeMillis() - spawnedAt > maxMinutes * 60_000L) {
            Bukkit.broadcast(plugin.msg("&7Nobody defeated her in time... &#FF1493the Sakura Warden faded away."));
            boss.remove();
            cleanup();
        }
    }

    private void updateViewers() {
        Set<UUID> now = new HashSet<>();
        for (Player p : fighters(50)) {
            now.add(p.getUniqueId());
            if (viewers.add(p.getUniqueId())) {
                p.showBossBar(bar);
            }
        }
        for (UUID id : new ArrayList<>(viewers)) {
            if (!now.contains(id)) {
                viewers.remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(bar);
                }
            }
        }
    }

    /** Survival/adventure players near the boss. */
    private List<Player> fighters(double radius) {
        List<Player> list = new ArrayList<>();
        if (!isAlive()) {
            return list;
        }
        Location at = boss.getLocation();
        for (Player p : at.getWorld().getPlayers()) {
            if ((p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                    && p.getLocation().distanceSquared(at) <= radius * radius) {
                list.add(p);
            }
        }
        return list;
    }

    private void tell(double radius, String message) {
        for (Player p : fighters(radius)) {
            p.sendMessage(Text.component("&#FF1493❀ &f" + message));
        }
    }

    private void enrage() {
        enraged = true;
        Location at = boss.getLocation();
        at.getWorld().strikeLightningEffect(at);
        setAttribute(boss, Attribute.MOVEMENT_SPEED, 0.36);
        setAttribute(boss, Attribute.ATTACK_DAMAGE, plugin.getConfig().getDouble("boss.damage", 12) * 1.3);
        for (Player p : fighters(60)) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.4f);
            p.showTitle(Title.title(Text.component("&c&lENRAGED"), Text.component("&7The Warden is furious!")));
        }
        bar.color(BossBar.Color.RED);
    }

    // ------------------------------------------------------------------
    // Attacks
    // ------------------------------------------------------------------

    private void attack() {
        List<Player> near = fighters(20);
        if (near.isEmpty()) {
            return;
        }
        int pick = ThreadLocalRandom.current().nextInt(enraged ? 4 : 3);
        switch (pick) {
            case 0 -> petalStorm();
            case 1 -> summonThornlings();
            case 2 -> blossomLeap(near);
            default -> witherBloom();
        }
    }

    /** Hurts and slows everyone close. */
    private void petalStorm() {
        Location at = boss.getLocation();
        at.getWorld().spawnParticle(Particle.CHERRY_LEAVES, at.clone().add(0, 1, 0), 300, 4, 1.5, 4, 0.05);
        at.getWorld().playSound(at, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.5f, 0.8f);
        tell(30, "&#FF69B4Petal Storm! &7Get away from the Warden!");
        double dmg = plugin.getConfig().getDouble("boss.storm-damage", 6);
        for (Player p : fighters(7)) {
            p.damage(dmg, boss);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            p.setVelocity(p.getVelocity().setY(0.6));
        }
    }

    /** Spawns pink zombie helpers. */
    private void summonThornlings() {
        minions.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || e.isDead();
        });
        if (minions.size() >= 8) {
            petalStorm();
            return;
        }
        Location at = boss.getLocation();
        at.getWorld().playSound(at, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.5f, 1.2f);
        tell(30, "&#FF69B4The Warden calls her Thornlings!");
        int count = enraged ? 4 : 3;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            Location spot = at.clone().add(Math.cos(angle) * 2, 0, Math.sin(angle) * 2);
            Zombie z = at.getWorld().spawn(spot, Zombie.class, zombie -> {
                zombie.customName(Text.component("&#FF69B4Thornling"));
                zombie.setAdult();
                zombie.setPersistent(false);
                zombie.setCanPickupItems(false);
                zombie.getPersistentDataContainer().set(minionKey, PersistentDataType.BYTE, (byte) 1);
                setAttribute(zombie, Attribute.MAX_HEALTH, 24);
                zombie.setHealth(24);
                EntityEquipment eq = zombie.getEquipment();
                if (eq != null) {
                    eq.setHelmet(plugin.getItems().pinkArmor(Material.LEATHER_HELMET));
                    eq.setHelmetDropChance(0f);
                    eq.setItemInMainHand(new ItemStack(Material.STONE_SWORD));
                    eq.setItemInMainHandDropChance(0f);
                }
            });
            if (z.isValid()) {
                minions.add(z.getUniqueId());
                at.getWorld().spawnParticle(Particle.CHERRY_LEAVES, spot.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0);
            }
        }
    }

    /** Jumps at the furthest player and slams down. */
    private void blossomLeap(List<Player> near) {
        Player target = near.get(0);
        double best = -1;
        Location at = boss.getLocation();
        for (Player p : near) {
            double d = p.getLocation().distanceSquared(at);
            if (d > best) {
                best = d;
                target = p;
            }
        }
        Vector jump = target.getLocation().toVector().subtract(at.toVector());
        jump.setY(0);
        double distance = jump.length();
        if (distance < 0.5) {
            petalStorm();
            return;
        }
        jump.normalize().multiply(Math.min(1.8, 0.4 + distance * 0.12)).setY(0.9);
        boss.setVelocity(jump);
        at.getWorld().playSound(at, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 1.3f);
        tell(30, "&#FF69B4The Warden leaps at &f" + target.getName() + "&#FF69B4!");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!isAlive()) {
                return;
            }
            Location land = boss.getLocation();
            land.getWorld().spawnParticle(Particle.EXPLOSION, land, 3, 1, 0.2, 1, 0);
            land.getWorld().spawnParticle(Particle.CHERRY_LEAVES, land.clone().add(0, 0.5, 0), 150, 3, 0.3, 3, 0.1);
            land.getWorld().playSound(land, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
            double dmg = plugin.getConfig().getDouble("boss.slam-damage", 9);
            for (Player p : fighters(4.5)) {
                p.damage(dmg, boss);
                Vector push = p.getLocation().toVector().subtract(land.toVector()).setY(0);
                if (push.lengthSquared() > 0.01) {
                    p.setVelocity(push.normalize().multiply(1.1).setY(0.5));
                }
            }
        }, 25L);
    }

    /** Enraged only: wither and darkness on everyone close. */
    private void witherBloom() {
        Location at = boss.getLocation();
        at.getWorld().playSound(at, Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.7f);
        at.getWorld().spawnParticle(Particle.SMOKE, at.clone().add(0, 1, 0), 120, 5, 1, 5, 0.02);
        tell(30, "&5Wither Bloom! &7The petals turn black...");
        for (Player p : fighters(10)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0));
        }
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageByEntityEvent event) {
        if (!isBoss(event.getEntity())) {
            return;
        }
        Player player = null;
        if (event.getDamager() instanceof Player p) {
            player = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            player = p;
        }
        if (player != null) {
            damage.merge(player.getUniqueId(), event.getFinalDamage(), Double::sum);
        }
    }

    /** No cheap kills: suffocation, falling, drowning, lava, cactus... don't hurt her. */
    @EventHandler(ignoreCancelled = true)
    public void onEnvironment(EntityDamageEvent event) {
        if (!isBoss(event.getEntity()) || event instanceof EntityDamageByEntityEvent) {
            return;
        }
        switch (event.getCause()) {
            case SUFFOCATION, FALL, DROWNING, LAVA, FIRE, FIRE_TICK, CONTACT, CRAMMING, HOT_FLOOR,
                    FREEZE, FALLING_BLOCK, BLOCK_EXPLOSION -> event.setCancelled(true);
            default -> {
            }
        }
    }

    /** The boss and her minions only fight players. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (isBossOrMinion(event.getEntity()) && event.getTarget() != null
                && !(event.getTarget() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    /** A Warden or Thornling left over from before a restart: remove it. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isBossOrMinion(entity) && !isBoss(entity) && !minions.contains(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(5);
            return;
        }
        if (!isBoss(dead)) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(plugin.getConfig().getInt("boss.xp", 1500));
        Location at = dead.getLocation();
        rewardFighters(at);
        at.getWorld().strikeLightningEffect(at);
        at.getWorld().spawnParticle(Particle.CHERRY_LEAVES, at.clone().add(0, 1, 0), 400, 3, 3, 3, 0.1);
        cleanup();
    }

    private void rewardFighters(Location at) {
        double total = 0;
        for (double d : damage.values()) {
            total += d;
        }
        if (total <= 0) {
            Bukkit.broadcast(plugin.msg("&7The Sakura Warden fell... but no player hurt her."));
            return;
        }
        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(damage.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        double minPercent = plugin.getConfig().getDouble("boss.min-damage-percent", 5);
        Reward participant = Reward.from(plugin.getConfig().getConfigurationSection("boss.rewards.participant"));
        Reward top = Reward.from(plugin.getConfig().getConfigurationSection("boss.rewards.top-damager"));
        int reaperChance = plugin.getConfig().getInt("boss.reaper-chance", 35);
        int sigilChance = plugin.getConfig().getInt("boss.sigil-chance", 10);

        Bukkit.broadcast(Text.component(""));
        Bukkit.broadcast(Text.component("&#FF1493&l❀ THE SAKURA WARDEN HAS FALLEN ❀"));
        String[] medals = {"&#FFD700&l#1", "&#C0C0C0&l#2", "&#CD7F32&l#3"};
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            Map.Entry<UUID, Double> e = sorted.get(i);
            Player p = Bukkit.getPlayer(e.getKey());
            String name = p != null ? p.getName() : Bukkit.getOfflinePlayer(e.getKey()).getName();
            Bukkit.broadcast(Text.component("  " + medals[i] + " &f" + name + " &8- &7"
                    + Math.round(e.getValue() * 100 / total) + "% damage"));
        }

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<UUID, Double> e = sorted.get(i);
            Player p = Bukkit.getPlayer(e.getKey());
            double percent = e.getValue() * 100 / total;
            if (p == null || percent < minPercent) {
                if (p != null) {
                    p.sendMessage(plugin.msg("&7You did " + Math.round(percent) + "% damage. You need "
                            + (int) minPercent + "% for a reward."));
                }
                continue;
            }
            plugin.getRewards().give(p, participant);
            plugin.getQuests().progress(p, QuestType.BOSS, "SAKURA_WARDEN", true, 1);
            p.sendMessage(plugin.msg("&aYou helped defeat the Sakura Warden! &7("
                    + String.join("&7, ", plugin.getRewards().describe(participant)) + "&7)"));
            if (i == 0) {
                plugin.getRewards().give(p, top);
                p.sendMessage(plugin.msg("&6&lTop damage bonus! &7("
                        + String.join("&7, ", plugin.getRewards().describe(top)) + "&7)"));
                if (ThreadLocalRandom.current().nextInt(100) < reaperChance) {
                    plugin.getRewards().giveItem(p, plugin.getItems().reaper());
                    Bukkit.broadcast(plugin.msg("&f" + p.getName() + " &7got the legendary &#FF1493&lPetal Reaper&7!"));
                    for (Player all : Bukkit.getOnlinePlayers()) {
                        all.playSound(all.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
                    }
                }
            }
            if (ThreadLocalRandom.current().nextInt(100) < sigilChance) {
                plugin.getRewards().giveItem(p, plugin.getItems().sigil());
                p.sendMessage(plugin.msg("&5You found a &dSakura Sigil&5! Use it to summon her again."));
            }
        }
    }

    /** Removes the boss bar, minions and chunk ticket. */
    public void cleanup() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bar != null) {
            for (UUID id : viewers) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(bar);
                }
            }
        }
        viewers.clear();
        for (UUID id : minions) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        minions.clear();
        if (chunkTicket != null && chunkTicket.getWorld() != null) {
            chunkTicket.getChunk().removePluginChunkTicket(plugin);
        }
        chunkTicket = null;
        boss = null;
        damage.clear();
    }

    /** /bq boss kill and plugin shutdown: remove without rewards. */
    public void despawn() {
        if (boss != null && boss.isValid()) {
            boss.remove();
        }
        cleanup();
    }

    // ------------------------------------------------------------------
    // Automatic spawns
    // ------------------------------------------------------------------

    private void scheduleNextAuto() {
        long minutes = Math.max(10, plugin.getConfig().getLong("boss.auto-spawn.every-minutes", 180));
        nextAutoSpawn = System.currentTimeMillis() + minutes * 60_000L;
        warned = false;
    }

    public long nextAutoSpawn() {
        return plugin.getConfig().getBoolean("boss.auto-spawn.enabled", false) ? nextAutoSpawn : 0;
    }

    private Location autoLocation() {
        String world = plugin.getConfig().getString("boss.auto-spawn.world", "world");
        World w = Bukkit.getWorld(world);
        if (w == null) {
            return null;
        }
        return new Location(w, plugin.getConfig().getDouble("boss.auto-spawn.x"),
                plugin.getConfig().getDouble("boss.auto-spawn.y", 80), plugin.getConfig().getDouble("boss.auto-spawn.z"));
    }

    private void autoTick() {
        if (!plugin.getConfig().getBoolean("boss.auto-spawn.enabled", false)) {
            return;
        }
        if (isAlive()) {
            scheduleNextAuto();
            return;
        }
        int minPlayers = plugin.getConfig().getInt("boss.auto-spawn.min-players", 3);
        if (Bukkit.getOnlinePlayers().size() < minPlayers) {
            return;
        }
        long left = nextAutoSpawn - System.currentTimeMillis();
        Location spot = autoLocation();
        if (spot == null) {
            return;
        }
        if (!warned && left <= 5 * 60_000L) {
            warned = true;
            Bukkit.broadcast(plugin.msg("&#FF1493The Sakura Warden awakens in &f5 minutes &#FF1493at &f"
                    + where(spot) + "&#FF1493! Get ready!"));
        }
        if (left <= 0) {
            spawn(spot, null);
            scheduleNextAuto();
        }
    }
}
