package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuParticles;
import com.ronaldw07.deku.DekuSounds;
import com.ronaldw07.deku.client.ExplosionFx.Blast;
import com.ronaldw07.deku.network.ExplosionFxPayload.Style;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The particles of an Explosion blast, played out over time: a white-hot flash, a fireball that
 * goes orange, red, then black, soot rolling up from it, secondary pops, burning chunks, a dust
 * ring racing across the floor, and a mushroom column on the big ones. Howitzer Impact's core adds
 * a huge column and falling ash. Every count is scaled by the particle detail setting and held
 * to a per-tick budget so a barrage of bombs can't flood the game.
 */
final class ExplosionSmoke {
	private static final int MAX_PARTICLES_PER_TICK = 900;
	private static final int FIRE_TICKS = 6;
	private static final int SMOKE_FIRST_TICK = 3;
	private static final int SMOKE_LAST_TICK = 25;
	private static final int CORE_FIRE_TICKS = 10;
	private static final double CORE_FIRE_COUNT_SCALE = 2.0;

	// Fireball and soot size and count per tick, relative to the blast's radius.
	private static final int MAX_FIREBALLS_PER_TICK = 20;
	private static final double FIREBALLS_PER_RADIUS = 1.5;
	private static final int MAX_SOOT_PER_TICK = 14;
	private static final double SOOT_PER_RADIUS = 0.8;
	private static final float MIN_FIREBALL_SCALE = 1f;
	private static final float MAX_FIREBALL_SCALE = 9f;
	private static final float MAX_SOOT_SCALE = 14f;
	private static final int FLASH_PUFFS = 6;
	private static final int ICE_SHARDS = 150;
	private static final int PURPLE_DUST = 260;
	private static final int PURPLE_PORTAL = 120;
	private static final DustParticleOptions PURPLE_BRIGHT = new DustParticleOptions(0xD080FF, 4.0f);
	private static final DustParticleOptions PURPLE_DEEP = new DustParticleOptions(0x6000C0, 4.0f);
	private static final int FROST_PUFFS = 60;
	private static final double FIREBALL_SPREAD = 0.05;
	private static final int MAX_FLAMES = 160;
	private static final int MAX_EXPLOSION_PUFFS = 12;
	private static final double EXPLOSION_PUFFS_PER_RADIUS = 2.0;

	private static final int NUKE_EMBERS = 60;
	private static final double NUKE_EMBERS_PER_RADIUS = 4.0;
	private static final DustParticleOptions NUKE_RED = new DustParticleOptions(0xFF0000, 4.0f);
	private static final DustParticleOptions NUKE_DARK_RED = new DustParticleOptions(0xC00000, 3.5f);

	// Secondary pops: small blasts going off around the main one a few ticks later.
	private static final double MIN_POP_RADIUS = 5.0;
	private static final int FIRST_POP_TICK = 2;
	private static final int POP_TICK_SPREAD = 8;
	private static final double POP_RADIUS_SHARE = 0.35;
	private static final double POP_SPREAD = 1.1;
	private static final int MAX_POP_FIREBALLS = 8;
	private static final int POP_SOOT = 3;
	private static final float POP_VOLUME = 0.5f;

	// Burning chunks flung out of the fireball, trailing fire and black smoke.
	private static final double MIN_CHUNK_RADIUS = 5.0;
	private static final int MAX_CHUNKS_PER_BLAST = 12;
	private static final int MAX_LIVE_CHUNKS = 80;
	private static final int CHUNK_TICKS = 14;
	private static final double CHUNK_MIN_SPEED = 0.5;
	private static final double CHUNK_EXTRA_SPEED = 0.6;
	private static final double CHUNK_GRAVITY = 0.04;
	private static final float CHUNK_SOOT_SCALE = 0.2f;

	// The dust ring that races over the ground.
	private static final double MIN_RING_RADIUS = 3.0;
	private static final int RING_PUFFS = 36;
	private static final double RING_START_SHARE = 0.5;
	private static final double RING_MIN_SPEED = 0.5;
	private static final double RING_SPEED_PER_RADIUS = 0.04;
	private static final double GROUND_SEARCH_SHARE = 0.9;

	// The mushroom column.
	private static final double MAX_COLUMN_BLAST_RADIUS = 14.0;
	private static final double MIN_COLUMN_RADIUS = 6.0;
	private static final double COLUMN_HEIGHT_PER_RADIUS = 3.0;
	private static final double COLUMN_STEM_SHARE = 0.22;
	private static final double COLUMN_CAP_SHARE = 0.9;
	private static final int COLUMN_RISE_TICKS = 20;
	private static final int COLUMN_STEM_PUFFS = 6;
	private static final int COLUMN_CAP_PUFFS = 8;
	// A core blast's column is sized from its radius (a Howitzer core, radius 36, is 120 blocks tall
	// with a 9 block stem and a 40 block cap), and rises over three seconds so the cap can be watched forming.
	private static final double CORE_HEIGHT_PER_RADIUS = 10.0 / 3.0;
	private static final double CORE_STEM_SHARE = 0.25;
	private static final double CORE_CAP_SHARE = 10.0 / 9.0;
	private static final double FULL_CORE_RADIUS = 36.0;
	private static final int MIN_CORE_STEM_PUFFS = 6;
	private static final int MIN_CORE_CAP_PUFFS = 6;
	private static final int CORE_RISE_TICKS = 60;
	private static final double CAP_THICKNESS_SHARE = 0.15;
	private static final double CAP_DOME_SHARE = 0.45; // how high the cap bulges above the top of the stem, relative to its width
	private static final int CORE_CAP_GLOW_PUFFS = 8;
	private static final double STEM_FLARE = 2.5; // how much wider than the stem the cloud is at ground level, minus one

