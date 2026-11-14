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
- **`/glow`**: glow in your rank colour without touching the sidebar
- `&` colour codes, MiniMessage tags, `%luckperms_prefix%` and PlaceholderAPI placeholders all work in the config
- Every message and label is editable in `messages.yml`, in tiny small-caps text or normal (`format.font`)

## Commands

| Command | Permission | Description |
|---|---|---|
| `/spawn` | `lobby.spawn` (everyone) | Go to spawn |
| `/glow` | `lobby.cmd.glow` (op) | Glow in your rank colour |
| `/setspawn` | `lobby.admin` (op) | Set spawn to where you stand |
| `/lobby reload` or `/lobbycore reload` | `lobby.admin` (op) | Reload config, messages and parkour (use `/lobbycore` if the proxy owns `/lobby`) |
| `/lobbyparkour setup` | `lobby.admin` (op) | Set up a pressure-plate start and hologram at your position |
| `/lobbyparkour leaderboard [remove]` | `lobby.admin` (op) | Put the parkour leaderboard hologram where you stand (or remove it) |

## Permissions

| Permission | Default | Allows |
|---|---|---|
| `lobby.staff` | op | Moving, dropping and swapping items |
| `lobby.build` | nobody | Placing and breaking blocks (name set by `protection.build-permission`; ops can build when `allow-ops-to-build` is on) |
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
