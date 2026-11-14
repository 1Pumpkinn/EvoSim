# EvoSim

Real-time natural selection inside Minecraft. Creatures are real sheep with
heritable traits (speed, size, vision, wool colour). They forage for food in
an arena and pay energy for their traits. Well-fed adults find a mate, hop
together under floating hearts, and have a child whose genes are a mix of
both parents plus mutation. Newborns grow up before they can mate. Watch the
population adapt.

## Quick start

1. `gradle runServer`, join, and `/op` yourself.
2. Stand where you want one corner of the arena and run `/evo create`.
3. `/evo start`, then watch the boss bar. Try `/evo speed 5` and `/evo color speed`.

## Commands (permission `evosim.admin`, default op)

| Command | What it does |
| --- | --- |
| `/evo create` | Build an arena from your position and teleport you into it |
| `/evo start` / `stop` | Run or pause the simulation |
| `/evo reset` | Wipe the ecosystem and respawn fresh founders |
| `/evo spawn [n]` | Add n founder creatures (default 10) |
| `/evo speed <0.1-20>` | Simulation speed multiplier |
| `/evo color <lineage\|speed\|size\|vision>` | What creature colours show (blue = low, red = high) |
| `/evo stats` | Population, mean traits, diversity, births and deaths |
| `/evo tp` | Teleport to the arena centre |
| `/evo hud` | Toggle the stats boss bar |
| `/evo remove` | Remove the arena and all creatures |
| `/evo reload` | Reload config.yml (applies to the next `/evo create`) |

## Requirements

- Paper 26.2
- JDK 25 (Paper 26.1+ requires Java 25)
- Gradle 9.1+ (`gradle build`, or open in IntelliJ)

## Build

    gradle build

The jar is written to `build/libs/`.

## Run a test server

    gradle runServer

Downloads Paper 26.2, builds the plugin and starts a server in `run/`.
On first start, accept the EULA by setting `eula=true` in `run/eula.txt`
and run the task again.
