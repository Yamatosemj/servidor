/*
 * Autonomous marked attacks for the one Auren entity.
 * The CE combat flight patterns HOVER_BLAST and SCORCH_STREAM are used as
 * navigation references; the CE FlightMoveHelper still performs ALL steering.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package br.com.auren.minecraft.entity;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authored orders. No client-supplied positions or entity IDs.
 *
 * AREA remains ordered until /auren parar, even through save/reload. It pauses
 * when owner / required chunks are unavailable. ENEMY pauses if unloaded and
 * automatically clears when the marked mob dies.
 */
public final class AurenAttackOrders {
    private static final int NONE = 0;
    private static final int ENEMY = 1;
    private static final int AREA = 2;
    private static final int MAX_MARK_DISTANCE = 64;
    private final AurenDragon dragon;
    private int mode;
    private UUID markedEnemy;
    private BlockPos areaCenter;
    private int radius;
    private double orbitAngle;
    private int combatTicks;
    private Vec3 fireAim;

    AurenAttackOrders(AurenDragon dragon) {
        this.dragon = dragon;
    }

    public boolean hasOrder() {
        return this.mode != NONE;
    }

    public boolean isAreaOrder() {
        return this.mode == AREA;
    }

    public BlockPos getAreaCenter() {
        return this.areaCenter;
    }

    public int getRadius() {
        return this.radius;
    }

    public Vec3 getFireAim() {
        return this.fireAim;
    }

    public String description() {
        if (mode == AREA && areaCenter != null)
            return "Bombardeio de área (" + radius + " blocos), centro "
                    + areaCenter.toShortString();
        if (mode == ENEMY) return "Caçando inimigo marcado";
        return "Sem ordem de ataque";
    }

    private void begin() {
        this.combatTicks = 0;
        this.orbitAngle = 0.0D;
        this.fireAim = null;
        this.dragon.setTarget(null);
        this.dragon.getNavigation().stop();
    }

    public boolean markEnemy(LivingEntity enemy) {
        if (!(enemy instanceof Enemy) || !enemy.isAlive()
                || enemy.level() != dragon.level() || dragon.isAlliedTo(enemy))
            return false;
        this.mode = ENEMY;
        this.markedEnemy = enemy.getUUID();
        this.areaCenter = null;
        this.radius = 0;
        begin();
        return true;
    }

    public boolean markArea(BlockPos center, int selectedRadius) {
        if (center == null || selectedRadius < 5 || selectedRadius > 30
                || selectedRadius % 5 != 0 || !dragon.level().hasChunkAt(center))
            return false;
        this.mode = AREA;
        this.areaCenter = center.immutable();
        this.markedEnemy = null;
        this.radius = selectedRadius;
        begin();
        return true;
    }

    public void clear() {
        this.mode = NONE;
        this.markedEnemy = null;
        this.areaCenter = null;
        this.radius = 0;
        this.fireAim = null;
        this.combatTicks = 0;
        if (!dragon.level().isClientSide() && !dragon.isVehicle()) {
            dragon.getFlightController().land();
        }
    }

