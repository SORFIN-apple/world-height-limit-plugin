# World Height Limit

World Height Limit raises the build ceiling by creating a datapack for each world. By default, the target is Y=2031. You can set a different height in the config and choose who may build above the vanilla limit.

## Server plugin

Available for Bukkit, Spigot, Paper, Purpur and Folia on Minecraft 1.17–26.3. Download the JAR for your Minecraft version. The 1.20.5–1.20.6 builds are separate from the neighboring version ranges.

The plugin provides separate settings for the Overworld, Nether and End; permissions for high-altitude building; configurable messages; and commands for status, debugging, filling and creating platforms. The `/highbuildlimit` alias and `highbuildlimit.*` permissions remain available.

**Installation:** Put the matching JAR in `plugins/` and start the server. On a fresh installation, the first start writes the datapack. Fully stop and start the server again to apply the new world height. A full restart is also needed after changing the configured height.

Back up an existing world before changing its height. Keep the generated datapack installed if you have built above the vanilla limit.

Anonymous server statistics are available on [bStats](https://bstats.org/plugin/bukkit/World%20Height%20Limit/34825). Server owners can disable reporting in `plugins/bStats/config.yml`.

## Fabric and NeoForge

Separate beta builds are available for Fabric and NeoForge. They have their own version compatibility and installation requirements; check the selected download before installing. Fabric requires Fabric API.

[Source code](https://github.com/SORFIN-apple/world-height-limit-plugin)
