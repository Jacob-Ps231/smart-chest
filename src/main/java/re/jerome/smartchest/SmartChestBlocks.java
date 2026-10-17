package re.jerome.smartchest;

import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

public final class SmartChestBlocks {
	// Vanilla keeps its tab keys private.
	private static final ResourceKey<CreativeModeTab> FUNCTIONAL_BLOCKS = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("functional_blocks"));

	public static final SmartChestBlock OAK = register("oak");

	// One type for every wood. Declared after the blocks, whose set it reads.
	public static final BlockEntityType<SmartChestBlockEntity> BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, SmartChestMod.id("smart_chest")),
			new BlockEntityType<>(SmartChestBlockEntity::new, Set.of(OAK)));

	private SmartChestBlocks() {
	}

	private static SmartChestBlock register(String wood) {
		Identifier id = SmartChestMod.id(wood + "_smart_chest");
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		// The vanilla chest's properties (Blocks.CHEST).
		SmartChestBlock block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
				new SmartChestBlock(wood, BlockBehaviour.Properties.of()
						.mapColor(MapColor.WOOD)
						.instrument(NoteBlockInstrument.BASS)
						.strength(2.5F)
						.sound(SoundType.WOOD)
						.ignitedByLava()
						.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		// Items.CHEST also carries an empty CONTAINER; it is not fuel here, unlike
		// the vanilla chest, since it holds a copper ingot.
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties()
				.component(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
				.useBlockDescriptionPrefix()
				.setId(itemKey)));
		return block;
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(FUNCTIONAL_BLOCKS)
				.register(output -> output.insertAfter(Items.CHEST, OAK));
	}
}
