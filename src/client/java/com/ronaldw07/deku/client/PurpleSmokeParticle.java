package com.ronaldw07.deku.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** A big purple smoke puff that drifts out, slows to a stop, hangs, then fades. */
final class PurpleSmokeParticle extends SingleQuadParticle {
	private static final float SCALE = 6.0f;
	private static final int MIN_LIFETIME = 160;
	private static final int EXTRA_LIFETIME = 60;
	private static final int FADE_TICKS = 40;
	// Strong drag so the opening burst spreads out about six blocks, then stops.
	private static final float DRAG = 0.92f;
	private static final float START_ALPHA = 0.9f;

	PurpleSmokeParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, TextureAtlasSprite sprite) {
		super(level, x, y, z, sprite);
		this.scale(SCALE);
		this.setSize(0.25f, 0.25f);
		this.lifetime = MIN_LIFETIME + this.random.nextInt(EXTRA_LIFETIME);
		this.friction = DRAG;
		this.gravity = 0;
		this.xd = xa;
		this.yd = ya;
		this.zd = za;
		float shade = 0.85f + this.random.nextFloat() * 0.15f;
		// Light lavender, so overlapping puffs build into a thick bright haze rather than a dark blot.
		this.setColor(0.7f * shade, 0.52f * shade, 0.92f * shade);
		this.setAlpha(START_ALPHA);
	}

	@Override
	public void tick() {
		super.tick();
		int remaining = this.lifetime - this.age;
		if (remaining < FADE_TICKS) {
			this.setAlpha(START_ALPHA * remaining / FADE_TICKS);
		}
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}
}
