package br.com.auren.minecraft.network;

import br.com.auren.minecraft.AurenMinecraft;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Only a verb and one of six radii cross the wire. No target IDs/coordinates. */
public record AurenMarkPayload(int radius, boolean clear) implements CustomPacketPayload {
    public static final Type<AurenMarkPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID, "mark_target"));
    public static final StreamCodec<ByteBuf, AurenMarkPayload> CODEC =
            ByteBufCodecs.fromCodec(RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("radius").forGetter(AurenMarkPayload::radius),
                    Codec.BOOL.fieldOf("clear").forGetter(AurenMarkPayload::clear)
            ).apply(i, AurenMarkPayload::new)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
