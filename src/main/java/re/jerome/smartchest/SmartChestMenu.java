package re.jerome.smartchest;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// A ChestMenu so that the chest counts its viewer: ChestBlockEntity's opener counter
// only recognises a ChestMenu over its own container, and closes the lid on anyone
// else. Slots, in order: the chest's rows, the player's inventory (both from
// ChestMenu), then the five upgrade slots -- capacity, crafter, three forced
// compressions -- of the first half for a double chest, the only ones it has once
// merged (SPEC §2.1).
//
// The capacity caps live in the chest slots (SPEC §4.1), on both sides: the client
// reads the capacity upgrade from its copy of the upgrade slot, which the server keeps
// in sync like any other slot.
public class SmartChestMenu extends ChestMenu {
	/** Upgrade slot positions, beside the chest rows (the screen draws them there). */
	public static final int UPGRADE_X = 186;
	// Drawn by tools/GenerateUpgrades.java, in the gui atlas.
	private static final Identifier CAPACITY_ICON = SmartChestMod.id("container/slot/capacity_upgrade");
	private static final Identifier COMPRESSION_ICON = SmartChestMod.id("container/slot/compression_upgrade");
	private static final Identifier FORCED_ICON = SmartChestMod.id("container/slot/forced_compression");

	private final Container upgrades;
	private final int chestEnd;
	private final int upgradesStart;
	// The client has no recipes: its forced compression slots take anything (SmartChestBlockEntity.acceptsUpgrade).
	private final boolean client;
	// The click being handled, for the capacity slot's mayPickup and the forced
	// slots' mayPlace (see there).
	private ContainerInput input;

	private SmartChestMenu(MenuType<?> type, int containerId, Inventory inventory, Container chest, int rows,
			Container upgrades) {
		super(type, containerId, inventory, chest, rows);
		this.upgrades = upgrades;
		chestEnd = rows * 9;
		// ChestMenu's chest slots are plain Slots (its addChestGrid is private):
		// swapped in place, same index and position, for ones that know the cap.
		for (int index = 0; index < chestEnd; index++) {
			Slot plain = slots.get(index);
			Slot capped = new ChestSlot(chest, plain.getContainerSlot(), plain.x, plain.y);
			capped.index = index;
			slots.set(index, capped);
		}
		client = inventory.player.level().isClientSide();
		upgradesStart = slots.size();
		// One column beside the rows, the forced slots 4 pixels below the crafter.
		for (int slot = 0; slot < SmartChestBlockEntity.UPGRADE_SLOTS; slot++) {
			addSlot(new UpgradeSlot(upgrades, slot, 18 + 18 * slot + (isForced(slot) ? 4 : 0)));
		}
	}

	private static boolean isForced(int upgradeSlot) {
		return upgradeSlot >= SmartChestBlockEntity.FIRST_FORCED_SLOT;
	}

	/** Whether this is a forced compression slot (SPEC §5.1). */
	public boolean isForced(Slot slot) {
		return slot instanceof UpgradeSlot && isForced(slot.getContainerSlot());
	}

	/** Server side, a single chest. */
	public static SmartChestMenu single(int containerId, Inventory inventory, Container chest, Container upgrades) {
		return new SmartChestMenu(SmartChestBlocks.MENU, containerId, inventory, chest, 3, upgrades);
	}

	/** Server side, a double chest: {@code upgrades} are those of its first half, which apply to both. */
	public static SmartChestMenu twoHalves(int containerId, Inventory inventory, Container chest, Container upgrades) {
		return new SmartChestMenu(SmartChestBlocks.DOUBLE_MENU, containerId, inventory, chest, 6, upgrades);
	}

	/** Client side: empty containers, which the server fills. */
	public static SmartChestMenu single(int containerId, Inventory inventory) {
		return single(containerId, inventory, mirror(27), new SimpleContainer(SmartChestBlockEntity.UPGRADE_SLOTS));
	}

	public static SmartChestMenu twoHalves(int containerId, Inventory inventory) {
		return twoHalves(containerId, inventory, mirror(54), new SimpleContainer(SmartChestBlockEntity.UPGRADE_SLOTS));
	}

