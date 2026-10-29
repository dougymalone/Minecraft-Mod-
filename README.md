# Codeworks

Fabric mod for Minecraft **26.3**: Create-inspired programmable machines driven by short C#-like scripts you edit in-game.

## What’s in

- **Breaker** — breaks blocks in front
- **Placer** — stores **one** block item and places it in front (`Place()`)
- In-game script editor + **Help** (API, settings, language notes)
- C#-like dialect: `if` / `else` / `while` / `for`, types `int`/`bool`/`string`, functions, arrays
- Host API: `Harvest`/`BreakFront`, `Place`, `Log`, `Sense`, `HasBlock`, `HasStoredBlock`/`HasItem`, `Extended`/`IsPistonExtended`, `Move`, `Wait`, `Send`/`Receive`
- Settings (persisted): `speed` (1–20), `range` (1–3), `channel` (wireless network id)
- Wireless: same-dimension, global-per-dimension signal bus (in-memory for the server session)

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

1. Creative: **Breaker** / **Placer** from Redstone or Functional tabs  
   Survival: craft Breaker (`I C I / C R C / I C I`) or Placer (`I C I / C D C / I R I` with dispenser)
2. Face machines at the blocks you care about; set the same `channel = "line1"` to link them
3. Placer: right-click with a block item to insert; shift-click empty-handed to extract
4. Right-click → edit script / open **Help** → Save

## License

MIT — see [LICENSE](LICENSE).
