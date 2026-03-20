package un.fibrant.mapwritten;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;


import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = Mapwritten.MODID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue SYNC_MAX_FILE_COUNT = BUILDER
            .comment("Max number of files transferred for one shared map sync")
            .defineInRange("syncMaxFileCount", 256, 1, 4096);

    private static final ModConfigSpec.IntValue SYNC_MAX_FILE_BYTES = BUILDER
            .comment("Max size in bytes for one transferred map file")
            .defineInRange("syncMaxFileBytes", 8 * 1024 * 1024, 1024, 64 * 1024 * 1024);

    private static final ModConfigSpec.IntValue SYNC_MAX_TOTAL_BYTES = BUILDER
            .comment("Max total transferred size in bytes for one shared map sync")
            .defineInRange("syncMaxTotalBytes", 64 * 1024 * 1024, 4096, 256 * 1024 * 1024);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int syncMaxFileCount = 256;
    public static int syncMaxFileBytes = 8 * 1024 * 1024;
    public static int syncMaxTotalBytes = 64 * 1024 * 1024;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        syncMaxFileCount = SYNC_MAX_FILE_COUNT.get();
        syncMaxFileBytes = SYNC_MAX_FILE_BYTES.get();
        syncMaxTotalBytes = SYNC_MAX_TOTAL_BYTES.get();


    }
}
