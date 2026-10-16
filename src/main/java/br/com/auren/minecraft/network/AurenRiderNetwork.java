/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Adapted from IceAndFire CE NetworkManager / ServerNetworkHandlers.
 * Source: https://github.com/IAFEnvoy/IceAndFire-CE
 */
package br.com.auren.minecraft.network;

import br.com.auren.minecraft.entity.AurenDragon;
import br.com.auren.minecraft.AurenCommands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class AurenRiderNetwork {
    private AurenRiderNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(AurenDragonControlPayload.ID,
                AurenDragonControlPayload.CODEC, AurenRiderNetwork::handleControl);
        registrar.playToServer(AurenMarkPayload.ID,
                AurenMarkPayload.CODEC, AurenRiderNetwork::handleMark);
    }

    private static void handleMark(AurenMarkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AurenCommands.handleMarkKey(player, payload.radius(), payload.clear());
            }
        });
    }

    private static void handleControl(AurenDragonControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            Entity entity = player.level().getEntity(payload.dragonId());
            // CE checks riding relationship and ownership before accepting input.
            // No position is accepted from the client, preventing teleport abuse.
            if (entity instanceof AurenDragon dragon
                    && !dragon.isSmallForm() && !dragon.isOnShoulder()
                    && player.getVehicle() == dragon && dragon.isOwnedBy(player)) {
                dragon.setRiderControlState((byte) (payload.controlState() & 15));
            }
        });
    }
}
