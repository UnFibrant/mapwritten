package un.fibrant.mapwritten.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import un.fibrant.mapwritten.block.entity.MapWorkplaceEntity;

public class MapWorkplaceBlock extends BaseEntityBlock {

    public static final MapCodec<MapWorkplaceBlock> CODEC = simpleCodec(MapWorkplaceBlock::new);

    public MapWorkplaceBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) {return RenderShape.MODEL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {return new MapWorkplaceEntity(pos, state);};
}
