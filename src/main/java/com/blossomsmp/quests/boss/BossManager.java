package com.blossomsmp.quests.boss;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.quest.QuestType;
import com.blossomsmp.quests.quest.Reward;
import com.blossomsmp.quests.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Runs every boss: spawning, boss bars, attacks, rage, looks, rewards and automatic spawns. */
public class BossManager implements Listener {

    private final BlossomQuests plugin;
    private final NamespacedKey bossKey;
    private final NamespacedKey minionKey;
    private final NamespacedKey displayKey;
    private final Map<String, BossType> types = new LinkedHashMap<>();
    private final Map<String, ActiveBoss> active = new ConcurrentHashMap<>();

    private long nextAutoSpawn;
    private boolean warned;

    public BossManager(BlossomQuests plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "boss_type");
        this.minionKey = new NamespacedKey(plugin, "minion");
        this.displayKey = new NamespacedKey(plugin, "boss_display");
        for (BossType type : List.of(new SakuraWarden(plugin), new EmberTyrant(plugin), new FrostKing(plugin))) {
            types.put(type.id(), type);
        }
        scheduleNextAuto();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::orbitTick, 2L, 2L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::autoTick, 20L * 30, 20L * 30);
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    public NamespacedKey minionKey() {
        return minionKey;
    }

    public Collection<BossType> types() {
        return types.values();
    }

    public BossType type(String id) {
        return id == null ? null : types.get(id.toLowerCase());
    }

    /** The boss type that drops this weapon or uses this sigil. */
    public BossType typeForSigil(String sigilId) {
        for (BossType t : types.values()) {
            if (t.sigilId().equals(sigilId)) {
                return t;
            }
        }
        return null;
    }

    public ActiveBoss active(String id) {
        ActiveBoss boss = active.get(id);
        return boss != null && boss.alive() ? boss : null;
    }

    public boolean isAlive() {
        for (ActiveBoss boss : active.values()) {
            if (boss.alive()) {
                return true;
            }
        }
        return false;
    }

    public List<ActiveBoss> alive() {
        List<ActiveBoss> list = new ArrayList<>();
        for (ActiveBoss boss : active.values()) {
            if (boss.alive()) {
                list.add(boss);
            }
        }
        return list;
    }

    public boolean isBossOrMinion(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.has(bossKey, PersistentDataType.STRING) || pdc.has(minionKey, PersistentDataType.BYTE)
                || pdc.has(displayKey, PersistentDataType.BYTE);
    }

    private ActiveBoss bossOf(Entity entity) {
        for (ActiveBoss boss : active.values()) {
            if (boss.entity.getUniqueId().equals(entity.getUniqueId())) {
                return boss;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    /** A player used a sigil. Returns true if the boss spawned (so the sigil is used up). */
    public boolean trySummon(Player player, BossType type, Location spot) {
        if (active(type.id()) != null) {
            player.sendMessage(plugin.msg("&cThe " + type.plainName() + " is already awake somewhere!"));
            return false;
        }
        if (!type.enabled()) {
            player.sendMessage(plugin.msg("&cThe " + type.plainName() + " is turned off on this server."));
            return false;
        }
        World world = spot.getWorld();
        List<String> worlds = plugin.getConfig().getStringList("boss-settings.summon-worlds");
        if (world == null || (!worlds.isEmpty() && !worlds.contains(world.getName()))) {
            player.sendMessage(plugin.msg("&cBosses can't be summoned in this world."));
            return false;
        }
        double minDistance = plugin.getConfig().getDouble("boss-settings.min-distance-from-spawn", 150);
        if (spot.distance(world.getSpawnLocation()) < minDistance) {
            player.sendMessage(plugin.msg("&cToo close to spawn! Go at least " + (int) minDistance + " blocks away."));
            return false;
        }
        return spawn(type, spot, player.getName());
    }

    public boolean spawn(BossType type, Location location, String by) {
        if (active(type.id()) != null || location.getWorld() == null) {
            return false;
        }
        LivingEntity entity = type.spawn(location, e ->
                e.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, type.id()));
        if (entity == null || !entity.isValid()) {
            plugin.getLogger().warning("Something stopped the " + type.plainName() + " from spawning at "
                    + where(location) + " (a protection plugin or mob spawn setting?).");
            return false;
        }
        ModelHook model = null;
        if (type.id().equals("sakura")) {
            ModelHook hook = new ModelHook(plugin);
            if (hook.apply(entity)) {
                model = hook;
                entity.setInvisible(true);
                EntityEquipment eq = entity.getEquipment();
                if (eq != null) {
                    eq.setHelmet(null);
                    eq.setChestplate(null);
                    eq.setLeggings(null);
                    eq.setBoots(null);
                    eq.setItemInMainHand(null);
                }
            }
        }
        BossBar bar = BossBar.bossBar(Text.component(type.name()), 1f, type.barColor(), BossBar.Overlay.NOTCHED_10);
        ActiveBoss boss = new ActiveBoss(type, entity, bar, location.clone(), model);
        active.put(type.id(), boss);
        location.getChunk().addPluginChunkTicket(plugin);
        glow(boss, true);
        if (model == null) {
            spawnOrbit(boss);
        }

        World world = location.getWorld();
        world.strikeLightningEffect(location);
        world.spawnParticle(type.ambientParticle(), location.clone().add(0, 1.5, 0), 200, 2, 2, 2, 0.02);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sounds()) {
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
            }
            p.showTitle(Title.title(Text.component(type.name()),
                    Text.component("&7has awoken at &f" + where(location))));
        }
        Bukkit.broadcast(plugin.msg(type.color() + "&lThe " + type.plainName() + " has awoken! &7(" + where(location)
                + (by == null ? "" : ", summoned by " + by) + ")"));
        Bukkit.broadcast(plugin.msg("&7Everyone who helps gets rewards. The best fighter might get a &c&llegendary weapon&7!"));
        updateViewers(boss);
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
    // Looks: coloured glow outline and items circling the boss
    // ------------------------------------------------------------------

    private void glow(ActiveBoss boss, boolean on) {
        try {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            String name = "bq_" + boss.type.id();
            Team team = board.getTeam(name);
            if (team == null) {
                team = board.registerNewTeam(name);
            }
            team.color(boss.type.glowColor());
            String entry = boss.entity.getUniqueId().toString();
            if (on) {
                team.addEntry(entry);
            } else {
                team.removeEntry(entry);
            }
        } catch (Throwable ignored) {
            // the glow just stays white
        }
    }

    private void spawnOrbit(ActiveBoss boss) {
        if (!plugin.getConfig().getBoolean("boss-settings.orbit-effects", true)) {
            return;
        }
        Location at = boss.entity.getLocation();
        ItemStack item = new ItemStack(boss.type.orbitItem());
        for (int i = 0; i < 8; i++) {
            ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(item);
                d.setPersistent(false);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(2);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(0.7f, 0.7f, 0.7f), new AxisAngle4f()));
                d.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
            });
            boss.orbit.add(display.getUniqueId());
        }
    }

    private void orbitTick() {
        for (ActiveBoss boss : active.values()) {
            if (!boss.alive() || boss.orbit.isEmpty()) {
                continue;
            }
            boss.orbitAngle += boss.enraged ? 0.22 : 0.12;
            Location at = boss.entity.getLocation();
            double size = boss.type.cfg("size", 1.6);
            double radius = 1.3 * size;
            for (int i = 0; i < boss.orbit.size(); i++) {
                Entity e = Bukkit.getEntity(boss.orbit.get(i));
                if (e == null) {
                    continue;
                }
                double angle = boss.orbitAngle + Math.PI * 2 * i / boss.orbit.size();
                double height = 1.4 * size + Math.sin(boss.orbitAngle * 2 + i) * 0.4;
                Location spot = at.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
                spot.setYaw((float) Math.toDegrees(-angle));
                e.teleport(spot);
            }
        }
    }

    // ------------------------------------------------------------------
    // Every second
    // ------------------------------------------------------------------

    private void tick() {
        for (ActiveBoss boss : new ArrayList<>(active.values())) {
            if (!boss.alive()) {
                if (!boss.entity.isDead()) {
                    end(boss);
                }
                continue;
            }
            tickBoss(boss);
        }
    }

    private void tickBoss(ActiveBoss boss) {
        BossType type = boss.type;
        boss.seconds++;
        double health = boss.entity.getHealth();
        double max = type.maxHealth();
        boss.bar.progress((float) Math.max(0, Math.min(1, health / max)));
        boss.bar.name(Text.component(type.name() + " &f" + (int) Math.ceil(health) + "&7/&f" + (int) max + " ❤"
                + (boss.enraged ? " &c&lENRAGED" : "")));
        updateViewers(boss);

        Location at = boss.entity.getLocation();
        at.getWorld().spawnParticle(type.ambientParticle(), at.clone().add(0, 1.5, 0), 10, 0.7, 1, 0.7, 0.01);
        // Wither growls during the fight
        if (sounds() && boss.seconds % 6 == 0) {
            at.getWorld().playSound(at, Sound.ENTITY_WITHER_AMBIENT, 2.5f, 0.9f);
        }

        if (!boss.enraged && health <= max / 2) {
            boss.enraged = true;
            at.getWorld().strikeLightningEffect(at);
            AttributeInstance speed = boss.entity.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(speed.getBaseValue() * 1.25);
            }
            setAttribute(boss.entity, Attribute.ATTACK_DAMAGE, type.cfg("damage", 12) * 1.3);
            for (Player p : nearby(boss, 60)) {
                if (sounds()) {
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.7f);
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.4f);
                }
                p.showTitle(Title.title(Text.component("&c&lENRAGED"),
                        Text.component("&7The " + type.plainName() + " is furious!")));
            }
            boss.bar.color(BossBar.Color.RED);
            type.onEnrage(boss);
        }
        int every = boss.enraged ? type.cfgInt("attack-seconds-enraged", 5) : type.cfgInt("attack-seconds", 8);
        if (boss.seconds % Math.max(2, every) == 0) {
            List<Player> near = nearby(boss, 20);
            if (!near.isEmpty()) {
                type.attack(boss, near);
            }
        }
        long maxMinutes = (long) type.cfg("escape-minutes", 20);
        if (System.currentTimeMillis() - boss.spawnedAt > maxMinutes * 60_000L) {
            Bukkit.broadcast(plugin.msg("&7Nobody defeated it in time... " + type.color() + "the "
                    + type.plainName() + " left."));
            boss.entity.remove();
            end(boss);
        }
    }

    private List<Player> nearby(ActiveBoss boss, double radius) {
        List<Player> list = new ArrayList<>();
        Location at = boss.entity.getLocation();
        for (Player p : at.getWorld().getPlayers()) {
            if ((p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE)
                    && p.getLocation().distanceSquared(at) <= radius * radius) {
                list.add(p);
            }
        }
        return list;
    }

    private boolean sounds() {
        return plugin.getConfig().getBoolean("boss-settings.wither-sounds", true);
    }

    /** Who sees the boss bar: everyone online, or every player (any game mode) within bar-range blocks. */
    private List<Player> barViewers(ActiveBoss boss) {
        if (plugin.getConfig().getBoolean("boss-settings.boss-bar-for-everyone", false)) {
            return new ArrayList<>(Bukkit.getOnlinePlayers());
        }
        double range = plugin.getConfig().getDouble("boss-settings.boss-bar-range", 80);
        Location at = boss.entity.getLocation();
        List<Player> list = new ArrayList<>();
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) <= range * range) {
                list.add(p);
            }
        }
        return list;
    }

    private void updateViewers(ActiveBoss boss) {
        Set<UUID> now = new HashSet<>();
        for (Player p : barViewers(boss)) {
            now.add(p.getUniqueId());
            if (boss.viewers.add(p.getUniqueId())) {
                p.showBossBar(boss.bar);
            }
        }
        for (UUID id : new ArrayList<>(boss.viewers)) {
            if (!now.contains(id)) {
                boss.viewers.remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(boss.bar);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageByEntityEvent event) {
        ActiveBoss hitter = bossOf(event.getDamager());
        if (hitter != null) {
            hitter.animate("attack");
        }
        ActiveBoss boss = bossOf(event.getEntity());
        if (boss == null) {
            return;
        }
        Player player = null;
        if (event.getDamager() instanceof Player p) {
            player = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            player = p;
        }
        if (player != null) {
            boss.damage.merge(player.getUniqueId(), event.getFinalDamage(), Double::sum);
        }
    }

    /** No cheap kills: suffocation, falling, drowning, lava, cactus... don't hurt bosses. */
    @EventHandler(ignoreCancelled = true)
    public void onEnvironment(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent || bossOf(event.getEntity()) == null) {
            return;
        }
        switch (event.getCause()) {
            case SUFFOCATION, FALL, DROWNING, LAVA, FIRE, FIRE_TICK, CONTACT, CRAMMING, HOT_FLOOR,
                    FREEZE, FALLING_BLOCK, BLOCK_EXPLOSION -> event.setCancelled(true);
            default -> {
            }
        }
    }

    /** Bosses and their minions only fight players. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (isBossOrMinion(event.getEntity()) && event.getTarget() != null
                && !(event.getTarget() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    /** A boss, minion or orbit item left over from before a restart: remove it. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (!isBossOrMinion(entity)) {
                continue;
            }
            boolean current = bossOf(entity) != null;
            for (ActiveBoss boss : active.values()) {
                if (boss.minions.contains(entity.getUniqueId()) || boss.orbit.contains(entity.getUniqueId())) {
                    current = true;
                }
            }
            if (!current) {
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
        ActiveBoss boss = bossOf(dead);
        if (boss == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(boss.type.cfgInt("xp", 1500));
        Location at = dead.getLocation();
        reward(boss);
        if (sounds()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.8f, 1f);
            }
        }
        at.getWorld().strikeLightningEffect(at);
        at.getWorld().spawnParticle(boss.type.ambientParticle(), at.clone().add(0, 1, 0), 400, 3, 3, 3, 0.1);
        end(boss);
    }

    private void reward(ActiveBoss boss) {
        BossType type = boss.type;
        double total = 0;
        for (double d : boss.damage.values()) {
            total += d;
        }
        if (total <= 0) {
            Bukkit.broadcast(plugin.msg("&7The " + type.plainName() + " fell... but no player hurt it."));
            return;
        }
        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(boss.damage.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        double minPercent = plugin.getConfig().getDouble("boss-settings.min-damage-percent", 5);
        String base = "bosses." + type.id() + ".rewards.";
        Reward participant = Reward.from(plugin.getConfig().getConfigurationSection(base + "participant"));
        Reward top = Reward.from(plugin.getConfig().getConfigurationSection(base + "top-damager"));
        int weaponChance = type.cfgInt("weapon-chance", 35);
        int sigilChance = type.cfgInt("sigil-chance", 10);

        Bukkit.broadcast(Text.component(""));
        Bukkit.broadcast(Text.component(type.color() + "&l" + type.plainName().toUpperCase() + " HAS FALLEN"));
        String[] medals = {"&#FFD700&l#1", "&#C0C0C0&l#2", "&#CD7F32&l#3"};
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            Map.Entry<UUID, Double> e = sorted.get(i);
            Player p = Bukkit.getPlayer(e.getKey());
            String name = p != null ? p.getName() : Bukkit.getOfflinePlayer(e.getKey()).getName();
            Bukkit.broadcast(Text.component("  " + medals[i] + " &f" + name + " &8- &7"
                    + Math.round(e.getValue() * 100 / total) + "% damage"));
        }
        String weaponName = plugin.getItems().displayName(type.weaponId());
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<UUID, Double> e = sorted.get(i);
            Player p = Bukkit.getPlayer(e.getKey());
            double percent = e.getValue() * 100 / total;
            if (p == null) {
                continue;
            }
            if (percent < minPercent) {
                p.sendMessage(plugin.msg("&7You did " + Math.round(percent) + "% damage. You need "
                        + (int) minPercent + "% for a reward."));
                continue;
            }
            plugin.getRewards().give(p, participant);
            plugin.getQuests().progress(p, QuestType.BOSS, type.id().toUpperCase(), true, 1);
            plugin.getQuests().addCounter(p, plugin.getData().get(p), "boss_" + type.id(), 1);
            p.sendMessage(plugin.msg("&aYou helped defeat the " + type.plainName() + "! &7("
                    + String.join("&7, ", plugin.getRewards().describe(participant)) + "&7)"));
            if (i == 0) {
                plugin.getRewards().give(p, top);
                p.sendMessage(plugin.msg("&6&lTop damage bonus! &7("
                        + String.join("&7, ", plugin.getRewards().describe(top)) + "&7)"));
                if (ThreadLocalRandom.current().nextInt(100) < weaponChance) {
                    plugin.getRewards().giveItem(p, plugin.getItems().weapon(type.weaponId()));
                    Bukkit.broadcast(plugin.msg("&f" + p.getName() + " &7got the legendary " + weaponName + "&7!"));
                    for (Player all : Bukkit.getOnlinePlayers()) {
                        all.playSound(all.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
                    }
                }
            }
            if (ThreadLocalRandom.current().nextInt(100) < sigilChance) {
                plugin.getRewards().giveItem(p, plugin.getItems().sigil(type.sigilId()));
                p.sendMessage(plugin.msg("&5You found a " + plugin.getItems().displayName(type.sigilId())
                        + "&5! Use it to summon the " + type.plainName() + " again."));
            }
        }
    }

    // ------------------------------------------------------------------
    // Ending
    // ------------------------------------------------------------------

    /** Removes the boss bar, minions, orbit items, glow and chunk ticket. */
    private void end(ActiveBoss boss) {
        active.remove(boss.type.id());
        if (boss.model != null) {
            boss.model.remove();
        }
        for (UUID id : boss.viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(boss.bar);
            }
        }
        boss.viewers.clear();
        for (UUID id : boss.minions) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        for (UUID id : boss.orbit) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        glow(boss, false);
        boolean otherHere = false;
        for (ActiveBoss other : active.values()) {
            if (other.chunkTicket.getChunk().equals(boss.chunkTicket.getChunk())) {
                otherHere = true;
            }
        }
        if (!otherHere && boss.chunkTicket.getWorld() != null) {
            boss.chunkTicket.getChunk().removePluginChunkTicket(plugin);
        }
    }

    /** Removes one boss (or all with null) without rewards. */
    public int despawn(String id) {
        int count = 0;
        for (ActiveBoss boss : new ArrayList<>(active.values())) {
            if (id == null || boss.type.id().equals(id)) {
                if (boss.entity.isValid()) {
                    boss.entity.remove();
                }
                end(boss);
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Automatic spawns
    // ------------------------------------------------------------------

    private void scheduleNextAuto() {
        long minutes = Math.max(10, plugin.getConfig().getLong("boss-settings.auto-spawn.every-minutes", 180));
        nextAutoSpawn = System.currentTimeMillis() + minutes * 60_000L;
        warned = false;
    }

    public long nextAutoSpawn() {
        return plugin.getConfig().getBoolean("boss-settings.auto-spawn.enabled", false) ? nextAutoSpawn : 0;
    }

    private Location autoLocation() {
        World w = Bukkit.getWorld(plugin.getConfig().getString("boss-settings.auto-spawn.world", "world"));
        if (w == null) {
            return null;
        }
        return new Location(w, plugin.getConfig().getDouble("boss-settings.auto-spawn.x"),
                plugin.getConfig().getDouble("boss-settings.auto-spawn.y", 80),
                plugin.getConfig().getDouble("boss-settings.auto-spawn.z"));
    }

    private void autoTick() {
        if (!plugin.getConfig().getBoolean("boss-settings.auto-spawn.enabled", false)) {
            return;
        }
        if (isAlive()) {
            scheduleNextAuto();
            return;
        }
        if (Bukkit.getOnlinePlayers().size() < plugin.getConfig().getInt("boss-settings.auto-spawn.min-players", 3)) {
            return;
        }
        Location spot = autoLocation();
        if (spot == null) {
            return;
        }
        long left = nextAutoSpawn - System.currentTimeMillis();
        if (!warned && left <= 5 * 60_000L) {
            warned = true;
            Bukkit.broadcast(plugin.msg("&dA boss awakens in &f5 minutes &dat &f" + where(spot) + "&d! Get ready!"));
        }
        if (left <= 0) {
            List<BossType> enabled = new ArrayList<>();
            for (BossType t : types.values()) {
                if (t.enabled()) {
                    enabled.add(t);
                }
            }
            if (!enabled.isEmpty()) {
                spawn(enabled.get(ThreadLocalRandom.current().nextInt(enabled.size())), spot, null);
            }
            scheduleNextAuto();
        }
    }
}
