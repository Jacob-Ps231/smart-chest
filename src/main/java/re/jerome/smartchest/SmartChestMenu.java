package re.jerome.smartchest;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// A ChestMenu so that the chest counts its viewer: ChestBlockEntity's opener counter
// only recognises a ChestMenu over its own container, and closes the lid on anyone
// else. Slots, in order: the chest's rows, the player's inventory (both from
// ChestMenu), then the two upgrades -- of the first half for a double chest, the
// only ones it has once merged (SPEC §2.1).
public class SmartChestMenu extends ChestMenu {
	/** Upgrade slot positions, beside the chest rows (the screen draws them there). */
	public static final int UPGRADE_X = 186;
	// Drawn by tools/GenerateUpgrades.java, in the gui atlas.
	private static final Identifier CAPACITY_ICON = SmartChestMod.id("container/slot/capacity_upgrade");
	private static final Identifier COMPRESSION_ICON = SmartChestMod.id("container/slot/compression_upgrade");

	private final int upgradesStart;

	private SmartChestMenu(MenuType<?> type, int containerId, Inventory inventory, Container chest, int rows,
			Container upgrades) {
		super(type, containerId, inventory, chest, rows);
		upgradesStart = slots.size();
		// Beside the first two rows.
		for (int slot = 0; slot < 2; slot++) {
			addSlot(new UpgradeSlot(upgrades, slot, 18 + 18 * slot));
		}
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
		return single(containerId, inventory, new SimpleContainer(27), new SimpleContainer(2));
	}

	public static SmartChestMenu twoHalves(int containerId, Inventory inventory) {
		return twoHalves(containerId, inventory, new SimpleContainer(54), new SimpleContainer(2));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		// ChestMenu's would send the chest's items into the upgrade slots.
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();
		int chestEnd = getRowCount() * 9;
		if (index < chestEnd || index >= upgradesStart) {
			if (!moveItemStackTo(stack, chestEnd, upgradesStart, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, upgradesStart, slots.size(), false)
				&& !moveItemStackTo(stack, 0, chestEnd, false)) {
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

	private static class UpgradeSlot extends Slot {
		UpgradeSlot(Container upgrades, int slot, int y) {
			super(upgrades, slot, UPGRADE_X, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return SmartChestBlockEntity.acceptsUpgrade(getContainerSlot(), stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public Identifier getNoItemIcon() {
			return getContainerSlot() == SmartChestBlockEntity.CAPACITY_SLOT ? CAPACITY_ICON : COMPRESSION_ICON;
		}
	}
}
