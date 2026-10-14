package br.com.auren.minecraft.client;

import br.com.auren.minecraft.AurenMinecraft;
import br.com.auren.minecraft.entity.AurenDragon;
import com.iafenvoy.uranus.client.model.AdvancedModelBox;
import com.iafenvoy.uranus.client.model.TabulaModel;
import com.iafenvoy.uranus.client.model.util.TabulaModelHandlerHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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

    private static final ResourceLocation SHOULDER_POSE =
            ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID,
                    "lightningdragon/lightningdragon_sitting_shoulder");

    private final TabulaModel<AurenDragon> groundModel;

    public AurenDragonRenderer(EntityRendererProvider.Context context) {
        super(context, load("lightningdragon/lightningdragon_ground"), 1.5F);
        this.groundModel = this.model;
        // Load the original CE shoulder pose once as a reference, not a swapped renderer.
        TabulaModelHandlerHelper.getModel(SHOULDER_POSE);
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
     * Lightweight procedural animation for the Tabula skeleton. Resets the
     * original pose each frame; no CE entity classes or animation code needed.
     * This is intentionally NOT the complete Ice and Fire flight/attack animator.
     */
    private static void animate(TabulaModel<AurenDragon> model, AurenDragon dragon,
                                float limbSwing, float limbSwingAmount,
                                float age, float yaw, float pitch, float scale) {
        model.resetToDefaultPose();
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float shoulderBlend = dragon.getShoulderBlend(partialTick);

        // As in IceAndFire CE: animate the SAME Tabula skeleton and blend every
        // bone from its normal pose toward the original SIT_ON_PLAYER_POSE.
        if (shoulderBlend > 0.0F && dragon.isSmallForm()) {
            TabulaModel<AurenDragon> originalPose = TabulaModelHandlerHelper.getModel(SHOULDER_POSE);
            AurenPoseTransition.apply(model, originalPose, shoulderBlend);
            // Our selected "living scarf" pose is an additive adjustment only.
            AurenNeckPose.apply(model, age, shoulderBlend);
            return;
        }

        float idle = (float) Math.sin(age * 0.115F);
        float movement = dragon.isOnShoulder() ? 0F : Math.min(1F, limbSwingAmount * 2.5F);
        float stride = (float) Math.sin(limbSwing * 0.68F) * movement;

        // All named bones come from the Lightning Dragon Tabula file.
        AdvancedModelBox body = model.getCube("BodyUpper");
        if (body != null) body.rotationPointY += idle * 0.075F;

        AdvancedModelBox neck = model.getCube("Neck1");
        if (neck != null) neck.rotateAngleX += idle * 0.026F;
        AdvancedModelBox head = model.getCube("Head");
        if (head != null) {
            head.rotateAngleY += clamp(yaw * (float) Math.PI / 180F * 0.35F, -0.35F, 0.35F);
            head.rotateAngleX += clamp(pitch * (float) Math.PI / 180F * 0.35F, -0.24F, 0.24F);
        }

        for (int i = 1; i <= 4; i++) {
            AdvancedModelBox tail = model.getCube("Tail" + i);
            if (tail != null) tail.rotateAngleY += (float) Math.sin(age * 0.10F - i * 0.65F)
                    * (0.025F + movement * 0.042F);
        }

        if (!dragon.isOnShoulder()) {
            rotateX(model, "ThighL", stride * 0.24F);
            rotateX(model, "ThighR", -stride * 0.24F);
            rotateX(model, "armL1", -stride * 0.14F);
            rotateX(model, "armR1", stride * 0.14F);
            // The shoulder/wing roots have very subtle breathing motion.
            float wingBreath = idle * (0.018F + movement * 0.02F);
            rotateZ(model, "armL1", wingBreath);
            rotateZ(model, "armR1", -wingBreath);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void rotateX(TabulaModel<AurenDragon> model, String name, float amount) {
        AdvancedModelBox part = model.getCube(name);
        if (part != null) part.rotateAngleX += amount;
    }

    private static void rotateZ(TabulaModel<AurenDragon> model, String name, float amount) {
        AdvancedModelBox part = model.getCube(name);
        if (part != null) part.rotateAngleZ += amount;
    }

    @Override
    public void render(AurenDragon entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        // No instant switching of models: the CE bone pose now blends in/out.
        this.model = groundModel;
        this.shadowRadius = entity.isOnShoulder() ? 0.0F : entity.isSmallForm() ? 0.2F : 1.7F;
        super.render(entity, yaw, partialTick, pose, buffer, packedLight);
    }

    @Override
    protected void scale(AurenDragon entity, PoseStack pose, float partialTick) {
        float blend = entity.getShoulderBlend(partialTick);
        if (entity.isSmallForm() && blend > 0.0F) {
            // Smoothly rotate from normal orientation into the scarf pose.
            pose.mulPose(Axis.YP.rotationDegrees(90.0F * blend));
            pose.translate(0.0D, -0.035D * blend, 0.0D);
        }
        // Keep the original baby size in every pose, as requested.
        float factor = entity.isSmallForm() ? 0.42F : 5.8F;
        pose.scale(factor, factor, factor);
    }

    @Override
    public ResourceLocation getTextureLocation(AurenDragon dragon) {
        return dragon.isSmallForm() ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
