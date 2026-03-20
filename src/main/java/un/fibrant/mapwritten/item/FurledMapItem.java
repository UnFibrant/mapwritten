package un.fibrant.mapwritten.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import un.fibrant.mapwritten.Mapwritten;
import un.fibrant.mapwritten.client.MapSwitchController;
import un.fibrant.mapwritten.client.MapSyncClient;
import un.fibrant.mapwritten.client.SharedMapManager;
import un.fibrant.mapwritten.network.InscribeMapPayload;
import un.fibrant.mapwritten.network.MapSyncPayload;

import java.util.List;

/**
 * A map item that links to a shared map id and opens that shared map when used.
 *
 * <p>Behaviour on right-click:
 * <ul>
 *   <li><b>Uninscribed, client side</b> – creates a new shared map id, inscribes the held
 *       item on server and uploads current local map files to server storage.</li>
 *   <li><b>Inscribed, client side</b> – activates that shared map, requests download and
 *       opens Mapwright map screen on it.</li>
 * </ul>
 */
public class FurledMapItem extends Item {

    public static final String TAG_MAP_ID = "map_id";
    public static final String TAG_MAP_NAME = "map_name";
//    public static final String LEGACY_TAG_SLOT_ID = "slot_id";
//    public static final String LEGACY_TAG_SLOT_NAME = "slot_name";

    public FurledMapItem(Properties properties) {
        super(properties);
    }


    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            if (!isInscribed(stack)) {
                final String mapId = SharedMapManager.newMapId();
                final String mapName = "Shared " + mapId;
                Mapwritten.LOG.info("[{}] Creating new shared map: {} ({})", Mapwritten.MODID, mapName, mapId);
                
                // Try to upload - if it fails, don't inscribe the item
                boolean uploadSuccess = MapSyncClient.uploadCurrentMap(mapId, mapName);
                if (!uploadSuccess) {
                    Mapwritten.LOG.warn("[{}] Upload failed - map not inscribed", Mapwritten.MODID);
                    player.displayClientMessage(

                            // TODO: normal translation + DATAGEN

                            Component.literal("Map is still saving. Try using Furled Map again in a moment."),
                            true
                    );
                    return InteractionResultHolder.fail(stack);
                }

                inscribe(stack, mapId, mapName);
                PacketDistributor.sendToServer(new InscribeMapPayload(mapId, mapName, hand));

                return InteractionResultHolder.success(stack);
            }

            final String mapId = getMapId(stack);
            final String mapName = getMapName(stack);
            Mapwritten.LOG.info("[{}] Opening existing shared map: {} ({})", Mapwritten.MODID, mapName, mapId);
            MapSwitchController.activateSharedMap(mapId, mapName);
            PacketDistributor.sendToServer(new MapSyncPayload.C2SDownloadRequest(mapId));
            SharedMapManager.beginSharedScreenSession();
            MapSwitchController.openMapScreen();
            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (isInscribed(stack)) {
            tooltipComponents.add(
                    // TODO: normal translation + DATAGEN
                    Component.literal("Shared map: " + getMapName(stack) + " [" + getMapId(stack) + "]")
                            .withStyle(s -> s.withColor(0xAAAAFF)));
            tooltipComponents.add(
                    // TODO: normal translation + DATAGEN
                    Component.literal("Right-click to open and sync this shared map")
                            .withStyle(s -> s.withColor(0x888888)));
        } else {
            tooltipComponents.add(
                    // TODO: normal translation + DATAGEN
                    Component.literal("Right-click to publish your current local map as shared")
                            .withStyle(s -> s.withColor(0x888888)));
        }
    }


    /** Stamps with the given shared map id and name. */
    public static ItemStack inscribe(ItemStack stack, String mapId, String mapName) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, existing -> {
            final CompoundTag tag = existing.copyTag();
            tag.putString(TAG_MAP_ID, mapId);
            tag.putString(TAG_MAP_NAME, mapName);
            return CustomData.of(tag);
        });
        return stack;
    }

    public static boolean isInscribed(ItemStack stack) {
        final CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return (tag.contains(TAG_MAP_ID) && !tag.getString(TAG_MAP_ID).isBlank());
    }

    public static String getMapId(ItemStack stack) {
        final CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TAG_MAP_ID)) {
            return tag.getString(TAG_MAP_ID);
        }
        return "local";
    }

    public static String getMapName(ItemStack stack) {
        final CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TAG_MAP_NAME)) {
            return tag.getString(TAG_MAP_NAME);
        }
        return "Shared Map";
    }
}
