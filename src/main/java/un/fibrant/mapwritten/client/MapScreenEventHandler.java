package un.fibrant.mapwritten.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import un.fibrant.mapwritten.Mapwritten;
import wawa.mapwright.map.MapScreen;


/**
 * Hooks into Mapwright's {@link MapScreen}:
 * <ul>
 *   <li>Renders the active shared-map label in the header.</li>
 *   <li>Resets shared-map state on world disconnect.</li>
 * </ul>
 * TODO: Custom MapScreen for shared maps
 */

@EventBusSubscriber(modid = Mapwritten.MODID, value = Dist.CLIENT)
public final class MapScreenEventHandler {
    private MapScreenEventHandler() {}

    //Screen event stuff
    @SubscribeEvent
    public static void onScreenInit(final ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof MapScreen)) return;
        if (!SharedMapManager.isSharedScreenSession()) {
            MapSwitchController.activateLocalMap();
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(final ScreenEvent.Closing event) {
        if (!(event.getScreen() instanceof MapScreen)) return;
        SharedMapManager.endSharedScreenSession();
    }
    @SubscribeEvent
    public static void onScreenRender(final ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof MapScreen screen)) return;
        final Minecraft mc = Minecraft.getInstance();
        final String text = SharedMapManager.getActiveMapLabel();
        final int textX = screen.width / 2 - mc.font.width(text) / 2;
        event.getGuiGraphics().drawString(mc.font, text, textX, 14, 0xFFFFFF, true);
    }


    /** Reset shared map state on world disconnect so it does not leak across sessions. */
    @SubscribeEvent
    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        SharedMapManager.reset();
    }
}
