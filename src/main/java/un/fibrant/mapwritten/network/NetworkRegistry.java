package un.fibrant.mapwritten.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkRegistry {

    private NetworkRegistry() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar reg = event.registrar("3");

        // Kindly ask server to inscribe map
        reg.playToServer(
                InscribeMapPayload.TYPE,
                InscribeMapPayload.STREAM_CODEC,
                ServerPayloadHandler::onInscribeMap);

        // Begin upload
        reg.playToServer(
                MapSyncPayload.C2SUploadBegin.TYPE,
                MapSyncPayload.C2SUploadBegin.STREAM_CODEC,
                ServerPayloadHandler::onUploadBegin);

        // Upload file chunk
        reg.playToServer(
                MapSyncPayload.C2SUploadFile.TYPE,
                MapSyncPayload.C2SUploadFile.STREAM_CODEC,
                ServerPayloadHandler::onUploadFile);

        // End upload
        reg.playToServer(
                MapSyncPayload.C2SUploadEnd.TYPE,
                MapSyncPayload.C2SUploadEnd.STREAM_CODEC,
                ServerPayloadHandler::onUploadEnd);

        // Request download
        reg.playToServer(
                MapSyncPayload.C2SDownloadRequest.TYPE,
                MapSyncPayload.C2SDownloadRequest.STREAM_CODEC,
                ServerPayloadHandler::onDownloadRequest);

        // Download start
        reg.playToClient(
                MapSyncPayload.S2CDownloadStart.TYPE,
                MapSyncPayload.S2CDownloadStart.STREAM_CODEC,
                ClientPayloadHandler::onDownloadStart);

        // Download file chunk
        reg.playToClient(
                MapSyncPayload.S2CDownloadFile.TYPE,
                MapSyncPayload.S2CDownloadFile.STREAM_CODEC,
                ClientPayloadHandler::onDownloadFile);

        // Download end
        reg.playToClient(
                MapSyncPayload.S2CDownloadEnd.TYPE,
                MapSyncPayload.S2CDownloadEnd.STREAM_CODEC,
                ClientPayloadHandler::onDownloadEnd);
    }
}

