package re.jerome.smartchest.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.world.level.block.state.properties.ChestType;
import re.jerome.smartchest.SmartChestBlocks;
import re.jerome.smartchest.SmartChestMod;

public class SmartChestClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		for (ChestType type : ChestType.values()) {
			ModelLayerRegistry.registerModelLayer(SmartChestLayers.ALL.select(type),
					() -> SmartChestLayers.create(type));
		}
		// Public through smartchest.accesswidener: Fabric has no API for special model types.
		SpecialModelRenderers.ID_MAPPER.put(SmartChestMod.id("chest"), SmartChestItemRenderer.MAP_CODEC);
		// BlockEntityRenderers keeps its register private; Fabric's registry is
		// deprecated without a replacement, and still the only way in.
		BlockEntityRendererRegistry.register(SmartChestBlocks.BLOCK_ENTITY, SmartChestRenderer::new);
		// Private in vanilla, opened by Fabric API (transitive access wideners).
		MenuScreens.register(SmartChestBlocks.MENU, SmartChestScreen::new);
		MenuScreens.register(SmartChestBlocks.DOUBLE_MENU, SmartChestScreen::new);
	}
}
