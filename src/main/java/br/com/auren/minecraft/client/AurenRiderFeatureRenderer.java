/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Selected rider-body attachment technique ported from original
 * IceAndFire CE 2.1.4 DragonRiderFeatureRenderer,
 * commit aa2f11a6b79acb48a1094765d610cdaca79e3d18.
 * Renders ONLY the controlling PLAYER of our single Auren entity.
 */
package br.com.auren.minecraft.client;

import br.com.auren.minecraft.entity.AurenDragon;
import com.iafenvoy.uranus.client.model.AdvancedModelBox;
import com.iafenvoy.uranus.client.model.TabulaModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Draw the rider FROM THE DRAGON'S ANIMATED BONE hierarchy, as original CE
 * does. Vanilla draws a passenger independently at its world position;
 * its avatar then stays rigid / appears to sink into the flapping torso.
 *
 * The rider is drawn only once by canceling its separate world render in
 * ClientEvents. The identity guard prevents canceling this nested render.
 */
public final class AurenRiderFeatureRenderer
        extends RenderLayer<AurenDragon, TabulaModel<AurenDragon>> {
    public static final Set<Entity> RENDERING_RIDERS =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public AurenRiderFeatureRenderer(MobRenderer<AurenDragon, TabulaModel<AurenDragon>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight,
                       AurenDragon dragon, float limbSwing, float limbSwingAmount,
                       float partialTick, float age, float yaw, float pitch) {
        if (!dragon.isVehicle() || dragon.isSmallForm() || dragon.isOnShoulder()) return;

        Entity passenger = dragon.getFirstPassenger();
        if (!(passenger instanceof Player player) || dragon.getControllingPassenger() != player) return;

        // First person should display hands/camera, not render the player's
        // full body inside the camera. Third person and other riders still render.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == player && mc.options.getCameraType().isFirstPerson()) return;

        float scale = 3.25F; // Identical to AurenDragonRenderer adult scale
        pose.pushPose();
        try {
            // ORIGINAL CE DragonRiderFeatureRenderer.translateToBody:
            // inherited animation matrices are already updated this frame.
            postRender(this.getParentModel().getCube("BodyUpper"), pose);
            postRender(this.getParentModel().getCube("Neck1"), pose);
            pose.translate(0.0F, -0.01F * scale, -0.035F * scale);

            float riderYaw = player.yRotO + (player.getYRot() - player.yRotO) * partialTick;
            pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
            pose.mulPose(Axis.YP.rotationDegrees(riderYaw + 180.0F));
            pose.scale(1.0F / scale, 1.0F / scale, 1.0F / scale);
            pose.translate(0.0F, -0.25F, 0.0F);

            RENDERING_RIDERS.add(player);
            try {
                mc.getEntityRenderDispatcher().render(
                        player, 0.0D, 0.0D, 0.0D, 0.0F,
                        partialTick, pose, buffer, packedLight);
            } finally {
                RENDERING_RIDERS.remove(player);
            }
        } finally {
            pose.popPose();
        }
    }

    private static void postRender(AdvancedModelBox bone, PoseStack pose) {
        if (bone == null) {
            throw new IllegalStateException(
                    "Missing original CE saddle bone (BodyUpper/Neck1) in Auren Tabula");
        }
        // Verbatim transform ordering of CE DragonRiderFeatureRenderer.
        pose.translate(bone.rotationPointX * 0.0625F,
                bone.rotationPointY * 0.0625F, bone.rotationPointZ * 0.0625F);
        if (bone.rotateAngleZ != 0.0F) pose.mulPose(Axis.ZP.rotation(bone.rotateAngleZ));
        if (bone.rotateAngleY != 0.0F) pose.mulPose(Axis.YP.rotation(bone.rotateAngleY));
        if (bone.rotateAngleX != 0.0F) pose.mulPose(Axis.XP.rotation(bone.rotateAngleX));
    }
}
