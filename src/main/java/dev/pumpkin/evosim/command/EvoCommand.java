package dev.pumpkin.evosim.command;

import dev.pumpkin.evosim.EvoSimPlugin;
import dev.pumpkin.evosim.config.SimConfig;
import dev.pumpkin.evosim.hud.Hud;
import dev.pumpkin.evosim.inspect.BreedingStick;
import dev.pumpkin.evosim.inspect.DeathStatsDialog;
import dev.pumpkin.evosim.inspect.FieldGuide;
import dev.pumpkin.evosim.inspect.SheepTracker;
import dev.pumpkin.evosim.render.ColorMode;
import dev.pumpkin.evosim.sim.Simulation;
import dev.pumpkin.evosim.sim.Stats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Handles {@code /evo <subcommand>}. */
public final class EvoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "start", "stop", "reset", "spawn", "speed",
            "color", "stats", "tp", "remove", "hud", "book", "stick", "tracker", "sheep", "deaths", "reload");

    private final EvoSimPlugin plugin;
    private final Hud hud;
    private final FieldGuide fieldGuide;
    private final BreedingStick breedingStick;
    private final SheepTracker sheepTracker;

    public EvoCommand(EvoSimPlugin plugin, Hud hud, FieldGuide fieldGuide, BreedingStick breedingStick,
                      SheepTracker sheepTracker) {
        this.plugin = plugin;
        this.hud = hud;
        this.fieldGuide = fieldGuide;
        this.breedingStick = breedingStick;
        this.sheepTracker = sheepTracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender);
            case "start" -> withSimulation(sender, sim -> {
                sim.start();
                ok(sender, "Simulation running at x" + sim.timeScale() + ".");
            });
            case "stop" -> withSimulation(sender, sim -> {
                sim.stop();
                ok(sender, "Simulation paused.");
            });
            case "reset" -> withSimulation(sender, sim -> {
                sim.reset();
                ok(sender, "Ecosystem reset with fresh founders.");
            });
            case "spawn" -> spawn(sender, args);
            case "speed" -> speed(sender, args);
            case "color" -> color(sender, args);
            case "stats" -> withSimulation(sender, sim -> stats(sender, sim));
            case "tp" -> teleport(sender);
            case "remove" -> remove(sender);
            case "hud" -> toggleHud(sender);
            case "book" -> giveBook(sender);
            case "stick" -> giveStick(sender);
            case "tracker" -> giveTracker(sender);
            case "sheep" -> openSheepList(sender);
            case "deaths" -> withSimulation(sender, sim -> deaths(sender, sim));
            case "reload" -> {
                plugin.reloadSimConfig();
                ok(sender, "Config reloaded. It applies the next time you run /evo create.");
            }
            default -> usage(sender);
        }
        return true;
    }

    private void create(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            error(sender, "Only players can create an arena.");
            return;
        }
        Simulation sim;
        try {
            sim = plugin.createSimulation(player.getLocation());
        } catch (IllegalArgumentException e) {
            error(sender, e.getMessage());
            return;
        }
        player.teleport(sim.arena().centerLocation());
        hud.show(player);
        ok(sender, plugin.simConfig().dimension().enabled()
                ? "Arena built in its own dimension. Use /evo start to begin."
                : "Arena built from your position. Use /evo start to begin.");
    }

    private void spawn(CommandSender sender, String[] args) {
        withSimulation(sender, sim -> {
            Integer count = args.length > 1 ? parseInt(args[1]) : Integer.valueOf(10);
            if (count == null || count < 1 || count > 500) {
                error(sender, "Usage: /evo spawn [1-500]");
                return;
            }
            int added = sim.spawnCreatures(count);
            ok(sender, "Spawned " + added + " founder creature" + (added == 1 ? "" : "s")
                    + (added < count ? " (population cap reached)." : "."));
        });
    }

    private void speed(CommandSender sender, String[] args) {
        withSimulation(sender, sim -> {
            Double value = args.length > 1 ? parseDouble(args[1]) : null;
            if (value == null) {
                error(sender, "Usage: /evo speed <0.1-" + (int) SimConfig.MAX_TIME_SCALE + ">  (currently x" + sim.timeScale() + ")");
                return;
            }
            sim.setTimeScale(value);
            ok(sender, "Time scale set to x" + sim.timeScale() + ".");
        });
    }

    private void color(CommandSender sender, String[] args) {
        withSimulation(sender, sim -> {
            Optional<ColorMode> mode = args.length > 1 ? ColorMode.parse(args[1]) : Optional.empty();
            if (mode.isEmpty()) {
                error(sender, "Usage: /evo color <lineage|speed|size|vision>  (currently "
                        + sim.colorMode().name().toLowerCase(Locale.ROOT) + ")");
                return;
            }
            sim.setColorMode(mode.get());
            ok(sender, "Colouring creatures by " + mode.get().name().toLowerCase(Locale.ROOT) + ".");
        });
    }

    private void stats(CommandSender sender, Simulation sim) {
        Stats s = sim.stats();
        int max = plugin.simConfig().population().max();
        sender.sendMessage(Component.text("--- EvoSim stats ---", NamedTextColor.GOLD));
        line(sender, String.format(Locale.ROOT, "Population %d/%d, food %d", s.population(), max, s.food()));
        line(sender, String.format(Locale.ROOT, "Time %s, top generation %d", formatTime(s.elapsedSeconds()), s.maxGeneration()));
        line(sender, String.format(Locale.ROOT, "Mean speed %.2f, size %.2f, vision %.1f", s.meanSpeed(), s.meanSize(), s.meanVision()));
        line(sender, String.format(Locale.ROOT, "Diversity %.2f, births %d, deaths %d", s.diversity(), s.births(), s.deaths()));
        line(sender, String.format(Locale.ROOT, "Mean water %.0f, mood %.0f, depressed %d, same-sex pairing %d",
                s.meanHydration(), s.meanMood(), s.depressed(), s.sameSex()));
    }

    private void teleport(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            error(sender, "Only players can teleport.");
            return;
        }
        withSimulation(sender, sim -> player.teleport(sim.arena().centerLocation()));
    }

    private void remove(CommandSender sender) {
        if (plugin.simulation().isEmpty()) {
            error(sender, "There is no arena to remove.");
            return;
        }
        plugin.removeSimulation();
        ok(sender, "Arena and creatures removed.");
    }

    private void giveBook(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            error(sender, "Only players can hold a field guide.");
            return;
        }
        player.getInventory().addItem(fieldGuide.createItem());
        ok(sender, "Right-click a sheep with the Field Guide to read its stats.");
    }

    private void giveStick(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            error(sender, "Only players can hold a breeding stick.");
            return;
        }
        player.getInventory().addItem(breedingStick.createItem());
        ok(sender, "Right-click two sheep with the Breeding Stick to make them breed.");
    }

    private void giveTracker(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            error(sender, "Only players can hold a tracker.");
            return;
        }
        player.getInventory().addItem(sheepTracker.createItem());
        ok(sender, "Right-click a sheep to select it, or the air to browse every sheep.");
    }

    private void openSheepList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            error(sender, "Only players can browse sheep.");
            return;
        }
        sheepTracker.openMenu(player);
    }

    private void deaths(CommandSender sender, Simulation sim) {
        if (sender instanceof Player player) {
            DeathStatsDialog.show(player, sim);
            return;
        }
        var tallies = sim.deathTallies();
        if (tallies.isEmpty()) line(sender, "No sheep have died yet.");
        tallies.forEach(t -> line(sender, String.format(Locale.ROOT, "%s: %d (average age %.0f s)",
                t.cause().label(), t.count(), t.meanAgeSeconds())));
    }

    private void toggleHud(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            error(sender, "Only players have a HUD.");
            return;
        }
        ok(sender, hud.toggle(player) ? "HUD shown." : "HUD hidden.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "color" -> filter(Arrays.stream(ColorMode.values())
                        .map(m -> m.name().toLowerCase(Locale.ROOT)).toList(), args[1]);
                case "speed" -> filter(List.of("0.5", "1", "2", "5", "10", "20", "50", "100"), args[1]);
                case "spawn" -> filter(List.of("10", "25", "50", "100"), args[1]);
                default -> List.of();
            };
        }
        return List.of();
    }

    // --- helpers ---

    private void withSimulation(CommandSender sender, java.util.function.Consumer<Simulation> action) {
        Optional<Simulation> sim = plugin.simulation();
        if (sim.isEmpty()) {
            error(sender, "No arena yet. Stand where you want it and run /evo create.");
            return;
        }
        action.accept(sim.get());
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(p)).toList();
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDouble(String text) {
        try {
            double v = Double.parseDouble(text);
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatTime(double seconds) {
        long s = (long) seconds;
        return String.format(Locale.ROOT, "%dm %02ds", s / 60, s % 60);
    }

    private static void usage(CommandSender sender) {
        error(sender, "Usage: /evo <" + String.join("|", SUBCOMMANDS) + ">");
    }

    private static void ok(CommandSender sender, String text) {
        sender.sendMessage(Component.text("[EvoSim] ", NamedTextColor.GREEN)
                .append(Component.text(text, NamedTextColor.WHITE)));
    }

    private static void error(CommandSender sender, String text) {
        sender.sendMessage(Component.text("[EvoSim] ", NamedTextColor.RED)
                .append(Component.text(text, NamedTextColor.WHITE)));
    }

    private static void line(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.GRAY));
    }
}
