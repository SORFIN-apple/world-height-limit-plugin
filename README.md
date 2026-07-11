# World Height Limit

Public source repository for a Paper plugin that removes the default top build limit by generating a version-aware datapack in each world save folder.

## What it does

- Raises the top build height to `Y=2031` by default
- Generates datapack files in `world/datapacks/high-build-limit-generated`
- Lets only players with a bypass permission build above the normal vanilla limit
- Includes `/worldheightlimit reload`, `/worldheightlimit status`, `/worldheightlimit fill`, and `/worldheightlimit platform`
- Detects the server version and writes the correct datapack format for that version
- Requires a full server restart after the first launch or after datapack height changes

## Supported versions

- `1.19.x`
- `1.20-1.20.4`
- `1.20.5-1.20.6`
- `1.21.x`

## Commands and permissions

- `/worldheightlimit reload` -> `highbuildlimit.reload`
- `/worldheightlimit status` -> `highbuildlimit.status`
- `/worldheightlimit fill` -> `highbuildlimit.fill`
- `/worldheightlimit platform` -> `highbuildlimit.fill`
- Build above vanilla height -> `highbuildlimit.bypass`

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

## Build from source

Requirements:

- Java `21`

Build command:

```powershell
./gradlew build
```

The finished jar will be created in `build/libs/`.

## Safety notes

- Make a backup before enabling this plugin on an existing world.
- On Minecraft `1.21.2+`, removing a height-changing datapack from an already used world can damage world data above the vanilla limit.
- Datapack height changes only take effect after a full server restart.

## Publishing notes

- This repository is prepared for GitHub with a CI build workflow and a `.gitignore` that excludes local jars and build artifacts.
- Before publishing as true open source, add a license file such as `MIT` or `GPL-3.0`.
