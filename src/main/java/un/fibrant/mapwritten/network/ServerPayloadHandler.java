package un.fibrant.mapwritten.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import un.fibrant.mapwritten.Config;
import un.fibrant.mapwritten.Mapwritten;
import un.fibrant.mapwritten.item.FurledMapItem;
import un.fibrant.mapwritten.server.MapSlotStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side handler for {@link InscribeMapPayload}.
 *
 * <p>Received when the player right-clicks an uninscribed FurledMapItem client-side.
 * The server stamps the held item's CUSTOM_DATA component with the requested slot UUID.
 */
public final class ServerPayloadHandler {

    private static final Map<UUID, UploadState> ACTIVE_UPLOADS = new ConcurrentHashMap<>();

    private record UploadState(String mapId, int files, int bytes) {
        private UploadState withFile(int fileBytes) {
            return new UploadState(mapId, files + 1, bytes + fileBytes);
        }
    }

    private ServerPayloadHandler() {
    }

    public static void onInscribeMap(InscribeMapPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            final ItemStack stack = player.getItemInHand(payload.hand());
            if (!(stack.getItem() instanceof FurledMapItem)) {
                return;
            }
            if (FurledMapItem.isInscribed(stack)) {
                // Already inscribed – ignore duplicate requests.
                return;
            }
            FurledMapItem.inscribe(stack, payload.mapId(), payload.mapName());
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            Mapwritten.LOG.debug("[{}] Inscribed FurledMap for {} with map {}",
                    Mapwritten.MODID, player.getName().getString(), payload.mapId());
        });
    }

    public static void onUploadBegin(MapSyncPayload.C2SUploadBegin payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            try {
                final String mapId = MapSlotStorage.sanitizeMapId(payload.mapId());
                MapSlotStorage.resetSlot(player.server, mapId);
                ACTIVE_UPLOADS.put(player.getUUID(), new UploadState(mapId, 0, 0));
                MapSlotStorage.writeSlotName(player.server, mapId, payload.mapName());
            } catch (Exception e) {
                Mapwritten.LOG.error("[{}] Failed to initialize upload for map {}", Mapwritten.MODID, payload.mapId(), e);
            }
        });
    }

    public static void onUploadFile(MapSyncPayload.C2SUploadFile payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            UploadState state = ACTIVE_UPLOADS.get(player.getUUID());
            final String mapId;
            try {
                mapId = MapSlotStorage.sanitizeMapId(payload.mapId());
            } catch (IllegalArgumentException e) {
                return;
            }
            if (state == null || !state.mapId().equals(mapId)) {
                Mapwritten.LOG.debug("[{}] Drop upload file '{}' from {}: no active upload for map {}",
                        Mapwritten.MODID, payload.fileName(), player.getName().getString(), mapId);
                return;
            }
            if (!MapSlotStorage.isAllowedMapFile(payload.fileName())) {
                Mapwritten.LOG.debug("[{}] Drop upload file '{}' from {}: disallowed file name",
                        Mapwritten.MODID, payload.fileName(), player.getName().getString());
                return;
            }
            if (payload.data().length > Config.syncMaxFileBytes) {
                Mapwritten.LOG.debug("[{}] Drop upload file '{}' from {}: {} bytes > {} bytes",
                        Mapwritten.MODID, payload.fileName(), player.getName().getString(),
                        payload.data().length, Config.syncMaxFileBytes);
                return;
            }
            if (state.files() + 1 > Config.syncMaxFileCount) {
                Mapwritten.LOG.debug("[{}] Drop upload file '{}' from {}: file count limit reached",
                        Mapwritten.MODID, payload.fileName(), player.getName().getString());
                return;
            }
            if (state.bytes() + payload.data().length > Config.syncMaxTotalBytes) {
                Mapwritten.LOG.debug("[{}] Drop upload file '{}' from {}: total bytes limit reached",
                        Mapwritten.MODID, payload.fileName(), player.getName().getString());
                return;
            }
            try {
                final String fileName = MapSlotStorage.sanitizeFileName(payload.fileName());
                MapSlotStorage.writeFile(player.server, state.mapId(), fileName, payload.data());
                ACTIVE_UPLOADS.put(player.getUUID(), state.withFile(payload.data().length));
            } catch (Exception e) {
                Mapwritten.LOG.error("[{}] Failed to store uploaded file '{}' for map {}",
                        Mapwritten.MODID, payload.fileName(), payload.mapId(), e);
            }
        });
    }

    public static void onUploadEnd(MapSyncPayload.C2SUploadEnd payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            final UploadState state = ACTIVE_UPLOADS.remove(player.getUUID());
            if (state == null) {
                Mapwritten.LOG.debug("[{}] Upload end for {} from {} without active state", Mapwritten.MODID,
                        payload.mapId(), player.getName().getString());
                return;
            }
            Mapwritten.LOG.info("[{}] Stored shared map {} from {}: files={}, bytes={}", Mapwritten.MODID,
                    state.mapId(), player.getName().getString(), state.files(), state.bytes());
        });
    }

    public static void onDownloadRequest(MapSyncPayload.C2SDownloadRequest payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            try {
                final String mapId = MapSlotStorage.sanitizeMapId(payload.mapId());
                final Path slotDir = MapSlotStorage.slotDirectory(player.server, mapId);
                if (!Files.isDirectory(slotDir)) {
                    PacketDistributor.sendToPlayer(player,
                            new MapSyncPayload.S2CDownloadStart(mapId, mapId, false));
                    PacketDistributor.sendToPlayer(player, new MapSyncPayload.S2CDownloadEnd(mapId, 0));
                    return;
                }

                final String slotName = MapSlotStorage.readSlotName(player.server, mapId, mapId);
                PacketDistributor.sendToPlayer(player, new MapSyncPayload.S2CDownloadStart(mapId, slotName, true));

                int sent = 0;
                int total = 0;
                try {
                    for (Path path : MapSlotStorage.listFiles(player.server, mapId)) {
                        final byte[] bytes = Files.readAllBytes(path);
                        if (bytes.length > Config.syncMaxFileBytes || sent + 1 > Config.syncMaxFileCount
                                || total + bytes.length > Config.syncMaxTotalBytes) {
                            break;
                        }
                        final String fileName = path.getFileName().toString();
                        if (!MapSlotStorage.isAllowedMapFile(fileName)) {
                            continue;
                        }
                        PacketDistributor.sendToPlayer(player, new MapSyncPayload.S2CDownloadFile(mapId, fileName, bytes));
                        sent++;
                        total += bytes.length;
                    }
                } catch (IOException e) {
                    Mapwritten.LOG.error("[{}] Failed to stream map {} to {}",
                            Mapwritten.MODID, mapId, player.getName().getString(), e);
                }

                PacketDistributor.sendToPlayer(player, new MapSyncPayload.S2CDownloadEnd(mapId, sent));
            } catch (Exception e) {
                Mapwritten.LOG.error("[{}] Failed handling map download request for {}",
                        Mapwritten.MODID, payload.mapId(), e);
            }
        });
    }
}
