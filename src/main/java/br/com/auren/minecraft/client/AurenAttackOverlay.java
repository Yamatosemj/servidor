package br.com.auren.minecraft.client;

import br.com.auren.minecraft.entity.AurenDragon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Comparator;

/**
 * Client-only reticle preview: circles/range are VISUAL. Combat remains
 * server-authoritative and only server-synchronized orders persist.
 */
public final class AurenAttackOverlay {
    private static final int MAX_LOOK = 48;
    private static final double DRAGON_RANGE = 64.0D;
    private static int selectedRadius = 5;
    private static final int SEGMENTS = 64;

    private AurenAttackOverlay() {}

    public static int selectedRadius() {
        return selectedRadius;
    }

    public static void nextRadius() {
        selectedRadius = selectedRadius >= 30 ? 5 : selectedRadius + 5;
    }

    public static AurenDragon nearbyOwnedDragon(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        return mc.level.getEntitiesOfClass(AurenDragon.class,
                mc.player.getBoundingBox().inflate(DRAGON_RANGE),
                dragon -> dragon.isAlive() && dragon.isTame()
                        && dragon.isOwnedBy(mc.player)).stream()
                .min(Comparator.comparingDouble(mc.player::distanceToSqr))
                .orElse(null);
    }

    /** Mimics server ray tests; never sent to server as position or entity ID. */
    private static Preview preview(Minecraft mc, AurenDragon dragon) {
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 end = eye.add(mc.player.getViewVector(1.0F).scale(MAX_LOOK));
        BlockHitResult block = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (block.getType() == HitResult.Type.BLOCK) end = block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(
                mc.level, mc.player, eye, end, new AABB(eye, end).inflate(1.0D),
                candidate -> candidate instanceof LivingEntity living
                        && living.isAlive() && candidate != mc.player && candidate != dragon);
        if (entity != null) {
            Entity found = entity.getEntity();
            if (found instanceof LivingEntity living && living instanceof Enemy
                    && !dragon.isAlliedTo(living)) return new Preview(null, living, false);
            return new Preview(null, null, true);
        }
        return new Preview(block.getType() == HitResult.Type.BLOCK
                ? block.getBlockPos() : null, null, false);
    }

