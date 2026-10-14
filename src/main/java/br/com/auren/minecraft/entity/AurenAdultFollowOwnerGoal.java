package br.com.auren.minecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;

/**
 * Auren follow + emergency teleport with a REAL autonomous flight controller.
 * Original adult teleport search and stop/start boundaries are preserved.
 * The baby uses the SAME safe flight system, with its old 4.5/1.7 range.
 *
 * Movement: start following at >15 blocks, stop at <=8 blocks.
 * Emergency teleport: only beyond 50 blocks, and only to a safe destination
 * approximately 8..12 blocks away. If no safe point exists, do not teleport.
 *
 * Baby never takes off while on the shoulder; no teleport until 50 blocks.
 */
public final class AurenAdultFollowOwnerGoal extends Goal {
    static final double START_DISTANCE = 15.0D;
    static final double STOP_DISTANCE = 8.0D;
    static final double BABY_START_DISTANCE = 4.5D;
    static final double BABY_STOP_DISTANCE = 1.7D;
    static final double EMERGENCY_TELEPORT_DISTANCE = 50.0D;

    private static final double WALK_SPEED = 1.05D;
    private static final int REPATH_INTERVAL_TICKS = 10;
    private static final int MAX_TELEPORT_ATTEMPTS = 20;
    // Match CE's separation of terrestrial and aerial navigation.
    private static final int GROUND_WALK_BEFORE_FLIGHT_TICKS = 40;
    private static final int GROUND_STALL_TICKS = 30;
    private static final double ADULT_LONG_CHASE_DISTANCE = 32.0D;
    private static final double BABY_LONG_CHASE_DISTANCE = 14.0D;

    private final AurenDragon dragon;
    private final boolean baby;
    private LivingEntity owner;
    private int repathCooldown;
    private int groundPursuitTicks;
    private int stalledGroundTicks;
    private double previousOwnerDistance;

    public AurenAdultFollowOwnerGoal(AurenDragon dragon) {
        this(dragon, false);
    }

    /** Shares the adult's proven safe teleport code with the small form. */
    public AurenAdultFollowOwnerGoal(AurenDragon dragon, boolean baby) {
        this.dragon = dragon;
        this.baby = baby;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean adultMayFollow(LivingEntity player) {
        return this.dragon.isSmallForm() == this.baby
                && !this.dragon.isOnShoulder()
                && this.dragon.isTame()
                && !this.dragon.isOrderedToSit()
                && player != null
                && player.isAlive()
                && !player.isSpectator()
                && player.level() == this.dragon.level();
    }

    @Override
    public boolean canUse() {
        LivingEntity current = this.dragon.getOwner();
        if (!adultMayFollow(current)) return false;
        double start = this.baby ? BABY_START_DISTANCE : START_DISTANCE;
        if (this.dragon.distanceToSqr(current) <= start * start) {
            return false;
        }
        this.owner = current;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!adultMayFollow(this.owner)) return false;
        double stop = this.baby ? BABY_STOP_DISTANCE : STOP_DISTANCE;
        // Compare HORIZONTAL distance in air. Cruising height would otherwise
        // keep the baby above its ground stop distance forever.
        if (this.dragon.getFlightMode() != AurenFlightController.GROUNDED) {
            double dx = this.owner.getX() - this.dragon.getX();
            double dz = this.owner.getZ() - this.dragon.getZ();
            double airborneStop = this.baby ? 2.0D : stop;
            double dy = Math.abs(this.owner.getY() - this.dragon.getY());
            double airborneHeight = this.baby ? 3.5D : 6.3D;
            return dx * dx + dz * dz > airborneStop * airborneStop
                    || dy > airborneHeight;
        }
        return this.dragon.distanceToSqr(this.owner) > stop * stop;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
        this.groundPursuitTicks = 0;
        this.stalledGroundTicks = 0;
        this.previousOwnerDistance = this.dragon.distanceTo(this.owner);
    }

    @Override
    public void stop() {
        this.owner = null;
        this.dragon.getNavigation().stop();
        this.dragon.getFlightController().land();
    }

