package br.com.auren.minecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;

/**
 * Adult-only follow behavior. Never uses vanilla FollowOwnerGoal, which
 * hard-codes teleportation at a distance unsuitable for Auren's huge form.
 *
 * Movement: start following at >13 blocks, stop at <=8 blocks.
 * Emergency teleport: only beyond 40 blocks, and only to a safe destination
 * approximately 8..12 blocks away. If no safe point exists, do not teleport.
 *
 * The baby retains the existing vanilla follow goal and shoulder mechanics.
 */
public final class AurenAdultFollowOwnerGoal extends Goal {
    static final double START_DISTANCE = 13.0D;
    static final double STOP_DISTANCE = 8.0D;
    static final double EMERGENCY_TELEPORT_DISTANCE = 40.0D;

    private static final double WALK_SPEED = 1.05D;
    private static final int REPATH_INTERVAL_TICKS = 10;
    private static final int MAX_TELEPORT_ATTEMPTS = 20;

    private final AurenDragon dragon;
    private final PathNavigation navigation;
    private LivingEntity owner;
    private int repathCooldown;

    public AurenAdultFollowOwnerGoal(AurenDragon dragon) {
        this.dragon = dragon;
        this.navigation = dragon.getNavigation();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean adultMayFollow(LivingEntity player) {
        return !this.dragon.isSmallForm()
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
        if (this.dragon.distanceToSqr(current) <= START_DISTANCE * START_DISTANCE) {
            return false;
        }
        this.owner = current;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return adultMayFollow(this.owner)
                && this.dragon.distanceToSqr(this.owner) > STOP_DISTANCE * STOP_DISTANCE;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.navigation.stop();
    }

    @Override
    public void tick() {
        if (this.owner == null) return;

        this.dragon.getLookControl().setLookAt(this.owner, 10.0F, 10.0F);
        if (--this.repathCooldown > 0) return;
        this.repathCooldown = REPATH_INTERVAL_TICKS;

        double distanceSq = this.dragon.distanceToSqr(this.owner);
        if (distanceSq >= EMERGENCY_TELEPORT_DISTANCE * EMERGENCY_TELEPORT_DISTANCE
                && this.teleportNearOwner()) {
            return;
        }

        // Do not run straight into the owner's position: navigation will be
        // stopped by canContinueToUse() once the 8-block limit is reached.
        this.navigation.moveTo(this.owner, WALK_SPEED);
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

                this.dragon.setPos(posX, posY, posZ);
                this.navigation.stop();
                return true;
            }
        }
        return false;
    }
}
