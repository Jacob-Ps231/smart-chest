package re.jerome.smartchest;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Everything else -- items, saving, lid, dropping the contents when broken -- is
// inherited: the copper golem queues only for a ChestBlockEntity (SPEC §3).
public class SmartChestBlockEntity extends ChestBlockEntity {
	public SmartChestBlockEntity(BlockPos pos, BlockState state) {
		super(SmartChestBlocks.BLOCK_ENTITY, pos, state);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.smartchest.smart_chest");
	}
}