    /** Only online owner and loaded chunks may authorize an active strike. */
    public void tick() {
        this.fireAim = null;
        if (mode == NONE || !(dragon.level() instanceof ServerLevel level)) return;
        // Keep vanilla owner-hurt goal from redirecting a manually marked attack.
        dragon.setTarget(null);
        // Autonomous orders pause during mounted flight without changing
        // the rider's existing CE gravity/flight mode.
        if (dragon.isVehicle()) return;
        if (!(dragon.getOwner() instanceof ServerPlayer owner)
                || !owner.isAlive() || owner.isSpectator() || owner.level() != level
                || !dragon.isAlive() || dragon.isSmallForm()
                || dragon.isOnShoulder() || dragon.isOrderedToSit()) {
            dragon.getFlightController().land();
            return;
        }

        Vec3 center;
        LivingEntity enemy = null;
        if (mode == ENEMY) {
            Entity found = markedEnemy == null ? null : level.getEntity(markedEnemy);
            if (found == null) {
                dragon.getFlightController().land(); // Target unloaded: keep order.
                return;
            }
            if (!(found instanceof LivingEntity living) || !living.isAlive()) {
                clear();
                return;
            }
            if (!(living instanceof Enemy) || dragon.isAlliedTo(living)) {
                clear();
                return;
            }
            enemy = living;
            center = living.getBoundingBox().getCenter();
        } else {
            if (areaCenter == null || !level.hasChunkAt(areaCenter)) {
                dragon.getFlightController().land();
                return;
            }
            center = Vec3.atCenterOf(areaCenter);
        }

        // The order does not force-load chunks. Far-away orders pause safely.
        if (dragon.position().distanceToSqr(center) > MAX_MARK_DISTANCE * MAX_MARK_DISTANCE
                || !level.hasChunkAt(BlockPos.containing(center))) {
            dragon.getFlightController().land();
            return;
        }

        ++combatTicks;
        if (combatTicks == 1) {
            orbitAngle = Math.atan2(dragon.getZ() - center.z, dragon.getX() - center.x);
        }
        // Constant tangential speed, independent of the selected circle radius.
        int orbitRadius = mode == AREA ? radius : 11;
        orbitAngle += 0.27D / orbitRadius;
        double ahead = orbitAngle + 0.55D;
        double altitude = mode == AREA ? 9.0D : 11.0D; // CE stage 3: 5 + 3*2
        Vec3 waypoint = new Vec3(
                center.x + Math.cos(ahead) * orbitRadius,
                Math.min(level.getMaxBuildHeight() - 4, center.y + altitude),
                center.z + Math.sin(ahead) * orbitRadius);

        if (!level.hasChunkAt(BlockPos.containing(waypoint))) {
            dragon.getFlightController().land();
            return;
        }

        if (!dragon.getFlightController().patrol(owner, waypoint)) return;

        if (enemy != null) {
            // CE HOVER_BLAST attacks from above, SCORCH_STREAM passes along
            // the prey. Alternate the two instead of falling back to bite.
            if ((combatTicks / 120) % 2 == 1) {
                double side = ((combatTicks / 60) % 2 == 0) ? 1.0D : -1.0D;
                Vec3 strafe = center.add(
                        Math.cos(ahead) * 12.0D * side, 9.0D,
                        Math.sin(ahead) * 12.0D * side);
                if (level.hasChunkAt(BlockPos.containing(strafe)))
                    dragon.getFlightController().patrol(owner, strafe);
            }
            this.fireAim = center;
        } else {
            // Sweep center/inner/outer rings instead of burning only one spot.
            double coverage = switch ((combatTicks / 60) % 3) {
                case 0 -> 0.0D;
                case 1 -> 0.45D;
                default -> 0.80D;
            };
            Vec3 aim = center.add(
                    Math.cos(orbitAngle - 0.30D) * radius * coverage, 0.0D,
                    Math.sin(orbitAngle - 0.30D) * radius * coverage);
            if (level.hasChunkAt(BlockPos.containing(aim))) this.fireAim = aim;
        }
    }

    public void save(CompoundTag tag) {
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
        // No active fire or stale navigation survives a world reload.
        this.mode = NONE;
        this.markedEnemy = null;
        this.areaCenter = null;
        this.fireAim = null;
        this.combatTicks = 0;
        this.radius = 0;
        int storedMode = tag.getInt("AurenAttackMode");
        if (storedMode == ENEMY && tag.hasUUID("AurenMarkedEnemy")) {
            this.mode = ENEMY;
            this.markedEnemy = tag.getUUID("AurenMarkedEnemy");
        } else if (storedMode == AREA && tag.contains("AurenAttackRadius")
                && tag.contains("AurenAttackX")
                && tag.contains("AurenAttackY") && tag.contains("AurenAttackZ")) {
            int storedRadius = tag.getInt("AurenAttackRadius");
            if (storedRadius >= 5 && storedRadius <= 30 && storedRadius % 5 == 0) {
                this.mode = AREA;
                this.radius = storedRadius;
                this.areaCenter = new BlockPos(tag.getInt("AurenAttackX"),
                        tag.getInt("AurenAttackY"), tag.getInt("AurenAttackZ"));
            }
        }
    }
}
