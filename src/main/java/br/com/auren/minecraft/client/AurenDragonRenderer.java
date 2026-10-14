package br.com.auren.minecraft.client;

import br.com.auren.minecraft.AurenMinecraft;
import br.com.auren.minecraft.entity.AurenDragon;
import com.iafenvoy.uranus.client.model.AdvancedModelBox;
import com.iafenvoy.uranus.client.model.TabulaModel;
import com.iafenvoy.uranus.client.model.util.TabulaModelHandlerHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class AurenDragonRenderer extends MobRenderer<AurenDragon, TabulaModel<AurenDragon>> {
    private static final ResourceLocation ADULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID, "textures/entity/auren_black_5.png");
    private static final ResourceLocation BABY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID, "textures/entity/auren_black_1.png");

    private final TabulaModel<AurenDragon> groundModel;
    private final TabulaModel<AurenDragon> shoulderModel;

    public AurenDragonRenderer(EntityRendererProvider.Context context) {
        super(context, load("lightningdragon/lightningdragon_ground"), 1.5F);
        this.groundModel = this.model;
        this.shoulderModel = load("lightningdragon/lightningdragon_sitting_shoulder");
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
        this.model = entity.isSmallForm() && entity.isOnShoulder() ? shoulderModel : groundModel;
        this.shadowRadius = entity.isSmallForm() ? 0.2F : 1.7F;
        super.render(entity, yaw, partialTick, pose, buffer, packedLight);
    }

    @Override
    protected void scale(AurenDragon entity, PoseStack pose, float partialTick) {
        float factor = entity.isSmallForm() ? 0.42F : 5.8F;
        pose.scale(factor, factor, factor);
    }

    @Override
    public ResourceLocation getTextureLocation(AurenDragon dragon) {
        return dragon.isSmallForm() ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
