package re.jerome.smartchest;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmartChestMod implements ModInitializer {
	public static final String MOD_ID = "smartchest";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SmartChestConfig.load();
		LOGGER.info("Smart Chest loaded");
	}
}
