package re.jerome.smartchest.client;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.special.ChestSpecialRenderer;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;

// The special model of the chest item, "smartchest:chest" in items/*.json: the
// vanilla "minecraft:chest" bakes ChestRenderer's own layer, so its latch would
// not be ours. Same renderer, closed, with the single chest of SmartChestLayers.
public record SmartChestItemRenderer(Identifier texture) implements NoDataSpecialModelRenderer.Unbaked {
	public static final MapCodec<SmartChestItemRenderer> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
			.group(Identifier.CODEC.fieldOf("texture").forGetter(SmartChestItemRenderer::texture))
			.apply(instance, SmartChestItemRenderer::new));

	@Override
	public MapCodec<SmartChestItemRenderer> type() {
		return MAP_CODEC;
	}

	@Override
	public ChestSpecialRenderer bake(SpecialModelRenderer.BakingContext context) {
		ChestModel model = new ChestModel(context.entityModelSet().bakeLayer(SmartChestLayers.ALL.single()));
		return new ChestSpecialRenderer(context.sprites(), model, Sheets.CHEST_MAPPER.apply(texture), 0.0F);
	}
}
