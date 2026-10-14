/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Adapted from IceAndFire Community Edition 1.21.1 DragonControlC2SPayload.
 * Source: https://github.com/IAFEnvoy/IceAndFire-CE
 */
package br.com.auren.minecraft.network;

import br.com.auren.minecraft.AurenMinecraft;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** CE rider controls: up=1, down=2, strike/breath=8; no client positions. */
public record AurenDragonControlPayload(int dragonId, byte controlState)
        implements CustomPacketPayload {
    public static final Type<AurenDragonControlPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID, "dragon_control"));
    public static final StreamCodec<ByteBuf, AurenDragonControlPayload> CODEC =
            ByteBufCodecs.fromCodec(RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("dragonId").forGetter(AurenDragonControlPayload::dragonId),
                    Codec.BYTE.fieldOf("controlState").forGetter(AurenDragonControlPayload::controlState)
            ).apply(i, AurenDragonControlPayload::new)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
