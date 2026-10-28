# Codeworks

Fabric mod for Minecraft **26.3**: Create-inspired programmable machines driven by short C#-like scripts you edit in-game.

## What’s in v0.2

- **Coder** machine block (faces a direction — “in front”)
- In-game script editor (right-click the Coder)
- Tiny C#-like interpreter with host API `Harvest()` / `BreakFront()` and `Log(string)`
- Default sample program breaks the block in front (every ~1s, with harvest cooldown)

## Requirements

- **JDK 25**
- Fabric Loader / Loom (versions pinned in `gradle.properties`)

## Build & run

```bash
./gradlew build
./gradlew runClient
```

Artifacts land in `build/libs/`.

## Try it in-game

1. Creative: grab **Coder** from the Redstone or Functional Blocks tab  
   Survival: craft with iron, copper, and redstone (`I C I / C R C / I C I`)
2. Place the Coder so its front faces a breakable block (dirt, log, etc.)
3. Right-click to open the editor — default script is `Harvest();`
4. Save and wait (~1 second between runs; harvest cooldown ~2 seconds)

## License

MIT — see [LICENSE](LICENSE).
