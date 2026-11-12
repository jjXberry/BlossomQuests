package com.blossomsmp.quests.board;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.quest.QuestDef;
import com.blossomsmp.quests.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * The Quest Board: players post "bring me 64 iron for $2,000".
 * The reward is taken from the poster when they post (so it's always there),
 * paid to whoever delivers, and the items wait in the poster's mailbox.
 */
public class BoardManager {

    public record Request(String id, UUID owner, String ownerName, Material material, int amount,
                          double reward, long expires) {
        public String itemName() {
            return QuestDef.pretty(material.name());
        }
    }

    private final BlossomQuests plugin;
    private final File file;
    private final Map<String, Request> requests = new LinkedHashMap<>();
    /** owner -> (material -> amount) of delivered items waiting to be collected */
    private final Map<UUID, Map<Material, Integer>> mailbox = new HashMap<>();
    private boolean dirty;

    public BoardManager(BlossomQuests plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "board.yml");
        // Expire old requests every 5 minutes
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::expireOld, 20L * 60, 20L * 300);
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    public void load() {
        requests.clear();
        mailbox.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection req = yaml.getConfigurationSection("requests");
        if (req != null) {
            for (String id : req.getKeys(false)) {
                ConfigurationSection s = req.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                Material material = Material.matchMaterial(s.getString("material", ""));
                UUID owner;
                try {
                    owner = UUID.fromString(s.getString("owner", ""));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (material == null) {
                    continue;
                }
                requests.put(id, new Request(id, owner, s.getString("owner-name", "?"), material,
                        s.getInt("amount"), s.getDouble("reward"), s.getLong("expires")));
            }
        }
        ConfigurationSection mail = yaml.getConfigurationSection("mailbox");
        if (mail != null) {
            for (String id : mail.getKeys(false)) {
                ConfigurationSection s = mail.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                try {
                    UUID owner = UUID.fromString(id);
                    Map<Material, Integer> items = new LinkedHashMap<>();
                    for (String m : s.getKeys(false)) {
                        Material material = Material.matchMaterial(m);
                        if (material != null) {
                            items.put(material, s.getInt(m));
                        }
                    }
                    mailbox.put(owner, items);
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Request r : requests.values()) {
            String p = "requests." + r.id() + ".";
            yaml.set(p + "owner", r.owner().toString());
            yaml.set(p + "owner-name", r.ownerName());
            yaml.set(p + "material", r.material().name());
            yaml.set(p + "amount", r.amount());
            yaml.set(p + "reward", r.reward());
            yaml.set(p + "expires", r.expires());
        }
        for (Map.Entry<UUID, Map<Material, Integer>> entry : mailbox.entrySet()) {
            for (Map.Entry<Material, Integer> item : entry.getValue().entrySet()) {
                yaml.set("mailbox." + entry.getKey() + "." + item.getKey().name(), item.getValue());
            }
        }
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save board.yml", e);
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    public List<Request> all() {
        List<Request> list = new ArrayList<>(requests.values());
        list.sort((a, b) -> Double.compare(b.reward(), a.reward()));
        return list;
    }

    public Request get(String id) {
        return requests.get(id);
    }

    public int countFor(UUID owner) {
        int count = 0;
        for (Request r : requests.values()) {
            if (r.owner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    public Map<Material, Integer> mailboxOf(UUID owner) {
        return mailbox.getOrDefault(owner, Map.of());
    }

    public int mailboxCount(UUID owner) {
        int total = 0;
        for (int amount : mailboxOf(owner).values()) {
            total += amount;
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Posting, delivering, cancelling
    // ------------------------------------------------------------------

    public boolean post(Player player, Material material, int amount, double reward) {
        int max = plugin.getConfig().getInt("board.max-requests-per-player", 3);
        double min = plugin.getConfig().getDouble("board.min-reward", 100);
        int maxAmount = plugin.getConfig().getInt("board.max-amount", 1728);
        if (!plugin.getRewards().hasEconomy()) {
            player.sendMessage(plugin.msg("&cThe Quest Board needs the money plugin (BlossomEconomy + Vault)."));
            return false;
        }
        if (material == null || material.isAir() || !material.isItem()) {
            player.sendMessage(plugin.msg("&cHold the item you want people to bring you."));
            return false;
        }
        for (String banned : plugin.getConfig().getStringList("board.banned-items")) {
            if (material.name().equalsIgnoreCase(banned)) {
                player.sendMessage(plugin.msg("&cYou can't ask for that item on the board."));
                return false;
            }
        }
        if (countFor(player.getUniqueId()) >= max) {
            player.sendMessage(plugin.msg("&cYou already have " + max + " requests up. Cancel one first."));
            return false;
        }
        if (amount < 1 || amount > maxAmount) {
            player.sendMessage(plugin.msg("&cThe amount must be 1 to " + maxAmount + "."));
            return false;
        }
        if (reward < min) {
            player.sendMessage(plugin.msg("&cThe reward must be at least " + Text.money(min) + "."));
            return false;
        }
        if (!plugin.getRewards().take(player, reward)) {
            player.sendMessage(plugin.msg("&cYou don't have " + Text.money(reward) + "."));
            return false;
        }
        String id;
        do {
            id = Integer.toString(ThreadLocalRandom.current().nextInt(36 * 36 * 36 * 36), 36);
        } while (requests.containsKey(id));
        long hours = plugin.getConfig().getLong("board.expire-hours", 72);
        Request request = new Request(id, player.getUniqueId(), player.getName(), material, amount, reward,
                System.currentTimeMillis() + hours * 3_600_000L);
        requests.put(id, request);
        dirty = true;
        save();
        player.sendMessage(plugin.msg("&aYour request is on the board: &f" + amount + "x " + request.itemName()
                + " &afor &f" + Text.money(reward) + "&a. The money is held until someone delivers."));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        if (plugin.getConfig().getBoolean("board.announce", true)) {
            Bukkit.broadcast(plugin.msg("&f" + player.getName() + " &7posted a quest: &f" + amount + "x "
                    + request.itemName() + " &7for &a" + Text.money(reward) + "&7. &d/quest board"));
        }
        return true;
    }

    /** Counts plain items of this type (no names, enchantments or other changes). */
    private int countPlain(Player player, Material material) {
        ItemStack plain = new ItemStack(material);
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.isSimilar(plain)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removePlain(Player player, Material material, int amount) {
        ItemStack plain = new ItemStack(material);
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !item.isSimilar(plain)) {
                continue;
            }
            int take = Math.min(amount, item.getAmount());
            item.setAmount(item.getAmount() - take);
            amount -= take;
            contents[i] = item.getAmount() <= 0 ? null : item;
        }
        player.getInventory().setStorageContents(contents);
    }

    public boolean deliver(Player player, String id) {
        Request request = requests.get(id);
        if (request == null) {
            player.sendMessage(plugin.msg("&cThat request is gone (someone else delivered it, or it ran out)."));
            return false;
        }
        if (request.owner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("&cThat's your own request."));
            return false;
        }
        int have = countPlain(player, request.material());
        if (have < request.amount()) {
            player.sendMessage(plugin.msg("&cYou need &f" + request.amount() + "x " + request.itemName()
                    + "&c (you have " + have + "). Only plain items count (no names or enchantments)."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return false;
        }
        requests.remove(id);
        removePlain(player, request.material(), request.amount());
        mailbox.computeIfAbsent(request.owner(), k -> new LinkedHashMap<>())
                .merge(request.material(), request.amount(), Integer::sum);
        double tax = Math.max(0, Math.min(100, plugin.getConfig().getDouble("board.tax-percent", 0)));
        double pay = Math.round(request.reward() * (100 - tax)) / 100.0;
        plugin.getRewards().give(player, pay);
        dirty = true;
        save();
        player.sendMessage(plugin.msg("&aDelivered &f" + request.amount() + "x " + request.itemName() + " &ato "
                + request.ownerName() + " &aand earned &f" + Text.money(pay) + "&a!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        plugin.getQuests().addCounter(player, plugin.getData().get(player), "board", 1);
        Player owner = Bukkit.getPlayer(request.owner());
        if (owner != null) {
            owner.sendMessage(plugin.msg("&f" + player.getName() + " &adelivered your &f" + request.amount() + "x "
                    + request.itemName() + "&a! Collect it in &d/quest board &a(mailbox)."));
            owner.playSound(owner.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }
        return true;
    }

    public boolean cancel(Player player, String id) {
        Request request = requests.get(id);
        if (request == null) {
            return false;
        }
        boolean staff = player.hasPermission("blossomquests.admin");
        if (!request.owner().equals(player.getUniqueId()) && !staff) {
            player.sendMessage(plugin.msg("&cThat isn't your request."));
            return false;
        }
        requests.remove(id);
        refund(request);
        dirty = true;
        save();
        player.sendMessage(plugin.msg("&7Request removed. " + Text.money(request.reward())
                + " was given back to " + request.ownerName() + "."));
        return true;
    }

    private void refund(Request request) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(request.owner());
        if (!plugin.getRewards().give(owner, request.reward())) {
            plugin.getLogger().warning("Could not refund " + Text.money(request.reward()) + " to "
                    + request.ownerName() + " for board request " + request.id());
        }
    }

    private void expireOld() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Request> it = requests.values().iterator();
        while (it.hasNext()) {
            Request request = it.next();
            if (request.expires() < now) {
                it.remove();
                refund(request);
                changed = true;
                Player owner = Bukkit.getPlayer(request.owner());
                if (owner != null) {
                    owner.sendMessage(plugin.msg("&7Your board request for &f" + request.amount() + "x "
                            + request.itemName() + " &7ran out. You got your " + Text.money(request.reward())
                            + " back."));
                }
            }
        }
        if (changed) {
            save();
        }
    }

    /** Gives the player everything in their mailbox. */
    public void collect(Player player) {
        Map<Material, Integer> items = mailbox.remove(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            player.sendMessage(plugin.msg("&7Your mailbox is empty."));
            return;
        }
        int total = 0;
        for (Map.Entry<Material, Integer> entry : items.entrySet()) {
            int left = entry.getValue();
            int stack = entry.getKey().getMaxStackSize();
            while (left > 0) {
                int n = Math.min(stack, left);
                plugin.getRewards().giveItem(player, new ItemStack(entry.getKey(), n));
                left -= n;
                total += n;
            }
        }
        dirty = true;
        save();
        player.sendMessage(plugin.msg("&aYou collected &f" + total + " &aitems from your mailbox."));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.0f);
    }

    public void notifyMailbox(Player player) {
        int count = mailboxCount(player.getUniqueId());
        if (count > 0) {
            player.sendMessage(plugin.msg("&dYou have &f" + count + " &ddelivered items waiting! &f/quest board"));
        }
    }

    /** "2d 4h" */
    public static String timeLeft(long millis) {
        long s = Math.max(0, millis / 1000);
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        if (d > 0) {
            return d + "d " + h + "h";
        }
        if (h > 0) {
            return h + "h " + m + "m";
        }
        return Math.max(1, m) + "m";
    }

    public static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
