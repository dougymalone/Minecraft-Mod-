# Codeworks

Fabric mod for Minecraft **26.3**: Create-inspired programmable machines driven by short C#-like scripts you edit in-game.

## What’s in

- **Breaker** — breaks blocks in front
- **Placer** — stores **one** block item and places it in front (`Place()`)
- **Channel Adapter** — thin panel on a piston/redstone device; shared wireless channel for state + activation
- In-game script editor + **Help** (API, settings, language notes)
- C#-like dialect: `if` / `else` / `while` / `for`, types `int`/`bool`/`string`, functions, arrays
- Host API: harvest/place/sense, wireless `Send`/`Receive`, adapter helpers `Pulse` / `SetPower` / `GetExtended` / `GetPower` / `GetHost`
- Settings (persisted): `speed` (1–20), `range` (1–3), `channel` (wireless network id)
- **Shared wireless bus** — Breaker, Placer, Channel Adapter, and any future scripted machines all talk on the same channels (same dimension, in-memory for the server session)

> Create belts/funnels into the Placer are planned next — Codeworks stays standalone Fabric for now.

## Requirements

- **JDK 25**
- Fabric Loader / Loom (versions pinned in `gradle.properties`)

## Build & run

**Windows:** double-click `launch.bat` (uses JDK 25 if present, then runs the client).

```bash
./gradlew build
./gradlew runClient
```


## Try it in-game

1. Creative: **Breaker** / **Placer** / **Channel Adapter** from Redstone or Functional tabs  
2. Place a Channel Adapter on the **back** of a piston; right-click to set `channel` (e.g. `gate`)
3. On a Breaker or Placer script: `Pulse("gate")` to extend, `GetExtended("gate")` to read state — same bus works from every machine
4. Placer: right-click with a block item to insert; shift-click empty-handed to extract
5. Right-click machines → edit script / open **Help** → Save

## License

MIT — see [LICENSE](LICENSE).
