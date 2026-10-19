# Real-server height smoke test

This runner starts an isolated Paper server on `127.0.0.1` with a fresh temporary world. A Mineflayer player with the bypass permission tries to place stone at Y=2000; another player without it tries the same. The server checks both resulting blocks, then restarts once more to check persistence. Reports and logs are written under `work/height-smoke/<minecraft-version>/<run-id>/` by default. The test uses `online-mode=false` only on its loopback server.

## Setup

Install Node.js 22 or newer and run `npm ci` in this directory. Build the appropriate plugin project first. Set `JAVA_BIN` to a Java runtime supported by the target Paper version.

Example from the repository root in PowerShell:

```powershell
cd tools/height-smoke
npm ci
cd ../..
$env:MC_VERSION = '1.18.2'
$env:PLUGIN_JAR = (Resolve-Path '1.18.x/project/build/libs/WorldHeightLimit-v1.2.0-Paper-Purpur-1.18.x.jar').Path
$env:JAVA_BIN = 'C:\path\to\java17\bin\java.exe'
node tools/height-smoke/smoke.js
```

The runner selects the newest stable Paper build returned by Paper's download API and caches the JAR per version. Set `PAPER_BUILD` to a specific build ID to pin it, or `ALLOW_EXPERIMENTAL=1` to use a release for which Paper has no stable build. The selected build and channel are recorded in the report. Inspect `bootstrap.console.log`, `placement.console.log`, and `persistence.console.log` in that run directory if a check fails.

Protocol client errors that occur after connection are recorded in the report's `warnings` array. A gameplay `PASS` still requires the server-side block checks and persistence to pass; inspect warnings before treating the client as fully healthy.

For 26.1.1 through 26.3, set `BOT_VERSION=26.1` to use Mineflayer through ViaVersion and ViaBackwards 5.12.1. Minecraft 26.3 requires a `client_tick_end` packet after movement; the runner sends it for that server version. Paper 26.3 build 206 passed placement, denial and persistence with this route.

The matrix runner uses the 1.19.2 client protocol for server 1.19.1 and the 1.21.8 client protocol for server 1.21.7. The pinned `minecraft-data` package maps those two server names to older protocol numbers; the paired releases use the correct shared protocol numbers. Both mapped runs passed the full test.

Optional environment variables:

- `SMOKE_ROOT`: ASCII-only directory for older Paper builds that cannot launch from a path containing non-ASCII characters.
- `OBSERVER_JAR`: test-only plugin that logs high-altitude `BlockPlaceEvent` delivery and cancellation. Its source is `BlockObserver.java`; it is not part of the release plugin.
- `VANILLA_SERVER_JAR`: a separately downloaded Mojang server JAR to seed old Paper's cache when its downloader fails. Verify its hash against Mojang's version manifest first.
- `ENGINE_PROBE_ONLY=1`: run a vanilla `/setblock` height probe without loading the plugin or a bot. The JSON result is `PROBE`, never a plugin compatibility pass.

The test world is disposable. Do not point `SMOKE_ROOT` at an existing Minecraft save.

## Version matrix

`run-matrix.ps1` runs the same test across a supplied list of versions, selects the matching plugin JAR and Java runtime, and writes `matrix-summary.json` in the smoke root. The `1.20.5-1.20.6` run uses its dedicated project; the root project is selected only for `1.21-26.3`. Build each plugin project before running it. The script accepts `-Versions`, `-Java16`, `-Java17`, `-Java21`, `-Java25`, optional `-SmokeRoot`, and optional `-Vanilla118Jar` for the old Paper 1.18 downloader. It continues after individual version failures so the summary shows all results. The default list covers representative versions in each profile, including the passing 26.3 check.
