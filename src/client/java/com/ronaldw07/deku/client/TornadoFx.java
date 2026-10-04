package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuParticles;
import com.ronaldw07.deku.network.TornadoFxPayload;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the United States of Smash tornado: a thick funnel of cloud whipping around and up,
 * widening toward the top, with rings of wind tearing around the ground and dust churning
 * at its foot. Shown from far away.
 */
final class TornadoFx {
	private static final double HEIGHT = 150.0;
	private static final double FUNNEL_BASE = 15.0;
	private static final double FUNNEL_FLARE = 0.4;
	private static final double RING_SPACING = 3.0; // blocks of height between rings of particles
	private static final int MIN_PER_RING = 4;
	private static final double BLOCKS_PER_PUFF = 16.0; // wider rings get more puffs, so the funnel stays solid
	private static final double WALL_THICKNESS = 4.0; // puffs scatter this far in and out of the funnel wall
	private static final double TWIST = 0.12; // radians of turn per block of height
	private static final double SPIN_SPEED = 0.6; // radians per tick
	private static final double SWIRL_SPEED = 1.2;
	private static final double RISE_SPEED = 0.25;
	private static final int DUST_PER_TICK = 30;
	private static final double DUST_REACH = 40.0;
	// Wind rings: circles of gusts tearing around the ground outside the funnel.
	private static final double[] WIND_RING_RADII = {22, 36, 50, 66};
	private static final int WIND_RING_POINTS = 24;
	private static final double WIND_SPEED = 1.6;
	private static final int GUSTS_PER_TICK = 2;
	private static final int FADE_TICKS = 40; // thins out over its last two seconds

	private record Tornado(Vec3 base, long startTick, long endTick) {
	}

	private static List<Tornado> tornadoes = List.of();

	private TornadoFx() {
	}

	static void add(TornadoFxPayload fx) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		long now = level.getGameTime();
		tornadoes = Stream.concat(tornadoes.stream(), Stream.of(new Tornado(fx.base(), now, now + fx.ticks()))).toList();
	}

	static void tick(ClientLevel level) {
		if (level == null) {
			tornadoes = List.of();
			return;
		}
		if (tornadoes.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		tornadoes = tornadoes.stream().filter(tornado -> now < tornado.endTick()).toList();
		RandomSource random = level.getRandom();
		for (Tornado tornado : tornadoes) {
			double fade = Math.min(1.0, (tornado.endTick() - now) / (double) FADE_TICKS);
			double spin = (now - tornado.startTick()) * SPIN_SPEED;
			for (double height = 0; height < HEIGHT; height += RING_SPACING) {
				double radius = FUNNEL_BASE + height * FUNNEL_FLARE;
				int perRing = MIN_PER_RING + (int) (Math.PI * 2 * radius / BLOCKS_PER_PUFF);
				for (int i = 0; i < perRing; i++) {
					if (random.nextDouble() > fade) {
						continue;
					}
					double angle = spin + height * TWIST + Math.PI * 2 * i / perRing + random.nextDouble() * 0.5;
					double wall = radius + (random.nextDouble() - 0.5) * WALL_THICKNESS;
					funnelPuff(level, tornado.base(), wall, height + random.nextDouble() * RING_SPACING, angle, random.nextBoolean());
				}
			}
			windRings(level, tornado.base(), spin, fade, random);
			for (int i = 0; i < DUST_PER_TICK * fade; i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double distance = FUNNEL_BASE + random.nextDouble() * DUST_REACH;
				Vec3 at = tornado.base().add(Math.cos(angle) * distance, random.nextDouble(), Math.sin(angle) * distance);
				Vec3 around = new Vec3(-Math.sin(angle), 0.05, Math.cos(angle)).scale(SWIRL_SPEED);
				level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, at.x, at.y, at.z, around.x, around.y, around.z);
			}
		}
	}

	private static void funnelPuff(ClientLevel level, Vec3 base, double radius, double height, double angle, boolean thick) {
		Vec3 at = base.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
		Vec3 around = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(SWIRL_SPEED).add(0, RISE_SPEED, 0);
		level.addAlwaysVisibleParticle(thick ? DekuParticles.WHITE_SMOKE : ParticleTypes.CLOUD, true, at.x, at.y, at.z,
			around.x, around.y, around.z);
	}

	/** Circles of wind racing around the ground, the inner ones fastest, with gusts bursting along them. */
	private static void windRings(ClientLevel level, Vec3 base, double spin, double fade, RandomSource random) {
		for (int ring = 0; ring < WIND_RING_RADII.length; ring++) {
			double radius = WIND_RING_RADII[ring];
			double speed = WIND_SPEED * (1 - ring * 0.15);
			for (int i = 0; i < WIND_RING_POINTS; i++) {
				if (random.nextDouble() > fade) {
					continue;
				}
				double angle = spin * (1 - ring * 0.15) + Math.PI * 2 * i / WIND_RING_POINTS + random.nextDouble() * 0.3;
				Vec3 at = base.add(Math.cos(angle) * radius, 1 + random.nextDouble() * 4, Math.sin(angle) * radius);
				Vec3 around = new Vec3(-Math.sin(angle), 0.02, Math.cos(angle)).scale(speed);
				level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, at.x, at.y, at.z, around.x, around.y, around.z);
			}
		}
		for (int i = 0; i < GUSTS_PER_TICK * fade; i++) {
			double radius = WIND_RING_RADII[random.nextInt(WIND_RING_RADII.length)];
			double angle = random.nextDouble() * Math.PI * 2;
			Vec3 at = base.add(Math.cos(angle) * radius, 1 + random.nextDouble() * 3, Math.sin(angle) * radius);
			level.addAlwaysVisibleParticle(ParticleTypes.GUST, true, at.x, at.y, at.z, 0, 0, 0);
		}
	}
}
