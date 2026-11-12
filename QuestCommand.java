package com.blossomsmp.quests.commands;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /quest [daily|weekly|achievements|board|boss|post|collect] */
public class QuestCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("daily", "weekly", "achievements", "board", "boss", "post", "collect");

    private final BlossomQuests plugin;

    public QuestCommand(BlossomQuests plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("&cOnly players can use /quest. Staff: /bq"));
            return true;
        }
        if (args.length == 0) {
            plugin.getMenus().openMain(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "daily" -> plugin.getMenus().openDaily(player);
            case "weekly" -> plugin.getMenus().openWeekly(player);
            case "achievements", "ach" -> plugin.getMenus().openAchievements(player, 0);
            case "board" -> plugin.getMenus().openBoard(player, 0);
            case "boss" -> plugin.getMenus().openBoss(player);
            case "collect", "mail" -> plugin.getBoard().collect(player);
            case "post" -> {
                // /quest post <amount> <reward> while holding the item
                if (args.length < 3) {
                    player.sendMessage(plugin.msg("&cHold an item and type &f/quest post <amount> <reward>"
                            + " &7(like /quest post 64 2k)"));
                    return true;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                int amount;
                try {
                    amount = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.msg("&cThe amount must be a number."));
                    return true;
                }
                Double reward = Text.parseAmount(args[2]);
                if (reward == null) {
                    player.sendMessage(plugin.msg("&cThat reward isn't a valid amount."));
                    return true;
                }
                plugin.getBoard().post(player, hand.getType(), amount, reward);
            }
            default -> plugin.getMenus().openMain(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
