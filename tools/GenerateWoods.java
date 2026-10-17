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
 * the language files are kept). Then reads everything back, and checks WOODS
 * against SmartChestBlocks.ALL.
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

	/** Every file written, with its bytes, to read back at the end. */
	static final Map<Path, byte[]> written = new LinkedHashMap<>();
	static final List<String> errors = new ArrayList<>();

	public static void main(String[] args) throws Exception {
		try (ZipFile jar = new ZipFile(args[0])) {
			float[] oakPlanks = averageHsb(read(jar, planksTexture("oak")));
			for (Wood wood : WOODS) {
				float[] planks = averageHsb(read(jar, planksTexture(wood.id())));
				for (String part : CHEST_PARTS) {
					writeImage(TEXTURES.resolve(wood.id() + part + ".png"), texture(jar, part, oakPlanks, planks));
				}
				writeWood(wood);
			}
		}
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

	// The provisional texture (SPEC §9 q.3): the vanilla oak chest, its grey latch
	// turned copper like the ingot of the recipe, which is enough to tell a Smart
	// Chest from a vanilla one, and its wood shifted from the colour of the oak
	// planks to that of the wood's planks. The final texture replaces this method
	// only.
	static BufferedImage texture(ZipFile jar, String part, float[] oakPlanks, float[] planks) throws IOException {
		BufferedImage base = read(jar, "assets/minecraft/textures/entity/chest/normal" + part + ".png");
		BufferedImage out = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < base.getHeight(); y++) {
			for (int x = 0; x < base.getWidth(); x++) {
				int argb = base.getRGB(x, y);
				int alpha = argb >>> 24;
				if (alpha == 0) {
					out.setRGB(x, y, argb);
					continue;
				}
				float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
				int rgb;
				if (hsb[1] < 0.2F) {
					// The latch is the only grey on the texture.
					rgb = Color.HSBtoRGB(0.06F, 0.7F, Math.min(1.0F, hsb[2] * 1.05F));
				} else {
					float hue = hsb[0] + planks[0] - oakPlanks[0];
					float saturation = Math.min(1.0F, hsb[1] * planks[1] / oakPlanks[1]);
					float brightness = Math.min(1.0F, hsb[2] * planks[2] / oakPlanks[2]);
					rgb = Color.HSBtoRGB(hue - (float) Math.floor(hue), saturation, brightness);
				}
				out.setRGB(x, y, (alpha << 24) | (rgb & 0xFFFFFF));
			}
		}
		return out;
	}

	static String planksTexture(String wood) {
		return "assets/minecraft/textures/block/" + wood + "_planks.png";
	}

	/** Hue, saturation and brightness of the mean colour of the opaque pixels. */
	static float[] averageHsb(BufferedImage image) {
		long r = 0, g = 0, b = 0, n = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getRGB(x, y);
				if (argb >>> 24 != 0) {
					r += (argb >> 16) & 0xFF;
					g += (argb >> 8) & 0xFF;
					b += argb & 0xFF;
					n++;
				}
			}
		}
		return Color.RGBtoHSB((int) (r / n), (int) (g / n), (int) (b / n), null);
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
				      "type": "minecraft:chest",
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
				if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
					errors.add(file.getKey() + " is not a 64x64 image");
				}
			}
		}
		int expected = WOODS.size() * (6 + CHEST_PARTS.length) + 4;
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

		// No file left over from a wood no longer in the list.
		for (Path dir : List.of(ASSETS.resolve("blockstates"), ASSETS.resolve("models/block"), ASSETS.resolve("models/item"),
				ASSETS.resolve("items"), TEXTURES, DATA.resolve("smartchest/recipe"), DATA.resolve("smartchest/loot_table/blocks"))) {
			try (Stream<Path> files = Files.list(dir)) {
				files.filter(file -> !written.containsKey(file)).forEach(file -> errors.add("unexpected " + file));
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
