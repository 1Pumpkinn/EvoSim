package dev.pumpkin.evosim.render;

import dev.pumpkin.evosim.genetics.Genome;
import org.bukkit.DyeColor;

import java.util.Optional;

/** Which gene a creature's wool colour displays. */
public enum ColorMode {
    /** Neutral hue gene: related creatures share a colour. */
    LINEAGE,
    /** Blue = slow, red = fast. */
    SPEED,
    /** Blue = small, red = large. */
    SIZE,
    /** Blue = short-sighted, red = far-sighted. */
    VISION;

    private static final double[] PALETTE_HUE = {
            0.00, 0.08, 0.15, 0.25, 0.33, 0.50, 0.55, 0.65, 0.75, 0.83, 0.92
    };
    private static final DyeColor[] PALETTE_DYE = {
            DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME,
            DyeColor.GREEN, DyeColor.CYAN, DyeColor.LIGHT_BLUE, DyeColor.BLUE,
            DyeColor.PURPLE, DyeColor.MAGENTA, DyeColor.PINK
    };

    /** Hue in 0..1 for the genome under this mode. */
    public double hue(Genome genome) {
        return switch (this) {
            case LINEAGE -> genome.hue();
            case SPEED -> traitHue(genome, Genome.Trait.SPEED);
            case SIZE -> traitHue(genome, Genome.Trait.SIZE);
            case VISION -> traitHue(genome, Genome.Trait.VISION);
        };
    }

    /** Wool dye whose hue is closest to the genome's colour. */
    public DyeColor dye(Genome genome) {
        double hue = hue(genome);
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < PALETTE_HUE.length; i++) {
            double d = Math.abs(hue - PALETTE_HUE[i]);
            d = Math.min(d, 1.0 - d); // hue is circular
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return PALETTE_DYE[best];
    }

    public static Optional<ColorMode> parse(String name) {
        for (ColorMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    private static double traitHue(Genome genome, Genome.Trait trait) {
        double n = Math.max(0.0, Math.min(1.0, trait.normalize(genome.get(trait))));
        return (1.0 - n) * 0.66; // 0.66 = blue, 0.0 = red
    }
}
