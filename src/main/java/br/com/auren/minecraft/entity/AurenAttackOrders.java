/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * The tactical patterns mirror IceAndFire CE IafDragonFlightManager:
 * TACKLE, HOVER_BLAST, SCORCH_STREAM. The existing CE FlightMoveHelper
 * remains the ONLY movement controller.
 */
package br.com.auren.minecraft.entity;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Owner-only, server-authored persistent attack orders. */
public final class AurenAttackOrders {
    private static final int NONE = 0;
    private static final int ENEMY = 1;
    private static final int AREA = 2;
    private static final int MAX_MARK_DISTANCE = 64;
    private enum AirAttack { TACKLE, HOVER_BLAST, SCORCH_STREAM }

    private final AurenDragon dragon;
    private final AurenRelentlessHunt hunter;
    private int mode;
    private boolean areaEnabled = true;
    private UUID markedEnemy;
    private LivingEntity recentlyResolvedEnemy;
    private BlockPos areaCenter;
    private int radius;
    private int combatTicks;
    private int nextTacticTick;
    private int nextWaypointTick;
    private int nextSweepTick;
    private int meleeCooldown;
    private int sightLostTicks;
    private int groundNextPathTick;
    private int groundRouteFailedTicks;
    private BlockPos groundRouteTarget;
    private int areaLegs;
    // 0: new organic sortie, 1: enter, 2: curved firing bend, 3: firing exit.
    // CE flight controls still steer; only waypoints/impact decisions change.
    private int areaPhase;
    private Vec3 areaRunDirection;
    private Vec3 areaFocus;
    private Vec3 lastAreaImpact;
    private double areaCurveSign;
    private final int[] areaCoverage = new int[25]; // 5x5 coverage memory
    private boolean areaFiringPass;
    private int tackleBreakawayUntil;
    private Vec3 tackleBreakaway;
    private int streamLeg;
    private AirAttack tactic;
    private Vec3 waypoint;
    private Vec3 streamDirection;
    private Vec3 fireAim;

    AurenAttackOrders(AurenDragon dragon) {
        this.dragon = dragon;
        this.hunter = new AurenRelentlessHunt(dragon);
    }

    public boolean hasOrder() { return mode != NONE; }
    public boolean isCompactHunting() { return hunter.isCompact(); }
    public int getMode() { return mode; }
    public boolean isAreaEnabled() { return areaEnabled; }

    /** The button B toggles bombing only; creature tactics remain available. */
    public boolean toggleAreaEnabled() {
        areaEnabled = !areaEnabled;
        if (!areaEnabled && mode == AREA) clear();
        return areaEnabled;
    }

    public int getMarkedEnemyEntityId() {
        if (mode != ENEMY || recentlyResolvedEnemy == null
                || !recentlyResolvedEnemy.isAlive() || recentlyResolvedEnemy.isRemoved()) return -1;
        return recentlyResolvedEnemy.getId();
    }

    public boolean isAreaOrder() { return mode == AREA; }
    public BlockPos getAreaCenter() { return areaCenter; }
    public int getRadius() { return radius; }
    public Vec3 getFireAim() { return fireAim; }

    /**
     * Compact hunter's electric breath is a MARKED-PREY ability, not an
     * automatic attack on unrelated hostiles. Sight and distance are checked
     * every tick on the server, while a real ground path is still followed.
     */
    public boolean isMarkedEnemy(LivingEntity candidate) {
        return mode == ENEMY && candidate != null
                && markedEnemy != null && markedEnemy.equals(candidate.getUUID())
                && canMarkTarget(candidate);
    }

    public Vec3 getCompactLightningAim() {
        if (mode != ENEMY || !hunter.isCompact() || !dragon.isSmallForm()
                || dragon.isOnShoulder() || dragon.isVehicle()
                || dragon.getFlightMode() != AurenFlightController.GROUNDED)
            return null;
        LivingEntity enemy = recentlyResolvedEnemy;
        if (!canMarkTarget(enemy) || !dragon.hasLineOfSight(enemy)
                || dragon.distanceToSqr(enemy) > 17.0D * 17.0D)
            return null;
        return enemy.getBoundingBox().getCenter();
    }

    public String description() {
        if (mode == AREA && areaCenter != null)
            return "Bombardeio livre (" + radius + " blocos), centro " + areaCenter.toShortString();
        if (mode == ENEMY) return "Combatendo alvo marcado";
        return "Sem ordem; bombardeio " + (areaEnabled ? "ligado" : "desligado");
    }

