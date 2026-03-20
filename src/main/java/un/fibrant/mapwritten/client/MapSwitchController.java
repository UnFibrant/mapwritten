package un.fibrant.mapwritten.client;

import net.minecraft.client.Minecraft;
import un.fibrant.mapwritten.Mapwritten;
import wawa.mapwright.MapwrightClient;
import wawa.mapwright.map.MapScreen;
import org.joml.Vector2d;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Stack;

/**
 * Controller for activating shared maps and reloading Mapwright storage.
 *
 * <p>Shared map state lives in {@link SharedMapManager}.
 *
 * <p>After {@code reloadPageIO} constructs a {@code PageIO} with the base path:
 * <ol>
 *   <li>Use reflection to replace {@code PageIO.pagePath} with the shared map path.</li>
 *   <li>Re-read pins from the corrected path and push them back into {@code PageManager}.</li>
 * </ol>
 */
public final class MapSwitchController {
    private MapSwitchController() {
    }
    public static String getCurrentSlotName() {
        return SharedMapManager.getActiveMapLabel();
    }
    public static void activateSharedMap(String mapId, String mapName) {
        SharedMapManager.activate(mapId, mapName);
        reloadMapStorage();
    }

    public static void activateLocalMap() {
        if (SharedMapManager.isLocalActive()) {
            return;
        }
        SharedMapManager.activate("local", "Local Map");
        reloadMapStorage();
    }
    /**
     * Reload storage for whatever shared map is currently active.
     */
    public static void reloadCurrent() {
        reloadMapStorage();
    }

    /**
     * Resolves a local storage path for a shared map id based on the active Mapwright path.
     */
    public static Path resolveSharedMapPath(String mapId) {
        final Path active = getActiveMapPath();
        if (active == null) {
            return null;
        }
        final Path base = SharedMapManager.isLocalActive() ? active : active.getParent();
        if (base == null) {
            return null;
        }
        return base.resolve(SharedMapManager.sanitizeMapId(mapId));
    }

