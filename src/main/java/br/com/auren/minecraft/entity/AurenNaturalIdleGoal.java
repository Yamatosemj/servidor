package br.com.auren.minecraft.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Calm, owner-centered companion behavior. No combat, flight or scripted
 * animations: just quiet observation with occasional short exploratory steps.
 *
 * The existing CE ground navigator owns every walking turn. A tiny bounded
 * search is performed at most once per calm session and only in loaded land.
 */
public final class AurenNaturalIdleGoal extends Goal {
    private static final int MIN_REST_TICKS = 65;
    private static final int MAX_REST_TICKS = 135;
    private static final int MIN_COOLDOWN_TICKS = 100;
    private static final int MAX_COOLDOWN_TICKS = 230;

    private final AurenDragon dragon;
    private LivingEntity owner;
    private int remainingTicks;
    private int cooldownTicks;
    private boolean exploring;
    private Vec3 interestPoint;

    public AurenNaturalIdleGoal(AurenDragon dragon) {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean calm(LivingEntity candidate) {
        if (candidate == null || !candidate.isAlive()
                || candidate.level() != dragon.level() || candidate.isSpectator())
            return false;
        double maxDistance = dragon.isSmallForm() ? 4.0D : 10.0D;
        return dragon.isTame() && dragon.isOwnedBy(candidate)
                && !dragon.isOrderedToSit() && !dragon.isOnShoulder()
                && !dragon.isVehicle() && !dragon.isPassenger()
                && !dragon.getAttackOrders().hasOrder()
                && dragon.getTarget() == null
                && dragon.getFlightMode() == AurenFlightController.GROUNDED
                && dragon.onGround() && !dragon.isInWater()
                && dragon.distanceToSqr(candidate) < maxDistance * maxDistance
                && candidate.getDeltaMovement().horizontalDistanceSqr() < 0.045D;
    }

    @Override
    public boolean canUse() {
        if (cooldownTicks > 0) {
            --cooldownTicks;
            return false;
        }
        LivingEntity possible = dragon.getOwner();
        if (!calm(possible)) return false;
        // A short pause between owner movement and new curiosity sessions
        // keeps following rather than idling as the owner starts walking.
        this.owner = possible;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return remainingTicks > 0 && calm(owner);
    }

    @Override
    public void start() {
        remainingTicks = MIN_REST_TICKS + dragon.getRandom().nextInt(
                MAX_REST_TICKS - MIN_REST_TICKS + 1);
        interestPoint = null;
        exploring = false;
        // Most sessions are quiet observation; only about one in three
        // involves a brief walk. Never wander kilometers away from the owner.
        if (dragon.getRandom().nextInt(3) == 0) {
            Path shortRoute = chooseInterestPath();
            if (shortRoute != null && shortRoute.canReach()
                    && dragon.getNavigation().moveTo(shortRoute, 0.68D)) {
                exploring = true;
            }
        }
        if (!exploring) dragon.getNavigation().stop();
    }

    /** No unloaded chunks, block breaking, teleport, or straight-line drift. */
    private Path chooseInterestPath() {
        if (owner == null) return null;
        double radius = dragon.isSmallForm() ? 3.0D : 6.0D;
        for (int attempt = 0; attempt < 7; ++attempt) {
            double angle = dragon.getRandom().nextDouble() * Math.PI * 2.0D;
            double d = 1.7D + dragon.getRandom().nextDouble() * radius;
            double x = owner.getX() + Math.cos(angle) * d;
            double z = owner.getZ() + Math.sin(angle) * d;
            int y = owner.getBlockY() + dragon.getRandom().nextInt(5) - 2;
            BlockPos floor = BlockPos.containing(x, y, z);
            BlockPos below = floor.below();
            if (!dragon.level().hasChunkAt(floor)
                    || !dragon.level().hasChunkAt(below)
                    || !dragon.level().getBlockState(below)
                        .isFaceSturdy(dragon.level(), below, Direction.UP)
                    || !dragon.level().getFluidState(floor).isEmpty()) continue;
            Vec3 destination = new Vec3(floor.getX() + 0.5D, floor.getY(),
                    floor.getZ() + 0.5D);
            AABB space = dragon.getBoundingBox().move(
                    destination.x - dragon.getX(),
                    destination.y - dragon.getY(),
                    destination.z - dragon.getZ());
            if (!dragon.level().noCollision(dragon, space)) continue;
            Path path = dragon.getNavigation().createPath(floor, 0);
            if (path != null && path.canReach()) {
                interestPoint = destination;
                return path;
            }
        }
        return null;
    }

    @Override
    public void tick() {
        --remainingTicks;
        if (owner == null) return;
        // Preserve ground A* corners while exploring; no velocity forcing.
        // Occasionally check back on the owner and slowly survey the area.
        if (!exploring || dragon.getNavigation().isDone()
                || dragon.tickCount % 25 < 8) {
            dragon.getLookControl().setLookAt(owner, 8.0F, 8.0F);
        } else if (interestPoint != null) {
            dragon.getLookControl().setLookAt(interestPoint.x,
                    interestPoint.y + (dragon.isSmallForm() ? 0.4D : 1.1D),
                    interestPoint.z, 7.0F, 7.0F);
        }
        if (exploring && dragon.getNavigation().isDone()) exploring = false;
    }

    @Override
    public void stop() {
        dragon.getNavigation().stop();
        owner = null;
        interestPoint = null;
        exploring = false;
        remainingTicks = 0;
        cooldownTicks = MIN_COOLDOWN_TICKS + dragon.getRandom().nextInt(
                MAX_COOLDOWN_TICKS - MIN_COOLDOWN_TICKS + 1);
    }
}
