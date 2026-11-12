# ❀ BlossomQuests

Quests, achievements, a player Quest Board, a custom boss and a legendary weapon for Blossom SMP
(Paper 1.21.11, Java 21). Pays money through BlossomEconomy (Vault) and Petals through BlossomToggles.

## What it does

- **`/quest`** opens a pink menu with everything below.
- **Daily quests:** 3 random quests every day (mine diamonds, kill zombies, fish, craft bread, play for an hour,
  win PvP fights...). Rewards **$250 – $1,000** plus Petals and Blossom Points.
- **Weekly quests:** 3 harder quests every Monday. Rewards **$4,000 – $15,000** plus lots of Petals.
- **Achievements:** 20 one-time goals (Mine 10,000 blocks, Kill 100 players, Play 100 hours, beat the boss 10
  times...). Some give a **Sakura Sigil**.
- **Quest Board:** players post their own quests: *"Bring me 128 iron, I'll pay $5,000"*. The money is taken when
  they post (so it's always there), paid to whoever delivers, and the items wait in the poster's **mailbox**.
  Requests end after 3 days and the money goes back.
- **The Sakura Warden:** a giant pink wither-skeleton boss with a pink boss bar, 750 health and 4 attacks:
  - **Petal Storm:** hurts and slows everyone close
  - **Thornlings:** calls pink zombie helpers
  - **Blossom Leap:** jumps at the furthest player and slams down
  - **Wither Bloom:** only below half health, when she's **ENRAGED** and faster
- **Rewards for everyone who helps** (at least 5% of the damage): $2,500 + 300 Petals.
  The **top damager** gets $5,000 more and a **35% chance at the Petal Reaper**. Everyone has a 10% chance at a Sigil.
- **Petal Reaper** (legendary netherite sword): **right-click to Blossom Dash** through enemies,
  and a 15% chance on hit to **wither** them.
- **Sakura Sigil:** right-click the ground (150+ blocks from spawn) to summon the Warden.
- Anti-cheese: placed blocks don't count as mining, spawner mobs don't count, killing the same player again within
  10 minutes doesn't count, AFK time doesn't count, and the boss can't be hurt by suffocation, lava or falling.

## Build the .jar (GitHub, no installing)

1. Make a new repository on GitHub called **BlossomQuests**.
2. Unzip `BlossomQuests.zip` and upload **everything inside the BlossomQuests folder**. `pom.xml` and `src` must be
   on the main page of the repo.
3. The `.github` folder is hidden and usually doesn't upload. If the **Actions** tab shows nothing, click
   **Add file → Create new file**, name it `.github/workflows/main.yml` and paste the contents of
   `.github/workflows/build.yml` from the zip.
4. Wait for the green tick in **Actions**, open the run, and download **BlossomQuests** (the .jar is inside).

If you get a red cross, send a screenshot of the error.

## Install

1. Upload `BlossomQuests.jar` to `plugins` and restart the server.
2. The console should say `Loaded 19 daily quests, 13 weekly quests and 20 achievements`,
   `Hooked into Vault` and `BlossomQuests is ready.`
3. Type `/quest` in game.

Files in `plugins/BlossomQuests`:

| File | What's in it |
|---|---|
| `config.yml` | Reset time zone, Quest Board rules, boss health/damage/rewards, Petal Reaper settings |
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
| `/bq boss spawn` | Spawn the Sakura Warden where you stand |
| `/bq boss kill` | Remove her (no rewards) |
| `/bq boss setspot` | Where she wakes up by herself (turn on `boss.auto-spawn.enabled`) |
| `/bq give <player> <reaper\|sigil> [amount]` | Give the custom items |
| `/bq reset <player>` | Give a player new daily and weekly quests |
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

Types: `MINE`, `PLACE`, `KILL`, `KILL_PLAYER`, `FISH`, `CRAFT`, `BREED`, `PLAYTIME` (minutes), `BOSS`.
Target: `ANY`, a list of blocks/items/mobs, or `HOSTILE` (any hostile mob, KILL only).
Rewards can also have `items: ["sigil:1", "DIAMOND:5"]` and `commands: ["say %player% rocks"]`.

## Placeholders (for TAB)

| Placeholder | Example |
|---|---|
| `%blossomquests_daily_done%` | `2/3` |
| `%blossomquests_weekly_done%` | `1/3` |
| `%blossomquests_ready%` | rewards waiting to be claimed |
| `%blossomquests_completed%` | quests finished |
| `%blossomquests_achievements%` | `5/20` |
| `%blossomquests_boss_kills%` | Sakura Warden kills |
| `%blossomquests_boss_status%` | `Awake!` / `Sleeping` |

## Custom textures

Minecraft can only show new textures through a **resource pack**, which players download when they join.
The plugin is ready for one: set `items.reaper.custom-model-data` (and `items.sigil.custom-model-data`) to a number
like `1001`, then make a pack that gives the netherite sword (or nether star) a new model for that number.
With `0` (the default) they look like a normal netherite sword and nether star, so nothing breaks without a pack.
The boss itself is styled with pink armour and a giant size. A fully custom boss model needs extra plugins
(ModelEngine) and a 3D model, which is a bigger project.

## Good to know (the honest limits)

- **It hasn't run on a real server yet.** It was compile-checked against stand-ins for the Paper classes, with no
  errors. The first start is the real test; send me any red text.
- **Spawn protection:** WorldGuard regions with `mob-spawning deny` can block the boss. Summon her outside spawn
  (Sigils already need 150+ blocks from world spawn).
- **Placed blocks** are remembered until the next restart, so after a restart, breaking blocks placed before it counts.
- **Quest Board items must be plain** (no names, enchantments or damage), so nobody can sneak in junk.
- **Offline helpers** don't get boss rewards; you have to be online when she dies.
- **Mojang's rules:** the Petal Reaper is earned in game, which is fine. Don't sell it or Sigils in a store.
