package un.fibrant.mapwritten.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class MapWorkplaceEntity extends BlockEntity  {
    public MapWorkplaceEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.MAP_WORKPLACE_BE.get(), pos, state); }


    private ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if(level != null && level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public boolean hasMap = false;

}
