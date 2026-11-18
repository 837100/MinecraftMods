package com.example.myfirstmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyFirstMod implements ModInitializer {
	public static final String MOD_ID = "my-first-mod";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	@SuppressWarnings("unchecked")
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.isEmpty() || stack.getItem() != Items.STICK || level.isClientSide()) {
				return InteractionResult.PASS;
			}

			ServerLevel serverLevel = (ServerLevel) level;
			HitResult hitResult = player.pick(128.0D, 1.0F, false);
			Vec3 target = hitResult.getLocation();
			EntityType<? extends LightningBolt> lightningType =
					(EntityType<? extends LightningBolt>) BuiltInRegistries.ENTITY_TYPE
							.getValue(Identifier.withDefaultNamespace("lightning_bolt"));
			LightningBolt lightning = new LightningBolt(lightningType, serverLevel);
			lightning.setPos(target);
			serverLevel.addFreshEntity(lightning);

			return InteractionResult.SUCCESS_SERVER;
		});

		LOGGER.info("My First Mod has been loaded successfully!");
		LOGGER.info("Welcome to the server! This is a message from your first mod (Log version).");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}