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
    private boolean compact;
    private boolean pendingRestore;
    private BlockPos lastCaveAttempt;
    private Vec3 fireAim;

    AurenRelentlessHunt(AurenDragon dragon) {
        this.dragon = dragon;
    }

    boolean isCompact() { return compact; }
    Vec3 getFireAim() { return fireAim; }

    void inspectAerialRoute(boolean canSeeFromSomewhere) {
        routeChecked = true;
        noAerialRoute = !canSeeFromSomewhere;
    }

    void save(CompoundTag tag) {
        tag.putBoolean("AurenHuntTemporarySmall", compact || pendingRestore);
    }

    void load(CompoundTag tag) {
        compact = tag.getBoolean("AurenHuntTemporarySmall") && dragon.isSmallForm();
        pendingRestore = compact;
        // Intentional reset: revalidate the environment after world loading,
        // including ownership and path availability, before any siege shot.
        blockedTicks = 0;
        nextSiegeTick = 0;
        siegeCharges = 0;
        routeChecked = false;
        noAerialRoute = false;
        attemptedCave = false;
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
        lastCaveAttempt = null;
        fireAim = null;
        tryRestore();
    }

    void tickPassive() {
        if (pendingRestore && !compact) tryRestore();
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
    }

    /** The target must actually have thick terrain above its head. */
    private boolean shelteredUnderground(ServerLevel level, LivingEntity target) {
        BlockPos column = target.blockPosition();
        if (!level.hasChunkAt(column)) return false;
        int roof = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).getY();
        return roof >= target.getEyeY() + 6.0D;
    }

    private boolean startCavePursuit(ServerLevel level, LivingEntity enemy) {
        if (compact || attemptedCave || dragon.isSmallForm()
                || dragon.isVehicle() || dragon.isOnShoulder()
                || dragon.distanceToSqr(enemy) > 24.0D * 24.0D) return false;
        if (!shelteredUnderground(level, enemy)) return false;
        if (dragon.getFlightMode() != AurenFlightController.GROUNDED) {
            // Do not instantly shrink midair; let the CE flight adapter land.
            dragon.getFlightController().land();
            return true;
        }
        if (!dragon.onGround()) return true;
        attemptedCave = true;
        lastCaveAttempt = enemy.blockPosition();
        dragon.setSmallForm(true);
        Path route = dragon.getNavigation().createPath(enemy, 0);
        if (route == null || !route.canReach()) {
            pendingRestore = true;
            tryRestore();
            return false;
        }
        compact = true;
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
        if (meleeCooldown > 0) --meleeCooldown;
        if (meleeCooldown == 0 && dragon.getBoundingBox().inflate(0.65D)
                .intersects(enemy.getBoundingBox()) && dragon.hasLineOfSight(enemy)) {
            dragon.doHurtTarget(enemy);
            meleeCooldown = 15;
        }
        if (++nextNavigationCheck >= 15) {
            nextNavigationCheck = 0;
            Path path = dragon.getNavigation().createPath(enemy, 0);
            if (path != null && path.canReach()) {
                dragon.getNavigation().moveTo(path, 1.17D);
                failedGroundRoutes = 0;
            } else if (++failedGroundRoutes >= 3) {
                compact = false;
                pendingRestore = true;
                dragon.getNavigation().stop();
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
        if (pendingRestore) return true;
        if (directSight) {
            blockedTicks = 0;
            routeChecked = false;
            noAerialRoute = false;
            attemptedCave = false;
            siegeCharges = 0;
            return false;
        }
        blockedTicks = Math.min(10000, blockedTicks + 1);
        if (lastCaveAttempt != null
                && enemy.blockPosition().distSqr(lastCaveAttempt) > 10.0D) {
            attemptedCave = false;
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
                && startCavePursuit(level, enemy)) return true;

        if (dragon.getFlightMode() != AurenFlightController.FLYING) {
            // Leave standing/small navigation to vanilla/CE; only airborne
            // adult Auren can launch fire-breath or siege shots.
            return false;
        }
        Vec3 strike = wall.getLocation();
        if (dragon.getFireBreathHeadPosition().distanceToSqr(strike)
                > CHARGE_REACH * CHARGE_REACH) return false;
        fireAim = strike;
        // Hold at the obstruction rather than switching randomly to an
        // unreachable point; flame covers wood while heat melts stone.
        Vec3 hold = dragon.position().add(0.0D, 0.12D, 0.0D);
        if (level.noCollision(dragon, dragon.getBoundingBox().move(0, 0.12D, 0)))
            dragon.getFlightController().patrolAttack(owner, hold, false);

        if (blockedTicks >= SIEGE_DELAY && routeChecked && noAerialRoute
                && (!shelteredUnderground(level, enemy) || attemptedCave)
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
                    SoundEvents.GENERIC_EXPLODE, dragon.getSoundSource(), 1.5F, 0.76F);
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
