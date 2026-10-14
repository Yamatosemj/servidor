package br.com.auren.minecraft.client;

import br.com.auren.minecraft.AurenMinecraft;
import br.com.auren.minecraft.entity.AurenDragon;
import com.iafenvoy.uranus.client.model.AdvancedModelBox;
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

    // The original CE sitting_shoulder keyframe is unexpectedly upright:
    // its torso/wing angles barely differ from GROUND and give the impression
    // of a standing adult placed on the player's head. The *original* CE
    // sleeping pose naturally curls the neck and wings around the body.
    // This is NOT a hand-authored pose or new skeletal animation.
    private static final ResourceLocation SHOULDER_REST_POSE =
            ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID,
                    "lightningdragon/lightningdragon_sleeping");

    private final TabulaModel<AurenDragon> groundModel;

    public AurenDragonRenderer(EntityRendererProvider.Context context) {
        super(context, load("lightningdragon/lightningdragon_ground"), 1.5F);
        this.groundModel = this.model;
        // Cache the original CE curled resting pose before entering shoulder mode.
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
     * CE original Lightning Dragon Tabula walk/flight keyframes and resting
     * pose. The original sleeping pose keeps the baby low and naturally
     * curled, unlike sitting_shoulder, which looks like standing upright.
     * The original CE idle movements continue on the posed bones.
     */
    private static void animate(TabulaModel<AurenDragon> model, AurenDragon dragon,
                                float limbSwing, float limbSwingAmount,
                                float age, float yaw, float pitch, float scale) {
        model.resetToDefaultPose();
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float shoulderBlend = dragon.getShoulderBlend(partialTick);

        // Blend the SAME skeleton to an actual original CE CURLED rest pose,
        // not the almost-upright sitting_shoulder pose visible in screenshots.
        if (shoulderBlend > 0.0F && dragon.isSmallForm()) {
            TabulaModel<AurenDragon> originalRest =
                    TabulaModelHandlerHelper.getModel(SHOULDER_REST_POSE);
            AurenPoseTransition.apply(model, originalRest, shoulderBlend);

            // Retain the original CE torso correction that places the model
            // closer to the avatar; this is a global offset, not a custom
            // skeletal pose. Only applied while perched.
            AdvancedModelBox torso = model.getCube("BodyUpper");
            if (torso != null) {
                torso.offsetZ += (-12.0F - torso.offsetZ) * shoulderBlend;
            }
            // CE's already ported idle routines animate resting neck, tail,
            // body and wings; unlike a static pose, the baby remains alive.
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
        // The original curled CE pose already orients the bones. Never add
        // global model rotations or procedural limb twists.
        // Keep the original baby size; adult now approximates a CE stage-3 dragon.
        float factor = entity.isSmallForm() ? 0.42F : 3.25F;
        pose.scale(factor, factor, factor);
    }

    @Override
    public ResourceLocation getTextureLocation(AurenDragon dragon) {
        return dragon.isSmallForm() ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
