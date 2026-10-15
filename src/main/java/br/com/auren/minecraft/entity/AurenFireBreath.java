/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Focused port of IceAndFire Community Edition 1.21.1:
 * FireDragonEntity.riderShootFire (continuous-breath branch),
 * DragonBaseEntity.rayTraceRider / performNormalBreathAttack,
 * IafDragonDestructionManager.destroyAreaBreath (stage-3 FIRE effects).
 * Upstream: IAFEnvoy/IceAndFire-CE, commit
 * aa2f11a6b79acb48a1094765d610cdaca79e3d18 (LGPL-3.0-or-later).
 *
 * Auren is not a DragonBaseEntity, so CE dragon-forge, charred blocks,
 * fire-charge entity and custom particles/sounds are not imported. Vanilla
 * flame particles, a sound and fire blocks substitute only those assets.
 */
package br.com.auren.minecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-only controlled fire breath; never accepts client coordinates.
 * Holding CE's strike bit triggers the FireDragon's normal breath branch.
 */
public final class AurenFireBreath {
    private static final int CE_STAGE = 3;
    private static final int CE_WARMUP_TICKS = 20;
    private static final int CE_BURN_PROGRESS_MAX = 40;
    private static final double CE_REACH = 10.0D * CE_STAGE;
    private static final double CE_IMPACT_RADIUS = 3.5D;
    private static final float CE_FIRE_DAMAGE_SCALE = 2.0F;
    private static final int CE_IGNITE_SECONDS = 5 + CE_STAGE * 5;
    private static final float CE_RENDER_SIZE = 3.25F / 0.35F;

    private final AurenDragon dragon;
    private int fireBreathTicks;
    private int burnProgress;

    public AurenFireBreath(AurenDragon dragon) {
        this.dragon = dragon;
    }

    public void reset() {
        this.fireBreathTicks = 0;
        this.burnProgress = 0;
    }

