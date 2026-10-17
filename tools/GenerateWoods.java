import java.awt.Color;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Generates the files of every Smart Chest wood (SPEC §7.3) from WOODS: blockstate,
 * block and item models, item, textures, recipe, loot table, the mineable/axe and
 * golem_destinations tags, and the block names in en_us and fr_fr (other keys of
 * the language files are kept); and from tools/latch_front.png, the latch drawn by
 * Jérôme, the boxes of the latch in SmartChestLatch.java. Then reads everything
 * back, and checks WOODS against SmartChestBlocks.ALL.
 *
 * Usage, from the project root:
 *   java tools/GenerateWoods.java C:/gradle-home/caches/fabric-loom/26.3/minecraft-merged.jar
 */
public class GenerateWoods {
	/** A vanilla wood: its id, which also names its planks, and its chest's French name after "Coffre intelligent". */
	record Wood(String id, String french) {
	}

	// Same order as SmartChestBlocks.ALL. French names as the vanilla shelves have them.
	static final List<Wood> WOODS = List.of(
			new Wood("oak", "en chêne"),
			new Wood("spruce", "en sapin"),
			new Wood("birch", "en bouleau"),
			new Wood("jungle", "en acajou"),
			new Wood("acacia", "en acacia"),
			new Wood("dark_oak", "en chêne noir"),
			new Wood("mangrove", "en palétuvier"),
			new Wood("cherry", "en cerisier"),
			new Wood("pale_oak", "en chêne pâle"),
			new Wood("poplar", "en peuplier"),
			new Wood("crimson", "carmin"),
			new Wood("warped", "biscornu"));

	static final String[] CHEST_PARTS = {"", "_left", "_right"};
	static final Path ASSETS = Path.of("src/main/resources/assets/smartchest");
	static final Path DATA = Path.of("src/main/resources/data");
	static final Path TEXTURES = ASSETS.resolve("textures/entity/chest");
	static final Path BLOCKS_JAVA = Path.of("src/main/java/re/jerome/smartchest/SmartChestBlocks.java");
	/** The latch as Jérôme draws it, see tools/latch_front.txt, and its boxes for SmartChestLayers. */
	static final Path LATCH_FRONT = Path.of("tools/latch_front.png");
	static final Path LATCH_JAVA = Path.of("src/client/java/re/jerome/smartchest/client/SmartChestLatch.java");

	/** Every file written, with its bytes, to read back at the end. */
	static final Map<Path, byte[]> written = new LinkedHashMap<>();
	static final List<String> errors = new ArrayList<>();

	public static void main(String[] args) throws Exception {
		BufferedImage latchFront = ImageIO.read(LATCH_FRONT.toFile());
		List<Box> latch = latchBoxes(latchFront);
		try (ZipFile jar = new ZipFile(args[0])) {
			for (Wood wood : WOODS) {
				BufferedImage planks = read(jar, planksTexture(wood.id()));
				for (String part : CHEST_PARTS) {
					writeImage(TEXTURES.resolve(wood.id() + part + ".png"), texture(part, planks, latchFront, latch));
				}
				writeWood(wood);
			}
		}
		// After the textures, so that a failure above leaves the boxes as they were.
		writeText(LATCH_JAVA, latchJava(latch));
		List<String> chests = WOODS.stream().map(wood -> "smartchest:" + chest(wood)).toList();
		writeText(DATA.resolve("minecraft/tags/block/mineable/axe.json"), tag(chests));
		writeText(DATA.resolve("smartchest/tags/block/golem_destinations.json"), tag(chests));
		writeLang("en_us", wood -> title(wood.id()) + " Smart Chest");
		writeLang("fr_fr", wood -> "Coffre intelligent " + wood.french());

		check(chests);
		if (!errors.isEmpty()) {
			errors.forEach(error -> System.err.println("ERROR " + error));
			System.exit(1);
		}
		System.out.println("Wrote and read back " + written.size() + " files for " + WOODS.size() + " woods.");
	}

