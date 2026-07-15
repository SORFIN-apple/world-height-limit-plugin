# World Height Limit 1.19.x

## Status

Smoke-tested and working.

## Notes

- Separate project created for `1.19` through `1.19.4`
- Test platform set: Bukkit, Spigot, Paper, Purpur, and Folia
- Verified on `1.19.4`: startup, config creation, datapack generation, and high-height building work
- Spigot/Bukkit port compiles against Spigot API and no longer requires Paper Adventure APIs
- Height values effectively work in 16-block world sections, so arbitrary tops like `2010` may not map exactly and should be tested carefully
