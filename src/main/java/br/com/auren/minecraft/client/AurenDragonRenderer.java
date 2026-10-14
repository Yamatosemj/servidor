package br.com.auren.minecraft.client;

import br.com.auren.minecraft.AurenMinecraft;
import br.com.auren.minecraft.entity.AurenDragon;
import com.iafenvoy.uranus.client.model.TabulaModel;
import com.iafenvoy.uranus.client.model.util.TabulaModelHandlerHelper;
import com.mojang.blaze3d.vertex.PoseStack;
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

    // Original awake CE shoulder pose is the reference for a single,
    // documented, authored Auren-specific STATIC posture.
    private static final ResourceLocation SHOULDER_AWAKE_POSE =
            ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID,
                    "lightningdragon/lightningdragon_sitting_shoulder");
    private final TabulaModel<AurenDragon> groundModel;

    public AurenDragonRenderer(EntityRendererProvider.Context context) {
        super(context, load("lightningdragon/lightningdragon_ground"), 1.5F);
        this.groundModel = this.model;
        // CE shoulder base; the authored posture is in AurenNeckPose.
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
     * Original awake shoulder base + ONE authored static target + CE idle.
     * Uses the same CE skeleton and does not affect walking/adult animations.
     */
    private static void animate(TabulaModel<AurenDragon> model, AurenDragon dragon,
                                float limbSwing, float limbSwingAmount,
                                float age, float yaw, float pitch, float scale) {
        model.resetToDefaultPose();
        // Uranus does NOT reset per-bone scale or scaleChildren itself.
        AurenNeckPose.resetTransientScale(model);
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float shoulderBlend = dragon.getShoulderBlend(partialTick);

        if (shoulderBlend > 0.0F && dragon.isSmallForm()) {
            // Preserve awake head and neck from original CE shoulder pose.
            TabulaModel<AurenDragon> awake =
                    TabulaModelHandlerHelper.getModel(SHOULDER_AWAKE_POSE);
            AurenPoseTransition.apply(model, awake, shoulderBlend);

            // Authored shoulder posture; folds the extremely broad wings
            // as a small cosmetic profile. The original CE provides idle.
            AurenNeckPose.apply(model, awake, shoulderBlend);

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
    }

    @Override
    protected void scale(AurenDragon entity, PoseStack pose, float partialTick) {
        // Model yaw follows player's torso, no whole-model sideways flip.
        // Keep the original baby size; adult now approximates a CE stage-3 dragon.
        float factor = entity.isSmallForm() ? 0.42F : 3.25F;
        pose.scale(factor, factor, factor);
    }

    @Override
    public ResourceLocation getTextureLocation(AurenDragon dragon) {
        return dragon.isSmallForm() ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