	// A wide, billowing base of cloud around ground zero.
	private static final int SKIRT_TICKS = 40;
	private static final int SKIRT_PUFFS = 22;
	private static final double SKIRT_RADIUS_SHARE = 0.75; // of the cap's radius
	private static final double SKIRT_HEIGHT_SHARE = 0.06; // of the column's height
	private static final double SKIRT_OUT_SPEED = 0.12;

	// Rings of cloud racing out across the ground and around the stem, and jets of smoke streaking away from the blast.
	private static final int RING_PUFFS_BASE = 44;
	private static final double RING_SPEED_START = 1.0;
	private static final double RING_SPEED_FADE_PER_RING = 0.15;
	private static final int JET_TICKS = 4;
	private static final int JETS_PER_TICK = 8;
	private static final int PUFFS_PER_JET = 10;
	private static final double JET_SPACING = 1.0;
	private static final double JET_MAX_ELEVATION = 0.6; // radians above flat
	private static final int CORE_STEM_PUFFS = 45;
	private static final int CORE_CAP_PUFFS = 40;
	private static final double CAP_STARTS_AT = 0.6; // share of the rise
	private static final double CAP_OUT_SPEED = 0.15;
	private static final double CAP_UP_SPEED = 0.05;
	private static final int CORE_MIN_LIFETIME = 300;
	private static final int CORE_EXTRA_LIFETIME = 100;

	// Howitzer core extras.
	private static final int HOWITZER_SPARKS = 200;
	private static final int ASH_TICKS = 200;
	private static final int ASH_EVERY_TICKS = 2;
	private static final int ASH_PER_BATCH = 14;
	private static final double ASH_RANGE = 45.0;
	private static final double ASH_MIN_HEIGHT = 4.0;
	private static final double ASH_EXTRA_HEIGHT = 22.0;
	private static final double ASH_FALL_SPEED = -0.06;
	private static final double ASH_DRIFT = 0.02;
	private static final double ASH_VIEW_DISTANCE = 300.0;

	private static final int FUGA_FIRE_TICKS = 12;
	private static final int FUGA_EMBERS_PER_TICK = 6;
	private static final double FUGA_EMBER_HEIGHT = 70.0;
	private static final int FUGA_FOOT_FLAMES = 6;
	// Decay: a dark crumbling cloud rolling out with the wave, and a column of dust where it started.
	private static final int DECAY_TICKS = 60;
	private static final int DECAY_COLUMN_TICKS = 40;
	private static final double DECAY_RING_PUFFS_PER_BLOCK = 1.2;
	private static final int DECAY_MAX_RING_PUFFS = 120;
	private static final int DECAY_DEBRIS_PER_TICK = 14;
	private static final double DECAY_COLUMN_HEIGHT_PER_RADIUS = 0.8;
	private static final double DECAY_MAX_COLUMN_HEIGHT = 50.0;
	private static final double DECAY_MAX_STEM = 4.0;
	private static final int DECAY_STEM_PUFFS = 18;
	private static final int DECAY_CAP_PUFFS = 14;
	private static final double DECAY_CAP_SHARE = 0.3;
	private static final double DECAY_MAX_CAP = 22.0;
	private static final DustParticleOptions DECAY_BLOOD = new DustParticleOptions(0x5A1010, 2.0f);
	private static final DustParticleOptions DECAY_ASH = new DustParticleOptions(0x6A6A6A, 2.5f);
	// Flashfreeze: shards of the shattered dome raining back down.
	private static final int ICE_RAIN_TICKS = 40;
	private static final int ICE_RAIN_PER_TICK = 18;
	private static final double ICE_RAIN_SPREAD = 0.8;

	private record Pop(long at, Vec3 center, float radius) {
	}

	private record Chunk(Vec3 position, Vec3 velocity, int ticksLeft) {
	}

	private record Ash(Vec3 center, long startTick) {
	}

	private static List<Pop> pops = List.of();
	private static List<Chunk> chunks = List.of();
	private static Ash ash;
	private static int budget;

	private ExplosionSmoke() {
	}

	/** How many particles a base count becomes at the chosen detail. */
	static int scaled(double base) {
		return Math.max(1, (int) Math.round(base * DekuSettings.get().detailScale()));
	}

	/** How long a blast keeps emitting, in ticks. */
	static int emitTicks(Blast blast) {
		if (ExplosionFx.isDecay(blast.style())) {
			return DECAY_TICKS;
		}
		if (blast.style() == Style.FUGA) {
			return FugaBeamFx.LIFETIME_TICKS;
		}
		if (isCore(blast.style())) {
			return CORE_RISE_TICKS + 1;
		}
		return hasColumn(blast) ? Math.max(SMOKE_LAST_TICK, COLUMN_RISE_TICKS) + 1 : SMOKE_LAST_TICK + 1;
	}

