package com.blossomsmp.quests.quest;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.data.PlayerData;
import com.blossomsmp.quests.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads quests.yml, hands out daily/weekly quests, tracks progress and pays rewards. */
public class QuestManager {

    private final BlossomQuests plugin;
    private final Map<String, QuestDef> daily = new LinkedHashMap<>();
    private final Map<String, QuestDef> weekly = new LinkedHashMap<>();
    private final Map<String, AchievementDef> achievements = new LinkedHashMap<>();

    public QuestManager(BlossomQuests plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    public void load() {
        File file = new File(plugin.getDataFolder(), "quests.yml");
        if (!file.exists()) {
            plugin.saveResource("quests.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        addNewDefaults(yaml, file);
        daily.clear();
        weekly.clear();
        achievements.clear();
        readQuests(yaml.getConfigurationSection("daily"), "daily", daily);
        readQuests(yaml.getConfigurationSection("weekly"), "weekly", weekly);
        ConfigurationSection ach = yaml.getConfigurationSection("achievements");
        if (ach != null) {
            for (String id : ach.getKeys(false)) {
                ConfigurationSection s = ach.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                long goal = s.getLong("goal", 0);
                String counter = s.getString("counter", "");
                if (goal <= 0 || counter.isEmpty()) {
                    plugin.getLogger().warning("Achievement '" + id + "' needs a counter and a goal above 0.");
                    continue;
                }
                achievements.put(id, new AchievementDef(id, s.getString("name", id), s.getString("description", ""),
                        material(s.getString("icon"), Material.GOLD_INGOT), counter.toLowerCase(Locale.ROOT), goal,
                        Reward.from(s.getConfigurationSection("rewards"))));
            }
        }
        plugin.getLogger().info("Loaded " + daily.size() + " daily quests, " + weekly.size() + " weekly quests and "
                + achievements.size() + " achievements.");
    }

    /** Bumped when new quests are added to the default quests.yml. */
    private static final int DEFAULTS_VERSION = 2;
    /** Quests and achievements added in 1.1.0 (the Blossom biomes ones). */
    private static final List<String> NEW_IN_2 = List.of("biome_gatherer", "biome_hunter", "blossom_harvest",
            "off_the_map", "holy_ground", "blossom_explorer", "beast_tamer", "legend_of_the_wilds", "petalfall",
            "thorn_in_the_side", "moonset", "warlords_end", "the_thaw", "gatherer", "hoarder");

    /**
     * An existing quests.yml is never replaced, so new default quests are copied into it once
     * (only ones that aren't there yet). Deleting one afterwards keeps it deleted.
     */
    private void addNewDefaults(YamlConfiguration yaml, File file) {
        if (yaml.getInt("defaults-version", 1) >= DEFAULTS_VERSION) {
            return;
        }
        java.io.InputStream in = plugin.getResource("quests.yml");
        if (in == null) {
            return;
        }
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        int added = 0;
        for (String scope : List.of("daily", "weekly", "achievements")) {
            for (String id : NEW_IN_2) {
                ConfigurationSection quest = defaults.getConfigurationSection(scope + "." + id);
                if (quest != null && !yaml.contains(scope + "." + id)) {
                    copy(quest, yaml, scope + "." + id);
                    added++;
                }
            }
        }
        yaml.set("defaults-version", DEFAULTS_VERSION);
        try {
            yaml.save(file);
            if (added > 0) {
                plugin.getLogger().info("Added " + added + " new Blossom biome quests and achievements to quests.yml.");
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Couldn't save the new quests to quests.yml: " + e.getMessage());
        }
    }

    private static void copy(ConfigurationSection from, ConfigurationSection to, String path) {
        for (String key : from.getKeys(false)) {
            Object value = from.get(key);
            if (value instanceof ConfigurationSection section) {
                copy(section, to, path + "." + key);
            } else {
                to.set(path + "." + key, value);
            }
        }
    }

    private void readQuests(ConfigurationSection root, String scope, Map<String, QuestDef> into) {
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            QuestType type;
            try {
                type = QuestType.valueOf(s.getString("type", "").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Quest '" + id + "' has an unknown type: " + s.getString("type")
                        + ". Use MINE, PLACE, KILL, KILL_PLAYER, FISH, CRAFT, BREED, PLAYTIME, BOSS, DISCOVER, GATHER or BIOME_BOSS.");
                continue;
            }
            int amount = s.getInt("amount", 0);
            if (amount <= 0) {
                plugin.getLogger().warning("Quest '" + id + "' needs an amount above 0.");
                continue;
            }
            List<String> targets = new ArrayList<>();
            if (s.isList("target")) {
                for (String t : s.getStringList("target")) {
                    targets.add(t.toUpperCase(Locale.ROOT));
                }
            } else if (s.getString("target") != null) {
                targets.add(s.getString("target").toUpperCase(Locale.ROOT));
            }
            List<String> description = s.isList("description") ? s.getStringList("description")
                    : s.getString("description") != null ? List.of(s.getString("description")) : List.of();
            into.put(id, new QuestDef(id, scope, s.getString("name", id), description,
                    material(s.getString("icon"), Material.PAPER), type, targets, amount,
                    Reward.from(s.getConfigurationSection("rewards"))));
        }
    }

    private Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material m = Material.matchMaterial(name);
        return m == null || !m.isItem() ? fallback : m;
    }

    public QuestDef quest(String scope, String id) {
        return scope.equals("weekly") ? weekly.get(id) : daily.get(id);
    }

    public List<AchievementDef> achievements() {
        return new ArrayList<>(achievements.values());
    }

    public AchievementDef achievement(String id) {
        return achievements.get(id);
    }

    // ------------------------------------------------------------------
    // Daily / weekly reset
    // ------------------------------------------------------------------

    private ZoneId zone() {
        try {
            return ZoneId.of(plugin.getConfig().getString("quests.timezone", "America/New_York"));
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    public String todayKey() {
        return LocalDate.now(zone()).toString();
    }

    public String weekKey() {
        LocalDate now = LocalDate.now(zone());
        return now.get(IsoFields.WEEK_BASED_YEAR) + "-W" + now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    public long secondsUntilDailyReset() {
        ZonedDateTime now = ZonedDateTime.now(zone());
        ZonedDateTime next = now.toLocalDate().plusDays(1).atStartOfDay(zone());
        return Duration.between(now, next).getSeconds();
    }

    public long secondsUntilWeeklyReset() {
        ZonedDateTime now = ZonedDateTime.now(zone());
        LocalDate monday = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return Duration.between(now, monday.atStartOfDay(zone())).getSeconds();
    }

    /** Gives the player new quests if a new day / week started. */
    public void ensureCurrent(PlayerData data) {
        String today = todayKey();
        if (!today.equals(data.dailyKey)) {
            data.dailyKey = today;
            assign(data.daily, daily, plugin.getConfig().getInt("quests.daily-count", 3));
            data.dirty = true;
        }
        String week = weekKey();
        if (!week.equals(data.weeklyKey)) {
            data.weeklyKey = week;
            assign(data.weekly, weekly, plugin.getConfig().getInt("quests.weekly-count", 3));
            data.dirty = true;
        }
    }

    private void assign(List<PlayerData.ActiveQuest> list, Map<String, QuestDef> pool, int count) {
        list.clear();
        List<String> ids = new ArrayList<>(pool.keySet());
        Collections.shuffle(ids);
        for (int i = 0; i < Math.min(count, ids.size()); i++) {
            list.add(new PlayerData.ActiveQuest(ids.get(i), 0, false));
        }
    }

    /** Admin: give new quests right now. */
    public void reroll(PlayerData data) {
        data.dailyKey = "";
        data.weeklyKey = "";
        ensureCurrent(data);
    }

    // ------------------------------------------------------------------
    // Progress
    // ------------------------------------------------------------------

    /** Something happened. target = Material or EntityType name. */
    public void progress(Player player, QuestType type, String target, boolean hostile, int amount) {
        if (amount <= 0) {
            return;
        }
        PlayerData data = plugin.getData().get(player);
        ensureCurrent(data);
        progressList(player, data, data.daily, "daily", type, target, hostile, amount);
        progressList(player, data, data.weekly, "weekly", type, target, hostile, amount);
        addCounter(player, data, type.counter(), amount);
    }

    private void progressList(Player player, PlayerData data, List<PlayerData.ActiveQuest> list, String scope,
                              QuestType type, String target, boolean hostile, int amount) {
        for (PlayerData.ActiveQuest active : list) {
            QuestDef def = quest(scope, active.id);
            if (def == null || active.claimed || active.progress >= def.amount()) {
                continue;
            }
            if (!def.matches(type, target, hostile)) {
                continue;
            }
            active.progress = Math.min(def.amount(), active.progress + amount);
            data.dirty = true;
            if (active.progress >= def.amount()) {
                player.showTitle(Title.title(Text.component("&d&l✿ Quest Complete! ✿"),
                        Text.component("&f" + def.name() + " &7- &d/quest &7to claim")));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
                player.sendMessage(plugin.msg("&aYou finished the " + scope + " quest &f" + def.name()
                        + "&a! Type &d/quest &ato claim your reward."));
            } else if (showProgress(def.amount(), active.progress, amount)) {
                player.sendActionBar(Text.component("&d✿ " + def.name() + " &8» &f" + active.progress + "&7/&f"
                        + def.amount()));
            }
        }
    }

    /** Don't spam the action bar: show it at about every 10% step. */
    private boolean showProgress(int goal, int now, int added) {
        int step = Math.max(1, goal / 10);
        return (now / step) != ((now - added) / step);
    }

    /** Adds to a lifetime stat and announces achievements that just became claimable. */
    public void addCounter(Player player, PlayerData data, String counter, long amount) {
        long before = data.counter(counter);
        data.addCounter(counter, amount);
        long after = data.counter(counter);
        for (AchievementDef ach : achievements.values()) {
            if (ach.counter().equals(counter) && !data.achievements.contains(ach.id())
                    && before < ach.goal() && after >= ach.goal()) {
                player.showTitle(Title.title(Text.component("&6&l★ Achievement! ★"),
                        Text.component("&f" + ach.name() + " &7- &d/quest &7to claim")));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
                player.sendMessage(plugin.msg("&6Achievement unlocked: &f" + ach.name()
                        + "&6! Claim it in &d/quest&6."));
            }
        }
    }

    // ------------------------------------------------------------------
    // Claiming
    // ------------------------------------------------------------------

    public boolean claimQuest(Player player, String scope, int index) {
        PlayerData data = plugin.getData().get(player);
        ensureCurrent(data);
        List<PlayerData.ActiveQuest> list = scope.equals("weekly") ? data.weekly : data.daily;
        if (index < 0 || index >= list.size()) {
            return false;
        }
        PlayerData.ActiveQuest active = list.get(index);
        QuestDef def = quest(scope, active.id);
        if (def == null) {
            player.sendMessage(plugin.msg("&cThat quest was removed from the server."));
            return false;
        }
        if (active.claimed) {
            player.sendMessage(plugin.msg("&7You already claimed this one."));
            return false;
        }
        if (active.progress < def.amount()) {
            player.sendMessage(plugin.msg("&cNot done yet: &f" + active.progress + "/" + def.amount()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return false;
        }
        active.claimed = true;
        data.dirty = true;
        plugin.getRewards().give(player, def.reward());
        player.sendMessage(plugin.msg("&aClaimed &f" + def.name() + "&a! &7(" + String.join("&7, ",
                plugin.getRewards().describe(def.reward())) + "&7)"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        addCounter(player, data, "quests", 1);
        return true;
    }

    public boolean claimAchievement(Player player, String id) {
        PlayerData data = plugin.getData().get(player);
        AchievementDef ach = achievements.get(id);
        if (ach == null) {
            return false;
        }
        if (data.achievements.contains(id)) {
            player.sendMessage(plugin.msg("&7You already claimed this achievement."));
            return false;
        }
        if (data.counter(ach.counter()) < ach.goal()) {
            player.sendMessage(plugin.msg("&cNot done yet: &f" + data.counter(ach.counter()) + "/" + ach.goal()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return false;
        }
        data.achievements.add(id);
        data.dirty = true;
        plugin.getRewards().give(player, ach.reward());
        player.sendMessage(plugin.msg("&6Achievement claimed: &f" + ach.name() + "&6! &7(" + String.join("&7, ",
                plugin.getRewards().describe(ach.reward())) + "&7)"));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        return true;
    }

    /** How many quests/achievements are ready to claim (for the menu badge and join message). */
    public int readyToClaim(PlayerData data) {
        ensureCurrent(data);
        int count = 0;
        for (PlayerData.ActiveQuest q : data.daily) {
            QuestDef def = quest("daily", q.id);
            if (def != null && !q.claimed && q.progress >= def.amount()) {
                count++;
            }
        }
        for (PlayerData.ActiveQuest q : data.weekly) {
            QuestDef def = quest("weekly", q.id);
            if (def != null && !q.claimed && q.progress >= def.amount()) {
                count++;
            }
        }
        for (AchievementDef ach : achievements.values()) {
            if (!data.achievements.contains(ach.id()) && data.counter(ach.counter()) >= ach.goal()) {
                count++;
            }
        }
        return count;
    }
}
