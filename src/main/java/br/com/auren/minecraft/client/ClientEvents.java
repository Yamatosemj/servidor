package br.com.auren.minecraft.client;

import br.com.auren.minecraft.AurenMinecraft;
import br.com.auren.minecraft.entity.AurenDragon;
import br.com.auren.minecraft.network.AurenDragonControlPayload;
import br.com.auren.minecraft.network.AurenMenuActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * One V menu for ground commands. Mounted CE controls remain available:
 * X descend, R fire and G lightning.
 */
@EventBusSubscriber(modid = AurenMinecraft.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class ClientEvents {
    private ClientEvents() {}

    private static final KeyMapping DRAGON_DOWN = new KeyMapping(
            "key.aurenminecraft.dragon_down", GLFW.GLFW_KEY_X, "key.categories.gameplay");
    private static final KeyMapping DRAGON_BREATH = new KeyMapping(
            "key.aurenminecraft.dragon_breath", GLFW.GLFW_KEY_R, "key.categories.gameplay");
    private static final KeyMapping DRAGON_LIGHTNING = new KeyMapping(
            "key.aurenminecraft.dragon_lightning", GLFW.GLFW_KEY_G,
            "key.categories.gameplay");
    private static final KeyMapping MARK_TARGET = new KeyMapping(
            "key.aurenminecraft.mark_target", GLFW.GLFW_KEY_V, "key.categories.gameplay");

    private static int lastPilotId = -1;
    private static byte lastPilotControls = -1;
    private static boolean selectingCreature;
    private static boolean selectingArea;
    // Use-item event may be raised once per hand, even after main-hand cancel.
    private static boolean suppressOffhand;

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(DRAGON_DOWN);
        event.register(DRAGON_BREATH);
        event.register(DRAGON_LIGHTNING);
        event.register(MARK_TARGET);
    }

    public static boolean matchesMenuKey(int key, int scancode) {
        return MARK_TARGET.matches(key, scancode);
    }

    public static boolean isTargeting() { return selectingCreature || selectingArea; }
    public static boolean isCreatureTargeting() { return selectingCreature; }
    public static boolean isAreaTargeting() { return selectingArea; }

    public static void beginSelection(boolean area) {
        selectingArea = area;
        selectingCreature = !area;
        AurenAttackOverlay.revealForTicks(Minecraft.getInstance());
    }

    private static void endSelection() {
        selectingCreature = false;
        selectingArea = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        suppressOffhand = false; // Never suppress interactions in a later tick.
        if (mc.player == null || mc.level == null) {
            AurenAttackOverlay.resetTransient();
            endSelection();
            lastPilotId = -1;
            lastPilotControls = -1;
            return;
        }
        if (mc.screen != null && isTargeting()) endSelection();

        if (mc.screen == null && mc.player.isAlive()) {
            while (MARK_TARGET.consumeClick()) {
                if (isTargeting()) {
                    endSelection(); // Cancel ONLY preview, never clear an active server order.
                    mc.setScreen(new AurenMenuScreen());
                } else {
                    mc.setScreen(new AurenMenuScreen());
                }
            }
        }
        Entity mount = mc.player.getVehicle();
        if (!(mount instanceof AurenDragon dragon)
                || dragon.isSmallForm() || !dragon.isOwnedBy(mc.player)) {
            lastPilotId = -1;
            lastPilotControls = -1;
            return;
        }
        byte controls = mc.screen == null
                ? (byte) ((mc.options.keyJump.isDown() ? 1 : 0)
                    | (DRAGON_DOWN.isDown() ? 2 : 0)
                    | (DRAGON_BREATH.isDown() ? 8 : 0)
                    | (DRAGON_LIGHTNING.isDown() ? 4 : 0))
                : 0;
        dragon.setRiderControlState(controls);
        if (dragon.getId() != lastPilotId || controls != lastPilotControls) {
            PacketDistributor.sendToServer(new AurenDragonControlPayload(
                    dragon.getId(), controls));
            lastPilotId = dragon.getId();
            lastPilotControls = controls;
        }
    }

    /**
     * The confirmation click is reserved for targeting: never mount Auren,
     * place a block, consume a held item or trigger an offhand action.
     */
    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        if (event.getHand() == InteractionHand.OFF_HAND && suppressOffhand) {
            event.setCanceled(true);
            event.setSwingHand(false);
            suppressOffhand = false;
            return;
        }
        if (!isTargeting() || event.getHand() != InteractionHand.MAIN_HAND) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        suppressOffhand = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mc.player.isAlive()
                || AurenAttackOverlay.nearbyOwnedDragon(mc) == null) {
            endSelection();
            return;
        }
        int action = selectingArea ? AurenMenuActionPayload.TARGET_AREA
                : AurenMenuActionPayload.TARGET_CREATURE;
        PacketDistributor.sendToServer(new AurenMenuActionPayload(
                action, AurenAttackOverlay.selectedRadius()));
        endSelection();
    }

    @SubscribeEvent
    public static void renderAttackMarkers(RenderLevelStageEvent event) {
        AurenAttackOverlay.renderWorld(event);
    }

    @SubscribeEvent
    public static void renderAttackHud(RenderGuiEvent.Post event) {
        AurenAttackOverlay.renderHud(event);
    }

    @SubscribeEvent
    public static void renderBoundHead(RenderPlayerEvent.Post event) {
        AurenHeadCompanionRenderer.render(event);
    }

    @SubscribeEvent
    public static void cancelUnattachedMountedPlayer(RenderPlayerEvent.Pre event) {
        if (event.getEntity().getVehicle() instanceof AurenDragon
                && !AurenRiderFeatureRenderer.RENDERING_RIDERS.contains(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AurenMinecraft.AUREN.get(), AurenDragonRenderer::new);
    }
}
