package com.blossomsmp.quests.data;

import com.blossomsmp.quests.BlossomQuests;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** One small file per player in plugins/BlossomQuests/players/. */
public class DataStore {

    private final BlossomQuests plugin;
    private final File folder;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public DataStore(BlossomQuests plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "players");
    }

    public PlayerData get(Player player) {
        PlayerData data = cache.computeIfAbsent(player.getUniqueId(), u -> load(u, player.getName()));
        data.name = player.getName();
        return data;
    }

    /** Only players that are loaded (online). */
    public PlayerData cached(UUID uuid) {
        return cache.get(uuid);
    }

    private PlayerData load(UUID uuid, String name) {
        PlayerData data = new PlayerData(uuid, name);
        File file = new File(folder, uuid + ".yml");
        if (!file.exists()) {
            return data;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        data.dailyKey = yaml.getString("daily.key", "");
        readQuests(yaml.getStringList("daily.quests"), data.daily);
        data.weeklyKey = yaml.getString("weekly.key", "");
        readQuests(yaml.getStringList("weekly.quests"), data.weekly);
        ConfigurationSection counters = yaml.getConfigurationSection("counters");
        if (counters != null) {
            for (String key : counters.getKeys(false)) {
                data.counters.put(key, counters.getLong(key, 0));
            }
        }
        data.achievements.addAll(yaml.getStringList("achievements"));
        return data;
    }

    private void readQuests(List<String> lines, List<PlayerData.ActiveQuest> into) {
        for (String line : lines) {
            String[] parts = line.split(";");
            if (parts.length < 3) {
                continue;
            }
            try {
                into.add(new PlayerData.ActiveQuest(parts[0], Integer.parseInt(parts[1]), Boolean.parseBoolean(parts[2])));
            } catch (NumberFormatException ignored) {
                // skip a broken line
            }
        }
    }

    private List<String> writeQuests(List<PlayerData.ActiveQuest> quests) {
        List<String> lines = new ArrayList<>();
        for (PlayerData.ActiveQuest q : quests) {
            lines.add(q.id + ";" + q.progress + ";" + q.claimed);
        }
        return lines;
    }

    public void save(PlayerData data) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", data.name);
        yaml.set("daily.key", data.dailyKey);
        yaml.set("daily.quests", writeQuests(data.daily));
        yaml.set("weekly.key", data.weeklyKey);
        yaml.set("weekly.quests", writeQuests(data.weekly));
        for (Map.Entry<String, Long> entry : data.counters.entrySet()) {
            yaml.set("counters." + entry.getKey(), entry.getValue());
        }
        yaml.set("achievements", new ArrayList<>(data.achievements));
        try {
            if (!folder.exists()) {
                folder.mkdirs();
            }
            yaml.save(new File(folder, data.uuid + ".yml"));
            data.dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save quest data for " + data.name, e);
        }
    }

    public void saveDirty() {
        for (PlayerData data : cache.values()) {
            if (data.dirty) {
                save(data);
            }
        }
    }

    public void saveAll() {
        for (PlayerData data : cache.values()) {
            save(data);
        }
    }

    public void unload(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null) {
            save(data);
        }
    }
}