	// The texture (SPEC §7.3), drawn by this code after Jérôme's sketches, at twice
	// the vanilla resolution: 128x128 for a model that declares 64x64.
	// ModelPart.Polygon divides the UVs by the declared size, so each model unit
	// is SCALE pixels and the image only needs the vanilla proportions. The wood is
	// the wood's planks with a finer grain; over it an iron frame on every edge,
	// copper corners, a band at the seam of lid and body with a copper rivet at
	// each end, and the latch that Jérôme draws in tools/latch_front.png.
	//
	// Layout, in model units, read on ChestModel and checked against the vanilla
	// textures. A cube textured from (u, v), of size w x h x d, is laid out by
	// ModelPart.Cube as
	//
	//          u      u+d      u+d+w     u+2d+w    u+2d+2w
	//   v             | DOWN   | UP      |
	//   v+d    | WEST | NORTH  | EAST    | SOUTH   |
	//   v+d+h
	//
	//   lid    (0, 0)    14 x 5 x 14, a half 15 wide; DOWN is its inside
	//   body   (0, 19)   14 x 10 x 14, a half 15 wide; UP is its inside
	//   latch  (u, 44)   the boxes of latchBoxes(), below everything vanilla,
	//                    sized in half units: one pixel each here
	//
	// SOUTH is the front, the side of the latch. DOWN and UP are stored with their
	// top row at the front and their left column at the west. Every side face is
	// stored turned half a turn: its top row is the bottom of the face, its left
	// column the right of the face seen from outside. Faces are painted here in
	// view coordinates, as seen from outside and the right way up, and Face turns
	// them. The lid and body overlap by one unit, so the two bottom rows of the
	// lid's sides and the two top rows of the body's are drawn in the same place
	// and must be the same: band() alone paints them.
	//
	// The halves of a double chest meet on the west of _left and on the east of
	// _right, whose face there is not drawn. In view coordinates the seam is the
	// left column of DOWN, UP and SOUTH and the right column of NORTH on _left, the
	// other way round on _right. Nothing is framed along it, so the double reads as
	// one chest.
	static final int SCALE = 2;

	// Iron; verdigris after oxidized copper; copper after the copper ingot.
	static final int IRON_LIGHT = 0xFF4A4D55;
	static final int IRON = 0xFF2E3036;
	static final int IRON_DARK = 0xFF1B1C20;
	static final int VERDIGRIS_LIGHT = 0xFF6EC59F;
	static final int VERDIGRIS = 0xFF4C9484;
	static final int COPPER_HIGHLIGHT = 0xFFFC9982;
	static final int COPPER_LIGHT = 0xFFE77C56;
	static final int COPPER = 0xFFC15A36;
	static final int COPPER_DARK = 0xFF8A4129;

	// The latch: tools/latch_front.png gives both its shape and its front. Its
	// opaque pixels are the front of the latch of a single chest, one pixel per
	// half unit: column c from x = c - 5 half units off the middle of the chest,
	// row r from y = 21 - r half units up the block; its other pixels are clear.
	// The pixels of the colours of LATCH_RAISED, the copper of the latch, stand as
	// deep as the vanilla latch, from LATCH_Z0 to LATCH_Z1; every other colour is
	// set back by LATCH_RECESS, so the stone sits sunk in its copper frame. Each run of
	// opaque pixels of one depth in a row is merged with the same run in the rows
	// below into a box; the boxes go to SmartChestLatch.java for
	// SmartChestLayers. A box's front is the drawing, its other faces the colour
	// of the edge of the drawing beside them, darker: the walls of a recess are
	// thus the copper around it.
	static final int LATCH_WIDTH = 10, LATCH_HEIGHT = 11, LATCH_LEFT = -5, LATCH_TOP = 21;
	static final int LATCH_Z0 = 30, LATCH_Z1 = 32, LATCH_RECESS = 1;
	// Outline, shadow, base, light and glint, from copper_ingot.png.
	static final Set<Integer> LATCH_RAISED = Set.of(0xFF6D3421, 0xFF9C4529, 0xFFC15A36, 0xFFE77C56, 0xFFFC9982);

