# WorldHeightLimit 1.0.1

## Fixed

- Fixed the plugin failing to start when `target-max-y` produced a world height not divisible by 16.
- Arbitrary height limits such as `2010` and `2032` are now supported.
- Generated datapack height is automatically aligned with Minecraft's 16-block world sections.
- Building remains restricted to the exact configured `target-max-y`, even when the technical world height is rounded up.
- Added the effective technical world height to the status command.

## Changed

- Renamed the main command from `/highbuildlimit` to `/worldheightlimit`.
- Updated plugin branding, messages, main class, and internal package to use the official `WorldHeightLimit` name.
- Removed obsolete duplicate classes and reduced the JAR size.
- Existing `highbuildlimit.*` permission nodes remain unchanged for compatibility.

## Important

A full server restart is required after installing the plugin or changing `target-max-y`.

On a fresh installation, start the server once to generate the datapack, then fully stop and start it again so Minecraft can load the new world height.
