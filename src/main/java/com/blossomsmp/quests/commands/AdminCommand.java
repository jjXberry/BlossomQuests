package com.blossomsmp.quests.commands;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /bq - staff commands. */
public class AdminCommand implements TabExecutor {

    private final BlossomQuests plugin;

    public AdminCommand(BlossomQuests plugin) {
        this.plugin = plugin;
    }

    private void send(CommandSender s, String text) {
        s.sendMessage(plugin.msg(text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("blossomquests.admin")) {
            send(sender, "&cYou don't have permission.");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getQuests().load();
                send(sender, "&aBlossomQuests reloaded (config.yml and quests.yml).");
            }
            case "boss" -> boss(sender, args);
            case "give" -> {
                if (args.length < 3) {
                    send(sender, "&cUse: /bq give <player> <reaper|sigil> [amount]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    send(sender, "&c" + args[1] + " isn't online.");
                    return true;
                }
                int amount = 1;
                if (args.length > 3) {
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ignored) {
                        amount = 1;
                    }
                }
                ItemStack item = plugin.getRewards().parseItem(args[2] + ":" + amount);
                if (item == null) {
                    send(sender, "&cUnknown item. Use reaper or sigil.");
                    return true;
                }
                plugin.getRewards().giveItem(target, item);
                send(sender, "&aGave " + target.getName() + " " + args[2] + ".");
            }
            case "reset" -> {
                if (args.length < 2) {
                    send(sender, "&cUse: /bq reset <player>  &7(gives them new daily + weekly quests)");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    send(sender, "&c" + args[1] + " isn't online.");
                    return true;
                }
                PlayerData data = plugin.getData().get(target);
                plugin.getQuests().reroll(data);
                send(sender, "&a" + target.getName() + " got new daily and weekly quests.");
            }
            case "save" -> {
                plugin.getData().saveAll();
                plugin.getBoard().save();
                send(sender, "&aSaved.");
            }
            default -> {
                send(sender, "&#FF69B4&lBlossomQuests staff commands:");
                send(sender, "&f/bq reload &7- reload config.yml and quests.yml");
                send(sender, "&f/bq boss spawn &7- spawn the Sakura Warden where you stand");
                send(sender, "&f/bq boss kill &7- remove her (no rewards)");
                send(sender, "&f/bq boss setspot &7- where she auto-spawns");
                send(sender, "&f/bq give <player> <reaper|sigil> [amount]");
                send(sender, "&f/bq reset <player> &7- new quests for that player");
                send(sender, "&f/bq save");
            }
        }
        return true;
    }

    private void boss(CommandSender sender, String[] args) {
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (what) {
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "&cStand where she should spawn and run this in game.");
                    return;
                }
                if (plugin.getBoss().isAlive()) {
                    send(sender, "&cShe's already awake. /bq boss kill first.");
                    return;
                }
                if (!plugin.getBoss().spawn(player.getLocation(), null)) {
                    send(sender, "&cShe couldn't spawn here (check the console).");
                }
            }
            case "kill", "remove" -> {
                plugin.getBoss().despawn();
                send(sender, "&aThe Sakura Warden was removed.");
            }
            case "setspot" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "&cRun this in game.");
                    return;
                }
                Location l = player.getLocation();
                plugin.getConfig().set("boss.auto-spawn.world", l.getWorld().getName());
                plugin.getConfig().set("boss.auto-spawn.x", l.getBlockX() + 0.5);
                plugin.getConfig().set("boss.auto-spawn.y", l.getBlockY());
                plugin.getConfig().set("boss.auto-spawn.z", l.getBlockZ() + 0.5);
                plugin.saveConfig();
                send(sender, "&aAuto-spawn spot saved. Turn it on with &fboss.auto-spawn.enabled: true &ain config.yml, then /bq reload.");
            }
            default -> send(sender, "&cUse: /bq boss <spawn|kill|setspot>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (!sender.hasPermission("blossomquests.admin")) {
            return options;
        }
        if (args.length == 1) {
            options.addAll(List.of("reload", "boss", "give", "reset", "save"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("boss")) {
            options.addAll(List.of("spawn", "kill", "setspot"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            options.addAll(List.of("reaper", "sigil"));
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return options;
    }
}
