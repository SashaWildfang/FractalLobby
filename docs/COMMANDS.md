# Lobby commands

In game: `/lobbyhelp` shows the topics, `/lobbyhelp <topic>` a topic, `/lobbyhelp <command>` one command.
Players only see the commands they're allowed to use.

## Everyone

Commands every player can use.

| Command | Permission | What it does |
|---|---|---|
| `/spawn` | `lobby.spawn` | Go back to the lobby spawn. |
| `/afk` | `lobby.cmd.afk` | Show that you're AFK (or that you're back).<br>Moving, chatting or clicking also brings you back.<br>You go AFK by yourself after 5 minutes idle. |
| `/parkour` | `lobby.cmd.parkour` | Start a parkour run, or stop the one you're on.<br>Clicking the Parkour NPC does the same.<br>Jump to the sparkling block; falling ends the run. |
| `/chatcolor [colour\|reset]` | `lobby.cmd.chatcolor` | Pick the colour of your chat messages.<br>Without a colour it opens the menu.<br>Also /chatcolour. |
| `/glow` | `lobby.cmd.glow` | Glow in your rank colour (or stop).<br>Guardian rank or higher. |
| `/flyingminecart` | `lobby.cmd.flyingminecart` | Summon a flying minecart anyone can ride.<br>W to fly, Space to climb, sneak to get out. Also /flycart. |
| `/lobbyhelp [topic\|command]` | everyone | This help.<br>Example: /lobbyhelp npcs<br>Example: /lobbyhelp npc skin |
| `/collectibles` | everyone | Open your Collectibles: hats, suits, gadgets, cloaks, emotes and gestures.<br>Aliases: /cosmetics, /wardrobe. Also the Collectibles item in your hotbar. |
| `/emote <name>` | everyone | Do an emote you've found (a big emoji over your head). |
| `/gesture <name>` | everyone | A copy of you does a gesture you've found (wave, clap, spin...). |

## Parkour

Setting up the parkour course and its holograms.

| Command | Permission | What it does |
|---|---|---|
| `/lobbyparkour setup` | `lobby.admin` | Put the Parkour NPC where you stand (it starts runs; make it again if it was removed). |
| `/lobbyparkour area pos1` | `lobby.admin` | Set one corner of the area the course is built in.<br>Stand in one corner, then the opposite one for pos2.<br>The course is built anywhere inside the box. |
| `/lobbyparkour area pos2` | `lobby.admin` | Set the opposite corner of the parkour area. |
| `/lobbyparkour area return` | `lobby.admin` | Set where players go after a run.<br>Without it they go to the lobby spawn. |
| `/lobbyparkour leaderboard` | `lobby.admin` | Put the parkour leaderboard hologram where you stand. |
| `/lobbyparkour leaderboard move` | `lobby.admin` | Move the leaderboard hologram to exactly where you stand. |
| `/lobbyparkour leaderboard remove` | `lobby.admin` | Take the leaderboard hologram away. |

## Mystery Boxes

Mystery Boxes, Mystery Dust and the Mystery Vault.

| Command | Permission | What it does |
|---|---|---|
| `/mysterybox` | everyone | Open the Mystery Vault menu: your boxes and crafting with dust.<br>Aliases: /mb, /boxes. Boxes are opened at the Mystery Vault. |
| `/dust` | everyone | See how much Mystery Dust you have. |
| `/mysterybox give <player> <stars> [amount]` | `lobby.admin` | Give Mystery Boxes (works from the console, for the store and votes). |
| `/mysterybox dust <player> <amount>` | `lobby.admin` | Give Mystery Dust (a negative amount takes it away). |
| `/mysterybox reset <player>` | `lobby.admin` | Take away a player's boxes, dust and found cosmetics. |
| `/mysterybox vault add` | `lobby.admin` | Add a Mystery Vault on the block you stand on; players stand on the side you face. |
| `/mysterybox vault remove` | `lobby.admin` | Remove the nearest Mystery Vault (without any, boxes open in front of the player). |
| `/mysterybox vault list` | `lobby.admin` | List the Mystery Vaults. |

## Holograms

Making and editing holograms.

