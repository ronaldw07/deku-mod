package com.ronaldw07.deku.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ronaldw07.deku.client.LightningDraw.Segment;
import org.joml.Matrix4fc;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import com.ronaldw07.deku.network.FireballChargeFxPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Cluster Bomb's fireball while it's being charged: a glowing red sphere floating in front of
 * the player, crackling with red rays and shedding embers, bigger and angrier the longer X is held.
 */
public final class FireballChargeFx {
	private static final double BALL_DISTANCE = 2.0;
	private static final double BALL_DISTANCE_PER_RADIUS = 1.3; // a bigger ball floats farther out, clear of the camera
	private static final double BALL_DROP = 0.4;
	private static final double MIN_RADIUS = 0.3;
	private static final double MAX_RADIUS = 3.9;
	// A halo of red light shaking around the player as the charge builds.
	private static final double HALO_STARTS_AT = 0.25;
	private static final double MIN_HALO_RADIUS = 1.5;
	private static final double EXTRA_HALO_RADIUS = 2.0;
	private static final int HALO_RAYS = 14;
	private static final double HALO_RAY_LENGTH = 1.5;
	private static final float MAX_HALO_RUMBLE = 0.35f;
	private static final float MAX_HALO_GLOW = 0.18f;
	private static final int SPHERE_SEGMENTS = 16;
	private static final int SOLID_LATITUDES = 12;
	private static final int SOLID_LONGITUDES = 20;
	private static final int SMOOTH_GREAT_CIRCLES = 6;
	private static final int SMOOTH_LATITUDES = 6;
	private static final int MIN_RAYS = 8;
	private static final int EXTRA_RAYS = 12;
	private static final double MIN_RAY_LENGTH = 1.2;
	private static final double EXTRA_RAY_LENGTH = 1.0;
	private static final int RAY_STEPS = 4;
	private static final double RAY_JAG = 0.15;
	private static final int TICKS_PER_SHAPE = 2;
	private static final double PULSE_SPEED = 0.5;
	private static final double PULSE_DEPTH = 0.1;
	private static final int MIN_EMBERS = 3;
	private static final int EXTRA_EMBERS = 6;
	private static final DustParticleOptions EMBER = new DustParticleOptions(0xFF1000, 1.6f);

	private static final int REMOTE_EXPIRY_TICKS = 40; // a charge nobody has heard about for this long is dropped

	private record Remote(int charge, long at) {
	}

	private static Map<UUID, Remote> others = Map.of();

	private FireballChargeFx() {
	}

