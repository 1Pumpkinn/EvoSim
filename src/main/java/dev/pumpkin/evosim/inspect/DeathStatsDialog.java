package dev.pumpkin.evosim.inspect;

import dev.pumpkin.evosim.sim.DeathTally;
import dev.pumpkin.evosim.sim.Simulation;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A dialog listing what the sheep have died of, most common cause first. Open it with {@code /evo deaths}. */
public final class DeathStatsDialog {

    private static final int BAR_WIDTH = 20;

    private DeathStatsDialog() {}

    public static void show(Player player, Simulation sim) {
        List<DeathTally> tallies = sim.deathTallies();
        int total = tallies.stream().mapToInt(DeathTally::count).sum();

        List<DialogBody> body = new ArrayList<>();
        if (total == 0) {
            body.add(DialogBody.plainMessage(Component.text("No sheep have died yet.", NamedTextColor.GRAY)));
        } else {
            body.add(DialogBody.plainMessage(Component.text(total + " deaths so far", NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD)));
            for (DeathTally t : tallies) {
                double share = t.count() / (double) total;
                int filled = (int) Math.round(share * BAR_WIDTH);
                body.add(DialogBody.plainMessage(Component.text()
                        .append(Component.text(t.cause().label(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .appendNewline()
                        .append(Component.text("|".repeat(filled), NamedTextColor.RED))
                        .append(Component.text("|".repeat(BAR_WIDTH - filled), NamedTextColor.DARK_GRAY))
                        .appendNewline()
                        .append(Component.text(String.format(Locale.ROOT, "%d deaths (%.0f%%), average age %.0f s",
                                t.count(), share * 100, t.meanAgeSeconds()), NamedTextColor.GRAY))
                        .build()));
            }
        }

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Death statistics")).body(body).build())
                .type(DialogType.notice()));
        player.showDialog(dialog);
    }
}
