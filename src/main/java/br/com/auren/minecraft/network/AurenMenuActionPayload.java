package br.com.auren.minecraft.network;

import br.com.auren.minecraft.AurenMinecraft;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Small, untrusted UI request. The server resolves ownership, dragon distance,
 * target ray casts, collisions and action permissions itself.
 */
public record AurenMenuActionPayload(int action, int radius) implements CustomPacketPayload {
    public static final int SHOULDER = 1;
    public static final int REST = 2;
    public static final int FORM = 3;
    public static final int FOLLOW_OR_STAY = 4;
    public static final int TARGET_CREATURE = 5;
    public static final int TARGET_AREA = 6;
    public static final int STOP_ATTACK = 7;
    public static final int TOGGLE_AREA = 8;

    public static final Type<AurenMenuActionPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurenMinecraft.MOD_ID, "menu_action"));
    public static final StreamCodec<ByteBuf, AurenMenuActionPayload> CODEC =
            ByteBufCodecs.fromCodec(RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("action").forGetter(AurenMenuActionPayload::action),
                    Codec.INT.fieldOf("radius").forGetter(AurenMenuActionPayload::radius)
            ).apply(i, AurenMenuActionPayload::new)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
