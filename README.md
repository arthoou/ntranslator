# NTranslator 🌐

NTranslator gives books and signs a lightweight, on-the-fly translation. It's built on top of the translation system from the NEXEL project, trimmed down to focus purely on in-game GUIs — no chat translation, just books and signs done well.

> 📦 Also available on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ntranslator).

## Versions

| Loader | Minecraft | Mod version | JDK | Source |
|---|---|---|---|---|
| forge | 1.18.2 | 2.0-beta | 17 | [forge/1.18.2](forge/1.18.2) |
| forge | 1.20.1 | 2.0-beta | 17 | [forge/1.20.1](forge/1.20.1) |
| forge | 1.21.1 | 2.0-beta | 21 | [forge/1.21.1](forge/1.21.1) |
| neoforge | 1.21.1 | 2.0-beta | 21 | [neoforge/1.21.1](neoforge/1.21.1) |

## Building from source

Install the JDK listed above for the version you want, then build from inside that folder:

```sh
cd neoforge/1.21.1
./gradlew build
```

On Windows, use `gradlew.bat build` instead. The finished jar lands in `build/libs/` for that version. The very first build will take a little longer, since Gradle needs to download itself and every dependency the project declares.

## About this repository

This repo keeps the source code and resources for every published version, each in its own folder. Local caches, test worlds, compiled output and backup copies are intentionally left out — only the real, published code lives here.

## License & credits

Every license, credit and notice file that shipped with each version has been kept as-is. Check the metadata for the version you're looking at before redistributing — publishing the source here doesn't change any declared license.