| Command | Permission | What it does |
|---|---|---|
| `/holo create <name> [text]` | `lobby.admin` | Make a hologram just above you.<br>Text can use & codes, &#RRGGBB, MiniMessage, {player}, %placeholders%, gradients like &lt;#FF7BE5>text&lt;/#FFC2F2> and animations like &lt;#ANIM:burn:&f,&d>text&lt;/#ANIM> (burn, wave, colors, typewriter, scroll). |
| `/holo addline <name> <text>` | `lobby.admin` | Add a line at the bottom.<br>Text can use & codes, &#RRGGBB, MiniMessage, {player}, %placeholders%, gradients like &lt;#FF7BE5>text&lt;/#FFC2F2> and animations like &lt;#ANIM:burn:&f,&d>text&lt;/#ANIM> (burn, wave, colors, typewriter, scroll).<br>#ICON: DIAMOND shows an item; #HEAD: name shows a head. |
| `/holo setline <name> <line> <text>` | `lobby.admin` | Change a line (lines start at 1).<br>Text can use & codes, &#RRGGBB, MiniMessage, {player}, %placeholders%, gradients like &lt;#FF7BE5>text&lt;/#FFC2F2> and animations like &lt;#ANIM:burn:&f,&d>text&lt;/#ANIM> (burn, wave, colors, typewriter, scroll). |
| `/holo insertline <name> <line> <text>` | `lobby.admin` | Put a new line in before another. |
| `/holo removeline <name> <line>` | `lobby.admin` | Remove a line. |
| `/holo height <name> <line> <blocks>` | `lobby.admin` | Set the gap a line takes (0.3 is normal).<br>Use a small height on an empty line for a little space. |
| `/holo movehere <name>` | `lobby.admin` | Move a hologram to where you stand. |
| `/holo tp <name>` | `lobby.admin` | Go to a hologram. |
| `/holo info <name>` | `lobby.admin` | Show a hologram's lines. |
| `/holo delete <name>` | `lobby.admin` | Delete a hologram. |
| `/holo toggle <name>` | `lobby.admin` | Hide or show a hologram. |
| `/holo origin <name>` | `lobby.admin` | Switch whether its location is the top or bottom line. |
| `/holo range <name> <blocks>` | `lobby.admin` | How far away it can be seen. |
| `/holo interval <name> <ticks>` | `lobby.admin` | How often placeholders in it refresh (20 = once a second). |
| `/holo action <name> <click> <TYPE:value>` | `lobby.admin` | Add something that happens when it's clicked.<br>click: ANY, LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT<br>MESSAGE:text, COMMAND:spawn, CONSOLE:say hi, CONNECT:survival,<br>SOUND:entity.player.levelup:1:1, TELEPORT:world:0:100:0, NEXT_PAGE, PREV_PAGE, PAGE:2<br>/holo action <name> <click> clear removes them. |
| `/holo addpage <name>` | `lobby.admin` | Add a page (clicking the hologram turns pages). |
| `/holo list` | `lobby.admin` | List every hologram. |
| `/holo near` | `lobby.admin` | List holograms within 30 blocks. |
| `/holo reload` | `lobby.admin` | Load holograms/*.yml again after editing them by hand. |

## NPCs

Making and editing NPCs and the holograms above them.

| Command | Permission | What it does |
|---|---|---|
| `/npc create <name>` | `lobby.admin` | Make an NPC where you stand, with your skin, facing your way. |
| `/npc skin <name> <player\|link\|@me>` | `lobby.admin` | Change its skin.<br>A player name, a textures.minecraft.net link, or @me for yours. |
| `/npc item <name> <slot>` | `lobby.admin` | Give it the item in your hand (empty hand takes it away).<br>slot: mainhand, offhand, head, chest, legs, feet |
| `/npc line <name> add <text>` | `lobby.admin` | Add a line to the hologram above its head.<br>Text can use & codes, &#RRGGBB, MiniMessage, {player}, %placeholders%, gradients like &lt;#FF7BE5>text&lt;/#FFC2F2> and animations like &lt;#ANIM:burn:&f,&d>text&lt;/#ANIM> (burn, wave, colors, typewriter, scroll). |
| `/npc line <name> set <line> <text>` | `lobby.admin` | Change a line of its hologram. |
| `/npc line <name> insert <line> <text>` | `lobby.admin` | Put a new line in before another. |
| `/npc line <name> remove <line>` | `lobby.admin` | Remove a line from its hologram. |
| `/npc line <name> height <line> <blocks>` | `lobby.admin` | Set the gap a line takes. |
| `/npc line <name> offset <blocks>` | `lobby.admin` | How far above its head the hologram starts (0.35 is normal). |
| `/npc action <name> <any\|left\|right> add <type:value>` | `lobby.admin` | Add something that happens when it's clicked.<br>realm:survival (like the realm selector: checks offline, full, maintenance, coming soon), parkour (starts a run),<br>message:<text>, player_command:spawn, console_command:say hi, send_to_server:survival,<br>play_sound:entity.player.levelup, need_permission:some.perm (stops if missing), wait:1.5,<br>execute_random_action (runs one of the actions after it) |
| `/npc action <name> <any\|left\|right> clear` | `lobby.admin` | Remove its click actions. |
| `/npc glow <name> <colour\|off>` | `lobby.admin` | Make it glow in a colour. |
| `/npc turn <name> <on\|off> [distance]` | `lobby.admin` | Whether it looks at nearby players. |
| `/npc scale <name> <size>` | `lobby.admin` | Make it bigger or smaller (1 is normal). |
| `/npc cooldown <name> <seconds>` | `lobby.admin` | Time before the same player can click it again. |
| `/npc rename <name> <new name>` | `lobby.admin` | Give an NPC a new name (everything else stays). |
| `/npc movehere <name>` | `lobby.admin` | Move it (and its hologram) to where you stand. |
| `/npc tp <name>` | `lobby.admin` | Go to an NPC. |
| `/npc info <name>` | `lobby.admin` | Show its skin, actions and hologram lines. |
| `/npc remove <name>` | `lobby.admin` | Delete an NPC. |
| `/npc list` | `lobby.admin` | List every NPC. |
| `/npc near` | `lobby.admin` | List NPCs within 30 blocks. |
| `/npc reload` | `lobby.admin` | Load npcs.yml again after editing it by hand. |

## Admin

Reloading, spawn, playtime board and the rest.

| Command | Permission | What it does |
|---|---|---|
| `/lobby reload` | `lobby.admin` | Reload config, messages, parkour, holograms and NPCs.<br>Also /lobbycore reload, for when the proxy owns /lobby. |
| `/setspawn` | `lobby.admin` | Set the lobby spawn to where you stand. |
| `/lobbyplaytime` | `lobby.admin` | Put the network playtime leaderboard hologram where you stand. |
| `/lobbyplaytime move` | `lobby.admin` | Move the playtime hologram to exactly where you stand. |
| `/lobbyplaytime remove` | `lobby.admin` | Take the playtime hologram away. |
