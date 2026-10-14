package br.com.auren.minecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dedicated, collision-aware, server-authoritative autonomous flight.
 *
 * Inspired by the separation of flight state / target / move control in
 * IceAndFire CE's IafDragonFlightManager, WITHOUT depending on CE classes.
 * Flight is real entity velocity, not setPos() or a fake render animation.
 * Vanilla collision, chunk loading and gravity still govern the movement.
 */
public final class AurenFlightController {
    public static final int GROUNDED = 0;
    public static final int TAKING_OFF = 1;
    public static final int FLYING = 2;
    public static final int LANDING = 3;

    private static final int TARGET_TIMEOUT_TICKS = 15;
    private static final int TAKEOFF_TICKS = 12;
    private static final double ADULT_AIR_SPEED = 0.32D;
    private static final double BABY_AIR_SPEED = 0.25D;

    private final AurenDragon dragon;
    private Vec3 target;
    private int ticksSinceTarget;
    private int flightTicks;
    private int obstructionTicks;

    AurenFlightController(AurenDragon dragon) {
        this.dragon = dragon;
    }

    /**
     * Called by the owner-follow goal every server tick. An immediate takeoff
     * is attempted only if there is headroom; otherwise normal ground walking
     * remains available. A moving owner causes a new target each tick.
     */
    public boolean follow(LivingEntity owner) {
        if (!(dragon.level() instanceof ServerLevel level)
                || dragon.isOnShoulder() || dragon.isOrderedToSit()
                || !dragon.isAlive() || dragon.isPassenger()
                || dragon.isInWater() || dragon.isInLava()
                || owner.level() != level || !owner.isAlive()) {
            return false;
        }

        if (dragon.getFlightMode() == GROUNDED) {
            if (!canLiftOff(level)) return false;
            dragon.getNavigation().stop();
            dragon.setFlightMode(TAKING_OFF);
            dragon.setNoGravity(true);
            dragon.fallDistance = 0;
            dragon.setDeltaMovement(dragon.getDeltaMovement().add(0, 0.29D, 0));
            this.flightTicks = 0;
        } else if (dragon.getFlightMode() == LANDING) {
            // Owner moved away again while the Auren was descending.
            dragon.setFlightMode(FLYING);
            dragon.setNoGravity(true);
        }

        double altitude = dragon.isSmallForm() ? 2.8D : 5.2D;
        double horizontalOffset = dragon.isSmallForm() ? 2.7D : 6.0D;
        Vec3 delta = dragon.position().subtract(owner.position());
        Vec3 horizontal = new Vec3(delta.x, 0, delta.z);
        if (horizontal.lengthSqr() < 0.01D) {
            float angle = owner.yBodyRot * Mth.DEG_TO_RAD;
            horizontal = new Vec3(-Mth.sin(angle), 0, Mth.cos(angle));
        }
        horizontal = horizontal.normalize();
        double targetY = Mth.clamp(owner.getY() + altitude,
                level.getMinBuildHeight() + 3.0D,
                level.getMaxBuildHeight() - 4.0D);
        target = new Vec3(owner.getX() + horizontal.x * horizontalOffset,
                targetY,
                owner.getZ() + horizontal.z * horizontalOffset);
        ticksSinceTarget = 0;
        return true;
    }

    private boolean canLiftOff(ServerLevel level) {
        BlockPos above = dragon.blockPosition().above(2);
        return level.hasChunkAt(above)
                && level.noCollision(dragon, dragon.getBoundingBox().move(0, 1.3D, 0));
    }

    /** Called if the goal is interrupted, owner reached, or mode changed. */
    public void land() {
        if (dragon.getFlightMode() == GROUNDED) return;
        target = null;
        dragon.setFlightMode(LANDING);
        dragon.setNoGravity(false);
        dragon.fallDistance = 0;
    }

    /** Important for shoulder transition and the 50-block safe teleport. */
    public void reset() {
        target = null;
        flightTicks = 0;
        obstructionTicks = 0;
        ticksSinceTarget = 0;
        dragon.setFlightMode(GROUNDED);
        // The shoulder code is responsible for re-enabling noGravity AFTER reset.
        dragon.setNoGravity(false);
        dragon.fallDistance = 0;
        dragon.setDeltaMovement(Vec3.ZERO);
    }

