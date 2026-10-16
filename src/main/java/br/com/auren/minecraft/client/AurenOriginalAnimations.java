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
    // Original CE Lightning Dragon attack-stream Tabula poses; four original
    // upstream .tbl archives are copied/sha-verified by Gradle.
    private final List<TabulaModel<AurenDragon>> streamCharge;
    private final TabulaModel<AurenDragon> streamBreath;
    private final List<TabulaModel<AurenDragon>> bite;

    private AurenOriginalAnimations() {
        this.walk = loadSeries("walk", 4);
        this.flight = loadSeries("flight", 6);
        this.flying = load("flying");
        this.hovering = load("hovering");
        this.diving = load("diving");
        this.streamCharge = loadSeries("attack_stream_charge", 3);
        this.streamBreath = load("attack_stream_breath");
        this.bite = loadSeries("bite", 3);
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
                    // CE increments flightCycle by 2/tick and uses 10 ticks/pose.
                    blendCycle(model, this.flight, age * 0.2F, flightBlend);
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

    /**
     * Original CE's STREAM charge/breath keyframes. Use ONLY the neck, head,
     * jaw and shoulders (CE itself excludes wing/finger bones during breath).
     * This preserves flight flaps, ground locomotion and the rider attachment.
     * Auren's TamableAnimal cannot run CE's full AnimationHandler unchanged,
     * so its synced warmup ticks select the exact upstream Tabula poses.
     */
    public void applyBreathPose(TabulaModel<AurenDragon> model, AurenDragon dragon,
                                float partialTick) {
        float blend = dragon.getBreathPoseBlend(partialTick);
        if (blend <= 0.01F || dragon.isSmallForm() || dragon.isOnShoulder()) return;

        int ticks = dragon.getBreathAttackTicks();
        TabulaModel<AurenDragon> pose;
        if (ticks == 0 || ticks > 20) {
            pose = this.streamBreath;
        } else if (ticks <= 7) {
            pose = this.streamCharge.get(0);
        } else if (ticks <= 14) {
            pose = this.streamCharge.get(1);
        } else {
            pose = this.streamCharge.get(2);
        }
        // Original CE bone naming, and original poses: jaw opens + head
        // rises as the attack charges. Never overwrite flapping wing bones.
        AurenPoseTransition.applyBones(model, pose, blend,
                "BodyUpper", "Neck1", "Neck2", "Neck3", "Head", "Jaw");
    }

    /** Original CE three-keyframe BITE attack for adult and baby defenders. */
    public void applyBitePose(TabulaModel<AurenDragon> model, AurenDragon dragon) {
        if (dragon.isOnShoulder()) return;
        int ticks = dragon.getBiteAttackTicks();
        if (ticks <= 0) return;
        // The server starts at 12 and counts down. Preserve the original
        // pose order bite1 -> bite2 -> bite3.
        TabulaModel<AurenDragon> pose = this.bite.get(ticks > 8 ? 0 : ticks > 4 ? 1 : 2);
        float weight = ticks > 9 ? (13.0F - ticks) / 4.0F
                : ticks <= 3 ? ticks / 3.0F : 1.0F;
        AurenPoseTransition.applyBones(model, pose, Mth.clamp(weight, 0, 1),
                "BodyUpper", "Neck1", "Neck2", "Neck3", "Head", "Jaw");
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