	/**
	 * A box of the latch on a single chest, in half units: x from the centre of
	 * the chest, y in the block, z1 its front. Its texture offset is in units.
	 */
	record Box(int x0, int x1, int y0, int y1, int z1, int u, int v) {
	}

	/** The pixel of the drawing from (x, y) to (x + 1, y + 1), in half units. */
	static int latchPixel(BufferedImage front, int x, int y) {
		return front.getRGB(x - LATCH_LEFT, LATCH_TOP - y);
	}

	/** The boxes of the drawing, their textures packed in rows from (0, 44). */
	static List<Box> latchBoxes(BufferedImage front) {
		if (front.getWidth() != LATCH_WIDTH || front.getHeight() != LATCH_HEIGHT) {
			throw new IllegalStateException(LATCH_FRONT + " is not " + LATCH_WIDTH + "x" + LATCH_HEIGHT);
		}
		List<int[]> runs = new ArrayList<>(); // x0, x1, y0, y1, z1
		for (int row = 0; row < LATCH_HEIGHT; row++) {
			int y = LATCH_TOP - row;
			for (int col = 0; col < LATCH_WIDTH; col++) {
				int alpha = front.getRGB(col, row) >>> 24;
				if (alpha != 0 && alpha != 0xFF) {
					throw new IllegalStateException(LATCH_FRONT + ": pixel " + col + ", " + row + " neither opaque nor clear");
				}
				if (alpha == 0) {
					continue;
				}
				int start = col, z1 = latchDepth(front.getRGB(col, row));
				while (col + 1 < LATCH_WIDTH && front.getRGB(col + 1, row) >>> 24 == 0xFF
						&& latchDepth(front.getRGB(col + 1, row)) == z1) {
					col++;
				}
				int x0 = start + LATCH_LEFT, x1 = col + 1 + LATCH_LEFT;
				runs.stream().filter(run -> run[0] == x0 && run[1] == x1 && run[2] == y + 1 && run[4] == z1).findFirst()
						.ifPresentOrElse(run -> run[2] = y, () -> runs.add(new int[] {x0, x1, y, y + 1, z1}));
			}
		}
		if (runs.isEmpty()) {
			throw new IllegalStateException(LATCH_FRONT + " has no opaque pixel");
		}
		// In units: a box w x h x d takes 2d + 2w by d + h, here sized in half units.
		List<Box> boxes = new ArrayList<>();
		int u = 0, v = 44, shelf = 0;
		for (int[] run : runs) {
			int depth = run[4] - LATCH_Z0;
			int width = depth + run[1] - run[0], height = (depth + run[3] - run[2] + 1) / 2;
			if (u + width > 64) {
				u = 0;
				v += shelf;
				shelf = 0;
			}
			if (v + height > 64) {
				throw new IllegalStateException(LATCH_FRONT + ": too many boxes for the texture");
			}
			boxes.add(new Box(run[0], run[1], run[2], run[3], run[4], u, v));
			u += width;
			shelf = Math.max(shelf, height);
		}
		return boxes;
	}

	/** The front of the box of a pixel of the drawing. */
	static int latchDepth(int argb) {
		return LATCH_RAISED.contains(argb) ? LATCH_Z1 : LATCH_Z1 - LATCH_RECESS;
	}

	static String latchJava(List<Box> boxes) {
		StringBuilder out = new StringBuilder("""
				package re.jerome.smartchest.client;

				// Written by tools/GenerateWoods.java from tools/latch_front.png: do not edit.
				// The boxes of the latch on a single chest, in half units: x from the centre
				// of the chest, y in the block, z from Z0 to their front z1. Their texture
				// offset is in units.
				final class SmartChestLatch {
					record Box(int x0, int x1, int y0, int y1, int z1, int u, int v) {
					}

					static final int Z0 = %d;
					static final Box[] BOXES = {
				""".formatted(LATCH_Z0));
		for (Box box : boxes) {
			out.append("\t\t\tnew Box(%d, %d, %d, %d, %d, %d, %d),\n"
					.formatted(box.x0(), box.x1(), box.y0(), box.y1(), box.z1(), box.u(), box.v()));
		}
		return out.append("\t};\n\n\tprivate SmartChestLatch() {\n\t}\n}\n").toString();
	}

