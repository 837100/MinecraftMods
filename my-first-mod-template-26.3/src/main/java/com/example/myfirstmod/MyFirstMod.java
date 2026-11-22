package com.example.myfirstmod;

import com.example.myfirstmod.feature.lightning.LightningStickFeature;
import com.example.myfirstmod.feature.hunting.HuntingFeature;
import com.example.myfirstmod.feature.staticaura.StaticAuraFeature;
import com.example.myfirstmod.feature.stormsovereign.StormSovereignFeature;
import com.example.myfirstmod.feature.zeusleap.ZeusLeapFeature;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyFirstMod implements ModInitializer {
	public static final String MOD_ID = "my-first-mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LightningStickFeature.initialize();
		ZeusLeapFeature.initialize();
		HuntingFeature.initialize();
		StormSovereignFeature.initialize();
		StaticAuraFeature.initialize();
		LOGGER.info("My First Mod has been loaded successfully!");
		LOGGER.info("Welcome to the server! This is a message from your first mod (Log version).");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
