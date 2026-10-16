package br.com.auren.minecraft.client;

import br.com.auren.minecraft.AurenMinecraft;
import com.mojang.blaze3d.platform.InputConstants;
import br.com.auren.minecraft.entity.AurenDragon;
import br.com.auren.minecraft.network.AurenDragonControlPayload;
import br.com.auren.minecraft.network.AurenMarkPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = AurenMinecraft.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class ClientEvents {
    private ClientEvents() {}

    // Same descent key as the original IceAndFire Community Edition.
    private static final KeyMapping DRAGON_DOWN = new KeyMapping(
            "key.aurenminecraft.dragon_down", GLFW.GLFW_KEY_X, "key.categories.gameplay");
    // CE IafKeyMappings.DRAGON_BREATH default is R.
    private static final KeyMapping DRAGON_BREATH = new KeyMapping(
            "key.aurenminecraft.dragon_breath", GLFW.GLFW_KEY_R, "key.categories.gameplay");
    // Additional mounted action. R remains fire; G launches CE lightning.
    private static final KeyMapping DRAGON_LIGHTNING = new KeyMapping(
            "key.aurenminecraft.dragon_lightning", GLFW.GLFW_KEY_G,
            "key.categories.gameplay");
    // One remappable key: plain V marks, Shift+V cancels or cycles radius.
    private static final KeyMapping MARK_TARGET = new KeyMapping(
            "key.aurenminecraft.mark_target", GLFW.GLFW_KEY_V, "key.categories.gameplay");
    // B toggles AREA bombing only; V still marks any permitted living target.
    private static final KeyMapping TOGGLE_BOMBARDMENT = new KeyMapping(
            "key.aurenminecraft.toggle_bombardment", GLFW.GLFW_KEY_B,
            "key.categories.gameplay");
    private static int lastPilotId = -1;
    private static byte lastPilotControls = -1;

    // Called from ClientModEvents on the MOD bus.
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(DRAGON_DOWN);
        event.register(DRAGON_BREATH);
        event.register(DRAGON_LIGHTNING);
        event.register(MARK_TARGET);
        event.register(TOGGLE_BOMBARDMENT);
    }

    /** Port of CE ClientEvents.onLivingUpdate rider input, once per client tick. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            AurenAttackOverlay.resetTransient();
            lastPilotId = -1;
            lastPilotControls = -1;
            return;
        }
        // Input is edge-triggered and ignored while typing in menus/chat.
        if (mc.screen == null && mc.player.isAlive()) {
            while (MARK_TARGET.consumeClick()) {
                AurenDragon owned = AurenAttackOverlay.nearbyOwnedDragon(mc);
                if (owned == null) continue;
                AurenAttackOverlay.revealForTicks(mc);
                // Physical Shift, independent of the player's remapped Sneak key.
                long window = mc.getWindow().getWindow();
                boolean shiftHeld = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                        || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
                if (shiftHeld) {
                    if (owned.getSyncedMarkMode() != 0) {
                        PacketDistributor.sendToServer(new AurenMarkPayload(0, true));
                    } else {
                        AurenAttackOverlay.nextRadius();
                    }
                } else {
                    PacketDistributor.sendToServer(
                            new AurenMarkPayload(AurenAttackOverlay.selectedRadius(), false));
                }
            }
            while (TOGGLE_BOMBARDMENT.consumeClick()) {
                if (AurenAttackOverlay.nearbyOwnedDragon(mc) == null) continue;
                AurenAttackOverlay.revealForTicks(mc);
                // 0 is the server-owned mode toggle, not a targeting radius.
                PacketDistributor.sendToServer(new AurenMarkPayload(0, false));
            }
        }
        Entity mount = mc.player.getVehicle();
        if (!(mount instanceof AurenDragon dragon)
                || dragon.isSmallForm() || !dragon.isOwnedBy(mc.player)) {
            lastPilotId = -1;
            lastPilotControls = -1;
            return;
        }

        byte controls = (byte) ((mc.options.keyJump.isDown() ? 1 : 0)
                | (DRAGON_DOWN.isDown() ? 2 : 0)
                | (DRAGON_BREATH.isDown() ? 8 : 0)
                | (DRAGON_LIGHTNING.isDown() ? 4 : 0));
        dragon.setRiderControlState(controls);
        if (dragon.getId() != lastPilotId || controls != lastPilotControls) {
            PacketDistributor.sendToServer(new AurenDragonControlPayload(
                    dragon.getId(), controls));
            lastPilotId = dragon.getId();
            lastPilotControls = controls;
        }
    }

    @SubscribeEvent
    public static void renderAttackMarkers(RenderLevelStageEvent event) {
        AurenAttackOverlay.renderWorld(event);
    }

    @SubscribeEvent
    public static void renderAttackHud(RenderGuiEvent.Post event) {
        AurenAttackOverlay.renderHud(event);
    }

    /** Suppress only the duplicate default player renderer while riding Auren. */
    @SubscribeEvent
    public static void cancelUnattachedMountedPlayer(RenderPlayerEvent.Pre event) {
        if (event.getEntity().getVehicle() instanceof AurenDragon
                && !AurenRiderFeatureRenderer.RENDERING_RIDERS.contains(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // Called from ClientModEvents on the MOD bus.
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AurenMinecraft.AUREN.get(), AurenDragonRenderer::new);
    }
}
