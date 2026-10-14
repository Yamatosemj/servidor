/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Port of selected algorithms from IceAndFire Community Edition 1.21.1:
 *   IafDragonFlightManager.FlightMoveHelper (yaw, acceleration, pitch)
 *   IafDragonFlightManager.update (airborne target maintenance / upward lift)
 *   DragonUtils.getBlockInViewEscort (owner-centric waypoint selection)
 *
 * Copyright (c) 2024 IAFEnvoy, original authors.
 * Changes © Auren contributors. Original upstream license and attribution:
 * https://github.com/IAFEnvoy/IceAndFire-CE/blob/1.21.1/LICENSE
 *
 * The port replaces DragonBaseEntity/IafCommonConfig with AurenDragon and
 * its owner-specific requirements. It does not import IceAndFire as a mod.
 */
package br.com.auren.minecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A lightly adapted port of the ACTUAL CE flight manager and its move helper.
 *
 * The Auren uses the same CE flight acceleration and yaw/pitch control, not
 * another hand-written pursuit velocity controller. The combat-only modes
 * (TACKLE/HOVER_BLAST/SCORCH_STREAM) are omitted until Auren has combat.
 * The source escort-position sampling is preserved, with smaller sampling
 * radius/altitude for the baby, loaded-chunk/collision safety, and updates for
 * an owner who is constantly moving.
 */
public final class AurenIafDragonFlightManager {
    // CE reselects on arrival or obstruction; we additionally refresh if
    // the owner travels away. A relaxed cadence keeps smooth 4° CE turns.
    private static final int WAYPOINT_REEVALUATE_TICKS = 60;
    private final AurenDragon dragon;
    private Vec3 target;
    private Vec3 lastOwnerPos;
    private int targetTicks;

    public AurenIafDragonFlightManager(AurenDragon dragon) {
        this.dragon = dragon;
    }

    // Unchanged helpers from IafDragonFlightManager.
    public static float approach(float number, float max, float min) {
        min = Math.abs(min);
        return number < max ? Mth.clamp(number + min, number, max)
                : Mth.clamp(number - min, max, number);
    }

    public static float approachDegrees(float number, float max, float min) {
        float add = Mth.wrapDegrees(max - number);
        return approach(number, number + add, min);
    }

    public static float degreesDifferenceAbs(float f1, float f2) {
        return Math.abs(Mth.wrapDegrees(f2 - f1));
    }

    public Vec3 getFlightTarget() {
        return this.target;
    }

    public void setFlightTarget(Vec3 target) {
        this.target = target;
    }

    public void clear() {
        this.target = null;
        this.lastOwnerPos = null;
        this.targetTicks = 0;
    }

    public void invalidateTarget() {
        this.target = null;
        this.targetTicks = WAYPOINT_REEVALUATE_TICKS;
    }

    /**
     * The owner-escort branch of CE's flightManager.update:
     * DragonUtils.getBlockInViewEscort supplies viewable target blocks.
     * Unlike CE's combat manager, the Auren has no combat target/airAttack
     * states, so the escort branch is the only relevant code today.
     */
    public void update(LivingEntity owner) {
        if (owner == null || !owner.isAlive() || owner.level() != dragon.level()) {
            this.target = null;
            return;
        }

        boolean moved = this.lastOwnerPos == null
                || this.lastOwnerPos.distanceToSqr(owner.position()) > 64.0D;
        boolean reached = this.target == null
                || dragon.position().distanceToSqr(this.target) < 4.0D;
        boolean obstructed = this.target != null && isTargetBlocked(this.target);
        if (reached || obstructed || moved || ++this.targetTicks > WAYPOINT_REEVALUATE_TICKS) {
            // CE helper: sample several potential escort points in front of
            // the owner, keep one that is in sight and sufficiently far away.
            Vec3 next = getBlockInViewEscort(owner);
            if (next != null) this.target = next;
            else if (this.target != null && (obstructed || reached)) this.target = null;
            this.lastOwnerPos = owner.position();
            this.targetTicks = 0;
        }

        // CE adds +0.1 Y per tick to counter its standard dragon gravity.
        // Auren's flight uses noGravity=true: retaining the unbounded boost
        // creates airborne 'jumps'. CE's FlightMoveHelper still controls
        // yaw/pitch/horizontal acceleration; lifecycle bounds vertical speed.
    }

