# World Height Limit

Public source repository for World Height Limit: a Minecraft plugin/mod project that expands the top world height by generating a version-aware datapack in the world save folder.

The Bukkit-family plugin targets Bukkit, Spigot, Paper, Purpur, and Folia. Fabric and NeoForge ports are available as beta builds.

## What it does

- Raises the top build height to `Y=2031` by default
- Supports arbitrary target values up to the stable vanilla datapack limit
- Generates datapack files in each world save folder
- Lets only players with a bypass permission build above the normal vanilla limit
- Includes `/worldheightlimit reload`, `/worldheightlimit status`, `/worldheightlimit debug`, `/worldheightlimit worlds`, `/worldheightlimit fill`, and `/worldheightlimit platform`
- Detects the server version and writes the correct datapack format for that version
- Requires a full server restart after the first launch or after datapack height changes

## Supported versions

- `1.19.x`
- `1.20-1.20.4`
- `1.20.5-1.20.6`
- `1.21.x`

## Supported platforms

Plugin:

- Paper
- Purpur
- Folia
- Spigot
- Bukkit

Mod beta:

- Fabric
- NeoForge

## Smoke-tested builds

Plugin:

- `1.19.4`
- `1.20.4`
- `1.20.6`
- `1.21.4`

Fabric beta:

- `1.19.4`
- `1.20.4`
- `1.20.6`
- `1.21.11`

NeoForge beta:

- `1.20.4`
- `1.21.11`

## Commands and permissions

- `/worldheightlimit reload` -> `highbuildlimit.reload`
- `/worldheightlimit status` -> `highbuildlimit.status`
- `/worldheightlimit debug` -> `highbuildlimit.status`
- `/worldheightlimit worlds` -> `highbuildlimit.status`
- `/worldheightlimit fill` -> `highbuildlimit.fill`
- `/worldheightlimit platform` -> `highbuildlimit.fill`
- Build above vanilla height -> `highbuildlimit.bypass`

The old `/highbuildlimit` command remains as an alias for compatibility.

## Configurable behavior

- Different restriction toggles for Overworld, Nether, and End
- Separate controls for block placing, block breaking, and bucket use
- Custom permission nodes
- Custom denial and admin messages
- Notification mode: `OFF`, `CHAT`, `ACTION_BAR`, `TITLE`, or `BOTH`
- Notification cooldown to prevent spam
- Admin fill limit
- Optional suppression of noisy heightmap warnings

## Repository layout

- `src/` -> current plugin source code
- `data/` -> bundled datapack templates used by the plugin
- `1.19.x/`, `1.20.x/`, `1.20.0-1.20.4/`, `1.21.x/` -> version notes, release notes, and archived branch-specific materials
- `fabric/`, `fabric-1.19.x/`, `fabric-1.20.0-1.20.4/`, `fabric-1.20.5-1.20.6/` -> Fabric beta source sets
- `neoforge/`, `neoforge-1.20.2-1.20.4/`, `neoforge-1.20.5-1.20.6/` -> NeoForge beta source sets
- `outputs/` -> release descriptions and changelog drafts

## Build from source

Plugin requirements:

- Java `21`

Plugin build command:

```powershell
./gradlew build
```

The finished jar will be created in `build/libs/`.

Fabric and NeoForge ports are separate Gradle projects. Build them from their own folders, for example:

```powershell
cd fabric
./gradlew build
```

## Safety notes

- Make a backup before enabling this plugin on an existing world.
- On Minecraft `1.21.2+`, removing a height-changing datapack from an already used world can damage world data above the vanilla limit.
- Datapack height changes only take effect after a full server restart.
- The beta mod ports intentionally cap the stable configurable height at `Y=2031`. Higher heights require a separate experimental implementation.

## Publishing notes

- This repository is prepared for GitHub with a CI build workflow and a `.gitignore` that excludes local jars and build artifacts.
- Before publishing as true open source, add a license file such as `MIT` or `GPL-3.0`.