    /** Server side: applies real velocity; never translates through obstacles. */
    public void tick() {
        if (!(dragon.level() instanceof ServerLevel level)) return;
        int state = dragon.getFlightMode();
        if (state == GROUNDED) return;

        if (dragon.isOnShoulder() || dragon.isOrderedToSit()
                || dragon.isInWater() || dragon.isInLava() || !dragon.isAlive()) {
            reset();
            return;
        }

        if (state == LANDING) {
            dragon.setNoGravity(false);
            // Gravity produces the descent; only damp fast horizontal drift.
            Vec3 velocity = dragon.getDeltaMovement();
            dragon.setDeltaMovement(velocity.x * 0.76D,
                    Math.max(-0.36D, velocity.y), velocity.z * 0.76D);
            dragon.fallDistance = 0;
            if (dragon.onGround() || dragon.verticalCollisionBelow) {
                reset();
            }
            return;
        }

        // The owner disappeared or the follow goal stopped sending targets.
        if (target == null || ++ticksSinceTarget > TARGET_TIMEOUT_TICKS) {
            land();
            return;
        }

        if (!level.hasChunkAt(BlockPos.containing(target))) {
            // Do not drag new terrain into memory to make the dragon fly.
            land();
            return;
        }

        flightTicks++;
        dragon.setNoGravity(true);
        dragon.fallDistance = 0;

        if (state == TAKING_OFF && flightTicks >= TAKEOFF_TICKS) {
            dragon.setFlightMode(FLYING);
        }

        Vec3 toward = target.subtract(dragon.position());
        double distance = toward.length();
        if (distance < 0.1D) {
            dragon.setDeltaMovement(dragon.getDeltaMovement().scale(0.7D));
            return;
        }

        double maxSpeed = dragon.isSmallForm() ? BABY_AIR_SPEED : ADULT_AIR_SPEED;
        double desiredSpeed = Math.min(maxSpeed, 0.07D + distance * 0.075D);
        Vec3 desired = toward.normalize().scale(desiredSpeed);

        // Lift off clearly before accelerating horizontally; avoids skidding
        // along the grass and gives the CE takeoff/hover pose room to render.
        if (dragon.getFlightMode() == TAKING_OFF && flightTicks < 7) {
            desired = new Vec3(0, 0.22D, 0);
        }

        // Search only CURRENTLY LOADED space. Never set noPhysics, phase into
        // walls or force-load arbitrary chunks. Prefer a small upward detour.
        Vec3 ahead = desired.normalize().scale(Math.max(1.0D, desired.length() * 5.0D));
        AABB futureBox = dragon.getBoundingBox().move(ahead);
        if (!level.hasChunkAt(BlockPos.containing(dragon.position().add(ahead)))
                || !level.noCollision(dragon, futureBox)) {
            obstructionTicks++;
            Vec3 rise = new Vec3(0, 0.24D, 0);
            if (level.hasChunkAt(dragon.blockPosition().above(2))
                    && level.noCollision(dragon, dragon.getBoundingBox().move(0, 1.5D, 0))) {
                desired = rise;
            } else {
                // Solid roof: do not try to force through the ceiling.
                desired = new Vec3(0, -0.06D, 0);
            }
            // If still trapped, restore normal gravity and ground navigation.
            if (obstructionTicks > 65) {
                land();
                return;
            }
        } else {
            obstructionTicks = Math.max(0, obstructionTicks - 3);
        }

        Vec3 previous = dragon.getDeltaMovement();
        Vec3 next = previous.scale(0.65D).add(desired.scale(0.35D));
        next = new Vec3(
                Mth.clamp(next.x, -maxSpeed, maxSpeed),
                Mth.clamp(next.y, -0.24D, 0.27D),
                Mth.clamp(next.z, -maxSpeed, maxSpeed)
        );
        dragon.setDeltaMovement(next);
        dragon.hasImpulse = true;

        if (next.horizontalDistanceSqr() > 0.0005D) {
            float yaw = (float) (Mth.atan2(next.z, next.x) * (180.0D / Math.PI)) - 90.0F;
            dragon.setYRot(Mth.rotLerp(0.20F, dragon.getYRot(), yaw));
            dragon.yBodyRot = dragon.getYRot();
            dragon.yHeadRot = dragon.getYRot();
        }
    }
}
