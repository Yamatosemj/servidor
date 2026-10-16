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
    private int nextHeadingFlipLeg;
    private int tackleBreakawayUntil;
    private Vec3 tackleBreakaway;
    private int streamLeg;
    private boolean clockwise = true;
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
        nextHeadingFlipLeg = 4 + dragon.getRandom().nextInt(4);
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

    private void chooseTactic(LivingEntity enemy) {
        AirAttack previous = tactic;
        AirAttack[] choices = AirAttack.values();
        // CE randomizeAttacks() chooses from these same three air attacks.
        // Avoid repeating a maneuver back-to-back for more varied combat.
        tactic = choices[dragon.getRandom().nextInt(choices.length)];
        if (tactic == previous) tactic = choices[(tactic.ordinal() + 1) % choices.length];
        nextTacticTick = combatTicks + 140 + dragon.getRandom().nextInt(100);
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
                fireAim = center;
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
                fireAim = center;
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
     * Attack AREA uses distinct flight legs, not a continuously rotating
     * fixed-radius ring: banked turns, a pass across the center, low sweeps
     * and high repositioning. The original CE FlightMoveHelper steers each
     * leg, while the server picks safe, loaded destinations.
     */
    private void tickArea(ServerLevel level, ServerPlayer owner, Vec3 center) {
        if (waypoint == null || combatTicks >= nextWaypointTick
                || dragon.position().distanceToSqr(waypoint) < 30.0D) {
            Vec3 candidate = null;
            double bearing = Math.atan2(dragon.getZ() - center.z,
                    dragon.getX() - center.x);
            if (areaLegs >= nextHeadingFlipLeg) {
                clockwise = !clockwise;
                nextHeadingFlipLeg = areaLegs + 4 + dragon.getRandom().nextInt(4);
            }
            int legStyle = areaLegs % 5;
            for (int i = 0; i < 12; ++i) {
                double angle;
                double range;
                double height;
                if (legStyle == 2) {
                    // CROSS_PASS: head through the center toward the far edge.
                    angle = bearing + Math.PI
                            + (dragon.getRandom().nextDouble() - 0.5D) * 0.7D;
                    range = radius * (0.65D + dragon.getRandom().nextDouble() * 0.28D);
                    height = 8.0D + dragon.getRandom().nextDouble() * 4.0D;
                } else if (legStyle == 3) {
                    // LOW_SWEEP: an inner, descending arc; fire crosses the ring.
                    angle = bearing + (clockwise ? 1.0D : -1.0D);
                    range = radius * (0.25D + dragon.getRandom().nextDouble() * 0.35D);
                    height = 5.5D + dragon.getRandom().nextDouble() * 3.0D;
                } else if (legStyle == 4) {
                    // CLIMB: recover altitude and set up a new attack pass.
                    angle = bearing + (clockwise ? 1.5D : -1.5D);
                    range = radius * (0.60D + dragon.getRandom().nextDouble() * 0.33D);
                    height = 11.0D + dragon.getRandom().nextDouble() * 4.0D;
                } else {
                    // BANKED_ARC: lateral leg with bounded, varied turn angle.
                    double step = 0.55D + dragon.getRandom().nextDouble() * 0.65D;
                    angle = bearing + (clockwise ? step : -step);
                    range = radius * (0.55D + dragon.getRandom().nextDouble() * 0.40D);
                    height = 7.5D + dragon.getRandom().nextDouble() * 4.5D;
                }
                Vec3 point = center.add(Math.cos(angle) * range, height,
                        Math.sin(angle) * range);
                if (canFlyTo(level, point)) { candidate = point; break; }
            }
            if (candidate != null) {
                waypoint = candidate;
                ++areaLegs;
                nextWaypointTick = combatTicks + 65 + dragon.getRandom().nextInt(50);
            }
        }
        if (waypoint == null || !level.hasChunkAt(BlockPos.containing(waypoint))
                || !dragon.getFlightController().patrol(owner, waypoint)) {
            fireAim = null;
            dragon.getFlightController().land();
            return;
        }

        // Distinct fire impacts stay inside the marked circle; the dragon
        // may pass through the interior but does not target outside it.
        if (fireAim == null || combatTicks >= nextSweepTick) {
            double angle = dragon.getRandom().nextDouble() * 2.0D * Math.PI;
            double distance = radius * 0.80D * Math.sqrt(dragon.getRandom().nextDouble());
            fireAim = center.add(Math.cos(angle) * distance, 0,
                    Math.sin(angle) * distance);
            nextSweepTick = combatTicks + 35 + dragon.getRandom().nextInt(35);
        }
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