    public void tick() {
        if (!(dragon.level() instanceof ServerLevel level)
                || !dragon.isAlive() || dragon.isSmallForm() || dragon.isOnShoulder()
                || !dragon.isRiderFiring() || dragon.isRiderLightning()
                || !(dragon.getControllingPassenger() instanceof Player rider)
                || !dragon.isOwnedBy(rider)) {
            reset();
            return;
        }

        // CE DragonBaseEntity.isActuallyBreathingFire = fireBreathTicks > 20.
        this.fireBreathTicks++;
        if (this.fireBreathTicks <= CE_WARMUP_TICKS) return;

        if (dragon.tickCount % 5 == 0) {
            level.playSound(null, dragon.blockPosition(), SoundEvents.BLAZE_SHOOT,
                    dragon.getSoundSource(), 1.4F, 0.75F);
        }

        // Original CE rayTraceRider: controller's eye and view, 10*stage range.
        Vec3 eye = rider.getEyePosition(1.0F);
        Vec3 endpoint = eye.add(rider.getViewVector(1.0F).scale(CE_REACH));
        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, endpoint, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon));
        Vec3 burnTarget = blockHit.getType() == HitResult.Type.MISS
                ? endpoint : blockHit.getLocation();
        BlockHitResult impactBlock = blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit : null;

        // The upstream CE block-only ray can pass through a mob in open sky.
        // Use vanilla ProjectileUtil to select the first hostile/non-allied
        // entity intersecting the sight ray, then apply CE area-breath there.
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                level, dragon, eye, burnTarget, new AABB(eye, burnTarget).inflate(1.0D),
                entity -> entity instanceof LivingEntity target && target.isAlive()
                        && target != rider && target != dragon
                        && !dragon.isAlliedTo(target)
                        && !(target instanceof TamableAnimal tame
                        && tame.isTame() && tame.getOwnerUUID() != null
                        && tame.getOwnerUUID().equals(dragon.getOwnerUUID())));
        if (entityHit != null) {
            burnTarget = entityHit.getLocation();
            impactBlock = null;
        }

        Vec3 head = getHeadPosition();
        // Do not allow a mouth behind a wall to breathe through solid blocks.
        BlockHitResult mouthBlock = level.clip(new ClipContext(
                head, burnTarget, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon));
        if (mouthBlock.getType() == HitResult.Type.BLOCK) {
            burnTarget = mouthBlock.getLocation();
            impactBlock = mouthBlock;
        }
        if (!level.hasChunkAt(BlockPos.containing(burnTarget))) return;

        // CE's burnProgress / 40 regulates the stream's visual reach.
        this.burnProgress = Math.min(CE_BURN_PROGRESS_MAX, this.burnProgress + 2);
        double progress = this.burnProgress / (double) CE_BURN_PROGRESS_MAX;
        Vec3 streamEnd = head.lerp(burnTarget, progress);
        for (int i = 1; i <= 12; i++) {
            Vec3 particlePos = head.lerp(streamEnd, i / 12.0D);
            if (!level.hasChunkAt(BlockPos.containing(particlePos))) break;
            level.sendParticles(ParticleTypes.FLAME, particlePos.x, particlePos.y,
                    particlePos.z, 1, 0.13D, 0.1D, 0.13D, 0.015D);
        }

        // CE destruction manager applies a stage-scaled area breath at the
        // impact. Auren throttles hit ticks for compatibility on Zalith.
        if (this.burnProgress == CE_BURN_PROGRESS_MAX && dragon.tickCount % 5 == 0) {
            applyBreathImpact(level, rider, streamEnd, impactBlock);
        }
    }

    /**
     * Adapted from CE DragonBaseEntity.getHeadPosition, using stage-3 size
     * and Auren's synced flight blend (no custom mouth-part entity exists).
     * The muzzle can be calibrated against the animated head in playtests.
     */
    Vec3 getHeadPosition() {
        float flyProg = dragon.getFlightBlend(1.0F) * 0.1F;
        float hoverProg = dragon.getFlightMode() == AurenFlightController.TAKING_OFF
                ? dragon.getFlightBlend(1.0F) * 0.03F : 0.0F;
        float dragonPitch = -dragon.getRiderVisualPitch(1.0F);
        float pitchMulti = 0.0F;
        float pitchAdjustment = 0.0F;
        float pitchMinus = 0.0F;
        if (dragon.getFlightMode() != AurenFlightController.GROUNDED) {
            pitchMulti = Mth.sin((float) Math.toRadians(dragonPitch));
            pitchAdjustment = 1.2F;
            pitchMulti *= 2.1F * Math.abs(dragonPitch) / 90.0F;
            if (pitchMulti > 0.0F) pitchMulti *= 1.5F - pitchMulti * 0.5F;
            if (pitchMulti < 0.0F) pitchMulti *= 1.3F - pitchMulti * 0.1F;
            pitchMinus = 0.3F * Math.abs(dragonPitch / 90.0F);
            if (dragonPitch >= 0.0F) {
                pitchAdjustment = 0.6F * Math.abs(dragonPitch / 90.0F);
                pitchMinus = 0.95F * Math.abs(dragonPitch / 90.0F);
            }
        }
        float flightXz = 1.0F + flyProg + hoverProg;
        float xzMod = (1.7F * CE_RENDER_SIZE * 0.3F * flightXz)
                + CE_RENDER_SIZE * (0.3F * Mth.sin((float)
                        ((dragonPitch + 90.0F) * Math.PI / 180.0F))
                * pitchAdjustment - pitchMinus - hoverProg * 0.45F);
        float angle = (float) ((dragon.getYRot() + 90.0F) * Math.PI / 180.0F);
        return new Vec3(
                dragon.getX() + xzMod * Mth.cos(angle),
                dragon.getY() + (0.7F + hoverProg + flyProg + pitchMulti)
                        * CE_RENDER_SIZE * 0.3F,
                dragon.getZ() + xzMod * Mth.sin(angle));
    }

    /** Stage-3 FireDragon destruction manager, without non-bundled CE blocks. */
    private void applyBreathImpact(ServerLevel level, Player rider,
                                   Vec3 center, BlockHitResult blockHit) {
        // CE attackDamageFire default=2, stage=3: 6 damage per hit.
        float damage = CE_STAGE * CE_FIRE_DAMAGE_SCALE;
        AABB bounds = new AABB(center, center).inflate(CE_IMPACT_RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds)) {
            if (target == dragon || target == rider || !target.isAlive()) continue;
            if (target instanceof TamableAnimal tame && tame.isTame()
                    && tame.getOwnerUUID() != null
                    && tame.getOwnerUUID().equals(dragon.getOwnerUUID())) continue;
            if (dragon.isAlliedTo(target) || !dragon.hasLineOfSight(target)) continue;
            if (target.hurt(level.damageSources().indirectMagic(dragon, rider), damage)) {
                target.igniteForSeconds(CE_IGNITE_SECONDS);
            }
        }

        // CE breath ignites terrain only where mobGriefing permits. Do not
        // transplant the mod-specific ash/charred blocks or destroy terrain.
        if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK
                && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                && dragon.getRandom().nextBoolean()) {
            BlockPos firePos = blockHit.getBlockPos().relative(blockHit.getDirection());
            if (level.hasChunkAt(firePos) && level.isEmptyBlock(firePos)
                    && Blocks.FIRE.defaultBlockState().canSurvive(level, firePos)) {
                level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
            }
        }
    }
}
