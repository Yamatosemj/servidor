package br.com.auren.minecraft.client;

import br.com.auren.minecraft.AurenMinecraft;
import br.com.auren.minecraft.entity.AurenDragon;
import com.iafenvoy.uranus.client.model.AdvancedModelBox;
import com.iafenvoy.uranus.client.model.TabulaModel;
import com.iafenvoy.uranus.client.model.util.TabulaModelHandlerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Uses the original IceAndFire-CE 1.21.1 Lightning Dragon Tabula pose files.
 *
 * LightningTabulaDragonAnimator in CE defines 4 WALK and 6 FLIGHT frames.
 * We preserve those actual upstream frames, interpolate individual bones
 * (the same technique CE uses in DragonTabulaModelAnimator), and avoid
 * fabricated walk/wing motion. The full CE animator cannot run unchanged
 * because it expects DragonBaseEntity's state machine and animation events.
 *
 * Visual airborne states are NOT a flying movement controller / flight AI.
 */
public final class AurenOriginalAnimations {
    private static final String PREFIX = "lightningdragon/lightningdragon_";
    private final List<TabulaModel<AurenDragon>> walk;
    private final List<TabulaModel<AurenDragon>> flight;
    private final TabulaModel<AurenDragon> flying;
    private final TabulaModel<AurenDragon> hovering;
    private final TabulaModel<AurenDragon> diving;

    private AurenOriginalAnimations() {
        this.walk = loadSeries("walk", 4);
        this.flight = loadSeries("flight", 6);
        this.flying = load("flying");
        this.hovering = load("hovering");
        this.diving = load("diving");
    }

    private static class Holder {
        static final AurenOriginalAnimations INSTANCE = new AurenOriginalAnimations();
    }

    public static AurenOriginalAnimations instance() {
        return Holder.INSTANCE;
    }

    private static TabulaModel<AurenDragon> load(String name) {
        ResourceLocation resource = ResourceLocation.fromNamespaceAndPath(
                AurenMinecraft.MOD_ID, PREFIX + name);
        TabulaModel<AurenDragon> model = TabulaModelHandlerHelper.getModel(resource);
        if (model == null) {
            throw new IllegalStateException("Missing original IceAndFire CE Tabula animation: " + resource);
        }
        return model;
    }

    private static List<TabulaModel<AurenDragon>> loadSeries(String prefix, int count) {
        List<TabulaModel<AurenDragon>> result = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) result.add(load(prefix + i));
        return List.copyOf(result);
    }

    /**
     * Walking uses CE's four original walk keyframes, selected by the vanilla
     * limb cycle. In air the original flying/hovering/diving poses are previewed
     * when a jump or fall is already happening. Actual flight comes later.
     */
    public void apply(TabulaModel<AurenDragon> model, AurenDragon dragon,
                      float limbSwing, float limbSwingAmount, float age) {
        if (!dragon.onGround() && !dragon.isInWater() && dragon.tickCount > 5) {
            double verticalSpeed = dragon.getDeltaMovement().y;
            if (verticalSpeed < -0.35D) {
                AurenPoseTransition.apply(model, this.diving, 1.0F);
            } else if (Math.abs(verticalSpeed) < 0.025D) {
                AurenPoseTransition.apply(model, this.hovering, 1.0F);
            } else {
                AurenPoseTransition.apply(model, this.flying, 1.0F);
                // Flight cycle is the six source keyframes from CE, not a
                // hand-animated wing flap.
                blendCycle(model, this.flight, age * 0.10F, 1.0F);
            }
            return;
        }

        // Stay in the neutral original ground pose while standing still.
        float strength = Mth.clamp(limbSwingAmount * 2.5F, 0.0F, 1.0F);
        if (strength > 0.02F) {
            blendCycle(model, this.walk, limbSwing * 0.22F, strength);
        }
    }

    /** Interpolate source keyframes by bone name, incl. pivots and wrapped angles. */
    private static void blendCycle(TabulaModel<AurenDragon> model,
                                   List<TabulaModel<AurenDragon>> frames,
                                   float cycle, float weight) {
        float phase = Mth.positiveModulo(cycle, (float) frames.size());
        int current = (int) Math.floor(phase);
        float fraction = phase - current;
        TabulaModel<AurenDragon> a = frames.get(current);
        TabulaModel<AurenDragon> b = frames.get((current + 1) % frames.size());
        for (AdvancedModelBox bone : model.getCubes().values()) {
            AdvancedModelBox start = a.getCube(bone.boxName);
            AdvancedModelBox end = b.getCube(bone.boxName);
            if (start == null || end == null) continue;
            float x = mixAngle(start.rotateAngleX, end.rotateAngleX, fraction);
            float y = mixAngle(start.rotateAngleY, end.rotateAngleY, fraction);
            float z = mixAngle(start.rotateAngleZ, end.rotateAngleZ, fraction);
            bone.rotateAngleX = mixAngle(bone.rotateAngleX, x, weight);
            bone.rotateAngleY = mixAngle(bone.rotateAngleY, y, weight);
            bone.rotateAngleZ = mixAngle(bone.rotateAngleZ, z, weight);
            bone.rotationPointX = Mth.lerp(weight, bone.rotationPointX,
                    Mth.lerp(fraction, start.rotationPointX, end.rotationPointX));
            bone.rotationPointY = Mth.lerp(weight, bone.rotationPointY,
                    Mth.lerp(fraction, start.rotationPointY, end.rotationPointY));
            bone.rotationPointZ = Mth.lerp(weight, bone.rotationPointZ,
                    Mth.lerp(fraction, start.rotationPointZ, end.rotationPointZ));
        }
    }

    /** Match CE's shortest-angle pose transitions across the +/- pi boundary. */
    private static float mixAngle(float start, float end, float amount) {
        float delta = (float) Math.atan2(Math.sin(end - start), Math.cos(end - start));
        return start + delta * amount;
    }
}
