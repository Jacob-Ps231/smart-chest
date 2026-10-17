package re.jerome.smartchest.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import re.jerome.smartchest.SmartChestMenu;
import re.jerome.smartchest.SmartChestMod;

// The vanilla chest screen (ContainerScreen), with a panel of upgrade slots on the
// right, beside rows 1-2. The texture, made by tools/GenerateUpgrades.java, is
// generic_54.png with that panel. The panel is inside imageWidth, so that a click
// on it is not a click outside.
public class SmartChestScreen extends AbstractContainerScreen<SmartChestMenu> {
	private static final Identifier BACKGROUND = SmartChestMod.id("textures/gui/container/smart_chest.png");
	private static final int CHEST_WIDTH = 176;
	private static final int PANEL_X = 178;
	private static final int PANEL_WIDTH = 32;
	private static final int PANEL_TOP = 10;
	private static final int PANEL_HEIGHT = 50;

	private final int rows;

	public SmartChestScreen(SmartChestMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PANEL_X + PANEL_WIDTH, 114 + menu.getRowCount() * 18);
		rows = menu.getRowCount();
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		// As ContainerScreen: the chest rows, then the player's inventory.
		int chestHeight = rows * 18 + 17;
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, CHEST_WIDTH, chestHeight, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos + chestHeight, 0, 126, CHEST_WIDTH, 96, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos + PANEL_X, topPos + PANEL_TOP, PANEL_X, PANEL_TOP,
				PANEL_WIDTH, PANEL_HEIGHT, 256, 256);
	}
}
