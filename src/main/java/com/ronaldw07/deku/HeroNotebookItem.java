package com.ronaldw07.deku;

import java.util.function.Consumer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Deku's notebook: right-click to read how every move works. The pages live on the
 * client and are drawn fresh each time, so an old copy never goes out of date.
 */
public class HeroNotebookItem extends Item {
	/** Set by the client to open the notebook screen; does nothing on a dedicated server. */
	public static Consumer<Player> opener = player -> {
	};

	public HeroNotebookItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			opener.accept(player);
		}
		return InteractionResult.SUCCESS;
	}
}
