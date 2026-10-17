/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Inspired by IceAndFire CE DragonBaseEntity digWhenStuck/breakBlocks:
 * https://github.com/IAFEnvoy/IceAndFire-CE
 * The escalation rules below are new Auren behavior, not a complete CE port.
 */
package br.com.auren.minecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Relentless marked-prey pursuit: try normal CE aerial tactics first, keep
 * breathing against cover, enter small passages when a ground path really
 * exists, and use tiny fire charges ONLY after prolonged, verified blockage.
 * Never force-loads chunks; never acts without a marked, non-allied target.
 */
final class AurenRelentlessHunt {
    private static final int COVER_BREATH_DELAY = 65;
    private static final int CAVE_SEARCH_DELAY = 110;
    private static final int SIEGE_DELAY = 260;
    private static final int SIEGE_COOLDOWN = 95;
    private static final int MAX_SIEGE_CHARGES = 5;
    private static final double CHARGE_REACH = 27.0D;
    private static final double BLAST_RADIUS = 1.35D;

    private final AurenDragon dragon;
    private int blockedTicks;
    private int nextNavigationCheck;
    private int nextSiegeTick;
    private int siegeCharges;
    private int failedGroundRoutes;
    private int meleeCooldown;
    private boolean noAerialRoute;
    private boolean routeChecked;
    private boolean attemptedCave;
    private boolean caveRouteExhausted;
    private boolean compact;
    private int compactLastProgressTick;
    private double compactClosestDistanceSq;
    private boolean pendingRestore;
    private BlockPos lastCaveAttempt;
    private BlockPos caveEntrance;
    private BlockPos compactRouteGoal;
    private Vec3 lastCompactMovement;
    private int compactLastMotionTick;
    private int compactStartTick;
    private int nextEntranceSearchTick;
    private int nextGroundMouthPathTick;
    private Vec3 caveLanding;
    private Vec3 caveMouthVantage;
    private int mouthVantageRefreshTick;
    private int inaccessibleMouthTicks;
    private Vec3 lastSafeAdultPosition;
    private int landingRefreshTick;
    private Vec3 fireAim;

    AurenRelentlessHunt(AurenDragon dragon) {
        this.dragon = dragon;
    }

    boolean isCompact() { return compact; }
    boolean isRestoringForm() { return pendingRestore; }
    Vec3 getFireAim() { return fireAim; }

    void inspectAerialRoute(boolean canSeeFromSomewhere) {
        routeChecked = true;
        noAerialRoute = !canSeeFromSomewhere;
    }

    void save(CompoundTag tag) {
        tag.putBoolean("AurenHuntTemporarySmall", compact || pendingRestore);
    }

    void load(CompoundTag tag) {
        // Reload must never leave an orphaned tiny dragon waiting forever for
        // a target. Reopen pathing to an adult-sized exit instead.
        compact = false;
        pendingRestore = tag.getBoolean("AurenHuntTemporarySmall")
                && dragon.isSmallForm();
        // Intentional reset: revalidate the environment after world loading,
        // including ownership and path availability, before any siege shot.
        blockedTicks = 0;
        nextSiegeTick = 0;
        siegeCharges = 0;
        routeChecked = false;
        noAerialRoute = false;
        attemptedCave = false;
        caveRouteExhausted = false;
        caveLanding = null;
        caveMouthVantage = null;
        mouthVantageRefreshTick = 0;
        inaccessibleMouthTicks = 0;
        caveEntrance = null;
        compactRouteGoal = null;
        lastCompactMovement = null;
        lastSafeAdultPosition = null;
        fireAim = null;
    }

