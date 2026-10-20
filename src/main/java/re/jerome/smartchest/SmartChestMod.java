package re.jerome.smartchest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmartChestMod implements ModInitializer {
	public static final String MOD_ID = "smartchest";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		SmartChestConfig.load();
		SmartChestBlocks.register();
		SmartChestItems.register();
		// The compression table follows the recipes and the no_compression tag (SPEC §5),
		// the sorting order the creative tabs, which read registries and tags (SPEC §5.2).
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SmartChestCompression.rebuild(server);
			SmartChestSorting.rebuild(server);
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) {
				SmartChestCompression.rebuild(server);
				SmartChestSorting.rebuild(server);
			}
		});
		LOGGER.info("Smart Chest loaded");
	}
}
