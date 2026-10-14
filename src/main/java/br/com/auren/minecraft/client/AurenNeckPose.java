package br.com.auren.minecraft.client;

import br.com.auren.minecraft.entity.AurenDragon;
import com.iafenvoy.uranus.client.model.AdvancedModelBox;
import com.iafenvoy.uranus.client.model.TabulaModel;
import net.minecraft.util.Mth;

/**
 * Single authored shoulder posture (NOT a new movement animation).
 * Targets are absolute Euler angles, documented in
 * docs/AUREN_SHOULDER_POSE_V057.json and checked by CI.
 *
 * They were fit against the actual CE Tabula bone hierarchy with a
 * player-sized proxy in front/back/side/top projections. The Y rotation
 * signs are adapted to Minecraft's 180-degree living-entity renderer
 * transform. Never rotate the whole dragon sideways to position its head.
 *
 * Wings and the very long tail need display-only compression while perched.
 * This is a cosmetic static transformation, NOT an original folded-wing
 * animation: CE provides no properly folded tiny-shoulder-wing keyframe.
 * Critically Uranus does NOT restore scale or scaleChildren in
 * AdvancedModelBox.resetToDefaultPose. Always clear it on every frame.
 */
public final class AurenNeckPose {
    private static final float BABY_WING_COMPACT_SCALE = 0.42F;
    private static final float BABY_TAIL_COMPACT_SCALE = 0.72F;

    private record PoseTarget(String bone, float xDeg, float yDeg, float zDeg) { }
    private static final PoseTarget[] TARGETS = {
            new PoseTarget("BodyUpper", -21.78F, 0.00F, 0.00F),
            new PoseTarget("BodyLower", -0.22F, 0.00F, 0.00F),
            new PoseTarget("Neck1", -5.22F, 38.83F, 0.00F),
            new PoseTarget("Neck2", -6.32F, 6.02F, 0.00F),
            new PoseTarget("Neck3", -12.45F, 4.75F, 0.00F),
            new PoseTarget("Head", 47.89F, -11.57F, 0.00F),
            new PoseTarget("Tail1", 29.77F, 3.48F, 0.00F),
            new PoseTarget("Tail2", 3.99F, 86.30F, 0.00F),
            new PoseTarget("Tail3", -18.47F, 41.31F, 0.00F),
            new PoseTarget("Tail4", -15.54F, -11.08F, 0.00F),
            new PoseTarget("Tail5", 10.43F, 12.00F, 0.00F),
            new PoseTarget("ThighL", -26.00F, 0.00F, 0.00F),
            new PoseTarget("ThighR", -26.00F, 0.00F, 0.00F),
            new PoseTarget("LegL", 0.00F, 0.00F, 0.00F),
            new PoseTarget("LegR", 0.00F, 0.00F, 0.00F)
    };

    private AurenNeckPose() { }

    /** Required every render BEFORE shoulder blending, including on ground. */
    public static void resetTransientScale(TabulaModel<AurenDragon> model) {
        resetPart(model, "armL1");
        resetPart(model, "armR1");
        resetPart(model, "Tail1");
    }

    private static void resetPart(TabulaModel<AurenDragon> model, String name) {
        AdvancedModelBox box = model.getCube(name);
        if (box == null) return;
        box.setScale(1.0F, 1.0F, 1.0F);
        box.setShouldScaleChildren(false);
    }

    public static void apply(TabulaModel<AurenDragon> model,
                             TabulaModel<AurenDragon> original,
                             float shoulderBlend) {
        if (original == null || shoulderBlend <= 0.0F) return;
        float t = Mth.clamp(shoulderBlend, 0.0F, 1.0F);
        for (PoseTarget pose : TARGETS) {
            AdvancedModelBox part = model.getCube(pose.bone());
            AdvancedModelBox reference = original.getCube(pose.bone());
            if (part == null || reference == null) {
                throw new IllegalStateException("CE dragon pose bone missing: " + pose.bone());
            }
            part.rotateAngleX += shortestDelta(reference.rotateAngleX, pose.xDeg()) * t;
            part.rotateAngleY += shortestDelta(reference.rotateAngleY, pose.yDeg()) * t;
            part.rotateAngleZ += shortestDelta(reference.rotateAngleZ, pose.zDeg()) * t;
        }

        // A tiny folded-looking shoulder profile using only EXISTING bones.
        // Explicit child scaling is essential: Uranus defaults it to FALSE.
        compact(model, "armL1", Mth.lerp(t, 1.0F, BABY_WING_COMPACT_SCALE));
        compact(model, "armR1", Mth.lerp(t, 1.0F, BABY_WING_COMPACT_SCALE));
        compact(model, "Tail1", Mth.lerp(t, 1.0F, BABY_TAIL_COMPACT_SCALE));
    }

    private static float shortestDelta(float originalRadians, float targetDegrees) {
        float targetRadians = (float) Math.toRadians(targetDegrees);
        float difference = targetRadians - originalRadians;
        return (float) Math.atan2(Math.sin(difference), Math.cos(difference));
    }

    private static void compact(TabulaModel<AurenDragon> model, String name, float factor) {
        AdvancedModelBox bone = model.getCube(name);
        if (bone == null) {
            throw new IllegalStateException("CE dragon wing/tail bone missing: " + name);
        }
        bone.setShouldScaleChildren(true);
        bone.setScale(factor, factor, factor);
    }
}
