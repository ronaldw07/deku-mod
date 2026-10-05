package com.ronaldw07.deku.client;

import java.lang.reflect.Method;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Picks how solid effects (flames, arms, slashes, orbs) are drawn. Plain Minecraft shows them
 * best as unlit flat faces with no distance fog; a shader pack ignores those, so with one on
 * they are drawn as lit entity surfaces that the pack renders like any mob.
 */
final class SolidRender {
	private static final int CHECK_EVERY_FRAMES = 60;

	private static Object iris;
	private static Method inUse;
	private static boolean looked;
	private static boolean shaders;
	private static int frames;

	private SolidRender() {
	}

	static RenderType type() {
		return shadersOn() ? RenderTypes.entityTranslucentEmissive(FireballChargeFx.WHITE) : RenderTypes.debugQuads();
	}

	/** For black and dark effects: a shader pack would light or glow an emissive surface, so these use a plain lit one. */
	static RenderType darkType() {
		return shadersOn() ? RenderTypes.entityTranslucent(FireballChargeFx.WHITE) : RenderTypes.debugQuads();
	}

	private static boolean shadersOn() {
		if (!looked) {
			looked = true;
			try {
				Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				iris = api.getMethod("getInstance").invoke(null);
				inUse = api.getMethod("isShaderPackInUse");
			} catch (ReflectiveOperationException | LinkageError missing) {
				iris = null;
			}
		}
		if (iris != null && frames++ % CHECK_EVERY_FRAMES == 0) {
			try {
				shaders = (boolean) inUse.invoke(iris);
			} catch (ReflectiveOperationException broken) {
				shaders = false;
			}
		}
		return shaders;
	}
}
