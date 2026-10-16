package br.com.auren.minecraft.entity;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Contextual, peaceful daily routine. A companion is neither a random-walk
 * generator nor a constantly-sleeping statue: it rests, looks around, visits
 * safe nearby spots, inspects animals and seeks cover during rain.
 *
 * All locomotion goes through vanilla/CE A* ground navigation. No fighting,
 * target selection, custom physics, block mutations, teleport or AI service.
 */
public final class AurenNaturalIdleGoal extends Goal {
    // Keep the already tested bounded idle/cooldown ranges as the baseline.
    private static final int MIN_REST_TICKS = 65;
    private static final int MAX_REST_TICKS = 135;
    private static final int MIN_COOLDOWN_TICKS = 100;
    private static final int MAX_COOLDOWN_TICKS = 230;
    private static final int MAX_SEARCH_ATTEMPTS = 9;

    private enum Routine { OBSERVE, EXPLORE, INVESTIGATE, REST, SEEK_SHELTER }

    private final AurenDragon dragon;
    private LivingEntity owner;
    private int remainingTicks;
    private int cooldownTicks;
    private Routine routine = Routine.OBSERVE;
    private Vec3 interestPoint;
    private Animal interestingAnimal;

    public AurenNaturalIdleGoal(AurenDragon dragon) {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean calm(LivingEntity candidate) {
        if (candidate == null || !candidate.isAlive()
                || candidate.level() != dragon.level() || candidate.isSpectator())
            return false;
        double maxDistance = dragon.isSmallForm() ? 4.0D : 10.0D;
        AurenWorldPerception.Snapshot observed = dragon.getAwareness().latest();
        boolean danger = observed != null
                && (observed.dangerNearOwner() || observed.ownerBurning()
                    || observed.lowOwnerHealth());
        return dragon.isTame() && dragon.isOwnedBy(candidate)
                && !dragon.isOrderedToSit() && !dragon.isOnShoulder()
                && !dragon.isVehicle() && !dragon.isPassenger()
                && !dragon.getAttackOrders().hasOrder()
                && dragon.getTarget() == null && !danger
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
        owner = possible;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return remainingTicks > 0 && calm(owner);
    }

    private boolean night() {
        long clock = dragon.level().getDayTime() % 24000L;
        return clock >= 13000L && clock < 23000L;
    }

    private boolean exposedRain() {
        return dragon.level().isRainingAt(dragon.blockPosition().above());
    }

    @Override
    public void start() {
        dragon.setNaturalRestPose(0);
        interestingAnimal = null;
        interestPoint = null;
        dragon.getNavigation().stop();
        remainingTicks = MIN_REST_TICKS + dragon.getRandom().nextInt(
                MAX_REST_TICKS - MIN_REST_TICKS + 1);

        // The local brain ranks OPTIONS, not motor instructions. The
        // existing safe native paths are still required before taking
        // an action. Failed shelter/animal paths fall back to the next
        // choice rather than freezing the Auren or forcing flight.
        Animal nearby = chooseNearbyAnimal();
        for (AurenLocalDecisionPolicy.Rated rated : dragon.getMind().rankIdle(
                night(), exposedRain(), nearby != null)) {
            switch (rated.activity()) {
                case SEEK_SHELTER -> {
                    Path cover = chooseInterestPath(true);
                    if (cover != null && cover.canReach()
                            && dragon.getNavigation().moveTo(cover, 0.72D)) {
                        routine = Routine.SEEK_SHELTER;
                        remainingTicks = 110 + dragon.getRandom().nextInt(100);
                        dragon.getMind().accepted(rated.activity());
                        return;
                    }
                }
                case REST -> {
                    if (exposedRain()) break; // resting in open rain looks wrong
                    routine = Routine.REST;
                    remainingTicks = night()
                            ? 200 + dragon.getRandom().nextInt(280)
                            : 95 + dragon.getRandom().nextInt(120);
                    dragon.setNaturalRestPose(night() ? 2 : 1);
                    dragon.getMind().accepted(rated.activity());
                    return;
                }
                case INVESTIGATE -> {
                    if (nearby == null) break;
                    Path route = dragon.getNavigation().createPath(
                            nearby.blockPosition(), 0);
                    if (route != null && route.canReach()
                            && dragon.getNavigation().moveTo(route, 0.63D)) {
                        interestingAnimal = nearby;
                        routine = Routine.INVESTIGATE;
                        dragon.getMind().accepted(rated.activity());
                        return;
                    }
                }
                case EXPLORE -> {
                    Path shortRoute = chooseInterestPath(false);
                    if (shortRoute != null && shortRoute.canReach()
                            && dragon.getNavigation().moveTo(shortRoute, 0.68D)) {
                        routine = Routine.EXPLORE;
                        dragon.getMind().accepted(rated.activity());
                        return;
                    }
                }
                case OBSERVE -> {
                    routine = Routine.OBSERVE;
                    dragon.getMind().accepted(rated.activity());
                    return;
                }
            }
        }
        // No unsafe or impossible action is ever forced.
        routine = Routine.OBSERVE;
        dragon.getMind().accepted(AurenLocalDecisionPolicy.Activity.OBSERVE);
    }

    private Animal chooseNearbyAnimal() {
        if (owner == null) return null;
        List<Animal> nearby = dragon.level().getEntitiesOfClass(Animal.class,
                dragon.getBoundingBox().inflate(7.0D, 3.0D, 7.0D),
                animal -> animal.isAlive() && animal != dragon
                    && !dragon.isAlliedTo(animal)
                    && animal.distanceToSqr(owner) < 10.0D * 10.0D
                    && animal.distanceToSqr(dragon) > 2.0D * 2.0D);
        return nearby.stream().min(
                Comparator.comparingDouble(dragon::distanceToSqr)).orElse(null);
    }

    /** A* only, close to the owner. Optional roof constraint for rainy days. */
    private Path chooseInterestPath(boolean shelteredOnly) {
        if (owner == null) return null;
        double radius = dragon.isSmallForm() ? 2.8D : 5.5D;
        for (int attempt = 0; attempt < MAX_SEARCH_ATTEMPTS; ++attempt) {
            double angle = dragon.getRandom().nextDouble() * Math.PI * 2.0D;
            double d = 1.7D + dragon.getRandom().nextDouble() * radius;
            double x = owner.getX() + Math.cos(angle) * d;
            double z = owner.getZ() + Math.sin(angle) * d;
            int y = owner.getBlockY() + dragon.getRandom().nextInt(5) - 2;
            BlockPos floor = BlockPos.containing(x, y, z);
            BlockPos below = floor.below();
            if (!dragon.level().hasChunkAt(floor)
                    || !dragon.level().hasChunkAt(below)
                    || !dragon.level().getBlockState(below).isFaceSturdy(
                            dragon.level(), below, Direction.UP)
                    || !dragon.level().getFluidState(floor).isEmpty()
                    || (shelteredOnly
                        && dragon.level().canSeeSky(floor.above()))) continue;
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
        if (routine == Routine.REST) {
            // Quiet CE sitting/sleeping pose; interruptible every tick.
            // Sleeping eyes do not constantly track the owner.
            if (dragon.getNaturalRestPose() == 1 && dragon.tickCount % 45 < 7)
                dragon.getLookControl().setLookAt(owner, 5.0F, 5.0F);
            return;
        }
        if (routine == Routine.INVESTIGATE) {
            if (interestingAnimal == null || !interestingAnimal.isAlive()
                    || interestingAnimal.distanceToSqr(owner) > 100.0D) {
                dragon.getNavigation().stop();
                routine = Routine.OBSERVE;
            } else {
                dragon.getLookControl().setLookAt(interestingAnimal, 7.0F, 7.0F);
                if (dragon.distanceToSqr(interestingAnimal) < 3.8D * 3.8D
                        || dragon.getNavigation().isDone()) {
                    dragon.getNavigation().stop();
                    routine = Routine.OBSERVE;
                }
            }
        } else if (routine == Routine.EXPLORE || routine == Routine.SEEK_SHELTER) {
            if (dragon.getNavigation().isDone()) {
                routine = Routine.OBSERVE;
                dragon.getNavigation().stop();
            } else if (dragon.tickCount % 40 < 8) {
                dragon.getLookControl().setLookAt(owner, 7.0F, 7.0F);
            } else if (interestPoint != null) {
                dragon.getLookControl().setLookAt(interestPoint.x,
                        interestPoint.y + (dragon.isSmallForm() ? 0.4D : 1.1D),
                        interestPoint.z, 7.0F, 7.0F);
            }
        } else {
            // Keep observer head motion irregular, not a repetitive 25-tick
            // robot scan. Vanilla look control never turns the whole body.
            if (dragon.getRandom().nextInt(50) == 0)
                dragon.getLookControl().setLookAt(owner, 7.0F, 7.0F);
            else if (dragon.getRandom().nextInt(75) == 0) {
                double angle = dragon.getRandom().nextDouble() * Math.PI * 2.0D;
                dragon.getLookControl().setLookAt(
                        dragon.getX() + Math.cos(angle) * 6.0D,
                        dragon.getEyeY(),
                        dragon.getZ() + Math.sin(angle) * 6.0D,
                        6.0F, 6.0F);
            }
        }
    }

    @Override
    public void stop() {
        dragon.setNaturalRestPose(0);
        dragon.getNavigation().stop();
        owner = null;
        interestingAnimal = null;
        interestPoint = null;
        remainingTicks = 0;
        routine = Routine.OBSERVE;
        cooldownTicks = MIN_COOLDOWN_TICKS + dragon.getRandom()
                .nextInt(MAX_COOLDOWN_TICKS - MIN_COOLDOWN_TICKS + 1);
    }
}
