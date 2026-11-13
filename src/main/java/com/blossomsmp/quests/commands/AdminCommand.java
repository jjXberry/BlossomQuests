package com.blossomsmp.quests.commands;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.boss.BossType;
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
                    send(sender, "&cUse: /bq give <player> <item> [amount]  &7Items: reaper, cleaver, frostfang, sigil, ember_sigil, frost_sigil");
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
                    send(sender, "&cUnknown item. Use reaper, cleaver, frostfang, sigil, ember_sigil or frost_sigil.");
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
            case "progress" -> progress(sender, args);
            case "save" -> {
                plugin.getData().saveAll();
                plugin.getBoard().save();
                send(sender, "&aSaved.");
            }
            default -> {
                send(sender, "&#FF69B4&lBlossomQuests staff commands:");
                send(sender, "&f/bq reload &7- reload config.yml and quests.yml");
                send(sender, "&f/bq boss spawn <sakura|ember|frost> &7- spawn a boss where you stand");
                send(sender, "&f/bq boss kill [sakura|ember|frost|all] &7- remove bosses (no rewards)");
                send(sender, "&f/bq boss setspot &7- where a random boss auto-spawns");
                send(sender, "&f/bq give <player> <reaper|cleaver|frostfang|sigil|ember_sigil|frost_sigil> [amount]");
                send(sender, "&f/bq reset <player> &7- new quests for that player");
                send(sender, "&f/bq progress <player> <type> <target> [amount] &7- count quest progress (used by BlossomBiomes)");
                send(sender, "&f/bq save");
            }
        }
        return true;
    }

    /**
     * /bq progress <player> <type> <target> [amount]
     * Used by other Blossom plugins (BlossomBiomes) to count quest progress. Silent when it works.
     */
    private void progress(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "&cUse: /bq progress <player> <DISCOVER|GATHER|BIOME_BOSS|...> <target> [amount]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            return; // they logged off: nothing to count
        }
        com.blossomsmp.quests.quest.QuestType type;
        try {
            type = com.blossomsmp.quests.quest.QuestType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            send(sender, "&cUnknown quest type: " + args[2]);
            return;
        }
        int amount = 1;
        if (args.length > 4) {
            try {
                amount = Math.max(1, Math.min(10_000, Integer.parseInt(args[4])));
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        String what = args[3].toUpperCase(Locale.ROOT);
        plugin.getQuests().progress(target, type, what, false, amount);
        // per-target stat too, for achievements like "beat the Frostmaw" (counter: biome_boss_frostmaw)
        plugin.getQuests().addCounter(target, plugin.getData().get(target),
                type.counter() + "_" + what.toLowerCase(Locale.ROOT), amount);
    }

    private void boss(CommandSender sender, String[] args) {
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        String which = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
        switch (what) {
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "&cStand where the boss should spawn and run this in game.");
                    return;
                }
                BossType type = plugin.getBoss().type(which.isEmpty() ? "sakura" : which);
                if (type == null) {
                    send(sender, "&cUse: /bq boss spawn <sakura|ember|frost>");
                    return;
                }
                if (plugin.getBoss().active(type.id()) != null) {
                    send(sender, "&cThe " + type.plainName() + " is already awake. /bq boss kill " + type.id() + " first.");
                    return;
                }
                if (!plugin.getBoss().spawn(type, player.getLocation(), null)) {
                    send(sender, "&cIt couldn't spawn here (check the console).");
                }
            }
            case "kill", "remove" -> {
                int removed = plugin.getBoss().despawn(which.isEmpty() || which.equals("all") ? null : which);
                send(sender, removed > 0 ? "&aRemoved " + removed + " boss(es)." : "&7No boss to remove.");
            }
            case "setspot" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "&cRun this in game.");
                    return;
                }
                Location l = player.getLocation();
                plugin.getConfig().set("boss-settings.auto-spawn.world", l.getWorld().getName());
                plugin.getConfig().set("boss-settings.auto-spawn.x", l.getBlockX() + 0.5);
                plugin.getConfig().set("boss-settings.auto-spawn.y", l.getBlockY());
                plugin.getConfig().set("boss-settings.auto-spawn.z", l.getBlockZ() + 0.5);
                plugin.saveConfig();
                send(sender, "&aAuto-spawn spot saved. Turn it on with &fboss-settings.auto-spawn.enabled: true &ain config.yml, then /bq reload.");
            }
            default -> send(sender, "&cUse: /bq boss <spawn|kill|setspot> [sakura|ember|frost]");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (!sender.hasPermission("blossomquests.admin")) {
            return options;
        }
        if (args.length == 1) {
            options.addAll(List.of("reload", "boss", "give", "reset", "progress", "save"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("boss")) {
            options.addAll(List.of("spawn", "kill", "setspot"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            options.addAll(List.of("reaper", "cleaver", "frostfang", "sigil", "ember_sigil", "frost_sigil"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("boss")) {
            options.addAll(List.of("sakura", "ember", "frost"));
            if (args[1].equalsIgnoreCase("kill")) {
                options.add("all");
            }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return options;
    }
}
