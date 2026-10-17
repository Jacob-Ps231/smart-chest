package re.jerome.smartchest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.MultiblockChestResources;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.world.phys.Vec3;
import re.jerome.smartchest.SmartChestBlock;
import re.jerome.smartchest.SmartChestBlockEntity;
import re.jerome.smartchest.SmartChestMod;

// ChestRenderer picks its texture from a closed enum (Sheets.chooseSprite), so
// this keeps its state extraction -- type, facing, lid, light of a double chest --
// and redoes only submit, with the textures of the chest's wood.
public class SmartChestRenderer extends ChestRenderer<SmartChestBlockEntity> {
	private final SpriteGetter sprites;
	private final MultiblockChestResources<ChestModel> models;
	private final Map<String, MultiblockChestResources<SpriteId>> textures = new HashMap<>();

	public SmartChestRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
		// Same as ChestRenderer's own fields, which are private.
		sprites = context.sprites();
		models = LAYERS.map(layer -> new ChestModel(context.bakeLayer(layer)));
	}

	@Override
	public ChestRenderState createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SmartChestBlockEntity chest, ChestRenderState state, float partialTicks,
			Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		super.extractRenderState(chest, state, partialTicks, cameraPosition, breakProgress);
		String wood = ((SmartChestBlock) chest.getBlockState().getBlock()).wood();
		((State) state).textures = textures.computeIfAbsent(wood, w -> new MultiblockChestResources<>(
				texture(w), texture(w + "_left"), texture(w + "_right")));
	}

	// textures/entity/chest/<name>.png, in the chest atlas like the vanilla ones.
	private static SpriteId texture(String name) {
		return Sheets.CHEST_MAPPER.apply(SmartChestMod.id(name));
	}

	@Override
	public void submit(ChestRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		// From ChestRenderer.submit, only the sprite differs.
		poseStack.pushPose();
		poseStack.mulPose(modelTransformation(state.facing));
		float open = 1.0F - state.open;
		open = 1.0F - open * open * open;
		SpriteId sprite = ((State) state).textures.select(state.type);
		ChestModel model = models.select(state.type);
		collector.submitModel(model, open, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
				sprite, sprites, 0);
		if (state.breakProgress != null) {
			collector.order(1).submitCrumblingOverlay(model, open, poseStack, sprite.renderType(model.renderType()),
					state.lightCoords, OverlayTexture.NO_OVERLAY, -1, state.breakProgress);
		}
		poseStack.popPose();
	}

	private static class State extends ChestRenderState {
		MultiblockChestResources<SpriteId> textures;
	}
}
