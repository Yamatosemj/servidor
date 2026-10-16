package br.com.auren.minecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Thin lifecycle adapter around the ORIGINAL IceAndFire CE flight manager port.
 *
 * AurenIafDragonFlightManager owns target selection, acceleration, turning
 * and pitch. This class ONLY manages takeoff, landing, gravity, owner lifetime
 * and the transition to/from the CE/uranus flying MoveControl/navigation.
 *
 * The previous bespoke Auren velocity-steering code was deliberately removed.
 */
public final class AurenFlightController {
    public static final int GROUNDED = 0;
    public static final int TAKING_OFF = 1;
    public static final int FLYING = 2;
    public static final int LANDING = 3;

    private static final int TARGET_TIMEOUT_TICKS = 20;
    private static final int TAKEOFF_TICKS = 12;
    private static final int MAX_OBSTRUCTION_TICKS = 65;

    private final AurenDragon dragon;
    private final AurenIafDragonFlightManager iafFlightManager;

    private LivingEntity owner;
    private Vec3 patrolPoint;
    private int ticksSinceOwner;
    private int flightTicks;
    private int obstructionTicks;
    // A stationary marked-prey firing stance must NOT be represented by a
    // moving waypoint a few centimetres above our current position.
    private boolean holding;
    private Vec3 holdFacing;

    AurenFlightController(AurenDragon dragon) {
        this.dragon = dragon;
        this.iafFlightManager = new AurenIafDragonFlightManager(dragon);
    }

    public AurenIafDragonFlightManager getIafFlightManager() {
        return this.iafFlightManager;
    }

    /**
     * Follow goal requests an air escort once per server tick.
     * Comfortable distances and the 50-block emergency teleport remain
     * defined by AurenAdultFollowOwnerGoal, not by this port.
     */
    public boolean follow(LivingEntity targetOwner) {
        if (!(dragon.level() instanceof ServerLevel level)
                || targetOwner == null || !targetOwner.isAlive()
                || targetOwner.level() != level
                || !dragon.isAlive() || dragon.isPassenger()
                || dragon.isOnShoulder() || dragon.isOrderedToSit()
                || dragon.isInWater() || dragon.isInLava()) {
            return false;
        }

        if (dragon.getFlightMode() == LANDING) return false;

        if (dragon.getFlightMode() == GROUNDED) {
            if (!canLiftOff(level)) return false;

            // IceAndFire DragonBaseEntity.switchNavigator(1) switches both
            // navigation and MoveControl. Our parent is TamableAnimal, so the
            // switch is exposed as a small AurenDragon adapter.
            dragon.useIceAndFireFlightNavigation(
                    new AurenIafDragonFlightManager.FlightMoveHelper(
                            dragon, this.iafFlightManager));
            dragon.setFlightMode(TAKING_OFF);
            dragon.setNoGravity(true);
            dragon.fallDistance = 0;
            dragon.setDeltaMovement(dragon.getDeltaMovement().add(0, 0.29D, 0));
            this.flightTicks = 0;
            this.obstructionTicks = 0;
            this.iafFlightManager.clear();
        }

        this.owner = targetOwner;
        this.patrolPoint = null;
        this.holding = false;
        this.holdFacing = null;
        this.ticksSinceOwner = 0;
        return true;
    }

    /** Marked attack waypoint. Reuses the CE FlightMoveHelper, not new physics. */
    public boolean patrol(LivingEntity targetOwner, Vec3 waypoint) {
        if (waypoint == null || !(dragon.level() instanceof ServerLevel level)
                || !level.hasChunkAt(BlockPos.containing(waypoint))) return false;
        if (!follow(targetOwner)) return false;
        this.patrolPoint = waypoint;
        this.iafFlightManager.setFlightTarget(waypoint);
        this.iafFlightManager.setTacklePursuit(false);
        return true;
    }

    /** Use the CE TACKLE 10-degree turn rate only for a prey dive. */
    public boolean patrolAttack(LivingEntity owner, Vec3 waypoint, boolean tackle) {
        if (!patrol(owner, waypoint)) return false;
        this.iafFlightManager.setTacklePursuit(tackle);
        return true;
    }

    /**
     * Stop CE pursuit acceleration while breathing at hidden prey.
     * Hold altitude with no artificial near-zero waypoint, and turn gently
     * towards the impact: no runaway body rotation while stationary.
     */
    public boolean holdFacing(LivingEntity targetOwner, Vec3 aim) {
        if (aim == null || !follow(targetOwner)) return false;
        this.holding = true;
        this.holdFacing = aim;
        this.patrolPoint = null;
        this.iafFlightManager.setFlightTarget(null);
        this.iafFlightManager.setTacklePursuit(false);
        return true;
    }

    private boolean canLiftOff(ServerLevel level) {
        BlockPos above = dragon.blockPosition().above(2);
        return level.hasChunkAt(above)
                && level.noCollision(dragon, dragon.getBoundingBox().move(0, 1.3D, 0));
    }

    /** Stop requesting horizontal flight and descend with vanilla gravity. */
    public void land() {
        if (dragon.getFlightMode() == GROUNDED
                || dragon.getFlightMode() == LANDING) return;
        this.owner = null;
        this.patrolPoint = null;
        this.holding = false;
        this.holdFacing = null;
        this.iafFlightManager.clear();
        dragon.setFlightMode(LANDING);
        dragon.setNoGravity(false);
        dragon.fallDistance = 0;
    }

