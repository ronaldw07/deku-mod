package com.ronaldw07.deku;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** What a player's crosshair lands on. */
public final class Aim {
	private Aim() {
	}

	/**
	 * The first living thing or block along the player's view within reach. A miss is a
	 * BlockHitResult of type MISS located at full reach.
	 */
	public static HitResult trace(ServerPlayer player, double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 reachEnd = eye.add(look.scale(reach));
		BlockHitResult blockHit = player.level().clip(new ClipContext(eye, reachEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 end = blockHit.getType() == HitResult.Type.MISS ? reachEnd : blockHit.getLocation();
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end,
			player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),
			entity -> entity instanceof LivingEntity && !entity.isSpectator() && entity.isPickable(),
			eye.distanceToSqr(end));

		if (entityHit != null) {
			return entityHit;
		}
		if (blockHit.getType() == HitResult.Type.MISS) {
			return BlockHitResult.miss(reachEnd, Direction.getApproximateNearest(look), BlockPos.containing(reachEnd));
		}
		return blockHit;
	}
}
