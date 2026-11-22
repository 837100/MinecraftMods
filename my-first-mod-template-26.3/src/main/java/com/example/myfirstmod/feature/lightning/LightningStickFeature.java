package com.example.myfirstmod.feature.lightning;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.HitResult;
import com.example.myfirstmod.feature.hunting.KillerAttribution;

public final class LightningStickFeature {
	private LightningStickFeature() {
	}

	public static void initialize() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.isEmpty()
					|| stack.getItem() != Items.BLAZE_ROD
					|| player.isShiftKeyDown()
					|| level.isClientSide()) {
				return InteractionResult.PASS;
			}

			ServerLevel serverLevel = (ServerLevel) level;
			HitResult hitResult = player.pick(128.0D, 1.0F, false);
			EntityType<? extends LightningBolt> lightningType =
					(EntityType<? extends LightningBolt>) BuiltInRegistries.ENTITY_TYPE
							.getValue(Identifier.withDefaultNamespace("lightning_bolt"));
			LightningBolt lightning = new LightningBolt(lightningType, serverLevel);
			lightning.setPos(hitResult.getLocation());
			if (player instanceof ServerPlayer serverPlayer) {
				lightning.setCause(serverPlayer);
				KillerAttribution.mark(lightning, serverPlayer);
			}
			serverLevel.addFreshEntity(lightning);
			return InteractionResult.SUCCESS_SERVER;
		});
	}
}