    public static void openMapScreen() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        final Vector2d playerPosition = new Vector2d((int) minecraft.player.getX(), (int) minecraft.player.getZ());
        MapwrightClient.targetPanningPosition.set(playerPosition);
        minecraft.setScreen(new MapScreen(playerPosition));
    }

    /**
     * Reads the current slotted page path from Mapwright's PageIO via reflection.
     */
    public static Path getActiveMapPath() {
        try {
            final Object pageIO = MapwrightClient.PAGE_MANAGER.pageIO;
            if (pageIO == null) {
                return null;
            }
            final Field pathField = pageIO.getClass().getDeclaredField("pagePath");
            pathField.setAccessible(true);
            return (Path) pathField.get(pageIO);
        } catch (ReflectiveOperationException e) {
            Mapwritten.LOG.error("[{}] Failed reading active slot path", Mapwritten.MODID, e);
            return null;
        }
    }

    private static void reloadMapStorage() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        softResetPageManager();
        try {
            // 1. Call reloadPageIO(Level, Minecraft) – creates a PageIO with the base path.
            boolean called = false;
            for (final Method candidate : MapwrightClient.PAGE_MANAGER.getClass().getMethods()) {
                if (candidate.getName().equals("reloadPageIO") && candidate.getParameterCount() == 2) {
                    candidate.invoke(MapwrightClient.PAGE_MANAGER, minecraft.level, minecraft);
                    called = true;
                    break;
                }
            }
            if (!called) {
                Mapwritten.LOG.warn("[{}] reloadPageIO(Level, Minecraft) not found on PageManager",
                        Mapwritten.MODID);
                return;
            }
            // 2. Replace pagePath inside PageIO with the active shared map directory.
            //    PageManager.pageIO is public; PageIO.pagePath is private final.
            final Object pageIO = MapwrightClient.PAGE_MANAGER.pageIO;
            if (pageIO == null) {
                Mapwritten.LOG.warn("[{}] pageIO is null after reload", Mapwritten.MODID);
                return;
            }
            final Field pathField = pageIO.getClass().getDeclaredField("pagePath");
            pathField.setAccessible(true);
            final Path basePath    = (Path) pathField.get(pageIO);
            final Path sharedPath = SharedMapManager.applySharedPath(basePath);
            pathField.set(pageIO, sharedPath);
            Files.createDirectories(sharedPath);
            // 3. Re-read pins from the corrected slotted path.
            //    PageIO.readPins() is public; PageManager.pins is private.
            final Method readPins = pageIO.getClass().getMethod("readPins");
            @SuppressWarnings("unchecked")
            final Map<Object, Object> sharedPins =
                    (Map<Object, Object>) readPins.invoke(pageIO);
            final Field pinsField = MapwrightClient.PAGE_MANAGER.getClass().getDeclaredField("pins");
            pinsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            final Map<Object, Object> pinsMap =
                    (Map<Object, Object>) pinsField.get(MapwrightClient.PAGE_MANAGER);
            pinsMap.clear();
            pinsMap.putAll(sharedPins);
            Mapwritten.LOG.debug("[{}] Activated shared map '{}' -> {}",
                    Mapwritten.MODID,
                    SharedMapManager.getActiveMapLabel(),
                    sharedPath);
        } catch (final ReflectiveOperationException e) {
            Mapwritten.LOG.error("[{}] Failed to reload Mapwright storage for shared map",
                    Mapwritten.MODID, e);
        } catch (final IOException e) {
            Mapwritten.LOG.error("[{}] Failed to create shared map directory", Mapwritten.MODID, e);
        }
    }

    private static void softResetPageManager() {
        try {
            // Save pending edits but do not close DynamicTexture objects.
            MapwrightClient.PAGE_MANAGER.save(false);

            final Class<?> pmClass = MapwrightClient.PAGE_MANAGER.getClass();

            final Field pagesField = pmClass.getDeclaredField("pages");
            pagesField.setAccessible(true);
            Object pagesObj = pagesField.get(MapwrightClient.PAGE_MANAGER);
            if (pagesObj instanceof Map<?, ?> pages) {
                pages.clear();
            }

            final Field pinsField = pmClass.getDeclaredField("pins");
            pinsField.setAccessible(true);
            Object pinsObj = pinsField.get(MapwrightClient.PAGE_MANAGER);
            if (pinsObj instanceof Map<?, ?> pins) {
                pins.clear();
            }

            final Field emptyCountField = pmClass.getDeclaredField("emptyCount");
            emptyCountField.setAccessible(true);
            emptyCountField.setInt(MapwrightClient.PAGE_MANAGER, 0);

            final Field loadedCountField = pmClass.getDeclaredField("loadedCount");
            loadedCountField.setAccessible(true);
            loadedCountField.setInt(MapwrightClient.PAGE_MANAGER, 0);

            final Field pageIOField = pmClass.getDeclaredField("pageIO");
            pageIOField.setAccessible(true);
            pageIOField.set(MapwrightClient.PAGE_MANAGER, null);

            clearHistoryStack(pmClass, "pastHistories");
            clearHistoryStack(pmClass, "futureHistories");
        } catch (ReflectiveOperationException e) {
            Mapwritten.LOG.error("[{}] Failed soft-reset of PageManager", Mapwritten.MODID, e);
            // Fallback: legacy hard clear if reflection mapping changes unexpectedly.
            MapwrightClient.PAGE_MANAGER.saveAndClear();
        }
    }

    private static void clearHistoryStack(Class<?> pmClass, String fieldName) throws ReflectiveOperationException {
        final Field stackField = pmClass.getDeclaredField(fieldName);
        stackField.setAccessible(true);
        Object stackObj = stackField.get(MapwrightClient.PAGE_MANAGER);
        if (stackObj instanceof Collection<?> c) {
            c.clear();
            return;
        }
        if (stackObj instanceof Stack<?> s) {
            s.clear();
        }
    }
}
