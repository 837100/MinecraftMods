package net.sg.tutorialmod;

import net.fabricmc.api.ModInitializer;

import net.sg.tutorialmod.block.ModBlocks;
import net.sg.tutorialmod.item.Moditems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TutorialMod implements ModInitializer {
	public static final String MOD_ID = "tutorialmod";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Moditems.registerModItems();
		ModBlocks.registerModBlocks();
	}
}