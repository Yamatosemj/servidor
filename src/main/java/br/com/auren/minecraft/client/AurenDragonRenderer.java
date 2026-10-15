package br.com.auren.minecraft.client;

import br.com.auren.minecraft.AurenMinecraft;
import br.com.auren.minecraft.entity.AurenDragon;
import com.iafenvoy.uranus.client.model.TabulaModel;
import com.iafenvoy.uranus.client.model.util.TabulaModelHandlerHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import br.com.auren.minecraft.client.lightning.LightningRenderer;
import br.com.auren.minecraft.client.lightning.LightningBoltData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class AurenDragonRenderer extends MobRenderer<AurenDragon, TabulaModel<AurenDragon>> {
    private static final ResourceLocation ADULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID, "textures/entity/auren_black_5.png");
    private static final ResourceLocation BABY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID, "textures/entity/auren_black_1.png");

    // Awake original CE pose for neck/head, folded CE sleeping WING JOINTS
    // only. The sleeping dragon's torso/head pose is deliberately NOT used.
    private static final ResourceLocation SHOULDER_AWAKE_POSE =
            ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID,
                    "lightningdragon/lightningdragon_sitting_shoulder");
    private static final ResourceLocation FOLDED_WINGS_POSE =
            ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID,
                    "lightningdragon/lightningdragon_sleeping");

    private final TabulaModel<AurenDragon> groundModel;
    // Original IceAndFire CE LightningDragonEntityRenderer uses this SAME
    // source code to draw the continuous bolt, not a placeholder particle.
    private final LightningRenderer lightningRenderer = new LightningRenderer();

    public AurenDragonRenderer(EntityRendererProvider.Context context) {
        super(context, load("lightningdragon/lightningdragon_ground"), 1.5F);
        this.groundModel = this.model;
        // Original CE draws the rider in the animated dragon model's bone
        // hierarchy, not as an independently positioned vanilla passenger.
        this.addLayer(new AurenRiderFeatureRenderer(this));
        // Cache two immutable source poses; no invented wing animation.
        TabulaModelHandlerHelper.getModel(SHOULDER_AWAKE_POSE);
        TabulaModelHandlerHelper.getModel(FOLDED_WINGS_POSE);
    }

    private static TabulaModel<AurenDragon> load(String modelName) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID, modelName);
        TabulaModel<AurenDragon> result = TabulaModelHandlerHelper.getModel(id,
                () -> AurenDragonRenderer::animate);
        if (result == null) {
            throw new IllegalStateException("Could not load Tabula model " + id
                    + " — verify Uranus and the generated CE assets");
        }
        return result;
    }

    /**
     * Awake CE shoulder pose + original CE sleeping wing joints + corrected
     * neck/tail wrap + CE idle. Does not affect the adult or normal walking.
     */
    private static void animate(TabulaModel<AurenDragon> model, AurenDragon dragon,
                                float limbSwing, float limbSwingAmount,
                                float age, float yaw, float pitch, float scale) {
        model.resetToDefaultPose();
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float shoulderBlend = dragon.getShoulderBlend(partialTick);

        if (shoulderBlend > 0.0F && dragon.isSmallForm()) {
            // Preserve awake head and neck from original CE shoulder pose.
            TabulaModel<AurenDragon> awake =
                    TabulaModelHandlerHelper.getModel(SHOULDER_AWAKE_POSE);
            AurenPoseTransition.apply(model, awake, shoulderBlend);

            // Restores the old living-scarf neck/tail concept, but NEVER
            // rotates the entire model 90 degrees (old sideways bug).
            AurenNeckPose.apply(model, shoulderBlend);

            // ONLY the wing joints inherit the original folded CE rest pose.
            TabulaModel<AurenDragon> folded =
                    TabulaModelHandlerHelper.getModel(FOLDED_WINGS_POSE);
            AurenPoseTransition.applyBones(model, folded, shoulderBlend,
                    "armL1", "armL2", "armR1", "armR2");

            // Keep original CE continuous idle: neck/tail/breathing.
            AurenOriginalShoulderIdle.apply(model, age, shoulderBlend);
            return;
        }

        // The original CE Tabula walk and flight frames, not hand-crafted
        // joint swings, are the source of animation outside shoulder mode.
        AurenOriginalAnimations.instance().apply(model, dragon, limbSwing, limbSwingAmount, age);
    }

    @Override
    public void render(AurenDragon entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        // No instant switching of models: the CE bone pose now blends in/out.
        this.model = groundModel;
        this.shadowRadius = entity.isOnShoulder() ? 0.0F : entity.isSmallForm() ? 0.2F : 1.7F;
        super.render(entity, yaw, partialTick, pose, buffer, packedLight);
        if (entity.hasLightningTarget()) {
            // Faithful CE rendering: BoltRenderInfo.ELECTRICITY, 15 segments,
            // 4-tick lifespan, NO_DELAY, RenderType.lightning().
            Vec3 mouth = entity.getFireBreathHeadPosition();
            Vec3 target = entity.getLightningTarget();
            float energyScale = 0.4F * 3.25F;
            float boundedScale = 0.5F + energyScale * (2.0F - 0.5F);
            LightningBoltData bolt = new LightningBoltData(
                    LightningBoltData.BoltRenderInfo.ELECTRICITY,
                    mouth, target, 15)
                    .size(0.05F * boundedScale)
                    .lifespan(4)
                    .spawn(LightningBoltData.SpawnFunction.NO_DELAY);
            this.lightningRenderer.update(null, bolt, partialTick);
            pose.pushPose();
            try {
                pose.translate(-entity.getX(), -entity.getY(), -entity.getZ());
                this.lightningRenderer.render(partialTick, pose, buffer);
            } finally {
                pose.popPose();
            }
        }
    }

    @Override
    protected void scale(AurenDragon entity, PoseStack pose, float partialTick) {
        // Model yaw follows player's torso. Do NOT rotate the whole dragon
        // 90 degrees like v0.4: curl only its neck and tail bones.
        // Keep the original baby size; adult now approximates a CE stage-3 dragon.
        // CE DragonBaseEntityRenderer.scale rotates the dragon around X by
        // interpolated prevDragonPitch. Only mounted Auren uses this port;
        // autonomous v0.6.2 rendering remains unchanged.
        if (entity.isVehicle()) {
            pose.mulPose(Axis.XP.rotationDegrees(entity.getRiderVisualPitch(partialTick)));
        }
        float factor = entity.isSmallForm() ? 0.42F : 3.25F;
        pose.scale(factor, factor, factor);
    }

    @Override
    public ResourceLocation getTextureLocation(AurenDragon dragon) {
        return dragon.isSmallForm() ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
