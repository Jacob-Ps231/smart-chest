package re.jerome.smartchest;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

	// ChestBlock.getAnalogOutputSignal's container, measured against the caps of the
	// first half's capacity upgrade, which applies to both halves.
	private static final DoubleBlockCombiner.Combiner<ChestBlockEntity, Integer> SIGNALS =
			new DoubleBlockCombiner.Combiner<>() {
				@Override
				public Integer acceptDouble(ChestBlockEntity first, ChestBlockEntity second) {
					return SmartChestBlockEntity.redstoneSignal(new CompoundContainer(first, second), capacityUpgrade(first));
				}

				@Override
				public Integer acceptSingle(ChestBlockEntity single) {
					return SmartChestBlockEntity.redstoneSignal(single, capacityUpgrade(single));
				}

				@Override
				public Integer acceptNone() {
					return 0;
				}
			};

	// The chest's container with the first half's upgrades, as for the menu, which
	// apply to both halves. Clears both halves' flags after the pass, whose own
	// setItem calls have raised them.
	private static final DoubleBlockCombiner.Combiner<ChestBlockEntity, Boolean> COMPRESSIONS =
			new DoubleBlockCombiner.Combiner<>() {
				@Override
				public Boolean acceptDouble(ChestBlockEntity first, ChestBlockEntity second) {
					compress(new CompoundContainer(first, second), (SmartChestBlockEntity) first);
					((SmartChestBlockEntity) second).compressionPending = false;
					return true;
				}

				@Override
				public Boolean acceptSingle(ChestBlockEntity single) {
					compress(single, (SmartChestBlockEntity) single);
					return true;
				}

				@Override
				public Boolean acceptNone() {
					return false;
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

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		// ChestBlock's reads Container.getMaxStackSize(ItemStack): the vanilla stack.
		return combine(state, level, pos, false).apply(SIGNALS);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// ChestBlock's recheckOpen, then the compression SmartChestBlockEntity.setChanged
		// scheduled. Even under a block or a cat (true), unlike the comparator's combine.
		super.tick(state, level, pos, random);
		if (level.getBlockEntity(pos) instanceof SmartChestBlockEntity chest && chest.compressionPending) {
			combine(state, level, pos, true).apply(COMPRESSIONS);
		}
	}

	private static void compress(Container chest, SmartChestBlockEntity first) {
		if (SmartChestConfig.get().compression().enabled()
				&& SmartChestItems.isCompressionUpgrade(first.upgrades().getItem(SmartChestBlockEntity.COMPRESSION_SLOT))) {
			Set<Item> forced = new HashSet<>();
			for (int slot = SmartChestBlockEntity.FIRST_FORCED_SLOT; slot < SmartChestBlockEntity.UPGRADE_SLOTS; slot++) {
				ItemStack stack = first.upgrades().getItem(slot);
				if (!stack.isEmpty()) {
					forced.add(stack.getItem());
				}
			}
			// Then the whole chest is sorted with the last button used (SPEC §5.2), and
			// compressed again: the merge may free the slot a block lacked. It ends, as
			// each compression lowers the number of items. The sort's writes raise the
			// flag again: cleared below.
			if (SmartChestCompression.compress(chest, capacityUpgrade(first), forced)) {
				do {
					SmartChestSorting.sort(chest, capacityUpgrade(first), first.sortMode);
				} while (SmartChestCompression.compress(chest, capacityUpgrade(first), forced));
			}
		}
		first.compressionPending = false;
	}

	private static ItemStack capacityUpgrade(ChestBlockEntity chest) {
		return ((SmartChestBlockEntity) chest).upgrades().getItem(SmartChestBlockEntity.CAPACITY_SLOT);
	}

	/** ChestBlock's double chest provider (an anonymous class there), step for step. */
	private record DoubleMenuProvider(SmartChestBlockEntity first, SmartChestBlockEntity second) implements MenuProvider {
		@Override
		public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
			if (first.canOpen(player) && second.canOpen(player)) {
				first.unpackLootTable(inventory.player);
				second.unpackLootTable(inventory.player);
				return SmartChestMenu.twoHalves(containerId, inventory, new CompoundContainer(first, second), first);
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