	static BufferedImage texture(String part, BufferedImage planks, BufferedImage latchFront, List<Box> latch) {
		BufferedImage out = new BufferedImage(64 * SCALE, 64 * SCALE, BufferedImage.TYPE_INT_ARGB);
		boolean west = part.equals("_left"), east = part.equals("_right");
		int width = part.isEmpty() ? 14 : 15;
		for (boolean lid : new boolean[] {true, false}) {
			int h = lid ? 5 : 10;
			Face[] faces = faces(0, (lid ? 0 : 19) * SCALE, width * SCALE, h * SCALE, 14 * SCALE);
			faces[lid ? 0 : 1].seams(west, east).wood(out, planks).frame(out).inside(out, lid ? 0.45F : 0.2F);
			faces[lid ? 1 : 0].seams(west, east).wood(out, planks).frame(out).corners(out, 3, 3, true, true);
			for (int side = 2; side < 6; side++) {
				if (side == 2 && west || side == 4 && east) {
					continue;
				}
				Face face = side == 3 ? faces[3].seams(east, west) : side == 5 ? faces[5].seams(west, east) : faces[side];
				// The corners away from the seam of lid and body, the band on it.
				face.wood(out, planks).frame(out).corners(out, 3, 3, lid, !lid).band(out, lid);
			}
		}

		// The latch, each half of a double chest with its half.
		for (Box box : latch) {
			int x0 = west ? Math.max(box.x0(), 0) : box.x0();
			int x1 = east ? Math.min(box.x1(), 0) : box.x1();
			if (x0 >= x1) {
				continue;
			}
			// Half units are pixels here.
			int w = x1 - x0, h = box.y1() - box.y0(), d = box.z1() - LATCH_Z0;
			Face[] faces = faces(box.u() * SCALE, box.v() * SCALE, w, h, d);
			for (int vy = 0; vy < h; vy++) {
				int y = box.y1() - 1 - vy;
				for (int vx = 0; vx < w; vx++) {
					faces[5].set(out, vx, vy, latchPixel(latchFront, x0 + vx, y));
					faces[3].set(out, vx, vy, shade(latchPixel(latchFront, x1 - 1 - vx, y), 0.5F));
				}
				for (int vx = 0; vx < d; vx++) {
					faces[2].set(out, vx, vy, shade(latchPixel(latchFront, x0, y), 0.7F));
					faces[4].set(out, vx, vy, shade(latchPixel(latchFront, x1 - 1, y), 0.7F));
				}
			}
			for (int vy = 0; vy < d; vy++) {
				for (int vx = 0; vx < w; vx++) {
					faces[1].set(out, vx, vy, shade(latchPixel(latchFront, x0 + vx, box.y1() - 1), 0.85F));
					faces[0].set(out, vx, vy, shade(latchPixel(latchFront, x0 + vx, box.y0()), 0.6F));
				}
			}
		}
		return out;
	}

	/** The DOWN, UP, WEST, NORTH, EAST and SOUTH faces of a cube, laid out as above, in pixels; the side faces turned. */
	static Face[] faces(int u, int v, int w, int h, int d) {
		return new Face[] {
				new Face(u + d, v, w, d, false, false, false),
				new Face(u + d + w, v, w, d, false, false, false),
				new Face(u, v + d, d, h, true, false, false),
				new Face(u + d, v + d, w, h, true, false, false),
				new Face(u + d + w, v + d, d, h, true, false, false),
				new Face(u + 2 * d + w, v + d, w, h, true, false, false)};
	}

