package com.ronaldw07.deku.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** A big smoke puff that drifts out, slows to a stop, hangs, then fades. */
final class SmokePuffParticle extends SingleQuadParticle {
	/** How a kind of puff looks and how long it lingers. */
	record Look(float red, float green, float blue, float scale, int minLifetime) {
	}

	// Light lavender, so overlapping puffs build into a thick bright haze rather than a dark blot.
	static final Look SMOKESCREEN = new Look(0.7f, 0.52f, 0.92f, 6.0f, 160);
	static final Look HOWITZER_CLOUD = new Look(0.95f, 0.95f, 0.97f, 4.0f, 25);

	private static final int FADE_TICKS = 40;
	// Strong drag so a burst spreads out a few blocks, then stops.
	private static final float DRAG = 0.92f;
	private static final float START_ALPHA = 0.9f;

	SmokePuffParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za,
			TextureAtlasSprite sprite, Look look) {
		super(level, x, y, z, sprite);
		this.scale(look.scale());
		this.setSize(0.25f, 0.25f);
		this.lifetime = look.minLifetime() + this.random.nextInt(Math.max(1, look.minLifetime() / 3));
		this.friction = DRAG;
		this.gravity = 0;
		this.xd = xa;
		this.yd = ya;
		this.zd = za;
		float shade = 0.85f + this.random.nextFloat() * 0.15f;
		this.setColor(look.red() * shade, look.green() * shade, look.blue() * shade);
		this.setAlpha(START_ALPHA);
	}

	@Override
	public void tick() {
		super.tick();
		int remaining = this.lifetime - this.age;
		int fade = Math.min(FADE_TICKS, this.lifetime / 2);
		if (remaining < fade) {
			this.setAlpha(START_ALPHA * remaining / fade);
		}
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}
}
