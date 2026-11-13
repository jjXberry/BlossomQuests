package com.blossomsmp.quests;

import com.blossomsmp.quests.board.BoardManager;
import com.blossomsmp.quests.boss.BossManager;
import com.blossomsmp.quests.commands.AdminCommand;
import com.blossomsmp.quests.commands.QuestCommand;
import com.blossomsmp.quests.data.DataStore;
import com.blossomsmp.quests.items.CustomItems;
import com.blossomsmp.quests.listeners.ChatListener;
import com.blossomsmp.quests.listeners.WeaponListener;
import com.blossomsmp.quests.listeners.MenuListener;
import com.blossomsmp.quests.listeners.ProgressListener;
import com.blossomsmp.quests.menus.MenuHolder;
import com.blossomsmp.quests.menus.Menus;
import com.blossomsmp.quests.quest.QuestManager;
import com.blossomsmp.quests.quest.RewardService;
import com.blossomsmp.quests.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * BlossomQuests - daily & weekly quests, achievements, the player Quest Board,
 * the Sakura Warden boss and the Petal Reaper.
 */
public final class BlossomQuests extends JavaPlugin {

    private DataStore data;
    private QuestManager quests;
    private RewardService rewards;
    private BoardManager board;
    private BossManager boss;
    private CustomItems items;
    private Menus menus;
    private ChatPrompts prompts;
    private WeaponListener weaponListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Add new settings from updates to an existing config.yml
        getConfig().options().copyDefaults(true);
        saveConfig();

        items = new CustomItems(this);
        data = new DataStore(this);
        rewards = new RewardService(this);
        quests = new QuestManager(this);
        quests.load();
        board = new BoardManager(this);
        board.load();
        boss = new BossManager(this);
        menus = new Menus(this);
        prompts = new ChatPrompts(this);

        register("quest", new QuestCommand(this));
        register("bq", new AdminCommand(this));

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new ProgressListener(this), this);
        weaponListener = new WeaponListener(this);
        pm.registerEvents(weaponListener, this);
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(boss, this);

        // Load players already online (after /reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            data.get(player);
        }

        // Save changed players every 2 minutes
        getServer().getScheduler().runTaskTimer(this, () -> {
            data.saveDirty();
            board.saveIfDirty();
        }, 20L * 120, 20L * 120);

        if (!rewards.hasEconomy()) {
            getLogger().warning("No money plugin found through Vault. Money rewards and the Quest Board won't work"
                    + " until BlossomEconomy + Vault are installed.");
        } else {
            getLogger().info("Hooked into Vault (money rewards are on).");
        }
        if (pm.getPlugin("PlaceholderAPI") != null) {
            try {
                new com.blossomsmp.quests.hooks.QuestPlaceholders(this).register();
                getLogger().info("Hooked into PlaceholderAPI (%blossomquests_...% placeholders).");
            } catch (Throwable t) {
                getLogger().warning("Could not register placeholders: " + t);
            }
        }
        getLogger().info("BlossomQuests is ready. ❀");
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) {
                player.closeInventory();
            }
        }
        if (boss != null) {
            boss.despawn(null);
        }
        if (data != null) {
            data.saveAll();
        }
        if (board != null) {
            board.save();
        }
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml!");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public Component msg(String text) {
        return Text.component(getConfig().getString("prefix", "&#FF69B4&l❀ &8» &r") + text);
    }

    public DataStore getData() {
        return data;
    }

    public QuestManager getQuests() {
        return quests;
    }

    public RewardService getRewards() {
        return rewards;
    }

    public BoardManager getBoard() {
        return board;
    }

    public BossManager getBoss() {
        return boss;
    }

    public CustomItems getItems() {
        return items;
    }

    public Menus getMenus() {
        return menus;
    }

    public ChatPrompts getPrompts() {
        return prompts;
    }

    public WeaponListener getWeaponListener() {
        return weaponListener;
    }
}
