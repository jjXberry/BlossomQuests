package com.blossomsmp.quests.items;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.util.Text;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * The custom items:
 *  - Petal Reaper: the Sakura Warden's legendary sword (right-click dash, wither on hit)
 *  - Sakura Sigil: right-click the ground to summon the Sakura Warden
 *
 * Texturing: set "custom-model-data" for an item in config.yml and add a model for it in a resource pack.
 * With 0 (default) the items look like normal Minecraft items, so nothing breaks without a pack.
 */
public class CustomItems {

    public static final String REAPER = "petal_reaper";
    public static final String SIGIL = "sakura_sigil";

    private final BlossomQuests plugin;
    private final NamespacedKey key;

    public CustomItems(BlossomQuests plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "item");
    }

    public NamespacedKey key() {
        return key;
    }

    /** Which custom item this is ("petal_reaper", "sakura_sigil") or null. */
    public String idOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public boolean isReaper(ItemStack item) {
        return REAPER.equals(idOf(item));
    }

    public boolean isSigil(ItemStack item) {
        return SIGIL.equals(idOf(item));
    }

    private List<net.kyori.adventure.text.Component> lore(String... lines) {
        List<net.kyori.adventure.text.Component> list = new ArrayList<>();
        for (String line : lines) {
            list.add(Text.item(line));
        }
        return list;
    }

    @SuppressWarnings("deprecation")
    private void modelData(ItemMeta meta, String path) {
        int cmd = plugin.getConfig().getInt(path, 0);
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
    }

    public ItemStack reaper() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item("&#FF1493&l✿ &#FF69B4&lP&#FF7EBF&le&#FF93CA&lt&#FFA8D5&la&#FFBDE0&ll "
                + "&#FFBDE0&lR&#FFA8D5&le&#FF93CA&la&#FF7EBF&lp&#FF69B4&le&#FF1493&lr &#FF1493&l✿"));
        int cooldown = plugin.getConfig().getInt("items.reaper.dash-cooldown-seconds", 12);
        int chance = plugin.getConfig().getInt("items.reaper.wither-chance", 15);
        meta.lore(lore(
                "&8Legendary Weapon",
                "",
                "&7Torn from the hands of the",
                "&dSakura Warden&7. Its blade still",
                "&7sheds petals when it swings.",
                "",
                "&d&lRIGHT-CLICK: &fBlossom Dash",
                "&7Dash forward through a storm of",
                "&7petals, hitting everyone you pass.",
                "&8Cooldown: " + cooldown + "s",
                "",
                "&d&lPASSIVE: &fThorns of Sakura",
                "&7" + chance + "% chance on hit to wither",
                "&7your target for 3 seconds.",
                "",
                "&#FF69B4&l✦ LEGENDARY ✦"));
        meta.addEnchant(Enchantment.SHARPNESS, plugin.getConfig().getInt("items.reaper.sharpness", 5), true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.LOOTING, 3, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, REAPER);
        modelData(meta, "items.reaper.custom-model-data");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack sigil() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item("&#C774FF&l❀ Sakura Sigil ❀"));
        meta.lore(lore(
                "&8Summoning Item",
                "",
                "&7A cursed blossom that still",
                "&7remembers its Warden.",
                "",
                "&d&lRIGHT-CLICK THE GROUND",
                "&7to summon the &dSakura Warden&7.",
                "&cBring friends. You'll need them.",
                "",
                "&#C774FF&l✦ EPIC ✦"));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, SIGIL);
        modelData(meta, "items.sigil.custom-model-data");
        item.setItemMeta(meta);
        return item;
    }

    /** Pink leather armour for the boss and its minions. */
    public ItemStack pinkArmor(Material material) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(Color.fromRGB(0xFF69B4));
            item.setItemMeta(meta);
        }
        return item;
    }
}
