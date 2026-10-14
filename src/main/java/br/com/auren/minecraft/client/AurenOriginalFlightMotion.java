package br.com.auren.minecraft.client;

import br.com.auren.minecraft.entity.AurenDragon;
import com.iafenvoy.uranus.client.model.AdvancedModelBox;
import com.iafenvoy.uranus.client.model.TabulaModel;
import net.minecraft.util.Mth;

/**
 * Ports the actual CE continuous flying branch (DragonTabulaModelAnimator,
 * 1.21.1 lines 96-104 + shared idle lines 118-122) onto the original
 * Lightning Dragon flight keyframes. No author-created skeletal keyframes.
 *
 * CE's flying animation is NOT merely cycling flight1..flight6: its animator
 * also moves body, tail, neck, toes, and thighs between frames. Without these
 * routines Auren looked unnaturally rigid while moving.
 */
public final class AurenOriginalFlightMotion {
    private AurenOriginalFlightMotion() {}

    public static void apply(TabulaModel<AurenDragon> model, float age, float flightBlend) {
        float weight = Mth.clamp(flightBlend, 0.0F, 1.0F);
        if (weight < 0.01F) return;
        float speedFly = 0.2F;
        float degreeFly = 0.5F * weight;
        float speedIdle = 0.05F;
        float degreeIdle = 0.5F * weight;

        AdvancedModelBox[] tailWithBody = {
                model.getCube("BodyLower"), model.getCube("Tail1"),
                model.getCube("Tail2"), model.getCube("Tail3"),
                model.getCube("Tail4")
        };
        AdvancedModelBox[] neck = {
                model.getCube("Neck1"), model.getCube("Neck2"),
                model.getCube("Neck3"), model.getCube("Head")
        };
        AdvancedModelBox[] toesL = {
                model.getCube("ToeL1"), model.getCube("ToeL2"), model.getCube("ToeL3")
        };
        AdvancedModelBox[] toesR = {
                model.getCube("ToeR1"), model.getCube("ToeR2"), model.getCube("ToeR3")
        };

        // Verbatim parameters from the original CE flight animator:
        model.bob(model.getCube("BodyUpper"), -speedFly, degreeFly * 5.0F,
                false, age, 1.0F);
        model.walk(model.getCube("BodyUpper"), -speedFly, degreeFly * 0.1F,
                false, 0, 0, age, 1.0F);
        model.chainWave(tailWithBody, speedFly, degreeFly * -0.1F,
                0, age, 1.0F);
        model.chainWave(neck, speedFly, degreeFly * 0.2F,
                -4, age, 1.0F);
        model.chainWave(toesL, speedFly, degreeFly * 0.2F,
                -2, age, 1.0F);
        model.chainWave(toesR, speedFly, degreeFly * 0.2F,
                -2, age, 1.0F);
        model.walk(model.getCube("ThighR"), -speedFly, degreeFly * 0.1F,
                false, 0, 0, age, 1.0F);
        model.walk(model.getCube("ThighL"), -speedFly, degreeFly * 0.1F,
                true, 0, 0, age, 1.0F);

        // CE applies these subtle awake idle bobs even during flight.
        model.bob(model.getCube("BodyUpper"), speedIdle,
                degreeIdle * 1.3F, false, age, 1.0F);
        for (String name : new String[]{"ThighR", "ThighL", "armR1", "armL1"}) {
            model.bob(model.getCube(name), speedIdle,
                    -degreeIdle * 1.3F, false, age, 1.0F);
        }
    }
}
