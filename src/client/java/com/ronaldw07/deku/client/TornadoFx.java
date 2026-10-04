package com.ronaldw07.deku.client;

import com.ronaldw07.deku.network.TornadoFxPayload;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the United States of Smash tornado: a funnel of cloud spiralling upward, widening
 * toward the top, with dust churning around its foot. Shown from far away.
 */
final class TornadoFx {
	private static final double HEIGHT = 70.0;
	private static final double FUNNEL_BASE = 2.0;
	private static final double FUNNEL_FLARE = 0.3;
	private static final double RING_SPACING = 1.5; // blocks of height between rings of particles
	private static final int PER_RING = 3;
	private static final double TWIST = 0.15; // radians of turn per block of height
	private static final double SPIN_SPEED = 0.35; // radians per tick
	private static final double SWIRL_SPEED = 0.4;
	private static final double RISE_SPEED = 0.1;
	private static final int DUST_PER_TICK = 8;
	private static final double DUST_REACH = 10.0;
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
				for (int i = 0; i < PER_RING; i++) {
					if (random.nextDouble() > fade) {
						continue;
					}
					double angle = spin + height * TWIST + Math.PI * 2 * i / PER_RING + random.nextDouble() * 0.5;
					funnelPuff(level, tornado.base(), radius, height, angle);
				}
			}
			for (int i = 0; i < DUST_PER_TICK * fade; i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double distance = FUNNEL_BASE + random.nextDouble() * DUST_REACH;
				Vec3 at = tornado.base().add(Math.cos(angle) * distance, random.nextDouble(), Math.sin(angle) * distance);
				Vec3 around = new Vec3(-Math.sin(angle), 0.05, Math.cos(angle)).scale(SWIRL_SPEED);
				level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, at.x, at.y, at.z, around.x, around.y, around.z);
			}
		}
	}

	private static void funnelPuff(ClientLevel level, Vec3 base, double radius, double height, double angle) {
		Vec3 at = base.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
		Vec3 around = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(SWIRL_SPEED).add(0, RISE_SPEED, 0);
		level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, at.x, at.y, at.z, around.x, around.y, around.z);
	}
}
