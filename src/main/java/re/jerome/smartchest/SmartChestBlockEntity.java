package re.jerome.smartchest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

// The lid, the items list and dropping the contents when broken are inherited: the
// copper golem queues only for a ChestBlockEntity (SPEC §3). Overridden for the
// capacity upgrade (SPEC §4.1): setItem, saving, loading and the creative copy.
public class SmartChestBlockEntity extends ChestBlockEntity {
	public static final int CAPACITY_SLOT = 0;
	public static final int COMPRESSION_SLOT = 1;
	private static final String[] UPGRADE_KEYS = {"capacity_upgrade", "compression_upgrade"};
	/** Items the capacity upgrade leaves at their vanilla stack (SPEC §4): shulker boxes, bundles, written books. */
	public static final TagKey<Item> NO_CAPACITY_UPGRADE = TagKey.create(Registries.ITEM, SmartChestMod.id("no_capacity_upgrade"));
	// The count field of ItemStack.CODEC stops here (SPEC §4.1).
	private static final int CODEC_MAX_COUNT = 99;
	private static final String LARGE_ITEMS_KEY = "large_items";

	// Apart from the inherited items, which are all the golem and hoppers see
	// (SPEC §2.2): only the screen shows these, and the comparator reads the
	// capacity upgrade's caps.
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

	/**
	 * The per-slot cap of {@code stack} under this capacity upgrade (SPEC §4): the
	 * tier, never below the vanilla stack, except for damageable and excluded items.
	 * Read on both sides, by the menu's slots, and by the comparator.
	 */
	public static int maxStackSize(ItemStack upgrade, ItemStack stack) {
		int tier = SmartChestItems.CAPACITY_UPGRADES.indexOf(upgrade.getItem());
		if (tier < 0 || stack.isDamageableItem() || stack.is(NO_CAPACITY_UPGRADE)) {
			return stack.getMaxStackSize();
		}
		return Math.max(SmartChestConfig.get().capacity().tiers().get(tier), stack.getMaxStackSize());
	}

	/**
	 * ChestBlockEntity's comparator signal, with the capacity caps instead of the
	 * vanilla ones, which the formula does not bound: 999 potions would give 260.
	 * A slot counts as full at most, for a pile left above a lowered cap.
	 */
	public static int redstoneSignal(Container chest, ItemStack upgrade) {
		float fill = 0;
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack stack = chest.getItem(slot);
			if (!stack.isEmpty()) {
				fill += Math.min(1, (float) stack.getCount() / maxStackSize(upgrade, stack));
			}
		}
		return Mth.lerpDiscrete(fill / chest.getContainerSize(), 0, 15);
	}

	public SimpleContainer upgrades() {
		return upgrades;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		// BaseContainerBlockEntity's, without its limitSize, which would cut a pile
		// above the vanilla stack: the menu's slots hold the caps, the golem and
		// hoppers stop at the vanilla stack by themselves (SPEC §4.1). A double
		// chest's CompoundContainer comes here too.
		unpackLootTable(null);
		getItems().set(slot, stack);
		setChanged();
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
		// The piles above 99, over the list ChestBlockEntity has just loaded. Their
		// count is kept even above a cap lowered since: nothing is lost. Not with a
		// loot table waiting, when ChestBlockEntity loads no items either.
		if (getLootTable() != null) {
			return;
		}
		NonNullList<ItemStack> items = getItems();
		for (LargeStack large : input.listOrEmpty(LARGE_ITEMS_KEY, LargeStack.CODEC)) {
			if (large.slot() < items.size()) {
				items.set(large.slot(), large.item().copyWithCount(large.count()));
			}
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		// ChestBlockEntity saves its items field with ItemStack.CODEC, which fails on
		// a pile above 99 (SPEC §4.1): it does not see them, they go under their own key.
		// With a loot table waiting, it saves no items, and neither does this.
		NonNullList<ItemStack> items = getItems();
		List<LargeStack> large = new ArrayList<>();
		for (int slot = 0; slot < items.size() && getLootTable() == null; slot++) {
			ItemStack stack = items.get(slot);
			if (stack.getCount() > CODEC_MAX_COUNT) {
				large.add(new LargeStack(slot, stack, stack.getCount()));
				items.set(slot, ItemStack.EMPTY);
			}
		}
		try {
			super.saveAdditional(output);
		} finally {
			// The same stacks back, not copies.
			large.forEach(stack -> items.set(stack.slot(), stack.item()));
		}
		if (!large.isEmpty()) {
			ValueOutput.TypedOutputList<LargeStack> list = output.list(LARGE_ITEMS_KEY, LargeStack.CODEC);
			large.forEach(list::add);
		}
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
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		// A creative copy (pick block with Ctrl) carries the items in the CONTAINER
		// component, which takes no pile above the vanilla stack: ItemStackTemplate
		// drops it on placement (validateStrict). The copy gets a vanilla stack of each.
		List<ItemStack> copy = new ArrayList<>();
		for (ItemStack stack : getItems()) {
			copy.add(stack.copyWithCount(Math.min(stack.getCount(), stack.getMaxStackSize())));
		}
		components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(copy));
	}

	@Override
	public void removeComponentsFromTag(ValueOutput output) {
		// The data a creative copy carries besides its components: no upgrades.
		// Loaded after the placement, they would overwrite what a merge put in. The
		// items travel in CONTAINER (collectImplicitComponents), so
		// BaseContainerBlockEntity discards Items; the large piles go with them.
		super.removeComponentsFromTag(output);
		output.discard(LARGE_ITEMS_KEY);
		for (String key : UPGRADE_KEYS) {
			output.discard(key);
		}
	}

	/**
	 * A pile above 99 in the save: the item as a stack of 1, which ItemStack.CODEC
	 * takes, and the count apart. {@code item} is the whole pile when saving.
	 */
	private record LargeStack(int slot, ItemStack item, int count) {
		static final Codec<LargeStack> CODEC = RecordCodecBuilder.create(i -> i.group(
						ExtraCodecs.UNSIGNED_BYTE.fieldOf("Slot").forGetter(LargeStack::slot),
						ItemStack.CODEC.fieldOf("item").forGetter(large -> large.item().copyWithCount(1)),
						ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(LargeStack::count))
				.apply(i, LargeStack::new));
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		// Drops the items, then the upgrades, which BlockEntity cannot see.
		// Containers.dropItemStack splits every pile into entities of 10 to 30, so
		// never one of 999 (SPEC §4.1).
		super.preRemoveSideEffects(pos, state);
		if (level != null) {
			Containers.dropContents(level, pos, upgrades);
		}
	}
}
