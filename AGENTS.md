# AGENTS.md

## Project Overview

**Booster Mod** — Minecraft 1.21.1 Fabric mod adding upgradeable "Booster Leggings" (7 tiers: Wood → Netherite). Players press `Z` to trigger a jet-style boost. Client→server networking model with server-side validation.

**Tech stack**: Java 21, Gradle 9.4.1 (wrapper), Fabric Loom 1.16-SNAPSHOT, Fabric API 0.116.11+1.21.1.

## Cursor Cloud specific instructions

### JDK Path Override

`gradle.properties` contains a hardcoded Windows JDK path (`org.gradle.java.home=C:/Program Files/...`). On Linux, override it by creating `~/.gradle/gradle.properties` with:

```
org.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64
```

This file is set up by the update script automatically.

### Build & Run

- **Build**: `./gradlew build` (compiles mod, produces JAR in `build/libs/`)
- **Run server** (headless): `./gradlew runServer` — first run requires `echo "eula=true" > run/eula.txt`
- **Run client** (requires display): `./gradlew runClient`
- **No automated tests** exist in this project; `./gradlew test` reports NO-SOURCE.

### Notes

- The Gradle wrapper (`./gradlew`) auto-downloads the correct Gradle version on first run. Subsequent builds are fast (under 1s when cached).
- The `run/` directory is the Minecraft server's working directory (generated at runtime, gitignored).
- CI (`.github/workflows/build.yml`) uses JDK 25 on Ubuntu; the project's source/target compatibility is Java 21.
