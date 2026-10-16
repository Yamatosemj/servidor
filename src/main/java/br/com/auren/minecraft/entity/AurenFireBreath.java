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
 * blue soul-fire particles and compatible soul-fire blocks substitute those assets.
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
    private int aimGraceTicks;
    private Vec3 previousGroundImpact;
    private int previousGroundImpactTick;

    public AurenFireBreath(AurenDragon dragon) {
        this.dragon = dragon;
    }

    /** Source CE attack progress drives the separate original Tabula pose. */
    int getAttackTicks() {
        return this.fireBreathTicks;
    }

    public void reset() {
        this.fireBreathTicks = 0;
        this.burnProgress = 0;
        this.aimGraceTicks = 0;
        this.previousGroundImpact = null;
        this.previousGroundImpactTick = 0;
    }

    public void tick() {
        // Rider controls remain unchanged. Unmounted orders come ONLY from
        // the server-side AurenAttackOrders, not an untrusted client packet.
        Player rider = dragon.getControllingPassenger() instanceof Player pilot ? pilot : null;
        Player owner = dragon.getOwner() instanceof Player player ? player : null;
        Vec3 autoAim = dragon.getAttackOrders().getFireAim();
        boolean riddenFire = rider != null && dragon.isOwnedBy(rider)
                && dragon.isRiderFiring() && !dragon.isRiderLightning();
        boolean autonomousFire = !dragon.isVehicle() && owner != null
                && owner.isAlive() && owner.level() == dragon.level()
                && autoAim != null && dragon.getFlightMode() == AurenFlightController.FLYING;
        if (!(dragon.level() instanceof ServerLevel level)
                || !dragon.isAlive() || dragon.isSmallForm() || dragon.isOnShoulder()) {
            reset();
            return;
        }
        if (!riddenFire && !autonomousFire) {
            // The previous implementation reset the long stream during every
            // brief roof/heading gap. Keep its charge for a short air-run grace
            // period; only the stream pauses, not the preparation animation.
            boolean briefAreaGap = !dragon.isVehicle() && owner != null
                    && dragon.getAttackOrders().isAreaOrder()
                    && dragon.getFlightMode() == AurenFlightController.FLYING
                    && aimGraceTicks++ < 16;
            if (briefAreaGap) {
                this.fireBreathTicks = Math.max(CE_WARMUP_TICKS, this.fireBreathTicks);
                this.burnProgress = Math.max(0, this.burnProgress - 1);
                return;
            }
            reset();
            return;
        }

        // No angle restriction: the marked target dictates aim, while the
        // original CE pose visibly lowers the head during breath.
        this.aimGraceTicks = 0;

        // CE DragonBaseEntity.isActuallyBreathingFire = fireBreathTicks > 20.
        this.fireBreathTicks++;
        if (this.fireBreathTicks <= CE_WARMUP_TICKS) return;

        if (dragon.tickCount % 5 == 0) {
            level.playSound(null, dragon.blockPosition(), SoundEvents.BLAZE_SHOOT,
                    dragon.getSoundSource(), 1.4F, 0.75F);
        }

        // Original CE rayTraceRider: controller's eye and view, 10*stage range.
        Vec3 eye = riddenFire ? rider.getEyePosition(1.0F) : getHeadPosition();
        Vec3 endpoint = riddenFire
                ? eye.add(rider.getViewVector(1.0F).scale(CE_REACH)) : autoAim;
        // CE stage-3 reach is 30 blocks. Do not artificially extend it for
        // distant order waypoints: orbit until within range before firing.
        if (autonomousFire && eye.distanceToSqr(endpoint) > CE_REACH * CE_REACH) {
            this.burnProgress = Math.max(0, this.burnProgress - 1);
            return;
        }
        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, endpoint, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon));
        Vec3 burnTarget = blockHit.getType() == HitResult.Type.MISS
                ? endpoint : blockHit.getLocation();
        BlockHitResult impactBlock = blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit : null;

        // The upstream CE block-only ray can pass through a mob in open sky.
        // Use vanilla ProjectileUtil to select the first hostile/non-allied
        // entity intersecting the sight ray, then apply CE area-breath there.
        // An area order MUST aim at the marked place, not snap onto an
        // unrelated entity passing across the view ray.
        if (riddenFire || !dragon.getAttackOrders().isAreaOrder()) {
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    level, dragon, eye, burnTarget, new AABB(eye, burnTarget).inflate(1.0D),
                    entity -> entity instanceof LivingEntity target && target.isAlive()
                            && target != rider && target != owner && target != dragon
                            && !dragon.isAlliedTo(target)
                            && !(target instanceof TamableAnimal tame
                            && tame.isTame() && tame.getOwnerUUID() != null
                            && tame.getOwnerUUID().equals(dragon.getOwnerUUID())));
            if (entityHit != null) {
                burnTarget = entityHit.getLocation();
                impactBlock = null;
            }
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
        // Real volumetric BLUE soul-fire: a widening, turbulent cone rather
        // than twelve points with the same 0.13-block spread (laser effect).
        // The DAMAGE / sight ray stays unchanged, only the visual plume changes.
        double plumeLength = head.distanceTo(streamEnd);
        int segments = Math.max(6, Math.min(14, (int) Math.ceil(plumeLength / 2.2D)));
        for (int i = 1; i <= segments; i++) {
            // Stagger the plume samples slightly each tick to avoid a solid line.
            double t = (i - 0.35D + dragon.getRandom().nextDouble() * 0.35D) / segments;
            Vec3 center = head.lerp(streamEnd, Mth.clamp(t, 0.0D, 1.0D));
            if (!level.hasChunkAt(BlockPos.containing(center))) break;
            double width = Math.min(2.3D, 0.20D + plumeLength * t * 0.10D);
            // sendParticles' positive count scatters each blue flame inside the
            // widening cross-section, creating a moving plume, not a ray.
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    center.x, center.y, center.z, 11,
                    width, width * 0.85D, width, 0.055D);
            // Light wisps emphasize heat and motion without orange fire.
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.SMOKE,
                        center.x, center.y, center.z, 1,
                        width * 0.35D, width * 0.35D, width * 0.35D, 0.012D);
            }
        }

        // The head-to-impact stream is FIRE, not a thin light beam. Flames
        // thicken and flare where the column of fire actually meets terrain.
        if (progress > 0.55D) {
            double flare = 0.65D + Math.min(1.2D, plumeLength * 0.035D);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    streamEnd.x, streamEnd.y, streamEnd.z, 13,
                    flare, flare * 0.65D, flare, 0.065D);
        }

        // CE destruction manager applies a stage-scaled area breath at the
        // impact. Auren throttles hit ticks for compatibility on Zalith.
        if (this.burnProgress == CE_BURN_PROGRESS_MAX && dragon.tickCount % 5 == 0) {
            applyBreathImpact(level, riddenFire ? rider : owner, streamEnd, impactBlock,
                    autonomousFire && dragon.getAttackOrders().isAreaOrder());
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
        // Follow the synced original breath pose with the muzzle: its neck
        // and head lower while fire is emitted, not just the particles.
        float neckLower = Mth.clamp((this.fireBreathTicks - 10.0F) / 18.0F,
                0.0F, 1.0F);
        float flightXz = 1.0F + flyProg + hoverProg;
        float xzMod = (1.7F * CE_RENDER_SIZE * 0.3F * flightXz)
                + CE_RENDER_SIZE * (0.3F * Mth.sin((float)
                        ((dragonPitch + 90.0F) * Math.PI / 180.0F))
                * pitchAdjustment - pitchMinus - hoverProg * 0.45F);
        float angle = (float) ((dragon.getYRot() + 90.0F) * Math.PI / 180.0F);
        return new Vec3(
                dragon.getX() + xzMod * Mth.cos(angle),
                dragon.getY() + (0.7F + hoverProg + flyProg + pitchMulti)
                        * CE_RENDER_SIZE * 0.3F - neckLower * 0.65D,
                dragon.getZ() + xzMod * Mth.sin(angle));
    }

    /** Stage-3 FireDragon destruction manager, without non-bundled CE blocks. */
    private void applyBreathImpact(ServerLevel level, Player rider,
                                   Vec3 center, BlockHitResult blockHit,
                                   boolean areaOrder) {
        // CE attackDamageFire default=2, stage=3: 6 damage per hit.
        float damage = CE_STAGE * CE_FIRE_DAMAGE_SCALE;
        AABB bounds = new AABB(center, center).inflate(CE_IMPACT_RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds)) {
            if (target == dragon || target == rider || !target.isAlive()) continue;
            if (areaOrder && !withinArea(target.position())) continue;
            if (target instanceof TamableAnimal tame && tame.isTame()
                    && tame.getOwnerUUID() != null
                    && tame.getOwnerUUID().equals(dragon.getOwnerUUID())) continue;
            if (dragon.isAlliedTo(target) || !dragon.hasLineOfSight(target)) continue;
            if (target.hurt(level.damageSources().indirectMagic(dragon, rider), damage)) {
                target.igniteForSeconds(CE_IGNITE_SECONDS);
            }
        }

        // The impact is a broad, irregular zone of ACTUAL flames. Repeated
        // fire scorches ground and can eventually melt stone or wear away
        // grass/magma. Wooden buildings only burn by ordinary vanilla fire.
        BlockPos scorchCenter = blockHit != null
                ? blockHit.getBlockPos() : BlockPos.containing(center).below();
        // Apply heat to the face the ray ACTUALLY struck, including rock
        // walls and cave ceilings. Keep the proven top-down footprint intact.
        boolean onWall = blockHit != null
                && blockHit.getDirection() != net.minecraft.core.Direction.UP;
        BlockPos areaCenter = areaOrder ? dragon.getAttackOrders().getAreaCenter() : null;
        int areaRadius = areaOrder ? dragon.getAttackOrders().getRadius() : 0;
        if (onWall) {
            AurenScorchTerrain.scorchFace(level, scorchCenter,
                    blockHit.getDirection(), dragon, areaCenter, areaRadius);
        } else {
            AurenScorchTerrain.scorch(level, scorchCenter, dragon,
                    areaCenter, areaRadius);
        }
        AurenScorchTerrain.igniteVillage(level, scorchCenter, dragon,
                areaCenter, areaRadius);

        // A moving fire stream should burn a contiguous, irregular RIBBON
        // across the surface, not isolated identical impact squares.
        // Only join nearby contacts in the same active stream, avoiding a
        // giant teleporting fire line when the target is suddenly retargeted.
        Vec3 thisGroundImpact = Vec3.atCenterOf(scorchCenter);
        if (!onWall && previousGroundImpact != null
                && dragon.tickCount - previousGroundImpactTick <= 10) {
            double horizontal = Math.hypot(
                    thisGroundImpact.x - previousGroundImpact.x,
                    thisGroundImpact.z - previousGroundImpact.z);
            if (horizontal > 1.25D && horizontal < 9.0D) {
                int samples = Math.min(6, (int) Math.ceil(horizontal / 1.6D));
                for (int i = 1; i < samples; i++) {
                    Vec3 sample = previousGroundImpact.lerp(
                            thisGroundImpact, i / (double) samples);
                    BlockPos trail = BlockPos.containing(sample);
                    if (areaOrder) {
                        AurenScorchTerrain.scorch(level, trail, dragon,
                                dragon.getAttackOrders().getAreaCenter(),
                                dragon.getAttackOrders().getRadius());
                    } else {
                        AurenScorchTerrain.scorch(level, trail, dragon);
                    }
                }
            }
        }
        previousGroundImpact = onWall ? null : thisGroundImpact;
        previousGroundImpactTick = dragon.tickCount;

        // Blue SOUL_FIRE persists only where vanilla soul fire can survive.
        // Do not place ordinary red fire or bypass block survival. On other
        // blocks, blue particles + entity burn still work as before.
        // Whitelisted terrain may temporarily turn into CE-textured scorched blocks.
        if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK
                && (!areaOrder || withinArea(blockHit.getLocation()))
                && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                && dragon.getRandom().nextBoolean()) {
            BlockPos firePos = blockHit.getBlockPos().relative(blockHit.getDirection());
            // The hit face may be inside the selected circle while the
            // adjacent placement block is outside it. Bound both positions.
            if ((!areaOrder || withinArea(Vec3.atCenterOf(firePos)))
                    && level.hasChunkAt(firePos) && level.isEmptyBlock(firePos)
                    && Blocks.SOUL_FIRE.defaultBlockState().canSurvive(level, firePos)) {
                level.setBlockAndUpdate(firePos, Blocks.SOUL_FIRE.defaultBlockState());
            }
        }
    }

    private boolean withinArea(Vec3 point) {
        BlockPos block = dragon.getAttackOrders().getAreaCenter();
        int radius = dragon.getAttackOrders().getRadius();
        if (block == null || radius < 5) return false;
        double dx = point.x - (block.getX() + 0.5D);
        double dz = point.z - (block.getZ() + 0.5D);
        return dx * dx + dz * dz <= radius * radius;
    }
}
