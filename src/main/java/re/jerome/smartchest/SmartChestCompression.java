package re.jerome.smartchest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * The compression upgrade (SPEC §5): the tables of compressions, read from the
 * crafting recipes, and the rule that turns piles into blocks. Server side only.
 */
public final class SmartChestCompression {
	/** Items the chest never compresses nor makes: a rule goes if its ingredient or its block is here. */
	public static final TagKey<Item> NO_COMPRESSION = TagKey.create(Registries.ITEM, SmartChestMod.id("no_compression"));

	// Rebuilt on the server thread at start and after each /reload, read by the
	// chests' ticks on the same thread.
	private static Map<Item, Rule> table = Map.of();
	// The same without the reverse recipe, for the forced compression slots (SPEC §5.1).
	private static Map<Item, Rule> forcedTable = Map.of();

	private SmartChestCompression() {
	}

	/** {@code count} of an item make one {@code block}. */
	private record Rule(Item block, int count) {
	}

	/** A crafting recipe from {@code count} times one item to one other, or from one item to {@code count} times another. */
	private record Conversion(Item from, Item to, int count) {
	}

	/**
	 * A compression is a vanilla shaped or shapeless recipe from N times one item
	 * (N ≥ 2) to one block without components, kept only if the block crafts back
	 * into exactly N of that item. The first one found wins; the others are logged.
	 * The forced table, for the items the normal one leaves out, does without the
	 * reverse recipe, but then only takes a full 2x2 or 3x3 grid, or 4 or 9
	 * shapeless, of something without durability; the largest N wins (prismarine
	 * shards make bricks), ties are logged. Never an item with a crafting
	 * remainder: the chest would destroy it (honey bottles).
	 */
	public static void rebuild(MinecraftServer server) {
		List<Conversion> compressions = new ArrayList<>();
		Set<Conversion> uncrafts = new HashSet<>();
		Set<Conversion> forceable = new HashSet<>();
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			if (!(holder.value() instanceof CraftingRecipe recipe)
					|| recipe.getSerializer() != ShapedRecipe.SERIALIZER && recipe.getSerializer() != ShapelessRecipe.SERIALIZER) {
				continue;
			}
			// placementInfo leaves out the empty cells of a shaped pattern.
			List<Ingredient> ingredients = recipe.placementInfo().ingredients();
			Optional<Item> from = singleItem(ingredients);
			// Both recipe classes return their result here, whatever the input.
			ItemStack result = recipe.assemble(CraftingInput.EMPTY);
			if (from.isEmpty() || result.isEmpty() || !result.getComponentsPatch().isEmpty() || result.is(from.get())
					|| from.get().getCraftingRemainder() != null) {
				continue;
			}
			if (ingredients.size() >= 2 && result.getCount() == 1) {
				Conversion compression = new Conversion(from.get(), result.getItem(), ingredients.size());
				compressions.add(compression);
				if (isFullGrid(recipe, ingredients.size()) && !result.isDamageableItem()) {
					forceable.add(compression);
				}
			} else if (ingredients.size() == 1 && result.getCount() >= 2) {
				uncrafts.add(new Conversion(from.get(), result.getItem(), result.getCount()));
			}
		}

