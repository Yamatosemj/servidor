package br.com.auren.minecraft.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Auren is a single tameable dragon with two cosmetic sizes.
 * Shoulder mode follows the owner position directly, without vanilla player
 * passenger controls; this is intentional to support touch launchers as well.
 */
public final class AurenDragon extends TamableAnimal {
    private static final EntityDataAccessor<Boolean> SMALL =
            SynchedEntityData.defineId(AurenDragon.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHOULDER =
            SynchedEntityData.defineId(AurenDragon.class, EntityDataSerializers.BOOLEAN);

    // Client-side animation progress, mirroring CE ridingProgress 0..20 ticks.
    // The desired state is synced by SHOULDER, never by sending every frame.
    private static final float SHOULDER_ANIMATION_TICKS = 20.0F;
    private float previousShoulderProgress;
    private float shoulderProgress;

    public AurenDragon(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SMALL, false);
        builder.define(SHOULDER, false);
    }

    public boolean isSmallForm() {
        return this.entityData.get(SMALL);
    }

    public boolean isOnShoulder() {
        return this.entityData.get(SHOULDER);
    }

    /** Blend factor for an interpolated frame: 0 = walking, 1 = resting on neck. */
    public float getShoulderBlend(float partialTick) {
        return Mth.clamp(Mth.lerp(partialTick, this.previousShoulderProgress,
                this.shoulderProgress) / SHOULDER_ANIMATION_TICKS, 0.0F, 1.0F);
    }

    public void setSmallForm(boolean small) {
        if (!small && isOnShoulder()) setOnShoulder(false);
        this.entityData.set(SMALL, small);
        this.refreshDimensions();
    }

    /** Updates state once; the normal tick keeps its position synchronized. */
    public void setOnShoulder(boolean enabled) {
        if (enabled && !this.isSmallForm()) this.setSmallForm(true);
        boolean wasOnShoulder = this.isOnShoulder();
        this.entityData.set(SHOULDER, enabled);
        this.setNoAi(enabled);
        this.setNoGravity(enabled);
        this.noPhysics = enabled;
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        if (wasOnShoulder && !enabled && this.getOwner() instanceof Player player) {
            // Move clear of the player to prevent immediately clipping into them.
            this.setPos(player.getX() + 0.9D, player.getY() + 0.1D, player.getZ());
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SMALL.equals(key)) this.refreshDimensions();
        if (SHOULDER.equals(key)) {
            this.noPhysics = this.isOnShoulder();
            this.setNoGravity(this.isOnShoulder());
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return isSmallForm()
                ? EntityDimensions.scalable(0.5F, 0.7F)
                : EntityDimensions.scalable(1.65F, 2.0F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.15D, 4.5F, 1.7F));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.65D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).isEmpty()) {
            if (!this.level().isClientSide()) {
                if (!this.isTame()) {
                    this.tame(player);
                    player.displayClientMessage(Component.literal("Auren: agora sou seu companheiro."), true);
                } else if (this.isOwnedBy(player)) {
                    if (player.isShiftKeyDown()) {
                        if (this.isOnShoulder()) {
                            this.setOnShoulder(false);
                            player.displayClientMessage(Component.literal("Auren saiu do ombro."), true);
                        } else {
                            this.setSmallForm(!this.isSmallForm());
                            player.displayClientMessage(Component.literal(this.isSmallForm()
                                    ? "Auren: forma filhote." : "Auren: forma adulta."), true);
                        }
                    } else if (this.isSmallForm()) {
                        boolean next = !this.isOnShoulder();
                        this.setOnShoulder(next);
                        player.displayClientMessage(Component.literal(next
                                ? "Auren subiu no ombro!" : "Auren saiu do ombro."), true);
                    } else {
                        player.displayClientMessage(Component.literal(
                                "Agache e interaja para transformar o Auren em filhote."), true);
                    }
                } else {
                    player.displayClientMessage(Component.literal("Este Auren pertence a outro jogador."), true);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void tick() {
        // Animate both entering and leaving shoulder mode. Kept client-side
        // and derived from the synced state so latency cannot spam packets.
        this.previousShoulderProgress = this.shoulderProgress;
        float destination = this.isOnShoulder() ? SHOULDER_ANIMATION_TICKS : 0.0F;
        this.shoulderProgress = Mth.clamp(this.shoulderProgress
                        + (destination > this.shoulderProgress ? 1.0F
                        : destination < this.shoulderProgress ? -1.0F : 0.0F),
                0.0F, SHOULDER_ANIMATION_TICKS);
        super.tick();
        if (!this.isOnShoulder()) return;
        if (this.getOwner() instanceof Player player && player.isAlive() && player.level() == this.level()) {
            if (!this.level().isClientSide() && player.isShiftKeyDown()) {
                this.setOnShoulder(false);
                return;
            }
            // Keep the torso behind the neck. The 3D pose curves the head
            // onto the left shoulder and the tail to the right.
            // At yaw 0 (+Z forward), left is +X and behind is -Z.
            double angle = Math.toRadians(player.yBodyRot);
            double lateral = 0.075D;
            double backward = 0.18D;
            double x = player.getX() + Math.cos(angle) * lateral + Math.sin(angle) * backward;
            double z = player.getZ() + Math.sin(angle) * lateral - Math.cos(angle) * backward;
            double y = player.getY() + player.getBbHeight() * 0.76D;
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.noPhysics = true;
            this.setPos(x, y, z);
            this.setYRot(player.yBodyRot);
            this.yBodyRot = player.yBodyRot;
            this.yHeadRot = player.yHeadRot;
        } else if (!this.level().isClientSide() && this.getOwnerUUID() == null) {
            // An orphaned shoulder state must never float forever.
            this.setOnShoulder(false);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("SmallForm", this.isSmallForm());
        tag.putBoolean("OnShoulder", this.isOnShoulder());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setSmallForm(tag.getBoolean("SmallForm"));
        this.setOnShoulder(tag.getBoolean("OnShoulder"));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob parent) {
        return null;
    }
}
