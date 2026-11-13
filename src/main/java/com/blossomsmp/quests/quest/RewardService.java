package com.blossomsmp.quests.quest;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.util.Text;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pays rewards: money (Vault / BlossomEconomy), Petals and Blossom Points (by console command), items. */
public class RewardService {

    private final BlossomQuests plugin;

    public RewardService(BlossomQuests plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Money (Vault). Only touched when Vault is installed.
    // ------------------------------------------------------------------

    private Object economy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        try {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            return rsp == null ? null : rsp.getProvider();
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean hasEconomy() {
        return economy() != null;
    }

    public double balance(OfflinePlayer player) {
        Object eco = economy();
        return eco == null ? 0 : ((Economy) eco).getBalance(player);
    }

    public boolean take(OfflinePlayer player, double amount) {
        Object eco = economy();
        if (eco == null || !((Economy) eco).has(player, amount)) {
            return false;
        }
        return ((Economy) eco).withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean give(OfflinePlayer player, double amount) {
        Object eco = economy();
        return eco != null && ((Economy) eco).depositPlayer(player, amount).transactionSuccess();
    }

    // ------------------------------------------------------------------
    // Rewards
    // ------------------------------------------------------------------

    public void give(Player player, Reward reward) {
        if (reward.money() > 0) {
            if (!give(player, reward.money())) {
                plugin.getLogger().warning("Could not pay " + Text.money(reward.money()) + " to " + player.getName()
                        + " (is Vault + BlossomEconomy installed?)");
            }
        }
        if (reward.petals() > 0) {
            runCommand(plugin.getConfig().getString("rewards.petals-command", "petals give %player% %amount%"),
                    player, reward.petals());
        }
        if (reward.points() > 0 && plugin.getConfig().getBoolean("rewards.points-enabled", true)
                && Bukkit.getPluginManager().getPlugin("BlossomCore") != null) {
            runCommand(plugin.getConfig().getString("rewards.points-command",
                    "blossomcore points give %player% %amount%"), player, reward.points());
        }
        for (String entry : reward.items()) {
            ItemStack item = parseItem(entry);
            if (item != null) {
                giveItem(player, item);
            }
        }
        for (String command : reward.commands()) {
            runCommand(command, player, 0);
        }
    }

    private void runCommand(String command, Player player, long amount) {
        if (command == null || command.isBlank()) {
            return;
        }
        String line = command.replace("%player%", player.getName()).replace("%amount%", String.valueOf(amount));
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
    }

    /** "sigil:2", "reaper", "DIAMOND:5" */
    public ItemStack parseItem(String entry) {
        String[] parts = entry.trim().split(":");
        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Math.max(1, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        String id = parts[0].trim();
        ItemStack custom = plugin.getItems().create(id);
        if (custom != null) {
            if (plugin.getItems().weaponOf(custom) == null) {
                custom.setAmount(Math.min(amount, 64));
            }
            return custom;
        }
        Material material = Material.matchMaterial(id);
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("Unknown reward item: " + entry);
            return null;
        }
        return new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
    }

    /** Gives an item, dropping it at their feet if the inventory is full. */
    public void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(item);
        for (ItemStack rest : left.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    /** Lines for item lore, e.g. "&a$1,000", "&d✿ 50 Petals". */
    public List<String> describe(Reward reward) {
        List<String> lines = new ArrayList<>();
        if (reward.money() > 0) {
            lines.add("&a" + Text.money(reward.money()));
        }
        if (reward.petals() > 0) {
            lines.add("&d✿ " + String.format(Locale.US, "%,d", reward.petals()) + " Petals");
        }
        if (reward.points() > 0 && Bukkit.getPluginManager().getPlugin("BlossomCore") != null) {
            lines.add("&e+" + reward.points() + " Blossom Points");
        }
        for (String entry : reward.items()) {
            String[] parts = entry.split(":");
            String amount = parts.length > 1 ? parts[1].trim() + "x " : "";
            String id = parts[0].trim();
            if (plugin.getItems().resolve(id) != null) {
                lines.add("&f" + amount + plugin.getItems().displayName(id));
            } else {
                lines.add("&f" + amount + QuestDef.pretty(id));
            }
        }
        if (!reward.commands().isEmpty() && lines.isEmpty()) {
            lines.add("&fA surprise!");
        }
        return lines;
    }
}
