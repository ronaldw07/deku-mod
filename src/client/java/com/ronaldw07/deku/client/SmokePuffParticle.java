package com.ronaldw07.deku.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A big smoke puff that drifts out, slows to a stop, hangs, then fades; hot ones glow and darken as they cool. */
final class SmokePuffParticle extends SingleQuadParticle {
	/**
	 * How a kind of puff looks and how long it lingers. Rise is added to the upward speed each tick
	 * (negative falls); a heat-shifting puff runs white-hot to orange to red to black over its life.
	 */
	record Look(float red, float green, float blue, float scale, int minLifetime, float drag, float rise,
			float startAlpha, int fadeTicks, boolean heatShift) {
	}

	// Light lavender, so overlapping puffs build into a thick bright haze rather than a dark blot.
	static final Look SMOKESCREEN = new Look(0.7f, 0.52f, 0.92f, 6.0f, 160, 0.92f, 0f, 0.9f, 40, false);
	static final Look HOWITZER_CLOUD = new Look(0.95f, 0.95f, 0.97f, 4.0f, 25, 0.92f, 0f, 0.9f, 40, false);
	// Near-black soot that rises slowly and hangs over a blast for ten seconds or more.
	static final Look SOOT = new Look(0.07f, 0.06f, 0.06f, 5.0f, 200, 0.96f, 0.002f, 0.85f, 60, false);
	// The fireball itself: burns out in about a second.
	static final Look FIREBALL = new Look(1.0f, 0.95f, 0.7f, 4.0f, 18, 0.88f, 0.01f, 0.95f, 8, true);
	// Small dark flakes drifting down.
	static final Look ASH = new Look(0.18f, 0.17f, 0.17f, 0.5f, 100, 0.98f, -0.004f, 0.8f, 30, false);

	private static final int FULL_BRIGHT = 0xF000F0;
	private static final float SOOT_WARMTH = 0.05f;
	// Heat gradient stops, as a share of the particle's life, with the colour at each.
	private static final float[] HEAT_STOPS = {0f, 0.2f, 0.5f, 0.8f};
	private static final float[][] HEAT_COLORS = {
		{1.0f, 0.95f, 0.7f}, {1.0f, 0.45f, 0.1f}, {0.55f, 0.08f, 0.04f}, {0.08f, 0.07f, 0.07f},
	};
	private static final float GLOWS_UNTIL = 0.5f;
	// Soot thins out close to the player, so standing in a blast's smoke doesn't black out the screen.
	private static final double CLEAR_WITHIN = 3.0;
	private static final double OPAQUE_BEYOND = 12.0;
	private static final float CLEARED_ALPHA = 0.12f;

	private final Look look;

	SmokePuffParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za,
			TextureAtlasSprite sprite, Look look) {
		super(level, x, y, z, sprite);
		this.look = look;
		this.scale(look.scale());
		this.setSize(0.25f, 0.25f);
		this.lifetime = look.minLifetime() + this.random.nextInt(Math.max(1, look.minLifetime() / 3));
		this.friction = look.drag();
		this.gravity = 0;
		this.xd = xa;
		this.yd = ya;
		this.zd = za;
		float shade = 0.85f + this.random.nextFloat() * 0.15f;
		float warmth = look == SOOT ? this.random.nextFloat() * SOOT_WARMTH : 0f;
		this.setColor(Math.min(1f, look.red() * shade + warmth), look.green() * shade, look.blue() * shade);
		this.setAlpha(look.startAlpha());
	}

	/** Lets a caller keep a puff around longer than its kind usually lasts. */
	void setLifetimeTicks(int ticks) {
		this.lifetime = ticks;
	}

	@Override
	public void tick() {
		super.tick();
		this.yd += look.rise();
		if (look.heatShift()) {
			heat(Math.min(1f, (float) this.age / this.lifetime));
		}
		int remaining = this.lifetime - this.age;
		int fade = Math.min(look.fadeTicks(), this.lifetime / 2);
		float alpha = remaining < fade ? look.startAlpha() * remaining / fade : look.startAlpha();
		this.setAlpha(look == SOOT ? alpha * nearPlayerClearance() : alpha);
	}

	private float nearPlayerClearance() {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return 1f;
		}
		double distance = player.getEyePosition().distanceTo(new Vec3(this.x, this.y, this.z));
		return (float) Mth.clamp((distance - CLEAR_WITHIN) / (OPAQUE_BEYOND - CLEAR_WITHIN), CLEARED_ALPHA, 1.0);
	}

	private void heat(float life) {
		int stop = 0;
		while (stop < HEAT_STOPS.length - 2 && life > HEAT_STOPS[stop + 1]) {
			stop++;
		}
		float t = Mth.clamp((life - HEAT_STOPS[stop]) / (HEAT_STOPS[stop + 1] - HEAT_STOPS[stop]), 0f, 1f);
		float[] from = HEAT_COLORS[stop];
		float[] to = HEAT_COLORS[stop + 1];
		this.setColor(Mth.lerp(t, from[0], to[0]), Mth.lerp(t, from[1], to[1]), Mth.lerp(t, from[2], to[2]));
	}

	@Override
	protected int getLightCoords(float partialTick) {
		boolean glowing = look.heatShift() && (float) this.age / this.lifetime < GLOWS_UNTIL;
		return glowing ? FULL_BRIGHT : super.getLightCoords(partialTick);
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}
}
