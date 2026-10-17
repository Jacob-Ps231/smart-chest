package re.jerome.smartchest;

import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

public final class SmartChestBlocks {
	// Vanilla keeps its tab keys private.
	static final ResourceKey<CreativeModeTab> FUNCTIONAL_BLOCKS = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("functional_blocks"));

	// The vanilla woods but bamboo (SPEC §2), in the creative tab order. The same
	// list, with the translations, is in tools/GenerateWoods.java, which checks it
	// against this one.
	public static final List<SmartChestBlock> ALL = List.of(
			register("oak", Blocks.OAK_PLANKS),
			register("spruce", Blocks.SPRUCE_PLANKS),
			register("birch", Blocks.BIRCH_PLANKS),
			register("jungle", Blocks.JUNGLE_PLANKS),
			register("acacia", Blocks.ACACIA_PLANKS),
			register("dark_oak", Blocks.DARK_OAK_PLANKS),
			register("mangrove", Blocks.MANGROVE_PLANKS),
			register("cherry", Blocks.CHERRY_PLANKS),
			register("pale_oak", Blocks.PALE_OAK_PLANKS),
			register("poplar", Blocks.POPLAR_PLANKS),
			register("crimson", Blocks.CRIMSON_PLANKS),
			register("warped", Blocks.WARPED_PLANKS));

	// One type for every wood. Declared after the blocks, whose set it reads.
	public static final BlockEntityType<SmartChestBlockEntity> BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, SmartChestMod.id("smart_chest")),
			new BlockEntityType<>(SmartChestBlockEntity::new, Set.copyOf(ALL)));

	// One type per size, as vanilla has GENERIC_9x3 and 9x6: the client builds the
	// menu from the type alone, so no opening data is needed. The constructor is
	// private in vanilla, opened by Fabric API (transitive access wideners).
	public static final MenuType<SmartChestMenu> MENU = registerMenu("smart_chest", SmartChestMenu::single);
	public static final MenuType<SmartChestMenu> DOUBLE_MENU = registerMenu("double_smart_chest", SmartChestMenu::twoHalves);

	private SmartChestBlocks() {
	}

	private static SmartChestBlock register(String wood, Block planks) {
		Identifier id = SmartChestMod.id(wood + "_smart_chest");
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		// The vanilla chest's properties (Blocks.CHEST), with the map colour, sound
		// and lava ignition of the wood's planks, as the vanilla shelves do for the
		// colour: crimson and warped do not catch fire from lava, like their planks.
		// The oak planks match Blocks.CHEST on all three.
		BlockState wooden = planks.defaultBlockState();
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
				.mapColor(planks.defaultMapColor())
				.instrument(NoteBlockInstrument.BASS)
				.strength(2.5F)
				.sound(wooden.getSoundType())
				.setId(blockKey);
		if (wooden.ignitedByLava()) {
			properties.ignitedByLava();
		}
		SmartChestBlock block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
				new SmartChestBlock(wood, properties));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		// Items.CHEST also carries an empty CONTAINER; it is not fuel here, unlike
		// the vanilla chest, since it holds a copper ingot.
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties()
				.component(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
				.useBlockDescriptionPrefix()
				.setId(itemKey)));
		return block;
	}

	private static MenuType<SmartChestMenu> registerMenu(String name, MenuType.MenuSupplier<SmartChestMenu> client) {
		return Registry.register(BuiltInRegistries.MENU, SmartChestMod.id(name), new MenuType<>(client, FeatureFlags.VANILLA_SET));
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(FUNCTIONAL_BLOCKS)
				.register(output -> output.insertAfter(Items.CHEST, ALL.toArray(new SmartChestBlock[0])));
	}
}
