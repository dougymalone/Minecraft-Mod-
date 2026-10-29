# Codeworks

Fabric mod for Minecraft **26.3** (Java 25, MIT). Scaffold for Create-inspired programmable machines driven by in-game scripts.

## Status

**Gameplay soft reset:** Breaker, Placer, Channel Adapter, wireless bus, and the machine script/Help UI were removed so we can rebuild from scratch. The Fabric project (build, `launch.bat`, modid `codeworks`, MIT) stays. Scripting and machines will return in later PRs.

## Requirements

- **JDK 25**
- Fabric Loader / Loom (versions pinned in `gradle.properties`)

## Build & run

**Windows:** double-click `launch.bat` (uses JDK 25 if present, then runs the client).

```bash
./gradlew build
./gradlew runClient
```

## License

MIT — see [LICENSE](LICENSE).
