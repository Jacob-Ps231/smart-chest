package re.jerome.smartchest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.StrictJsonParser;

// Gameplay values live here rather than scattered as constants (SPEC 6).
//
// Reading and writing use two codecs built from the same field list. The reading
// one uses optionalFieldOf so a partial file still loads; the writing one uses
// fieldOf, because optionalFieldOf omits a field equal to its default and would
// generate a file with nothing in it to edit.
//
// The file is written only when it is missing. A file the player edited is never
// rewritten, even when it is invalid: the mod then runs on the defaults and says
// so in the log, and the player's file stays as they left it.
public record SmartChestConfig(Capacity capacity, Compression compression) {
	public static final SmartChestConfig DEFAULTS = new SmartChestConfig(Capacity.DEFAULTS, Compression.DEFAULTS);

	private static final Codec<SmartChestConfig> READ_CODEC = codec(false);
	private static final Codec<SmartChestConfig> WRITE_CODEC = codec(true);

	private static Codec<SmartChestConfig> codec(boolean everyField) {
		return RecordCodecBuilder.create(
				i -> i.group(
								field(Capacity.codec(everyField), "capacity", Capacity.DEFAULTS, everyField)
										.forGetter(SmartChestConfig::capacity),
								field(Compression.codec(everyField), "compression", Compression.DEFAULTS, everyField)
										.forGetter(SmartChestConfig::compression))
						.apply(i, SmartChestConfig::new));
	}

	private static <T> MapCodec<T> field(Codec<T> codec, String name, T fallback, boolean everyField) {
		return everyField ? codec.fieldOf(name) : codec.optionalFieldOf(name, fallback);
	}

	// JSON has no comments, so the file carries its own explanation as a key the
	// reading codec ignores.
	private static final String[] COMMENT = {
		"Smart Chest -- configuration. Read at startup: restart the game to apply a change.",
		"Delete this file to regenerate it with the default values.",
		"If a value is invalid, the whole file is ignored, the defaults are used and",
		"the log says why. This file is never overwritten.",
		"",
		"capacity.tiers: the per-slot cap of the four capacity upgrades, in order.",
		"Four whole numbers, each larger than the one before, between 2 and 999.",
		"",
		"compression.enabled: false turns the compression upgrade off.",
	};

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static SmartChestConfig current = DEFAULTS;

	public static SmartChestConfig get() {
		return current;
	}

	/** Reads the file, or writes the defaults to it when it is missing. */
	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(SmartChestMod.MOD_ID + ".json");

		if (Files.exists(path)) {
			current = read(path);
		} else {
			current = DEFAULTS;
			write(path);
		}

		SmartChestMod.LOGGER.info(
				"Config {}: capacity tiers {}, compression enabled {}",
				path, current.capacity.tiers, current.compression.enabled);
	}

	private static SmartChestConfig read(Path path) {
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			DataResult<SmartChestConfig> result = READ_CODEC.parse(JsonOps.INSTANCE, StrictJsonParser.parse(reader));
			// result() rather than resultOrPartial(): a partial result would mix the
			// player's values with defaults the log does not name.
			result.error().ifPresent(error ->
					SmartChestMod.LOGGER.warn("Invalid config {}, using defaults: {}", path, error.message()));
			return result.result().orElse(DEFAULTS);
		} catch (IOException | JsonParseException e) {
			SmartChestMod.LOGGER.warn("Unreadable config {}, using defaults", path, e);
			return DEFAULTS;
		}
	}

	private static void write(Path path) {
		WRITE_CODEC.encodeStart(JsonOps.INSTANCE, DEFAULTS)
				.resultOrPartial(error -> SmartChestMod.LOGGER.warn("Could not encode config: {}", error))
				.ifPresent(encoded -> {
					JsonObject out = new JsonObject();
					out.add("_comment", GSON.toJsonTree(COMMENT));
					encoded.getAsJsonObject().entrySet().forEach(entry -> out.add(entry.getKey(), entry.getValue()));

					try {
						Files.createDirectories(path.getParent());
						Files.writeString(path, GSON.toJson(out));
					} catch (IOException e) {
						SmartChestMod.LOGGER.warn("Could not write config {}", path, e);
					}
				});
	}

	/** Per-slot cap of each capacity upgrade, lowest tier first (SPEC 4). */
	public record Capacity(List<Integer> tiers) {
		public static final Capacity DEFAULTS = new Capacity(List.of(128, 256, 512, 999));

		// 999 is the ceiling the design fixes (SPEC 4). It is not ItemStack.CODEC's:
		// that one stops at 99, which is why the chest saves its counts apart
		// (SPEC 4.1). 2 is the floor because a cap of 1 is no upgrade.
		// The size is checked here rather than by listOf(4, 4): that one drops an
		// out-of-range element before counting, and the log would then blame the
		// list length instead of naming the bad value.
		private static final Codec<List<Integer>> TIERS = Codec.intRange(2, 999)
				.listOf()
				.validate(tiers -> {
					if (tiers.size() != 4) {
						return DataResult.error(() -> "capacity tiers must be exactly 4 numbers: " + tiers);
					}
					for (int n = 1; n < tiers.size(); n++) {
						if (tiers.get(n) <= tiers.get(n - 1)) {
							return DataResult.error(() -> "capacity tiers must increase: " + tiers);
						}
					}
					return DataResult.success(tiers);
				});

		static Codec<Capacity> codec(boolean everyField) {
			return RecordCodecBuilder.create(
					i -> i.group(
									field(TIERS, "tiers", DEFAULTS.tiers, everyField)
											.forGetter(Capacity::tiers))
							.apply(i, Capacity::new));
		}
	}

	public record Compression(boolean enabled) {
		public static final Compression DEFAULTS = new Compression(true);

		static Codec<Compression> codec(boolean everyField) {
			return RecordCodecBuilder.create(
					i -> i.group(
									field(Codec.BOOL, "enabled", DEFAULTS.enabled, everyField)
											.forGetter(Compression::enabled))
							.apply(i, Compression::new));
		}
	}
}