	/**
	 * The client's copy of the chest, which takes the server's piles as they come:
	 * SimpleContainer.setItem would cut them to the vanilla stack, and on opening the
	 * chest slots arrive before the upgrade slot that sets their cap.
	 */
	private static SimpleContainer mirror(int size) {
		return new SimpleContainer(size) {
			@Override
			public int getMaxStackSize(ItemStack stack) {
				return Integer.MAX_VALUE;
			}
		};
	}

	/** Whether this is the capacity upgrade slot, and the upgrade cannot come out (SPEC §4.1). */
	public boolean blocksRemoval(Slot slot) {
		return slot instanceof UpgradeSlot && slot.getContainerSlot() == SmartChestBlockEntity.CAPACITY_SLOT
				&& capacityLocked(ItemStack.EMPTY);
	}

	/** Whether a pile would be above its cap with {@code replacement} in the capacity slot. */
	private boolean capacityLocked(ItemStack replacement) {
		for (int index = 0; index < chestEnd; index++) {
			ItemStack pile = slots.get(index).getItem();
			if (pile.getCount() > SmartChestBlockEntity.maxStackSize(replacement, pile)) {
				return true;
			}
		}
		return false;
	}

	private ItemStack capacityUpgrade() {
		return upgrades.getItem(SmartChestBlockEntity.CAPACITY_SLOT);
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player player) {
		this.input = input;
		try {
			if (slotId >= 0 && slotId < chestEnd && !handleLargePile(slots.get(slotId), button, input, player)) {
				return;
			}
			super.clicked(slotId, button, input, player);
		} finally {
			this.input = null;
		}
	}

