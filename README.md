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
  rate, grief sensitivity and ram rest time, plus cold resistance, heat tolerance, sure-footedness,
  digestion and mutability. They cross over and mutate like the others. Resistances and rich
  digestion cost a little extra upkeep everywhere, so they only pay off where they are needed.
- **Gene marks:** a gene in the outer quarter of its range is shown as a mutation (Swift, Giant,
  Long-lived, Efficient, Camel-like, Stoic, Quick recovery, Frostproof, Sun-hardened, Sure-footed,
  Rich digestion, Volatile and their opposites) on the stat bar and in the Field Guide.
- **Special mutations (procedurally generated):** when a sheep is born it has a small chance
  (`evolution.special-mutation-chance`, scaled by its mutability gene) to gain a brand new
  mutation. Each one is rolled from scratch: a random benefit (+10% to +35% on a random stat) and,
  most of the time, a random drawback on another stat, plus a generated name like *Fleet Marrow*
  or *Bogwalker Pelt*. Twelve stats can be affected (speed, vision, lifespan, upkeep, thirst, food
  energy, grief, ram rest, cold, desert thirst, slow ground, birth cost). Each parent passes a
  special mutation on with `evolution.special-mutation-inheritance`; a sheep carries at most three.
- **Mating cooldown:** after mating every sheep rests and looks for no mate until it ends. A ram's
  rest is `breeding.male-rest-seconds` scaled by his rest-time gene; ewes rest
  `breeding.female-rest-seconds`. Mating in a row no longer causes depression, it just waits.

## Rare food

About 4% of food spawns are **golden apples** (`food.rare-chance`, at most `food.rare-max` at once). They glow
gold, are worth `food.rare-energy-multiplier` times a berry, and also refill some water and lift mood.
They ignore the biome food rules, so they are relatively commoner in deserts and tundra. Sheep see them
as about twice as close as they are, so they will take a longer walk for one.

## Adaptation

Sheep slowly get better at surviving what has been killing them. Recent deaths are tallied by cause
and biome, and fade with a half-life (`adaptation.half-life-seconds`). The bigger the share of recent
deaths a cause has, the more newborns get a small nudge towards the genes that help against it:

| Recent deaths | Newborns get better at |
| --- | --- |
| Starvation | efficiency (lower upkeep), digestion, vision |
| Dehydration | thirst resistance, vision |
| Starving in the tundra | cold resistance, efficiency |
| Dying of thirst in the desert | heat tolerance, thirst resistance |
| Starving in forest or swamp | sure-footedness, speed |

Old age is no pressure. Natural selection still runs on top, and when a cause stops killing sheep the
pressure fades. See what the population is adapting to with `/evo stats` and `/evo deaths`. Tune or
disable it in the `adaptation:` section of `config.yml`.

## Field Guide

`/evo book`, then right-click a sheep. Page 1 is a contents page; click an entry to jump to it.
**Hover over any label, value or bar** to get a text box that explains it: what a gene does,
what its range is, which mutation it earned, what a special mutation changes and how a biome
treats this particular sheep. Pages: overview, body genes, survival genes, special mutations,
gene marks and needs. Every page has a `<< Contents` link back to page 1.

## Needs, mood and orientation

- **Thirst:** ponds are built into the arena floor. Hydration drains over time; thirsty
  sheep look for a pond and stand in it to drink. At zero they lose energy.
- **Depression:** mood falls while hungry, thirsty, alone or when others die nearby, and recovers otherwise.
  Depressed sheep are slower and will not mate. Eating and mating lift mood.
- **Orientation:** some sheep pair with their own sex. They have rainbow wool, pair up and
  show hearts like any pair, but no child is born. Rate: `orientation.same-sex-chance`.
- **Stat bars:** a floating bar over each sheep (`animation.stat-bars`).

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
