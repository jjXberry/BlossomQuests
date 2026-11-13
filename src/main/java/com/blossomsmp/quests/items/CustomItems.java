package com.blossomsmp.quests.items;

import com.blossomsmp.quests.BlossomQuests;
import com.blossomsmp.quests.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The custom items: 3 legendary weapons and 3 summoning sigils.
 * Texturing: set "custom-model-data" for an item in config.yml and add a model for it in a resource pack.
 */
public class CustomItems {

    private record Sigil(String id, String name, Material material, String boss, String color) {
    }

    private final BlossomQuests plugin;
    private final NamespacedKey key;
    private final Map<String, Weapon> weapons = new LinkedHashMap<>();
    private final Map<String, Sigil> sigils = new LinkedHashMap<>();

    public CustomItems(BlossomQuests plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "item");
        for (Weapon w : List.of(new PetalReaper(plugin), new InfernalCleaver(plugin), new Frostfang(plugin))) {
            weapons.put(w.id(), w);
        }
        sigils.put("sakura_sigil", new Sigil("sakura_sigil", "&#C774FF&l❀ Sakura Sigil ❀", Material.NETHER_STAR,
                "Sakura Warden", "&#FF69B4"));
        sigils.put("ember_sigil", new Sigil("ember_sigil", "&#FF6A00&l✹ Ember Sigil ✹", Material.MAGMA_CREAM,
                "Ember Tyrant", "&#FF6A00"));
        sigils.put("frost_sigil", new Sigil("frost_sigil", "&#7FDBFF&l❄ Frost Sigil ❄", Material.HEART_OF_THE_SEA,
                "Frostbound King", "&#7FDBFF"));
    }

    public NamespacedKey key() {
        return key;
    }

    /** Which custom item this is, or null. */
    public String idOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public Weapon weaponOf(ItemStack item) {
        String id = idOf(item);
        return id == null ? null : weapons.get(id);
    }

    public boolean isSigil(ItemStack item) {
        String id = idOf(item);
        return id != null && sigils.containsKey(id);
    }

    public List<Weapon> weapons() {
        return new ArrayList<>(weapons.values());
    }

    /** "reaper" -> "petal_reaper", "sigil" -> "sakura_sigil", ... */
    public String resolve(String alias) {
        String a = alias.toLowerCase(Locale.ROOT).trim();
        return switch (a) {
            case "reaper", "petal_reaper" -> "petal_reaper";
            case "cleaver", "infernal_cleaver" -> "infernal_cleaver";
            case "frostfang", "fang" -> "frostfang";
            case "sigil", "sakura_sigil" -> "sakura_sigil";
            case "ember_sigil" -> "ember_sigil";
            case "frost_sigil" -> "frost_sigil";
            default -> null;
        };
    }

    /** Builds a weapon or sigil from its id or alias, or null. */
    public ItemStack create(String alias) {
        String id = resolve(alias);
        if (id == null) {
            return null;
        }
        return weapons.containsKey(id) ? weapon(id) : sigil(id);
    }

    public String displayName(String alias) {
        String id = resolve(alias);
        if (id == null) {
            return alias;
        }
        if (weapons.containsKey(id)) {
            return weapons.get(id).displayName();
        }
        return sigils.get(id).name();
    }

    public ItemStack weapon(String id) {
        Weapon w = weapons.get(id);
        return w == null ? new ItemStack(Material.STICK) : w.build(key);
    }

    @SuppressWarnings("deprecation")
    public ItemStack sigil(String id) {
        Sigil s = sigils.get(id);
        if (s == null) {
            return new ItemStack(Material.PAPER);
        }
        ItemStack item = new ItemStack(s.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(s.name()));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : List.of("&8Summoning Item", "", "&7A cursed relic that still",
                "&7remembers its master.", "", "&d&lRIGHT-CLICK THE GROUND",
                "&7to summon the " + s.color() + s.boss() + "&7.", "&cBring friends. You'll need them.", "",
                "&#C774FF&l✦ EPIC ✦")) {
            lore.add(Text.item(line));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, s.id());
        int cmd = plugin.getConfig().getInt("items." + s.id() + ".custom-model-data", 0);
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        item.setItemMeta(meta);
        return item;
    }
}
