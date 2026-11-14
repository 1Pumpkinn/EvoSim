package dev.pumpkin.evosim.inspect;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import dev.pumpkin.evosim.EvoSimPlugin;
import dev.pumpkin.evosim.config.SimConfig;
import dev.pumpkin.evosim.render.CreatureView;
import dev.pumpkin.evosim.sim.Creature;
import dev.pumpkin.evosim.sim.Simulation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The EvoSim Tracker: pick sheep out of the herd and keep an eye on them. Get one with
 * {@code /evo tracker}.
 *
 * <ul>
 *   <li>Right-click a sheep: select it. It glows and a live panel (needs, what it is doing, and an
 *       arrow with the distance to it) shows on your action bar until you deselect it.</li>
 *   <li>Right-click the air: open the sheep browser, a list you can filter and sort (thirsty, hungry,
 *       mating, ewes, rams, ...). Click a sheep to select it, shift-click to teleport next to it,
 *       right-click to view the world through its eyes.</li>
 *   <li>Sneak + right-click a sheep: view through its eyes. Sneak to come back.</li>
 *   <li>Left-click: clear your selection.</li>
 * </ul>
 * {@code /evo sheep} opens the browser without the item.
 */
public final class SheepTracker implements Listener {

    /** The browser's top five rows hold sheep; the bottom row holds the controls. */
    private static final int SHEEP_SLOTS = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_FILTER = 46;
    private static final int SLOT_SORT = 47;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    /** How often the action-bar panel refreshes, in ticks. */
    private static final long PANEL_TICKS = 5;

    /** Same thresholds as the stat bar and Field Guide: hungry below this share of the reproduction threshold. */
    private static final double HUNGRY_FRACTION = 0.35;
    private static final double THIRSTY_BELOW = 25;

    private static final String[] ARROWS = {"\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"};

    private enum Filter {
        ALL("All sheep"), EWES("Ewes"), RAMS("Rams"), THIRSTY("Thirsty"), HUNGRY("Hungry"),
        MATING("Mating"), DEPRESSED("Depressed"), BABIES("Not yet adult");

        private final String label;

        Filter(String label) { this.label = label; }

        boolean matches(Creature c, SimConfig config) {
            return switch (this) {
                case ALL -> true;
                case EWES -> c.isFemale();
                case RAMS -> !c.isFemale();
                case THIRSTY -> c.hydration() < THIRSTY_BELOW;
                case HUNGRY -> c.energy() < config.population().reproductionThreshold() * HUNGRY_FRACTION;
                case MATING -> c.isMating();
                case DEPRESSED -> c.mood() < config.needs().depressionBelow();
                case BABIES -> !c.isAdult(config.population().maturitySeconds());
            };
        }
    }

    private enum Sort {
        ID("Oldest id first"), DISTANCE("Nearest to you"), AGE("Oldest sheep"), GENERATION("Highest generation"),
        ENERGY("Lowest energy"), WATER("Lowest water"), MOOD("Lowest mood");

        private final String label;

        Sort(String label) { this.label = label; }
    }

    /** What a player last chose in the browser, kept between openings. */
    private static final class Prefs {
        Filter filter = Filter.ALL;
        Sort sort = Sort.ID;
        int page;
    }

    /** Inventory holder that marks an inventory as the browser and remembers what its slots show. */
    private static final class Menu implements InventoryHolder {
        private final List<Creature> shown = new ArrayList<>();
        private Inventory inventory;
        private int pages = 1;

        @Override
        public Inventory getInventory() { return inventory; }
    }

    /** Where a player was before they started viewing through a sheep. */
    private record Viewing(GameMode mode, Location location) {}

    private final EvoSimPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID, Creature> selected = new HashMap<>();
    private final Map<UUID, Prefs> prefs = new HashMap<>();
    private final Map<UUID, Viewing> viewing = new HashMap<>();
    /** Sheep currently outlined, so the outline is removed once nobody has them selected. */
    private final Set<Creature> glowing = new HashSet<>();
    private BukkitTask task;

