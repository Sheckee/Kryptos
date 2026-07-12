# Building Kryptos

This mod is set up as a Gradle project, same layout as Anuken's official
[mindustry-example-java-mod](https://github.com/Anuken/mindustry-example-java-mod).

## One-time setup (do this locally — this environment has no internet access)

1. Install **JDK 17**.
2. Install Gradle (or just let the wrapper handle it — see below).
3. From the project root, generate the Gradle wrapper:
   ```
   gradle wrapper --gradle-version 8.5
   ```
   This creates `gradlew`, `gradlew.bat`, and `gradle/wrapper/`. Commit these
   to your repo so nobody else needs Gradle pre-installed.

## Building

```
./gradlew jar        # compiles + builds Kryptos.jar in build/libs/
./gradlew deploy      # builds a full mod package (jar + sprites + mod.hjson)
                       # into build/libs/KryptosDeploy.jar — rename this to
                       # Kryptos.zip and drop it in your Mindustry mods folder,
                       # or attach it to a GitHub Release.
```

## Project layout

```
Kryptos/
├── build.gradle          Gradle build config (Mindustry v146 API deps)
├── settings.gradle
├── gradle.properties
├── mod.hjson              Mod metadata, main class = kryptos.KryptosMod
├── STYLE.md                Visual style guide (palette, sprite rules, tiers)
├── src/kryptos/
│   └── KryptosMod.java     Entry point — register content here
├── content/                JSON/HJSON content defs (items, blocks, units)
├── sprites/                PNG sprites, sorted by category
├── sounds/, bundles/, maps/
```

## Development loop

Point Mindustry's `mods/` folder at a symlink to this repo (or just copy
`mod.hjson`, `src/`, `sprites/`, `content/` in after each `./gradlew deploy`)
and launch the game with `--debug` to get mod loading logs.
