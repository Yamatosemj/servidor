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
    private static final int MAX_SEARCH_ATTEMPTS = 12;

    private enum Routine { OBSERVE, EXPLORE, INVESTIGATE, REST, SEEK_SHELTER, APPROACH_OWNER }

    private final AurenDragon dragon;
    private LivingEntity owner;
    private int remainingTicks;
    private long nextRoutineTick;
    private Routine routine = Routine.OBSERVE;
    private Vec3 interestPoint;
    private Vec3 routeStart;
    private double initialOwnerDistance;
    private int remainingPathBudget;
    private LivingEntity sampledOwner;
    private Vec3 lastOwnerSample;
    private int lastOwnerSampleTick = Integer.MIN_VALUE;
    private int ownerLastMovedTick = Integer.MIN_VALUE;
    private Vec3 lastRouteSample;
    private double traveledDuringRoute;
    private boolean routeReported;
    private Routine startedRoutine = Routine.OBSERVE;
    private Animal interestingAnimal;

    public AurenNaturalIdleGoal(AurenDragon dragon) {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * Require real owner stillness, including slow movement the velocity
     * snapshot can miss. Avoid an instant idle right after the owner stops.
     */
    private boolean ownerStationary(LivingEntity candidate) {
        if (sampledOwner != candidate || lastOwnerSample == null) {
            sampledOwner = candidate;
            lastOwnerSample = candidate.position();
            lastOwnerSampleTick = dragon.tickCount;
            ownerLastMovedTick = dragon.tickCount;
        } else if (dragon.tickCount - lastOwnerSampleTick >= 8) {
            double dx = candidate.getX() - lastOwnerSample.x;
            double dz = candidate.getZ() - lastOwnerSample.z;
            if (dx * dx + dz * dz > 0.16D)
                ownerLastMovedTick = dragon.tickCount;
            lastOwnerSample = candidate.position();
            lastOwnerSampleTick = dragon.tickCount;
        }
        if (candidate.getDeltaMovement().horizontalDistanceSqr() >= 0.012D)
            ownerLastMovedTick = dragon.tickCount;
        return dragon.tickCount - ownerLastMovedTick >= 12;
    }

    private boolean calm(LivingEntity candidate) {
        if (candidate == null || !candidate.isAlive()
                || candidate.level() != dragon.level() || candidate.isSpectator())
            return false;
        double maxDistance = dragon.isSmallForm() ? 4.5D : 15.0D;
        AurenWorldPerception.Snapshot observed = dragon.getAwareness().latest();
        boolean danger = observed != null
                && (observed.dangerNearOwner() || observed.ownerBurning()
                    || observed.lowOwnerHealth());
        return dragon.isTame() && dragon.isOwnedBy(candidate)
                && !dragon.isOrderedToSit() && !dragon.isOnShoulder()
                && !dragon.isVehicle() && !dragon.isPassenger()
                && !dragon.getAttackOrders().hasOrder()
                && !dragon.getAttackOrders().isRestoringForm()
                && dragon.getTarget() == null && !danger
                && dragon.getFlightMode() == AurenFlightController.GROUNDED
                && dragon.onGround() && !dragon.isInWater()
                && dragon.distanceToSqr(candidate) < maxDistance * maxDistance
                && ownerStationary(candidate);
    }

    @Override
    public boolean canUse() {
        // GoalSelector does NOT invoke canUse every server tick. Count
        // against game time rather than decrementing per canUse() call:
        // otherwise a 100-230 tick break becomes 3x longer or more.
        if (dragon.tickCount < nextRoutineTick) {
            dragon.getMind().idleUnready("pausa entre rotinas ("
                    + (nextRoutineTick - dragon.tickCount) + " ticks)");
            return false;
        }
        LivingEntity possible = dragon.getOwner();
        if (!calm(possible)) {
            String reason;
            if (possible == null || !possible.isAlive()
                    || possible.level() != dragon.level())
                reason = "dono ausente";
            else if (dragon.isOrderedToSit()) reason = "sentado por ordem";
            else if (dragon.isOnShoulder()) reason = "no ombro";
            else if (dragon.isVehicle()) reason = "montado";
            else if (dragon.getAttackOrders().isRestoringForm())
                reason = "recuperando forma adulta apos caverna";
            else if (dragon.getAttackOrders().hasOrder())
                reason = dragon.getAttackOrders().routineBlockReason();
            else if (dragon.getTarget() != null)
                reason = "combate ou alvo ativo";
            else if (dragon.getFlightMode() != AurenFlightController.GROUNDED
                    || !dragon.onGround()) reason = "voando ou fora do chao";
            else if (dragon.distanceToSqr(possible)
                    >= (dragon.isSmallForm() ? 20.25D : 225.0D))
                reason = "dono distante";
            else if (!ownerStationary(possible))
                reason = "dono em movimento ou ainda parando";
            else reason = "ambiente inseguro ou sem condicoes de descanso";
            dragon.getMind().idleUnready(reason);
            return false;
        }
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
        routeStart = dragon.position();
        lastRouteSample = routeStart;
        traveledDuringRoute = 0.0D;
        routeReported = false;
        startedRoutine = Routine.OBSERVE;
        initialOwnerDistance = dragon.distanceTo(owner);
        remainingPathBudget = 6;
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
                        routine = startedRoutine = Routine.SEEK_SHELTER;
                        remainingTicks = 110 + dragon.getRandom().nextInt(100);
                        dragon.getMind().accepted(rated.activity());
                        return;
                    }
                    dragon.getMind().idlePathFailed();
                }
                case REST -> {
                    if (exposedRain()) break; // resting in open rain looks wrong
                    routine = startedRoutine = Routine.REST;
                    remainingTicks = night()
                            ? 200 + dragon.getRandom().nextInt(280)
                            : 95 + dragon.getRandom().nextInt(120);
                    dragon.setNaturalRestPose(night() ? 2 : 1);
                    dragon.getMind().accepted(rated.activity());
                    return;
                }
                case INVESTIGATE -> {
                    if (nearby == null) break;
                    if (remainingPathBudget <= 0) break;
                    --remainingPathBudget;
                    Path route = dragon.getNavigation().createPath(
                            nearby.blockPosition(), 0);
                    if (route != null && route.canReach()
                            && dragon.getNavigation().moveTo(route, 0.63D)) {
                        interestingAnimal = nearby;
                        routine = startedRoutine = Routine.INVESTIGATE;
                        dragon.getMind().accepted(rated.activity());
                        return;
                    }
                    dragon.getMind().idlePathFailed();
                }
                case EXPLORE -> {
                    Path shortRoute = chooseInterestPath(false);
                    if (shortRoute != null && shortRoute.canReach()
                            && dragon.getNavigation().moveTo(shortRoute, 0.68D)) {
                        routine = startedRoutine = Routine.EXPLORE;
                        dragon.getMind().accepted(rated.activity());
                        return;
                    }
                    dragon.getMind().idlePathFailed();
                }
                case APPROACH_OWNER -> {
                    double minimum = dragon.isSmallForm() ? 2.6D : 4.6D;
                    if (dragon.distanceToSqr(owner) <= minimum * minimum)
                        break;
                    // Choose a safe spot alongside the player, never their
                    // feet. The follow goal is not overridden at long range.
                    Path approach = chooseInterestPath(false,
                            dragon.isSmallForm() ? 1.8D : 3.0D,
                            dragon.isSmallForm() ? 2.3D : 3.6D, true);
                    if (approach != null && approach.canReach()
                            && dragon.getNavigation().moveTo(approach, 0.82D)) {
                        routine = startedRoutine = Routine.APPROACH_OWNER;
                        remainingTicks = 90 + dragon.getRandom().nextInt(95);
                        dragon.getMind().accepted(rated.activity());
                        return;
                    }
                    dragon.getMind().idlePathFailed();
                }
                case OBSERVE -> {
                    routine = startedRoutine = Routine.OBSERVE;
                    dragon.getMind().accepted(rated.activity());
                    return;
                }
            }
        }
        // No unsafe or impossible action is ever forced.
        routine = startedRoutine = Routine.OBSERVE;
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

    /** A* only; local owner-centered waypoints, no teleport or flight. */
    private Path chooseInterestPath(boolean shelteredOnly) {
        return chooseInterestPath(shelteredOnly, 2.2D,
                dragon.isSmallForm() ? 3.8D : 6.8D, false);
    }

    /**
     * Sample real ground at the owner's elevation first, then nearby
     * offsets. The previous single random Y sample wasted most attempts,
     * often falling through to OBSERVE without any visible activity.
     */
    private Path chooseInterestPath(boolean shelteredOnly, double minDist,
                                    double maxDist, boolean approaching) {
        if (owner == null) return null;
        if (remainingPathBudget <= 0) return null;
        for (int attempt = 0; attempt < MAX_SEARCH_ATTEMPTS; ++attempt) {
            double angle = dragon.getRandom().nextDouble() * Math.PI * 2.0D;
            double d = minDist + dragon.getRandom().nextDouble()
                    * (maxDist - minDist);
            double x = owner.getX() + Math.cos(angle) * d;
            double z = owner.getZ() + Math.sin(angle) * d;
            for (int dy : new int[] {0, 1, -1, 2, -2}) {
                BlockPos floor = BlockPos.containing(
                        x, owner.getBlockY() + dy, z);
                BlockPos below = floor.below();
                if (!dragon.level().hasChunkAt(floor)
                        || !dragon.level().hasChunkAt(below)
                        || !dragon.level().getBlockState(below).isFaceSturdy(
                                dragon.level(), below, Direction.UP)
                        || !dragon.level().getFluidState(floor).isEmpty()
                        || (shelteredOnly
                            && dragon.level().canSeeSky(floor.above())))
                    continue;

                Vec3 destination = new Vec3(floor.getX() + 0.5D,
                        floor.getY(), floor.getZ() + 0.5D);
                AABB space = dragon.getBoundingBox().move(
                        destination.x - dragon.getX(),
                        destination.y - dragon.getY(),
                        destination.z - dragon.getZ());
                if (!dragon.level().noCollision(dragon, space)) continue;
                // Voluntary approach must make progress, not wander away.
                if (approaching && (dragon.position().distanceToSqr(destination) < 1.0D
                        || destination.distanceToSqr(owner.position())
                            >= dragon.distanceToSqr(owner) - 2.0D)) continue;
                // Do not run an unbounded number of A* searches in a tick.
                if (remainingPathBudget <= 0) return null;
                --remainingPathBudget;
                Path path = dragon.getNavigation().createPath(floor, 0);
                if (path != null && path.canReach()) {
                    interestPoint = destination;
                    return path;
                }
            }
        }
        return null;
    }

    @Override
    public void tick() {
        --remainingTicks;
        if (owner == null) return;
        if (!routeReported && movingRoutine(routine) && lastRouteSample != null) {
            double step = dragon.position().distanceTo(lastRouteSample);
            if (step >= 0.01D && step < 3.0D) traveledDuringRoute += step;
            lastRouteSample = dragon.position();
        }
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
                reportRouteCompletion(true);
                routine = Routine.OBSERVE;
            } else {
                dragon.getLookControl().setLookAt(interestingAnimal, 7.0F, 7.0F);
                if (dragon.distanceToSqr(interestingAnimal) < 3.8D * 3.8D
                        || dragon.getNavigation().isDone()) {
                    dragon.getNavigation().stop();
                    reportRouteCompletion(false);
                    routine = Routine.OBSERVE;
                }
            }
        } else if (routine == Routine.EXPLORE
                || routine == Routine.SEEK_SHELTER
                || routine == Routine.APPROACH_OWNER) {
            if (dragon.getNavigation().isDone()) {
                reportRouteCompletion(false);
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

    private static boolean movingRoutine(Routine routine) {
        return routine == Routine.EXPLORE || routine == Routine.APPROACH_OWNER
                || routine == Routine.INVESTIGATE || routine == Routine.SEEK_SHELTER;
    }

    /** A* acceptance is not movement. Track actual travel and interruption. */
    private void reportRouteCompletion(boolean interrupted) {
        if (routeReported || routeStart == null || owner == null
                || !movingRoutine(routine)) return;
        double progress = routine == Routine.APPROACH_OWNER
                ? initialOwnerDistance - dragon.distanceTo(owner)
                : dragon.position().distanceTo(routeStart);
        dragon.getMind().idleRouteEnded(
                routine.name().toLowerCase(java.util.Locale.ROOT),
                progress, traveledDuringRoute, interrupted);
        routeReported = true;
    }

    @Override
    public void stop() {
        reportRouteCompletion(true);
        dragon.setNaturalRestPose(0);
        dragon.getNavigation().stop();
        dragon.getMind().idleFinished(
                (remainingTicks <= 0 ? "tempo encerrado: " : "interrompida: ")
                + startedRoutine.name().toLowerCase(java.util.Locale.ROOT));
        owner = null;
        interestingAnimal = null;
        interestPoint = null;
        routeStart = null;
        lastRouteSample = null;
        routeReported = false;
        remainingTicks = 0;
        routine = Routine.OBSERVE;
        nextRoutineTick = dragon.tickCount + MIN_COOLDOWN_TICKS
                + dragon.getRandom().nextInt(
                    MAX_COOLDOWN_TICKS - MIN_COOLDOWN_TICKS + 1);
    }
}
