# ZPP-Bots

Free and open source PvP practice bots addon for [ZonePractice Pro](https://github.com/ZoneDevelopement/ZonePracticePro). The bots are server-side players driven by player-like inputs, so movement, combat, knockback, cooldowns, shields, food, potions and bows follow normal player rules. Difficulty and play style change only how a bot perceives, aims and decides; health, speed, reach, damage and items are the same on every level.

## Requirements

- ZonePractice Pro with API 2.8.0 or newer (API level 7)
- Paper 1.21.11, 26.1.x, 26.2 or 26.3
- Java 25
- PacketEvents, which ZonePractice Pro already needs

## Installation

1. Put the ZPP-Bots jar into the `plugins/` folder next to ZonePractice Pro.
2. Restart the server. `/botpractice` opens the setup menu.
3. Prepare a ladder and arena for bot practice: see the [Bot Practice setup guide](https://zone-developement.gitbook.io/zonepractice-pro/setup-guides/bot-practice). In short, use an enabled BASIC or BUILD ladder with the **Bot Practice** match type on, hit delay `1x` and no shrinking border. Any kit item is accepted.

## Playing

- `/botpractice` (alias `/bots`) opens the setup menu. The top row picks the difficulty (Beginner, Easy, Normal, Hard or Expert) and the second row picks the play style (Default, Safe, Aggressive, Adaptive, Defensive, Balanced or Random); the selected entries glow and the menu starts on `DEFAULT-DIFFICULTY` and `BEHAVIOR.PLAY-STYLE`. The **Start** button opens the ZonePractice Pro ladder, arena and round selection. If `BEHAVIOR.PLAYER-PLAY-STYLES` lists one style, the style row is hidden. Every bot in the match uses the chosen style; Random rolls one per bot.
- `/botpractice rematch` starts the last bot match again, for 60 seconds after it ends. The chat result and the lobby rematch item do the same.

After each completed round, chat provides links to view both fighters' inventories for that round.

To give players a lobby item, add a `LOBBY-BASIC.EXTRA` entry with `COMMAND: botpractice` to ZonePractice Pro's `inventories.yml`.

## Commands and permissions

| Command | Permission | Default | Description |
| --- | --- | --- | --- |
| `/botpractice [rematch]` (`/bots`) | `zppbots.use` | everyone | Open bot practice or start a rematch |
| `/botbody` | `zppbots.body` | op | Spawn and inspect a diagnostic bot body (needs `-Dzpp.bots.debugTools=true`) |
| `/botprobe` | `zppbots.probe` | op | Record your own player mechanics for parity checks (needs `-Dzpp.bots.debugTools=true`) |

## Configuration

`config.yml` contains local setup choices and diagnostics options:

- `LIMITS.MAX-ACTIVE-MATCHES` (default `16`, 1-64): bot matches running at the same time.
- `LIMITS.MAX-TOTAL-BOTS` (default `32`, 1-256): bots alive on the whole server at once.
- `LIMITS.MAX-BOTS-PER-MATCH` (default `7`, 1-7): bots in one match. 1 allows only 1v1.
- `LIMITS.START-TIMEOUT-TICKS` (default `200`, 20-1200): how long a match may take to start.
- `LIMITS.MAX-MATCH-DURATION-SECONDS` (default `600`, 30-3600): longest match before it ends as a timeout.
- `DEFAULT-DIFFICULTY` (default `3`, 1-5): selected difficulty when a match does not choose one.
- `BEHAVIOR.PLAY-STYLE` (default `DEFAULT`): the style the setup menu starts on, and the style used when a match does not choose one.
- `BEHAVIOR.PLAYER-PLAY-STYLES` (default all seven styles): styles players can pick, in menu order. `PLAY-STYLE` must be in the list.
- `BEHAVIOR.TECHNIQUES`: optionally set known boolean technique keys to override the built-in technique switches. The bundled map is empty.
- `DEBUG` (default `false`): logs bot decisions to the console. Very noisy.
- `DIAGNOSTICS.WORLDS` (default empty): worlds where staff may spawn diagnostic `/botbody` bodies (debug tools only).

Difficulty values, combat behavior and random-style weights are built into the plugin. Edit them in `src/main/java/dev/nandi0813/zppbots/tuning/BuiltInTuning.java` and rebuild.

At round start, bots can move missing speed, strength or fire resistance buffs from inventory into their hotbar and drink them one at a time while safe. They skip buffs that are already active. During a fight, bots briefly try to create distance before refilling healing splash potions, then refill after a short bounded retreat even if the opponent keeps pressure on them; normal inventory timing still applies. Melee critical jumps require close range and a free weapon hand, and are spaced at least two seconds apart.

`messages.yml` holds the setup menu (`SETUP-MENU`), level names and descriptions (`DIFFICULTY.<n>.NAME/LORE`), play-style names and descriptions (`PLAY-STYLE.<STYLE>.NAME/LORE`) and command messages, in MiniMessage format. Match messages, results and the sidebar come from ZonePractice Pro's `language.yml` and `sidebar.yml`.

## Building

```bash
./gradlew build
```

The plugin jar is written to `build/libs/ZPP-Bots-<version>.jar`. Add `-PlocalApi=false` to build against the released ZonePracticePro API from JitPack instead of a sibling `../ZPP-Api` checkout.

Bot tuning lives in `src/main/java/dev/nandi0813/zppbots/tuning/BuiltInTuning.java`.

## Capacity

Each bot costs about 0.1–0.3 ms of server time per tick once the server has warmed up, and a new bot takes about 3 ms to spawn (about 230 ms for the first one after a restart). On an Apple M4 Max with Paper 26.3, 16 fighting bots kept the median tick at 5.8 ms and the 99th percentile at 21.7 ms; 64 bots stayed under 39 ms per tick. `LIMITS.MAX-ACTIVE-MATCHES` (default 16) limits how many bot matches run at once. Raise it only after checking your own tick times.

## Known limits

- Any kit item is accepted, but bots use only the items their AI knows; other items stay unused.
- Riptide only works in water or rain, like for players.
- Bots are server-side players without a network connection. Packet-level anticheats do not see their inputs.
- A team has at most 4 fighters. Parties fight on team 1 with bot fillers; the other team is all bots.
