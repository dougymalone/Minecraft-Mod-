# Codeworks

Fabric mod for Minecraft **26.3**: **Central Command** runs all C#-like scripts and drives actuator machines over **channels**.

## Architecture

- **Central Command** — only place scripts live (Channels / Breaker / Placer stub / Script / Help tabs)
- **Breaker** — channel selector only; Command calls `Break("channel")` to harvest in front
- Placer + Channel Adapter return in later PRs

## Requirements

- **JDK 25**
- Fabric Loader / Loom (versions pinned in `gradle.properties`)

## Build & run

**Windows:** double-click `launch.bat`.

```bash
./gradlew build
./gradlew runClient
```

## Try it

1. Place **Central Command** → Channels tab → add `mine`
2. Place **Breaker** facing blocks → set channel to `mine`
3. Script tab (default script) → Save → Command breaks via those Breakers

## License

MIT — see [LICENSE](LICENSE).
