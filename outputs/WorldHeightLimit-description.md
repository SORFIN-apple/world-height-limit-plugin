# World Height Limit

## Short Summary

Expand world height on Paper 1.20.5-1.21.11 while keeping the normal vanilla height limit for regular players.

## Full Description

World Height Limit is a Paper plugin for 1.20.5-1.21.11 that increases world build height by generating a version-aware datapack in the world save folder.

It is made for servers that want extra vertical build space without giving that access to everyone.

## What It Does

- raises the top world height
- keeps the normal vanilla limit for regular players
- lets admins or permitted players build above that limit
- provides configurable restrictions in `config.yml`

## Features

- automatic datapack generation
- version-aware datapack generation for supported server versions
- configurable build restrictions
- separate settings for Overworld, Nether, and End
- separate toggles for placing, breaking, and buckets
- customizable permissions and messages
- chat, action bar, title, both, or no notifications
- admin commands for reload and status

## Commands

- `/highbuildlimit help`
- `/highbuildlimit reload`
- `/highbuildlimit status`

## Permissions

- `highbuildlimit.bypass`
- `highbuildlimit.reload`
- `highbuildlimit.status`

## Notes

- Make a backup before first use.
- Height changes require a full server restart.
- Reload updates config-driven restrictions and messages, but not active world height by itself.

## One-Line Version

Expand world height on Paper and reserve high-altitude building for admins or permitted players.