    private record Preview(BlockPos block, LivingEntity enemy, boolean invalidEntity) {}

    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || event.getPoseStack() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null
                || mc.options.hideGui || mc.player.isSpectator()) return;
        AurenDragon dragon = nearbyOwnedDragon(mc);
        if (dragon == null) return;

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        // Server-accepted target remains visible even when the player looks away.
        if (dragon.getSyncedMarkMode() == 2) {
            drawArea(pose, lines, dragon.getSyncedMarkCenter(),
                    dragon.getSyncedMarkRadius(), 45, 235, 255, 240);
        } else if (dragon.getSyncedMarkMode() == 1) {
            Entity marked = mc.level.getEntity(dragon.getSyncedMarkedEnemyId());
            if (marked instanceof LivingEntity alive && alive.isAlive()) {
                drawEnemy(pose, lines, alive, 35, 235, 255, 255);
            }
        }

        // Dimmer local preview; target decision is repeated on server on V.
        if (!dragon.isVehicle() && !dragon.isSmallForm() && !dragon.isOnShoulder()) {
            Preview aiming = preview(mc, dragon);
            if (aiming.enemy() != null) {
                drawEnemy(pose, lines, aiming.enemy(), 130, 205, 255, 180);
            } else if (aiming.block() != null
                    && (dragon.getSyncedMarkMode() != 2
                    || !aiming.block().equals(dragon.getSyncedMarkCenter())
                    || selectedRadius != dragon.getSyncedMarkRadius())) {
                drawArea(pose, lines, aiming.block(),
                        selectedRadius, 95, 190, 245, 145);
            }
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static void drawEnemy(PoseStack pose, VertexConsumer lines,
                                  LivingEntity enemy, int red, int green, int blue, int alpha) {
        LevelRenderer.renderLineBox(pose, lines,
                enemy.getBoundingBox().inflate(0.14D),
                red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
        Vec3 p = enemy.getBoundingBox().getCenter();
        double top = enemy.getBoundingBox().maxY + 0.30D;
        line(pose, lines, p.x - 0.5, top, p.z, p.x + 0.5, top, p.z,
                red, green, blue, alpha);
        line(pose, lines, p.x, top - 0.4, p.z, p.x, top + 0.4, p.z,
                red, green, blue, alpha);
    }

    private static void drawArea(PoseStack pose, VertexConsumer lines, BlockPos center,
                                 int radius, int red, int green, int blue, int alpha) {
        if (center == null || radius < 5 || radius > 30) return;
        double x = center.getX() + 0.5D;
        double z = center.getZ() + 0.5D;
        double y = center.getY() + 1.03D;
        ring(pose, lines, x, y, z, radius, red, green, blue, alpha);
        // Orbit is approx. nine blocks over the marked block center (v0.7.7).
        ring(pose, lines, x, center.getY() + 9.5D, z,
                radius, red, green, blue, Math.max(55, alpha / 3));
        line(pose, lines, x - 0.75, y, z, x + 0.75, y, z, red, green, blue, alpha);
        line(pose, lines, x, y, z - 0.75, x, y, z + 0.75, red, green, blue, alpha);
        line(pose, lines, x, y, z, x, y + 2.0D, z, red, green, blue, alpha);
    }

    private static void ring(PoseStack pose, VertexConsumer lines,
                             double x, double y, double z, int radius,
                             int red, int green, int blue, int alpha) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a = i * Math.PI * 2.0D / SEGMENTS;
            double b = (i + 1) * Math.PI * 2.0D / SEGMENTS;
            line(pose, lines, x + Math.cos(a) * radius, y, z + Math.sin(a) * radius,
                    x + Math.cos(b) * radius, y, z + Math.sin(b) * radius,
                    red, green, blue, alpha);
        }
    }

    private static void line(PoseStack pose, VertexConsumer lines,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             int red, int green, int blue, int alpha) {
        // RenderType.lines expects a direction vector for each segment.
        // A fixed upward normal can collapse horizontal/vertical line widths.
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6D) return;
        float nx = (float) (dx / length);
        float ny = (float) (dy / length);
        float nz = (float) (dz / length);
        lines.addVertex(pose.last().pose(), (float) x1, (float) y1, (float) z1)
                .setColor(red, green, blue, alpha).setNormal(nx, ny, nz);
        lines.addVertex(pose.last().pose(), (float) x2, (float) y2, (float) z2)
                .setColor(red, green, blue, alpha).setNormal(nx, ny, nz);
    }

    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null
                || mc.options.hideGui || mc.player.isSpectator()) return;
        AurenDragon dragon = nearbyOwnedDragon(mc);
        if (dragon == null) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int y = graphics.guiHeight() - 70;
        int width = 258;
        graphics.fill(6, y - 5, width, y + 32, 0xA50C1724);
        String mode = switch (dragon.getSyncedMarkMode()) {
            case 1 -> "Auren | Inimigo marcado";
            case 2 -> "Auren | Bombardeio ativo: raio " + dragon.getSyncedMarkRadius();
            default -> "Auren | Raio de ataque: " + selectedRadius + " blocos";
        };
        graphics.drawString(mc.font, mode, 12, y, 0x77E9FF, true);
        if (dragon.getSyncedMarkMode() != 0) {
            graphics.drawString(mc.font, "V: novo alvo   Shift+V: cancelar",
                    12, y + 13, 0xFFFFFF, true);
        } else {
            graphics.drawString(mc.font, "V: marcar   Shift+V: mudar raio",
                    12, y + 13, 0xFFFFFF, true);
        }
        if (dragon.getSyncedMarkMode() == 0 && !dragon.isSmallForm()) {
            Preview aim = preview(mc, dragon);
            if (aim.invalidEntity()) {
                graphics.drawString(mc.font, "Alvo não hostil", 12, y + 24, 0xFFA999, true);
            }
        }
    }
}
