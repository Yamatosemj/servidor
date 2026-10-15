/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Derived from IceAndFire Community Edition 2.1.4, commit:
 * aa2f11a6b79acb48a1094765d610cdaca79e3d18
 * com.iafenvoy.iceandfire.entity.util.dragon.IafDragonFlightManager.GroundMoveHelper
 *
 * The original MoveControl STRAFE / MOVE_TO / JUMPING branches remain
 * functionally intact. No IceAndFire runtime mod or registrations required.
 */
package br.com.auren.minecraft.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;

/**
 * The original CE ground movement controller adapted to the SINGLE Auren entity.
 *
 * CE's large-dragon turning allowance is based on its age and hitbox:
 *   5 + (1 - min(ageDays, 125) / 125) * 10 degrees / tick
 * For Auren's fixed adult presentation we use the existing CE stage-3
 * reference (50 days = 11 degrees); our small form uses the CE 70-degree
 * small-hitbox branch. No second dragon entity or age data is introduced.
 *
 * This controller never overrides rider commands, shoulder attachment or
 * the autonomous CE-inspired flight controller. Switching airborne replaces
 * MoveControl temporarily; landing restores this SAME object.
 */
public final class AurenCeGroundMoveControl extends MoveControl {
    // Source: CE GroundMoveHelper.MOVE_TO age-dependent turn range.
    private static final float CE_REFERENCE_ADULT_AGE_DAYS = 50.0F;
    private static final float CE_SMALL_TURN_DEGREES = 70.0F;
    private static final float CE_ADULT_TURN_DEGREES =
            5.0F + (1.0F - Math.min(CE_REFERENCE_ADULT_AGE_DAYS, 125.0F) / 125.0F) * 10.0F;

    private final AurenDragon dragon;

    public AurenCeGroundMoveControl(AurenDragon dragon) {
        super(dragon);
        this.dragon = dragon;
    }

    @Override
    public void tick() {
        // Auren-specific state guard: CE uses other MoveControls for mounted
        // flight and cannot have a shoulder form. Do not stomp rider input.
        if (this.dragon.isVehicle() || this.dragon.isOnShoulder()
                || this.dragon.getFlightMode() != AurenFlightController.GROUNDED) {
            this.operation = Operation.WAIT;
            return;
        }

        // ORIGINAL CE GroundMoveHelper STRAFE branch.
        if (this.operation == Operation.STRAFE) {
            float f = (float) this.mob.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
            float f1 = (float) this.speedModifier * f;
            float f2 = this.strafeForwards;
            float f3 = this.strafeRight;
            float f4 = Mth.sqrt(f2 * f2 + f3 * f3);
            if (f4 < 1.0F) f4 = 1.0F;
            f4 = f1 / f4;
            f2 = f2 * f4;
            f3 = f3 * f4;
            float f5 = Mth.sin(this.mob.getYRot() * 0.017453292F);
            float f6 = Mth.cos(this.mob.getYRot() * 0.017453292F);
            float f7 = f2 * f6 - f3 * f5;
            float f8 = f3 * f6 + f2 * f5;
            PathNavigation pathnavigate = this.mob.getNavigation();
            NodeEvaluator nodeprocessor = pathnavigate.getNodeEvaluator();
            if (nodeprocessor.getPathType(
                    new PathfindingContext(this.mob.level(), this.mob),
                    Mth.floor(this.mob.getX() + (double) f7),
                    Mth.floor(this.mob.getY()),
                    Mth.floor(this.mob.getZ() + (double) f8)) != PathType.WALKABLE) {
                this.strafeForwards = 1.0F;
                this.strafeRight = 0.0F;
                f1 = f;
            }
            this.mob.setSpeed(f1);
            this.mob.setZza(this.strafeForwards);
            this.mob.setXxa(this.strafeRight);
            this.operation = Operation.WAIT;

        // ORIGINAL CE GroundMoveHelper MOVE_TO branch. The only replacement
        // for the DragonBaseEntity-only cast/age accessor is turnRange.
        } else if (this.operation == Operation.MOVE_TO) {
            this.operation = Operation.WAIT;
            double d0 = this.getWantedX() - this.mob.getX();
            double d1 = this.getWantedZ() - this.mob.getZ();
            double d2 = this.getWantedY() - this.mob.getY();
            double d3 = d0 * d0 + d2 * d2 + d1 * d1;

            if (d3 < 2.500000277905201E-7D) {
                this.mob.setZza(0.0F);
                return;
            }

            float targetDegree = (float) (Mth.atan2(d1, d0) * (180D / Math.PI)) - 90.0F;
            // Original CE: 70 degrees for small hitboxes, stage-age formula
            // for large ones. Auren's dimensions are deliberately small,
            // so use its FORM instead of bounding box to keep adult weight.
            float changeRange = this.dragon.isSmallForm()
                    ? CE_SMALL_TURN_DEGREES : CE_ADULT_TURN_DEGREES;
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), targetDegree, changeRange));
            this.mob.setSpeed((float) (this.speedModifier
                    * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            if (d2 > (double) this.mob.maxUpStep()
                    && d0 * d0 + d1 * d1
                    < (double) Math.max(1.0F, this.mob.getBbWidth() / 2)) {
                this.mob.getJumpControl().jump();
                this.operation = Operation.JUMPING;
            }

        // ORIGINAL CE GroundMoveHelper JUMPING branch.
        } else if (this.operation == Operation.JUMPING) {
            this.mob.setSpeed((float) (this.speedModifier
                    * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            if (this.mob.onGround()) this.operation = Operation.WAIT;
        } else {
            this.mob.setZza(0.0F);
        }
    }
}
