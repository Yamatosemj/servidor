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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
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
    private static final int PREVIEW_TICKS = 80;
    private static long previewUntil = Long.MIN_VALUE;
    private static net.minecraft.client.multiplayer.ClientLevel previewLevel;
    private static final int PERCEPTION_TICKS = 140;
    private static long perceptionUntil = Long.MIN_VALUE;
    private static net.minecraft.client.multiplayer.ClientLevel perceptionLevel;

    /** Preview is temporary; gameplay never leaves permanent rings in the world. */
    public static void revealForTicks(Minecraft mc) {
        if (mc.level != null) {
            previewLevel = mc.level;
            previewUntil = mc.level.getGameTime() + PREVIEW_TICKS;
        }
    }

    public static void resetTransient() {
        previewUntil = Long.MIN_VALUE;
        previewLevel = null;
        perceptionUntil = Long.MIN_VALUE;
        perceptionLevel = null;
    }

    /** A quick 7-second glance; never a permanent intrusive overlay. */
    public static void revealPerceptionForTicks(Minecraft mc) {
        if (mc.level != null) {
            perceptionLevel = mc.level;
            perceptionUntil = mc.level.getGameTime() + PERCEPTION_TICKS;
        }
    }

    private static boolean perceptionActive(Minecraft mc) {
        return mc.level != null && mc.level == perceptionLevel
                && mc.level.getGameTime() < perceptionUntil;
    }

    private static boolean previewActive(Minecraft mc) {
        return ClientEvents.isTargeting() || (mc.level != null && mc.level == previewLevel
                && mc.level.getGameTime() < previewUntil);
    }

    private AurenAttackOverlay() {}

    public static int selectedRadius() {
        return selectedRadius;
    }

    public static void setSelectedRadius(int radius) {
        if (radius >= 5 && radius <= 30 && radius % 5 == 0)
            selectedRadius = radius;
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
            if (found instanceof LivingEntity living && !dragon.isAlliedTo(living)
                    && living != dragon.getOwner()
                    && !(living instanceof Player p && (p.isCreative() || p.isSpectator()))
                    && !(living instanceof TamableAnimal pet && pet.isTame()
                        && dragon.getOwnerUUID() != null
                        && dragon.getOwnerUUID().equals(pet.getOwnerUUID())))
                return new Preview(block.getType() == HitResult.Type.BLOCK
                        ? block.getBlockPos() : null, living, false);
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
        // Only display 3D markers briefly after the user interacts with V/B.
        if (!previewActive(mc)) return;
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
            if (aiming.enemy() != null && !ClientEvents.isAreaTargeting()) {
                drawEnemy(pose, lines, aiming.enemy(), 130, 205, 255, 180);
            } else if (!ClientEvents.isCreatureTargeting() && aiming.block() != null
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
        // The new natural patrol has no fixed aerial orbit to draw.
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
        int mode = dragon.getSyncedMarkMode();
        boolean transientHint = previewActive(mc);
        boolean perceptionHint = perceptionActive(mc);
        // Nothing on screen while idle. Perception is opt-in for 7 seconds.
        if (mode == 0 && !transientHint && !perceptionHint) return;

        GuiGraphics graphics = event.getGuiGraphics();
        String status = ClientEvents.isTargeting()
                ? (ClientEvents.isAreaTargeting()
                    ? "AUREN  •  SELECIONE A AREA  •  RAIO " + selectedRadius
                    : "AUREN  •  SELECIONE A CRIATURA")
                : switch (mode) {
            case 1 -> "AUREN  •  ALVO MARCADO";
            case 2 -> "AUREN  •  BOMBARDEIO ATIVO  •  RAIO " + dragon.getSyncedMarkRadius();
            default -> "AUREN  •  BOMBARDEIO "
                    + (dragon.isSyncedAreaAttackEnabled() ? "LIGADO" : "DESLIGADO")
                    + "  •  RAIO " + selectedRadius;
        };
        if (mode != 0 || transientHint || ClientEvents.isTargeting()) {
            int x = (graphics.guiWidth() - mc.font.width(status)) / 2;
            graphics.drawString(mc.font, status, x, 8, 0x66DAFF, true);
        }

        if (perceptionHint) {
            String observed = dragon.getSyncedPerceptionBrief();
            int alert = dragon.getSyncedPerceptionAlert();
            int color = alert == 2 ? 0xF6BB79 : alert == 1 ? 0xB4E4F8 : 0x66DAFF;
            int y = mode != 0 || transientHint ? 30 : 8;
            graphics.drawString(mc.font, observed,
                    (graphics.guiWidth() - mc.font.width(observed)) / 2,
                    y, color, true);
            String prompt = "H: observar   /auren ambiente: detalhes";
            graphics.drawString(mc.font, prompt,
                    (graphics.guiWidth() - mc.font.width(prompt)) / 2,
                    y + 12, 0x8CBCCD, true);
        }

        if (transientHint) {
            String hint = ClientEvents.isTargeting()
                    ? "Botao direito: confirmar   V: voltar ao menu   ESC: sair"
                    : "V: abrir o painel do Auren";
            graphics.drawString(mc.font, hint,
                    (graphics.guiWidth() - mc.font.width(hint)) / 2, 20,
                    0x8CBCCD, true);
        }
    }
}
