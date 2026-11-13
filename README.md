# ❀ BlossomQuests

Quests, achievements, a player Quest Board, 3 custom bosses and 3 legendary weapons for Blossom SMP
(Paper 1.21.11, Java 21). Pays money through BlossomEconomy (Vault) and Petals through BlossomToggles.
With BlossomBiomes 1.2.0+, exploring the Blossom biomes counts for quests too.

## What it does

- **`/quest`** opens a pink menu with everything below.
- **Daily quests:** 3 random quests every day (mine diamonds, kill zombies, fish, craft bread, play for an hour,
  win PvP fights, gather Blossom materials...). Rewards **$250 – $1,000** plus Petals and Blossom Points.
- **Weekly quests:** 3 harder quests every Monday. Rewards **$4,000 – $15,000** plus lots of Petals.
- **Achievements:** 36 one-time goals (Mine 10,000 blocks, Kill 100 players, Play 100 hours, beat each boss 10
  times, discover every Blossom biome...). Some give Sigils.
- **Quest Board:** players post their own quests: *"Bring me 128 iron, I'll pay $5,000"*. The money is taken when
  they post (so it's always there), paid to whoever delivers, and the items wait in the poster's **mailbox**.
  Requests end after 3 days and the money goes back.
- **3 bosses**, each with its own look (trimmed armour, coloured glow outline, 8 items floating in a circle
  around them, particles), boss bar, attacks and an **ENRAGED** mode below half health:

  | Boss | Looks like | Attacks |
  |---|---|---|
  | **Sakura Warden** (750 HP) | giant pink wither skeleton, pink glow, petals orbiting | Petal Storm, Thornling helpers, Blossom Leap, Wither Bloom (enraged) |
  | **Ember Tyrant** (850 HP) | burning netherite piglin brute, gold glow, blaze powder orbiting | Meteor Rain (watch the red circles!), Flame Ring, Magma Cube helpers, Charge, Eruption (enraged) |
  | **Frostbound King** (800 HP) | diamond-armoured stray with a Power IV bow, aqua glow, blue ice orbiting | Blizzard, Ice Prison, Frost Wisp helpers, Icicle Rain, Absolute Zero (enraged) |

- **Wither sounds** when a boss spawns (whole server hears it), growls during the fight, when it's enraged and
  when it dies. **Boss bar** with health shows for everyone within 80 blocks (any game mode), or the whole server
  with `boss-bar-for-everyone: true`.
- **Rewards for everyone who helps** (at least 5% of the damage): $2,500 – $3,000 + Petals + Blossom Points.
  The **top damager** gets more and a **35% chance at that boss's legendary weapon**. Everyone has a 10% chance at
  that boss's Sigil.
- **3 legendary weapons**, each with **3 abilities and a passive** (cooldowns show on the action bar):

  | Weapon | Right-click | Shift + Right-click | Left-click (at the air) | Passive |
  |---|---|---|---|---|
  | **Petal Reaper** (netherite sword, Sakura) | Blossom Dash: dash through enemies | Petal Storm: AoE hit + slow, you get Regen II | Petal Bolt: beam that withers | 15% wither on hit, kills heal 3 hearts |
  | **Infernal Cleaver** (netherite axe, Ember) | Hellfire Bolt: fire beam | Flame Ring: burn + push everyone around you | Ember Slash: flame wave in front of you | Fire immunity (not lava), hits set enemies on fire |
  | **Frostfang** (diamond sword, Frost) | Ice Shard: freezing beam | Frost Nova: freeze + slow everything around you | Blizzard Step: teleport 8 blocks forward | Hits slow enemies, 20% deep freeze, immune to powder snow |

  Left-click on a mob is still a normal sword hit, so the left-click ability only fires when you swing at the air.
- **Sigils** (Sakura Sigil, Ember Sigil, Frost Sigil): right-click the ground (150+ blocks from spawn, in the
  worlds in `summon-worlds`, normally just the Overworld) to summon that boss. Each boss can only be awake once at a time.
- Anti-cheese: placed blocks don't count as mining, spawner mobs don't count, killing the same player again within
  10 minutes doesn't count, AFK time doesn't count, and the boss can't be hurt by suffocation, lava or falling.

## Build the .jar (GitHub, no installing)

1. Make a new repository on GitHub called **BlossomQuests**.
2. Unzip `BlossomQuests.zip` and upload **everything inside the BlossomQuests folder**. `pom.xml` and `src` must be
   on the main page of the repo.
3. The `.github` folder is hidden and usually doesn't upload. If the **Actions** tab shows nothing, click
   **Add file → Create new file**, name it `.github/workflows/main.yml` and paste the contents of
   `.github/workflows/main.yml` from the zip.
4. Wait for the green tick in **Actions**, open the run, and download **BlossomQuests** (the .jar is inside).

If you get a red cross, send a screenshot of the error.

## Install

1. Upload `BlossomQuests.jar` to `plugins` and restart the server.
2. The console should say `Loaded 20 daily quests, 15 weekly quests and 36 achievements`,
   `Hooked into Vault` and `BlossomQuests is ready.`
3. Type `/quest` in game.

Files in `plugins/BlossomQuests`:

| File | What's in it |
|---|---|
| `config.yml` | Reset time zone, Quest Board rules, each boss's health/damage/rewards, every weapon's cooldowns and damage |
| `quests.yml` | Every daily quest, weekly quest and achievement. Add your own! |
| `board.yml` | Quest Board requests and mailboxes |
| `players/` | One file per player |

After editing, type `/bq reload`.

## Commands

| Command | What it does |
|---|---|
| `/quest` | The quest menu |
| `/quest daily`, `/quest weekly`, `/quest achievements`, `/quest board`, `/quest boss` | Open one page |
| `/quest post <amount> <reward>` | Post a board request for the item in your hand (like `/quest post 64 2k`) |
| `/quest collect` | Collect delivered items |

Staff (`blossomquests.admin`, ops have it):

| Command | What it does |
|---|---|
| `/bq boss spawn <sakura\|ember\|frost>` | Spawn a boss where you stand |
| `/bq boss kill [sakura\|ember\|frost\|all]` | Remove bosses (no rewards) |
| `/bq boss setspot` | Where a random boss wakes up by itself (turn on `boss-settings.auto-spawn.enabled`) |
| `/bq give <player> <item> [amount]` | Items: `reaper`, `cleaver`, `frostfang`, `sigil`, `ember_sigil`, `frost_sigil` |
| `/bq reset <player>` | Give a player new daily and weekly quests |
| `/bq progress <player> <type> <target> [amount]` | Count quest progress. BlossomBiomes uses this; you normally don't need it |
| `/bq reload`, `/bq save` | Reload files / save now |

On the Quest Board, staff can **right-click** someone else's request to remove it (they get their money back).

## Adding quests (quests.yml)

```yaml
daily:
  emerald_finder:
    name: "Emerald Finder"
    icon: EMERALD
    type: MINE
    target: [EMERALD_ORE, DEEPSLATE_EMERALD_ORE]
    amount: 3
    rewards:
      money: 750
      petals: 60
      points: 10
```

Types: `MINE`, `PLACE`, `KILL`, `KILL_PLAYER`, `FISH`, `CRAFT`, `BREED`, `PLAYTIME` (minutes), `BOSS`, and from
BlossomBiomes: `DISCOVER` (target: a biome like `MOONLIGHT_GARDEN`), `GATHER` (target: a material like
`SAKURA_PETAL`) and `BIOME_BOSS` (target: `RONIN`, `BRAMBLEHORN`, `MOON_SORCERESS`, `EMERALD_WARLORD`, `FROSTMAW`).
For `BOSS` quests the target is `SAKURA`, `EMBER`, `FROST` or `ANY`.
Target: `ANY`, a list of blocks/items/mobs, or `HOSTILE` (any hostile mob, KILL only).
Rewards can also have `items: ["sigil:1", "frostfang", "DIAMOND:5"]` and `commands: ["say %player% rocks"]`.

## Blossom biomes quests (new in 1.1.0)

With **BlossomBiomes 1.2.0 or newer** installed, the biomes count for quests:

| Kind | Quest | Goal |
|---|---|---|
| Daily | Blossom Gatherer | Gather 24 Blossom materials |
| Weekly | Biome Hunter | Help defeat any biome boss |
| Weekly | Blossom Harvest | Gather 160 Blossom materials |
| Achievement | Off the Map / Blossom Explorer | Discover 1 / all 6 Blossom biomes |
| Achievement | Holy Ground | Find an Ancient Blossom Sanctuary |
| Achievement | Beast Tamer / Legend of the Wilds | Help defeat 1 / 25 biome bosses |
| Achievement | Petalfall, Thorn in the Side, Moonset, Warlord's End, The Thaw | Help defeat each biome boss |
| Achievement | Gatherer / Hoarder | Gather 250 / 2,500 Blossom materials |

**Your quests.yml is updated by itself:** on the first start of 1.1.0 these 15 quests are added to your existing file
(nothing else in it changes). If you delete one later, it stays deleted.

Achievement counters from the biomes: `biome_discover`, `biome_discover_<biome>`, `biome_gather`,
`biome_gather_<material>`, `biome_boss`, `biome_boss_<boss>`.

## Placeholders (for TAB)

| Placeholder | Example |
|---|---|
| `%blossomquests_daily_done%` | `2/3` |
| `%blossomquests_weekly_done%` | `1/3` |
| `%blossomquests_ready%` | rewards waiting to be claimed |
| `%blossomquests_completed%` | quests finished |
| `%blossomquests_achievements%` | `5/36` |
| `%blossomquests_boss_kills%` | all boss kills |
| `%blossomquests_boss_kills_sakura%` / `_ember` / `_frost` | kills of one boss |
| `%blossomquests_boss_status%` | `Awake!` / `Sleeping` (any boss) |
| `%blossomquests_boss_name%` | which bosses are awake right now |

## Updating from the first version

- Your old `config.yml` keeps working: new settings are added on start. The old `boss:` and `items.reaper` sections
  are no longer used (it's now `boss-settings:`, `bosses.sakura/ember/frost` and `items.petal_reaper/...`), so
  delete them if you'd changed them and copy your numbers into the new sections.
- `quests.yml`: the new Blossom biome quests are added by themselves (see above).
- Old Petal Reapers keep working.

## Optional: the 3D Sakura Queen model (BetterModel)

You don't need this. Without it the bosses use their armour/glow/orbit look.

The first BlossomQuests zip had a `model` folder with **sakura_queen.bbmodel** (the 3D model, texture inside), **sakura_queen.png**
and **preview.png**. She's an armoured queen with a gold crown, glowing pink eyes, a cape, a giant scythe and a
ring of petals floating around her, with 5 animations: `idle`, `walk`, `attack` (scythe swing), `cast` (arms up,
petals spin) and `leap`.

Without BetterModel she uses her normal look (giant pink-armoured wither skeleton), so nothing breaks.

**Set it up:**
1. Install **BetterModel** on the server (Modrinth or Hangar, pick the version for 1.21.11). Restart once.
2. Upload `sakura_queen.bbmodel` **and** `sakura_queen.png` to `plugins/BetterModel/models/`.
3. Type `bettermodel reload` in the console. It builds the resource pack.
4. **Send the pack to players.** BetterModel builds the pack as a zip in the `BetterModel` folder (the
   `build-folder-location` in its config.yml). Players need a direct download link to it:
   - Download that zip from the panel, upload it to **mc-packs.net**, and copy the **download link** and **SHA-1** it gives you.
   - In `server.properties` set `resource-pack=<link>`, `resource-pack-sha1=<sha1>`, and
     `resource-pack-prompt=Download the Blossom pack to see the Sakura Queen!` (keep `require-resource-pack=false`
     so nobody gets kicked).
   - Restart. Every time you change a model, run `bettermodel reload` and upload the new zip again.
5. `/bq boss spawn` somewhere open. The console should say `Sakura Warden is using the 3D model 'sakura_queen'.`

**Size:** the model is about 2.6 blocks tall and the boss has `size: 1.6` in config.yml. If BetterModel follows the
mob's size she'll be about 4 blocks tall. If she looks small (and her hitbox is bigger than her), set the model's
scale to 1.6 in BetterModel's settings for this model.

**Want to change her look?** Open `sakura_queen.bbmodel` in **Blockbench** (free), File → Open. You can recolour the
texture, move parts or edit animations, then save and upload it again. Keep the bone names (`right_arm`, `head`,
`petal_ring`...) and animation names the same.

**Custom item textures:** set `items.<weapon or sigil>.custom-model-data` (like `items.frostfang.custom-model-data`) to a number like
`1001` and add a model for it in a resource pack. With `0` (the default) they look like normal vanilla items.

Note: the server can only send **one** resource pack link. If you use the Blossom resource pack (BlossomPack) for
the biomes and menus, the BetterModel pack would have to be merged into it first.

## Good to know (the honest limits)

- **The bosses' looks and attack effects haven't been seen in game yet**, so some particle sizes or the orbit
  height might need tweaking. Tell me what looks off.
- **The 3D model was made by code and checked with a preview picture, not in game.** BetterModel is a separate
  plugin and its setup can differ between versions. If the console says it couldn't attach the model, send me the text.
- **Version 1.1.0 was checked against the real Paper 1.21.11, Vault and PlaceholderAPI code**, with no errors.
  It hasn't run on a real server yet; send me any red text.
- **Spawn protection:** WorldGuard regions with `mob-spawning deny` can block the boss. Summon bosses outside spawn
  (Sigils already need 150+ blocks from world spawn).
- **Placed blocks** are remembered until the next restart, so after a restart, breaking blocks placed before it counts.
- **Quest Board items must be plain** (no names, enchantments or damage), so nobody can sneak in junk.
- **Offline helpers** don't get boss rewards; you have to be online when the boss dies.
- **Mojang's rules:** the weapons are earned in game, which is fine. Don't sell them or Sigils in a store.
