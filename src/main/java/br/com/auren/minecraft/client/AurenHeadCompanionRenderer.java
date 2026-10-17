package br.com.auren.minecraft.client;

import br.com.auren.minecraft.AurenMinecraft;
import br.com.auren.minecraft.entity.AurenDragon;
import com.iafenvoy.uranus.client.model.TabulaModel;
import com.iafenvoy.uranus.client.model.util.TabulaModelHandlerHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Renders the original CE Tabula dragon on the player's HEAD BONE.
 * No dragon is instantiated/added to the world, and no collision/hitbox exists.
 *
 * Preliminary pose E: a little dragon clinging on top of the head, folded
 * wings, sitting/awake source animation. Fine alignment requires game QA.
 */
public final class AurenHeadCompanionRenderer {
    private static final ResourceLocation POSE =
            ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID,
                    "lightningdragon/lightningdragon_sitting_shoulder");
    private static final ResourceLocation FOLDED =
            ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID,
                    "lightningdragon/lightningdragon_sleeping");
    private static final ResourceLocation SKIN =
            ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID,
                    "textures/entity/auren_black_1.png");

    private AurenHeadCompanionRenderer() {}

    private static boolean hasBond(Player p) {
        for (var stack : p.getInventory().items)
            if (stack.is(AurenMinecraft.BOND_ITEM.get())) return true;
        for (var stack : p.getInventory().offhand)
            if (stack.is(AurenMinecraft.BOND_ITEM.get())) return true;
        return false;
    }

    public static void render(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        // First-person camera should not be inside the dragon's head.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == player && mc.options.getCameraType().isFirstPerson()) return;
        // The local inventory is authoritative only for the local client.
        // Other players require a later explicit tracking-state sync.
        if (!hasBond(player)) return;

        TabulaModel<AurenDragon> model = TabulaModelHandlerHelper.getModel(POSE);
        if (model == null) return;
        model.resetToDefaultPose();
        AurenPoseTransition.applyBones(model,
                TabulaModelHandlerHelper.getModel(FOLDED), 1.0F,
                "armL1", "armL2", "armR1", "armR2");
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            // The vanilla player head ModelPart supplies ALL yaw, pitch and
            // sneaking/body transforms, identical to a real helmet layer.
            event.getRenderer().getModel().head.translateAndRotate(pose);
            // Head origin is at eye/neck pivot: top of hat is -8/16 blocks.
            pose.translate(0.0F, -0.50F, -0.035F);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.scale(0.20F, 0.20F, 0.20F);
            model.renderToBuffer(pose,
                    event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(SKIN)),
                    event.getPackedLight(), OverlayTexture.NO_OVERLAY, -1);
        } finally {
            pose.popPose();
        }
    }
}
