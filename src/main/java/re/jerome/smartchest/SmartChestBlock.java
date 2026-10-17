package re.jerome.smartchest;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// Extending ChestBlock is what the copper golem needs (SPEC §3), and it brings the
// double chest, hoppers and comparators along. ChestBlock.chestCanConnectTo is
// state.is(this): a Smart Chest only pairs with the same block, so never with a
// vanilla chest nor with another wood: DoubleBlockCombiner tests the same identity.
public class SmartChestBlock extends ChestBlock {
	// ChestBlock's MENU_PROVIDER_COMBINER, private, with our menu instead of
	// ChestMenu.sixRows. The first half is the ChestType.RIGHT one, whose items fill
	// the top rows (ChestBlock.getBlockType): its upgrades are the ones that apply,
	// and the only ones left once merged (SmartChestBlockEntity.setBlockState).
	private static final DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<MenuProvider>> MENU_PROVIDERS =
			new DoubleBlockCombiner.Combiner<>() {
				@Override
				public Optional<MenuProvider> acceptDouble(ChestBlockEntity first, ChestBlockEntity second) {
					return Optional.of(new DoubleMenuProvider((SmartChestBlockEntity) first, (SmartChestBlockEntity) second));
				}

				@Override
				public Optional<MenuProvider> acceptSingle(ChestBlockEntity single) {
					return Optional.of(single);
				}

				@Override
				public Optional<MenuProvider> acceptNone() {
					return Optional.empty();
				}
			};

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

	@Override
	protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		return combine(state, level, pos, false).apply(MENU_PROVIDERS).orElse(null);
	}

	/** ChestBlock's double chest provider (an anonymous class there), step for step. */
	private record DoubleMenuProvider(SmartChestBlockEntity first, SmartChestBlockEntity second) implements MenuProvider {
		@Override
		public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
			if (first.canOpen(player) && second.canOpen(player)) {
				first.unpackLootTable(inventory.player);
				second.unpackLootTable(inventory.player);
				return SmartChestMenu.twoHalves(containerId, inventory, new CompoundContainer(first, second),
						first.upgrades());
			}
			if (!player.isSpectator()) {
				Direction toSecond = ChestBlock.getConnectedDirection(first.getBlockState());
				Vec3 middle = Vec3.atCenterOf(first.getBlockPos()).add(toSecond.getStepX() / 2.0, 0, toSecond.getStepZ() / 2.0);
				BaseContainerBlockEntity.sendChestLockedNotifications(middle, player, getDisplayName());
			}
			return null;
		}

		@Override
		public Component getDisplayName() {
			if (first.hasCustomName()) {
				return first.getDisplayName();
			}
			if (second.hasCustomName()) {
				return second.getDisplayName();
			}
			return Component.translatable("container.smartchest.double_smart_chest");
		}
	}
}
