# World Height Limit 1.0.0

## Release Summary

Initial public release of World Height Limit.

## Added

- Support for Paper `1.20.5` through `1.21.11`
- Automatic version-aware datapack generation for supported server versions
- Higher world build height with configurable top limit
- Admin-only access above the normal vanilla build limit
- Per-dimension restriction settings for Overworld, Nether, and End
- Separate restriction toggles for:
  - block placing
  - block breaking
  - bucket use
- Configurable permission nodes
- Configurable player/admin messages
- Notification modes:
  - chat
  - action bar
  - title
  - both
  - off
- Notification cooldown to reduce message spam
- `/highbuildlimit reload`
- `/highbuildlimit status`
- `/highbuildlimit fill`
- `/highbuildlimit platform`

## Notes

- World height changes require a full server restart.
- Existing worlds should be backed up before first use.
- The plugin can suppress repeated heightmap warning spam in console with the config option:
  - `logging.suppress-heightmap-warnings: true`
