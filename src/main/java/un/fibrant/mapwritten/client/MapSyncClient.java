package un.fibrant.mapwritten.client;

import net.neoforged.neoforge.network.PacketDistributor;
import un.fibrant.mapwritten.Config;
import un.fibrant.mapwritten.Mapwritten;
import un.fibrant.mapwritten.network.MapSyncPayload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Client-side upload/download bridge for slot files.
 */
public final class MapSyncClient {
    private static final Map<String, DownloadState> ACTIVE_DOWNLOADS = new ConcurrentHashMap<>();

    private static final class DownloadState {
        private final Path targetPath;
        private final Path stagingPath;
        private int files;
        private int bytes;

        private DownloadState(Path targetPath, Path stagingPath) {
            this.targetPath = targetPath;
            this.stagingPath = stagingPath;
        }
    }

    private MapSyncClient() {
    }

    public static boolean uploadCurrentMap(String mapId, String mapName) {
        // Force PageManager to flush all pending saves to disk before reading files
        flushMapwrightBuffers();

        if (isMapwrightStillSaving()) {
            Mapwritten.LOG.debug("[{}] Map files are still being saved by Mapwright. Please wait a moment and try again.", Mapwritten.MODID);
            return false;
        }

        final Path slotPath = resolveUploadSourcePath();
        if (slotPath == null) {
            Mapwritten.LOG.warn("[{}] Cannot upload map '{}' because local path is unavailable", Mapwritten.MODID, mapId);
            return false;
        }

        PacketDistributor.sendToServer(new MapSyncPayload.C2SUploadBegin(mapId, mapName));
        int uploaded = 0;
        int totalBytes = 0;
        int discovered = 0;
        int skippedByType = 0;
        int skippedBySize = 0;
        int skippedByBudget = 0;

        try (Stream<Path> files = Files.list(slotPath)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                final String name = file.getFileName().toString();
                discovered++;
                if (!isAllowedMapFile(name)) {
                    skippedByType++;
                    continue;
                }
                
                final byte[] data;
                try {
                    data = Files.readAllBytes(file);
                } catch (IOException e) {
                    Mapwritten.LOG.warn("[{}] Skip map file '{}' during upload", Mapwritten.MODID, name);
                    continue;
                }
                
                if (data.length > Config.syncMaxFileBytes) {
                    skippedBySize++;
                    Mapwritten.LOG.debug("[{}] Skip upload file '{}' ({} bytes > {} bytes)",
                            Mapwritten.MODID, name, data.length, Config.syncMaxFileBytes);
                    continue;
                }
                if (uploaded + 1 > Config.syncMaxFileCount) {
                    skippedByBudget++;
                    break;
                }
                if (totalBytes + data.length > Config.syncMaxTotalBytes) {
                    skippedByBudget++;
                    break;
                }
                PacketDistributor.sendToServer(new MapSyncPayload.C2SUploadFile(mapId, name, data));
                uploaded++;
                totalBytes += data.length;
            }
        } catch (IOException e) {
            Mapwritten.LOG.error("[{}] Failed to upload map '{}'", Mapwritten.MODID, mapId, e);
            return false;
        }

        PacketDistributor.sendToServer(new MapSyncPayload.C2SUploadEnd(mapId, uploaded));
        Mapwritten.LOG.info("[{}] Upload '{}' from {}: discovered={}, sent={}, bytes={}, skippedType={}, skippedSize={}, skippedBudget={}",
                Mapwritten.MODID, mapId, slotPath, discovered, uploaded, totalBytes,
                skippedByType, skippedBySize, skippedByBudget);

