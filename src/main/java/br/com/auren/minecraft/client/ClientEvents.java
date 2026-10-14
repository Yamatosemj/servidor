package br.com.auren.minecraft.client;

import br.com.auren.minecraft.AurenMinecraft;
import br.com.auren.minecraft.entity.AurenDragon;
import br.com.auren.minecraft.network.AurenDragonControlPayload;
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

@EventBusSubscriber(modid = AurenMinecraft.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    // Same descent key as the original IceAndFire Community Edition.
    private static final KeyMapping DRAGON_DOWN = new KeyMapping(
            "key.aurenminecraft.dragon_down", GLFW.GLFW_KEY_X, "key.categories.gameplay");
    private static int lastPilotId = -1;
    private static byte lastPilotControls = -1;

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(DRAGON_DOWN);
    }

    /** Port of CE ClientEvents.onLivingUpdate rider input, once per client tick. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            lastPilotId = -1;
            lastPilotControls = -1;
            return;
        }
        Entity mount = mc.player.getVehicle();
        if (!(mount instanceof AurenDragon dragon)
                || dragon.isSmallForm() || !dragon.isOwnedBy(mc.player)) {
            lastPilotId = -1;
            lastPilotControls = -1;
            return;
        }

        byte controls = (byte) ((mc.options.keyJump.isDown() ? 1 : 0)
                | (DRAGON_DOWN.isDown() ? 2 : 0));
        dragon.setRiderControlState(controls);
        if (dragon.getId() != lastPilotId || controls != lastPilotControls) {
            PacketDistributor.sendToServer(new AurenDragonControlPayload(
                    dragon.getId(), controls));
            lastPilotId = dragon.getId();
            lastPilotControls = controls;
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AurenMinecraft.AUREN.get(), AurenDragonRenderer::new);
    }
}
