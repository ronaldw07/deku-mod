package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

/** The villain: a zombie's build in its own dark armor and skull face. */
final class VillainRenderer extends ZombieRenderer {
	private static final Identifier TEXTURE = DekuMod.id("textures/entity/villain.png");

	VillainRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public Identifier getTextureLocation(ZombieRenderState state) {
		return TEXTURE;
	}
}
