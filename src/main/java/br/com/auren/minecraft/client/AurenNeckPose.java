package br.com.auren.minecraft.client;

import br.com.auren.minecraft.entity.AurenDragon;
import com.iafenvoy.uranus.client.model.AdvancedModelBox;
import com.iafenvoy.uranus.client.model.TabulaModel;

/**
 * Curled neck pose, based on the user's Auren concept illustration.
 *
 * Body behind the neck; head on the left shoulder; tail loops toward
 * the right shoulder; wings folded against the back. The renderer turns
 * the whole dragon sideways; local bone poses provide the curvature.
 *
 * This class is used only by the baby while mounted as a collar.
 */
public final class AurenNeckPose {
    private AurenNeckPose() {}

    public static void apply(TabulaModel<AurenDragon> model, float age) {
        float breathing = (float) Math.sin(age * 0.095F);
        float tailMotion = (float) Math.sin(age * 0.065F);

        // Nestle the torso behind the neck instead of remaining upright.
        rotateX(model, "BodyUpper", -4.0F);
        rotateZ(model, "BodyUpper", 4.0F);
        rotateX(model, "BodyLower", 8.0F);

        // Three independently jointed neck segments curve over left shoulder.
        rotateY(model, "Neck1", 7.0F);
        rotateY(model, "Neck2", 23.0F);
        rotateY(model, "Neck3", 21.0F);
        rotateX(model, "Neck2", -7.0F);
        rotateX(model, "Head", 6.0F + breathing * 1.4F);
        rotateY(model, "Head", -12.0F);

        // Counter-curve the tail over the opposite shoulder and upper back.
        rotateY(model, "Tail1", -12.0F);
        rotateY(model, "Tail2", -22.0F);
        rotateY(model, "Tail3", -29.0F);
        rotateY(model, "Tail4", -23.0F);
        rotateY(model, "Tail5", -14.0F + tailMotion * 2.3F);
        rotateX(model, "Tail3", 11.0F);
        rotateX(model, "Tail4", 9.0F);

        // Fold the wings by turning both shoulder joints and elbow joints
        // backward. Keep the complete original wing mesh and baby scale.
        rotateY(model, "armL1", -22.0F);
        rotateY(model, "armR1", 22.0F);
        rotateZ(model, "armL1", 32.0F);
        rotateZ(model, "armR1", -32.0F);
        rotateX(model, "armL1", -16.0F);
        rotateX(model, "armR1", -16.0F);
        rotateX(model, "armL2", -30.0F);
        rotateX(model, "armR2", -30.0F);
        rotateZ(model, "armL2", -15.0F);
        rotateZ(model, "armR2", 15.0F);

        // Hind paws cling to the wearer's shoulders and upper back.
        rotateX(model, "ThighL", -18.0F);
        rotateX(model, "ThighR", -18.0F);
        rotateX(model, "LegL", 22.0F);
        rotateX(model, "LegR", 22.0F);

        // Tiny breathing movement, no walking while resting on the player.
        AdvancedModelBox body = model.getCube("BodyUpper");
        if (body != null) body.rotationPointY += breathing * 0.045F;
    }

    private static void rotateX(TabulaModel<AurenDragon> model, String name, float degrees) {
        AdvancedModelBox part = model.getCube(name);
        if (part != null) part.rotateAngleX += radians(degrees);
    }

    private static void rotateY(TabulaModel<AurenDragon> model, String name, float degrees) {
        AdvancedModelBox part = model.getCube(name);
        if (part != null) part.rotateAngleY += radians(degrees);
    }

    private static void rotateZ(TabulaModel<AurenDragon> model, String name, float degrees) {
        AdvancedModelBox part = model.getCube(name);
        if (part != null) part.rotateAngleZ += radians(degrees);
    }

    private static float radians(float degrees) {
        return degrees * ((float) Math.PI / 180.0F);
    }
}
