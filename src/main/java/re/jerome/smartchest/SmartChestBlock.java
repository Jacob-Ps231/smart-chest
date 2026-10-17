package re.jerome.smartchest;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Extending ChestBlock is what the copper golem needs (SPEC §3), and it brings the
// double chest, hoppers and comparators along. ChestBlock.chestCanConnectTo is
// state.is(this): a Smart Chest only pairs with the same block, so never with a
// vanilla chest nor with another wood: DoubleBlockCombiner tests the same identity.
public class SmartChestBlock extends ChestBlock {
	private final String wood;

	public SmartChestBlock(String wood, Properties properties) {
		// The supplier defers the read: the block entity type is built after the
		// blocks, from the set of them.
		super(() -> SmartChestBlocks.BLOCK_ENTITY, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties);
		this.wood = wood;
	}

	/** The wood name, which also names the chest textures. */
	public String wood() {
		return wood;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		// ChestBlock hard-codes a vanilla ChestBlockEntity here.
		return new SmartChestBlockEntity(pos, state);
	}
}
