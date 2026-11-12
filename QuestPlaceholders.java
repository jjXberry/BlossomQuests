package com.blossomsmp.quests.hooks;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.data.PlayerData;
import com.blossomsmp.quests.quest.QuestDef;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * %blossomquests_daily_done%      2/3
 * %blossomquests_weekly_done%     1/3
 * %blossomquests_ready%           rewards ready to claim
 * %blossomquests_completed%       quests finished (lifetime)
 * %blossomquests_achievements%    5/20
 * %blossomquests_boss_kills%      Sakura Warden kills
 * %blossomquests_boss_status%     Awake! / Sleeping
 */
public class QuestPlaceholders extends PlaceholderExpansion {

    private final BlossomQuests plugin;

    public QuestPlaceholders(BlossomQuests plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "blossomquests";
    }

    @Override
    public String getAuthor() {
        return "BlossomSMP";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        try {
            if (params.equalsIgnoreCase("boss_status")) {
                return plugin.getBoss().isAlive() ? "&c&lAwake!" : "&7Sleeping";
            }
            if (!(player instanceof Player online)) {
                return "";
            }
            PlayerData data = plugin.getData().cached(online.getUniqueId());
            if (data == null) {
                return "";
            }
            return switch (params.toLowerCase()) {
                case "daily_done" -> done(data, "daily") + "/" + data.daily.size();
                case "weekly_done" -> done(data, "weekly") + "/" + data.weekly.size();
                case "ready" -> String.valueOf(plugin.getQuests().readyToClaim(data));
                case "completed" -> String.valueOf(data.counter("quests"));
                case "achievements" -> data.achievements.size() + "/" + plugin.getQuests().achievements().size();
                case "boss_kills" -> String.valueOf(data.counter("boss"));
                default -> null;
            };
        } catch (Exception e) {
            return "";
        }
    }

    private int done(PlayerData data, String scope) {
        int count = 0;
        for (PlayerData.ActiveQuest q : scope.equals("weekly") ? data.weekly : data.daily) {
            QuestDef def = plugin.getQuests().quest(scope, q.id);
            if (def != null && q.progress >= def.amount()) {
                count++;
            }
        }
        return count;
    }
}
