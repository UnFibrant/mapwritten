package un.fibrant.mapwritten.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import un.fibrant.mapwritten.Mapwritten;

import java.util.Map;

/**
 * Kindly ask server to inscribe map
 */
public record InscribeMapPayload(String mapId, String mapName, InteractionHand hand)
        implements CustomPacketPayload {


    public static final Type<InscribeMapPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(Mapwritten.MODID, "inscribe_map"));

    private static final StreamCodec<ByteBuf, InteractionHand> HAND_CODEC =
            ByteBufCodecs.BOOL.map(
                    b -> b ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                    h -> h == InteractionHand.MAIN_HAND);


    public static final StreamCodec<ByteBuf, InscribeMapPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,    InscribeMapPayload::mapId,
                    ByteBufCodecs.STRING_UTF8,    InscribeMapPayload::mapName,
                    HAND_CODEC,                   InscribeMapPayload::hand,
                    InscribeMapPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}