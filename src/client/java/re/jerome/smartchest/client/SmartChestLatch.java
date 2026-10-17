package re.jerome.smartchest.client;

// Written by tools/GenerateWoods.java from tools/latch_front.png: do not edit.
// The boxes of the latch on a single chest, in half units: x from the centre
// of the chest, y in the block, z from Z0 to their front z1. Their texture
// offset is in units.
final class SmartChestLatch {
	record Box(int x0, int x1, int y0, int y1, int z1, int u, int v) {
	}

	static final int Z0 = 30;
	static final Box[] BOXES = {
			new Box(-1, 1, 20, 22, 32, 0, 44),
			new Box(-2, 2, 19, 20, 32, 4, 44),
			new Box(-3, -1, 17, 19, 32, 10, 44),
			new Box(-1, 1, 17, 19, 31, 14, 44),
			new Box(1, 3, 17, 19, 32, 17, 44),
			new Box(-2, 2, 16, 17, 32, 21, 44),
			new Box(-1, 1, 14, 16, 32, 27, 44),
	};

	private SmartChestLatch() {
	}
}