	/** Runs every client tick: resets the budget and moves chunks, pops and falling ash along. */
	static void tick(ClientLevel level) {
		budget = scaled(MAX_PARTICLES_PER_TICK);
		if (level == null) {
			pops = List.of();
			chunks = List.of();
			ash = null;
			return;
		}
		long now = level.getGameTime();
		List<Pop> due = pops.stream().filter(pop -> pop.at() <= now).toList();
		pops = pops.stream().filter(pop -> pop.at() > now).toList();
		due.forEach(pop -> pop(level, pop));
		chunks = chunks.stream().map(chunk -> advance(level, chunk)).filter(chunk -> chunk.ticksLeft() > 0).toList();
		fallAsh(level, now);
	}

	/** Plays what the blast does at this age; age 0 is the instant it goes off. */
	static void emit(ClientLevel level, Blast blast, int age) {
		if (blast.style() == Style.ICE_DOME) {
			return; // the dome is drawn as lines by ExplosionFx
		}
		if (blast.style() == Style.PURPLE) {
			if (age == 0) {
				purpleBurst(level, blast, level.getRandom());
			}
			return;
		}
		RandomSource random = level.getRandom();
		if (ExplosionFx.isDecay(blast.style())) {
			decay(level, blast, age, random);
			return;
		}
		if (blast.style() == Style.FUGA) {
			fuga(level, blast, age, random);
			return;
		}
		if (blast.style() == Style.HEATWAVE && age >= 1 && age <= ICE_RAIN_TICKS) {
			iceRain(level, blast, random);
		}
		double share = share(blast.style());
		boolean core = isCore(blast.style());
		if (age == 0) {
			burst(level, blast, share, random);
			scheduleExtras(blast, share);
		}
		if (age == 1 && blast.radius() >= MIN_RING_RADIUS && blast.style() != Style.SHOT) {
			dustRing(level, blast, share, random);
		}
		if (age <= (core ? CORE_FIRE_TICKS : FIRE_TICKS)) {
			fireball(level, blast, share * (core ? CORE_FIRE_COUNT_SCALE : 1), random);
		}
		if (age >= SMOKE_FIRST_TICK && age <= SMOKE_LAST_TICK) {
			soot(level, blast, share, random);
		}
		if (hasColumn(blast)) {
			column(level, blast, age, share, random);
		}
		if (core) {
			double bigness = Math.min(1, blast.radius() / FULL_CORE_RADIUS);
			if (age >= 1 && age <= JET_TICKS) {
				smokeJets(level, blast, bigness, random);
			}
			if (age <= SKIRT_TICKS) {
				baseSkirt(level, blast, bigness, random);
			}
			if (age == 2 || age == 6 || age == 12 || age == 20) {
				groundRing(level, blast, age, bigness, random);
			}
			if (age == 8 || age == 16 || age == 26) {
				airRing(level, blast, age, bigness, random);
			}
		}
	}

	private static double share(Style style) {
		return switch (style) {
			case SHOT -> 0.35;
			case BIG_SHOT -> 0.8;
			case GROUND -> 0.8;
			case NUKE -> 1.0;
			case HOWITZER -> 0.5;
			case HOWITZER_RING -> 0.35;
			case HOWITZER_CORE -> 1.0;
			case HEATWAVE -> 1.0;
			case ICE_DOME -> 0.0;
			case PURPLE -> 0.0;
			case DECAY_WAVE, DECAY_CATASTROPHE -> 0.0;
			case FUGA -> 1.0;
		};
	}

	/** The blasts big enough for a full mushroom cloud, ash and thunder: the Howitzer's core and the nuke. */
	private static boolean isCore(Style style) {
		return style == Style.HOWITZER_CORE || style == Style.NUKE || style == Style.HEATWAVE;
	}

	private static boolean hasColumn(Blast blast) {
		return isCore(blast.style())
			|| blast.style() == Style.BIG_SHOT && blast.radius() >= MIN_COLUMN_RADIUS && blast.radius() <= MAX_COLUMN_BLAST_RADIUS;
	}

