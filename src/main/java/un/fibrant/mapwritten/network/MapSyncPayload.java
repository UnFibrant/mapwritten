package un.fibrant.mapwritten.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import un.fibrant.mapwritten.Mapwritten;

/**
 * Upload/download protocol for shared maps.
 * Separate payload classes bc i hate networking but i needed to debug sync stuff
 */
public final class MapSyncPayload {
    private MapSyncPayload() {
    }

    public record C2SUploadBegin(String mapId, String mapName) implements CustomPacketPayload {
        public static final Type<C2SUploadBegin> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Mapwritten.MODID, "map_sync_upload_begin"));

        public static final StreamCodec<ByteBuf, C2SUploadBegin> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, C2SUploadBegin::mapId,
                        ByteBufCodecs.STRING_UTF8, C2SUploadBegin::mapName,
                        C2SUploadBegin::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SUploadFile(String mapId, String fileName, byte[] data) implements CustomPacketPayload {
        public static final Type<C2SUploadFile> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Mapwritten.MODID, "map_sync_upload_file"));

        public static final StreamCodec<ByteBuf, C2SUploadFile> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, C2SUploadFile::mapId,
                        ByteBufCodecs.STRING_UTF8, C2SUploadFile::fileName,
                        ByteBufCodecs.BYTE_ARRAY, C2SUploadFile::data,
                        C2SUploadFile::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SUploadEnd(String mapId, int fileCount) implements CustomPacketPayload {
        public static final Type<C2SUploadEnd> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Mapwritten.MODID, "map_sync_upload_end"));

        public static final StreamCodec<ByteBuf, C2SUploadEnd> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, C2SUploadEnd::mapId,
                        ByteBufCodecs.INT, C2SUploadEnd::fileCount,
                        C2SUploadEnd::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SDownloadRequest(String mapId) implements CustomPacketPayload {
        public static final Type<C2SDownloadRequest> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Mapwritten.MODID, "map_sync_download_request"));

        public static final StreamCodec<ByteBuf, C2SDownloadRequest> STREAM_CODEC =
                ByteBufCodecs.STRING_UTF8.map(C2SDownloadRequest::new, C2SDownloadRequest::mapId);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }


    /**
     * Server starts sending map data or thats it's no(
     */
    public record S2CDownloadStart(String mapId, String mapName, boolean found) implements CustomPacketPayload {
        public static final Type<S2CDownloadStart> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Mapwritten.MODID, "map_sync_download_start"));

        public static final StreamCodec<ByteBuf, S2CDownloadStart> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, S2CDownloadStart::mapId,
                        ByteBufCodecs.STRING_UTF8, S2CDownloadStart::mapName,
                        ByteBufCodecs.BOOL, S2CDownloadStart::found,
                        S2CDownloadStart::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CDownloadFile(String mapId, String fileName, byte[] data) implements CustomPacketPayload {
        public static final Type<S2CDownloadFile> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Mapwritten.MODID, "map_sync_download_file"));

        public static final StreamCodec<ByteBuf, S2CDownloadFile> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, S2CDownloadFile::mapId,
                        ByteBufCodecs.STRING_UTF8, S2CDownloadFile::fileName,
                        ByteBufCodecs.BYTE_ARRAY, S2CDownloadFile::data,
                        S2CDownloadFile::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CDownloadEnd(String mapId, int fileCount) implements CustomPacketPayload {
        public static final Type<S2CDownloadEnd> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Mapwritten.MODID, "map_sync_download_end"));

        public static final StreamCodec<ByteBuf, S2CDownloadEnd> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, S2CDownloadEnd::mapId,
                        ByteBufCodecs.INT, S2CDownloadEnd::fileCount,
                        S2CDownloadEnd::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}

