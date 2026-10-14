package br.com.auren.minecraft.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import com.iafenvoy.uranus.object.entity.pathfinding.raycoms.AdvancedPathNavigate;
import com.iafenvoy.uranus.object.entity.pathfinding.raycoms.PathingStuckHandler;
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
    private static final EntityDataAccessor<Integer> FLIGHT_MODE =
            SynchedEntityData.defineId(AurenDragon.class, EntityDataSerializers.INT);

    // Client-side animation progress, mirroring CE ridingProgress 0..20 ticks.
    // The desired state is synced by SHOULDER, never by sending every frame.
    private static final float SHOULDER_ANIMATION_TICKS = 20.0F;
    private float previousShoulderProgress;
    private float shoulderProgress;
    // Flight animation progress is client-side and interpolated, just like
    // CE's flyProgress and hoverProgress. Flight authority is server-side.
    private static final float FLIGHT_ANIMATION_TICKS = 10.0F;
    private float previousFlightProgress;
    private float flightProgress;
    private final AurenFlightController flightController = new AurenFlightController(this);

    // IceAndFire CE's CONTROL_STATE bits: 0 = jump/climb, 1 = descend.
    // Client sets its local value and forwards it only to the owning server.
    // Never persist transient controls across reloads.
    private byte riderControlState;
    private int riderTakeoffTicks;
    private int riderAirTicks;
    private boolean wasPilotMounted;

    // CE's DragonBaseEntity.switchNavigator(0/1) changes BOTH the path
    // navigator and the MoveControl. Keep the original instances to restore
    // ground walking after landing or emergency teleport.
    private PathNavigation savedGroundNavigation;
    private MoveControl savedGroundMoveControl;

    // A bounded temporary region ticket keeps a tamed ADULT ticking while
    // its online owner moves beyond the simulation distance.
    private final AurenAdultChunkLoader adultChunkLoader = new AurenAdultChunkLoader();

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
        builder.define(FLIGHT_MODE, AurenFlightController.GROUNDED);
    }

    public boolean isSmallForm() {
        return this.entityData.get(SMALL);
    }

    public boolean isOnShoulder() {
        return this.entityData.get(SHOULDER);
    }

    public int getFlightMode() {
        return this.entityData.get(FLIGHT_MODE);
    }

    /** Server-controlled flight state, synced for CE animation selection. */
    void setFlightMode(int mode) {
        if (!this.level().isClientSide()) this.entityData.set(FLIGHT_MODE, mode);
    }

    public AurenFlightController getFlightController() {
        return this.flightController;
    }

    /** Port of CE DragonBaseEntity.getControllingPassenger: only the owner pilots. */
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity passenger = this.getFirstPassenger();
        return passenger instanceof Player player && this.isOwnedBy(player) ? player : null;
    }

    /** Port of CE canAddPassenger, limited to one adult/owner pilot. */
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return !this.isVehicle() && !this.isSmallForm() && !this.isOnShoulder()
                && passenger instanceof Player player && this.isTame()
                && this.isOwnedBy(player);
    }

    public byte getRiderControlState() {
        return this.riderControlState;
    }

    public void setRiderControlState(byte state) {
        this.riderControlState = (byte) (state & 3);
    }

    private boolean riderGoingUp() {
        return (this.riderControlState & 1) != 0;
    }

    private boolean riderGoingDown() {
        return (this.riderControlState & 2) != 0;
    }

    /** CE positionRider, scaled to Auren's single stage-3-sized model. */
    @Override
    public void positionRider(Entity passenger, MoveFunction callback) {
        if (!this.hasPassenger(passenger)) return;
        // CE keeps the rider above the neck/back and turns with the camera.
        // The Auren has a smaller hitbox than the original multipart dragon.
        double seatHeight = this.getBbHeight() * 0.94D;
        callback.accept(passenger, this.getX(), this.getY() + seatHeight, this.getZ());
        if (passenger == this.getControllingPassenger()) {
            this.setYRot(passenger.getYRot());
            this.yBodyRot = this.getYRot();
            this.yHeadRot = passenger.getYHeadRot();
        }
    }

    /**
     * Port of CE DragonBaseEntity.updateRider: hold jump to take off;
     * X/Dragon Down descends. Riding physics is handled in travel().
     * The autonomous CE FlightMoveHelper is deliberately never run for a pilot.
     */
    private void tickMountedRider(LivingEntity rider) {
        this.wasPilotMounted = true;
        this.getNavigation().stop();
        if (this.getFlightMode() == AurenFlightController.GROUNDED) {
            this.setNoGravity(false);
            this.riderAirTicks = 0;
            this.riderTakeoffTicks = riderGoingUp()
                    ? Math.min(40, this.riderTakeoffTicks + 2)
                    : Math.max(0, this.riderTakeoffTicks - 1);
            if (this.riderTakeoffTicks > 20 && this.onGround()
                    && !this.isInWater() && !this.isInLava()
                    && this.level().hasChunkAt(this.blockPosition().above(2))
                    && this.level().noCollision(this,
                            this.getBoundingBox().move(0, 1.3D, 0))) {
                this.setFlightMode(AurenFlightController.TAKING_OFF);
                this.setNoGravity(true);
                this.fallDistance = 0;
                this.setDeltaMovement(this.getDeltaMovement().add(0, 0.29D, 0));
                this.riderTakeoffTicks = 0;
            }
            return;
        }

        this.setNoGravity(true);
        this.fallDistance = 0;
        this.riderAirTicks++;
        if (this.getFlightMode() == AurenFlightController.TAKING_OFF
                && this.riderAirTicks >= 12) {
            this.setFlightMode(AurenFlightController.FLYING);
        }
        // Same CE principle: descending into terrain ends mounted flight.
        if (this.riderAirTicks >= 12 && (this.onGround() || this.verticalCollisionBelow)) {
            this.flightController.reset();
            this.riderTakeoffTicks = 0;
            this.riderAirTicks = 0;
        }
    }

    /**
     * Adaptation of CE DragonBaseEntity.travel (rider movement only).
     * CE ground movement uses rider's vanilla strafe/forward; flying uses
     * moveRelative -> move(MoverType.SELF) -> equal-axis drag 0.9.
     * The vanilla vehicle packet handles movement on the controlling client.
     */
    @Override
    public void travel(Vec3 input) {
        LivingEntity rider = this.getControllingPassenger();
        if (rider == null) {
            super.travel(input);
            return;
        }

        this.setYRot(rider.getYRot());
        this.yBodyRot = this.getYRot();
        this.yHeadRot = rider.getYHeadRot();

        if (this.getFlightMode() == AurenFlightController.TAKING_OFF
                || this.getFlightMode() == AurenFlightController.FLYING) {
            double forward = rider.zza;
            double strafe = rider.xxa * 0.4D;
            double vertical = 0.0D;
            float speed = (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * 5.2F;

            // CE's "mouse-controlled yaw and pitch" when sprint-gliding.
            // Normal flight uses up/down control bits from the original packet.
            if (rider.isSprinting() && forward > 0.0D) {
                speed *= 1.5F;
                strafe *= 0.25D;
                float pitchRadians = rider.getXRot() * ((float) Math.PI / 180F);
                vertical = -Mth.sin(pitchRadians);
                forward *= Mth.abs(Mth.cos(pitchRadians));
            }
            if (riderGoingUp() && !riderGoingDown()) vertical = 1.0D;
            else if (riderGoingDown() && !riderGoingUp()) vertical = -1.0D;
            else if (riderGoingUp()) vertical = 0.0D;

            if (this.isControlledByLocalInstance()) {
                float flyingSpeed = speed * 0.1F;
                this.setSpeed(flyingSpeed);
                this.moveRelative(flyingSpeed, new Vec3(strafe, vertical, forward));
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
                if (this.horizontalCollision) {
                    Vec3 motion = this.getDeltaMovement();
                    this.setDeltaMovement(motion.x, Math.max(motion.y, 0.1D), motion.z);
                }
                this.calculateEntityAnimation(false);
            } else {
                this.setDeltaMovement(Vec3.ZERO);
            }
            this.tryCheckInsideBlocks();
        } else {
            // CE walking branch: same input, strafe reduced, reverse slowed.
            float speed = (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.8F;
            double forward = rider.zza * (rider.zza > 0 ? 1.0D : 0.2D);
            double strafe = rider.xxa * 0.5D;
            if (rider.isSprinting()) forward *= 1.2D;
            if (this.isControlledByLocalInstance()) {
                this.setSpeed(speed);
                super.travel(new Vec3(strafe, input.y, forward));
            } else {
                this.setDeltaMovement(Vec3.ZERO);
            }
            this.tryCheckInsideBlocks();
        }
    }

    /**
     * Adaptation of original CE DragonBaseEntity.switchNavigator(1).
     * AdvancedPathNavigate ships in the existing embedded Uranus dependency.
     * Explicit no-teleport stuck handler: the ONLY teleport is the proven
     * 50-block emergency fallback in AurenAdultFollowOwnerGoal.
     */
    public void useIceAndFireFlightNavigation(MoveControl flightMoveControl) {
        if (this.savedGroundNavigation != null) return;
        this.savedGroundNavigation = this.navigation;
        this.savedGroundMoveControl = this.moveControl;
        this.navigation.stop();
        this.navigation = new AdvancedPathNavigate(this, this.level(),
                AdvancedPathNavigate.MovementType.FLYING,
                Math.max(0.5F, this.getBbWidth()),
                Math.max(0.7F, this.getBbHeight()),
                PathingStuckHandler.createStuckHandler());
        this.navigation.setCanFloat(true);
        this.moveControl = flightMoveControl;
    }

    /** Adaptation of original CE DragonBaseEntity.switchNavigator(0). */
    public void useOriginalGroundNavigation() {
        if (this.savedGroundNavigation == null) return;
        this.navigation.stop();
        this.navigation = this.savedGroundNavigation;
        this.moveControl = this.savedGroundMoveControl;
        this.savedGroundNavigation = null;
        this.savedGroundMoveControl = null;
    }

    public float getFlightBlend(float partialTick) {
        return Mth.clamp(Mth.lerp(partialTick, this.previousFlightProgress,
                this.flightProgress) / FLIGHT_ANIMATION_TICKS, 0.0F, 1.0F);
    }

    /** Blend factor for an interpolated frame: 0 = walking, 1 = resting on neck. */
    public float getShoulderBlend(float partialTick) {
        return Mth.clamp(Mth.lerp(partialTick, this.previousShoulderProgress,
                this.shoulderProgress) / SHOULDER_ANIMATION_TICKS, 0.0F, 1.0F);
    }

    public void setSmallForm(boolean small) {
        if (this.isVehicle()) return;
        if (!small && isOnShoulder()) setOnShoulder(false);
        if (!this.level().isClientSide() && this.getFlightMode() != AurenFlightController.GROUNDED) {
            this.flightController.land();
        }
        this.entityData.set(SMALL, small);
        this.refreshDimensions();
    }

    /** Updates state once; the normal tick keeps its position synchronized. */
    public void setOnShoulder(boolean enabled) {
        if (this.isVehicle()) return;
        if (enabled && !this.isSmallForm()) this.setSmallForm(true);
        if (enabled && !this.level().isClientSide()) this.flightController.reset();
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
                : EntityDimensions.scalable(1.05F, 1.3F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        // One CE-inspired flight system, but SEPARATE comfortable ranges:
        // adult begins at 15, stops at 8; baby begins at 4.5, stops at 1.7.
        // Both use real aerial motion; teleport is a 50+ block last resort.
        this.goalSelector.addGoal(2, new AurenAdultFollowOwnerGoal(this, true));
        this.goalSelector.addGoal(2, new AurenAdultFollowOwnerGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.65D) {
            @Override
            public boolean canUse() {
                return !AurenDragon.this.isVehicle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !AurenDragon.this.isVehicle() && super.canContinueToUse();
            }
        });
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
                    // CE DragonBaseEntity.mobInteract: adult, empty hand,
                    // non-sneaking owner mounts the tame dragon.
                    if (!this.isSmallForm() && !player.isShiftKeyDown()) {
                        if (!this.isVehicle() && !player.isPassenger()) {
                            this.flightController.reset();
                            this.setOrderedToSit(false);
                            this.getNavigation().stop();
                            player.startRiding(this, true);
                            this.riderControlState = 0;
                            this.riderTakeoffTicks = 0;
                            this.riderAirTicks = 0;
                        }
                        return InteractionResult.sidedSuccess(false);
                    }
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
        if (this.level() instanceof ServerLevel serverLevel) {
            this.adultChunkLoader.tick(this, serverLevel);
        }
        // Update synchronized CE-style interpolation for actual flight modes.
        this.previousFlightProgress = this.flightProgress;
        float flightDestination = this.getFlightMode() == AurenFlightController.GROUNDED
                ? 0.0F : FLIGHT_ANIMATION_TICKS;
        this.flightProgress = Mth.clamp(this.flightProgress
                        + (flightDestination > this.flightProgress ? 1.0F
                        : flightDestination < this.flightProgress ? -1.0F : 0.0F),
                0.0F, FLIGHT_ANIMATION_TICKS);
        // Animate both entering and leaving shoulder mode. Kept client-side
        // and derived from the synced state so latency cannot spam packets.
        this.previousShoulderProgress = this.shoulderProgress;
        float destination = this.isOnShoulder() ? SHOULDER_ANIMATION_TICKS : 0.0F;
        this.shoulderProgress = Mth.clamp(this.shoulderProgress
                        + (destination > this.shoulderProgress ? 1.0F
                        : destination < this.shoulderProgress ? -1.0F : 0.0F),
                0.0F, SHOULDER_ANIMATION_TICKS);
        // Server owns takeoff state, client owns ridden movement (CE pattern).
        if (!this.level().isClientSide()) {
            LivingEntity pilot = this.getControllingPassenger();
            if (pilot != null) {
                this.tickMountedRider(pilot);
            } else if (this.wasPilotMounted) {
                // Vanilla dismount in midair: recover gravity and land safely.
                this.wasPilotMounted = false;
                this.riderControlState = 0;
                this.riderTakeoffTicks = 0;
                this.riderAirTicks = 0;
                this.flightController.land();
            }
        }
        super.tick();
        if (!this.level().isClientSide()) this.flightController.tick();
        if (!this.isOnShoulder()) return;
        if (this.getOwner() instanceof Player player && player.isAlive() && player.level() == this.level()) {
            if (!this.level().isClientSide() && player.isShiftKeyDown()) {
                this.setOnShoulder(false);
                return;
            }
            // Back-of-neck anchor restored from v0.4. The previous
            // location was comfortable; the sideways 90-degree rotation
            // was the problem, not the distance from the player.
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
            // Shoulder rider should follow BODY yaw, not player's camera yaw.
            this.yHeadRot = player.yBodyRot;
        } else if (!this.level().isClientSide() && this.getOwnerUUID() == null) {
            // An orphaned shoulder state must never float forever.
            this.setOnShoulder(false);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide()) this.flightController.reset();
        if (this.level() instanceof ServerLevel serverLevel) {
            this.adultChunkLoader.release(this, serverLevel);
        }
        super.remove(reason);
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
        // Flight targets are transient. Safe server reload: return to vanilla
        // gravity instead of persisting a stale midair destination.
        if (!this.level().isClientSide()) this.flightController.reset();
        this.wasPilotMounted = false;
        this.riderControlState = 0;
        this.riderTakeoffTicks = 0;
        this.riderAirTicks = 0;
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