	/** The instant of the blast: a white-hot flash, flames, sparks and a few leftover puffs. */
	private static void burst(ClientLevel level, Blast blast, double share, RandomSource random) {
		Vec3 c = blast.center();
		float radius = blast.radius();
		double spread = radius / 3.0;
		for (int i = 0; i < scaled(FLASH_PUFFS * share); i++) {
			spawn(level, DekuParticles.FIREBALL, c, Vec3.ZERO, radius * 0.9f / 4f);
		}
		int flames = scaled(Math.min(MAX_FLAMES, radius * radius * 8) * share);
		for (int i = 0; i < flames; i++) {
			Vec3 v = LightningDraw.randomDirection(random).scale((0.15 + random.nextDouble() * 0.3) * spread);
			spawn(level, ParticleTypes.FLAME, c, v, 1f);
		}
		for (int i = 0; i < scaled(radius * 2 * share); i++) {
			spawn(level, ParticleTypes.LAVA, c, Vec3.ZERO, 1f);
		}
		int puffs = (int) Math.min(MAX_EXPLOSION_PUFFS, radius * EXPLOSION_PUFFS_PER_RADIUS * share);
		for (int i = 0; i < puffs; i++) {
			spawn(level, ParticleTypes.EXPLOSION, c.add(inSphere(random, radius * 0.8)), Vec3.ZERO, 1f);
		}
		if (blast.style() == Style.HEATWAVE) {
			iceBurst(level, c, radius, random);
		}
		if (blast.style() == Style.NUKE) {
			// The nuke burns deep red: a ball of red embers filling the blast.
			for (int i = 0; i < scaled(NUKE_EMBERS + radius * NUKE_EMBERS_PER_RADIUS); i++) {
				Vec3 at = c.add(inSphere(random, radius));
				spawn(level, random.nextBoolean() ? NUKE_RED : NUKE_DARK_RED, at, at.subtract(c).scale(0.05), 1f);
			}
		}
		if (isCore(blast.style())) {
			spawn(level, ParticleTypes.EXPLOSION_EMITTER, c, Vec3.ZERO, 1f);
			for (int i = 0; i < scaled(HOWITZER_SPARKS); i++) {
				Vec3 v = LightningDraw.randomDirection(random).scale(0.6 + random.nextDouble() * 0.8);
				spawn(level, ParticleTypes.FIREWORK, c, new Vec3(v.x, Math.abs(v.y), v.z), 1f);
			}
			ash = new Ash(c, blast.startTick());
		}
	}

	/**
	 * Decay's wave: a dark cloud of dust and the ground's own crumbs racing out along the front, with a
	 * column of rotting dust and falling ash where it began.
	 */
	private static void decay(ClientLevel level, Blast blast, int age, RandomSource random) {
		Vec3 c = blast.center();
		float radius = blast.radius();
		if (age == 0) {
			spawn(level, ParticleTypes.EXPLOSION_EMITTER, c, Vec3.ZERO, 1f);
			ash = new Ash(c, blast.startTick());
		}
		double front = ExplosionFx.decayFront(blast, age);
		if (front > 1 && front < radius + 2) {
			int puffs = scaled(Mth.clamp(front * DECAY_RING_PUFFS_PER_BLOCK, 12, DECAY_MAX_RING_PUFFS));
			for (int i = 0; i < puffs; i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
				double x = c.x + out.x * front;
				double z = c.z + out.z * front;
				double y = surface(level, x, z, c.y);
				float size = 2f + radius * 0.03f;
				Particle puff = spawn(level, random.nextInt(3) == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : DekuParticles.SOOT_SMOKE,
					new Vec3(x, y + 0.6, z), out.scale(0.05 + random.nextDouble() * 0.08).add(0, 0.04 + random.nextDouble() * 0.06, 0), size);
				if (puff instanceof SmokePuffParticle soot) {
					soot.setLifetimeTicks(80 + random.nextInt(60));
				}
				if (i % 4 == 0) {
					spawn(level, random.nextBoolean() ? DECAY_BLOOD : DECAY_ASH, new Vec3(x, y + 1.5, z), out.scale(0.1).add(0, 0.05, 0), 1f);
				}
			}
			// The crumbling ground flung up at the front.
			for (int i = 0; i < scaled(DECAY_DEBRIS_PER_TICK); i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double reach = front - random.nextDouble() * 3;
				double x = c.x + Math.cos(angle) * reach;
				double z = c.z + Math.sin(angle) * reach;
				double y = surface(level, x, z, c.y);
				var state = level.getBlockState(BlockPos.containing(x, y - 1, z));
				if (!state.isAir()) {
					spawn(level, new BlockParticleOption(ParticleTypes.BLOCK, state), new Vec3(x, y + 0.3, z),
						new Vec3((random.nextDouble() - 0.5) * 0.3, 0.25 + random.nextDouble() * 0.45, (random.nextDouble() - 0.5) * 0.3), 1.5f);
				}
			}
		}
		if (age <= DECAY_COLUMN_TICKS) {
			double progress = (double) age / DECAY_COLUMN_TICKS;
			double height = Math.min(DECAY_MAX_COLUMN_HEIGHT, radius * DECAY_COLUMN_HEIGHT_PER_RADIUS);
			double top = height * (1 - (1 - progress) * (1 - progress));
			double stem = Math.min(DECAY_MAX_STEM, 1 + radius * 0.04);
			for (int i = 0; i < scaled(DECAY_STEM_PUFFS); i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double out = random.nextDouble() * stem;
				Vec3 at = c.add(Math.cos(angle) * out, top * random.nextDouble(), Math.sin(angle) * out);
				Particle puff = spawn(level, DekuParticles.SOOT_SMOKE, at, new Vec3(0, 0.06 + random.nextDouble() * 0.1, 0), 2f + (float) stem * 0.5f);
				if (puff instanceof SmokePuffParticle soot) {
					soot.setLifetimeTicks(CORE_MIN_LIFETIME / 2 + random.nextInt(CORE_EXTRA_LIFETIME));
				}
			}
			if (progress > CAP_STARTS_AT) {
				double cap = Math.min(DECAY_MAX_CAP, radius * DECAY_CAP_SHARE);
				for (int i = 0; i < scaled(DECAY_CAP_PUFFS); i++) {
					double angle = random.nextDouble() * Math.PI * 2;
					double out = cap * Math.sqrt(random.nextDouble());
					Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
					Particle puff = spawn(level, DekuParticles.SOOT_SMOKE, c.add(direction.x * out, top + random.nextDouble() * cap * 0.3, direction.z * out),
						direction.scale(0.1).add(0, 0.03, 0), 3f + (float) cap * 0.15f);
					if (puff instanceof SmokePuffParticle soot) {
						soot.setLifetimeTicks(CORE_MIN_LIFETIME / 2 + random.nextInt(CORE_EXTRA_LIFETIME));
					}
				}
			}
		}
	}

