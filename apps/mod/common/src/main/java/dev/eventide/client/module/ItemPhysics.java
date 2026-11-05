package dev.eventide.client.module;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Item Physics: dropped items stop bobbing and spinning once they land and lie flat on the ground, each turned
 * its own way; while they fall they tumble as usual.
 */
public final class ItemPhysics {
	private static final Set<ItemClusterRenderState> RESTING = Collections.newSetFromMap(new WeakHashMap<>());

	private ItemPhysics() {
	}

	/**
	 * A landed item's bob is held at its lowest and its spin at a turn of its own (both read from its age),
	 * and it's marked to be laid flat.
	 */
	public static void extract(ItemEntity entity, ItemEntityRenderState state) {
		if (!Modules.ITEM_PHYSICS.enabled() || !entity.onGround()) {
			RESTING.remove(state);
			return;
		}
		state.ageInTicks = 10f * (-(float) Math.PI / 2 - state.bobOffset);
		RESTING.add(state);
	}

	/** Whether a dropped item is lying on the ground. */
	public static boolean resting(ItemClusterRenderState state) {
		return RESTING.contains(state);
	}
}
