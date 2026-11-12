package com.blossomsmp.quests.menus;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.board.BoardManager;
import com.blossomsmp.quests.data.PlayerData;
import com.blossomsmp.quests.quest.AchievementDef;
import com.blossomsmp.quests.quest.QuestDef;
import com.blossomsmp.quests.quest.QuestManager;
import com.blossomsmp.quests.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** All the pink /quest menus. */
public class Menus {

    private static final String TITLE = "&#FF1493&l❀ &#FF69B4&l";
    private static final String NAME = "&#FF69B4&l";
    private static final String CLICK = "&#FFB6C1» ";

    private final BlossomQuests plugin;

    public Menus(BlossomQuests plugin) {
        this.plugin = plugin;
    }

    private QuestManager quests() {
        return plugin.getQuests();
    }

    // ------------------------------------------------------------------
    // Building blocks
    // ------------------------------------------------------------------

    private MenuHolder create(int rows, String title) {
        MenuHolder holder = new MenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, Text.component(title));
        holder.setInventory(inventory);
        for (int slot = 0; slot < rows * 9; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                inventory.setItem(slot, item((row + col) % 2 == 0 ? Material.PINK_STAINED_GLASS_PANE
                        : Material.MAGENTA_STAINED_GLASS_PANE, " "));
            }
        }
        return holder;
    }

    private static List<Integer> inner(int rows) {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row < rows - 1; row++) {
            for (int col = 1; col < 8; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }

    static ItemStack item(Material material, String name, String... lore) {
        return item(material, name, Arrays.asList(lore));
    }

    static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.item(name));
            if (!lore.isEmpty()) {
                List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
                for (String line : lore) {
                    lines.add(Text.item(line));
                }
                meta.lore(lines);
            }
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    static ItemStack glow(ItemStack item, boolean glow) {
        if (glow) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(true);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private ItemStack back() {
        return item(Material.ARROW, "&#FFB6C1&l« Back");
    }

    private void open(Player player, MenuHolder holder) {
        player.openInventory(holder.getInventory());
    }

    /** ■■■■■□□□□□ 50% */
    private String bar(long now, long goal) {
        int filled = goal <= 0 ? 10 : (int) Math.min(10, now * 10 / goal);
        StringBuilder b = new StringBuilder("&#FF69B4");
        for (int i = 0; i < 10; i++) {
            if (i == filled) {
                b.append("&8");
            }
            b.append('■');
        }
        return b + " &f" + Math.min(now, goal) + "&7/&f" + goal;
    }

    private String time(long seconds) {
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        if (d > 0) {
            return d + "d " + h + "h";
        }
        if (h > 0) {
            return h + "h " + m + "m";
        }
        return Math.max(1, m) + "m";
    }

    private List<String> rewardLines(com.blossomsmp.quests.quest.Reward reward) {
        List<String> lines = new ArrayList<>();
        lines.add("&7Rewards:");
        for (String line : plugin.getRewards().describe(reward)) {
            lines.add(" &8• " + line);
        }
        return lines;
    }

    // ------------------------------------------------------------------
    // Main menu
    // ------------------------------------------------------------------

    public void openMain(Player player) {
        PlayerData data = plugin.getData().get(player);
        quests().ensureCurrent(data);
        MenuHolder h = create(5, TITLE + "Blossom Quests");

        int ready = quests().readyToClaim(data);
        ItemStack head = item(Material.PLAYER_HEAD, "&f&l" + player.getName(),
                "&7Quests finished: &f" + data.counter("quests"),
                "&7Achievements: &f" + data.achievements.size() + "&7/&f" + quests().achievements().size(),
                "&7Sakura Warden kills: &f" + data.counter("boss"),
                "&7Board deliveries: &f" + data.counter("board"),
                "",
                ready > 0 ? "&a&l" + ready + " reward" + (ready == 1 ? "" : "s") + " ready to claim!" : "&7Nothing to claim right now");
        if (head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            head.setItemMeta(skull);
        }
        h.set(4, head, null);

        int dailyReady = countReady(data.daily, "daily");
        h.set(20, glow(item(Material.CLOCK, NAME + "☀ Daily Quests",
                "&7" + data.daily.size() + " new quests every day.",
                "&7Earn &a$100 - $1,000+ &7and &dPetals&7.", "",
                "&7New quests in &f" + time(quests().secondsUntilDailyReset()),
                dailyReady > 0 ? "&a&l" + dailyReady + " ready to claim!" : "", "", CLICK + "Click to open"),
                dailyReady > 0), this::openDaily);

        int weeklyReady = countReady(data.weekly, "weekly");
        h.set(21, glow(item(Material.WRITABLE_BOOK, NAME + "✦ Weekly Quests",
                "&7Harder quests, bigger rewards.",
                "&7Earn &a$3,000 - $15,000 &7and lots of &dPetals&7.", "",
                "&7New quests in &f" + time(quests().secondsUntilWeeklyReset()),
                weeklyReady > 0 ? "&a&l" + weeklyReady + " ready to claim!" : "", "", CLICK + "Click to open"),
                weeklyReady > 0), this::openWeekly);

        h.set(22, item(Material.GOLDEN_APPLE, NAME + "★ Achievements",
                "&7Big goals you finish once.",
                "&7Claimed: &f" + data.achievements.size() + "&7/&f" + quests().achievements().size(), "",
                CLICK + "Click to open"), p -> openAchievements(p, 0));

        int mail = plugin.getBoard().mailboxCount(player.getUniqueId());
        h.set(23, glow(item(Material.OAK_HANGING_SIGN, NAME + "☰ Quest Board",
                "&7Quests posted by other players.",
                "&7Bring them items, get paid!",
                "&7Or post your own request.", "",
                "&7Open requests: &f" + plugin.getBoard().all().size(),
                mail > 0 ? "&a&lYou have " + mail + " delivered items!" : "", "",
                CLICK + "Click to open"), mail > 0), p -> openBoard(p, 0));

        h.set(24, item(Material.WITHER_SKELETON_SKULL, "&#FF1493&l❀ Sakura Warden",
                "&7The boss of Blossom SMP.", "",
                plugin.getBoss().isAlive() ? "&c&lAWAKE NOW!" : "&7Sleeping...", "",
                CLICK + "Click for info"), this::openBoss);

        h.set(40, item(Material.BARRIER, "&c&lClose"), Player::closeInventory);
        open(player, h);
    }

    private int countReady(List<PlayerData.ActiveQuest> list, String scope) {
        int count = 0;
        for (PlayerData.ActiveQuest q : list) {
            QuestDef def = quests().quest(scope, q.id);
            if (def != null && !q.claimed && q.progress >= def.amount()) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Daily / weekly
    // ------------------------------------------------------------------

    public void openDaily(Player player) {
        openQuests(player, "daily");
    }

    public void openWeekly(Player player) {
        openQuests(player, "weekly");
    }

    private void openQuests(Player player, String scope) {
        PlayerData data = plugin.getData().get(player);
        quests().ensureCurrent(data);
        boolean weekly = scope.equals("weekly");
        List<PlayerData.ActiveQuest> list = weekly ? data.weekly : data.daily;
        MenuHolder h = create(4, TITLE + (weekly ? "Weekly Quests" : "Daily Quests"));
        int[] slots = list.size() <= 3 ? new int[]{11, 13, 15} : new int[]{10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < list.size() && i < slots.length; i++) {
            PlayerData.ActiveQuest active = list.get(i);
            QuestDef def = quests().quest(scope, active.id);
            if (def == null) {
                continue;
            }
            boolean done = active.progress >= def.amount();
            List<String> lore = new ArrayList<>();
            List<String> desc = def.description().isEmpty() ? List.of(def.autoDescription()) : def.description();
            for (String line : desc) {
                lore.add("&7" + line);
            }
            lore.add("");
            lore.add(bar(active.progress, def.amount()));
            lore.add("");
            lore.addAll(rewardLines(def.reward()));
            lore.add("");
            if (active.claimed) {
                lore.add("&a✔ Claimed");
            } else if (done) {
                lore.add("&a&l» Click to claim!");
            } else {
                lore.add("&7Keep going!");
            }
            Material icon = active.claimed ? Material.LIME_STAINED_GLASS_PANE : def.icon();
            final int index = i;
            h.set(slots[i], glow(item(icon, (active.claimed ? "&a&m" : NAME) + def.name(), lore), done && !active.claimed),
                    done && !active.claimed ? p -> {
                        quests().claimQuest(p, scope, index);
                        openQuests(p, scope);
                    } : null);
        }
        long left = weekly ? quests().secondsUntilWeeklyReset() : quests().secondsUntilDailyReset();
        h.set(31, item(Material.CLOCK, "&7New quests in &f" + time(left)), null);
        h.set(27, back(), this::openMain);
        open(player, h);
    }

    // ------------------------------------------------------------------
    // Achievements
    // ------------------------------------------------------------------

    public void openAchievements(Player player, int page) {
        PlayerData data = plugin.getData().get(player);
        List<AchievementDef> all = quests().achievements();
        MenuHolder h = create(6, TITLE + "Achievements");
        List<Integer> slots = inner(6);
        int start = page * slots.size();
        for (int i = 0; i < slots.size() && start + i < all.size(); i++) {
            AchievementDef ach = all.get(start + i);
            long now = data.counter(ach.counter());
            boolean claimed = data.achievements.contains(ach.id());
            boolean done = now >= ach.goal();
            List<String> lore = new ArrayList<>();
            if (!ach.description().isEmpty()) {
                lore.add("&7" + ach.description());
                lore.add("");
            }
            lore.add(bar(now, ach.goal()));
            lore.add("");
            lore.addAll(rewardLines(ach.reward()));
            lore.add("");
            lore.add(claimed ? "&a✔ Claimed" : done ? "&a&l» Click to claim!" : "&7Not done yet");
            Material icon = claimed ? Material.LIME_DYE : ach.icon();
            h.set(slots.get(i), glow(item(icon, (claimed ? "&a" : done ? "&6&l" : "&f") + ach.name(), lore),
                    done && !claimed), done && !claimed ? p -> {
                        quests().claimAchievement(p, ach.id());
                        openAchievements(p, page);
                    } : null);
        }
        pages(h, 6, page, all.size(), slots.size(), p -> openAchievements(p, page - 1),
                p -> openAchievements(p, page + 1));
        h.set(49, back(), this::openMain);
        open(player, h);
    }

    // ------------------------------------------------------------------
    // Quest Board
    // ------------------------------------------------------------------

    public void openBoard(Player player, int page) {
        BoardManager board = plugin.getBoard();
        List<BoardManager.Request> all = board.all();
        MenuHolder h = create(6, TITLE + "Quest Board");
        List<Integer> slots = inner(6);
        int start = page * slots.size();
        if (all.isEmpty()) {
            h.set(22, item(Material.PAPER, "&7No requests yet",
                    "&7Be the first! Hold an item and", "&7click &fPost a Request &7below."), null);
        }
        for (int i = 0; i < slots.size() && start + i < all.size(); i++) {
            BoardManager.Request r = all.get(start + i);
            boolean mine = r.owner().equals(player.getUniqueId());
            boolean staff = player.hasPermission("blossomquests.admin");
            ItemStack icon = item(r.material(), "&f&l" + r.amount() + "x " + r.itemName(),
                    "&7Wanted by &f" + r.ownerName(),
                    "&7Reward: &a&l" + Text.money(r.reward()),
                    "&7Ends in &f" + BoardManager.timeLeft(r.expires() - System.currentTimeMillis()),
                    "",
                    mine ? "&cClick to cancel &7(you get your money back)"
                            : "&a&l» Click to deliver!",
                    mine ? "" : "&8Only plain items count (no names/enchants)",
                    !mine && staff ? "&8Staff: right-click to remove" : "");
            icon.setAmount(Math.max(1, Math.min(64, r.amount())));
            String id = r.id();
            h.set(slots.get(i), icon, mine ? p -> confirm(p, "Cancel your request?",
                    item(r.material(), "&f" + r.amount() + "x " + r.itemName(), "&7You get " + Text.money(r.reward())
                            + " back."),
                    yes -> {
                        plugin.getBoard().cancel(yes, id);
                        openBoard(yes, page);
                    }, no -> openBoard(no, page))
                    : p -> {
                        plugin.getBoard().deliver(p, id);
                        openBoard(p, page);
                    });
            if (!mine && staff) {
                h.setRightClick(slots.get(i), p -> {
                    plugin.getBoard().cancel(p, id);
                    openBoard(p, page);
                });
            }
        }
        int max = plugin.getConfig().getInt("board.max-requests-per-player", 3);
        h.set(47, item(Material.WRITABLE_BOOK, "&a&l+ Post a Request",
                "&7Hold the item you want in your hand,",
                "&7then click here. You'll type how many",
                "&7you want and how much you'll pay.", "",
                "&7Your requests: &f" + board.countFor(player.getUniqueId()) + "&7/&f" + max,
                "&7The money is held until someone delivers.", "",
                CLICK + "Click to post"), this::startPost);
        int mail = board.mailboxCount(player.getUniqueId());
        StringBuilder mailLore = new StringBuilder();
        for (Map.Entry<Material, Integer> e : board.mailboxOf(player.getUniqueId()).entrySet()) {
            mailLore.append(e.getValue()).append("x ").append(QuestDef.pretty(e.getKey().name())).append(", ");
        }
        h.set(51, glow(item(Material.CHEST, "&#FFB6C1&l✉ Your Mailbox",
                mail > 0 ? "&7Delivered to you: &f" + mail + " items" : "&7Nothing delivered yet.",
                mail > 0 ? "&8" + trim(mailLore.toString()) : "",
                "", mail > 0 ? "&a&l» Click to collect!" : ""), mail > 0),
                mail > 0 ? p -> {
                    plugin.getBoard().collect(p);
                    openBoard(p, page);
                } : null);
        pages(h, 6, page, all.size(), slots.size(), p -> openBoard(p, page - 1), p -> openBoard(p, page + 1));
        h.set(49, back(), this::openMain);
        open(player, h);
    }

    private String trim(String s) {
        if (s.endsWith(", ")) {
            s = s.substring(0, s.length() - 2);
        }
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }

    /** Asks for the amount and reward in chat, then posts. */
    private void startPost(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.closeInventory();
            player.sendMessage(plugin.msg("&cHold the item you want people to bring you, then try again."));
            return;
        }
        Material material = hand.getType();
        String itemName = QuestDef.pretty(material.name());
        plugin.getPrompts().ask(player, "&#FF69B4✿ &fHow many &d" + itemName + " &fdo you want? &7(1 - "
                + plugin.getConfig().getInt("board.max-amount", 1728) + ")", amountText -> {
            int amount;
            try {
                amount = Integer.parseInt(amountText.replace(",", "").trim());
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.msg("&cThat isn't a number."));
                return;
            }
            plugin.getPrompts().ask(player, "&#FF69B4✿ &fHow much will you pay for &d" + amount + "x " + itemName
                    + "&f? &7(like 500, 2k, 10k)", rewardText -> {
                Double reward = Text.parseAmount(rewardText);
                if (reward == null) {
                    player.sendMessage(plugin.msg("&cThat isn't a valid amount."));
                    return;
                }
                if (plugin.getBoard().post(player, material, amount, reward)) {
                    openBoard(player, 0);
                }
            });
        });
    }

    // ------------------------------------------------------------------
    // Boss info
    // ------------------------------------------------------------------

    public void openBoss(Player player) {
        MenuHolder h = create(4, TITLE + "Sakura Warden");
        LivingEntity boss = plugin.getBoss().boss();
        if (boss != null) {
            Location l = boss.getLocation();
            h.set(13, glow(item(Material.WITHER_SKELETON_SKULL, "&c&l❀ The Sakura Warden is AWAKE!",
                    "&7Location: &f" + l.getWorld().getName() + " " + l.getBlockX() + ", " + l.getBlockY() + ", "
                            + l.getBlockZ(),
                    "&7Health: &c" + (int) Math.ceil(boss.getHealth()) + " ❤", "",
                    "&7Go fight her for rewards!"), true), null);
        } else {
            long next = plugin.getBoss().nextAutoSpawn();
            h.set(13, item(Material.SKELETON_SKULL, "&#FF69B4&l❀ The Sakura Warden sleeps",
                    next > 0 ? "&7She wakes up in about &f" + time(Math.max(0, (next - System.currentTimeMillis()) / 1000))
                            : "&7Summon her with a &dSakura Sigil&7.",
                    "&7(right-click the ground with it)"), null);
        }
        h.set(10, item(Material.PINK_PETALS, "&#FF69B4&lHer attacks",
                "&d• Petal Storm &7- hurts and slows everyone close",
                "&d• Thornlings &7- calls pink zombie helpers",
                "&d• Blossom Leap &7- jumps at the furthest player",
                "&5• Wither Bloom &7- only when enraged (below half health)"), null);
        h.set(11, item(Material.GOLD_INGOT, "&6&lRewards",
                "&7Everyone who does at least &f" + (int) plugin.getConfig().getDouble("boss.min-damage-percent", 5)
                        + "% &7of the damage gets:",
                " &8• " + String.join("&8, ", plugin.getRewards().describe(
                        com.blossomsmp.quests.quest.Reward.from(plugin.getConfig()
                                .getConfigurationSection("boss.rewards.participant")))),
                "",
                "&7The &6top damager &7also gets a bonus and a",
                "&f" + plugin.getConfig().getInt("boss.reaper-chance", 35) + "% &7chance at the &c&lPetal Reaper&7.",
                "&7Everyone has a &f" + plugin.getConfig().getInt("boss.sigil-chance", 10)
                        + "% &7chance at a &dSakura Sigil&7."), null);
        h.set(15, plugin.getItems().reaper(), null);
        h.set(16, plugin.getItems().sigil(), null);
        h.set(27, back(), this::openMain);
        open(player, h);
    }

    // ------------------------------------------------------------------
    // Confirm and pages
    // ------------------------------------------------------------------

    public void confirm(Player player, String question, ItemStack info, Consumer<Player> yes, Consumer<Player> no) {
        MenuHolder h = create(3, TITLE + question);
        h.set(11, item(Material.LIME_CONCRETE, "&a&l✔ Yes"), yes);
        h.set(13, info, null);
        h.set(15, item(Material.RED_CONCRETE, "&c&l✖ No"), no);
        open(player, h);
    }

    private void pages(MenuHolder h, int rows, int page, int total, int perPage,
                       Consumer<Player> previous, Consumer<Player> next) {
        int bottom = (rows - 1) * 9;
        if (page > 0) {
            h.set(bottom, item(Material.ARROW, "&#FFB6C1&l« Page " + page), previous);
        }
        if ((page + 1) * perPage < total) {
            h.set(bottom + 8, item(Material.ARROW, "&#FFB6C1&lPage " + (page + 2) + " »"), next);
        }
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
