package un.fibrant.mapwritten.block.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import un.fibrant.mapwritten.Mapwritten;
import un.fibrant.mapwritten.block.MapWorkplaceBlock;
import un.fibrant.mapwritten.block.ModBlocks;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Mapwritten.MODID);


    public static final Supplier<BlockEntityType<MapWorkplaceEntity>> MAP_WORKPLACE_BE =
            BLOCK_ENTITIES.register("map_workplace_be", () ->
                    BlockEntityType.Builder.of(MapWorkplaceEntity::new, ModBlocks.MAP_WORKPLACE_BLOCK.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
