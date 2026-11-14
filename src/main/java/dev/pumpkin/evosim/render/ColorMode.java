package dev.pumpkin.evosim.render;

import dev.pumpkin.evosim.genetics.Genome;
import org.bukkit.Color;
import org.bukkit.Material;

import java.util.Optional;

/** Which gene a creature's colour displays. */
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
    private static final Material[] PALETTE_BLOCK = {
            Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE, Material.GREEN_CONCRETE, Material.CYAN_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE,
            Material.MAGENTA_CONCRETE, Material.PINK_CONCRETE
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

    /** Exact colour, used for the glow outline. */
    public Color color(Genome genome) {
        return hsv(hue(genome), 0.85, 1.0);
    }

    /** Concrete block whose hue is closest, used as the body. */
    public Material block(Genome genome) {
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
        return PALETTE_BLOCK[best];
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

    private static Color hsv(double h, double s, double v) {
        h = h - Math.floor(h);
        double c = v * s;
        double hp = h * 6.0;
        double x = c * (1.0 - Math.abs(hp % 2.0 - 1.0));
        double[] rgb = switch ((int) hp % 6) {
            case 0 -> new double[]{c, x, 0};
            case 1 -> new double[]{x, c, 0};
            case 2 -> new double[]{0, c, x};
            case 3 -> new double[]{0, x, c};
            case 4 -> new double[]{x, 0, c};
            default -> new double[]{c, 0, x};
        };
        double m = v - c;
        return Color.fromRGB(channel(rgb[0] + m), channel(rgb[1] + m), channel(rgb[2] + m));
    }

    private static int channel(double value) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, value)) * 255.0);
    }
}