    public SheepTracker(EvoSimPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "sheep_tracker");
    }

    /** Starts the panel refresh. */
    public void start() {
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, PANEL_TICKS, PANEL_TICKS);
        }
    }

    /** Stops the refresh, brings viewers back and clears every outline. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : new ArrayList<>(viewing.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) stopViewing(player);
        }
        selected.clear();
        syncGlow(plugin.simulation().orElse(null));
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("EvoSim Tracker", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                gray("Right-click a sheep: select it"),
                gray("Right-click the air: browse all sheep"),
                gray("Sneak + right-click a sheep: see through its eyes"),
                gray("Left-click: clear your selection")));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isTracker(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    // --- Picking sheep in the world ---

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!isTracker(player.getInventory().getItemInMainHand())) return;

        Optional<Simulation> sim = plugin.simulation();
        if (sim.isEmpty()) return;
        Optional<Creature> clicked = sim.get().creatureFor(event.getRightClicked());
        if (clicked.isEmpty()) return;

        event.setCancelled(true);
        if (player.isSneaking()) {
            startViewing(player, sim.get(), clicked.get());
        } else if (clicked.get() == selected.get(player.getUniqueId())) {
            deselect(player, sim.get());
        } else {
            select(player, sim.get(), clicked.get());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!isTracker(player.getInventory().getItemInMainHand())) return;

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            openMenu(player);
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            plugin.simulation().ifPresent(sim -> deselect(player, sim));
        }
    }

    private void select(Player player, Simulation sim, Creature creature) {
        selected.put(player.getUniqueId(), creature);
        syncGlow(sim);
        say(player, NamedTextColor.GREEN, "Selected sheep #" + creature.id()
                + ". Its panel is on your action bar; left-click with the tracker to clear it.");
    }

    private void deselect(Player player, Simulation sim) {
        if (selected.remove(player.getUniqueId()) == null) return;
        syncGlow(sim);
        player.sendActionBar(Component.empty());
        say(player, NamedTextColor.YELLOW, "Selection cleared.");
    }

    // --- Viewing through a sheep ---

    private void startViewing(Player player, Simulation sim, Creature creature) {
        Optional<CreatureView> view = sim.viewOf(creature);
        if (view.isEmpty()) return;
        UUID id = player.getUniqueId();
        viewing.putIfAbsent(id, new Viewing(player.getGameMode(), player.getLocation()));
        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(view.get().entity());
        select(player, sim, creature);
        say(player, NamedTextColor.AQUA, "Viewing through sheep #" + creature.id() + ". Sneak to come back.");
    }

    /** Puts a viewer back in their old game mode and place. */
    private void stopViewing(Player player) {
        Viewing back = viewing.remove(player.getUniqueId());
        if (back == null) return;
        player.setSpectatorTarget(null);
        player.setGameMode(back.mode());
        player.teleport(back.location());
    }

    @EventHandler
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        Player player = event.getPlayer();
        if (!viewing.containsKey(player.getUniqueId())) return;
        // Let the event finish before changing the game mode.
        plugin.getServer().getScheduler().runTask(plugin, () -> stopViewing(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        stopViewing(player);
        selected.remove(player.getUniqueId());
        prefs.remove(player.getUniqueId());
        syncGlow(plugin.simulation().orElse(null));
    }

    // --- Live panel ---

    private void tick() {
        Optional<Simulation> opt = plugin.simulation();
        boolean changed = false;
        for (Iterator<Map.Entry<UUID, Creature>> it = selected.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Creature> entry = it.next();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            Creature creature = entry.getValue();
            if (player == null) {
                it.remove();
                changed = true;
                continue;
            }
            if (opt.isEmpty() || !opt.get().isAlive(creature)) {
                it.remove();
                changed = true;
                stopViewing(player);
                player.sendActionBar(Component.text("Sheep #" + creature.id() + " is gone.", NamedTextColor.RED));
                continue;
            }
            player.sendActionBar(panel(player, opt.get(), creature));
        }
        if (changed) syncGlow(opt.orElse(null));
    }

    private Component panel(Player player, Simulation sim, Creature c) {
        SimConfig config = sim.config();
        double energyMax = config.population().reproductionThreshold();
        var text = Component.text()
                .append(Component.text("Sheep #" + c.id(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" " + (c.isFemale() ? "Ewe" : "Ram") + " G" + c.generation(), NamedTextColor.GRAY))
                .append(sep())
                .append(stat("Energy", c.energy() / energyMax, NamedTextColor.GOLD))
                .append(Component.text(" "))
                .append(stat("Water", c.hydration() / 100.0, NamedTextColor.AQUA))
                .append(Component.text(" "))
                .append(stat("Mood", c.mood() / 100.0, NamedTextColor.GREEN))
                .append(sep())
                .append(Component.text(c.activity().label(), NamedTextColor.WHITE));
        if (!viewing.containsKey(player.getUniqueId())) {
            Location there = sim.arena().toLocation(c.x(), c.z());
            if (there.getWorld() != null && there.getWorld().equals(player.getWorld())) {
                text.append(sep()).append(Component.text(
                        arrow(player, there) + " " + Math.round(horizontal(player.getLocation(), there)) + "m",
                        NamedTextColor.YELLOW));
            }
        }
        return text.build();
    }

    private static Component stat(String label, double ratio, NamedTextColor color) {
        int percent = (int) Math.round(Math.max(0, Math.min(1, ratio)) * 100);
        return Component.text(label + " " + percent + "%", color);
    }

    private static Component sep() {
        return Component.text(" | ", NamedTextColor.DARK_GRAY);
    }

    private static double horizontal(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /** An arrow that points from where the player is looking to the target: up is straight ahead. */
    private static String arrow(Player player, Location target) {
        Location from = player.getLocation();
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        // Minecraft yaw 0 faces +z and grows clockwise seen from above.
        double toTarget = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = ((toTarget - from.getYaw()) % 360.0 + 540.0) % 360.0 - 180.0;
        int index = (int) Math.round(relative / 45.0);
        return ARROWS[((index % 8) + 8) % 8];
    }

    /** Outlines exactly the sheep somebody has selected. */
    private void syncGlow(Simulation sim) {
        Set<Creature> wanted = new HashSet<>(selected.values());
        for (Iterator<Creature> it = glowing.iterator(); it.hasNext(); ) {
            Creature c = it.next();
            if (!wanted.contains(c)) {
                if (sim != null) sim.viewOf(c).ifPresent(v -> v.setHighlighted(false));
                it.remove();
            }
        }
        if (sim == null) return;
        for (Creature c : wanted) {
            if (glowing.add(c)) sim.viewOf(c).ifPresent(v -> v.setHighlighted(true));
        }
    }

    // --- The browser ---

    /** Opens the sheep list for a player, honouring the filter, sort and page they last used. */
    public void openMenu(Player player) {
        Optional<Simulation> opt = plugin.simulation();
        if (opt.isEmpty()) {
            say(player, NamedTextColor.RED, "No arena yet. Run /evo create first.");
            return;
        }
        Simulation sim = opt.get();
        Prefs pref = prefs.computeIfAbsent(player.getUniqueId(), id -> new Prefs());
        SimConfig config = sim.config();

        List<Creature> list = new ArrayList<>();
        for (Creature c : sim.creatures()) {
            if (pref.filter.matches(c, config)) list.add(c);
        }
        list.sort(comparator(pref.sort, player, sim));

        Menu menu = new Menu();
        menu.pages = Math.max(1, (list.size() + SHEEP_SLOTS - 1) / SHEEP_SLOTS);
        pref.page = Math.max(0, Math.min(pref.page, menu.pages - 1));
        int from = pref.page * SHEEP_SLOTS;
        menu.shown.addAll(list.subList(Math.min(from, list.size()), Math.min(from + SHEEP_SLOTS, list.size())));

        menu.inventory = plugin.getServer().createInventory(menu, 54,
                Component.text("Sheep: " + pref.filter.label + " (" + list.size() + ")"));
        Inventory inv = menu.inventory;
        for (int i = 0; i < menu.shown.size(); i++) {
            inv.setItem(i, sheepIcon(menu.shown.get(i), sim, selected.get(player.getUniqueId())));
        }
        if (pref.page > 0) inv.setItem(SLOT_PREV, button(Material.ARROW, "Previous page"));
        if (pref.page < menu.pages - 1) inv.setItem(SLOT_NEXT, button(Material.ARROW, "Next page"));
        inv.setItem(SLOT_FILTER, button(Material.HOPPER, "Show: " + pref.filter.label,
                "Click to change the filter", "Right-click to go back"));
        inv.setItem(SLOT_SORT, button(Material.COMPARATOR, "Sort: " + pref.sort.label,
                "Click to change the order", "Right-click to go back"));
        inv.setItem(SLOT_INFO, button(Material.PAPER, "Page " + (pref.page + 1) + " / " + menu.pages,
                list.size() + " of " + sim.creatures().size() + " sheep",
                "Click: select", "Shift-click: teleport next to it", "Right-click: see through its eyes"));
        player.openInventory(inv);
    }

    private Comparator<Creature> comparator(Sort sort, Player player, Simulation sim) {
        Comparator<Creature> byId = Comparator.comparingLong(Creature::id);
        Comparator<Creature> main = switch (sort) {
            case ID -> byId;
            case DISTANCE -> Comparator.comparingDouble(c -> horizontal(player.getLocation(),
                    sim.arena().toLocation(c.x(), c.z())));
            case AGE -> Comparator.comparingDouble(Creature::age).reversed();
            case GENERATION -> Comparator.comparingInt(Creature::generation).reversed();
            case ENERGY -> Comparator.comparingDouble(Creature::energy);
            case WATER -> Comparator.comparingDouble(Creature::hydration);
            case MOOD -> Comparator.comparingDouble(Creature::mood);
        };
        return main.thenComparing(byId);
    }

    private ItemStack sheepIcon(Creature c, Simulation sim, Creature current) {
        DyeColor dye = sim.colorMode().dye(c.genome());
        ItemStack item = new ItemStack(Material.valueOf(dye.name() + "_WOOL"));
        ItemMeta meta = item.getItemMeta();
        SimConfig config = sim.config();
        double energyMax = config.population().reproductionThreshold();
        meta.displayName(Component.text("Sheep #" + c.id() + (c == current ? " (selected)" : ""),
                        c == current ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                gray((c.isFemale() ? "Ewe" : "Ram") + ", generation " + c.generation()
                        + (c.isAdult(config.population().maturitySeconds()) ? "" : ", young")),
                gray("Energy " + Math.round(c.energy()) + "/" + Math.round(energyMax)
                        + "  Water " + Math.round(c.hydration()) + "  Mood " + Math.round(c.mood())),
                gray(c.activity().label()),
                gray("Click: select | Shift: teleport | Right-click: view")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) lines.add(gray(line));
            meta.lore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        Optional<Simulation> opt = plugin.simulation();
        if (opt.isEmpty()) {
            player.closeInventory();
            return;
        }
        Simulation sim = opt.get();
        Prefs pref = prefs.computeIfAbsent(player.getUniqueId(), id -> new Prefs());
        int slot = event.getSlot();
        ClickType click = event.getClick();
        boolean back = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;

        if (slot < SHEEP_SLOTS) {
            if (slot >= menu.shown.size()) return;
            Creature creature = menu.shown.get(slot);
            if (!sim.isAlive(creature)) {
                openMenu(player); // it died while the list was open
                return;
            }
            player.closeInventory();
            if (click.isShiftClick()) {
                select(player, sim, creature);
                teleportNextTo(player, sim, creature);
            } else if (back) {
                startViewing(player, sim, creature);
            } else {
                select(player, sim, creature);
            }
            return;
        }

        switch (slot) {
            case SLOT_PREV -> pref.page = Math.max(0, pref.page - 1);
            case SLOT_NEXT -> pref.page = Math.min(menu.pages - 1, pref.page + 1);
            case SLOT_FILTER -> {
                pref.filter = cycle(Filter.values(), pref.filter, back);
                pref.page = 0;
            }
            case SLOT_SORT -> {
                pref.sort = cycle(Sort.values(), pref.sort, back);
                pref.page = 0;
            }
            default -> {
                return;
            }
        }
        openMenu(player);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    private static <T> T cycle(T[] values, T current, boolean backwards) {
        int i = 0;
        while (values[i] != current) i++;
        return values[(i + (backwards ? values.length - 1 : 1)) % values.length];
    }

    private void teleportNextTo(Player player, Simulation sim, Creature creature) {
        Location sheep = sim.arena().toLocation(creature.x(), creature.z());
        Location spot = sheep.clone().add(3, 1.5, 3);
        spot.setDirection(sheep.toVector().subtract(spot.toVector()));
        player.teleport(spot);
    }

    // --- helpers ---

    private static Component gray(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static void say(Player player, NamedTextColor color, String text) {
        player.sendMessage(Component.text("[EvoSim] ", color).append(Component.text(text, NamedTextColor.WHITE)));
    }
}
