package re.jerome.smartchest;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

// Everything else -- items, saving, lid, dropping the contents when broken -- is
// inherited: the copper golem queues only for a ChestBlockEntity (SPEC §3).
public class SmartChestBlockEntity extends ChestBlockEntity {
	public static final int CAPACITY_SLOT = 0;
	public static final int COMPRESSION_SLOT = 1;
	private static final String[] UPGRADE_KEYS = {"capacity_upgrade", "compression_upgrade"};

	// Apart from the inherited items, which are all the golem, hoppers and
	// comparators see (SPEC §2.2): only the screen shows these.
	private final SimpleContainer upgrades = new SimpleContainer(UPGRADE_KEYS.length) {
		@Override
		public void setChanged() {
			SmartChestBlockEntity.this.setChanged();
		}
	};

	public SmartChestBlockEntity(BlockPos pos, BlockState state) {
		super(SmartChestBlocks.BLOCK_ENTITY, pos, state);
	}

	/** Whether the upgrade slot {@code slot} takes this item. Read on both sides, by the menu's slots. */
	public static boolean acceptsUpgrade(int slot, ItemStack stack) {
		return slot == CAPACITY_SLOT ? SmartChestItems.isCapacityUpgrade(stack) : SmartChestItems.isCompressionUpgrade(stack);
	}

	public SimpleContainer upgrades() {
		return upgrades;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.smartchest.smart_chest");
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		// A single chest; ChestBlock asks SmartChestBlock for a double one.
		return SmartChestMenu.single(containerId, inventory, this, upgrades);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		// Into the list directly: setItem would call setChanged during the load.
		for (int slot = 0; slot < UPGRADE_KEYS.length; slot++) {
			upgrades.getItems().set(slot, input.read(UPGRADE_KEYS[slot], ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		// One item per slot, so ItemStack's cap of 99 does not matter here (SPEC §4.1).
		for (int slot = 0; slot < UPGRADE_KEYS.length; slot++) {
			output.store(UPGRADE_KEYS[slot], ItemStack.OPTIONAL_CODEC, upgrades.getItem(slot));
		}
	}

	@Override
	public void setBlockState(BlockState state) {
		// LevelChunk.setBlockState calls this when the block stays a Smart Chest but its
		// state changes. Two chests merge when the one already there turns from SINGLE
		// to LEFT or RIGHT, by updateShape, after the other one and its block entity
		// are in place: whichever was placed first, this is the moment, and only once.
		ChestType before = getBlockState().getValue(ChestBlock.TYPE);
		super.setBlockState(state);
		if (before == ChestType.SINGLE && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE && level instanceof ServerLevel) {
			mergeUpgrades();
		}
	}

	/**
	 * Only the first half's upgrades show and apply (SPEC §2.1): the other half's go
	 * into its free slots, or out of the front of the chest when they are taken.
	 */
	private void mergeUpgrades() {
		BlockPos otherPos = ChestBlock.getConnectedBlockPos(worldPosition, getBlockState());
		ChestType type = getBlockState().getValue(ChestBlock.TYPE);
		// Never load a chunk from here; and only the real partner, which updateShape
		// paired with this one: same block, the opposite half.
		if (!level.isLoaded(otherPos)
				|| !(level.getBlockEntity(otherPos) instanceof SmartChestBlockEntity other)
				|| !other.getBlockState().is(getBlockState().getBlock())
				|| other.getBlockState().getValue(ChestBlock.TYPE) != type.getOpposite()) {
			return;
		}
		// The first half is the RIGHT one (ChestBlock.getBlockType).
		boolean main = type == ChestType.RIGHT;
		SmartChestBlockEntity first = main ? this : other;
		SmartChestBlockEntity second = main ? other : this;
		for (int slot = 0; slot < UPGRADE_KEYS.length; slot++) {
			ItemStack upgrade = second.upgrades.removeItemNoUpdate(slot);
			if (upgrade.isEmpty()) {
				continue;
			}
			if (first.upgrades.getItem(slot).isEmpty()) {
				first.upgrades.setItem(slot, upgrade);
			} else {
				// As the contents of a broken chest, whatever the blockDrops rule, which
				// Block.popResource obeys.
				Vec3 front = Vec3.atCenterOf(second.worldPosition)
						.relative(second.getBlockState().getValue(ChestBlock.FACING), 0.75);
				Containers.dropItemStack(level, front.x, front.y, front.z, upgrade);
			}
			second.setChanged();
		}
	}

	@Override
	public void removeComponentsFromTag(ValueOutput output) {
		// The data a creative copy (pick block with Ctrl) carries: no upgrades, as no
		// items. Loaded after the placement, they would overwrite what a merge put in.
		super.removeComponentsFromTag(output);
		for (String key : UPGRADE_KEYS) {
			output.discard(key);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		// Drops the items, then the upgrades, which BlockEntity cannot see.
		super.preRemoveSideEffects(pos, state);
		if (level != null) {
			Containers.dropContents(level, pos, upgrades);
		}
	}
}