    /**
     * Called by shoulder mode, emergency teleport and reload.
     * Clears all transient state AND restores the pre-flight navigator;
     * never leaves a stale CE MoveControl active after landing.
     */
    public void reset() {
        this.iafFlightManager.clear();
        this.owner = null;
        this.patrolPoint = null;
        this.holding = false;
        this.holdFacing = null;
        this.ticksSinceOwner = 0;
        this.flightTicks = 0;
        this.obstructionTicks = 0;
        dragon.useOriginalGroundNavigation();
        dragon.setFlightMode(GROUNDED);
        dragon.setNoGravity(false);
        dragon.fallDistance = 0;
        dragon.setDeltaMovement(Vec3.ZERO);
    }

    public void tick() {
        if (!(dragon.level() instanceof ServerLevel level)) return;
        int state = dragon.getFlightMode();
        if (state == GROUNDED || dragon.getControllingPassenger() != null) return;

        if (dragon.isOnShoulder() || !dragon.isAlive()
                || dragon.isInWater() || dragon.isInLava()) {
            reset();
            return;
        }

        if (dragon.isOrderedToSit() && state != LANDING) {
            land();
            state = LANDING;
        }

        if (state == LANDING) {
            dragon.setNoGravity(false);
            Vec3 velocity = dragon.getDeltaMovement();
            dragon.setDeltaMovement(velocity.x * 0.76D,
                    Math.max(-0.36D, velocity.y), velocity.z * 0.76D);
            dragon.fallDistance = 0;
            if (dragon.onGround() || dragon.verticalCollisionBelow) reset();
            return;
        }

        if (this.owner == null || !this.owner.isAlive()
                || this.owner.level() != level
                || ++this.ticksSinceOwner > TARGET_TIMEOUT_TICKS) {
            land();
            return;
        }

        this.flightTicks++;
        dragon.setNoGravity(true);
        dragon.fallDistance = 0;
        if (this.holding) {
            this.iafFlightManager.setFlightTarget(null);
            // No horizontal acceleration and no yaw spin from a self-target.
            // This preserves CE moving-flight physics outside this posture.
            dragon.setDeltaMovement(dragon.getDeltaMovement().scale(0.72D));
            if (this.holdFacing != null) {
                Vec3 diff = this.holdFacing.subtract(dragon.position());
                if (diff.x * diff.x + diff.z * diff.z > 0.25D) {
                    float desiredYaw = (float) (Mth.atan2(diff.z, diff.x)
                            * (180.0D / Math.PI)) - 90.0F;
                    dragon.setYRot(AurenIafDragonFlightManager.approachDegrees(
                            dragon.getYRot(), desiredYaw, 3.0F));
                    dragon.yBodyRot = dragon.getYRot();
                    dragon.setYHeadRot(dragon.getYRot());
                }
            }
            this.obstructionTicks = 0;
            return;
        }

        if (state == TAKING_OFF && this.flightTicks >= TAKEOFF_TICKS)
            dragon.setFlightMode(FLYING);

        // The original CE flightManager.update chooses a line-of-sight
        // owner-escort target and adds upward momentum if the target is above.
        if (this.patrolPoint != null) {
            // HOVER_BLAST / SCORCH_STREAM route, under server authority.
            this.iafFlightManager.setFlightTarget(this.patrolPoint);
        } else {
            this.iafFlightManager.update(this.owner);
        }
        Vec3 goal = this.iafFlightManager.getFlightTarget();

        if (goal == null) {
            // No waypoint: bleed leftover momentum rather than drifting away.
            dragon.setDeltaMovement(dragon.getDeltaMovement().scale(0.85D));
            if (++this.obstructionTicks > MAX_OBSTRUCTION_TICKS) land();
            return;
        }
        if (!level.hasChunkAt(BlockPos.containing(goal))) {
            // Do not force-load remote areas for a cosmetic escort flight.
            land();
            return;
        }

        // CE's original upward impulse presumes normal dragon gravity.
        // Auren disables gravity during flight, so stabilize only vertical
        // movement while leaving the CE horizontal steering and turns intact.
        Vec3 motion = dragon.getDeltaMovement();
        double desiredVerticalSpeed = Mth.clamp(
                (goal.y - dragon.getY()) * 0.10D, -0.16D, 0.18D);
        double stabilizedY = Mth.lerp(0.22D, motion.y, desiredVerticalSpeed);
        dragon.setDeltaMovement(motion.x,
                Mth.clamp(stabilizedY, -0.20D, 0.22D), motion.z);

        // Safety retained from our previous working prototype: the original
        // flight mover already handles horizontalCollision by retargeting.
        // Here we only abort endless loops and avoid unloaded forward chunks.
        Vec3 ahead = dragon.position().add(dragon.getDeltaMovement().scale(3.0D));
        if (!level.hasChunkAt(BlockPos.containing(ahead))) {
            land();
            return;
        }

        if (dragon.horizontalCollision || dragon.verticalCollision) {
            if (++this.obstructionTicks > MAX_OBSTRUCTION_TICKS) land();
        } else {
            this.obstructionTicks = Math.max(0, this.obstructionTicks - 2);
        }
    }
}
