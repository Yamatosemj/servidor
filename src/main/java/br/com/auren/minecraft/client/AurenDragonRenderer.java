package br.com.auren.minecraft.client;

import br.com.auren.minecraft.AurenMinecraft;
import br.com.auren.minecraft.entity.AurenDragon;
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
                () -> (model, dragon, swing, swingAmount, age, yaw, pitch, scale) -> {
                    model.resetToDefaultPose();
                    // The original Tabula pose is kept intact in this first prototype.
                });
        if (result == null) {
            throw new IllegalStateException("Could not load Tabula model " + id +
                    " — verify Uranus and the generated CE assets");
        }
        return result;
    }

    @Override
    public void render(AurenDragon entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        this.model = entity.isSmallForm() && entity.isPassenger() ? shoulderModel : groundModel;
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