    void reset() {
        if (compact) pendingRestore = true;
        compact = false;
        blockedTicks = 0;
        nextNavigationCheck = 0;
        nextSiegeTick = 0;
        siegeCharges = 0;
        failedGroundRoutes = 0;
        meleeCooldown = 0;
        noAerialRoute = false;
        routeChecked = false;
        attemptedCave = false;
        caveRouteExhausted = false;
        lastCaveAttempt = null;
        caveEntrance = null;
        caveMouthVantage = null;
        mouthVantageRefreshTick = 0;
        inaccessibleMouthTicks = 0;
        compactRouteGoal = null;
        lastCompactMovement = null;
        caveLanding = null;
        landingRefreshTick = 0;
        nextEntranceSearchTick = 0;
        nextGroundMouthPathTick = 0;
        fireAim = null;
        tryRestore();
    }

    void tickPassive() {
        if (pendingRestore && !compact) {
            tryRestore();
            if (pendingRestore) walkToSafeExit();
        }
    }

    private void tryRestore() {
        if (!pendingRestore) return;
        if (!dragon.isSmallForm()) {
            pendingRestore = false;
            return;
        }
        if (!(dragon.level() instanceof ServerLevel level)
                || dragon.getFlightMode() != AurenFlightController.GROUNDED
                || dragon.isOnShoulder() || dragon.isVehicle()) return;
        BlockPos pos = dragon.blockPosition();
        // Adult size is 2.535 x 3.9. Only expand in a fully loaded space.
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above(4))) return;
        double half = 0.78D * 3.25D / 2.0D;
        AABB adult = new AABB(dragon.getX() - half, dragon.getY(),
                dragon.getZ() - half, dragon.getX() + half,
                dragon.getY() + 1.20D * 3.25D, dragon.getZ() + half);
        if (!level.noCollision(dragon, adult)) return;
        dragon.setSmallForm(false);
        pendingRestore = false;
        lastSafeAdultPosition = null;
        dragon.getNavigation().stop();
    }

    /**
     * Never freeze in a tunnel merely because adult hitbox cannot expand.
     * Reuse a known landing location or path to a nearby open, solid floor.
     */
    private void walkToSafeExit() {
        if (!(dragon.level() instanceof ServerLevel level)
                || !dragon.isSmallForm()
                || dragon.getFlightMode() != AurenFlightController.GROUNDED)
            return;
        if (dragon.tickCount % 20 != 0 && !dragon.getNavigation().isDone()) return;
        if (lastSafeAdultPosition != null && lastSafeAdultPosition.distanceToSqr(
                dragon.position()) > 2.25D) {
            Path back = dragon.getNavigation().createPath(
                    BlockPos.containing(lastSafeAdultPosition), 0);
            if (back != null && back.canReach()) {
                dragon.getNavigation().moveTo(back, 1.10D);
                return;
            }
        }
        for (int dist : new int[] {3, 6, 9, 12}) {
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI / 6.0D;
                BlockPos candidate = BlockPos.containing(
                        dragon.getX() + Math.cos(angle) * dist, dragon.getY(),
                        dragon.getZ() + Math.sin(angle) * dist);
                if (!safeAdultGround(level, candidate)) continue;
                Path path = dragon.getNavigation().createPath(candidate, 0);
                if (path != null && path.canReach()) {
                    dragon.getNavigation().moveTo(path, 1.10D);
                    return;
                }
            }
        }
    }

    /** Ground that supports both an adult dragon and a safe landing. */
    private boolean safeAdultGround(ServerLevel level, BlockPos floor) {
        if (!level.hasChunkAt(floor) || !level.hasChunkAt(floor.above(4))
                || !level.hasChunkAt(floor.below())
                || level.getBlockState(floor.below()).isAir()
                || !level.getBlockState(floor).isAir()) return false;
        double half = 0.78D * 3.25D / 2.0D;
        AABB adult = new AABB(floor.getX() + 0.5D - half, floor.getY(),
                floor.getZ() + 0.5D - half, floor.getX() + 0.5D + half,
                floor.getY() + 1.20D * 3.25D, floor.getZ() + 0.5D + half);
        return level.noCollision(dragon, adult);
    }

    /**
     * Sample the actual cave-mouth altitude, not the mountain TOP.
     * Never choose a landing spot far above the hidden mob.
     */
    private Vec3 findCaveLanding(ServerLevel level, LivingEntity prey) {
        if (caveEntrance == null) return null;
        Vec3 best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        // Only approach the mouth that the interior BFS found to connect
        // to the prey. Sampling around the prey picks the WRONG mountain face.
        for (int dist : new int[] {3, 5, 8}) {
            for (int i = 0; i < 16; i++) {
                double angle = i * Math.PI / 8.0D;
                int x = (int) Math.floor(caveEntrance.getX()
                        + Math.cos(angle) * dist);
                int z = (int) Math.floor(caveEntrance.getZ()
                        + Math.sin(angle) * dist);
                for (int dy : new int[] {0, 1, -1, 2, -2}) {
                    BlockPos ground = new BlockPos(x, caveEntrance.getY() + dy, z);
                    if (!safeAdultGround(level, ground)
                            || !level.canSeeSky(ground.above(4))) continue;
                    Vec3 landing = new Vec3(x + 0.5D, ground.getY(), z + 0.5D);
                    Vec3 approach = landing.add(0.0D, 5.0D, 0.0D);
                    if (!level.hasChunkAt(BlockPos.containing(approach))
                            || !level.noCollision(dragon, dragon.getBoundingBox().move(
                            approach.x - dragon.getX(),
                            approach.y - dragon.getY(),
                            approach.z - dragon.getZ()))) continue;
                    double cost = dragon.position().distanceToSqr(approach) * 0.35D
                            + caveEntrance.distSqr(ground) * 1.8D;
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = approach;
                    }
                }
            }
        }
        return best;
    }

    /**
     * A narrow cliff entrance often has NO adult-size landing shelf at the
     * cave's elevation. In that case, look for a flight vantage facing the
     * cave opening instead of burning straight down through the mountain.
     * This is a cautious fallback, not a complete 3D flight pathfinder.
     */
    private Vec3 findMouthVantage(ServerLevel level) {
        if (caveEntrance == null) return null;
        Vec3 mouth = Vec3.atCenterOf(caveEntrance);
        Vec3 best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int distance : new int[] {5, 9, 13}) {
            for (int i = 0; i < 16; i++) {
                double radians = i * Math.PI / 8.0D;
                for (int up : new int[] {1, 4, 7}) {
                    Vec3 candidate = mouth.add(Math.cos(radians) * distance,
                            up, Math.sin(radians) * distance);
                    BlockPos targetBlock = BlockPos.containing(candidate);
                    if (!level.hasChunkAt(targetBlock)
                            || !level.noCollision(dragon,
                                dragon.getBoundingBox().move(candidate.subtract(dragon.position()))))
                        continue;
                    BlockHitResult sight = level.clip(new ClipContext(
                            candidate, mouth, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, dragon));
                    if (sight.getType() == HitResult.Type.BLOCK
                            && sight.getLocation().distanceToSqr(mouth) > 2.25D)
                        continue;
                    double score = candidate.distanceToSqr(dragon.position())
                            + Math.abs(candidate.y - mouth.y) * 3.0D;
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Insist on an accessible cave MOUTH before treating the target as
     * trapped under an impenetrable rock roof. Shoot into the opening when
     * the adult cannot land rather than aiming straight down at the target.
     */
    private boolean pressureCaveMouth(ServerLevel level, ServerPlayer owner) {
        if (caveEntrance == null || caveRouteExhausted
                || dragon.getFlightMode() != AurenFlightController.FLYING)
            return false;
        if (caveMouthVantage == null || dragon.tickCount >= mouthVantageRefreshTick) {
            caveMouthVantage = findMouthVantage(level);
            mouthVantageRefreshTick = dragon.tickCount + 60;
        }
        Vec3 mouth = Vec3.atCenterOf(caveEntrance);
        if (caveMouthVantage != null) {
            inaccessibleMouthTicks = 0;
            if (dragon.position().distanceToSqr(caveMouthVantage) > 16.0D) {
                if (dragon.getFlightController().patrolAttack(
                        owner, caveMouthVantage, false)) return true;
            } else {
                fireAim = mouth;
                dragon.getFlightController().holdFacing(owner, mouth);
                return true;
            }
        }
        // Even without a stable vantage, avoid blindly switching back
        // to rooftop destruction if the mouth is already visible.
        BlockHitResult sight = level.clip(new ClipContext(
                dragon.getFireBreathHeadPosition(), mouth,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon));
        if (sight.getType() == HitResult.Type.MISS
                && dragon.getFireBreathHeadPosition().distanceToSqr(mouth)
                    < CHARGE_REACH * CHARGE_REACH) {
            fireAim = mouth;
            dragon.getFlightController().holdFacing(owner, mouth);
            return true;
        }
        // After a bounded genuine search, allow the rock-only LAST RESORT.
        if (++inaccessibleMouthTicks < 200) return true;
        caveRouteExhausted = true;
        return false;
    }

    /**
     * Native A* navigation is still the authority for actual movement.
     * Only scout an entrance TOPOLOGICALLY CONNECTED to the prey's cave.
     * Do not choose an apparently close point separated by solid rock.
     */
    private Path findCaveScoutingPath(ServerLevel level, LivingEntity enemy) {
        if (caveEntrance == null || !level.hasChunkAt(caveEntrance)) return null;
        Path route = dragon.getNavigation().createPath(caveEntrance, 0);
        if (route != null && route.canReach()) {
            compactRouteGoal = caveEntrance;
            return route;
        }
        // A tiny entrance can be unpathable as an exact endpoint. Look for
        // a nearby OUTSIDE staging tile, never another point near the prey.
        for (int dist : new int[] {1, 2, 3}) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0D;
                BlockPos pos = BlockPos.containing(
                        caveEntrance.getX() + Math.cos(angle) * dist,
                        caveEntrance.getY(), caveEntrance.getZ() + Math.sin(angle) * dist);
                if (!level.hasChunkAt(pos) || !level.canSeeSky(pos)
                        || !level.getBlockState(pos).isAir()) continue;
                Path approach = dragon.getNavigation().createPath(pos, 0);
                if (approach != null && approach.canReach()) {
                    compactRouteGoal = pos;
                    return approach;
                }
            }
        }
        return null;
    }

    /** The target must actually have a roof, not just hide behind a tree. */
    private boolean shelteredUnderground(ServerLevel level, LivingEntity target) {
        BlockPos column = target.blockPosition();
        if (!level.hasChunkAt(column)) return false;
        int roof = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).getY();
        return roof >= target.getEyeY() + 2.0D;
    }

    /**
     * Unlike the previous hunter, do not become small while still far outside
     * the cave. Walk the adult to a reachable staging tile near the real
     * cave mouth, then switch to compact A* pathfinding.
     */
    private Path groundCaveApproach(ServerLevel level) {
        if (caveEntrance == null) return null;
        Path chosen = null;
        double bestCost = Double.POSITIVE_INFINITY;
        int pathsChecked = 0;
        for (int dist : new int[] {3, 5, 8, 11}) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0D;
                int x = (int) Math.floor(caveEntrance.getX() + Math.cos(angle) * dist);
                int z = (int) Math.floor(caveEntrance.getZ() + Math.sin(angle) * dist);
                for (int dy : new int[] {0, 1, -1}) {
                    BlockPos test = new BlockPos(x, caveEntrance.getY() + dy, z);
                    if (!safeAdultGround(level, test)) continue;
                    // A* on Android is expensive; never attempt unlimited
                    // full routes within one server tick.
                    if (++pathsChecked > 28) return chosen;
                    Path path = dragon.getNavigation().createPath(test, 0);
                    if (path == null || !path.canReach()) continue;
                    double score = dragon.blockPosition().distSqr(test) * 0.25D
                            + test.distSqr(caveEntrance);
                    if (score < bestCost) {
                        bestCost = score;
                        chosen = path;
                    }
                }
            }
        }
        return chosen;
    }

    private boolean startCavePursuit(ServerLevel level, ServerPlayer owner,
                                     LivingEntity enemy) {
        if (compact || attemptedCave || dragon.isSmallForm()
                || dragon.isVehicle() || dragon.isOnShoulder()
                || dragon.distanceToSqr(enemy) > 40.0D * 40.0D) return false;
        if (!shelteredUnderground(level, enemy)) return false;

        if (caveEntrance == null || dragon.tickCount >= nextEntranceSearchTick) {
            caveEntrance = AurenCaveRoutePlanner.findEntrance(level, enemy);
            nextEntranceSearchTick = dragon.tickCount + 100;
            if (caveEntrance == null) {
                caveLanding = null;
                caveMouthVantage = null;
            }
        }
        // No connected cave mouth: small form has no useful route.
        if (caveEntrance == null) return false;

        int mode = dragon.getFlightMode();
        if (mode == AurenFlightController.FLYING
                || mode == AurenFlightController.TAKING_OFF) {
            if (caveLanding == null || !level.hasChunkAt(
                    BlockPos.containing(caveLanding))
                    || dragon.tickCount >= landingRefreshTick) {
                caveLanding = findCaveLanding(level, enemy);
                landingRefreshTick = dragon.tickCount + 60;
            }
            // Never shrink while flying over the mountain! Look for a safe
            // open landing at the height of the cave mouth.
            if (caveLanding == null) return pressureCaveMouth(level, owner);
            if (dragon.position().distanceToSqr(caveLanding) > 16.0D) {
                if (dragon.getFlightController().patrolAttack(
                        owner, caveLanding, false)) return true;
                return pressureCaveMouth(level, owner);
            }
            dragon.getFlightController().land();
            return true;
        }
        if (mode == AurenFlightController.LANDING || !dragon.onGround())
            return true;

        if (dragon.position().distanceToSqr(Vec3.atCenterOf(caveEntrance))
                > 11.0D * 11.0D) {
            // Do not overwrite a valid approach route just because the
            // navigator temporarily considers the next corner completed.
            if (dragon.tickCount < nextGroundMouthPathTick) return true;
            if (dragon.tickCount >= nextGroundMouthPathTick) {
                nextGroundMouthPathTick = dragon.tickCount + 55;
                Path approach = groundCaveApproach(level);
                if (approach != null && approach.canReach()) {
                    dragon.getNavigation().moveTo(approach, 1.10D);
                    return true;
                }
            }
            // Ground approach genuinely impossible: only then retry aerial
            // cave-mouth positioning, rather than transforming prematurely.
            return false;
        }

        attemptedCave = true;
        lastCaveAttempt = enemy.blockPosition();
        lastSafeAdultPosition = dragon.position();
        dragon.setSmallForm(true);
        Path route = dragon.getNavigation().createPath(enemy, 0);
        if (route != null && route.canReach()) {
            compactRouteGoal = enemy.blockPosition();
        } else {
            route = findCaveScoutingPath(level, enemy);
        }
        if (route == null || !route.canReach()) {
            // No route: stay mobile. Undo temporary shrinking where safe
            // instead of entering a permanent pendingRestore deadlock.
            caveRouteExhausted = true;
            pendingRestore = true;
            tryRestore();
            if (pendingRestore) walkToSafeExit();
            return true;
        }
        compact = true;
        caveRouteExhausted = false;
        compactLastProgressTick = dragon.tickCount;
        compactClosestDistanceSq = dragon.distanceToSqr(enemy);
        lastCompactMovement = dragon.position();
        compactLastMotionTick = dragon.tickCount;
        compactStartTick = dragon.tickCount;
        pendingRestore = false;
        failedGroundRoutes = 0;
        nextNavigationCheck = 0;
        dragon.getNavigation().moveTo(route, 1.17D);
        return true;
    }

    private void pursueCave(ServerLevel level, LivingEntity enemy) {
        fireAim = null;
        if (!dragon.isSmallForm() || dragon.isOnShoulder()) {
            compact = false;
            return;
        }
        if (dragon.distanceToSqr(enemy) > 30.0D * 30.0D) {
            compact = false;
            pendingRestore = true;
            dragon.getNavigation().stop();
            return;
        }
        double currentDistance = dragon.distanceToSqr(enemy);
        if (currentDistance + 2.25D < compactClosestDistanceSq) {
            compactClosestDistanceSq = currentDistance;
            compactLastProgressTick = dragon.tickCount;
        }
        // A legitimate corridor can initially move AWAY from the prey.
        // Detect absence of physical motion, not rising straight-line
        // distance, before declaring this cave impossible.
        if (lastCompactMovement == null
                || dragon.position().distanceToSqr(lastCompactMovement) > 1.0D) {
            lastCompactMovement = dragon.position();
            compactLastMotionTick = dragon.tickCount;
        }
        if (dragon.tickCount - compactLastMotionTick > 100
                || (dragon.tickCount - compactStartTick > 1200
                && currentDistance > 16.0D)) {
            caveRouteExhausted = true;
            compact = false;
            pendingRestore = true;
            dragon.getNavigation().stop();
            walkToSafeExit();
            return;
        }
        if (meleeCooldown > 0) --meleeCooldown;
        if (meleeCooldown == 0 && dragon.getBoundingBox().inflate(0.65D)
                .intersects(enemy.getBoundingBox()) && dragon.hasLineOfSight(enemy)) {
            dragon.doHurtTarget(enemy);
            meleeCooldown = 15;
        }
        if (++nextNavigationCheck >= 40) {
            nextNavigationCheck = 0;
            // Let A* follow its corner-by-corner nodes. Repath only when
            // route ends, the prey moved, or the navigator stopped making
            // physical progress; never overwrite the route every 15 ticks.
            boolean goalMoved = compactRouteGoal == null
                    || (caveEntrance != null
                    && !compactRouteGoal.equals(caveEntrance)
                    && compactRouteGoal.distSqr(enemy.blockPosition()) > 9.0D);
            boolean stalled = dragon.tickCount - compactLastMotionTick > 50;
            if (!dragon.getNavigation().isDone() && !goalMoved && !stalled)
                return;
            Path path = dragon.getNavigation().createPath(enemy, 0);
            if (path != null && path.canReach()) {
                compactRouteGoal = enemy.blockPosition();
            } else {
                path = findCaveScoutingPath(level, enemy);
            }
            if (path != null && path.canReach()) {
                dragon.getNavigation().moveTo(path, 1.17D);
                failedGroundRoutes = 0;
            } else if (++failedGroundRoutes >= 4) {
                caveRouteExhausted = true;
                compact = false;
                pendingRestore = true;
                dragon.getNavigation().stop();
                walkToSafeExit();
            }
        }
    }

    /**
     * @return true if the hunter owns movement/fire this tick; false to allow
     * the existing TACKLE / HOVER_BLAST / SCORCH_STREAM to operate unchanged.
     */
    boolean tick(ServerLevel level, ServerPlayer owner, LivingEntity enemy,
                 boolean directSight) {
        fireAim = null;
        if (compact) {
            pursueCave(level, enemy);
            return true;
        }
        tryRestore();
        if (pendingRestore) {
            walkToSafeExit();
            return true;
        }
        if (directSight) {
            blockedTicks = 0;
            routeChecked = false;
            noAerialRoute = false;
            attemptedCave = false;
            caveRouteExhausted = false;
            caveLanding = null;
            caveMouthVantage = null;
            inaccessibleMouthTicks = 0;
            caveEntrance = null;
            compactRouteGoal = null;
            siegeCharges = 0;
            return false;
        }
        blockedTicks = Math.min(10000, blockedTicks + 1);
        if (lastCaveAttempt != null
                && enemy.blockPosition().distSqr(lastCaveAttempt) > 10.0D) {
            attemptedCave = false;
            caveRouteExhausted = false;
            caveLanding = null;
            caveMouthVantage = null;
            inaccessibleMouthTicks = 0;
            caveEntrance = null;
            compactRouteGoal = null;
            nextEntranceSearchTick = 0;
        nextGroundMouthPathTick = 0;
            lastCaveAttempt = null;
        }
        if (blockedTicks < COVER_BREATH_DELAY) return false;

        BlockHitResult wall = level.clip(new ClipContext(
                dragon.getFireBreathHeadPosition(), enemy.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon));
        if (wall.getType() != HitResult.Type.BLOCK
                || !level.hasChunkAt(wall.getBlockPos())) return false;

        // A cave is a *different pursuit problem*. Test a real path in the
        // small form before damaging the mountain.
        if (blockedTicks >= CAVE_SEARCH_DELAY
                && startCavePursuit(level, owner, enemy)) return true;

        if (dragon.getFlightMode() != AurenFlightController.FLYING) {
            // Leave standing/small navigation to vanilla/CE; only airborne
            // adult Auren can launch fire-breath or siege shots.
            return false;
        }
        Vec3 strike = wall.getLocation();
        if (dragon.getFireBreathHeadPosition().distanceToSqr(strike)
                > CHARGE_REACH * CHARGE_REACH) return false;
        fireAim = strike;
        // A real stationary firing posture: no self-directed waypoint that
        // causes the flight mover to spin the entire body every tick.
        dragon.getFlightController().holdFacing(owner, strike);

        if (blockedTicks >= SIEGE_DELAY && routeChecked && noAerialRoute
                && (!shelteredUnderground(level, enemy) || attemptedCave)
                && (caveLanding == null || caveRouteExhausted)
                && siegeCharges < MAX_SIEGE_CHARGES && dragon.tickCount >= nextSiegeTick
                && canBreachRock(level, owner, enemy, wall.getBlockPos())) {
            // Localized vanilla MOB explosion (respects mobGriefing and
            // mod/platform explosion events). No explosion for wooden houses.
            level.explode(dragon, strike.x, strike.y, strike.z,
                    (float) BLAST_RADIUS, false, Level.ExplosionInteraction.MOB);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    strike.x, strike.y, strike.z, 65,
                    1.1D, 0.75D, 1.1D, 0.11D);
            level.playSound(null, wall.getBlockPos(),
                    SoundEvents.GENERIC_EXPLODE.value(), dragon.getSoundSource(), 1.5F, 0.76F);
            ++siegeCharges;
            nextSiegeTick = dragon.tickCount + SIEGE_COOLDOWN;
        }
        return true;
    }

    private boolean canBreachRock(ServerLevel level, ServerPlayer owner,
                                  LivingEntity prey, BlockPos impact) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !level.hasChunkAt(impact)
                || owner.distanceToSqr(Vec3.atCenterOf(impact)) < 8.0D * 8.0D
                || !level.getEntitiesOfClass(LivingEntity.class,
                  new AABB(impact).inflate(5.0D),
                  entity -> entity != prey && entity != dragon
                         && dragon.isAlliedTo(entity)).isEmpty()) return false;

        // Strict local rock-only whitelist. A village, wood door, chest,
        // ore, lava pocket, warded block or unloaded region prohibits siege.
        // Prevent collateral damage to user builds from nearby explosions.
        for (BlockPos pos : BlockPos.betweenClosed(
                impact.offset(-2, -2, -2), impact.offset(2, 2, 2))) {
            if (!level.hasChunkAt(pos) || level.getBlockEntity(pos) != null) return false;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (!state.is(BlockTags.BASE_STONE_OVERWORLD)
                    && !state.is(Blocks.COBBLESTONE)
                    && !state.is(Blocks.MAGMA_BLOCK)
                    && !state.is(AurenScorchedBlocks.STONE.get())
                    && !state.is(AurenScorchedBlocks.COBBLESTONE.get())) return false;
            if (state.getDestroySpeed(level, pos) < 0.0F) return false;
        }
        return true;
    }
}
