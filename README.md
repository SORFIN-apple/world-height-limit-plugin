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

- `1.17-1.17.1` (separate project in `1.17.x/project`)
- `1.18-1.18.2` (separate project in `1.18.x/project`)
- `1.19.x`
- `1.20-1.20.4`
- `1.20.5-1.20.6`
- `1.21.x`
- `26.1-26.3` (root plugin profiles; tested on 26.1.1, 26.1.2, 26.2 and 26.3)

Minecraft 1.16.x is not supported by this datapack-based plugin. On a clean Paper 1.16.5 server, `/setblock` at Y=2000 returns `That position is out of this world!` even with the chunk loaded. Reaching Y=2000 in this branch requires a different engine and protocol approach; this project has no verified 1.16 solution.

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

- `1.17`, `1.17.1` — real player placement at Y=2000, permission denial and persistence
- `1.18`, `1.18.1`, `1.18.2` — same full check (Paper 1.18 build was marked ALPHA)
- `1.19`, `1.19.1`, `1.19.2`, `1.19.3`, `1.19.4`
- `1.20`, `1.20.1`, `1.20.2`, `1.20.4` — Paper has no 1.20.3 build
- `1.20.5`, `1.20.6` — full player placement check on the root plugin (Paper 1.20.5 build was marked experimental)
- `1.21`, `1.21.1`, `1.21.3` through `1.21.11` — full player placement check across every available Paper release in this range; Paper has no 1.21.2 build (the tested 1.21.5 build was experimental and 1.21.9 was ALPHA)
- `26.1.1`, `26.1.2`, `26.2` and `26.3` — full player placement check; on 26.3 the test client sends `client_tick_end` after movement as required by that server version

The automated test runner is in [`tools/height-smoke`](tools/height-smoke/README.md). Paper 26.3 build 206 passed player placement at Y=2000, permission denial and persistence after restart.

Server JARs are divided by Minecraft version. The `1.20.5-1.20.6` branch has its own project and build; the root project covers `1.21-26.3`. Use the JAR in the folder matching your server version. The packaged server JARs are in version subfolders under `release-server-1.2.0/` after running `tools/package-server-jars.ps1`.

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

## bStats server statistics

All six server-plugin branches include bStats for anonymous server counts, Minecraft versions, and the other standard Bukkit charts. No custom player or world data is submitted. Server owners can turn off bStats in `plugins/bStats/config.yml`.

All branches use the same registered bStats plugin ID, `34825`, owned by `sorfin`. Their reports contribute to the [World Height Limit bStats page](https://bstats.org/plugin/bukkit/World%20Height%20Limit/34825). To rebuild and package all branches, run:

```powershell
.\tools\package-server-jars.ps1
```

The script embeds that ID in every version-specific JAR and recreates `release-server-1.2.0/`. Once installed on servers, bStats generally displays the first measurements 3–36 minutes after startup. The page can be linked from the Modrinth project description or links section.

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
- `1.17.x/`, `1.18.x/`, `1.19.x/`, `1.20.x/`, `1.20.0-1.20.4/`, `1.21.x/` -> version-specific plugin projects and notes
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

The root project's JAR for `1.21-26.3` will be created in `build/libs/`. Run `tools/package-server-jars.ps1` to build all six server version branches and collect their JARs in six version folders.

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
- The project is licensed under Apache-2.0; see `LICENSE`.