	/**
	 * Fuga: a flash and a ball of fire at the crater, then embers and lava sparks climbing the
	 * column of light for as long as it stands. No soot, only fire.
	 */
	private static void fuga(ClientLevel level, Blast blast, int age, RandomSource random) {
		Vec3 c = blast.center();
		if (age == 0) {
			burst(level, blast, 1.0, random);
		}
		if (age <= FUGA_FIRE_TICKS) {
			fireball(level, blast, 1.0, random);
		}
		double radius = Mth.clamp(blast.radius() * 0.5, 5.0, 14.0);
		for (int i = 0; i < scaled(FUGA_EMBERS_PER_TICK); i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double out = radius * (0.3 + random.nextDouble() * 1.4);
			Vec3 at = c.add(Math.cos(angle) * out, random.nextDouble() * FUGA_EMBER_HEIGHT, Math.sin(angle) * out);
			spawn(level, random.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, at, new Vec3(0, 0.15 + random.nextDouble() * 0.25, 0), 1.2f);
		}
		if (age % 2 == 0) {
			for (int i = 0; i < scaled(FUGA_FOOT_FLAMES); i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double out = radius * (1 + random.nextDouble() * 3);
				spawn(level, ParticleTypes.FLAME, c.add(Math.cos(angle) * out, 0.2, Math.sin(angle) * out), new Vec3(0, 0.1, 0), 1.5f);
			}
		}
	}

	/** The height of the top of the ground at this spot, or the fallback if it hasn't loaded. */
	private static double surface(ClientLevel level, double x, double z, double fallback) {
		BlockPos pos = BlockPos.containing(x, fallback, z);
		if (!level.hasChunkAt(pos)) {
			return fallback;
		}
		return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
	}

	/** Shards of the shattered ice dome thrown up and raining back down across it. */
	private static void iceRain(ClientLevel level, Blast blast, RandomSource random) {
		Vec3 c = blast.center();
		for (int i = 0; i < scaled(ICE_RAIN_PER_TICK); i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double out = blast.radius() * ICE_RAIN_SPREAD * Math.sqrt(random.nextDouble());
			Vec3 at = c.add(Math.cos(angle) * out, random.nextDouble() * blast.radius() * 0.3, Math.sin(angle) * out);
			spawn(level, new BlockParticleOption(ParticleTypes.BLOCK, random.nextBoolean() ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.BLUE_ICE.defaultBlockState()),
				at, new Vec3((random.nextDouble() - 0.5) * 0.4, 0.2 + random.nextDouble() * 0.6, (random.nextDouble() - 0.5) * 0.4), 2.5f);
		}
		for (int i = 0; i < scaled(4); i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double out = blast.radius() * ICE_RAIN_SPREAD * Math.sqrt(random.nextDouble());
			spawn(level, ParticleTypes.SNOWFLAKE, c.add(Math.cos(angle) * out, blast.radius() * 0.5, Math.sin(angle) * out), new Vec3(0, -0.05, 0), 1.5f);
		}
	}

	/** Hollow Purple collapsing: a sphere of violet light blowing outward and a flash that sucks the air after it. */
	private static void purpleBurst(ClientLevel level, Blast blast, RandomSource random) {
		Vec3 c = blast.center();
		spawn(level, ParticleTypes.EXPLOSION_EMITTER, c, Vec3.ZERO, 1f);
		for (int i = 0; i < scaled(PURPLE_DUST); i++) {
			Vec3 v = LightningDraw.randomDirection(random).scale((0.4 + random.nextDouble() * 1.4) * blast.radius() / 14.0);
			spawn(level, random.nextBoolean() ? PURPLE_BRIGHT : PURPLE_DEEP, c.add(inSphere(random, blast.radius() * 0.3)), v, 1f);
		}
		for (int i = 0; i < scaled(PURPLE_PORTAL); i++) {
			Vec3 v = LightningDraw.randomDirection(random).scale(0.5 + random.nextDouble());
			spawn(level, ParticleTypes.REVERSE_PORTAL, c.add(inSphere(random, blast.radius() * 0.6)), v, 1f);
		}
	}

	/** The dome of ice shattering: shards of it flying out with a cloud of frost and steam. */
	private static void iceBurst(ClientLevel level, Vec3 c, float radius, RandomSource random) {
		for (int i = 0; i < scaled(ICE_SHARDS); i++) {
			Vec3 v = LightningDraw.randomDirection(random).scale(0.5 + random.nextDouble() * 1.2);
			spawn(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
				c.add(LightningDraw.randomDirection(random).scale(radius * 0.4)), new Vec3(v.x, Math.abs(v.y) + 0.2, v.z), 2f);
		}
		for (int i = 0; i < scaled(FROST_PUFFS); i++) {
			Vec3 v = LightningDraw.randomDirection(random).scale(0.6);
			spawn(level, DekuParticles.WHITE_SMOKE, c.add(LightningDraw.randomDirection(random).scale(radius * 0.5)), v, 1f);
			spawn(level, ParticleTypes.SNOWFLAKE, c, v.scale(2), 1f);
		}
	}

