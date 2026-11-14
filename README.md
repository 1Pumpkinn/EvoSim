# EvoSim

Real-time natural selection inside Minecraft. Creatures are real sheep with
heritable traits (speed, size, vision, wool colour). They forage for food in
an arena and pay energy for their traits. Well-fed adults find a mate, stand
together under floating hearts, and have a child whose genes are a mix of
both parents plus mutation. Newborns grow up before they can mate. Watch the
population adapt.

## Quick start

1. `gradle runServer`, join, and `/op` yourself.
2. Run `/evo create`. The arena is built in its own void world (`dimension.*` in config.yml) and you are teleported into it. With `dimension.enabled: false` it is built from where you stand instead.
3. `/evo start`, then watch the boss bar. Try `/evo speed 5` and `/evo color speed`.

## Commands (permission `evosim.admin`, default op)

| Command | What it does |
| --- | --- |
| `/evo create` | Build the arena (in the simulation dimension) and teleport you into it |
| `/evo start` / `stop` | Run or pause the simulation |
| `/evo reset` | Wipe the ecosystem and respawn fresh founders |
| `/evo spawn [n]` | Add n founder creatures (default 10) |
| `/evo speed <0.1-100>` | Simulation speed multiplier (very high speeds with a large population can lower the server's TPS) |
| `/evo color <lineage\|speed\|size\|vision>` | What creature colours show (blue = low, red = high) |
| `/evo stats` | Population, mean traits, diversity, births and deaths |
| `/evo tp` | Teleport to the arena centre |
| `/evo hud` | Toggle the stats boss bar |
| `/evo book` | Give yourself the Field Guide (right-click a sheep to read its stats) |
| `/evo stick` | Give yourself the Breeding Stick (testing: right-click two sheep and they breed, whatever their sex or preference) |
| `/evo tracker` | Give yourself the Tracker: right-click a sheep to select it (glows, live panel on your action bar with an arrow and distance), right-click the air to browse all sheep, sneak + right-click a sheep to see through its eyes |
| `/evo sheep` | Open the sheep browser without the item: filter (thirsty, hungry, mating, ewes, rams...) and sort, then click to select, shift-click to teleport, right-click to view |
| `/evo deaths` | Open a dialog with what the sheep have died of (old age, starvation, dehydration), with counts, shares and average age |
| `/evo remove` | Remove the arena and all creatures, and send players back to the main world |
| `/evo reload` | Reload config.yml (applies to the next `/evo create`) |

## Biomes and movement

- **Biomes:** the arena (384 blocks wide by default) is split into patches of grassland, forest,
  desert, tundra and swamp (`biomes.regions`, `biomes.enabled`). Every `/evo create` rolls a new map: the patch count, patch sizes and biome mix all vary (`biomes.region-variance`, `biomes.size-variance`), so one biome may sprawl while another is tiny. Forests grow more food but slow
  walking, deserts have little food and fierce thirst, tundra burns extra energy, swamps slow
  walking but barely cause thirst. The Field Guide shows the biome a sheep stands in.
- **Water and food on the way:** thirsty sheep walk to a pond they can see, or one they have seen before and
  remember, even when it is out of sight. With no water known and no hunger they search in long straight
  stretches instead of grazing. A hungry sheep heading for water grabs food that lies close to the route
  (less of a detour when parched). The Field Guide and stat bar show what each sheep is doing.
- **Pathfinding:** sheep stick to one piece of food until it is eaten, turn at a limited rate,
  stop at their target instead of overshooting, steer away from walls and from each other, count
  food on slow ground as further away, and only stop to drink when they are thirsty.

## Mutations and breeding rest

- **Genes:** besides speed, size and vision, sheep carry lifespan, metabolism (upkeep), thirst
  rate, grief sensitivity and ram rest time. They cross over and mutate like the others.
- **Mutations:** a gene in the outer quarter of its range is shown as a mutation (Swift, Giant,
  Long-lived, Efficient, Camel-like, Stoic, Quick recovery and their opposites) on the stat bar
  and in the Field Guide's Mutations page.
- **Mating cooldown:** after mating every sheep rests and looks for no mate until it ends. A ram's
  rest is `breeding.male-rest-seconds` scaled by his rest-time gene; ewes rest
  `breeding.female-rest-seconds`. Mating in a row no longer causes depression, it just waits.

## Needs, mood and orientation

- **Thirst:** ponds are built into the arena floor. Hydration drains over time; thirsty
  sheep look for a pond and stand in it to drink. At zero they lose energy.
- **Depression:** mood falls while hungry, thirsty, alone or when others die nearby, and recovers otherwise.
  Depressed sheep are slower and will not mate. Eating and mating lift mood.
- **Orientation:** some sheep pair with their own sex. They have rainbow wool, pair up and
  show hearts like any pair, but no child is born. Rate: `orientation.same-sex-chance`.
- **Stat bars:** a floating bar over each sheep (`animation.stat-bars`).
- **Field Guide:** `/evo book`, then right-click a sheep for its full stats.

## Tilted mating pose

The mounting partner is drawn as a tilted model, which needs the resource pack in
`resourcepack/` (see its README). Set `animation.tilted-mount: false` in
`config.yml` to use the plain flat pose without it.

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
