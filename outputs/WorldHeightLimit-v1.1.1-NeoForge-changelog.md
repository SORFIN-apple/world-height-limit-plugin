# WorldHeightLimit v1.1.1 - NeoForge Beta

## Added

- Added NeoForge builds for Minecraft 1.20.2-1.21.x.
- Added support for both singleplayer worlds and dedicated NeoForge servers.
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
  - `/worldheightlimit set <maxY> confirm`
- Added legacy `/highbuildlimit` command alias.

## Safety

- Stable height range is limited to Minecraft's validated range: `319-2031`.
- Lowering height through commands requires explicit `confirm`.
- Invalid manual config values are rejected and repaired back to defaults.

## Files

- `WorldHeightLimit-v1.1.1-NeoForge-1.20.2-1.20.4.jar`
- `WorldHeightLimit-v1.1.1-NeoForge-1.20.5-1.20.6.jar`
- `WorldHeightLimit-v1.1.1-NeoForge-1.21.x.jar`

## Important

- NeoForge `21.11+` is required.
- A full world/server restart is required after changing the height.
- This build does not bypass Minecraft's internal `2031` top-Y limit. Higher limits require a separate experimental build.
