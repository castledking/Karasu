<p align="center">
<img alt="Karasu" width=100% height=auto src="https://castled.codes/assets/karasu-banner.png">
</p>

# <p align="center">A plugin inspired by Itachi's crow morphing abilities</p>

## Features

- Transform players into crows with scaling (0.6x)
- Two models: `crow_fly` while flying in the air, `crow_stand` otherwise (swapped automatically)
- Automatic transformation on teleportation and spectator mode
- Spawn crows at location
- ModelEngine integration for animated crow models
- Resource pack management
- Cross-plugin compatibility (Essentials, Citizens, Nexo, ModelEngine)

## Commands

- `/crows morph` - Toggle crow transformation
- `/crows info` - Show crow transformation status
- `/crow spawn [count]` - Spawn crows at location

## Configuration

Configuration is managed through `src/main/resources/karasu.yml`

## Building

To build the plugin:
```bash
mvn clean package -DskipTests
```

The JAR file will be located at `target/Karasu.jar`
