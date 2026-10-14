package br.com.auren.minecraft.client;

import br.com.auren.minecraft.AurenMinecraft;
import br.com.auren.minecraft.entity.AurenDragon;
import br.com.auren.minecraft.entity.AurenFlightController;
import com.iafenvoy.uranus.client.model.AdvancedModelBox;
import com.iafenvoy.uranus.client.model.TabulaModel;
import com.iafenvoy.uranus.client.model.util.TabulaModelHandlerHelper;
import net.minecraft.client.Minecraft;
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
 * The real AurenFlightController now drives takeoff, flight and landing on
 * the server; these original CE keyframes are selected by its synced state.
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
     * Visual states come from the actual SERVER flight state, never just
     * 'off ground'. This prevents random jumps from looking like long flights
     * and prevents no-gravity flight from being mistaken for hovering.
     */
    public void apply(TabulaModel<AurenDragon> model, AurenDragon dragon,
                      float limbSwing, float limbSwingAmount, float age) {
        int mode = dragon.getFlightMode();
        float partialTick = Minecraft.getInstance().getTimer()
                .getGameTimeDeltaPartialTick(false);
        float flightBlend = dragon.getFlightBlend(partialTick);

        if (mode != AurenFlightController.GROUNDED || flightBlend > 0.01F) {
            if (mode == AurenFlightController.FLYING) {
                if (dragon.getDeltaMovement().y < -0.18D) {
                    AurenPoseTransition.apply(model, this.diving, flightBlend);
                } else {
                    AurenPoseTransition.apply(model, this.flying, flightBlend * 0.45F);
                    // Six ORIGINAL lightning-dragon CE flight frames, blended
                    // continuously (never fabricated per-joint wing flapping).
                    blendCycle(model, this.flight, age * 0.16F, flightBlend);
                }
            } else {
                // Original CE hovering pose for takeoff and slow landing.
                AurenPoseTransition.apply(model, this.hovering, flightBlend);
            }
            // In the original CE the keyframes are only part of the flight
            // animation: body bob, tail/neck waves, toes and thighs still move.
            AurenOriginalFlightMotion.apply(model, age, flightBlend);
            return;
        }

        // Walking uses CE's four original walk keyframes.
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
