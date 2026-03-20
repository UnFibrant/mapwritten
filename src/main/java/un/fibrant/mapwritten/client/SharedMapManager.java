package un.fibrant.mapwritten.client;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * Client-side active shared-map state.
 */
public final class SharedMapManager {
    private static final String DEFAULT_MAP_ID = "local";
    private static final String DEFAULT_MAP_NAME = "Local Map";
    private static String activeMapId = DEFAULT_MAP_ID;
    private static String activeMapName = DEFAULT_MAP_NAME;
    private static boolean sharedScreenSession;

    private SharedMapManager() {
    }

    public static String getActiveMapId() {
        return activeMapId;
    }

    public static String getActiveMapName() {
        return activeMapName;
    }

    public static String getActiveMapLabel() {
        return activeMapName + " [" + activeMapId + "]";
    }

    public static void activate(String mapId, String mapName) {
        activeMapId = sanitizeMapId(mapId);
        activeMapName = (mapName == null || mapName.isBlank()) ? ("Map " + activeMapId) : mapName;
    }

    public static void reset() {
        activeMapId = DEFAULT_MAP_ID;
        activeMapName = DEFAULT_MAP_NAME;
        sharedScreenSession = false;
    }

    public static boolean isLocalActive() {
        return DEFAULT_MAP_ID.equals(activeMapId);
    }

    public static void beginSharedScreenSession() {
        sharedScreenSession = true;
    }

    public static void endSharedScreenSession() {
        sharedScreenSession = false;
    }

    public static boolean isSharedScreenSession() {
        return sharedScreenSession;
    }

    public static String newMapId() {
        final String raw = Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits(), 36).toLowerCase(Locale.ROOT);
        final String compact = raw.replace("-", "");
        return compact.length() >= 8 ? compact.substring(0, 8) : String.format("%1$-8s", compact).replace(' ', '0');
    }

    public static String sanitizeMapId(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            return DEFAULT_MAP_ID;
        }
        final String cleaned = mapId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (cleaned.isBlank()) {
            return DEFAULT_MAP_ID;
        }
        return cleaned.length() > 16 ? cleaned.substring(0, 16) : cleaned;
    }

    public static Path applySharedPath(Path basePath) {
        if (isLocalActive()) {
            return basePath;
        }
        return basePath.resolve(sanitizeMapId(activeMapId));
    }
}

