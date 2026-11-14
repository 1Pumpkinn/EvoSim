package dev.pumpkin.evosim.inspect;

import dev.pumpkin.evosim.EvoSimPlugin;
import dev.pumpkin.evosim.sim.Creature;
import dev.pumpkin.evosim.sim.Simulation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The EvoSim Breeding Stick, a testing item. Right-click one sheep, then another: the two
 * are paired up at once, whatever their sex or pairing preference, so the mating animation
 * and births can be watched on demand. Get one with {@code /evo stick}.
 */
public final class BreedingStick implements Listener {

    private final EvoSimPlugin plugin;
    private final NamespacedKey key;
    /** The first sheep each player has picked, waiting for a partner. */
    private final Map<UUID, Creature> selected = new HashMap<>();

    public BreedingStick(EvoSimPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "breeding_stick");
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("EvoSim Breeding Stick", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click two sheep to make them breed,", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("whatever their sex or preference.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isStick(ItemStack item) {
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!isStick(player.getInventory().getItemInMainHand())) return;

        Optional<Simulation> sim = plugin.simulation();
        if (sim.isEmpty()) return;
        Optional<Creature> clicked = sim.get().creatureFor(event.getRightClicked());
        if (clicked.isEmpty()) return;

        event.setCancelled(true);
        Creature creature = clicked.get();
        Creature first = selected.get(player.getUniqueId());

        if (first == null) {
            selected.put(player.getUniqueId(), creature);
            say(player, NamedTextColor.GREEN, "Picked sheep #" + creature.id() + ". Right-click a second sheep.");
        } else if (first == creature) {
            selected.remove(player.getUniqueId());
            say(player, NamedTextColor.YELLOW, "Cancelled.");
        } else if (sim.get().forceBreed(first, creature)) {
            selected.remove(player.getUniqueId());
            say(player, NamedTextColor.GREEN, describe(first, creature)
                    + (sim.get().isRunning() ? "" : " Run /evo start to watch."));
        } else {
            selected.remove(player.getUniqueId());
            say(player, NamedTextColor.RED, "Could not pair them: one is already mating or is gone. Pick again.");
        }
    }

    /** Says who plays which part, so the animation you should see is clear. */
    private static String describe(Creature a, Creature b) {
        if (a.isFemale() && b.isFemale()) {
            return "Ewes #" + a.id() + " and #" + b.id() + " are breeding: they face each other and nuzzle.";
        }
        if (a.isFemale() != b.isFemale()) {
            Creature ewe = a.isFemale() ? a : b;
            Creature ram = a.isFemale() ? b : a;
            return "Ram #" + ram.id() + " is mounting ewe #" + ewe.id() + ".";
        }
        Creature top = a.id() < b.id() ? a : b;
        Creature bottom = top == a ? b : a;
        return "Ram #" + top.id() + " is mounting ram #" + bottom.id() + ".";
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selected.remove(event.getPlayer().getUniqueId());
    }

    private static void say(Player player, NamedTextColor color, String text) {
        player.sendMessage(Component.text("[EvoSim] ", color).append(Component.text(text, NamedTextColor.WHITE)));
    }
}
