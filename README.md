# Lobby

The lobby core for **Fractal Network**: a purple sidebar with live player counts from the proxy, a clean tab list, chat and nametag formats with LuckPerms ranks, lobby protection, void rescue, launch pads, spawn, and locked join items.

**Built by Sasha Wildfang for Fractal Network.**

![Paper](https://img.shields.io/badge/Paper-1.21.11%2B-8E2DE2) ![Java](https://img.shields.io/badge/Java-21-B266FF)

## Features

- **Sidebar**: purple, tiny-text, no numbers on the right. Shows players on every realm, asked straight from the proxy (Velocity or BungeeCord, no proxy plugin needed). Updates twice a second and only sends lines that changed.
- **Tab list**: header and footer, with names in the nametag format
- **Chat**: default and ranked formats; what players type is never recoloured
- **Nametags**: rank prefix above heads, coloured by rank
- **Join, quit and first-join messages**
- **Realm selector** (compass): a menu of realms with live player counts; click to join
- **Radio** (jukebox): opens the [Radio](https://github.com/SashaWildfang/Radio) menu when Radio is installed
- **Player visibility toggle**: hide or show other players, remembered between joins
- **Locked inventory**: only staff (`lobby.staff`) can move, drop or swap items
- **Protection**: build permission, no PvP, no hurting mobs, no fall damage, no hunger
- **Void rescue**: falling off the map puts you back on the last block you stood on, with a puff of clouds
- **Launch pads**: stone pressure plates fling you forward
- **Spawn**: teleport on join, `/spawn`, `/setspawn`
- **Status**: players pick one of 25 emoji statuses in My Profile (Happy, Chilling, Thinking...), shown under their name as "Status: (emoji) Happy". Listed in `config.yml` (`status`), open to everyone by default
- **Holograms** (no DecentHolograms needed): `holograms/*.yml` in DecentHolograms' own format (copied over from DecentHolograms the first time). Shown with display entities: lines that are the same for everyone are shared, lines with `{player}` or placeholders are per player. Supports `&` and hex colours, MiniMessage, DecentHolograms gradients and animations (burn, wave, colors, typewriter, scroll), `#ICON:`/`#HEAD:` lines, line heights, down-origin, display range, update interval, pages, and click actions (MESSAGE, COMMAND, CONSOLE, CONNECT, SOUND, TELEPORT, NEXT_PAGE, PREV_PAGE, PAGE). Edit with `/holo`
- **NPCs** (no FancyNpcs needed): player-shaped mannequins with skins, held items and armour, glow colours, scale, turning to look at players, click actions (message, player_command, console_command, send_to_server, play_sound, need_permission, wait, execute_random_action), and their own hologram above their head that moves with them. Kept in `npcs.yml` (FancyNpcs' NPCs and skins are brought over the first time, and a hologram named like an NPC becomes its label). With nothing to bring over, the built-in Survival, Prison and Capture the Flag NPCs are made, facing spawn, each with an icon and label above it; the welcome hologram and the parkour area above spawn are built in too. Edit with `/npc`
- **Help**: `/lobbyhelp` shows every command you can use by topic; click to type one, hover for details. Full reference: [docs/COMMANDS.md](docs/COMMANDS.md)
- **Parkour**: infinite parkour built in (no ajParkour needed). Click the Parkour NPC or type `/parkour`: blocks appear in the air inside the parkour area one jump ahead, getting longer as your score goes up. The next block sparkles and a dotted arc shows the jump; a parkour sidebar shows your score, time, best and the record until you fall. Scores live in `parkour-scores.yml` (ajParkour's scores and area are brought over the first time)
- **AFK**: after 5 minutes idle (`afk.minutes`), a red "AFK" with a clock shows under the player's name in place of their status, and a light red AFK badge replaces their rank and their name (and glow) turns light red; nothing in chat. `/afk` does it straight away. Walking a block away, turning around, chatting or clicking brings everything back (small mouse movements and pressure plates don't)
- **No crafting**: the inventory's crafting grid and recipe book are hidden and blocked (`join-items.no-crafting`)
- **`/glow`**: glow in your rank colour without touching the sidebar
- `&` colour codes, MiniMessage tags and placeholders work in the config. Built in (no PlaceholderAPI needed): `%lobby_online_<realm|all|here>%`, `%luckperms_prefix%`, `%luckperms_suffix%`, `%player_name%`, `%player_ping%`, `%server_online%`. Other PlaceholderAPI placeholders work too when PlaceholderAPI is installed
- Every message and label is editable in `messages.yml`, in tiny small-caps text or normal (`format.font`)

## Commands

| Command | Permission | Description |
|---|---|---|
| `/spawn` | `lobby.spawn` (everyone) | Go to spawn |
| `/afk` | `lobby.cmd.afk` (everyone) | Show or stop showing that you're AFK |
| `/glow` | `lobby.cmd.glow` (op) | Glow in your rank colour |
| `/setspawn` | `lobby.admin` (op) | Set spawn to where you stand |
| `/lobby reload` or `/lobbycore reload` | `lobby.admin` (op) | Reload config, messages and parkour (use `/lobbycore` if the proxy owns `/lobby`) |
| `/lobbyparkour setup` | `lobby.admin` (op) | Move the Parkour NPC (where runs start) to your position |
| `/holo` (or `/lobbyholo`) | `lobby.admin` (op) | Make and edit holograms: create, addline, setline, insertline, removeline, height, movehere, tp, info, delete, toggle, origin, range, interval, action, addpage, list, near, reload |
| `/npc` (or `/lobbynpc`) | `lobby.admin` (op) | Make and edit NPCs: create, skin, item, line, action, glow, turn, scale, cooldown, movehere, tp, info, remove, list, near, reload |
| `/lobbyhelp [topic\|command]` (or `/lhelp`) | everyone | Help for every command; also `/lobby help` |
| `/parkour` | `lobby.cmd.parkour` (everyone) | Start a parkour run (or stop one) |
| `/lobbyparkour area pos1\|pos2\|return` | `lobby.admin` (op) | Set the corners of the parkour area, or where players go after a run |
| `/lobbyparkour leaderboard [remove]` | `lobby.admin` (op) | Put the parkour leaderboard hologram where you stand (or remove it) |

## Permissions

| Permission | Default | Allows |
|---|---|---|
| `lobby.staff` | op | Moving, dropping and swapping items |
| `lobby.build` | nobody | Placing and breaking blocks (name set by `protection.build-permission`; ops can build when `allow-ops-to-build` is on) |
| `lobby.cmd.afk` | everyone | `/afk` |
| `lobby.cmd.glow` | op | `/glow` |
| `lobby.spawn` | everyone | `/spawn` |
| `lobby.admin` | op | `/setspawn`, `/lobby reload` |

## Scoreboard and tab tags

`<online:NAME>` (players on a proxy server), `<online:all>` (whole network), `<here>` (this server), `<player>`, `<date>`, `<time>`. PlaceholderAPI placeholders work too when it's installed.

## Placeholders

With PlaceholderAPI installed, the same counts as the scoreboard are available everywhere (holograms, other plugins):

- `%lobby_online_all%`: players on the whole network
- `%lobby_online_<server>%`: players on one proxy server, e.g. `%lobby_online_survival%`
- `%lobby_online_here%`: players on this server

## Setup

1. Put `Lobby-<version>.jar` in the lobby server's `plugins/` folder and restart.
2. In `config.yml`, list your proxy server names under `proxy.servers` and `realm-selector.realms`.
3. Stand where players should appear and run `/setspawn`.
4. Velocity: keep `bungee-plugin-message-channel = true` in `velocity.toml` (the default) so player counts and the realm selector work.

Optional: LuckPerms (rank prefixes, glow colour), PlaceholderAPI (extra placeholders), Radio (the jukebox item).

## Building

```bash
./gradlew build
```

The jar goes to `build/libs/`. Tests run as part of the build.

## Project layout

```
src/main/java/com/sasha/lobby/
├── Lobby.java          # Plugin entry point, commands
├── config/             # config.yml, messages.yml, theme, & codes, small caps
├── format/             # Player formats (chat, join messages, nametags)
├── hook/               # LuckPerms and PlaceholderAPI
├── items/              # Join items, realm selector, inventory lock
├── listener/           # Protection, void rescue, launch pads, spawn, chat, glow
├── proxy/              # Player counts and server switching via the proxy
└── scoreboard/         # Sidebar, nametags, tab list
```

---

Lobby is built by **Sasha Wildfang** for **Fractal Network**.
