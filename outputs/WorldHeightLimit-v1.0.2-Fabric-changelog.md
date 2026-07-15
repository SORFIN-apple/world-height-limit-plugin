# WorldHeightLimit v1.0.2 - Fabric

## Added

- Added a Fabric server/client mod build for Minecraft 1.21.x.
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

## Safety

- The stable Fabric build is limited to Minecraft's validated height range: `319-2031`.
- The mod refuses to lower the configured world height through commands to avoid hiding or breaking existing blocks above the new limit.
- Invalid manual config values are rejected and repaired back to defaults.
- The mod only writes datapack files for the currently loaded world, not every singleplayer save.

## Important

- A full world/server restart is required after changing the height.
- Fabric API is required.
- This stable build does not bypass Minecraft's internal `2031` top-Y limit. Higher limits require a separate experimental mixin-based build.
