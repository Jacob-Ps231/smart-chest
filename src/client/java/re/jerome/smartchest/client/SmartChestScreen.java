package re.jerome.smartchest.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import re.jerome.smartchest.SmartChestMenu;
import re.jerome.smartchest.SmartChestMod;

// The vanilla chest screen (ContainerScreen), with a panel of five upgrade slots on
// the right, from the first row down. The texture, made by tools/GenerateUpgrades.java, is
// generic_54.png with that panel. The panel is inside imageWidth, so that a click
// on it is not a click outside.
public class SmartChestScreen extends AbstractContainerScreen<SmartChestMenu> {
	private static final Identifier BACKGROUND = SmartChestMod.id("textures/gui/container/smart_chest.png");
	private static final int CHEST_WIDTH = 176;
	private static final int PANEL_X = 178;
	private static final int PANEL_WIDTH = 32;
	private static final int PANEL_TOP = 10;
	private static final int PANEL_HEIGHT = 108;
	private static final List<Component> FORCED_HINT = List.of(
			Component.translatable("container.smartchest.forced_compression"),
			Component.translatable("container.smartchest.forced_compression.hint").withStyle(ChatFormatting.GRAY));

	private final int rows;

	public SmartChestScreen(SmartChestMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PANEL_X + PANEL_WIDTH, 114 + menu.getRowCount() * 18);
		rows = menu.getRowCount();
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
		List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
		// Why the capacity upgrade does not come out (SPEC §4.1).
		if (hoveredSlot != null && menu.blocksRemoval(hoveredSlot)) {
			lines.add(Component.translatable("container.smartchest.capacity_locked").withStyle(ChatFormatting.RED));
		}
		return lines;
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		// What an empty forced compression slot is for (SPEC §5.1), as the smithing
		// table explains its empty slots.
		if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty() && menu.isForced(hoveredSlot)) {
			graphics.setComponentTooltipForNextFrame(font, FORCED_HINT, mouseX, mouseY);
		}
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
