package dev.pumpkin.evosim;

import dev.pumpkin.evosim.arena.Arena;
import dev.pumpkin.evosim.arena.SimWorld;
import dev.pumpkin.evosim.command.EvoCommand;
import dev.pumpkin.evosim.config.SimConfig;
import dev.pumpkin.evosim.hud.Hud;
import dev.pumpkin.evosim.inspect.BreedingStick;
import dev.pumpkin.evosim.inspect.FieldGuide;
import dev.pumpkin.evosim.inspect.SheepTracker;
import dev.pumpkin.evosim.sim.BiomeMap;
import dev.pumpkin.evosim.sim.Pond;
import dev.pumpkin.evosim.sim.Simulation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Optional;

public final class EvoSimPlugin extends JavaPlugin {

    private SimConfig simConfig;
    private Simulation simulation;
    private Hud hud;
    private SimWorld simWorld;
    private SheepTracker sheepTracker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSimConfig();

        hud = new Hud(this);
        getServer().getPluginManager().registerEvents(hud, this);
        hud.start();

        FieldGuide fieldGuide = new FieldGuide(this);
        getServer().getPluginManager().registerEvents(fieldGuide, this);

        BreedingStick breedingStick = new BreedingStick(this);
        getServer().getPluginManager().registerEvents(breedingStick, this);

        sheepTracker = new SheepTracker(this);
        getServer().getPluginManager().registerEvents(sheepTracker, this);
        sheepTracker.start();

        PluginCommand command = Objects.requireNonNull(getCommand("evo"), "evo command missing from plugin.yml");
        EvoCommand executor = new EvoCommand(this, hud, fieldGuide, breedingStick, sheepTracker);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getLogger().info("EvoSim enabled. Use /evo create to build an arena.");
    }

    @Override
    public void onDisable() {
        if (sheepTracker != null) {
            sheepTracker.stop();
        }
        if (hud != null) {
            hud.stop();
        }
        if (simulation != null) {
            simulation.shutdown();
            simulation = null;
        }
        if (simWorld != null) {
            simWorld.stop();
        }
    }

    /** Re-reads config.yml and rebuilds the validated {@link SimConfig}. */
    public void reloadSimConfig() {
        reloadConfig();
        simConfig = SimConfig.load(getConfig(), getLogger());
    }

    public SimConfig simConfig() {
        return simConfig;
    }

    /** The current simulation, if an arena has been created. */
    public Optional<Simulation> simulation() {
        return Optional.ofNullable(simulation);
    }

    /**
     * Builds a fresh arena and creates a paused simulation in it. With the dimension
     * enabled the arena goes in the dedicated simulation world; otherwise it is built at
     * {@code fallbackOrigin} (its minimum corner; the floor is the block below). Any
     * previous simulation is shut down first. Throws IllegalArgumentException if the
     * arena does not fit the world's height limits.
     */
    public Simulation createSimulation(Location fallbackOrigin) {
        boolean dedicated = simConfig.dimension().enabled();
        Location origin = fallbackOrigin;
        if (dedicated) {
            if (simWorld == null || !simWorld.matches(simConfig.dimension())) {
                if (simWorld != null) simWorld.stop();
                simWorld = new SimWorld(this, simConfig.dimension());
                getServer().getPluginManager().registerEvents(simWorld, this);
            }
            World world = simWorld.getOrCreate();
            origin = new Location(world, 0, simConfig.dimension().originY(), 0);
        }
        Arena arena = new Arena(origin, simConfig.arena());
        if (simulation != null) {
            simulation.shutdown();
        }
        arena.purgeEntities();
        BiomeMap biomes = BiomeMap.generate(simConfig, new java.util.SplittableRandom());
        arena.build(Pond.layout(simConfig), biomes);
        arena.keepLoaded(this);
        if (dedicated) {
            simWorld.fence(arena.world(), arena.centerLocation(), arena.size());
        }
        simulation = new Simulation(this, simConfig, arena, biomes);
        simulation.onExtinction(() -> getServer().broadcast(
                Component.text("[EvoSim] The population went extinct. Use /evo reset to try again.",
                        NamedTextColor.RED)));
        return simulation;
    }

    /** Stops the simulation and removes the arena's blocks and entities. */
    public void removeSimulation() {
        if (simulation == null) return;
        Simulation old = simulation;
        simulation = null;
        if (simWorld != null) simWorld.evacuate();
        old.stop();
        old.arena().demolish();
        old.shutdown();
    }
}
