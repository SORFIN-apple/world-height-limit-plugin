# World Height Limit 1.18.x

Server plugin for `1.18` through `1.18.2` that raises the build height by generating a version-aware datapack in each world save folder.

## What it does

- By default raises the top build height to `Y=2031`
- Writes datapack files into `world/datapacks/high-build-limit-generated`
- Lets only players with a bypass permission build above the normal vanilla limit
- Includes `/worldheightlimit reload` and `/worldheightlimit status`
- Detects the server version and writes the correct datapack format for that version
- Requires a full server restart after the first launch or after datapack height changes

## Permissions

- `highbuildlimit.bypass`
- `highbuildlimit.reload`
- `highbuildlimit.status`

## Configurable behavior

- Different restriction toggles for Overworld, Nether, and End
- Separate controls for block placing, block breaking, and bucket use
- Custom permission nodes
- Custom denial and admin messages
- Notification mode: chat, action bar, title, both, or off
- Notification cooldown to prevent spam

## Supported versions

- `1.18`, `1.18.1` and `1.18.2` (real-server placement and persistence verified)

## Important

- Make a backup before using this on an existing world.
- Keep the generated datapack installed after building above the normal height limit.

## Build

```powershell
./gradlew build
```

The finished jar will be in `build/libs/`.
