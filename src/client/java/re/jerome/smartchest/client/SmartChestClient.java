package re.jerome.smartchest.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import re.jerome.smartchest.SmartChestBlocks;

public class SmartChestClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// BlockEntityRenderers keeps its register private; Fabric's registry is
		// deprecated without a replacement, and still the only way in.
		BlockEntityRendererRegistry.register(SmartChestBlocks.BLOCK_ENTITY, SmartChestRenderer::new);
	}
}
