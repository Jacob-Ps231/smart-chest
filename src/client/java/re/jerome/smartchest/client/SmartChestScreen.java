package re.jerome.smartchest.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import re.jerome.smartchest.SmartChestMenu;
import re.jerome.smartchest.SmartChestMod;
import re.jerome.smartchest.SmartChestSorting;

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

	// The sorting buttons (SPEC §5.2), drawn by tools/GenerateUpgrades.java: a frame
	// per state, then the mode's icon on it. In a column left of the chest, from the top.
	private static final Identifier BUTTON = SmartChestMod.id("container/sort/button");
	private static final Identifier BUTTON_HIGHLIGHTED = SmartChestMod.id("container/sort/button_highlighted");
	private static final Identifier BUTTON_SELECTED = SmartChestMod.id("container/sort/button_selected");
	private static final int BUTTON_SIZE = 16;
	private static final Component SORT_HINT =
			Component.translatable("container.smartchest.sort.hint").withStyle(ChatFormatting.GRAY);

	private final int rows;
	private final List<SortButton> sortButtons = new ArrayList<>();

	public SmartChestScreen(SmartChestMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PANEL_X + PANEL_WIDTH, 114 + menu.getRowCount() * 18);
		rows = menu.getRowCount();
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		sortButtons.clear();
		for (SmartChestSorting.Mode mode : SmartChestSorting.Mode.values()) {
			SortButton button = new SortButton(leftPos - BUTTON_SIZE - 2, topPos + 4 + (BUTTON_SIZE + 2) * mode.ordinal(), mode);
			// The server ignores a spectator's buttons: the pressed one would be wrong.
			button.active = !minecraft.player.isSpectator();
			sortButtons.add(addRenderableWidget(button));
		}
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
		// The buttons are outside the image: a release there would be a click outside,
		// slot -999, which drops the item carried. As the recipe book's button.
		for (SortButton button : sortButtons) {
			if (button.isMouseOver(mouseX, mouseY)) {
				return false;
			}
		}
		return super.hasClickedOutside(mouseX, mouseY, left, top);
	}

	/**
	 * As the stonecutter screen: the menu first, which highlights the mode at once,
	 * then vanilla's container button packet; the server sorts (SmartChestMenu.clickMenuButton).
	 */
	private void sort(SmartChestSorting.Mode mode) {
		menu.clickMenuButton(minecraft.player, mode.ordinal());
		minecraft.gameMode.handleInventoryButtonClick(menu.containerId, mode.ordinal());
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
		for (SortButton button : sortButtons) {
			if (button.isHovered()) {
				graphics.setComponentTooltipForNextFrame(font, List.of(button.getMessage(), SORT_HINT), mouseX, mouseY);
			}
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

	/** One button per mode; the chest's last one used, which also sorts after a compression, stays pressed. */
	private class SortButton extends ImageButton {
		private final SmartChestSorting.Mode mode;
		private final Identifier icon;

		SortButton(int x, int y, SmartChestSorting.Mode mode) {
			super(x, y, BUTTON_SIZE, BUTTON_SIZE, new WidgetSprites(BUTTON, BUTTON, BUTTON_HIGHLIGHTED),
					button -> sort(mode), Component.translatable("container.smartchest.sort." + mode.key()));
			this.mode = mode;
			icon = SmartChestMod.id("container/sort/" + mode.key());
		}

		@Override
		public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			Identifier frame = menu.sortMode() == mode ? BUTTON_SELECTED : sprites.get(isActive(), isHoveredOrFocused());
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, frame, getX(), getY(), width, height);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, getX(), getY(), width, height);
		}
	}
}
