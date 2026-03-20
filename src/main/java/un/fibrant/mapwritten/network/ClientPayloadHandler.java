package un.fibrant.mapwritten.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import un.fibrant.mapwritten.client.MapSyncClient;

/**
 * Client-side network handler for shared map sync payloads.
 */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {
    }

    public static void onDownloadStart(MapSyncPayload.S2CDownloadStart payload, IPayloadContext context) {
        context.enqueueWork(() -> MapSyncClient.onDownloadStart(payload));
    }

    public static void onDownloadFile(MapSyncPayload.S2CDownloadFile payload, IPayloadContext context) {
        context.enqueueWork(() -> MapSyncClient.onDownloadFile(payload));
    }

    public static void onDownloadEnd(MapSyncPayload.S2CDownloadEnd payload, IPayloadContext context) {
        context.enqueueWork(() -> MapSyncClient.onDownloadEnd(payload));
    }
}