	/**
	 * The clicks that would move a whole pile above the vanilla stack out of the
	 * chest, which no other inventory can hold: AbstractContainerMenu.doClick swaps
	 * it onto the cursor (PICKUP with another item carried) or into the hotbar
	 * (SWAP), without going through ChestSlot.remove; and Ctrl+Q (THROW, button 1)
	 * repeats while the slot keeps the item, one entity per potion. Returns whether
	 * vanilla handles the click.
	 */
	private boolean handleLargePile(Slot slot, int button, ContainerInput input, Player player) {
		ItemStack pile = slot.getItem();
		if (pile.getCount() <= pile.getMaxStackSize()) {
			return true;
		}
		if (input == ContainerInput.PICKUP && !getCarried().isEmpty()
				&& !ItemStack.isSameItemSameComponents(getCarried(), pile)) {
			// Refused. As doClick would, end a drag the click interrupts.
			resetQuickCraft();
			return false;
		}
		if (input == ContainerInput.SWAP) {
			// As doClick would, end a drag the click interrupts. Its button check:
			// a hotbar slot, or 40, the offhand.
			resetQuickCraft();
			Inventory inventory = player.getInventory();
			if ((button >= 0 && button < 9 || button == 40) && inventory.getItem(button).isEmpty()) {
				// One vanilla stack (ChestSlot.remove) to an empty slot, else nothing.
				inventory.setItem(button, slot.safeTake(pile.getCount(), Integer.MAX_VALUE, player));
			}
			return false;
		}
		if (input == ContainerInput.THROW && button == 1 && getCarried().isEmpty()) {
			// doClick's THROW, once: one vanilla stack (ChestSlot.remove).
			if (player.canDropItems()) {
				ItemStack thrown = slot.safeTake(pile.getCount(), Integer.MAX_VALUE, player);
				player.drop(thrown, true, Prediction.PREDICTED);
				player.handleCreativeModeItemDrop(thrown);
			}
			return false;
		}
		return true;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot slot) {
		// A double-click on melon slices would take the slice that forces them.
		return !isForced(slot) && super.canTakeItemForPickAll(carried, slot);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		// ChestMenu's would send the chest's items into the upgrade slots.
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		if (index < chestEnd) {
			// One vanilla stack per shift-click (SPEC §4.1). Returning EMPTY ends
			// doClick's loop, which would otherwise move the whole pile.
			ItemStack part = stack.copyWithCount(Math.min(stack.getCount(), stack.getMaxStackSize()));
			int count = part.getCount();
			if (moveItemStackTo(part, chestEnd, upgradesStart, true)) {
				slot.safeTake(count - part.getCount(), Integer.MAX_VALUE, player);
			}
			return ItemStack.EMPTY;
		}
		ItemStack moved = stack.copy();
		if (index >= upgradesStart) {
			if (!moveItemStackTo(stack, chestEnd, upgradesStart, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, upgradesStart, slots.size(), false) && !moveToChest(stack)) {
			// An upgrade goes to its slot if it is free, anything else to the chest.
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return moved;
	}

	/**
	 * Into the chest: first onto the piles of the same item, up to their cap, then
	 * into empty slots. moveItemStackTo merges only stackable items, so a potion
	 * would never join its pile.
	 */
	private boolean moveToChest(ItemStack stack) {
		boolean merged = false;
		for (int index = 0; index < chestEnd && !stack.isEmpty(); index++) {
			Slot slot = slots.get(index);
			ItemStack pile = slot.getItem();
			if (!pile.isEmpty() && ItemStack.isSameItemSameComponents(pile, stack)) {
				int count = Math.min(stack.getCount(), slot.getMaxStackSize(pile) - pile.getCount());
				if (count > 0) {
					pile.grow(count);
					stack.shrink(count);
					slot.setChanged();
					merged = true;
				}
			}
		}
		boolean placed = moveItemStackTo(stack, 0, chestEnd, false);
		return merged || placed;
	}

	private class ChestSlot extends Slot {
		ChestSlot(Container chest, int slot, int x, int y) {
			super(chest, slot, x, y);
		}

		@Override
		public int getMaxStackSize(ItemStack stack) {
			return SmartChestBlockEntity.maxStackSize(capacityUpgrade(), stack);
		}

		@Override
		public ItemStack remove(int amount) {
			// Every click that takes part of a pile comes here, through tryRemove: a
			// vanilla stack at most -- 64, 16 or 1 (SPEC §4.1). The clicks that move
			// the whole pile without it are handled by handleLargePile.
			return super.remove(Math.min(amount, getItem().getMaxStackSize()));
		}
	}

	private class UpgradeSlot extends Slot {
		UpgradeSlot(Container upgrades, int slot, int y) {
			super(upgrades, slot, UPGRADE_X, y);
		}

		@Override
		public boolean mayPickup(Player player) {
			if (getContainerSlot() != SmartChestBlockEntity.CAPACITY_SLOT) {
				return true;
			}
			// Refused while a pile is above the cap that follows (SPEC §4.1). The cap
			// of the upgrade carried counts only for a PICKUP that swaps it in (a
			// single other capacity upgrade); for any other click, the vanilla stack,
			// which also refuses a SWAP with an upgrade from the hotbar: cautious.
			ItemStack carried = getCarried();
			boolean swap = input == ContainerInput.PICKUP && SmartChestItems.isCapacityUpgrade(carried)
					&& carried.getCount() == 1 && !ItemStack.isSameItem(carried, getItem());
			return !capacityLocked(swap ? carried : ItemStack.EMPTY);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			// A forced slot takes only a direct click (SPEC §5.1). Shift-click
			// (QUICK_MOVE), keys 1-9 and F (SWAP) and dragging (QUICK_CRAFT) all ask
			// here inside clicked; the screen asks for its drag outside of it (null).
			// A click that moves the mouse within the slot comes as a PICKUP, since
			// the screen never added the slot to the drag.
			if (isForced(getContainerSlot()) && input != ContainerInput.PICKUP) {
				return false;
			}
			return SmartChestBlockEntity.acceptsUpgrade(getContainerSlot(), stack, client);
		}

		@Override
		public void setChanged() {
			super.setChanged();
			// The upgrades notify only the first half; the caps change the fill of
			// both, so the chest container (a CompoundContainer for a double chest)
			// tells the comparators of each half.
			if (getContainerSlot() == SmartChestBlockEntity.CAPACITY_SLOT) {
				SmartChestMenu.this.getContainer().setChanged();
			}
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public Identifier getNoItemIcon() {
			return switch (getContainerSlot()) {
				case SmartChestBlockEntity.CAPACITY_SLOT -> CAPACITY_ICON;
				case SmartChestBlockEntity.COMPRESSION_SLOT -> COMPRESSION_ICON;
				default -> FORCED_ICON;
			};
		}
	}
}
