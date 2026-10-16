package br.com.auren.minecraft.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure Java, deterministic offline intention ranking. No network, LLM,
 * Minecraft world mutation or physics. Values are simulated preferences,
 * not claims of human-like consciousness or subjective needs.
 */
public final class AurenLocalDecisionPolicy {
    public enum Activity { OBSERVE, EXPLORE, INVESTIGATE, REST, SEEK_SHELTER }

    public record Context(double energy, double curiosity, double vigilance,
                          boolean night, boolean rainExposed,
                          boolean passiveAnimalNearby, Activity previous,
                          int variation) { }

    public record Rated(Activity activity, double utility) { }

    private AurenLocalDecisionPolicy() { }

    public static List<Rated> rank(Context context) {
        List<Rated> options = new ArrayList<>(Activity.values().length);
        for (Activity activity : Activity.values()) {
            if (activity == Activity.INVESTIGATE && !context.passiveAnimalNearby())
                continue;
            if (activity == Activity.SEEK_SHELTER && !context.rainExposed())
                continue;
            options.add(new Rated(activity, score(activity, context)));
        }
        options.sort(Comparator.comparingDouble(Rated::utility).reversed());
        return List.copyOf(options);
    }

    public static double score(Activity activity, Context context) {
        double energy = clamp(context.energy());
        double curiosity = clamp(context.curiosity());
        double vigilance = clamp(context.vigilance());
        double base = switch (activity) {
            case OBSERVE -> 21.0D + (100.0D - curiosity) * 0.15D
                    + vigilance * 0.12D;
            case EXPLORE -> 15.0D + curiosity * 0.46D + energy * 0.10D
                    - vigilance * 0.27D - (context.night() ? 8.0D : 0.0D)
                    - (context.rainExposed() ? 14.0D : 0.0D);
            case INVESTIGATE -> context.passiveAnimalNearby()
                    ? 14.0D + curiosity * 0.40D + energy * 0.08D
                    - vigilance * 0.18D : Double.NEGATIVE_INFINITY;
            case REST -> 10.0D + (100.0D - energy) * 0.74D
                    + (context.night() ? 25.0D : 0.0D)
                    - vigilance * 0.15D;
            case SEEK_SHELTER -> context.rainExposed()
                    ? 72.0D + vigilance * 0.12D
                    : Double.NEGATIVE_INFINITY;
        };
        if (context.previous() == activity) base -= 19.0D;
        // Bounded, reproducible variation in a single idle decision.
        // No noisy rerolls on every game tick, which causes indecision.
        int mix = Math.floorMod(context.variation() * 13
                + activity.ordinal() * 17, 23);
        return base + (mix - 11) * 0.30D;
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) return 50.0D;
        return Math.max(0.0D, Math.min(100.0D, value));
    }
}
