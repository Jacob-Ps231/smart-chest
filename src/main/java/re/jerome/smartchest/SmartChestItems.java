package re.jerome.smartchest;

import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SmartChestItems {
	// Lowest tier first: the tier of an upgrade is its index + 1, and its cap is
	// capacity.tiers at the same index (SPEC §4). No effect before step 7.
	public static final List<Item> CAPACITY_UPGRADES = List.of(
			register("capacity_upgrade_128"),
			register("capacity_upgrade_256"),
			register("capacity_upgrade_512"),
			register("capacity_upgrade_999"));

	private SmartChestItems() {
	}

	private static Item register(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SmartChestMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}

	/** Whether this item goes in the capacity upgrade slot: one of the four upgrades. */
	public static boolean isCapacityUpgrade(ItemStack stack) {
		return CAPACITY_UPGRADES.contains(stack.getItem());
	}

	/** Whether this item goes in the compression upgrade slot: a vanilla crafter (SPEC §5). */
	public static boolean isCompressionUpgrade(ItemStack stack) {
		return stack.is(Items.CRAFTER);
	}

	public static void register() {
		// Right after the Smart Chests.
		CreativeModeTabEvents.modifyOutputEvent(SmartChestBlocks.FUNCTIONAL_BLOCKS)
				.register(output -> output.insertAfter(SmartChestBlocks.ALL.getLast(), CAPACITY_UPGRADES.toArray(new Item[0])));
	}
}
