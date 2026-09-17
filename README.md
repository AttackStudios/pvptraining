# PVPTraining

A combat masterclass for Minecraft Java **1.21.11 (Fabric)**: a desktop app for Windows and macOS plus a client-side mod. Start Minecraft with the mod, press **Connect** in the app, and you are dropped into a ready-made practice world with bots, drills and duels.

Website and downloads: https://attackstudios.github.io/pvptraining/

## What is in here

| Folder | What it is |
| --- | --- |
| `mod/` | Fabric 1.21.11 mod (Yarn, Loom 1.16, Java 21). Hosts the local bridge, creates the practice world, builds the arenas, runs the bots and sessions. Fabric API and Java-WebSocket are nested, so players install one jar. |
| `app/` | Electron app. Launcher onboarding (Dawn, Lunar, Modrinth App, Other), brand-styled install guides, one-click connect, live session panel, skill tree, progress, synthesized sound effects. |
| `shared/catalog.json` | Single source of truth for gamemodes, drills, medal targets, bot tiers and mastery rules. `app/tools/sync.mjs` copies it into both the app and the mod. |
| `site/` | The GitHub Pages site. |

## Gamemodes

- **Mace**: tier-list style kit. Density V + Wind Burst mace, Breach IV mace, sword, axe, shield, wind charges, pearls, golden apples, totem, elytra with **no rockets**.
- **Crystal**: netherite (Blast Protection legs), totems, end crystals, obsidian, anchors, glowstone, pearls, pickaxe.
- **Spear** (Mace branch): Mace kit without the elytra, plus a Lunge III netherite spear. Trains attribute swapping: select the spear and jab in the same tick so the lunge uses the previous item's charged cooldown.
- **Elytra + Mace** (Mace branch): Mace kit plus rockets. Wings on, climb, turn, rocket down, wings off, smash.

Every mode has three scored drills (bronze, silver, gold) and a five-tier bot ladder (Rookie, Fighter, Veteran, Elite, Ace). Mastery is half drill medals and half the toughest bot beaten in a first-to-3. Branches unlock at 80% mastery plus a win over the Elite bot.

## How the app finds the game

When the client starts, the mod opens a WebSocket server on `127.0.0.1` (ports 47811 and up) and writes `~/.pvptraining/instances/<pid>.json` with the port and a random per-launch token. The app reads that file, connects, and must present the token. Browser origins are rejected. Progress is stored in `~/.pvptraining/progress.json` so the app can show the skill tree while the game is closed.

Messages are JSON with a `t` field. App to mod: `hello`, `enterWorld`, `start {mode, activity, id}`, `stop`. Mod to app: `welcome`, `state`, `event`, `result`, `progress`, `error`, `denied`.

The same sessions can be started in game without the app:

```
/pvpt start mace drill mace_smash
/pvpt start crystal duel veteran
/pvpt stop
```

## Custom kits

`/kit edit <mace|cpvp|spear|elytra>` hands you the standard kit to rearrange. `/kit save <name> <mode>` stores the whole inventory (every slot, armour, off hand, with enchantments and components) in `~/.pvptraining/kits.json` and makes it the active kit for that mode, so it is what you get whenever a drill or duel starts. `/kit list`, `/kit use <name>`, `/kit load <name>`, `/kit default <mode>` and `/kit delete <name>` manage them. Bots always use the standard kit.

## Auto update

On every launch the app asks the GitHub API for the latest release of this repository. A newer mod jar is downloaded and copied into every mods folder the app previously installed into. A newer app build is downloaded in the background and applied on "Restart to update" or when the app is closed: Windows runs the NSIS installer silently, macOS swaps its own bundle (the builds are unsigned, so Squirrel is not an option). Downloads are verified against the SHA-256 digest GitHub publishes for each asset. Release assets must keep these exact names: `pvptraining-mc1.21.11.jar`, `PVPTraining-win-x64.exe`, `PVPTraining-mac-arm64.zip`, `PVPTraining-mac-x64.zip`.

## Building

```sh
# mod
cd mod && ./gradlew build            # -> mod/build/libs/pvptraining-<ver>+mc1.21.11.jar
./gradlew runClient                  # dev client, logs every hit (-Dpvptraining.debug=true)

# app
cd app && npm install
npm run sync                         # copy catalog + newest mod jar into the app
npm start
npm run dist:mac                     # universal dmg
npm run dist:win                     # x64 NSIS installer
npm run shots                        # render every screen to PNG with sample data
```

## Version lock

`fabric.mod.json` depends on exactly `minecraft: 1.21.11`, so Fabric refuses to load the mod on any other version. The version indicator in the app is deliberately greyed out and non-interactive until more versions exist.

## Bots

Bots are real server-side players with no client (`BotPlayer`), modelled on the approach used by fabric-carpet (MIT). Because vanilla only moves players in response to their own packets, the bot ticks itself, feeds `handleFall` manually so crits, mace smashes and fall damage work, and a small mixin keeps player-dealt knockback from being rolled back. One 1 to 10 difficulty (`Skill`) drives reaction delay, aim error, turn speed, crit and dive cadence, lunge cadence, re-totem speed and crystal place/break delay.

Not affiliated with Mojang, Microsoft, Dawn, Lunar Client or Modrinth.
