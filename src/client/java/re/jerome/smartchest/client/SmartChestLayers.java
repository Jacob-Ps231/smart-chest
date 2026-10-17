package re.jerome.smartchest.client;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiblockChestResources;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.properties.ChestType;
import re.jerome.smartchest.SmartChestMod;

// The vanilla chest model (ChestModel.create*BodyLayer) with another latch: the
// boxes of SmartChestLatch, which tools/GenerateWoods.java makes from Jérôme's
// drawing, in the "lock" part, which ChestModel turns with the lid. On a double
// chest each half carries its half of the latch, centred on the seam as the
// vanilla latch is.
public final class SmartChestLayers {
	/** Named after the vanilla layers (ModelLayers.CHEST, DOUBLE_CHEST_LEFT, DOUBLE_CHEST_RIGHT). */
	public static final MultiblockChestResources<ModelLayerLocation> ALL = new MultiblockChestResources<>(
			new ModelLayerLocation(SmartChestMod.id("chest"), "main"),
			new ModelLayerLocation(SmartChestMod.id("double_chest_left"), "main"),
			new ModelLayerLocation(SmartChestMod.id("double_chest_right"), "main"));

	private SmartChestLayers() {
	}

	public static LayerDefinition create(ChestType type) {
		// A half meets the other on its west (LEFT) or east (RIGHT), and has no face there.
		int x = type == ChestType.LEFT ? 0 : 1;
		int width = type == ChestType.SINGLE ? 14 : 15;
		Set<Direction> faces = switch (type) {
			case SINGLE -> EnumSet.allOf(Direction.class);
			case LEFT -> Util.allOfEnumExcept(Direction.WEST);
			case RIGHT -> Util.allOfEnumExcept(Direction.EAST);
		};
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(0, 19).addBox(x, 0, 1, width, 10, 14, faces),
				PartPose.ZERO);
		root.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 0).addBox(x, 0, 0, width, 5, 14, faces),
				PartPose.offset(0, 9, 1));

		// The centre of the latch, in half units: the middle of a single chest, the seam of a double.
		int centre = switch (type) {
			case SINGLE -> 16;
			case LEFT -> 0;
			case RIGHT -> 32;
		};
		CubeListBuilder latch = CubeListBuilder.create();
		for (SmartChestLatch.Box box : SmartChestLatch.BOXES) {
			int x0 = type == ChestType.LEFT ? Math.max(box.x0(), 0) : box.x0();
			int x1 = type == ChestType.RIGHT ? Math.min(box.x1(), 0) : box.x1();
			if (x0 >= x1) {
				continue;
			}
			// A box across the seam has no face there; one that only ends on it keeps its own.
			boolean onSeam = type != ChestType.SINGLE && box.x0() < 0 && box.x1() > 0;
			// In the lid's frame, which is offset by (0, 9, 1), and in units.
			latch.texOffs(box.u(), box.v()).addBox((centre + x0) / 2.0F, box.y0() / 2.0F - 9,
					SmartChestLatch.Z0 / 2.0F - 1, (x1 - x0) / 2.0F, (box.y1() - box.y0()) / 2.0F,
					(box.z1() - SmartChestLatch.Z0) / 2.0F, onSeam ? faces : EnumSet.allOf(Direction.class));
		}
		root.addOrReplaceChild("lock", latch, PartPose.offset(0, 9, 1));
		return LayerDefinition.create(mesh, 64, 64);
	}
}
