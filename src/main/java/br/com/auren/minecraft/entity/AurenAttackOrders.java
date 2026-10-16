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
    private int nextRecoveryTick;
    private int recoveryFailureTicks;
    private boolean lastTackleConnected;
    private int areaLegs;
    private int areaPhase; // 0: plan approach, 1: approach, 2: committed attack pass
    private Vec3 areaRunDirection;
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
    }

    public boolean hasOrder() { return mode != NONE; }
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

    public String description() {
        if (mode == AREA && areaCenter != null)
            return "Bombardeio livre (" + radius + " blocos), centro " + areaCenter.toShortString();
        if (mode == ENEMY) return "Combatendo alvo marcado";
        return "Sem ordem; bombardeio " + (areaEnabled ? "ligado" : "desligado");
    }

    private void begin() {
        combatTicks = 0;
        nextTacticTick = 0;
        nextWaypointTick = 0;
        nextSweepTick = 0;
        meleeCooldown = 0;
        sightLostTicks = 0;
        nextRecoveryTick = 0;
        recoveryFailureTicks = 0;
        lastTackleConnected = false;
        areaLegs = 0;
        areaPhase = 0;
        areaRunDirection = null;
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
        // Match the actual 64-block combat-operating radius at the time
        // an order is accepted. The owner's 48-block crosshair is separate.
        if (!canMarkTarget(enemy)
                || dragon.distanceToSqr(enemy) > MAX_MARK_DISTANCE * MAX_MARK_DISTANCE)
            return false;
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
                || dragon.distanceToSqr(Vec3.atCenterOf(center))
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
            if (canFlyTo(level, point) && clearFlightCorridor(level, point)
                    && clearViewFrom(level, point, enemy))
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
     * TACKLE still follows CE's direct-prey concept, but ground entities may
     * occupy a point too low for Auren's large body. Approach their side at
     * mouth height instead of sending a full-sized dragon through a wall.
     * Every sampled destination must pass collision and block-ray checks.
     */
    private Vec3 safeTacklePoint(ServerLevel level, LivingEntity enemy, Vec3 desired) {
        if (canFlyTo(level, desired) && clearFlightCorridor(level, desired))
            return desired;
        Vec3 retreat = dragon.position().subtract(enemy.position());
        Vec3 horizontal = new Vec3(retreat.x, 0.0D, retreat.z);
        if (horizontal.lengthSqr() < 1.0E-4D)
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 away = horizontal.normalize().scale(
                (dragon.getBbWidth() + enemy.getBbWidth()) * 0.5D + 0.6D);
        for (double height : new double[] { 1.0D, 2.5D, 4.5D }) {
            Vec3 candidate = new Vec3(desired.x + away.x,
                    enemy.getY() + enemy.getBbHeight() + height,
                    desired.z + away.z);
            if (canFlyTo(level, candidate) && clearFlightCorridor(level, candidate))
                return candidate;
        }
        return null;
    }

    /** Do not call the CE flight mover towards a point behind solid terrain. */
    private boolean clearFlightCorridor(ServerLevel level, Vec3 point) {
        return level.clip(new ClipContext(dragon.getEyePosition(), point,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                dragon)).getType() == HitResult.Type.MISS;
    }

    /**
     * Losing line of sight isn't the same as losing the enemy. CE-style
     * hovering first looks for a clear firing position, then ascends/repositions
     * near Auren without forcing new chunks or trying to fly inside blocks.
     */
    private Vec3 recoveryPoint(ServerLevel level, LivingEntity enemy) {
        Vec3 openFire = hoverPoint(level, enemy);
        if (openFire != null && clearFlightCorridor(level, openFire))
            return openFire;
        Vec3 here = dragon.position();
        for (int i = 0; i < 8; ++i) {
            double angle = (i + (combatTicks / 40) % 8) * Math.PI / 4.0D;
            Vec3 candidate = here.add(Math.cos(angle) * 7.0D,
                    3.0D + i % 2 * 3.0D, Math.sin(angle) * 7.0D);
            if (canFlyTo(level, candidate) && clearFlightCorridor(level, candidate))
                return candidate;
        }
        return null;
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
        } else if (lastTackleConnected && distanceSq <= 28.0D * 28.0D) {
            // Complete the physical bite and retreat, then use fire rather
            // than immediately repeating the same tackle by a timer.
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
        lastTackleConnected = false;
        recoveryFailureTicks = 0;
        nextRecoveryTick = 0;
        nextWaypointTick = 0;
        waypoint = null;
        streamDirection = null;
        streamLeg = 0;
        tackleBreakaway = null;
        tackleBreakawayUntil = 0;
    }

    private void tickEnemy(ServerLevel level, ServerPlayer owner, LivingEntity enemy) {
        sightLostTicks = dragon.hasLineOfSight(enemy) ? 0 : sightLostTicks + 1;
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
                Vec3 desired = center.add(lead).add(0, enemy.getBbHeight() * 0.3D, 0);
                next = safeTacklePoint(level, enemy, desired);
                fireAim = null;
                if (meleeCooldown > 0) --meleeCooldown;
                double reach = (dragon.getBbWidth() + enemy.getBbWidth() + 0.6D);
                if (meleeCooldown == 0 && dragon.distanceToSqr(enemy) <= reach * reach
                        && dragon.getBoundingBox().inflate(0.65D)
                                .intersects(enemy.getBoundingBox())
                        && dragon.hasLineOfSight(enemy) && canMarkTarget(enemy)) {
                    // CE TACKLE must complete a physical contact hit, then
                    // create space for another dive instead of circling in place.
                    boolean landedHit = dragon.doHurtTarget(enemy);
                    meleeCooldown = 20;
                    if (landedHit) {
                        lastTackleConnected = true;
                        nextTacticTick = Math.min(nextTacticTick, combatTicks + 50);
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
                // Replan on cadence or an unsafe arrival, not every tick
                // while close to the prey. Failure waits before another scan.
                boolean unsafeWaypoint = waypoint != null && !canFlyTo(level, waypoint);
                if (waypoint == null || unsafeWaypoint || combatTicks >= nextWaypointTick) {
                    Vec3 chosen = hoverPoint(level, enemy);
                    if (chosen != null) waypoint = chosen;
                    else if (unsafeWaypoint) waypoint = null;
                    nextWaypointTick = combatTicks + (chosen == null ? 20 : 80);
                }
                if (horizontalSq > 900.0D && combatTicks + 25 < nextTacticTick)
                    nextTacticTick = combatTicks + 25;
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
        if (next == null || !canFlyTo(level, next)
                || !clearFlightCorridor(level, next)) {
            fireAim = null;
            // Never bypass canFlyTo for a diving target embedded in terrain.
            // Reuse the same recovery destination for a short interval.
            if (combatTicks >= nextRecoveryTick) {
                waypoint = recoveryPoint(level, enemy);
                nextRecoveryTick = combatTicks + 25;
            }
            if (waypoint != null && canFlyTo(level, waypoint)
                    && clearFlightCorridor(level, waypoint)
                    && dragon.getFlightController().patrolAttack(owner, waypoint, false)) {
                recoveryFailureTicks = 0;
            } else if (++recoveryFailureTicks >= 80) {
                // A truly blocked dragon eventually lands safely rather than
                // staying forever in gravity-free flight with no waypoint.
                dragon.getFlightController().land();
            }
            return;
        }
        recoveryFailureTicks = 0;
        if (!dragon.getFlightController().patrolAttack(
                owner, next, tactic == AirAttack.TACKLE
                        && tackleBreakaway == null)) fireAim = null;
    }

    /**
     * Structured bombing sortie: approach a rim, fly a straight committed
     * attack pass across the mark, then bank/reposition for another pass.
     * Decisions happen at leg boundaries, NEVER randomly while breathing.
     * The CE FlightMoveHelper still owns acceleration, turning and collision.
     */
    private void tickArea(ServerLevel level, ServerPlayer owner, Vec3 center) {
        if (waypoint == null || combatTicks >= nextWaypointTick
                || dragon.position().distanceToSqr(waypoint) < 36.0D) {
            Vec3 candidate = null;
            int nextPhase = areaPhase;
            Vec3 direction = areaRunDirection;
            if (areaPhase == 0 || areaPhase == 2) {
                Vec3 towardCenter = center.subtract(dragon.position());
                direction = new Vec3(towardCenter.x, 0, towardCenter.z);
                if (direction.lengthSqr() < 1.0D) {
                    double yaw = Math.toRadians(dragon.getYRot());
                    direction = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
                }
                direction = direction.normalize();
                // Make the next route recognizably different without a
                // sudden random yaw change in the middle of firing.
                double side = areaLegs % 2 == 0 ? 0.38D : -0.38D;
                double dx = direction.x * Math.cos(side) - direction.z * Math.sin(side);
                double dz = direction.x * Math.sin(side) + direction.z * Math.cos(side);
                direction = new Vec3(dx, 0, dz);
                // The muzzle is several blocks in front of the torso. Start far
                // enough outside even a five-block circle to allow a real
                // continuous forward stream before passing the mark.
                Vec3 start = center.subtract(direction.scale(radius + 12.0D));
                candidate = safeAreaWaypoint(level, center, start, 10.0D);
                nextPhase = 1;
            } else if (areaPhase == 1 && direction != null) {
                Vec3 finish = center.add(direction.scale(radius * 0.98D));
                candidate = safeAreaWaypoint(level, center, finish, 10.0D);
                nextPhase = 2;
            }
            if (candidate != null) {
                areaRunDirection = direction;
                areaPhase = nextPhase;
                areaFiringPass = areaPhase == 2;
                waypoint = candidate;
                nextWaypointTick = combatTicks + (areaFiringPass ? 180 : 120);
                if (areaFiringPass) ++areaLegs;
                fireAim = null;
                nextSweepTick = combatTicks;
            } else {
                // Failed path samples do not send a big dragon into a wall.
                fireAim = null;
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

        // No flame during turn/approach. Commit to the long forward strafing
        // run; steering and target selection now agree on the same heading.
        if (!areaFiringPass) {
            fireAim = null;
        } else if (combatTicks >= nextSweepTick || fireAim == null) {
            fireAim = findForwardAreaImpact(level, center);
            nextSweepTick = combatTicks + 3;
        }
    }

    /** Air above the local roof/terrain, not inside an unsuspected building. */
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
     * A stable three-second-plus fire stream sweeps forward over actual roof
     * surfaces. No randomized block coordinate is selected each frame.
     * The moving dragon and a slow lateral sinusoid create the sweep.
     */
    private Vec3 findForwardAreaImpact(ServerLevel level, Vec3 markedCenter) {
        Vec3 muzzle = dragon.getFireBreathHeadPosition();
        double yaw = Math.toRadians(dragon.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double lateral = Math.sin(combatTicks * 0.06D) * 1.7D;
        for (int distance = 22; distance >= 2; distance -= 2) {
            double x = muzzle.x + forwardX * distance - forwardZ * lateral;
            double z = muzzle.z + forwardZ * distance + forwardX * lateral;
            double dx = x - markedCenter.x;
            double dz = z - markedCenter.z;
            if (dx * dx + dz * dz > radius * radius) continue;
            BlockPos sample = BlockPos.containing(x, muzzle.y, z);
            if (!level.hasChunkAt(sample)) continue;
            BlockPos roof = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sample);
            if (roof.getY() >= muzzle.y - 1.0D) continue;
            Vec3 aim = new Vec3(x, roof.getY() - 0.25D, z);
            if (muzzle.distanceToSqr(aim) <= 28.0D * 28.0D) return aim;
        }
        return null;
    }

    public void tick() {
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
                || !dragon.isAlive() || dragon.isSmallForm()
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