	/** A ball of fire inside the blast that cools from white to red to black as it rises. */
	private static void fireball(ClientLevel level, Blast blast, double share, RandomSource random) {
		float radius = blast.radius();
		float size = Mth.clamp(0.6f + radius * 0.25f, MIN_FIREBALL_SCALE, MAX_FIREBALL_SCALE);
		int count = scaled(Math.min(MAX_FIREBALLS_PER_TICK, radius * FIREBALLS_PER_RADIUS) * share);
		for (int i = 0; i < count; i++) {
			Vec3 at = blast.center().add(inSphere(random, radius * 0.7));
			Vec3 v = at.subtract(blast.center()).scale(FIREBALL_SPREAD).add(0, 0.02, 0);
			spawn(level, DekuParticles.FIREBALL, at, v, size);
		}
	}

	/** Black smoke rolling up out of the fireball. */
	private static void soot(ClientLevel level, Blast blast, double share, RandomSource random) {
		float radius = blast.radius();
		float size = Mth.clamp(0.5f + radius * 0.35f, 1f, MAX_SOOT_SCALE);
		int count = scaled(Math.min(MAX_SOOT_PER_TICK, radius * SOOT_PER_RADIUS) * share);
		for (int i = 0; i < count; i++) {
			Vec3 at = blast.center().add(inSphere(random, radius * 0.6));
			Vec3 v = new Vec3((random.nextDouble() - 0.5) * 0.04, 0.04 + random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.04);
			spawn(level, DekuParticles.SOOT_SMOKE, at, v, size);
		}
	}

	/** Queues the secondary pops and flings the burning chunks. */
	private static void scheduleExtras(Blast blast, double share) {
		RandomSource random = RandomSource.create(blast.startTick() * 31 + Double.hashCode(blast.center().x));
		float radius = blast.radius();
		boolean lightShot = blast.style() == Style.SHOT;
		if (radius >= MIN_POP_RADIUS && !lightShot) {
			List<Pop> queued = new ArrayList<>(pops);
			int count = 2 + (int) Math.min(2, radius / 10);
			for (int i = 0; i < count; i++) {
				long at = blast.startTick() + FIRST_POP_TICK + random.nextInt(POP_TICK_SPREAD);
				queued.add(new Pop(at, blast.center().add(inSphere(random, radius * POP_SPREAD)), radius * (float) POP_RADIUS_SHARE));
			}
			pops = List.copyOf(queued);
		}
		if (radius >= MIN_CHUNK_RADIUS && !lightShot) {
			List<Chunk> flung = new ArrayList<>(chunks);
			int count = scaled(Math.min(MAX_CHUNKS_PER_BLAST, radius) * share);
			for (int i = 0; i < count; i++) {
				Vec3 out = LightningDraw.randomDirection(random);
				Vec3 v = new Vec3(out.x, Math.abs(out.y) + 0.4, out.z).normalize().scale(CHUNK_MIN_SPEED + random.nextDouble() * CHUNK_EXTRA_SPEED);
				flung.add(new Chunk(blast.center(), v, CHUNK_TICKS));
			}
			chunks = List.copyOf(flung.subList(Math.max(0, flung.size() - MAX_LIVE_CHUNKS), flung.size()));
		}
	}

	private static void pop(ClientLevel level, Pop pop) {
		RandomSource random = level.getRandom();
		float size = Mth.clamp(0.6f + pop.radius() * 0.25f, MIN_FIREBALL_SCALE, MAX_FIREBALL_SCALE);
		for (int i = 0; i < scaled(Math.min(MAX_POP_FIREBALLS, pop.radius() + 3)); i++) {
			spawn(level, DekuParticles.FIREBALL, pop.center().add(inSphere(random, pop.radius() * 0.5)),
				new Vec3(0, 0.02, 0), size);
		}
		for (int i = 0; i < scaled(POP_SOOT); i++) {
			spawn(level, DekuParticles.SOOT_SMOKE, pop.center().add(inSphere(random, pop.radius() * 0.5)),
				new Vec3(0, 0.05, 0), Mth.clamp(0.5f + pop.radius() * 0.35f, 1f, MAX_SOOT_SCALE));
		}
		spawn(level, ParticleTypes.EXPLOSION, pop.center(), Vec3.ZERO, 1f);
		level.playLocalSound(pop.center().x, pop.center().y, pop.center().z, DekuSounds.EXPLOSION_POP, SoundSource.BLOCKS,
			POP_VOLUME, 0.5f + random.nextFloat() * 0.3f, false);
	}

	private static Chunk advance(ClientLevel level, Chunk chunk) {
		spawn(level, ParticleTypes.FLAME, chunk.position(), Vec3.ZERO, 1f);
		spawn(level, DekuParticles.SOOT_SMOKE, chunk.position(), new Vec3(0, 0.02, 0), CHUNK_SOOT_SCALE);
		return new Chunk(chunk.position().add(chunk.velocity()), chunk.velocity().subtract(0, CHUNK_GRAVITY, 0), chunk.ticksLeft() - 1);
	}

