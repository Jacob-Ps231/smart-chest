package re.jerome.smartchest;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Sorting a chest (SPEC §5.2), on the server only: the identical piles merged up to
 * their capacity cap, then the whole chest in the order of a mode, empty slots last.
 */
public final class SmartChestSorting {
	/** The four sorting modes, one button each; the chest keeps the last one used. */
	public enum Mode {
		CATEGORY, NAME, QUANTITY, ID;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The mode saved under this key, else the default: a chest from 0.1.0 has none. */
		static Mode byKey(String key) {
			for (Mode mode : values()) {
				if (mode.key().equals(key)) {
					return mode;
				}
			}
			return CATEGORY;
		}
	}

	// Each item's first place in the creative category tabs, in their order.
	private static Map<Item, Integer> creativeOrder = Map.of();

	private SmartChestSorting() {
	}

	/**
	 * The creative order, at server start and after each /reload. Not from the tabs'
	 * own contents: CreativeModeTabs.tryRebuildTabContents caches them in static fields
	 * that the creative screen checks on every tick, on the client thread of the same
	 * game in single player, and rebuilds when the registries differ (by identity):
	 * the server's would make both sides rebuild them in turn, each over the other's
	 * reading. So each category tab's generator runs into lists of our
	 * own, followed by the two events Fabric fires at the end of
	 * CreativeModeTab.buildContents, which place the items mods add (ours too).
	 */
	public static void rebuild(MinecraftServer server) {
		FeatureFlagSet features = server.getWorldData().enabledFeatures();
		// With permissions, as an operator sees them: the operator tab counts too.
		CreativeModeTab.ItemDisplayParameters parameters =
				new CreativeModeTab.ItemDisplayParameters(features, true, server.registryAccess());
		Map<Item, Integer> order = new HashMap<>();
		for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
			if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
				continue;
			}
			ResourceKey<CreativeModeTab> key = BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(tab).orElseThrow();
			try {
				List<ItemStack> display = new ArrayList<>();
				// As CreativeModeTab.ItemDisplayBuilder: the parent tab's items, if enabled.
				tab.displayItemsGenerator.accept(parameters, (stack, visibility) -> {
					if (visibility != CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY && stack.isItemEnabled(features)) {
						display.add(stack);
					}
				});
				FabricCreativeModeTabOutput output = new FabricCreativeModeTabOutput(parameters, display, new ArrayList<>());
				CreativeModeTabEvents.modifyOutputEvent(key).invoker().modifyOutput(output);
				CreativeModeTabEvents.MODIFY_OUTPUT_ALL.invoker().modifyOutput(tab, output);
				for (ItemStack stack : display) {
					order.putIfAbsent(stack.getItem(), order.size());
				}
			} catch (RuntimeException | LinkageError e) {
				// A modded tab nobody builds on a dedicated server may reach for the
				// client: its items then sort after the others, by id.
				SmartChestMod.LOGGER.warn("Creative tab {} left out of the sorting order", key.identifier(), e);
			}
		}
		creativeOrder = order;
		SmartChestMod.LOGGER.info("Sorting order: {} items from the creative tabs", order.size());
	}

	/**
	 * Merges and orders the chest. Each slot gives at most one pile -- what it has left
	 * once poured into the earlier identical piles -- so they always fit; a pile above
	 * a cap lowered since only gives. The new piles are copies, written at the end, and
	 * only if every item still counts the same. Only the changed slots are written.
	 */
	public static void sort(Container chest, ItemStack capacityUpgrade, Mode mode) {
		List<ItemStack> piles = new ArrayList<>();
		Map<Item, Long> before = new HashMap<>();
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack stack = chest.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			before.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
			ItemStack rest = stack.copy();
			int cap = SmartChestBlockEntity.maxStackSize(capacityUpgrade, rest);
			for (ItemStack pile : piles) {
				if (rest.isEmpty()) {
					break;
				}
				if (ItemStack.isSameItemSameComponents(pile, rest)) {
					int moved = Math.min(rest.getCount(), cap - pile.getCount());
					if (moved > 0) {
						pile.grow(moved);
						rest.shrink(moved);
					}
				}
			}
			if (!rest.isEmpty()) {
				piles.add(rest);
			}
		}
		Map<Item, Long> after = new HashMap<>();
		piles.forEach(pile -> after.merge(pile.getItem(), (long) pile.getCount(), Long::sum));
		if (!after.equals(before) || piles.size() > chest.getContainerSize()) {
			SmartChestMod.LOGGER.error("Sorting cancelled, the counts would change: {} -> {}", before, after);
			return;
		}
		// Identical piles stay together, behind the first of them: potions or enchanted
		// books tie on the mode's key whatever their contents. The full ones first.
		Map<ItemStack, Integer> group = new IdentityHashMap<>();
		for (int n = 0; n < piles.size(); n++) {
			int first = 0;
			while (!ItemStack.isSameItemSameComponents(piles.get(first), piles.get(n))) {
				first++;
			}
			group.put(piles.get(n), first);
		}
		piles.sort(order(mode, before).thenComparingInt(group::get)
				.thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed()));
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack sorted = slot < piles.size() ? piles.get(slot) : ItemStack.EMPTY;
			if (!ItemStack.matches(chest.getItem(slot), sorted)) {
				chest.setItem(slot, sorted);
			}
		}
	}

	/** The order of a mode. Ties keep the order of the slots (List.sort is stable). */
	private static Comparator<ItemStack> order(Mode mode, Map<Item, Long> totals) {
		ToIntFunction<ItemStack> category = stack -> creativeOrder.getOrDefault(stack.getItem(), Integer.MAX_VALUE);
		Comparator<ItemStack> id = Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		Comparator<ItemStack> byCategory = Comparator.comparingInt(category).thenComparing(id);
		return switch (mode) {
			case CATEGORY -> byCategory;
			// The server's language: the game's in single player, English on a
			// dedicated server, which has no other. The collator puts É with E.
			case NAME -> Comparator.comparing((ItemStack stack) -> stack.getHoverName().getString(),
					Collator.getInstance(Locale.ROOT)).thenComparing(id);
			case QUANTITY -> Comparator.comparing((ItemStack stack) -> totals.get(stack.getItem()), Comparator.reverseOrder())
					.thenComparing(byCategory);
			case ID -> id;
		};
	}
}