    private void begin() {
        hunter.reset();
        combatTicks = 0;
        nextTacticTick = 0;
        nextWaypointTick = 0;
        nextSweepTick = 0;
        meleeCooldown = 0;
        sightLostTicks = 0;
        groundNextPathTick = 0;
        groundRouteFailedTicks = 0;
        groundRouteTarget = null;
        areaLegs = 0;
        areaPhase = 0;
        areaRunDirection = null;
        areaFocus = null;
        lastAreaImpact = null;
        areaCurveSign = 1.0D;
        java.util.Arrays.fill(areaCoverage, 0);
        areaFiringPass = false;
        tackleBreakawayUntil = 0;
        tackleBreakaway = null;
        streamLeg = 0;
        tactic = null;
        waypoint = null;
        streamDirection = null;
        fireAim = null;
        dragon.setTarget(null);
        dragon.getNavigation().stop();
    }

    /** Allow passive mobs and survival players, never the owner or allied pets. */
    public boolean canMarkTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || target.isRemoved()
                || target == dragon || target == dragon.getOwner()
                || target.level() != dragon.level() || dragon.isAlliedTo(target)) return false;
        if (target instanceof Player player && (player.isCreative() || player.isSpectator()))
            return false;
        return !(target instanceof TamableAnimal tame && tame.isTame()
                && dragon.getOwnerUUID() != null
                && dragon.getOwnerUUID().equals(tame.getOwnerUUID()));
    }

    public boolean markEnemy(LivingEntity enemy) {
        if (!canMarkTarget(enemy)
                || dragon.position().distanceToSqr(enemy.getBoundingBox().getCenter())
                   > MAX_MARK_DISTANCE * MAX_MARK_DISTANCE
                || !dragon.level().hasChunkAt(enemy.blockPosition())) return false;
        mode = ENEMY;
        markedEnemy = enemy.getUUID();
        recentlyResolvedEnemy = enemy;
        areaCenter = null;
        radius = 0;
        begin();
        return true;
    }

    public boolean markArea(BlockPos center, int selectedRadius) {
        if (!areaEnabled || center == null || selectedRadius < 5 || selectedRadius > 30
                || selectedRadius % 5 != 0 || !dragon.level().hasChunkAt(center)
                || dragon.position().distanceToSqr(Vec3.atCenterOf(center))
                   > MAX_MARK_DISTANCE * MAX_MARK_DISTANCE) return false;
        mode = AREA;
        areaCenter = center.immutable();
        markedEnemy = null;
        recentlyResolvedEnemy = null;
        radius = selectedRadius;
        begin();
        return true;
    }

    public void clear() {
        mode = NONE;
        markedEnemy = null;
        recentlyResolvedEnemy = null;
        areaCenter = null;
        radius = 0;
        begin();
        if (!dragon.level().isClientSide() && !dragon.isVehicle())
            dragon.getFlightController().land();
    }

    private boolean canFlyTo(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        return pos.getY() > level.getMinBuildHeight() + 2
                && pos.getY() < level.getMaxBuildHeight() - 3
                && level.hasChunkAt(pos)
                && level.noCollision(dragon, dragon.getBoundingBox().move(
                        point.x - dragon.getX(), point.y - dragon.getY(),
                        point.z - dragon.getZ()));
    }

    /**
     * CE HOVER_BLAST keeps a workable aerial firing position near the prey.
     * Don't randomly pick an unreachable point behind a house: sample
     * roof-clear positions with a real LOS ray from that prospective vantage.
     */
    private Vec3 hoverPoint(ServerLevel level, LivingEntity enemy) {
        for (int i = 0; i < 16; ++i) {
            double angle = dragon.getRandom().nextDouble() * Math.PI * 2.0D;
            double range = 7.0D + dragon.getRandom().nextDouble() * 8.0D;
            Vec3 point = new Vec3(
                    enemy.getX() + Math.cos(angle) * range,
                    enemy.getY() + 10.0D + dragon.getRandom().nextDouble() * 6.0D,
                    enemy.getZ() + Math.sin(angle) * range);
            if (canFlyTo(level, point) && clearViewFrom(level, point, enemy))
                return point;
        }
        return null;
    }

    /** A candidate vantage must see the victim before we commit to flying it. */
    private boolean clearViewFrom(ServerLevel level, Vec3 vantage, LivingEntity enemy) {
        BlockPos targetChunk = enemy.blockPosition();
        if (!level.hasChunkAt(targetChunk)) return false;
        return level.clip(new ClipContext(vantage, enemy.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                dragon)).getType() == HitResult.Type.MISS;
    }

    /**
     * CE SCORCH_STREAM records the prey/attacker starting positions and
     * commits to one side of the prey before reversing on arrival.
     * Reuse that directional relationship instead of selecting random points.
     */
    private Vec3 streamPoint(ServerLevel level, LivingEntity enemy) {
        if (streamDirection == null) {
            Vec3 delta = enemy.position().subtract(dragon.position());
            Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
            streamDirection = horizontal.lengthSqr() > 1.0E-4D
                    ? horizontal.normalize() : new Vec3(1, 0, 0);
        }
        double side = streamLeg % 2 == 0 ? 1.0D : -1.0D;
        Vec3 point = enemy.position().add(
                streamDirection.x * 17.0D * side,
                11.0D, streamDirection.z * 17.0D * side);
        return canFlyTo(level, point) && clearViewFrom(level, point, enemy)
                ? point : hoverPoint(level, enemy);
    }

    /**
     * Find a safe approach *near* a blocked target; never send the large
     * collision box of Auren into the target's solid block or cave wall.
     * Still lets the melee hit be triggered by actual box proximity later.
     */
    private Vec3 tackleApproachPoint(ServerLevel level, LivingEntity enemy) {
        Vec3 away = dragon.position().subtract(enemy.position());
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 1.0E-4D) away = new Vec3(1.0D, 0.0D, 0.0D);
        away = away.normalize();
        for (int dist : new int[] {3, 5, 8}) {
            for (int elevation : new int[] {2, 5, 9}) {
                Vec3 candidate = enemy.position().add(away.scale(dist))
                        .add(0.0D, elevation, 0.0D);
                if (canFlyTo(level, candidate)
                        && clearViewFrom(level, candidate, enemy))
                    return candidate;
            }
        }
        return null;
    }

    /**
     * When an enemy is hidden in a cave or room, a LOS-approved firing
     * position may not exist. Recover to a COLLISION-FREE rally point and
     * periodically try again; never deliberately dive through the wall.
     */
    private Vec3 recoveryPoint(ServerLevel level, LivingEntity enemy) {
        Vec3 visible = hoverPoint(level, enemy);
        if (visible != null) return visible;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0D;
            double distance = 9.0D + (i % 2) * 4.0D;
            BlockPos horizontal = BlockPos.containing(
                    enemy.getX() + Math.cos(angle) * distance,
                    enemy.getY(), enemy.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunkAt(horizontal)) continue;
            int surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, horizontal).getY();
            Vec3 candidate = new Vec3(horizontal.getX() + 0.5D,
                    Math.max(surface + 8.0D, enemy.getY() + 8.0D),
                    horizontal.getZ() + 0.5D);
            if (canFlyTo(level, candidate)) return candidate;
        }
        // Loaded and unobstructed: a steady local holding point is better
        // than a repeated forced landing, especially in constrained spaces.
        Vec3 holding = dragon.position().add(0.0D, 2.0D, 0.0D);
        return canFlyTo(level, holding) ? holding : null;
    }

    /**
     * Preserve CE's three original tactics while adapting the decision
     * logic to a marked prey. Decisions are based on combat opportunity:
     * reacquire sight, close distance, dive, then sweep or hover to breathe.
     * No arbitrary random tactic switching mid-maneuver.
     */
    private void chooseTactic(LivingEntity enemy) {
        AirAttack previous = tactic;
        double distanceSq = dragon.distanceToSqr(enemy);
        if (!dragon.hasLineOfSight(enemy)) {
            tactic = AirAttack.HOVER_BLAST;
        } else if (distanceSq > 28.0D * 28.0D) {
            tactic = AirAttack.TACKLE;
        } else if (distanceSq < 9.0D * 9.0D && previous != AirAttack.TACKLE) {
            tactic = AirAttack.TACKLE;
        } else if (previous == AirAttack.HOVER_BLAST
                && distanceSq >= 9.0D * 9.0D) {
            tactic = AirAttack.SCORCH_STREAM;
        } else {
            tactic = AirAttack.HOVER_BLAST;
        }
        // Keep each CE maneuver alive long enough for a real fire stream,
        // rather than swapping strategies faster than the breath wind-up.
        nextTacticTick = combatTicks + 125 + dragon.getRandom().nextInt(45);
        nextWaypointTick = 0;
        waypoint = null;
        streamDirection = null;
        streamLeg = 0;
        tackleBreakaway = null;
        tackleBreakawayUntil = 0;
    }

    /**
     * Prefer a REAL ground A* route before taking off. The marked target
     * is not a flight order: pursuing on foot allows Auren to approach the
     * cave entrance rather than hovering above its ceiling.
     *
     * Replan sparingly to avoid resetting turns at every corner. If the adult
     * has no route to a concealed target, hold off on takeoff long enough for
     * the cave-mouth/small-form hunter to get a chance at 110 ticks.
     */
    private boolean tickGroundEnemy(ServerLevel level, LivingEntity enemy,
                                    boolean directSight) {
        if (dragon.getFlightMode() != AurenFlightController.GROUNDED
                || dragon.isSmallForm() || dragon.isVehicle()) return false;
        double distanceSq = dragon.distanceToSqr(enemy);
        if (distanceSq > 35.0D * 35.0D) return false;

        if (combatTicks >= groundNextPathTick
                || groundRouteTarget == null
                || groundRouteTarget.distSqr(enemy.blockPosition()) > 9.0D) {
            groundNextPathTick = combatTicks + 35;
            Path path = dragon.getNavigation().createPath(enemy, 0);
            if (path != null && path.canReach()) {
                groundRouteFailedTicks = 0;
                groundRouteTarget = enemy.blockPosition();
                if (distanceSq > 10.0D * 10.0D)
                    dragon.getNavigation().moveTo(path, 1.12D);
                else
                    dragon.getNavigation().stop();
            } else {
                groundRouteTarget = null;
                groundRouteFailedTicks = Math.min(300, groundRouteFailedTicks + 35);
            }
        }

        if (directSight && distanceSq <= 24.0D * 24.0D) {
            // Fire from the ground. Hold far enough away for the fire cone;
            // do not take off merely to trigger the fire-breath state.
            if (distanceSq <= 10.0D * 10.0D)
                dragon.getNavigation().stop();
            fireAim = enemy.getBoundingBox().getCenter();
            return true;
        }
        fireAim = null;
        if (groundRouteTarget != null && !dragon.getNavigation().isDone())
            return true;
        // Wait for the *cave* detector before aerial assault. This avoids
        // the previous immediate takeoff when the prey ducks into a tunnel.
        return !directSight && sightLostTicks < 145;
    }

    private void tickEnemy(ServerLevel level, ServerPlayer owner, LivingEntity enemy) {
        boolean directSight = dragon.hasLineOfSight(enemy);
        sightLostTicks = directSight ? 0 : Math.min(10000, sightLostTicks + 1);
        // Only mark a prey genuinely inaccessible after searching actual
        // collision-free CE air vantage positions, then escalate gradually.
        if (sightLostTicks >= 100 && sightLostTicks % 40 == 0)
            hunter.inspectAerialRoute(hoverPoint(level, enemy) != null);
        if (hunter.tick(level, owner, enemy, directSight)) {
            fireAim = hunter.getFireAim();
            return;
        }
        if (tickGroundEnemy(level, enemy, directSight)) return;
        // If a wall hides the prey, stop a blind dive and search for a
        // clear aerial vantage instead of circling uselessly.
        if (tactic == null || combatTicks >= nextTacticTick
                || (sightLostTicks == 20 && tactic != AirAttack.HOVER_BLAST))
            chooseTactic(enemy);
        Vec3 center = enemy.getBoundingBox().getCenter();
        Vec3 next;
        switch (tactic) {
            case TACKLE -> {
                // Original CE TACKLE homes directly to the prey and hits on contact.
                // Small motion lead prevents the old chase-the-previous-
                // coordinate effect on moving ground enemies.
                Vec3 velocity = enemy.getDeltaMovement();
                Vec3 lead = new Vec3(velocity.x, 0, velocity.z).scale(4.0D);
                if (lead.lengthSqr() > 25.0D) lead = lead.normalize().scale(5.0D);
                next = center.add(lead).add(0, enemy.getBbHeight() * 0.3D, 0);
                fireAim = null;
                if (meleeCooldown > 0) --meleeCooldown;
                double reach = (dragon.getBbWidth() + enemy.getBbWidth() + 0.6D);
                if (meleeCooldown == 0 && dragon.distanceToSqr(enemy) <= reach * reach
                        && dragon.getBoundingBox().inflate(0.7D).intersects(enemy.getBoundingBox())
                        && dragon.hasLineOfSight(enemy) && canMarkTarget(enemy)) {
                    // CE TACKLE must complete a physical contact hit, then
                    // create space for another dive instead of circling in place.
                    boolean landedHit = dragon.doHurtTarget(enemy);
                    meleeCooldown = 20;
                    if (landedHit) {
                        // A successful dive is an actual combat event: let
                        // the next tactical decision follow the breakaway.
                        nextTacticTick = Math.min(nextTacticTick, combatTicks + 42);
                    }
                    if (landedHit) {
                        Vec3 delta = dragon.position().subtract(enemy.position());
                        Vec3 retreat = new Vec3(delta.x, 0, delta.z);
                        if (retreat.lengthSqr() < 1.0E-4D) retreat = new Vec3(1, 0, 0);
                        Vec3 candidate = dragon.position().add(
                                retreat.normalize().scale(10.0D)).add(0, 6.0D, 0);
                        if (canFlyTo(level, candidate)) {
                            tackleBreakaway = candidate;
                            tackleBreakawayUntil = combatTicks + 40;
                        }
                    }
                }
                if (tackleBreakaway != null && combatTicks < tackleBreakawayUntil)
                    next = tackleBreakaway;
                else tackleBreakaway = null;
            }
            case HOVER_BLAST -> {
                // CE picks a new overhead offset when too near or far from prey.
                // Original CE HOVER_BLAST keeps its position until too
                // close/far; avoid rerolling directions every few seconds.
                double horizontalSq = dragon.distanceToSqr(
                        enemy.getX(), dragon.getY(), enemy.getZ());
                if (waypoint == null || !canFlyTo(level, waypoint)
                        || combatTicks >= nextWaypointTick
                        || (sightLostTicks == 20)) {
                    Vec3 chosen = hoverPoint(level, enemy);
                    // If the target is fully enclosed, keep flying around
                    // loaded, clear terrain rather than repeatedly landing.
                    if (chosen == null) chosen = recoveryPoint(level, enemy);
                    if (chosen != null) waypoint = chosen;
                    nextWaypointTick = combatTicks + ((horizontalSq < 16.0D
                            || horizontalSq > 900.0D) ? 45 : 70);
                }
                next = waypoint;
                fireAim = dragon.hasLineOfSight(enemy) ? center : null;
            }
            case SCORCH_STREAM -> {
                // CE reverses its streaming pass near the opposite side.
                // CE reverses the scorch leg only at the far side of prey.
                // Keep it for a full pass, but replan if line of sight is lost.
                if (waypoint == null || dragon.position().distanceToSqr(waypoint) < 100.0D
                        || combatTicks >= nextWaypointTick) {
                    if (waypoint != null) ++streamLeg;
                    Vec3 chosen = streamPoint(level, enemy);
                    if (chosen != null) waypoint = chosen;
                    nextWaypointTick = combatTicks + 125;
                }
                next = waypoint;
                fireAim = dragon.hasLineOfSight(enemy) ? center : null;
            }
            default -> { return; }
        }
        if (next == null || !canFlyTo(level, next)) {
            // TACKLE must never bypass collision checks. Approach near the
            // prey only through free space; otherwise seek a rally waypoint.
            Vec3 recovery = tactic == AirAttack.TACKLE
                    && waypoint != null && combatTicks < nextWaypointTick
                    && canFlyTo(level, waypoint) ? waypoint : null;
            if (recovery == null && tactic == AirAttack.TACKLE)
                recovery = tackleApproachPoint(level, enemy);
            if (recovery == null) recovery = recoveryPoint(level, enemy);
            if (recovery != null) {
                if (waypoint == null || !waypoint.equals(recovery)
                        || combatTicks >= nextWaypointTick) {
                    waypoint = recovery;
                    nextWaypointTick = combatTicks + 45;
                }
                dragon.getFlightController().patrolAttack(owner, recovery, false);
            } else {
                // No verified free location: do not fly into solid blocks.
                dragon.getFlightController().land();
            }
            fireAim = null;
            return;
        }
        if (!dragon.getFlightController().patrolAttack(
                owner, next, tactic == AirAttack.TACKLE
                        && tackleBreakaway == null)) fireAim = null;
    }

    /**
     * Five-by-five coverage map across the circular order. The cells are
     * coarse heat memory, NOT a rectangular effect on the actual world.
     * Coordinates are normalized by radius, so 5 and 30 block areas both
     * get a balanced mix of locations as the fight goes on.
     */
    private int areaCell(Vec3 center, double x, double z) {
        int col = Math.max(0, Math.min(4,
                (int) Math.floor(((x - center.x) / radius + 1.0D) * 2.5D)));
        int row = Math.max(0, Math.min(4,
                (int) Math.floor(((z - center.z) / radius + 1.0D) * 2.5D)));
        return row * 5 + col;
    }

    /** Only the SERVER's actual breath impacts count as burned coverage. */
    public void recordAreaImpact(Vec3 impact) {
        if (mode != AREA || areaCenter == null || impact == null) return;
        Vec3 center = Vec3.atCenterOf(areaCenter);
        double dx = impact.x - center.x;
        double dz = impact.z - center.z;
        if (dx * dx + dz * dz <= radius * radius)
            rememberAreaImpact(center, impact);
    }

    private void rememberAreaImpact(Vec3 center, Vec3 impact) {
        int cell = areaCell(center, impact.x, impact.z);
        areaCoverage[cell] = Math.min(200, areaCoverage[cell] + 1);
        lastAreaImpact = impact;
        // Decay relative importance after several full sweeps; otherwise
        // an hour-long attack would saturate all grid buckets.
        int minimum = Integer.MAX_VALUE;
        for (int hits : areaCoverage) minimum = Math.min(minimum, hits);
        if (minimum >= 16) {
            for (int i = 0; i < areaCoverage.length; i++)
                areaCoverage[i] -= minimum;
        }
    }

    /** A fresh, lightly-visited region for EACH sortie, not the center again. */
    private Vec3 chooseAreaFocus(ServerLevel level, Vec3 center) {
        Vec3 best = center;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 44; i++) {
            double angle = dragon.getRandom().nextDouble() * Math.PI * 2.0D;
            double spread = Math.sqrt(dragon.getRandom().nextDouble())
                    * Math.max(2.0D, radius - 2.0D);
            double x = center.x + Math.cos(angle) * spread;
            double z = center.z + Math.sin(angle) * spread;
            if (!level.hasChunkAt(BlockPos.containing(x, center.y, z))) continue;
            int cell = areaCell(center, x, z);
            double score = areaCoverage[cell] * 18.0D
                    + dragon.getRandom().nextDouble() * 4.0D;
            if (lastAreaImpact != null) {
                double dist2 = Math.pow(x - lastAreaImpact.x, 2)
                        + Math.pow(z - lastAreaImpact.z, 2);
                if (dist2 < 7.0D * 7.0D) score += 22.0D;
            }
            if (score < bestScore) {
                bestScore = score;
                best = new Vec3(x, center.y, z);
            }
        }
        return best;
    }

    /**
     * Organic bombing: approach -> bent midway waypoint -> exit with a
     * second bend. No perfect orbit, no symmetric parallel center-crossing
     * lines. The upstream CE FlightMoveHelper owns every turn and wing beat.
     */
    private void tickArea(ServerLevel level, ServerPlayer owner, Vec3 center) {
        if (waypoint == null || combatTicks >= nextWaypointTick
                || dragon.position().distanceToSqr(waypoint) < 36.0D) {
            Vec3 candidate = null;
            int nextPhase = areaPhase;
            if (areaPhase == 0 || areaPhase == 3) {
                areaFocus = chooseAreaFocus(level, center);
                Vec3 direction = null;
                for (int attempt = 0; attempt < 12; attempt++) {
                    double angle = dragon.getRandom().nextDouble() * Math.PI * 2.0D;
                    Vec3 sampled = new Vec3(Math.cos(angle), 0.0D,
                            Math.sin(angle));
                    if (areaRunDirection == null
                            || Math.abs(sampled.dot(areaRunDirection)) < 0.82D
                            || attempt == 11) {
                        direction = sampled;
                        break;
                    }
                }
                if (direction == null) direction = new Vec3(1, 0, 0);
                areaRunDirection = direction;
                areaCurveSign = dragon.getRandom().nextBoolean() ? 1.0D : -1.0D;
                Vec3 beginning = areaFocus.subtract(
                        direction.scale(radius * 0.66D + 10.0D));
                candidate = safeAreaWaypoint(level, center, beginning, 11.0D);
                nextPhase = 1;
                ++areaLegs;
            } else if (areaPhase == 1 && areaRunDirection != null
                    && areaFocus != null) {
                Vec3 perpendicular = new Vec3(-areaRunDirection.z, 0.0D,
                        areaRunDirection.x);
                Vec3 bend = areaFocus.add(areaRunDirection.scale(radius * 0.18D))
                        .add(perpendicular.scale(areaCurveSign
                                * Math.max(2.0D, radius * 0.25D)));
                candidate = safeAreaWaypoint(level, center, bend, 10.0D);
                nextPhase = 2;
            } else if (areaPhase == 2 && areaRunDirection != null
                    && areaFocus != null) {
                Vec3 perpendicular = new Vec3(-areaRunDirection.z, 0.0D,
                        areaRunDirection.x);
                Vec3 exit = areaFocus.add(areaRunDirection.scale(
                        radius * 0.67D + 7.0D))
                        .add(perpendicular.scale(areaCurveSign
                                * Math.max(3.0D, radius * 0.43D)));
                candidate = safeAreaWaypoint(level, center, exit, 11.0D);
                nextPhase = 3;
            }

            if (candidate != null) {
                areaPhase = nextPhase;
                areaFiringPass = areaPhase == 2 || areaPhase == 3;
                waypoint = candidate;
                nextWaypointTick = combatTicks + (areaFiringPass ? 115 : 130);
                fireAim = null;
                nextSweepTick = combatTicks;
            } else {
                fireAim = null;
                // No safe waypoint: reset sortie and retry later rather
                // than repeatedly fly through loaded buildings or hills.
                waypoint = null;
                areaPhase = 0;
                nextWaypointTick = combatTicks + 20;
                dragon.getFlightController().land();
                return;
            }
        }
        if (waypoint == null || !level.hasChunkAt(BlockPos.containing(waypoint))
                || !dragon.getFlightController().patrol(owner, waypoint)) {
            fireAim = null;
            dragon.getFlightController().land();
            return;
        }

        if (!areaFiringPass) {
            fireAim = null; // Turn and approach with no blind fire.
        } else if (combatTicks >= nextSweepTick || fireAim == null) {
            fireAim = findDistributedAreaImpact(level, center);
            nextSweepTick = combatTicks + 6;
        }
    }

    /** Select collision-free flight altitudes, without changing CE steering. */
    private Vec3 safeAreaWaypoint(ServerLevel level, Vec3 center,
                                  Vec3 horizontal, double rise) {
        BlockPos pos = BlockPos.containing(horizontal);
        if (!level.hasChunkAt(pos)) return null;
        int roof = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos).getY();
        BlockPos middle = BlockPos.containing(center);
        if (!level.hasChunkAt(middle)) return null;
        int centralRoof = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle).getY();
        double height = Math.max(Math.max(roof, centralRoof) + rise,
                center.y + rise);
        Vec3 candidate = new Vec3(horizontal.x, height, horizontal.z);
        return canFlyTo(level, candidate) ? candidate : null;
    }

    /**
     * Target under-attacked patches rather than the first block directly in
     * front of the muzzle. The forward-cone and ray guards make the flames
     * look like real dragon breath, not a magic beam behind its head.
     * All candidate impact points are within the user-selected circle.
     */
    private Vec3 findDistributedAreaImpact(ServerLevel level, Vec3 center) {
        Vec3 mouth = dragon.getFireBreathHeadPosition();
        double yaw = Math.toRadians(dragon.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        Vec3 best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int i = 0; i < 48; i++) {
            double a = dragon.getRandom().nextDouble() * Math.PI * 2.0D;
            double r = Math.sqrt(dragon.getRandom().nextDouble())
                    * Math.max(1.0D, radius - 1.0D);
            double x = center.x + Math.cos(a) * r;
            double z = center.z + Math.sin(a) * r;
            BlockPos sample = BlockPos.containing(x, mouth.y, z);
            if (!level.hasChunkAt(sample)) continue;
            BlockPos roof = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sample);
            if (roof.getY() >= mouth.y - 1.0D) continue;
            Vec3 aim = new Vec3(x, roof.getY() - 0.25D, z);
            double rangeSq = mouth.distanceToSqr(aim);
            if (rangeSq > 27.5D * 27.5D || rangeSq < 3.0D * 3.0D)
                continue;
            Vec3 dxz = new Vec3(aim.x - mouth.x, 0.0D, aim.z - mouth.z);
            double horizontal = Math.sqrt(dxz.lengthSqr());
            if (horizontal < 0.1D) continue;
            double facing = (dxz.x * forwardX + dxz.z * forwardZ) / horizontal;
            if (facing < 0.22D) continue;

            // Reject positions behind cliffs or roofs; FireBreath's ray is
            // authoritative and will still clip actual terrain.
            if (level.clip(new ClipContext(mouth, aim,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    dragon)).getLocation().distanceToSqr(aim) > 3.0D * 3.0D)
                continue;

            int cell = areaCell(center, aim.x, aim.z);
            double score = areaCoverage[cell] * 16.0D
                    + (1.0D - facing) * 14.0D
                    + dragon.getRandom().nextDouble() * 6.0D;
            if (areaFocus != null)
                score += Math.sqrt(Math.pow(x - areaFocus.x, 2)
                        + Math.pow(z - areaFocus.z, 2)) * 0.18D;
            if (lastAreaImpact != null) {
                double d = Math.sqrt(Math.pow(x - lastAreaImpact.x, 2)
                        + Math.pow(z - lastAreaImpact.z, 2));
                if (d < 3.0D) score += 48.0D;
                else if (d > 13.0D) score += 14.0D;
            }
            if (score < bestScore) {
                bestScore = score;
                best = aim;
            }
        }
        return best;
    }

    public void tick() {
        hunter.tickPassive();
        if (mode == NONE || !(dragon.level() instanceof ServerLevel level)) {
            fireAim = null;
            return;
        }
        dragon.setTarget(null);
        if (dragon.isVehicle()) {
            fireAim = null;
            return;
        }
        if (!(dragon.getOwner() instanceof ServerPlayer owner)
                || !owner.isAlive() || owner.isSpectator() || owner.level() != level
                || !dragon.isAlive()
                || (dragon.isSmallForm() && !hunter.isCompact())
                || dragon.isOnShoulder() || dragon.isOrderedToSit()) {
            fireAim = null;
            dragon.getFlightController().land();
            return;
        }

        LivingEntity enemy = null;
        Vec3 center;
        if (mode == ENEMY) {
            Entity found = markedEnemy == null ? null : level.getEntity(markedEnemy);
            if (found == null) {
                if (recentlyResolvedEnemy != null && !recentlyResolvedEnemy.isAlive())
                    clear();
                else dragon.getFlightController().land();
                fireAim = null;
                return;
            }
            if (!(found instanceof LivingEntity living) || !canMarkTarget(living)) {
                clear();
                return;
            }
            enemy = living;
            recentlyResolvedEnemy = living;
            center = living.getBoundingBox().getCenter();
        } else {
            if (!areaEnabled || areaCenter == null || !level.hasChunkAt(areaCenter)) {
                fireAim = null;
                dragon.getFlightController().land();
                return;
            }
            center = Vec3.atCenterOf(areaCenter);
        }

        // Preserve v0.7.7 chunk/range safety; no remote force-loading.
        if (dragon.position().distanceToSqr(center) > MAX_MARK_DISTANCE * MAX_MARK_DISTANCE
                || !level.hasChunkAt(BlockPos.containing(center))) {
            fireAim = null;
            dragon.getFlightController().land();
            return;
        }

        ++combatTicks;
        if (enemy != null) tickEnemy(level, owner, enemy);
        else tickArea(level, owner, center);
    }

    public void save(CompoundTag tag) {
        hunter.save(tag);
        tag.putBoolean("AurenAreaAttackEnabled", areaEnabled);
        tag.putInt("AurenAttackMode", mode);
        if (mode == ENEMY && markedEnemy != null)
            tag.putUUID("AurenMarkedEnemy", markedEnemy);
        if (mode == AREA && areaCenter != null) {
            tag.putInt("AurenAttackX", areaCenter.getX());
            tag.putInt("AurenAttackY", areaCenter.getY());
            tag.putInt("AurenAttackZ", areaCenter.getZ());
            tag.putInt("AurenAttackRadius", radius);
        }
    }

    public void load(CompoundTag tag) {
        mode = NONE;
        areaEnabled = !tag.contains("AurenAreaAttackEnabled")
                || tag.getBoolean("AurenAreaAttackEnabled");
        markedEnemy = null;
        recentlyResolvedEnemy = null;
        areaCenter = null;
        radius = 0;
        begin();
        int storedMode = tag.getInt("AurenAttackMode");
        hunter.load(tag);
        if (storedMode == ENEMY && tag.hasUUID("AurenMarkedEnemy")) {
            mode = ENEMY;
            markedEnemy = tag.getUUID("AurenMarkedEnemy");
        } else if (storedMode == AREA && areaEnabled && tag.contains("AurenAttackRadius")
                && tag.contains("AurenAttackX")
                && tag.contains("AurenAttackY") && tag.contains("AurenAttackZ")) {
            int storedRadius = tag.getInt("AurenAttackRadius");
            if (storedRadius >= 5 && storedRadius <= 30 && storedRadius % 5 == 0) {
                mode = AREA;
                radius = storedRadius;
                areaCenter = new BlockPos(tag.getInt("AurenAttackX"),
                        tag.getInt("AurenAttackY"), tag.getInt("AurenAttackZ"));
            }
        }
    }
}