	/** Black dust racing outward across the floor, if the blast is near one. */
	private static void dustRing(ClientLevel level, Blast blast, double share, RandomSource random) {
		BlockPos start = BlockPos.containing(blast.center());
		int reach = (int) Math.ceil(blast.radius() * GROUND_SEARCH_SHARE) + 1;
		for (int down = 0; down <= reach; down++) {
			BlockPos pos = start.below(down);
			if (level.getBlockState(pos).isAir()) {
				continue;
			}
			float size = 1.5f + blast.radius() * 0.12f;
			double speed = RING_MIN_SPEED + blast.radius() * RING_SPEED_PER_RADIUS;
			for (int i = 0; i < scaled(RING_PUFFS * share); i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
				Vec3 at = new Vec3(blast.center().x, pos.getY() + 1.2, blast.center().z).add(out.scale(blast.radius() * RING_START_SHARE));
				spawn(level, DekuParticles.SOOT_SMOKE, at, out.scale(speed).add(0, 0.02, 0), size);
			}
			return;
		}
	}

	/** A rising stem of smoke with a wide flat cap, like a mushroom cloud; a core blast's cap glows red underneath. */
	private static void column(ClientLevel level, Blast blast, int age, double share, RandomSource random) {
		boolean core = isCore(blast.style());
		int rise = core ? CORE_RISE_TICKS : COLUMN_RISE_TICKS;
		if (age > rise) {
			return;
		}
		float radius = blast.radius();
		double height = radius * (core ? CORE_HEIGHT_PER_RADIUS : COLUMN_HEIGHT_PER_RADIUS);
		double stem = radius * (core ? CORE_STEM_SHARE : COLUMN_STEM_SHARE);
		double cap = radius * (core ? CORE_CAP_SHARE : COLUMN_CAP_SHARE);
		double bigness = Math.min(1, radius / FULL_CORE_RADIUS);
		double progress = (double) age / rise;
		double top = height * (1 - (1 - progress) * (1 - progress));
		Vec3 c = blast.center();

		int stemPuffs = core ? Math.max(MIN_CORE_STEM_PUFFS, (int) (CORE_STEM_PUFFS * bigness)) : COLUMN_STEM_PUFFS;
		for (int i = 0; i < scaled(stemPuffs); i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double heightShare = random.nextDouble();
			// The stem flares out wide at its foot, like the base of a nuclear cloud.
			double flare = core ? 1 + STEM_FLARE * (1 - heightShare) * (1 - heightShare) : 1;
			double out = random.nextDouble() * stem * flare;
			Vec3 at = c.add(Math.cos(angle) * out, top * heightShare, Math.sin(angle) * out);
			column(level, at, new Vec3(0, 0.05 + random.nextDouble() * 0.1, 0), 1.5f + (float) (stem * flare) * 0.25f, core, random);
		}
		if (progress >= CAP_STARTS_AT) {
			// The cap is a dome of smoke on top of the stem, much wider than the stem itself.
			int capPuffs = core ? Math.max(MIN_CORE_CAP_PUFFS, (int) (CORE_CAP_PUFFS * bigness)) : COLUMN_CAP_PUFFS;
			for (int i = 0; i < scaled(capPuffs); i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double across = Math.sqrt(random.nextDouble());
				double out = cap * across;
				double dome = Math.sqrt(1 - across * across) * cap * CAP_DOME_SHARE;
				Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
				Vec3 at = c.add(direction.x * out, top + dome * random.nextDouble(), direction.z * out);
				column(level, at, direction.scale(CAP_OUT_SPEED).add(0, CAP_UP_SPEED, 0), 2f + (float) cap * 0.1f, core, random);
			}
			if (core) {
				// Fire still burning under the cap lights it from below, cooling to black as it rises.
				for (int i = 0; i < scaled(CORE_CAP_GLOW_PUFFS * bigness + 1); i++) {
					double angle = random.nextDouble() * Math.PI * 2;
					double out = cap * 0.7 * Math.sqrt(random.nextDouble());
					Vec3 at = c.add(Math.cos(angle) * out, top - cap * CAP_THICKNESS_SHARE, Math.sin(angle) * out);
					spawn(level, DekuParticles.FIREBALL, at, new Vec3(0, CAP_UP_SPEED, 0), 1f + (float) cap * 0.12f);
				}
			}
		}
	}

	/** Cloud rolling out of the ground all around the foot of the column. */
	private static void baseSkirt(ClientLevel level, Blast blast, double bigness, RandomSource random) {
		double baseRadius = blast.radius() * CORE_CAP_SHARE * SKIRT_RADIUS_SHARE;
		double height = blast.radius() * CORE_HEIGHT_PER_RADIUS * SKIRT_HEIGHT_SHARE;
		for (int i = 0; i < scaled(SKIRT_PUFFS * bigness + 3); i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double out = baseRadius * Math.sqrt(random.nextDouble());
			Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			Vec3 at = blast.center().add(direction.x * out, random.nextDouble() * height, direction.z * out);
			column(level, at, direction.scale(SKIRT_OUT_SPEED).add(0, 0.02, 0), 2f + (float) baseRadius * 0.12f, true, random);
		}
	}

