package br.com.auren.minecraft.client;

import br.com.auren.minecraft.entity.AurenDragon;
import com.iafenvoy.uranus.client.model.AdvancedModelBox;
import com.iafenvoy.uranus.client.model.TabulaModel;
import net.minecraft.util.Mth;

/**
 * Ports the continuous *idle* animation from the original IceAndFire-CE
 * DragonTabulaModelAnimator.setRotationAngles, branch "walking", lines
 * 113-123 (CE 1.21.1). These are NOT invented Auren animations.
 *
 * The upstream Lightning Dragon sitting_shoulder Tabula file is a STATIC POSE:
 * CE composes its usual idle movement over that pose every rendered frame.
 * Auren v0.5.3 applied only the pose, which left the shoulder dragon rigid.
 *
 * Upstream: https://github.com/IAFEnvoy/IceAndFire-CE/blob/1.21.1/
 * src/main/java/com/iafenvoy/iceandfire/render/model/animator/
 * DragonTabulaModelAnimator.java
 *
 * CE default awake idle speed = 0.05F, degree = 0.5F. Original relative
 * amplitudes, offsets and bone sequences are preserved. Blend is only used
 * to enter/leave shoulder mode smoothly over the existing 20-tick transition.
 */
public final class AurenOriginalShoulderIdle {
    private AurenOriginalShoulderIdle() {}

    public static void apply(TabulaModel<AurenDragon> model, float ageInTicks, float shoulderBlend) {
        float blend = Mth.clamp(shoulderBlend, 0.0F, 1.0F);
        if (blend <= 0.0F) return;

        AdvancedModelBox[] tailParts = {
                model.getCube("Tail1"), model.getCube("Tail2"),
                model.getCube("Tail3"), model.getCube("Tail4")
        };
        AdvancedModelBox[] neckParts = {
                model.getCube("Neck1"), model.getCube("Neck2"),
                model.getCube("Neck3"), model.getCube("Head")
        };

        // Values, signs, phase offsets and chain orders: original CE animator.
        float speedIdle = 0.05F;
        float degreeIdle = 0.5F * blend;
        model.chainSwing(tailParts, speedIdle, degreeIdle * 0.25F, -2, ageInTicks, 1.0F);
        model.chainWave(tailParts, speedIdle, degreeIdle * 0.15F, -2, ageInTicks, 1.0F);
        model.chainWave(neckParts, speedIdle, degreeIdle * -0.15F, -3, ageInTicks, 1.0F);
        model.walk(model.getCube("Neck1"), speedIdle, degreeIdle * 0.05F,
                false, 0, 0, ageInTicks, 1.0F);

        model.bob(model.getCube("BodyUpper"), speedIdle, degreeIdle * 1.3F,
                false, ageInTicks, 1.0F);
        model.bob(model.getCube("ThighR"), speedIdle, -degreeIdle * 1.3F,
                false, ageInTicks, 1.0F);
        model.bob(model.getCube("ThighL"), speedIdle, -degreeIdle * 1.3F,
                false, ageInTicks, 1.0F);
        model.bob(model.getCube("armR1"), speedIdle, -degreeIdle * 1.3F,
                false, ageInTicks, 1.0F);
        model.bob(model.getCube("armL1"), speedIdle, -degreeIdle * 1.3F,
                false, ageInTicks, 1.0F);
    }
}