		Map<Item, Rule> rules = new LinkedHashMap<>();
		Map<Item, Rule> forced = new LinkedHashMap<>();
		// A forced rule that ties with the one kept, while no larger N has replaced it.
		Map<Item, Rule> ties = new LinkedHashMap<>();
		for (Conversion compression : compressions) {
			if (compression.from().builtInRegistryHolder().is(NO_COMPRESSION)
					|| compression.to().builtInRegistryHolder().is(NO_COMPRESSION)) {
				continue;
			}
			Rule rule = new Rule(compression.to(), compression.count());
			// Without the reverse recipe, the full grids only: no boats, pressure plates nor armour.
			if (forceable.contains(compression)) {
				Rule largest = forced.get(compression.from());
				if (largest == null || rule.count() > largest.count()) {
					forced.put(compression.from(), rule);
					ties.remove(compression.from());
				} else if (rule.count() == largest.count() && !rule.equals(largest)) {
					ties.putIfAbsent(compression.from(), rule);
				}
			}
			if (!uncrafts.contains(new Conversion(compression.to(), compression.from(), compression.count()))) {
				continue;
			}
			Rule kept = rules.putIfAbsent(compression.from(), rule);
			if (kept != null && !kept.equals(rule)) {
				SmartChestMod.LOGGER.warn("Ambiguous compression of {}: {} kept, {} ignored (tag {} decides)",
						name(compression.from()), describe(kept), describe(rule), NO_COMPRESSION.location());
			}
		}
		// The forced slots are for what the normal table leaves out: an emerald compresses anyway.
		forced.keySet().removeAll(rules.keySet());
		ties.forEach((item, ignored) -> {
			if (forced.containsKey(item)) {
				SmartChestMod.LOGGER.warn("Ambiguous forced compression of {}: {} kept, {} ignored (tag {} decides)",
						name(item), describe(forced.get(item)), describe(ignored), NO_COMPRESSION.location());
			}
		});
		table = Map.copyOf(rules);
		forcedTable = Map.copyOf(forced);
		log("Compression table", rules);
		log("Forced compression table", forced);
	}

	private static void log(String title, Map<Item, Rule> rules) {
		List<String> lines = rules.entrySet().stream()
				.map(entry -> name(entry.getKey()) + " " + describe(entry.getValue()))
				.toList();
		SmartChestMod.LOGGER.info("{}: {} rules: {}", title, rules.size(), String.join(", ", lines));
	}

	/** Whether a forced compression slot takes this item: it is in the forced table (SPEC §5.1). */
	public static boolean canForce(ItemStack stack) {
		return forcedTable.containsKey(stack.getItem());
	}

	/** Whether the recipe fills a 2x2 or 3x3 grid, or is a shapeless one of 4 or 9 ingredients. */
	private static boolean isFullGrid(CraftingRecipe recipe, int ingredients) {
		if (ingredients != 4 && ingredients != 9) {
			return false;
		}
		// placementInfo leaves out the empty cells: a full grid is width x height of them.
		return !(recipe instanceof ShapedRecipe shaped)
				|| shaped.getWidth() == shaped.getHeight() && shaped.getWidth() * shaped.getHeight() == ingredients;
	}

	/** The item every ingredient is, if they are all the same single item. */
	private static Optional<Item> singleItem(List<Ingredient> ingredients) {
		Item item = null;
		for (Ingredient ingredient : ingredients) {
			Optional<Holder<Item>> single = ingredient.getSingleItem();
			if (single.isEmpty() || item != null && single.get().value() != item) {
				return Optional.empty();
			}
			item = single.get().value();
		}
		return Optional.ofNullable(item);
	}

	private static String name(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	private static String describe(Rule rule) {
		return "x" + rule.count() + " -> " + name(rule.block());
	}

	/**
	 * SPEC §5: while the total of an item in the chest is above its per-slot cap and
	 * makes at least one block, N of it become one block, as long as the block fits.
	 * Again until nothing changes, for the cascades (nuggets, ingots, blocks). It
	 * ends: each compression lowers the number of items in the chest. The items in
	 * {@code forced} compress without a reverse recipe too (SPEC §5.1). Returns whether
	 * anything was compressed, for the sort that follows (SPEC §5.2).
	 */
	public static boolean compress(Container chest, ItemStack capacityUpgrade, Set<Item> forced) {
		boolean any = false;
		boolean changed;
		do {
			changed = false;
			Set<Item> done = new HashSet<>();
			for (int slot = 0; slot < chest.getContainerSize(); slot++) {
				ItemStack stack = chest.getItem(slot);
				Rule rule = table.get(stack.getItem());
				if (rule == null && forced.contains(stack.getItem())) {
					rule = forcedTable.get(stack.getItem());
				}
				if (rule != null && isPlain(stack) && done.add(stack.getItem())) {
					changed |= compress(chest, capacityUpgrade, stack.getItem(), rule);
				}
			}
			any |= changed;
		} while (changed);
		return any;
	}

	/** Only piles without components count and change: renamed emeralds stay emeralds. */
	private static boolean isPlain(ItemStack stack) {
		return !stack.isEmpty() && stack.getComponentsPatch().isEmpty();
	}

	private static boolean compress(Container chest, ItemStack capacityUpgrade, Item item, Rule rule) {
		// The piles of the item, smallest first: taking from them frees the most slots.
		List<Integer> piles = new ArrayList<>();
		int total = 0;
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack stack = chest.getItem(slot);
			if (stack.is(item) && isPlain(stack)) {
				piles.add(slot);
				total += stack.getCount();
			}
		}
		int cap = SmartChestBlockEntity.maxStackSize(capacityUpgrade, new ItemStack(item));
		if (total <= cap || total < rule.count()) {
			return false;
		}
		piles.sort(Comparator.comparingInt(slot -> chest.getItem(slot).getCount()));

		// Enough blocks to bring the total back to the cap, if there are that many.
		int blocks = Math.min(Math.ceilDiv(total - cap, rule.count()), total / rule.count());
		ItemStack block = new ItemStack(rule.block());
		int blockCap = SmartChestBlockEntity.maxStackSize(capacityUpgrade, block);
		// Only the blocks that fit: none of the item goes for a block with no room. Room
		// never grows with fewer blocks, so no count above it can fit: straight to it.
		int room;
		while (blocks > 0 && (room = room(chest, piles, block, blockCap, blocks * rule.count())) < blocks) {
			blocks = room;
		}
		if (blocks == 0) {
			return false;
		}

		int toRemove = blocks * rule.count();
		for (int slot : piles) {
			ItemStack stack = chest.getItem(slot);
			int taken = Math.min(stack.getCount(), toRemove);
			chest.setItem(slot, stack.getCount() == taken ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - taken));
			toRemove -= taken;
			if (toRemove == 0) {
				break;
			}
		}

		// The block piles first, then the empty slots: the places room counts.
		int toAdd = blocks;
		for (int slot = 0; slot < chest.getContainerSize() && toAdd > 0; slot++) {
			ItemStack stack = chest.getItem(slot);
			if (ItemStack.isSameItemSameComponents(stack, block) && stack.getCount() < blockCap) {
				int added = Math.min(blockCap - stack.getCount(), toAdd);
				chest.setItem(slot, stack.copyWithCount(stack.getCount() + added));
				toAdd -= added;
			}
		}
		for (int slot = 0; slot < chest.getContainerSize() && toAdd > 0; slot++) {
			if (chest.getItem(slot).isEmpty()) {
				int added = Math.min(blockCap, toAdd);
				chest.setItem(slot, block.copyWithCount(added));
				toAdd -= added;
			}
		}
		return true;
	}

	/**
	 * How many blocks the chest takes once {@code removed} of the item have left
	 * {@code piles} (smallest first, as compress takes them): room in the block piles,
	 * plus the empty slots and the slots the removal empties.
	 */
	private static int room(Container chest, List<Integer> piles, ItemStack block, int blockCap, int removed) {
		int room = 0;
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack stack = chest.getItem(slot);
			if (stack.isEmpty()) {
				room += blockCap;
			} else if (ItemStack.isSameItemSameComponents(stack, block)) {
				room += Math.max(0, blockCap - stack.getCount());
			}
		}
		for (int slot : piles) {
			int count = chest.getItem(slot).getCount();
			if (removed < count) {
				break;
			}
			removed -= count;
			room += blockCap;
		}
		return room;
	}
}
