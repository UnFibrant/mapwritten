package un.fibrant.mapwritten.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Server-side persistence for uploaded map slot files.
 */
public final class MapSlotStorage {
    private static final String ROOT_DIR = "mapwritten_shared";
    private static final String NAME_FILE = "map_name.txt";

    private MapSlotStorage() {
    }

    public static Path slotDirectory(MinecraftServer server, String mapId) {
        return server.getWorldPath(LevelResource.ROOT).resolve(ROOT_DIR).resolve(sanitizeMapId(mapId));
    }

    public static void resetSlot(MinecraftServer server, String mapId) throws IOException {
        final Path dir = slotDirectory(server, mapId);
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
        Files.createDirectories(dir);
    }

    public static void writeSlotName(MinecraftServer server, String mapId, String slotName) throws IOException {
        Files.writeString(slotDirectory(server, mapId).resolve(NAME_FILE), slotName);
    }

    public static String readSlotName(MinecraftServer server, String mapId, String fallback) {
        final Path path = slotDirectory(server, mapId).resolve(NAME_FILE);
        try {
            if (Files.isRegularFile(path)) {
                return Files.readString(path);
            }
        } catch (IOException ignored) {
        }
        return fallback;
    }

    public static void writeFile(MinecraftServer server, String mapId, String fileName, byte[] data) throws IOException {
        final Path target = slotDirectory(server, mapId).resolve(sanitizeFileName(fileName));
        Files.write(target, data);
    }

    public static List<Path> listFiles(MinecraftServer server, String mapId) throws IOException {
        final Path dir = slotDirectory(server, mapId);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals(NAME_FILE))
                    .toList();
        }
    }

    public static String sanitizeMapId(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            throw new IllegalArgumentException("Map id is blank");
        }
        final String cleaned = mapId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Invalid map id: " + mapId);
        }
        return cleaned.length() > 16 ? cleaned.substring(0, 16) : cleaned;
    }

    public static String sanitizeFileName(String fileName) {
        final String cleaned = fileName.replace('\\', '_').replace('/', '_');
        if (!cleaned.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid file name: " + fileName);
        }
        return cleaned;
    }

    public static boolean isAllowedMapFile(String name) {
        return "pins.json".equals(name) || name.matches("-?\\d+_-?\\d+\\.png");
    }
}

