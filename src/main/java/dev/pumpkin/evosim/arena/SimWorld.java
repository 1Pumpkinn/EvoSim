package dev.pumpkin.evosim.arena;

import dev.pumpkin.evosim.config.SimConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The dedicated void world the simulation lives in. It is created on demand, never saved,
 * locked to daytime and clear weather, and nothing but EvoSim itself can spawn mobs in it.
 */
public final class SimWorld implements Listener {

    private static final long KEEP_DAY_INTERVAL_TICKS = 200L;
    private static final long NOON = 6000L;
    private static final int BORDER_MARGIN = 32;

    private final Plugin plugin;
    private final String name;
    private BukkitTask dayTask;

    public SimWorld(Plugin plugin, SimConfig.Dimension config) {
        this.plugin = plugin;
        this.name = sanitize(config.name());
    }

    private static String sanitize(String raw) {
        String cleaned = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "");
        return cleaned.isEmpty() ? "evosim" : cleaned;
    }

    /** Loads the simulation world, creating it first if it does not exist yet. */
    public World getOrCreate() {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .generator(new VoidGenerator())
                    .biomeProvider(new PlainsOnly())
                    .generateStructures(false)
                    .createWorld();
        }
        if (world == null) {
            throw new IllegalStateException("Could not create the simulation world '" + name + "'.");
        }
        world.setAutoSave(false);
        world.setStorm(false);
        world.setThundering(false);
        world.setTime(NOON);
        startDayLock(world);
        return world;
    }

    /** Fences the world to the arena so nobody wanders off into the void. */
    public void fence(World world, Location arenaCenter, int arenaSize) {
        world.getWorldBorder().setCenter(arenaCenter.getX(), arenaCenter.getZ());
        world.getWorldBorder().setSize(arenaSize + BORDER_MARGIN * 2.0);
        world.setSpawnLocation(arenaCenter);
    }

    /** Sends everyone in the simulation world back to the main world's spawn. */
    public void evacuate() {
        World world = Bukkit.getWorld(name);
        if (world == null) return;
        Location home = Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player player : List.copyOf(world.getPlayers())) {
            player.teleport(home);
        }
    }

    /** True if this helper was made for the same world name, so a reload can reuse it. */
    public boolean matches(SimConfig.Dimension config) {
        return name.equals(sanitize(config.name()));
    }

    public void stop() {
        if (dayTask != null) {
            dayTask.cancel();
            dayTask = null;
        }
        HandlerList.unregisterAll(this);
    }

    public boolean isSimWorld(World world) {
        return world != null && world.getName().equals(name);
    }

    private void startDayLock(World world) {
        if (dayTask != null) return;
        dayTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            World current = Bukkit.getWorld(name);
            if (current != null) current.setTime(NOON);
        }, KEEP_DAY_INTERVAL_TICKS, KEEP_DAY_INTERVAL_TICKS);
    }

    /** Only EvoSim's own spawns are allowed in the world. */
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!isSimWorld(event.getLocation().getWorld())) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (isSimWorld(event.getWorld()) && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    /** Generates nothing at all: an empty world for the arena to be built in. */
    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0, 65, 0);
        }
    }

    /** One plain biome everywhere, so the void has no stray biome borders. */
    private static final class PlainsOnly extends BiomeProvider {
        @Override
        public org.bukkit.block.Biome getBiome(WorldInfo world, int x, int y, int z) {
            return org.bukkit.block.Biome.PLAINS;
        }

        @Override
        public List<org.bukkit.block.Biome> getBiomes(WorldInfo world) {
            return List.of(org.bukkit.block.Biome.PLAINS);
        }
    }
}
