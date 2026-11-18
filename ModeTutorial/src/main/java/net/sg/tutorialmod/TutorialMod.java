package net.sg.tutorialmod;

import net.fabricmc.api.ModInitializer;

import net.sg.tutorialmod.block.ModBlocks;
import net.sg.tutorialmod.item.ModitemGroups;
import net.sg.tutorialmod.item.ModItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TutorialMod implements ModInitializer {
	public static final String MOD_ID = "tutorialmod";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModitemGroups.registerItemGroups();

		ModItems.registerModItems();
		ModBlocks.registerModBlocks();
	}
}