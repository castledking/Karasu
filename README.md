<p align="center">
<img alt="Karasu" width=100% height=auto src="https://castled.codes/assets/karasu-banner.png">
</p>

# <p align="center">A plugin inspired by Itachi's crow morphing abilities</p>

## Features

- Transform players into crows with scaling (0.6x)
- Two models: `crow_fly` while flying in the air, `crow_stand` otherwise (swapped automatically)
- Automatic transformation on teleportation and spectator mode
- Spawn crows at location
- BetterModel integration for animated crow models, with ModelEngine kept as a fallback
- Resource pack management
- Cross-plugin compatibility (Essentials, Citizens, Nexo)

## Model engines

Karasu draws crows through one plugin at a time, picked by `karasu.visual.model-engine`:

| Value | Behaviour |
| --- | --- |
| `auto` (default) | Prefer BetterModel, fall back to ModelEngine |
| `bettermodel` | BetterModel only, never falls back |
| `modelengine` | ModelEngine only |
| `none` | No models; players still transform, the crow is just invisible |

**Model support differs by Minecraft version.** BetterModel targets 26.3; ModelEngine has not been
updated for it yet. On 26.3 `auto` picks BetterModel, and on 26.2 it falls back to ModelEngine.
Pinning `modelengine` on 26.3 gives you no models and a warning at startup — use `auto` unless you
have a reason not to.

Model names are configured per engine, since the two live in different namespaces:

```yaml
karasu:
  visual:
    model-engine: auto
    models:            # ModelEngine blueprint names
      flying: crow_fly
      perched: crow_stand
    bettermodel:       # BetterModel model names
      models:
        flying: crow_fly
        perched: crow_stand
```

`/karasu debug` reports the active engine, what was requested, whether each engine plugin is installed
and usable, and whether both model ids are actually loaded.

## Commands

- `/karasu morph` - Toggle crow transformation
- `/karasu info` - Show crow transformation status
- `/karasu debug` - Show model engine integration status
- `/karasu spawn [count]` - Spawn crows at location
- `/karasu reload` - Reload the config and re-pick the model engine

Aliased as `/kcrows` and `/crow`, so `/crow spawn 3` still works.

## Configuration

Configuration is managed through `src/main/resources/karasu.yml`

## Building

To build the plugin:
```bash
mvn clean package -DskipTests
```

The JAR file will be located at `target/Karasu.jar`

Karasu compiles against `paper-api` and `bettermodel-bukkit-api`, both `provided`. BetterModel binds
through `softdepend`, and ModelEngine is bound reflectively, so **neither model engine is required to
build** — in particular ModelEngine's premium jar is no longer needed. `libs/` is only needed for the
UnlimitedNametags API jars.
