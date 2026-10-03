package com.ronaldw07.deku;

import com.ronaldw07.deku.network.DangerPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of Danger Sense: for each player who has it on, finds the most urgent
 * threat (a mob hunting them, or a projectile on course to hit them) and tells their client.
 */
public final class DangerSense {
	private static final double SENSE_RANGE = 36.0;
	// A projectile counts if it will pass this close, and gets more urgent the sooner it arrives.
	private static final double PROJECTILE_MISS_MARGIN = 2.0;
	private static final double PROJECTILE_WARNING_TICKS = 40.0;
	private static final double MIN_PROJECTILE_SPEED_SQR = 0.01; // ignores arrows stuck in the ground
	// Only resend when the level moves this much, so a steady threat isn't a packet every tick.
	private static final float RESEND_STEP = 0.05f;

	private record Threat(float level, Vec3 source) {
		static final Threat NONE = new Threat(0, Vec3.ZERO);
	}

	private static final Set<UUID> enabled = new HashSet<>();
	private static final Map<UUID, Float> lastSent = new HashMap<>();

	private DangerSense() {
	}

	public static void setEnabled(ServerPlayer player, boolean on) {
		if (on) {
			enabled.add(player.getUUID());
		} else {
			enabled.remove(player.getUUID());
			lastSent.remove(player.getUUID());
		}
	}

	public static void tick(MinecraftServer server) {
		for (UUID id : enabled) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null || !ServerPlayNetworking.canSend(player, DangerPayload.TYPE)) {
				continue;
			}

			Threat threat = player.isAlive() ? assess(player) : Threat.NONE;
			float previous = lastSent.getOrDefault(id, 0f);
			boolean changed = Math.abs(threat.level() - previous) >= RESEND_STEP || (threat.level() == 0) != (previous == 0);
			if (changed) {
				ServerPlayNetworking.send(player, new DangerPayload(threat.level(), threat.source()));
				lastSent.put(id, threat.level());
			}
		}
	}

	private static Threat assess(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 center = player.getBoundingBox().getCenter();
		AABB area = player.getBoundingBox().inflate(SENSE_RANGE);
		Threat worst = Threat.NONE;

		for (Mob mob : level.getEntitiesOfClass(Mob.class, area, mob -> mob.isAlive() && mob.getTarget() == player)) {
			float urgency = (float) Mth.clamp(1 - mob.distanceTo(player) / SENSE_RANGE, 0, 1);
			worst = urgency > worst.level() ? new Threat(urgency, mob.getBoundingBox().getCenter()) : worst;
		}

		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area, p -> p.getOwner() != player)) {
			float urgency = projectileUrgency(projectile.position(), projectile.getDeltaMovement(), center);
			worst = urgency > worst.level() ? new Threat(urgency, projectile.position()) : worst;
		}
		return worst;
	}

	/** How soon a projectile flying in a straight line will pass within the miss margin; 0 if it won't. */
	static float projectileUrgency(Vec3 position, Vec3 velocity, Vec3 target) {
		double speedSqr = velocity.lengthSqr();
		if (speedSqr < MIN_PROJECTILE_SPEED_SQR) {
			return 0;
		}

		double ticksToClosest = target.subtract(position).dot(velocity) / speedSqr;
		if (ticksToClosest < 0) {
			return 0;
		}

		double missDistance = position.add(velocity.scale(ticksToClosest)).distanceTo(target);
		if (missDistance > PROJECTILE_MISS_MARGIN) {
			return 0;
		}
		return (float) Mth.clamp(1 - ticksToClosest / PROJECTILE_WARNING_TICKS, 0, 1);
	}
}
