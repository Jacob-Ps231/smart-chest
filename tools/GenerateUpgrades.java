import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Generates the files of the upgrades (SPEC §4, step 6): for each capacity upgrade,
 * its item, item model, recipe and sprite; the empty slot icons of the capacity,
 * crafter and forced compression (step 8b) slots; and the chest screen background,
 * generic_54.png with the upgrade panel. Then reads everything back. Names and
 * translations are in the language files, by hand.
 *
 * The sprites are placeholders: replace the method sprite() to draw others.
 *
 * Usage, from the project root:
 *   java tools/GenerateUpgrades.java C:/gradle-home/caches/fabric-loom/26.3/minecraft-merged.jar
 */
public class GenerateUpgrades {
	/** A capacity upgrade: its id, and the vanilla item at the centre of its recipe, which also colours its sprite. */
	record Upgrade(String id, String material, String materialTexture) {
	}

	// Same order as SmartChestItems.CAPACITY_UPGRADES: lowest tier first.
	static final List<Upgrade> UPGRADES = List.of(
			new Upgrade("capacity_upgrade_128", "minecraft:iron_ingot", "iron_ingot"),
			new Upgrade("capacity_upgrade_256", "minecraft:gold_ingot", "gold_ingot"),
			new Upgrade("capacity_upgrade_512", "minecraft:diamond", "diamond"),
			new Upgrade("capacity_upgrade_999", "minecraft:netherite_scrap", "netherite_scrap"));

	static final Path ASSETS = Path.of("src/main/resources/assets/smartchest");
	static final Path DATA = Path.of("src/main/resources/data/smartchest");
	static final String VANILLA = "assets/minecraft/textures/";

	// Screen layout, shared with SmartChestScreen and SmartChestMenu: a panel 32 wide
	// at x 178, five slots tall: capacity, crafter, then 4 pixels lower the three
	// forced compressions (SPEC §5.1).
	static final int PANEL_X = 178;
	static final int PANEL_WIDTH = 32;
	static final int PANEL_HEIGHT = 108;
	static final int PANEL_Y = 10;

	static final Map<Path, byte[]> written = new LinkedHashMap<>();
	static final List<String> errors = new ArrayList<>();

	public static void main(String[] args) throws Exception {
		try (ZipFile jar = new ZipFile(args[0])) {
			int[] lapis = palette(read(jar, VANILLA + "item/lapis_lazuli.png"));
			int[] redstone = palette(read(jar, VANILLA + "item/redstone.png"));
			int[] amethyst = palette(read(jar, VANILLA + "item/amethyst_shard.png"));
			for (int n = 0; n < UPGRADES.size(); n++) {
				Upgrade upgrade = UPGRADES.get(n);
				int[] material = palette(read(jar, VANILLA + "item/" + upgrade.materialTexture() + ".png"));
				writeImage(ASSETS.resolve("textures/item/" + upgrade.id() + ".png"), sprite(n + 1, material, lapis, redstone, amethyst));
				writeUpgrade(upgrade);
			}
			writeImage(ASSETS.resolve("textures/gui/sprites/container/slot/capacity_upgrade.png"), capacityIcon());
			writeImage(ASSETS.resolve("textures/gui/sprites/container/slot/compression_upgrade.png"), compressionIcon());
			writeImage(ASSETS.resolve("textures/gui/sprites/container/slot/forced_compression.png"), forcedIcon());
			writeImage(ASSETS.resolve("textures/gui/container/smart_chest.png"),
					background(read(jar, VANILLA + "gui/container/generic_54.png")));
		}
		check();
		if (!errors.isEmpty()) {
			errors.forEach(System.err::println);
			System.exit(1);
		}
		System.out.println(written.size() + " files written and read back");
	}

	// ---- Sprites ----

