# World Height Limit 1.20.5-1.20.6

Paper plugin for `1.20.5` through `1.20.6` that removes the default top build limit by generating a version-aware datapack in each world save folder.

## What it does

- By default raises the top build height to `Y=2031`
- Writes datapack files into `world/datapacks/high-build-limit-generated`
- Lets only players with a bypass permission build above the normal vanilla limit
- Includes `/highbuildlimit reload` and `/highbuildlimit status`
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

- `1.20.5`
- `1.20.6`

## Important

- Make a backup before using this on an existing world.
- On Minecraft `1.21.2+`, removing a height-changing datapack from a used world can damage the world data above the normal limit.

## Build

```powershell
./gradlew build
```

The finished jar will be in `build/libs/`.