    /**
     * Ported from DragonUtils.getBlockInViewEscort:
     *
     * The source uses random offsets around escortPos and altitude
     * 'escortY + 8 + random(16)' unless the owner is high above the ground.
     * We retain the strategy, but downscale the baby and reject unloaded,
     * blocked or obstructed candidate positions. We never load owner-neighbor
     * chunks purely to choose a flight target.
     */
    private Vec3 getBlockInViewEscort(LivingEntity owner) {
        BlockPos escortPos = owner.blockPosition();
        if (!dragon.level().hasChunkAt(escortPos)) return null;

        BlockPos ground = dragon.level().getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, escortPos);
        int distFromGround = escortPos.getY() - ground.getY();
        int wanderDistance = dragon.isSmallForm() ? 7 : 15;
        // The vanilla CE's adult escort height (8..24 above owner) is taller
        // than Auren's selected 2/8-block stop radius. Use stage-appropriate
        // target heights so canContinueToUse() can eventually land.
        int baseHeight = dragon.isSmallForm() ? 2 : 4;
        int heightRange = dragon.isSmallForm() ? 2 : 3;

        for (int i = 0; i < 10; i++) {
            BlockPos pos = new BlockPos(
                    escortPos.getX() + dragon.getRandom().nextInt(wanderDistance)
                            - wanderDistance / 2,
                    distFromGround > 16 ? escortPos.getY() :
                            escortPos.getY() + baseHeight
                                    + dragon.getRandom().nextInt(heightRange),
                    escortPos.getZ() + dragon.getRandom().nextInt(wanderDistance)
                            - wanderDistance / 2);

            if (pos.getY() > dragon.level().getMaxBuildHeight() - 3
                    || pos.getY() < dragon.level().getMinBuildHeight() + 3
                    || !dragon.level().hasChunkAt(pos)) continue;

            Vec3 candidate = Vec3.atCenterOf(pos);
            if (dragon.position().distanceToSqr(candidate) <= 6.0D) continue;
            if (!dragon.level().noCollision(dragon, dragon.getBoundingBox().move(
                    candidate.x - dragon.getX(),
                    candidate.y - dragon.getY(),
                    candidate.z - dragon.getZ()))) continue;
            if (!isTargetBlocked(candidate)) return candidate;
        }
        return null;
    }

    private boolean isTargetBlocked(Vec3 candidate) {
        // A ray is cheap and deterministic; CE's original uses
        // dragon.isTargetBlocked for the same escort-candidate test.
        Vec3 start = dragon.getEyePosition();
        return dragon.level().clip(new ClipContext(start, candidate,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                dragon)).getType() != HitResult.Type.MISS;
    }

    /**
     * DIRECT PORT of the IceAndFire CE IafDragonFlightManager.FlightMoveHelper.
     * Vanilla Mob invokes this in its normal AI movement-control tick.
     *
     * Specific Auren adaptations are commented next to their lines:
     * - guard division-by-zero around vertical flight (upstream can NaN),
     * - clamp yaw climb modifier rather than allowing reverse flight,
     * - do not repeatedly multiply the PERSISTENT speedModifier by proximity
     *   slowdown (upstream uses distant patrol targets; Auren follows nearby),
     * - use symmetric acceleration bounds for negative components,
     * - have the owner target instead of any combat-only target/attack state.
     */
    public static final class FlightMoveHelper extends MoveControl {
        private final AurenDragon dragon;
        private final AurenIafDragonFlightManager flightManager;

        public FlightMoveHelper(AurenDragon dragon, AurenIafDragonFlightManager flightManager) {
            super(dragon);
            this.dragon = dragon;
            this.flightManager = flightManager;
            // The vanilla parent default speedModifier is 0, which makes
            // the original CE acceleration formula divide by zero at start.
            this.speedModifier = 0.2D;
        }

        @Override
        public void tick() {
            if (dragon.getFlightMode() == AurenFlightController.GROUNDED
                    || dragon.getFlightMode() == AurenFlightController.LANDING) {
                return;
            }
            if (this.dragon.horizontalCollision) {
                this.dragon.setYRot(this.dragon.getYRot() + 180.0F);
                this.speedModifier = 0.1F;
                this.flightManager.invalidateTarget();
                return;
            }

            Vec3 flightTarget = this.flightManager.getFlightTarget();
            if (flightTarget == null) return;

            float distX = (float) (flightTarget.x - this.dragon.getX());
            float distY = (float) (flightTarget.y - this.dragon.getY());
            float distZ = (float) (flightTarget.z - this.dragon.getZ());
            double planeDist = Math.sqrt(distX * distX + distZ * distZ);
            if (planeDist < 1.0E-4D && Math.abs(distY) < 1.0E-4D) return;

            // CE: 1.0 - abs(distY * .7) / planeDist. Protect vertical
            // trajectories from zero/negative factors (otherwise reverses).
            double yDistMod = Mth.clamp(
                    1.0D - (double) Mth.abs(distY * 0.7F)
                            / Math.max(planeDist, 1.0D), 0.2D, 1.0D);
            distX = (float) (distX * yDistMod);
            distZ = (float) (distZ * yDistMod);
            planeDist = Mth.sqrt(distX * distX + distZ * distZ);
            double dist = Math.sqrt(distX * distX + distZ * distZ + distY * distY);

            if (dist > 1.0F) {
                float yawCopy = this.dragon.getYRot();
                float atan = (float) Mth.atan2(distZ, distX);
                float yawTurn = Mth.wrapDegrees(this.dragon.getYRot() + 90);
                float yawTurnAtan = Mth.wrapDegrees(atan * 57.295776F);
                // CE's natural 4-degree turn limiter, not arbitrary lerp.
                this.dragon.setYRot(AurenIafDragonFlightManager.approachDegrees(
                        yawTurn, yawTurnAtan, 4.0F) - 90.0F);
                this.dragon.yBodyRot = this.dragon.getYRot();

                if (AurenIafDragonFlightManager.degreesDifferenceAbs(
                        yawCopy, this.dragon.getYRot()) < 3.0F) {
                    this.speedModifier = AurenIafDragonFlightManager.approach(
                            (float) this.speedModifier, 1.8F,
                            0.005F * (1.8F / Math.max((float) this.speedModifier, 0.05F)));
                } else {
                    this.speedModifier = AurenIafDragonFlightManager.approach(
                            (float) this.speedModifier, 0.2F, 0.025F);
                }

                float finPitch = (float) (-(Mth.atan2(-distY, planeDist)
                        * 57.2957763671875D));
                this.dragon.setXRot(finPitch);
                float yawTurnHead = this.dragon.getYRot() + 90.0F;

                // CE dragonFlightSpeedMod default is 1.0. IMPORTANT:
                // apply the near-target slowdown LOCALLY; compounding it
                // into this.speedModifier makes escort flights too slow.
                double localSpeed = this.speedModifier
                        * Math.min(1.0D, dist / 50.0D + 0.3D);
                double x = localSpeed * Mth.cos(yawTurnHead * 0.017453292F)
                        * Math.abs((double) distX / dist);
                double y = localSpeed * Mth.sin(finPitch * 0.017453292F)
                        * Math.abs((double) distY / dist);
                double z = localSpeed * Mth.sin(yawTurnHead * 0.017453292F)
                        * Math.abs((double) distZ / dist);
                double motionCap = 0.2D;
                this.dragon.setDeltaMovement(this.dragon.getDeltaMovement().add(
                        Mth.clamp(x * 0.2D, -motionCap, motionCap),
                        Mth.clamp(y * 0.2D, -motionCap, motionCap),
                        Mth.clamp(z * 0.2D, -motionCap, motionCap)));
                this.dragon.hasImpulse = true;
            }
        }
    }
}
