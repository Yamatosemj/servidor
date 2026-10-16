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
        if (!canMarkTarget(enemy)) return false;
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
                || selectedRadius % 5 != 0 || !dragon.level().hasChunkAt(center)) return false;
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

    /** CE HOVER_BLAST samples a new vantage around the prey, not a circle. */
    private Vec3 hoverPoint(ServerLevel level, LivingEntity enemy) {
        for (int i = 0; i < 12; ++i) {
            Vec3 point = new Vec3(enemy.getX() + dragon.getRandom().nextInt(21) - 10,
                    enemy.getY() + 11.0D + dragon.getRandom().nextInt(5) - 2,
                    enemy.getZ() + dragon.getRandom().nextInt(21) - 10);
            if (canFlyTo(level, point)) return point;
        }
        return null;
    }

    /** CE SCORCH_STREAM: capture starting alignment, then pass over the prey. */
    private Vec3 streamPoint(ServerLevel level, LivingEntity enemy) {
        if (streamDirection == null) {
            Vec3 delta = enemy.position().subtract(dragon.position());
            Vec3 horizontal = new Vec3(delta.x, 0, delta.z);
            streamDirection = horizontal.lengthSqr() > 1.0E-4D
                    ? horizontal.normalize() : new Vec3(1, 0, 0);
        }
        double side = streamLeg % 2 == 0 ? 1.0D : -1.0D;
        Vec3 point = enemy.position().add(
                streamDirection.x * 17.0D * side, 11.0D,
                streamDirection.z * 17.0D * side);
        if (canFlyTo(level, point)) return point;
        return hoverPoint(level, enemy);
    }

    /**
     * Choose the original CE attack TYPES by tactical opportunity.
     * This is still our TamableAnimal adapter, not DragonBaseEntity AI.
     */
    private void chooseTactic(LivingEntity enemy) {
        AirAttack previous = tactic;
        double distanceSq = dragon.distanceToSqr(enemy);
        if (!dragon.hasLineOfSight(enemy)) {
            tactic = AirAttack.HOVER_BLAST; // Seek a new clear vantage.
        } else if (distanceSq > 25.0D * 25.0D) {
            tactic = AirAttack.TACKLE; // Close in when target is far away.
        } else if (distanceSq < 7.0D * 7.0D) {
            tactic = previous == AirAttack.TACKLE
                    ? AirAttack.HOVER_BLAST : AirAttack.TACKLE;
        } else if (distanceSq < 13.0D * 13.0D) {
            tactic = dragon.getRandom().nextInt(3) == 0
                    ? AirAttack.TACKLE : AirAttack.HOVER_BLAST;
        } else {
            tactic = dragon.getRandom().nextBoolean()
                    ? AirAttack.SCORCH_STREAM : AirAttack.HOVER_BLAST;
        }
        nextTacticTick = combatTicks + 75 + dragon.getRandom().nextInt(75);
        nextWaypointTick = 0;
        waypoint = null;
        streamDirection = null;
        streamLeg = 0;
        tackleBreakaway = null;
        tackleBreakawayUntil = 0;
    }

    private void tickEnemy(ServerLevel level, ServerPlayer owner, LivingEntity enemy) {
        if (tactic == null || combatTicks >= nextTacticTick) chooseTactic(enemy);
        Vec3 center = enemy.getBoundingBox().getCenter();
        Vec3 next;
        switch (tactic) {
            case TACKLE -> {
                // Original CE TACKLE homes directly to the prey and hits on contact.
                next = center.add(0, enemy.getBbHeight() * 0.3D, 0);
                fireAim = null;
                if (meleeCooldown > 0) --meleeCooldown;
                double reach = (dragon.getBbWidth() + enemy.getBbWidth() + 0.6D);
                if (meleeCooldown == 0 && dragon.distanceToSqr(enemy) <= reach * reach
                        && dragon.hasLineOfSight(enemy) && canMarkTarget(enemy)) {
                    // CE TACKLE must complete a physical contact hit, then
                    // create space for another dive instead of circling in place.
                    boolean landedHit = dragon.doHurtTarget(enemy);
                    meleeCooldown = 20;
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
                if (waypoint == null || combatTicks >= nextWaypointTick
                        || dragon.position().distanceToSqr(waypoint) < 16.0D) {
                    Vec3 chosen = hoverPoint(level, enemy);
                    if (chosen != null) waypoint = chosen;
                    nextWaypointTick = combatTicks + 45 + dragon.getRandom().nextInt(30);
                }
                next = waypoint;
                fireAim = dragon.hasLineOfSight(enemy) ? center : null;
            }
            case SCORCH_STREAM -> {
                // CE reverses its streaming pass near the opposite side.
                if (waypoint == null || dragon.position().distanceToSqr(waypoint) < 100.0D
                        || combatTicks >= nextWaypointTick) {
                    if (waypoint != null) ++streamLeg;
                    waypoint = streamPoint(level, enemy);
                    nextWaypointTick = combatTicks + 110;
                }
                next = waypoint;
                fireAim = dragon.hasLineOfSight(enemy) ? center : null;
            }
            default -> { return; }
        }
        if (next == null || !canFlyTo(level, next)) {
            // If the target is standing inside terrain, CE can still aim
            // towards it for tackle, but avoid pushing unloaded waypoints.
            if (tactic == AirAttack.TACKLE && next != null
                    && level.hasChunkAt(BlockPos.containing(next))) {
                dragon.getFlightController().patrolAttack(owner, next, true);
            } else {
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
                Vec3 start = center.subtract(direction.scale(radius * 0.92D));
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
        for (int distance = 22; distance >= 7; distance -= 3) {
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
