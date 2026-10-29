# Codeworks

Fabric mod for Minecraft **26.3**: Create-inspired programmable machines driven by short C#-like scripts you edit in-game.

## What’s in

- **Breaker** machine (faces a direction — “in front”)
- In-game script editor + **Help** (API, settings, language notes)
- C#-like dialect: `if` / `else` / `while` / `for`, types `int`/`bool`/`string`
- Host API: `Harvest`/`BreakFront`, `Log`, `Sense`, `HasBlock`, `Move`, `Wait`
- Settings (persisted): `speed` (1–20), `range` (1–3), `autoPickup`

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

1. Creative: **Breaker** from Redstone or Functional tabs  
   Survival: craft iron + copper + redstone (`I C I / C R C / I C I`)
2. Place so the front faces breakable blocks; optional chest behind for `autoPickup`
3. Right-click → edit script / open **Help** → Save

## License

MIT — see [LICENSE](LICENSE).
