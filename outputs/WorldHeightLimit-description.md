# World Height Limit

**Expand your world height without giving high-altitude building access to everyone.**

World Height Limit increases Minecraft world build height by automatically generating a version-aware datapack inside the world save folder.

The Bukkit-family version supports **Bukkit, Spigot, Paper, Purpur, and Folia** for **Minecraft 1.19.x-1.21.x**.

Fabric and NeoForge versions are available as **Beta** ports for modded servers and singleplayer worlds.

GitHub page: https://github.com/SORFIN-apple/world-height-limit-plugin

---

## What does it do?

World Height Limit lets you raise the top world height while keeping building above the vanilla limit restricted to admins or players with permission.

Regular players can stay limited to the normal vanilla build height, while trusted players can build higher depending on your configuration.

---

## Why use World Height Limit?

- Keeps vanilla building limits for normal players
- Supports Bukkit, Spigot, Paper, Purpur, and Folia
- Provides Fabric and NeoForge Beta ports
- Supports multiple Minecraft version ranges
- Supports arbitrary target height values up to the stable datapack limit
- Automatically aligns generated datapack height to Minecraft's 16-block world sections
- Keeps building restricted to the exact configured target height
- Generates required datapacks automatically
- Provides debug and world height diagnostics

---

## Features

- Automatic datapack generation
- Version-aware datapack generation
- Configurable build restrictions
- Exact player build-limit enforcement
- Effective generated world height reporting
- Separate Overworld, Nether, and End settings
- Separate toggles for placing blocks, breaking blocks, and using buckets
- Customizable permissions and messages
- Notification modes: Chat, Action bar, Title, Both, Disabled
- Admin commands for reload, status, debug info, and world height checks

---

## Supported platforms

Plugin:

- Bukkit
- Spigot
- Paper
- Purpur
- Folia

Mod Beta:

- Fabric
- NeoForge

---

## Supported Minecraft versions

Plugin:

- `1.19.x`
- `1.20.0-1.20.4`
- `1.20.5-1.20.6`
- `1.21.x`

Fabric Beta:

- `1.19.x`
- `1.20.0-1.20.4`
- `1.20.5-1.20.6`
- `1.21.x`

NeoForge Beta:

- `1.20.2-1.20.4`
- `1.20.5-1.20.6`
- `1.21.x`

---

## Commands

Plugin:

| Command | Description |
| --- | --- |
| `/worldheightlimit help` | Shows the command list |
| `/worldheightlimit reload` | Reloads config-based restrictions and messages |
| `/worldheightlimit status` | Shows plugin/world height status |
| `/worldheightlimit debug` | Shows detailed height/datapack diagnostics |
| `/worldheightlimit worlds` | Lists loaded worlds and their active height range |
| `/worldheightlimit fill` | Fills an area using the plugin |
| `/worldheightlimit platform` | Creates a square platform around the player |

The old `/highbuildlimit` command remains available as an alias for compatibility.

Fabric/NeoForge Beta:

| Command | Description |
| --- | --- |
| `/worldheightlimit info` | Shows current height, datapack, and world diagnostics |
| `/worldheightlimit worlds` | Lists loaded worlds and their active height range |
| `/worldheightlimit set <height> confirm` | Saves a new target height and regenerates the datapack |

---

## Permissions

Plugin:

| Permission | Description |
| --- | --- |
| `highbuildlimit.bypass` | Allows building above the normal vanilla height limit |
| `highbuildlimit.reload` | Allows using the reload command |
| `highbuildlimit.status` | Allows using status, debug, and worlds commands |
| `highbuildlimit.fill` | Allows using fill and platform commands |

Permission nodes keep the old `highbuildlimit.*` names for compatibility.

---

## Important notes

- Make a backup before first use.
- Height changes require a full restart.
- Reloading does not apply active world height changes by itself.
- On a fresh installation, start the server or world once to generate the datapack, then fully stop and start it again.
- Stable Fabric/NeoForge Beta builds cap configurable height at `Y=2031`.
- The Fabric version requires Fabric API.

---

## One-line summary

**Expand world height across Bukkit, Spigot, Paper, Purpur, Folia, Fabric, and NeoForge while reserving high-altitude building for admins or permitted players.**
