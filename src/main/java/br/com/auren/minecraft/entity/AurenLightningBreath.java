/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Selected LightningDragonEntity.riderShootFire / performNormalBreathAttack,
 * DragonBaseEntity.rayTraceRider and
 * IafDragonDestructionManager.destroyAreaBreath (LIGHTNING branch).
 * Source: IceAndFire-CE 2.1.4 (NeoForge 1.21.1) at
 * aa2f11a6b79acb48a1094765d610cdaca79e3d18.
 *
 * Not a lightning projectile/charge port: that original CE branch needs a
 * separately registered projectile entity, which Auren does not want.
 * Original continuous breath and original bolt renderer are the scope.
 */
package br.com.auren.minecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server authoritative CE Lightning Dragon stage-3 continuous lightning breath.
 * The client sends only the control bit; server derives target from rider view.
 * Fire remains bound to R, lightning to G and takes priority if both held.
 *
 * No lightning_bolt entity is spawned, hence no thunder storms, random fires,
 * CE block transformations or unexpected additional mob/loot registrations.
 */
public final class AurenLightningBreath {
    private static final int CE_STAGE = 3;
    private static final int CE_WARMUP_TICKS = 20;
    private static final int CE_BURN_PROGRESS_MAX = 40;
    private static final double CE_REACH = 10.0D * CE_STAGE;
    private static final double CE_DAMAGE_RADIUS = 3.5D;
    // IafCommonConfig.dragon.attackDamageLightning default 3.5.
    private static final float CE_LIGHTNING_DAMAGE_SCALE = 3.5F;
    // IafDragonDestructionManager: lightning statusDuration = 3;
    // effect: target.knockback(statusDuration / 10, dx, dz).
    private static final double CE_LIGHTNING_KNOCKBACK = 0.3D;

    private final AurenDragon dragon;
    private int fireBreathTicks;
    private int burnProgress;

    public AurenLightningBreath(AurenDragon dragon) {
        this.dragon = dragon;
    }

    public void reset() {
        this.fireBreathTicks = 0;
        this.burnProgress = 0;
        this.dragon.clearLightningTarget();
    }

    public void tick() {
        if (!(dragon.level() instanceof ServerLevel level)
                || !dragon.isAlive() || dragon.isSmallForm() || dragon.isOnShoulder()
                || !dragon.isRiderLightning()
                || !(dragon.getControllingPassenger() instanceof Player rider)
                || !dragon.isOwnedBy(rider)) {
            reset();
            return;
        }

        // Original CE: isActuallyBreathingFire() after 20 ticks.
        this.fireBreathTicks++;
        if (this.fireBreathTicks <= CE_WARMUP_TICKS) {
            this.dragon.clearLightningTarget();
            return;
        }
        if (this.fireBreathTicks % 7 == 0) {
            level.playSound(null, dragon.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(),
                    dragon.getSoundSource(), 0.8F, 1.5F);
        }

        // Original DragonBaseEntity.rayTraceRider, with server ownership
        // and no client-supplied coordinates. Stage 3: 10*3 = 30 blocks.
        Vec3 eye = rider.getEyePosition(1.0F);
        Vec3 endpoint = eye.add(rider.getViewVector(1.0F).scale(CE_REACH));
        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, endpoint, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon));
        Vec3 target = blockHit.getType() == HitResult.Type.MISS
                ? endpoint : blockHit.getLocation();

        // Prefer the first attackable entity BEFORE the first solid block,
        // not monsters behind structures or the owner's tame animals.
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                level, dragon, eye, target, new AABB(eye, target).inflate(1.0D),
                entity -> entity instanceof LivingEntity living && living.isAlive()
                        && living != rider && living != dragon
                        && !dragon.isAlliedTo(living)
                        && !(living instanceof TamableAnimal tame && tame.isTame()
                            && tame.getOwnerUUID() != null
                            && tame.getOwnerUUID().equals(dragon.getOwnerUUID())));
        if (entityHit != null) target = entityHit.getLocation();

        // Prevent mouth-to-target line clipping through a wall. The CE
        // similarly stops/redirects lightning when a ray is blocked.
        Vec3 mouth = dragon.getFireBreathHeadPosition();
        if (!level.hasChunkAt(BlockPos.containing(mouth))) {
            dragon.clearLightningTarget();
            return;
        }
        BlockHitResult mouthHit = level.clip(new ClipContext(
                mouth, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon));
        if (mouthHit.getType() == HitResult.Type.BLOCK) target = mouthHit.getLocation();
        if (!level.hasChunkAt(BlockPos.containing(target))) {
            dragon.clearLightningTarget();
            return;
        }

        // Original LightningDragonEntity.performNormalBreathAttack uses
        // burnProgress / 40 to grow the active ray. Its lightning renderer
        // draws continuous electricity between muzzle and selected target.
        this.burnProgress = Math.min(CE_BURN_PROGRESS_MAX, this.burnProgress + 2);
        Vec3 currentEnd = mouth.lerp(target,
                this.burnProgress / (double) CE_BURN_PROGRESS_MAX);
        dragon.setLightningTarget(currentEnd);
        if (this.burnProgress >= CE_BURN_PROGRESS_MAX && dragon.tickCount % 5 == 0) {
            applyBreathImpact(level, rider, currentEnd);
        }
    }

    /** Lightning branch of CE destruction manager, without mod-specific blocks. */
    private void applyBreathImpact(ServerLevel level, Player rider, Vec3 center) {
        if (!level.hasChunkAt(BlockPos.containing(center))) return;

        float damage = CE_STAGE * CE_LIGHTNING_DAMAGE_SCALE;
        // Source uses AABB with radius 3.5 around center block.
        AABB bounds = new AABB(center, center).inflate(CE_DAMAGE_RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds)) {
            if (target == dragon || target == rider || !target.isAlive()
                    || target.isSpectator()) continue;
            if (target instanceof TamableAnimal tame && tame.isTame()
                    && tame.getOwnerUUID() != null
                    && tame.getOwnerUUID().equals(dragon.getOwnerUUID())) continue;
            if (dragon.isAlliedTo(target) || !dragon.hasLineOfSight(target)) continue;
            if (target.hurt(level.damageSources().indirectMagic(dragon, rider), damage)) {
                // Identical CE statusDuration / 10 = 3 / 10 knockback.
                target.knockback(CE_LIGHTNING_KNOCKBACK,
                        dragon.getX() - target.getX(), dragon.getZ() - target.getZ());
            }
        }
        // Impact electric particles complement the unchanged CE bolt
        // renderer. These are vanilla assets and need no CE registrations.
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y, center.z, 12, 0.45D, 0.45D, 0.45D, 0.1D);
    }
}