	/** Someone nearby is growing, or let go of, their fireball. */
	static void add(FireballChargeFxPayload fx) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		Map<UUID, Remote> next = new HashMap<>(others);
		if (fx.charge() > 0) {
			next.put(fx.player(), new Remote(fx.charge(), level.getGameTime()));
		} else {
			next.remove(fx.player());
		}
		others = Map.copyOf(next);
	}

	/** How far someone else's fireball is grown, as far as this client has been told; 0 if none. */
	public static int remoteCharge(UUID player) {
		Remote remote = others.get(player);
		return remote == null ? 0 : remote.charge();
	}

	static void reset() {
		others = Map.of();
	}

	private static Vec3 ballCenter(Player player, float partialTick, double radius) {
		double distance = BALL_DISTANCE + radius * BALL_DISTANCE_PER_RADIUS;
		return player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(distance)).add(0, -BALL_DROP, 0);
	}

	/** The ball's size at a charge of 0-1 (also the size of a thrown ball for a blast that big). */
	static double radius(double power, double time) {
		return Mth.lerp(power, MIN_RADIUS, MAX_RADIUS) * (1 + PULSE_DEPTH * Math.sin(time * PULSE_SPEED));
	}

	/** Embers shedding off the ball's surface. */
	static void tick(LocalPlayer player) {
		if (player == null) {
			reset();
			return;
		}
		long now = player.level().getGameTime();
		others = others.entrySet().stream().filter(entry -> now - entry.getValue().at() < REMOTE_EXPIRY_TICKS)
			.collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
		for (Map.Entry<UUID, Remote> entry : others.entrySet()) {
			Player other = player.level().getPlayerByUUID(entry.getKey());
			if (other != null && other != player) {
				embers(other, entry.getValue().charge());
			}
		}
		int charge = ExplosionClient.fireballCharge();
		if (charge == 0) {
			return;
		}
		double power = charge / 100.0;
		ScreenShake.rumble(MAX_HALO_RUMBLE * (float) (power * power));
		ScreenShake.glow(MAX_HALO_GLOW * (float) (power * power));
		embers(player, charge);
	}

	private static void embers(Player player, int charge) {
		double power = charge / 100.0;
		double radius = radius(power, player.tickCount);
		Vec3 center = ballCenter(player, 1f, radius);
		RandomSource random = player.getRandom();
		int count = Math.max(1, (int) Math.round((MIN_EMBERS + EXTRA_EMBERS * power) * DekuSettings.get().detailScale()));
		for (int i = 0; i < count; i++) {
			Vec3 at = center.add(LightningDraw.randomDirection(random).scale(radius));
			player.level().addParticle(i % 3 == 0 ? ParticleTypes.FLAME : EMBER, at.x, at.y, at.z, 0, 0.03, 0);
		}
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		if (ExplosionClient.fireballCharge() > 0) {
			draw(context, player, ExplosionClient.fireballCharge(), partialTick);
		}
		for (Map.Entry<UUID, Remote> entry : others.entrySet()) {
			Player other = minecraft.level.getPlayerByUUID(entry.getKey());
			if (other != null && other != player) {
				draw(context, other, entry.getValue().charge(), partialTick);
			}
		}
	}

	private static void draw(LevelRenderContext context, Player player, int charge, float partialTick) {
		double power = charge / 100.0;
		double radius = radius(power, player.tickCount + partialTick);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		RandomSource random = RandomSource.create(player.getId() * 31L + player.tickCount / TICKS_PER_SHAPE);

		List<Segment> sphere = new ArrayList<>();
		List<Segment> rays = new ArrayList<>();
		addBall(sphere, rays, random, ballCenter(player, partialTick, radius).subtract(camera), radius, MIN_RAYS + EXTRA_RAYS * power);
		if (power >= HALO_STARTS_AT) {
			double halo = Mth.lerp(power * power, MIN_HALO_RADIUS, MIN_HALO_RADIUS + EXTRA_HALO_RADIUS);
			Vec3 chest = player.getPosition(partialTick).add(0, player.getBbHeight() * 0.55, 0).subtract(camera);
			addBall(sphere, rays, random, chest, halo, HALO_RAYS * power);
		}
		float width = (float) (1 + 4 * power);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
			LightningDraw.draw(pose.pose(), buffer, sphere, width * 2, LightningDraw.CRIMSON, 0.6f);
			LightningDraw.draw(pose.pose(), buffer, rays, width, LightningDraw.CRIMSON, 1f);
		});
	}

	/** Draws a glowing sphere as three crossed rings with rays leaping off it, into the two lists. */
	static void addBall(List<Segment> sphere, List<Segment> rays, RandomSource random, Vec3 center, double radius, double rayCount) {
		sphere.addAll(ring(center, radius, 0));
		sphere.addAll(ring(center, radius, 1));
		sphere.addAll(ring(center, radius, 2));
		for (int i = 0; i < rayCount; i++) {
			Vec3 out = LightningDraw.randomDirection(random);
			double length = radius * (MIN_RAY_LENGTH + random.nextDouble() * EXTRA_RAY_LENGTH);
			rays.addAll(LimbLightning.jagged(random, center.add(out.scale(radius * 0.6)), center.add(out.scale(length)), RAY_STEPS, RAY_JAG));
		}
	}

	/** A solid ball: a mesh of flat-coloured faces, lit a little brighter on its top so it reads as round. */
	static void drawSolidSphere(Matrix4fc pose, VertexConsumer buffer, Vec3 center, double radius, float red, float green, float blue, float alpha) {
		for (int i = 0; i < SOLID_LATITUDES; i++) {
			double polar0 = Math.PI * i / SOLID_LATITUDES;
			double polar1 = Math.PI * (i + 1) / SOLID_LATITUDES;
			float light = (float) (0.75 + 0.25 * Math.cos((polar0 + polar1) / 2));
			for (int j = 0; j < SOLID_LONGITUDES; j++) {
				double around0 = Math.PI * 2 * j / SOLID_LONGITUDES;
				double around1 = Math.PI * 2 * (j + 1) / SOLID_LONGITUDES;
				LightningDraw.drawFlatQuad(pose, buffer, sphere(center, radius, polar0, around0), sphere(center, radius, polar0, around1),
					sphere(center, radius, polar1, around1), sphere(center, radius, polar1, around0), red * light, green * light, blue * light, alpha);
			}
		}
	}

	private static Vec3 sphere(Vec3 center, double radius, double polar, double around) {
		return center.add(radius * Math.sin(polar) * Math.cos(around), radius * Math.cos(polar), radius * Math.sin(polar) * Math.sin(around));
	}

	/** A glossy sphere drawn as many tilted great circles and latitude rings, with no loose rays, for a ball of solid light. */
	static void addSmoothBall(List<Segment> sphere, Vec3 center, double radius) {
		for (int i = 0; i < SMOOTH_GREAT_CIRCLES; i++) {
			double tilt = Math.PI * i / SMOOTH_GREAT_CIRCLES;
			for (int j = 0; j < SPHERE_SEGMENTS * 2; j++) {
				sphere.add(new Segment(tiltedPoint(center, radius, tilt, Math.PI * j / SPHERE_SEGMENTS),
					tiltedPoint(center, radius, tilt, Math.PI * (j + 1) / SPHERE_SEGMENTS)));
			}
		}
		for (int lat = 1; lat < SMOOTH_LATITUDES; lat++) {
			double polar = Math.PI * lat / SMOOTH_LATITUDES;
			double ringRadius = radius * Math.sin(polar);
			double y = radius * Math.cos(polar);
			for (int j = 0; j < SPHERE_SEGMENTS * 2; j++) {
				double a = Math.PI * j / SPHERE_SEGMENTS;
				double b = Math.PI * (j + 1) / SPHERE_SEGMENTS;
				sphere.add(new Segment(center.add(Math.cos(a) * ringRadius, y, Math.sin(a) * ringRadius),
					center.add(Math.cos(b) * ringRadius, y, Math.sin(b) * ringRadius)));
			}
		}
	}

	private static Vec3 tiltedPoint(Vec3 center, double radius, double tilt, double angle) {
		double a = Math.cos(angle) * radius;
		double b = Math.sin(angle) * radius;
		return center.add(a, b * Math.cos(tilt), b * Math.sin(tilt));
	}

	/** A circle around the ball in one of its three planes. */
	private static List<Segment> ring(Vec3 center, double radius, int plane) {
		List<Segment> segments = new ArrayList<>();
		for (int i = 0; i < SPHERE_SEGMENTS; i++) {
			segments.add(new Segment(onRing(center, radius, plane, i), onRing(center, radius, plane, i + 1)));
		}
		return segments;
	}

	private static Vec3 onRing(Vec3 center, double radius, int plane, int index) {
		double angle = Math.PI * 2 * index / SPHERE_SEGMENTS;
		double a = Math.cos(angle) * radius;
		double b = Math.sin(angle) * radius;
		return switch (plane) {
			case 0 -> center.add(a, b, 0);
			case 1 -> center.add(0, a, b);
			default -> center.add(a, 0, b);
		};
	}
}
