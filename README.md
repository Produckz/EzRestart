# EzRestart

A lightweight, server-side Fabric mod that restarts your server without kicking anyone. Instead of stopping at a fixed time, EzRestart flags the server for a restart and then waits until it is empty, so players never get interrupted mid-session.

## How it works

Once per day at a configurable time (default 04:00, with an optional timezone setting), the server is automatically flagged. The next time the player count hits zero, the server shuts down cleanly and your wrapper script or host panel brings it back up. If the shutdown hangs, a watchdog forces the JVM to exit after a configurable timeout so the server never gets stuck half-stopped.

## Commands

- `/ezrestart flag` -- flags the server to restart the next time it is empty
- `/ezrestart cancel` -- clears a pending restart flag
- `/ezrestart status` -- shows the current flag and settings
- `/ezrestart reload` -- reloads the config from disk
- `/ezrestart set time <HH:mm>` -- sets the daily restart time (24hr e.g. 22:30)
- `/ezrestart set auto <true|false>` -- toggles the daily auto-flag
- `/ezrestart set timezone <zone>` -- sets the scheduling timezone (IANA id like `America/New_York`, or `system`)
- `/ezrestart set timeout <seconds>` -- sets the shutdown watchdog timeout (0 disables it)

Commands use Fabric Permissions API nodes (`ezrestart.command.*`) and fall back to vanilla OP levels.

## Configuration

Settings live in `config/ezrestart.json` and can also be changed in-game with the `set` subcommands.

## Notes

EzRestart stops the server; something else has to start it again. Run it under a restart-on-exit wrapper script, systemd unit, or a host panel with auto-restart enabled.