	/** A ring of dark smoke and white vapour racing out along the ground, each ring slower than the last. */
	private static void groundRing(ClientLevel level, Blast blast, int age, double bigness, RandomSource random) {
		double speed = (RING_SPEED_START - RING_SPEED_FADE_PER_RING * ringIndex(age)) * (0.6 + blast.radius() * 0.02);
		ring(level, new Vec3(blast.center().x, blast.center().y + 1, blast.center().z), blast.radius() * 0.4, speed, bigness, 2f + blast.radius() * 0.1f, random);
	}

	/** A ring of vapour expanding around the stem partway up, like the pressure rings on a nuclear cloud. */
	private static void airRing(ClientLevel level, Blast blast, int age, double bigness, RandomSource random) {
		double height = blast.radius() * CORE_HEIGHT_PER_RADIUS * (0.25 + 0.12 * ringIndex(age));
		double speed = (RING_SPEED_START - RING_SPEED_FADE_PER_RING * ringIndex(age)) * 0.7 * (0.6 + blast.radius() * 0.02);
		ring(level, blast.center().add(0, height, 0), blast.radius() * CORE_STEM_SHARE * 1.5, speed, bigness, 1.5f + blast.radius() * 0.06f, random);
	}

	private static int ringIndex(int age) {
		return age / 8;
	}

	private static void ring(ClientLevel level, Vec3 center, double startRadius, double speed, double bigness, float size, RandomSource random) {
		int puffs = scaled(RING_PUFFS_BASE * bigness + 10);
		for (int i = 0; i < puffs; i++) {
			double angle = Math.PI * 2 * i / puffs + random.nextDouble() * 0.1;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			Vec3 at = center.add(out.scale(startRadius));
			Particle puff = spawn(level, i % 2 == 0 ? DekuParticles.SOOT_SMOKE : ParticleTypes.CLOUD, at, out.scale(speed), i % 2 == 0 ? size : size * 0.6f);
			if (puff instanceof SmokePuffParticle soot) {
				soot.setLifetimeTicks(CORE_MIN_LIFETIME + random.nextInt(CORE_EXTRA_LIFETIME));
			}
		}
	}

	/** Streaks of smoke and vapour shooting out of the blast in every direction, showing the power of it. */
	private static void smokeJets(ClientLevel level, Blast blast, double bigness, RandomSource random) {
		double speed = 1.0 + blast.radius() * 0.03;
		float size = 0.5f + blast.radius() * 0.025f;
		for (int jet = 0; jet < scaled(JETS_PER_TICK * bigness + 2); jet++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double elevation = random.nextDouble() * JET_MAX_ELEVATION;
			Vec3 direction = new Vec3(Math.cos(angle) * Math.cos(elevation), Math.sin(elevation), Math.sin(angle) * Math.cos(elevation));
			for (int i = 0; i < PUFFS_PER_JET; i++) {
				Vec3 at = blast.center().add(direction.scale(i * JET_SPACING));
				spawn(level, jet % 2 == 0 ? DekuParticles.SOOT_SMOKE : ParticleTypes.CLOUD, at, direction.scale(speed), jet % 2 == 0 ? size : size * 0.5f);
			}
		}
	}

	private static void column(ClientLevel level, Vec3 at, Vec3 velocity, float size, boolean core, RandomSource random) {
		Particle puff = spawn(level, DekuParticles.SOOT_SMOKE, at, velocity, size);
		if (core && puff instanceof SmokePuffParticle soot) {
			soot.setLifetimeTicks(CORE_MIN_LIFETIME + random.nextInt(CORE_EXTRA_LIFETIME));
		}
	}

	/** Black ash drifting down around the player for several seconds after a Howitzer Impact. */
	private static void fallAsh(ClientLevel level, long now) {
		Minecraft minecraft = Minecraft.getInstance();
		if (ash == null || minecraft.player == null) {
			return;
		}
		long age = now - ash.startTick();
		if (age >= ASH_TICKS) {
			ash = null;
			return;
		}
		Vec3 player = minecraft.player.position();
		if (age % ASH_EVERY_TICKS != 0 || player.distanceTo(ash.center()) > ASH_VIEW_DISTANCE) {
			return;
		}
		RandomSource random = level.getRandom();
		for (int i = 0; i < scaled(ASH_PER_BATCH); i++) {
			Vec3 at = player.add((random.nextDouble() * 2 - 1) * ASH_RANGE, ASH_MIN_HEIGHT + random.nextDouble() * ASH_EXTRA_HEIGHT,
				(random.nextDouble() * 2 - 1) * ASH_RANGE);
			spawn(level, DekuParticles.ASH_FLAKE, at,
				new Vec3((random.nextDouble() - 0.5) * ASH_DRIFT, ASH_FALL_SPEED, (random.nextDouble() - 0.5) * ASH_DRIFT), 1f);
		}
	}

	private static Vec3 inSphere(RandomSource random, double radius) {
		return LightningDraw.randomDirection(random).scale(radius * Math.cbrt(random.nextDouble()));
	}

	/** Spawns one particle, resized by the factor, unless this tick's budget is spent. */
	private static Particle spawn(ClientLevel level, ParticleOptions options, Vec3 at, Vec3 velocity, float scale) {
		if (budget <= 0) {
			return null;
		}
		budget--;
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(options, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
		if (particle != null && scale != 1f) {
			particle.scale(scale);
		}
		return particle;
	}
}
