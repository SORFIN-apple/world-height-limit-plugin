# WorldHeightLimit v1.1.1 - Fabric Port

## Added

- Added Fabric builds for Minecraft 1.19.x-1.21.x.
- Added support for both singleplayer worlds and dedicated Fabric servers.
- Added automatic config creation at `config/world-height-limit.json`.
- Added automatic datapack generation for the currently loaded world.
- Added in-game commands:
  - `/worldheightlimit help`
  - `/worldheightlimit status`
  - `/worldheightlimit debug`
  - `/worldheightlimit worlds`
  - `/worldheightlimit config`
  - `/worldheightlimit reload`
  - `/worldheightlimit set <maxY>`
- Added legacy `/highbuildlimit` command alias.

## Files

- `WorldHeightLimit-v1.1.1-Fabric-1.19.x.jar`
- `WorldHeightLimit-v1.1.1-Fabric-1.20.0-1.20.4.jar`
- `WorldHeightLimit-v1.1.1-Fabric-1.20.5-1.20.6.jar`
- `WorldHeightLimit-v1.1.1-Fabric-1.21.x.jar`

## Safety

- Stable Fabric height range is limited to Minecraft's validated range: `319-2031`.
- The mod refuses to lower the configured world height through commands to avoid hiding or breaking existing blocks above the new limit.
- Invalid manual config values are rejected and repaired back to defaults.
- The mod writes datapack files only for the currently loaded world.

## Important

- Fabric API is required.
- A full world/server restart is required after changing the height.
- This stable build does not bypass Minecraft's internal `2031` top-Y limit. Higher limits require a separate experimental build.