	/**
	 * The planks at twice their size, each texel made four pixels: a seam of the
	 * planks (a run of its darkest texels) keeps one dark row over a softer one,
	 * and the boards get a grain of short light and dark streaks along them.
	 */
	static int grain(BufferedImage planks, float mean, int vx, int vy) {
		int px = Math.floorMod(vx / 2, 16), py = Math.floorMod(vy / 2, 16);
		int argb = planks.getRGB(px, py);
		if (brightness(argb) < mean * 0.8F) {
			boolean run = brightness(planks.getRGB((px + 15) % 16, py)) < mean * 0.8F
					&& brightness(planks.getRGB((px + 1) % 16, py)) < mean * 0.8F;
			return run && vy % 2 == 1 ? mix(argb, planks.getRGB(px, (py + 1) % 16), 0.5F) : argb;
		}
		long streak = Math.floorMod(((vx + 7L * (vy * 31 % 11)) / 5) * 2654435761L + vy * 40503L, 7L);
		return shade(argb, streak == 0 ? 0.92F : streak == 1 ? 1.07F : 1.0F);
	}

	static float brightness(int argb) {
		return Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null)[2];
	}

	static int shade(int argb, float factor) {
		int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * factor));
		int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * factor));
		int b = Math.min(255, Math.round((argb & 0xFF) * factor));
		return (argb & 0xFF000000) | r << 16 | g << 8 | b;
	}

	static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	/**
	 * A face on the texture, its position and size in pixels. It is painted
	 * in view pixels (see above): turned tells it is stored half a turn round, and
	 * seamLeft and seamRight whether its left or right column is the seam of a
	 * double chest.
	 */
	record Face(int x, int y, int w, int h, boolean turned, boolean seamLeft, boolean seamRight) {
		Face seams(boolean left, boolean right) {
			return new Face(x, y, w, h, turned, left, right);
		}

		int width() {
			return w;
		}

		int height() {
			return h;
		}

		boolean contains(int vx, int vy) {
			return vx >= 0 && vx < width() && vy >= 0 && vy < height();
		}

		int tx(int vx) {
			return x + (turned ? w - 1 - vx : vx);
		}

		int ty(int vy) {
			return y + (turned ? h - 1 - vy : vy);
		}

		void set(BufferedImage image, int vx, int vy, int argb) {
			if (contains(vx, vy)) {
				image.setRGB(tx(vx), ty(vy), argb);
			}
		}

		void shadeAt(BufferedImage image, int vx, int vy, float factor) {
			if (contains(vx, vy)) {
				image.setRGB(tx(vx), ty(vy), shade(image.getRGB(tx(vx), ty(vy)), factor));
			}
		}

		Face fill(BufferedImage image, int argb) {
			for (int vy = 0; vy < height(); vy++) {
				row(image, vy, argb);
			}
			return this;
		}

		Face row(BufferedImage image, int vy, int argb) {
			for (int vx = 0; vx < width(); vx++) {
				set(image, vx, vy, argb);
			}
			return this;
		}

		/** Distance in pixels to the nearest edge that is not the seam. */
		int edge(int vx, int vy) {
			int distance = Math.min(vy, height() - 1 - vy);
			if (!seamLeft) {
				distance = Math.min(distance, vx);
			}
			if (!seamRight) {
				distance = Math.min(distance, width() - 1 - vx);
			}
			return distance;
		}

		Face wood(BufferedImage image, BufferedImage planks) {
			float mean = 0;
			for (int py = 0; py < 16; py++) {
				for (int px = 0; px < 16; px++) {
					mean += brightness(planks.getRGB(px, py)) / 256;
				}
			}
			for (int vy = 0; vy < height(); vy++) {
				for (int vx = 0; vx < width(); vx++) {
					set(image, vx, vy, grain(planks, mean, vx, vy));
				}
			}
			return this;
		}

		/** Iron on each edge but the seam, light on the outer pixel, and its shadow on the wood. */
		Face frame(BufferedImage image) {
			for (int vy = 0; vy < height(); vy++) {
				for (int vx = 0; vx < width(); vx++) {
					switch (edge(vx, vy)) {
						case 0 -> set(image, vx, vy, IRON_LIGHT);
						case 1 -> set(image, vx, vy, IRON);
						case 2 -> shadeAt(image, vx, vy, 0.7F);
						default -> {
						}
					}
				}
			}
			return this;
		}

		/** The inside of the chest: a rim of wood, one unit wide inside the frame, around a shadow. */
		Face inside(BufferedImage image, float shadow) {
			for (int vy = 0; vy < height(); vy++) {
				for (int vx = 0; vx < width(); vx++) {
					if (edge(vx, vy) >= 2 * SCALE) {
						shadeAt(image, vx, vy, shadow);
					}
				}
			}
			return this;
		}

		/**
		 * A copper L in the top corners (if top) and the bottom ones (if bottom)
		 * away from the seam, its arms across and along pixels long on the frame,
		 * a light pixel at its tip, its shadow on the wood, then verdigris on the
		 * iron where the arms end.
		 */
		Face corners(BufferedImage image, int across, int along, boolean top, boolean bottom) {
			for (int corner = 0; corner < 4; corner++) {
				boolean right = corner % 2 == 1, low = corner >= 2;
				if ((right ? seamRight : seamLeft) || !(low ? bottom : top)) {
					continue;
				}
				int cx = right ? width() - 1 : 0, cy = low ? height() - 1 : 0, dx = right ? -1 : 1, dy = low ? -1 : 1;
				for (int i = 2; i <= across; i++) {
					shadeAt(image, cx + dx * i, cy + 2 * dy, 0.6F);
				}
				for (int i = 2; i <= along; i++) {
					shadeAt(image, cx + 2 * dx, cy + dy * i, 0.6F);
				}
				for (int i = 0; i < across; i++) {
					set(image, cx + dx * i, cy, i == 0 ? COPPER_HIGHLIGHT : COPPER_LIGHT);
					set(image, cx + dx * i, cy + dy, COPPER);
				}
				for (int i = 1; i < along; i++) {
					set(image, cx, cy + dy * i, COPPER_LIGHT);
					set(image, cx + dx, cy + dy * i, COPPER);
				}
				set(image, cx + dx * across, cy, COPPER_DARK);
				set(image, cx + dx * across, cy + dy, COPPER_DARK);
				set(image, cx, cy + dy * along, COPPER_DARK);
				set(image, cx + dx, cy + dy * along, COPPER_DARK);
				set(image, cx + dx * (across + 1), cy, VERDIGRIS_LIGHT);
				set(image, cx + dx * (across + 1), cy + dy, VERDIGRIS);
				set(image, cx, cy + dy * (along + 1), VERDIGRIS_LIGHT);
				set(image, cx + dx, cy + dy * (along + 1), VERDIGRIS);
			}
			return this;
		}

		/**
		 * The band at the seam of lid and body, light on top, and a copper rivet at
		 * each end on the rows the lid and body share: the lid's two bottom rows,
		 * the body's two top rows, then two more rows and a shadow on the body.
		 */
		Face band(BufferedImage image, boolean lid) {
			int shared = lid ? height() - 2 : 0;
			row(image, shared, IRON_LIGHT);
			row(image, shared + 1, IRON);
			if (!lid) {
				row(image, 2, IRON);
				row(image, 3, IRON_DARK);
				for (int vx = 0; vx < width(); vx++) {
					if (edge(vx, 4) > 1) {
						shadeAt(image, vx, 4, 0.65F);
					}
				}
			}
			for (boolean right : new boolean[] {false, true}) {
				if (right ? seamRight : seamLeft) {
					continue;
				}
				int outer = right ? width() - 1 : 0, inner = right ? width() - 2 : 1;
				set(image, outer, shared, COPPER_HIGHLIGHT);
				set(image, inner, shared, COPPER_LIGHT);
				set(image, outer, shared + 1, COPPER);
				set(image, inner, shared + 1, COPPER_DARK);
			}
			return this;
		}

	}

	static String planksTexture(String wood) {
		return "assets/minecraft/textures/block/" + wood + "_planks.png";
	}

	static String chest(Wood wood) {
		return wood.id() + "_smart_chest";
	}

	static void writeWood(Wood wood) throws IOException {
		String name = chest(wood);
		String planks = "minecraft:" + wood.id() + "_planks";
		writeText(ASSETS.resolve("blockstates/" + name + ".json"), """
				{
				  "variants": {
				    "": {
				      "model": "smartchest:block/%s"
				    }
				  }
				}
				""".formatted(name));
		writeText(ASSETS.resolve("models/block/" + name + ".json"), """
				{
				  "textures": {
				    "particle": "minecraft:block/%s_planks"
				  }
				}
				""".formatted(wood.id()));
		writeText(ASSETS.resolve("models/item/" + name + ".json"), """
				{
				  "parent": "minecraft:item/template_chest",
				  "textures": {
				    "particle": "minecraft:block/%s_planks"
				  }
				}
				""".formatted(wood.id()));
		writeText(ASSETS.resolve("items/" + name + ".json"), """
				{
				  "model": {
				    "type": "minecraft:special",
				    "base": "smartchest:item/%s",
				    "model": {
				      "type": "smartchest:chest",
				      "texture": "smartchest:%s"
				    }
				  }
				}
				""".formatted(name, wood.id()));
		writeText(DATA.resolve("smartchest/recipe/" + name + ".json"), """
				{
				  "type": "minecraft:crafting_shaped",
				  "key": {
				    "#": "%s",
				    "C": "minecraft:copper_ingot"
				  },
				  "pattern": [
				    "###",
				    "#C#",
				    "###"
				  ],
				  "result": {
				    "id": "smartchest:%s"
				  }
				}
				""".formatted(planks, name));
		writeText(DATA.resolve("smartchest/loot_table/blocks/" + name + ".json"), """
				{
				  "type": "minecraft:block",
				  "pools": [
				    {
				      "condition": {
				        "type": "minecraft:survives_explosion"
				      },
				      "entries": [
				        {
				          "type": "minecraft:item",
				          "modifier": {
				            "type": "minecraft:copy_components",
				            "include": [
				              "minecraft:custom_name"
				            ],
				            "source": "block_entity"
				          },
				          "name": "smartchest:%s"
				        }
				      ],
				      "rolls": 1
				    }
				  ],
				  "random_sequence": "smartchest:blocks/%s"
				}
				""".formatted(name, name));
	}

	static String tag(List<String> values) {
		return "{\n  \"values\": [\n" + String.join(",\n", values.stream().map(v -> "    \"" + v + "\"").toList())
				+ "\n  ]\n}\n";
	}

	static final Pattern LANG_ENTRY = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
	static final Pattern CHEST_KEY = Pattern.compile("block\\.smartchest\\.[a-z_]+_smart_chest");

	/** Replaces the chest names of a language file, keeps its other keys, sorts them all. */
	static void writeLang(String language, java.util.function.Function<Wood, String> name) throws IOException {
		Path file = ASSETS.resolve("lang/" + language + ".json");
		Map<String, String> entries = readLang(file);
		entries.keySet().removeIf(key -> CHEST_KEY.matcher(key).matches());
		for (Wood wood : WOODS) {
			entries.put("block.smartchest." + chest(wood), name.apply(wood));
		}
		StringBuilder out = new StringBuilder("{\n");
		entries.forEach((key, value) -> out.append("  \"").append(key).append("\": \"")
				.append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append("\",\n"));
		out.setLength(out.length() - 2);
		writeText(file, out.append("\n}\n").toString());
	}

	/** A flat language file, values unescaped. */
	static Map<String, String> readLang(Path file) throws IOException {
		Map<String, String> entries = new TreeMap<>();
		Matcher matcher = LANG_ENTRY.matcher(Files.readString(file));
		while (matcher.find()) {
			entries.put(matcher.group(1), matcher.group(2).replace("\\\"", "\"").replace("\\\\", "\\"));
		}
		return entries;
	}

	static void check(List<String> chests) throws IOException {
		// Byte for byte what was meant, and every texture still decodes at its size.
		for (Map.Entry<Path, byte[]> file : written.entrySet()) {
			if (!Arrays.equals(Files.readAllBytes(file.getKey()), file.getValue())) {
				errors.add(file.getKey() + " differs from what was written");
			}
			if (file.getKey().toString().endsWith(".png")) {
				BufferedImage image = ImageIO.read(file.getKey().toFile());
				if (image == null || image.getWidth() != 64 * SCALE || image.getHeight() != 64 * SCALE) {
					errors.add(file.getKey() + " is not a " + 64 * SCALE + "x" + 64 * SCALE + " image");
				}
			}
		}
		int expected = WOODS.size() * (6 + CHEST_PARTS.length) + 5;
		if (written.size() != expected) {
			errors.add(written.size() + " files written, " + expected + " expected");
		}

		// Every chest in both tags and both languages, once.
		for (String tag : List.of("minecraft/tags/block/mineable/axe.json", "smartchest/tags/block/golem_destinations.json")) {
			List<String> values = new ArrayList<>();
			Matcher matcher = Pattern.compile("\"(smartchest:[a-z_]+)\"").matcher(Files.readString(DATA.resolve(tag)));
			while (matcher.find()) {
				values.add(matcher.group(1));
			}
			if (!values.equals(chests)) {
				errors.add(tag + " holds " + values);
			}
		}
		for (String language : List.of("en_us", "fr_fr")) {
			List<String> keys = readLang(ASSETS.resolve("lang/" + language + ".json")).keySet().stream()
					.filter(key -> CHEST_KEY.matcher(key).matches()).toList();
			List<String> wanted = WOODS.stream().map(wood -> "block.smartchest." + chest(wood)).sorted().toList();
			if (!keys.equals(wanted)) {
				errors.add(language + " names " + keys);
			}
		}

		// No file left over from a wood no longer in the list. The upgrades share the
		// item, model and recipe folders, with their own script: chest files only.
		for (Path dir : List.of(ASSETS.resolve("blockstates"), ASSETS.resolve("models/block"), ASSETS.resolve("models/item"),
				ASSETS.resolve("items"), TEXTURES, DATA.resolve("smartchest/recipe"), DATA.resolve("smartchest/loot_table/blocks"))) {
			try (Stream<Path> files = Files.list(dir)) {
				files.filter(file -> !written.containsKey(file))
						.filter(file -> dir.equals(TEXTURES) || file.getFileName().toString().endsWith("_smart_chest.json"))
						.forEach(file -> errors.add("unexpected " + file));
			}
		}

		// The same woods, in the same order and with their own planks, as the mod.
		List<String> registered = new ArrayList<>();
		Matcher matcher = Pattern.compile("register\\(\"([a-z_]+)\", Blocks\\.([A-Z_]+)_PLANKS\\)")
				.matcher(Files.readString(BLOCKS_JAVA));
		while (matcher.find()) {
			registered.add(matcher.group(1));
			if (!matcher.group(1).toUpperCase().equals(matcher.group(2))) {
				errors.add(BLOCKS_JAVA + ": " + matcher.group(1) + " with " + matcher.group(2) + "_PLANKS");
			}
		}
		List<String> listed = WOODS.stream().map(Wood::id).toList();
		if (!registered.equals(listed)) {
			errors.add(BLOCKS_JAVA + " registers " + registered + ", WOODS lists " + listed);
		}
	}

	static String title(String id) {
		StringBuilder out = new StringBuilder();
		for (String word : id.split("_")) {
			out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
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
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(image, "png", bytes);
		write(file, bytes.toByteArray());
	}

	static void write(Path file, byte[] bytes) throws IOException {
		Files.createDirectories(file.getParent());
		Files.write(file, bytes);
		written.put(file, bytes);
	}
}
