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

    // The original CE 'sitting_shoulder' still looks like a standing dragon:
    // its head remains upright and wings partly spread. The original CE
    // sleeping Tabula keyframe has a lowered torso, tucked wings, bent legs
    // and curved tail, suitable for resting horizontally on a shoulder.
    private static final ResourceLocation SHOULDER_REST_POSE =
            ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID,
                    "lightningdragon/lightningdragon_sleeping");

    private final TabulaModel<AurenDragon> groundModel;

    public AurenDragonRenderer(EntityRendererProvider.Context context) {
        super(context, load("lightningdragon/lightningdragon_ground"), 1.5F);
        this.groundModel = this.model;
        // Load original CE sleeping keyframe as the resting pose on shoulder.
        TabulaModelHandlerHelper.getModel(SHOULDER_REST_POSE);
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
     * The shoulder Auren uses the CE Lightning Dragon's ORIGINAL sleeping
     * posture (lowered torso, tucked wings, curled tail), with the same slow
     * CE idle motion over it. No custom keyframe animation is invented.
     */
    private static void animate(TabulaModel<AurenDragon> model, AurenDragon dragon,
                                float limbSwing, float limbSwingAmount,
                                float age, float yaw, float pitch, float scale) {
        model.resetToDefaultPose();
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float shoulderBlend = dragon.getShoulderBlend(partialTick);

        // v0.5.4's 'sitting_shoulder' remains upright even with CE idle
        // animation. Reuse the actual CE sleeping keyframe instead of trying
        // to create a custom animation for a curled, resting hatchling.
        // This is a full original Tabula posture; there is intentionally no
        // CE riding-specific BodyUpper.offsetZ displacement over it.
        if (shoulderBlend > 0.0F && dragon.isSmallForm()) {
            TabulaModel<AurenDragon> restPose =
                    TabulaModelHandlerHelper.getModel(SHOULDER_REST_POSE);
            AurenPoseTransition.apply(model, restPose, shoulderBlend);
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
        // The original CE sitting_shoulder pose already orients all bones.
        // Do not rotate the entire dragon 90 degrees: that twists the pose.
        // Keep the original baby size; adult now approximates a CE stage-3 dragon.
        float factor = entity.isSmallForm() ? 0.42F : 3.25F;
        pose.scale(factor, factor, factor);
    }

    @Override
    public ResourceLocation getTextureLocation(AurenDragon dragon) {
        return dragon.isSmallForm() ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
