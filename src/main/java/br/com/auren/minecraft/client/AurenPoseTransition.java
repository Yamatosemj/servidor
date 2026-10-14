package br.com.auren.minecraft.client;

import br.com.auren.minecraft.entity.AurenDragon;
import com.iafenvoy.uranus.client.model.AdvancedModelBox;
import com.iafenvoy.uranus.client.model.TabulaModel;
import net.minecraft.util.Mth;

/**
 * Small CE-style pose interpolation without dragging the entire Ice and Fire
 * entity/animator system into Auren.
 *
 * The IceAndFire CE transitionTo() method interpolates rotation and pivot
 * coordinates per named bone. We apply the same principle to the ground and
 * sitting_shoulder Tabula poses using a synced-state-driven 20-tick blend.
 *
 * Original approach: IAFEnvoy/IceAndFire-CE 1.21.1,
 * render/model/animator/IceAndFireTabulaModelAnimator.java.
 */
public final class AurenPoseTransition {
    private AurenPoseTransition() {}

    public static void apply(TabulaModel<AurenDragon> current,
                             TabulaModel<AurenDragon> target, float blend) {
        if (target == null || blend <= 0.0F) return;
        float t = Mth.clamp(blend, 0.0F, 1.0F);
        for (AdvancedModelBox from : current.getCubes().values()) {
            AdvancedModelBox to = target.getCube(from.boxName);
            if (to == null) continue;
            from.rotateAngleX += angleDelta(from.rotateAngleX, to.rotateAngleX) * t;
            from.rotateAngleY += angleDelta(from.rotateAngleY, to.rotateAngleY) * t;
            from.rotateAngleZ += angleDelta(from.rotateAngleZ, to.rotateAngleZ) * t;
            from.rotationPointX += (to.rotationPointX - from.rotationPointX) * t;
            from.rotationPointY += (to.rotationPointY - from.rotationPointY) * t;
            from.rotationPointZ += (to.rotationPointZ - from.rotationPointZ) * t;
        }
    }

    /** Shortest angular distance, avoiding 360-degree flips across +/- pi. */
    private static float angleDelta(float from, float to) {
        float delta = to - from;
        return (float) Math.atan2(Math.sin(delta), Math.cos(delta));
    }
}