	/**
	 * A placeholder capacity upgrade, 16x16: a lapis board framed in the material,
	 * a redstone arrow pointing up, and one amethyst pip per tier.
	 */
	static BufferedImage sprite(int tier, int[] material, int[] lapis, int[] redstone, int[] amethyst) {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 1; y <= 14; y++) {
			for (int x = 1; x <= 14; x++) {
				boolean corner = (x == 1 || x == 14) && (y == 1 || y == 14);
				if (corner) {
					continue;
				}
				boolean outline = x == 1 || x == 14 || y == 1 || y == 14;
				boolean frame = x == 2 || x == 13 || y == 2 || y == 13;
				int colour;
				if (outline) {
					colour = material[0];
				} else if (frame) {
					// Light from the top left, as vanilla items.
					colour = x == 2 || y == 2 ? material[2] : material[1];
				} else {
					// A board with a sparse grain.
					colour = (x * 7 + y * 3) % 11 == 0 ? lapis[0] : lapis[1];
				}
				image.setRGB(x, y, colour);
			}
		}
		// The arrow, its point at the top.
		int[][] arrow = ARROW;
		for (int row = 0; row < arrow.length; row++) {
			for (int x = arrow[row][0]; x <= arrow[row][1]; x++) {
				image.setRGB(x, 4 + row, x < 8 ? redstone[2] : redstone[1]);
			}
		}
		// The tier, centred under the arrow.
		for (int pip = 0; pip < tier; pip++) {
			image.setRGB(8 - tier + 2 * pip, 11, amethyst[2]);
		}
		return image;
	}

	/** Rows of the arrow from its point, first and last x of each. */
	static final int[][] ARROW = {{7, 8}, {6, 9}, {5, 10}, {4, 11}, {7, 8}, {7, 8}};

	/** The vanilla slot icons' grey, opaque. */
	static final int ICON = 0xFF9C9C9C;

	/** The outline of a capacity upgrade and its arrow. */
	static BufferedImage capacityIcon() {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int n = 2; n <= 13; n++) {
			image.setRGB(n, 1, ICON);
			image.setRGB(n, 14, ICON);
			image.setRGB(1, n, ICON);
			image.setRGB(14, n, ICON);
		}
		int[][] arrow = ARROW;
		for (int row = 0; row < arrow.length; row++) {
			for (int x = arrow[row][0]; x <= arrow[row][1]; x++) {
				image.setRGB(x, 4 + row, ICON);
			}
		}
		return image;
	}

	/** A 3x3 grid, the crafter's. */
	static BufferedImage compressionIcon() {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		// Three cells of 3 pixels between four lines.
		for (int line = 2; line <= 14; line += 4) {
			for (int a = 2; a <= 14; a++) {
				image.setRGB(line, a, ICON);
				image.setRGB(a, line, ICON);
			}
		}
		return image;
	}

	/** An arrow pointing down onto a block: forced into a block. */
	static BufferedImage forcedIcon() {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		// Shaft, then the head narrowing to its point at y 7.
		int[][] arrow = {{7, 8}, {7, 8}, {7, 8}, {4, 11}, {5, 10}, {6, 9}, {7, 8}};
		for (int row = 0; row < arrow.length; row++) {
			for (int x = arrow[row][0]; x <= arrow[row][1]; x++) {
				image.setRGB(x, 1 + row, ICON);
			}
		}
		for (int y = 10; y <= 14; y++) {
			for (int x = 3; x <= 12; x++) {
				image.setRGB(x, y, ICON);
			}
		}
		return image;
	}

	/** Darkest, middle and lightest colours of a texture, by brightness, transparent pixels left out. */
	static int[] palette(BufferedImage texture) {
		List<Integer> colours = new ArrayList<>();
		for (int y = 0; y < texture.getHeight(); y++) {
			for (int x = 0; x < texture.getWidth(); x++) {
				int argb = texture.getRGB(x, y);
				if (argb >>> 24 == 0xFF) {
					colours.add(argb);
				}
			}
		}
		colours.sort((a, b) -> Integer.compare(brightness(a), brightness(b)));
		return new int[] {
			colours.get(colours.size() * 15 / 100),
			colours.get(colours.size() / 2),
			colours.get(colours.size() * 85 / 100),
		};
	}

	static int brightness(int argb) {
		return 299 * (argb >> 16 & 0xFF) + 587 * (argb >> 8 & 0xFF) + 114 * (argb & 0xFF);
	}

	// ---- Screen background ----

	/** generic_54.png, its chest rows and inventory where they are, plus the upgrade panel. */
	static BufferedImage background(BufferedImage vanilla) {
		BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		copy(vanilla, 0, 0, image, 0, 0, 176, 222);
		panel(vanilla, image, PANEL_X, PANEL_Y);
		for (int slot = 0; slot < 5; slot++) {
			// SmartChestMenu.UPGRADE_X is the item, one pixel inside the slot.
			copy(vanilla, 7, 17, image, PANEL_X + 7, PANEL_Y + 7 + 18 * slot + (slot >= 2 ? 4 : 0), 18, 18);
		}
		return image;
	}

	/**
	 * A vanilla frame of PANEL_WIDTH x PANEL_HEIGHT, cut from generic_54.png: its four
	 * 4x4 corners, its edges (one column or row stretched), its body colour.
	 */
	static void panel(BufferedImage vanilla, BufferedImage image, int px, int py) {
		int w = PANEL_WIDTH;
		int h = PANEL_HEIGHT;
		int right = 172;
		int bottom = 218;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int sx = x < 4 ? x : x >= w - 4 ? right + x - (w - 4) : 10;
				int sy = y < 4 ? y : y >= h - 4 ? bottom + y - (h - 4) : 10;
				image.setRGB(px + x, py + y, vanilla.getRGB(sx, sy));
			}
		}
	}

	static void copy(BufferedImage from, int fx, int fy, BufferedImage to, int tx, int ty, int w, int h) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				to.setRGB(tx + x, ty + y, from.getRGB(fx + x, fy + y));
			}
		}
	}

	// ---- Data ----

	static void writeUpgrade(Upgrade upgrade) throws IOException {
		String id = upgrade.id();
		writeText(ASSETS.resolve("items/" + id + ".json"), """
				{
				  "model": {
				    "type": "minecraft:model",
				    "model": "smartchest:item/%s"
				  }
				}
				""".formatted(id));
		writeText(ASSETS.resolve("models/item/" + id + ".json"), """
				{
				  "parent": "minecraft:item/generated",
				  "textures": {
				    "layer0": "smartchest:item/%s"
				  }
				}
				""".formatted(id));
		// The same grid for all four, only the centre changes (SPEC §4).
		writeText(DATA.resolve("recipe/" + id + ".json"), """
				{
				  "type": "minecraft:crafting_shaped",
				  "key": {
				    "A": "minecraft:amethyst_shard",
				    "L": "minecraft:lapis_lazuli",
				    "M": "%s",
				    "O": "minecraft:observer",
				    "P": "minecraft:piston",
				    "R": "minecraft:redstone"
				  },
				  "pattern": [
				    "LOL",
				    "AMA",
				    "RPR"
				  ],
				  "result": {
				    "id": "smartchest:%s"
				  }
				}
				""".formatted(upgrade.material(), id));
	}

	// ---- Files ----

	static void check() throws IOException {
		for (Map.Entry<Path, byte[]> file : written.entrySet()) {
			if (!Arrays.equals(Files.readAllBytes(file.getKey()), file.getValue())) {
				errors.add(file.getKey() + " differs from what was written");
			}
			if (file.getKey().toString().endsWith(".png")) {
				BufferedImage image = ImageIO.read(file.getKey().toFile());
				int size = file.getKey().endsWith("smart_chest.png") ? 256 : 16;
				if (image == null || image.getWidth() != size || image.getHeight() != size) {
					errors.add(file.getKey() + " is not a " + size + "x" + size + " image");
				}
			}
		}
		int expected = UPGRADES.size() * 4 + 4;
		if (written.size() != expected) {
			errors.add(written.size() + " files written, " + expected + " expected");
		}
	}

	static BufferedImage read(ZipFile jar, String entry) throws IOException {
		try (InputStream in = jar.getInputStream(jar.getEntry(entry))) {
			return ImageIO.read(in);
		}
	}

	static void writeText(Path file, String text) throws IOException {
		write(file, text.getBytes(StandardCharsets.UTF_8));
	}

	static void writeImage(Path file, BufferedImage image) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		write(file, out.toByteArray());
	}

	static void write(Path file, byte[] bytes) throws IOException {
		Files.createDirectories(file.getParent());
		Files.write(file, bytes);
		written.put(file, bytes);
	}
}
