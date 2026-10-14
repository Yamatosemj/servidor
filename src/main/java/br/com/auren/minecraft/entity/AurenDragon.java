package br.com.auren.minecraft.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class AurenDragon extends TamableAnimal {
    private static final EntityDataAccessor<Boolean> SMALL =
            SynchedEntityData.defineId(AurenDragon.class, EntityDataSerializers.BOOLEAN);

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
    }

    public boolean isSmallForm() {
        return this.entityData.get(SMALL);
    }

    public void setSmallForm(boolean small) {
        this.entityData.set(SMALL, small);
        this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
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
                } else if (this.isOwnedBy(player)) {
                    if (player.isShiftKeyDown()) {
                        if (this.isPassenger()) this.stopRiding();
                        this.setSmallForm(!this.isSmallForm());
                    } else if (this.isSmallForm() && !this.isPassenger()) {
                        this.startRiding(player, true);
                    }
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void rideTick() {
        super.rideTick();
        Entity vehicle = this.getVehicle();
        if (vehicle instanceof Player player && this.isSmallForm()) {
            if (player.isShiftKeyDown() && !this.level().isClientSide()) {
                this.stopRiding();
                return;
            }
            // Small dragon rides near the player's shoulder, not their feet.
            double angle = Math.toRadians(player.yBodyRot);
            double x = player.getX() + Math.cos(angle) * 0.35;
            double z = player.getZ() + Math.sin(angle) * 0.35;
            double y = player.getY() + player.getBbHeight() * 0.81;
            this.setPos(x, y, z);
            this.setYRot(player.yBodyRot);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("SmallForm", this.isSmallForm());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setSmallForm(tag.getBoolean("SmallForm"));
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob parent) {
        return null; // Auren is unique; no breeding.
    }
}