    @Override
    public void tick() {
        if (this.owner == null) return;

        this.dragon.getLookControl().setLookAt(this.owner, 10.0F, 10.0F);
        int flightMode = this.dragon.getFlightMode();

        // In the air, refresh CE's moving escort destination each tick.
        // Calling follow() on the ground initiates takeoff, so don't do that.
        if (flightMode == AurenFlightController.TAKING_OFF
                || flightMode == AurenFlightController.FLYING) {
            if (this.dragon.getFlightController().follow(this.owner)) {
                this.dragon.getNavigation().stop();
            }
        }

        // Ground pathfinding every tick is costly on Zalith.
        if (--this.repathCooldown > 0) return;
        this.repathCooldown = REPATH_INTERVAL_TICKS;

        double distanceSq = this.dragon.distanceToSqr(this.owner);
        if (distanceSq >= EMERGENCY_TELEPORT_DISTANCE * EMERGENCY_TELEPORT_DISTANCE
                && this.teleportNearOwner()) return;

        // The CE navigator switches back to ground only after touchdown.
        if (flightMode != AurenFlightController.GROUNDED) return;

        // Walk FIRST. Air travel is a fallback, not the default follow action.
        boolean hasGroundRoute = this.dragon.getNavigation().moveTo(this.owner, WALK_SPEED);
        double distance = Math.sqrt(distanceSq);
        if (hasGroundRoute && this.previousOwnerDistance - distance > 0.3D) {
            this.stalledGroundTicks = 0;
        } else {
            this.stalledGroundTicks += REPATH_INTERVAL_TICKS;
        }
        this.previousOwnerDistance = distance;
        this.groundPursuitTicks += REPATH_INTERVAL_TICKS;

        boolean blocked = this.stalledGroundTicks >= GROUND_STALL_TICKS;
        double longChase = this.baby ? BABY_LONG_CHASE_DISTANCE
                : ADULT_LONG_CHASE_DISTANCE;
        boolean longPursuit = this.groundPursuitTicks >= GROUND_WALK_BEFORE_FLIGHT_TICKS
                && distanceSq >= longChase * longChase;

        if (blocked || longPursuit) {
            // Same CE mover/escort used in v0.6.1, but only when necessary.
            // If the ceiling blocks takeoff, keep the active ground route.
            if (this.dragon.getFlightController().follow(this.owner)) {
                this.dragon.getNavigation().stop();
            }
        }
    }

    /**
     * Finds an unoccupied, solid-ground position outside the owner's immediate
     * space. Rejects unloaded chunks, liquids and colliding bounding boxes.
     * The spawn is intentionally not on top of / inside the owner.
     */
    private boolean teleportNearOwner() {
        if (this.owner == null) return false;
        Level level = this.dragon.level();
        BlockPos center = this.owner.blockPosition();

        for (int attempt = 0; attempt < MAX_TELEPORT_ATTEMPTS; attempt++) {
            double angle = this.dragon.getRandom().nextDouble() * (Math.PI * 2.0D);
            double radius = 8.0D + this.dragon.getRandom().nextDouble() * 4.0D;
            int x = (int) Math.floor(this.owner.getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(this.owner.getZ() + Math.sin(angle) * radius);

            // Prefer the owner's ground level, but accept nearby changes in
            // elevation without searching/loading whole new chunks.
            int[] heightOffsets = {0, 1, -1, 2, -2, 3, -3};
            for (int offset : heightOffsets) {
                BlockPos candidate = new BlockPos(x, center.getY() + offset, z);
                BlockPos below = candidate.below();

                if (!level.hasChunkAt(candidate) || !level.hasChunkAt(below)) continue;
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue;
                if (!level.getFluidState(candidate).isEmpty()
                        || !level.getFluidState(candidate.above()).isEmpty()) continue;

                double posX = x + 0.5D;
                double posY = candidate.getY();
                double posZ = z + 0.5D;
                AABB destinationBox = this.dragon.getBoundingBox().move(
                        posX - this.dragon.getX(),
                        posY - this.dragon.getY(),
                        posZ - this.dragon.getZ());
                if (!level.noCollision(this.dragon, destinationBox)) continue;

                this.dragon.getFlightController().reset();
                this.dragon.setPos(posX, posY, posZ);
                this.dragon.getNavigation().stop();
                return true;
            }
        }
        return false;
    }
}
