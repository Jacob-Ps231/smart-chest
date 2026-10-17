package re.jerome.smartchest.mixin;

import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.animal.golem.CopperGolemAi;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.jerome.smartchest.SmartChestMod;

/**
 * Lets copper golems drop items into the blocks of {@code #smartchest:golem_destinations}. The
 * golem only searches {@code ChestBlockEntity} instances, so a tagged block without one is ignored.
 *
 * <p>Vanilla only accepts {@code Blocks.CHEST} and {@code Blocks.TRAPPED_CHEST}, compared by
 * identity in a private static predicate. The predicate is wrapped once, at the end of the static
 * initializer: the vanilla test still runs first and unchanged, and the only anchor is the field
 * name. The field is read by {@code initIdleActivity}, which builds the transport behaviour for
 * each golem brain, so always after this class is initialized. Rewriting the value rather than
 * hooking that method also covers any later reader, and another mod doing the same just chains
 * one more {@code or}.
 */
@Mixin(CopperGolemAi.class)
abstract class CopperGolemAiMixin {
	@Shadow
	@Final
	@Mutable
	private static Predicate<BlockState> TRANSPORT_ITEM_DESTINATION_BLOCK;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void smartchest$addDestinationTag(CallbackInfo ci) {
		TagKey<Block> destinations = TagKey.create(Registries.BLOCK, SmartChestMod.id("golem_destinations"));
		TRANSPORT_ITEM_DESTINATION_BLOCK = TRANSPORT_ITEM_DESTINATION_BLOCK.or(state -> state.is(destinations));
	}
}
