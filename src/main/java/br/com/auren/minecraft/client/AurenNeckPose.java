package br.com.auren.minecraft.client;

import br.com.auren.minecraft.entity.AurenDragon;
import com.iafenvoy.uranus.client.model.AdvancedModelBox;
import com.iafenvoy.uranus.client.model.TabulaModel;

/**
 * The user's illustrated Auren neck-wrap silhouette, based on the v0.4 pose:
 *
 * - awake head and neck over player's LEFT shoulder
 * - torso on the back of their nape, not standing above it
 * - tail curved toward RIGHT shoulder; feet lightly tucked
 * - folded wing positions are copied from original CE sleeping keyframes
 *   by the renderer, NOT manually authored in this class
 *
 * Crucial v0.4 correction: NO whole-model 90-degree rotation. Negative neck
 * Y yaw turns the model's forward-facing (-Z) head toward player-left (+X)
 * when player yaw = zero; negative tail Y bends +Z tail toward -X (right).
 */
public final class AurenNeckPose {
    private AurenNeckPose() {}

    public static void apply(TabulaModel<AurenDragon> model, float blend) {
        if (blend <= 0.0F) return;

        // Slightly recline over the nape while remaining awake.
        rotateX(model, "BodyUpper", -4.0F * blend);
        rotateX(model, "BodyLower", 8.0F * blend);

        // Different from old v0.4: curve the NECK towards player's LEFT.
        rotateY(model, "Neck1", -8.0F * blend);
        rotateY(model, "Neck2", -22.0F * blend);
        rotateY(model, "Neck3", -18.0F * blend);
        rotateX(model, "Neck2", -7.0F * blend);
        rotateY(model, "Head", 12.0F * blend);
        rotateX(model, "Head", 5.0F * blend);

        // Keep tail on the other shoulder, not straight down the spine.
        rotateY(model, "Tail1", -10.0F * blend);
        rotateY(model, "Tail2", -16.0F * blend);
        rotateY(model, "Tail3", -25.0F * blend);
        rotateY(model, "Tail4", -20.0F * blend);
        rotateY(model, "Tail5", -12.0F * blend);
        rotateX(model, "Tail3", 10.0F * blend);
        rotateX(model, "Tail4", 8.0F * blend);

        // Keep the back legs against the shoulder instead of dangling.
        rotateX(model, "ThighL", -17.0F * blend);
        rotateX(model, "ThighR", -17.0F * blend);
        rotateX(model, "LegL", 20.0F * blend);
        rotateX(model, "LegR", 20.0F * blend);
    }

    private static void rotateX(TabulaModel<AurenDragon> model, String name, float degrees) {
        AdvancedModelBox bone = model.getCube(name);
        if (bone != null) bone.rotateAngleX += (float) Math.toRadians(degrees);
    }

    private static void rotateY(TabulaModel<AurenDragon> model, String name, float degrees) {
        AdvancedModelBox bone = model.getCube(name);
        if (bone != null) bone.rotateAngleY += (float) Math.toRadians(degrees);
    }
}