        return true;
    }

    public static void onDownloadStart(MapSyncPayload.S2CDownloadStart payload) {
        final Path targetPath = MapSwitchController.resolveSharedMapPath(payload.mapId());
        if (targetPath == null) {
            Mapwritten.LOG.warn("[{}] Cannot prepare local storage for downloaded map '{}'", Mapwritten.MODID, payload.mapId());
            return;
        }

        try {
            final Path parent = targetPath.getParent();
            if (parent == null) {
                return;
            }
            final Path stagingPath = parent.resolve(targetPath.getFileName().toString() + ".incoming");
            clearDirectory(stagingPath);
            Files.createDirectories(stagingPath);
            ACTIVE_DOWNLOADS.put(payload.mapId(), new DownloadState(targetPath, stagingPath));
            
            if (!payload.found()) {
                Mapwritten.LOG.warn("[{}] No stored map data found on server for '{}'", Mapwritten.MODID, payload.mapId());
            }
        } catch (IOException e) {
            Mapwritten.LOG.error("[{}] Failed to prepare local map directory for '{}'", Mapwritten.MODID, payload.mapId(), e);
        }
    }

    public static void onDownloadFile(MapSyncPayload.S2CDownloadFile payload) {
        final DownloadState state = ACTIVE_DOWNLOADS.get(payload.mapId());
        if (state == null || !isAllowedMapFile(payload.fileName())) {
            return;
        }
        if (payload.data().length > Config.syncMaxFileBytes) {
            return;
        }
        if (state.files + 1 > Config.syncMaxFileCount) {
            return;
        }
        if (state.bytes + payload.data().length > Config.syncMaxTotalBytes) {
            return;
        }
        try {
            Files.write(state.stagingPath.resolve(payload.fileName()), payload.data());
            state.files++;
            state.bytes += payload.data().length;
        } catch (IOException e) {
            Mapwritten.LOG.error("[{}] Failed to store downloaded file '{}'", Mapwritten.MODID, payload.fileName(), e);
        }
    }

    public static void onDownloadEnd(MapSyncPayload.S2CDownloadEnd payload) {
        final DownloadState removed = ACTIVE_DOWNLOADS.remove(payload.mapId());
        if (removed != null) {
            try {
                clearDirectory(removed.targetPath);
                Files.createDirectories(removed.targetPath);
                try (Stream<Path> files = Files.list(removed.stagingPath)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        Files.move(file, removed.targetPath.resolve(file.getFileName()));
                    }
                }
                clearDirectory(removed.stagingPath);
            } catch (IOException e) {
                Mapwritten.LOG.error("[{}] Failed to finalize downloaded map '{}'", Mapwritten.MODID, payload.mapId(), e);
            }
        }
        if (removed != null && SharedMapManager.getActiveMapId().equals(payload.mapId())) {
            try {
                MapSwitchController.reloadCurrent();
            } catch (RuntimeException e) {
                Mapwritten.LOG.error("[{}] Failed to reload current shared map '{}' after download", Mapwritten.MODID,
                        payload.mapId(), e);
            }
        }
        Mapwritten.LOG.debug("[{}] Downloaded {} files for shared map {}", Mapwritten.MODID, payload.fileCount(), payload.mapId());
    }


    private static boolean isAllowedMapFile(String name) {
        return name.equals("pins.json") || name.matches("-?\\d+_-?\\d+\\.png");
    }

    private static Path resolveUploadSourcePath() {
        final Path activePath = MapSwitchController.getActiveMapPath();
        if (activePath == null) {
            return null;
        }

        if (SharedMapManager.isLocalActive()) {
            if (Files.isDirectory(activePath)) {
                return activePath;
            }
            // Fresh local maps may not have emitted files yet; create the slot folder eagerly.
            try {
                Files.createDirectories(activePath);
                return activePath;
            } catch (IOException e) {
                Mapwritten.LOG.warn("[{}] Cannot create local map directory for upload: {}", Mapwritten.MODID, activePath);
                return null;
            }
        }

        final Path basePath = activePath.getParent();
        if (basePath != null && Files.isDirectory(basePath)) {
            return basePath;
        }

        return Files.isDirectory(activePath) ? activePath : null;
    }

    private static void flushMapwrightBuffers() {
        try {
            final var pageManager = Class.forName("wawa.mapwright.MapwrightClient")
                    .getDeclaredField("PAGE_MANAGER");
            pageManager.setAccessible(true);
            final Object pm = pageManager.get(null);
            if (pm != null) {
                final var saveMethod = pm.getClass().getMethod("save", boolean.class);
                saveMethod.invoke(pm, false);  // false = don't close textures
                Mapwritten.LOG.debug("[{}] Flushed Mapwright buffers before upload", Mapwritten.MODID);
            }
        } catch (ReflectiveOperationException ignored) {

        }
    }

    private static boolean isMapwrightStillSaving() {
        try {
            final Class<?> pageClass = Class.forName("wawa.mapwright.data.Page");
            final var method = pageClass.getMethod("hasPendingSaves");
            final Object value = method.invoke(null);
            return value instanceof Boolean b && b;
        } catch (ReflectiveOperationException ignored) {
            // No patch for saving state
            return false;
        }
    }


    private static void clearDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}

