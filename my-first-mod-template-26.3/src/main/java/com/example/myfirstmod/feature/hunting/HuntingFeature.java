package com.example.myfirstmod.feature.hunting;

import com.example.myfirstmod.MyFirstMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class HuntingFeature {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<UUID, SpawnedMob> SPAWNED_MOBS = new HashMap<>();
	private static final List<RespawnEntry> RESPAWNS = new ArrayList<>();
	private static final Map<UUID, BlockPos> SELECTIONS = new HashMap<>();
	private static Config CONFIG = defaultConfig();
	private static int tickCounter;

	private HuntingFeature() {
	}

	public static void initialize() {
		loadConfig();
		ServerTickEvents.END_SERVER_TICK.register(HuntingFeature::tick);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide()
					|| !(player instanceof ServerPlayer serverPlayer)
					|| !player.getItemInHand(hand).is(Items.NETHER_STAR)) {
				return InteractionResult.PASS;
			}

			if (player.isShiftKeyDown()) {
				boolean removedSelection = SELECTIONS.remove(player.getUUID()) != null;
				Zone removedZone = removedSelection ? null : findZoneFor(serverPlayer);
				if (removedZone != null) {
					CONFIG.zones.remove(removedZone);
					removeZoneRuntimeEntries(((ServerLevel) serverPlayer.level()).getServer(), removedZone);
					saveConfig();
					serverPlayer.sendSystemMessage(Component.literal(
							"Hunting zone canceled: " + removedZone.id), false);
				} else if (removedSelection) {
					serverPlayer.sendSystemMessage(Component.literal("Hunting area selection canceled."), false);
				} else {
					serverPlayer.sendSystemMessage(Component.literal(
							"No hunting area or in-progress selection to cancel."), false);
				}
				return InteractionResult.SUCCESS_SERVER;
			}

			HitResult hit = serverPlayer.pick(128.0D, 1.0F, false);
			if (!(hit instanceof BlockHitResult blockHit)) {
				serverPlayer.sendSystemMessage(Component.literal(
						"Look at a block to select a hunting-area corner."), false);
				return InteractionResult.SUCCESS_SERVER;
			}
			BlockPos pos = blockHit.getBlockPos();
			BlockPos first = SELECTIONS.putIfAbsent(player.getUUID(), pos);
			if (first == null) {
				serverPlayer.sendSystemMessage(Component.literal(
						"First hunting-area corner set: " + posToString(pos)
								+ ". Right-click the opposite corner."), false);
				return InteractionResult.SUCCESS_SERVER;
			}

			SELECTIONS.remove(player.getUUID());
			Zone zone = createZone(serverPlayer, first, pos);
			if (zone == null) {
				serverPlayer.sendSystemMessage(Component.literal(
						"Could not create hunting zone."), false);
				return InteractionResult.SUCCESS_SERVER;
			}
			CONFIG.zones = new ArrayList<>(CONFIG.zones);
			CONFIG.zones.add(zone);
			saveConfig();
			serverPlayer.sendSystemMessage(Component.literal(
					"Created hunting zone " + zone.id + ": "
							+ posToString(zone.min) + " to " + posToString(zone.max)), false);
			return InteractionResult.SUCCESS_SERVER;
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof LivingEntity victim)) {
				return;
			}
			SpawnedMob spawned = SPAWNED_MOBS.remove(victim.getUUID());
			if (spawned == null) {
				MobSetting zoneSetting = findMatchingZoneSetting(victim);
				if (zoneSetting == null) {
					MyFirstMod.LOGGER.debug("Hunting death ignored for untracked mob outside a configured zone: {}",
							victim.getUUID());
					return;
				}
				spawned = new SpawnedMob(findZoneFor(victim), zoneSetting, victim.getUUID());
			}

			Zone deathZone = spawned.zone != null ? spawned.zone : findZoneFor(victim);
			if (deathZone == null) {
				MyFirstMod.LOGGER.debug("Hunting mob died outside a configured hunting zone: {}",
						victim.getUUID());
			} else {
				giveExperienceToPlayersInZone(victim, deathZone, spawned.setting);
			}
			RESPAWNS.add(new RespawnEntry(spawned.zone, spawned.setting,
					tickCounter + spawned.setting.respawnTicks));
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				dispatcher.register(literal("hunting")
						.then(literal("status").executes(context -> {
							ServerPlayer player = context.getSource().getPlayerOrException();
							showStatus(player);
							return 1;
						}))
						.then(literal("reload").executes(context -> {
							loadConfig();
							context.getSource().sendSuccess(
									() -> Component.literal("Hunting configuration reloaded."),
									false);
							return 1;
						}))
						.then(literal("cancel")
								.executes(context -> {
									ServerPlayer player = context.getSource().getPlayerOrException();
									SELECTIONS.remove(player.getUUID());
									Zone zone = findZoneFor(player);
									if (zone != null) {
										CONFIG.zones.remove(zone);
										removeZoneRuntimeEntries(((ServerLevel) player.level()).getServer(), zone);
										saveConfig();
										context.getSource().sendSuccess(
												() -> Component.literal("Canceled hunting zone: " + zone.id),
												false);
									} else {
										context.getSource().sendSuccess(
												() -> Component.literal("Canceled pending hunting-area selection."),
												false);
									}
									return 1;
								})
								.then(argument("zoneId", StringArgumentType.greedyString())
										.executes(context -> {
											String zoneId = StringArgumentType.getString(context, "zoneId");
											Zone zone = CONFIG.zones.stream()
													.filter(candidate -> candidate.id.equals(zoneId))
													.findFirst().orElse(null);
											if (zone == null) {
												context.getSource().sendSuccess(
														() -> Component.literal("Unknown hunting zone: " + zoneId),
														false);
												return 1;
											}
											CONFIG.zones.remove(zone);
											removeZoneRuntimeEntries(((ServerLevel) context.getSource().getPlayerOrException().level()).getServer(), zone);
											saveConfig();
											context.getSource().sendSuccess(
													() -> Component.literal("Canceled hunting zone: " + zoneId),
													false);
											return 1;
										})))
						.then(literal("list").executes(context -> {
							context.getSource().sendSuccess(() -> Component.literal(
									CONFIG.zones.isEmpty()
											? "No hunting zones configured."
											: "Hunting zones: " + CONFIG.zones.stream()
													.map(zone -> zone.id).reduce((a, b) -> a + ", " + b)
													.orElse("")), false);
							return 1;
						}))
						.then(literal("debug").executes(context -> {
							ServerPlayer player = context.getSource().getPlayerOrException();
							HuntingProgressData data = getData(((ServerLevel) player.level()).getServer());
							HuntingProgressData.PlayerProgress progress =
									data.getOrCreate(player.getUUID());
							context.getSource().sendSuccess(() -> Component.literal(
									String.format("Hunting debug: level=%d exp=%d trackedMobs=%d respawns=%d zones=%d",
											progress.level(), progress.experience(),
											SPAWNED_MOBS.size(), RESPAWNS.size(), CONFIG.zones.size())),
									false);
							return 1;
						}))
						.then(literal("setlevel")
								.then(argument("level", IntegerArgumentType.integer(1))
										.executes(context -> {
											int level = IntegerArgumentType.getInteger(context, "level");
											ServerPlayer player = context.getSource().getPlayerOrException();
											HuntingProgressData data = getData(((ServerLevel) player.level()).getServer());
											HuntingProgressData.PlayerProgress progress =
													data.getOrCreate(player.getUUID());
											progress.set(level, 0L);
											data.markChanged();
											showStatus(player);
											return 1;
										})))));
	}

	private static void tick(MinecraftServer server) {
		tickCounter++;
		KillerAttribution.cleanupRemovedEntities();

		Iterator<RespawnEntry> iterator = RESPAWNS.iterator();
		while (iterator.hasNext()) {
			RespawnEntry entry = iterator.next();
			if (tickCounter >= entry.respawnAtTick) {
				ServerLevel level = findLevel(server, entry.zone.dimension);
				if (level != null) {
					spawnMob(level, entry.zone, entry.setting);
				}
				iterator.remove();
			}
		}

		for (Zone zone : CONFIG.zones) {
			ServerLevel level = findLevel(server, zone.dimension);
			if (level == null) {
				continue;
			}
			int zoneMobLimit = Math.min(20, CONFIG.maxZoneMobCount > 0
					? CONFIG.maxZoneMobCount
					: 20);
			int aliveInZone = 0;
			for (SpawnedMob mob : SPAWNED_MOBS.values()) {
				if (mob.zone.id.equals(zone.id)
						&& level.getEntityInAnyDimension(mob.uuid) instanceof LivingEntity living
						&& living.isAlive()) {
					aliveInZone++;
				}
			}
			for (MobSetting setting : zone.mobSettings) {
				long alive = SPAWNED_MOBS.values().stream()
						.filter(mob -> mob.zone.id.equals(zone.id)
								&& mob.setting.entity.equals(setting.entity))
						.filter(mob -> level.getEntityInAnyDimension(mob.uuid) instanceof LivingEntity living
								&& living.isAlive())
						.count();
				int settingLimit = Math.min(20, setting.maxCount);
				if (aliveInZone < zoneMobLimit && alive < settingLimit) {
					spawnMob(level, zone, setting);
					aliveInZone++;
				}
			}
		}

		SPAWNED_MOBS.values().removeIf(mob -> mob == null || !isMobAlive(server, mob.uuid));

		if (tickCounter % 20 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				showStatus(player);
			}
		}
		if (tickCounter % 10 == 0) {
			showHuntingBorders(server);
		}
	}

	private static void spawnMob(ServerLevel level, Zone zone, MobSetting setting) {
		if (setting.spawnPoints == null || setting.spawnPoints.isEmpty()) {
			return;
		}
		Identifier entityId = Identifier.tryParse(setting.entity);
		if (entityId == null) {
			MyFirstMod.LOGGER.warn("Unknown hunting monster entity id: {}", setting.entity);
			return;
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(entityId);
		if (type == null) {
			MyFirstMod.LOGGER.warn("Unregistered hunting monster entity id: {}", setting.entity);
			return;
		}
		List<SpawnPoint> inZone = setting.spawnPoints.stream()
				.filter(point -> zone.contains(point.x + 0.5D, point.y, point.z + 0.5D))
				.toList();
		if (inZone.isEmpty()) {
			MyFirstMod.LOGGER.warn("No hunting spawn point is inside zone {}", zone.id);
			return;
		}
		SpawnPoint point = inZone.get(level.getRandom().nextInt(inZone.size()));
		BlockPos position = new BlockPos(point.x, point.y, point.z);
		Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (!(entity instanceof LivingEntity living)) {
			return;
		}
		living.snapTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D,
				level.getRandom().nextFloat() * 360.0F, 0.0F);
		living.setCustomName(Component.literal("Lv." + setting.monsterLevel + " " + setting.entity));
		living.setCustomNameVisible(true);
		if (level.addFreshEntity(living)) {
			SPAWNED_MOBS.put(living.getUUID(), new SpawnedMob(zone, setting, living.getUUID()));
		}
	}

	private static ServerLevel findLevel(MinecraftServer server, String dimension) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension().identifier().toString().equals(dimension)) {
				return level;
			}
		}
		return null;
	}

	private static boolean isMobAlive(MinecraftServer server, UUID uuid) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntityInAnyDimension(uuid) instanceof LivingEntity living && living.isAlive()) {
				return true;
			}
		}
		return false;
	}

	private static MobSetting findMatchingZoneSetting(LivingEntity victim) {
		Zone zone = findZoneFor(victim);
		if (zone == null) {
			return null;
		}
		String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
		for (MobSetting setting : zone.mobSettings) {
			if (setting.entity.equals(entityId)) {
				return setting;
			}
		}
		return null;
	}

	private static Zone findZoneFor(LivingEntity victim) {
		if (!(victim.level() instanceof ServerLevel level)) {
			return null;
		}
		String dimension = level.dimension().identifier().toString();
		for (Zone zone : CONFIG.zones) {
			if (zone.dimension.equals(dimension)
					&& zone.contains(victim.getX(), victim.getY(), victim.getZ())) {
				return zone;
			}
		}
		return null;
	}

	private static void giveExperienceToPlayersInZone(LivingEntity victim,
			Zone zone, MobSetting setting) {
		if (!(victim.level() instanceof ServerLevel level)
				|| !zone.dimension.equals(level.dimension().identifier().toString())) {
			return;
		}

		int rewardedPlayers = 0;
		for (ServerPlayer player : level.players()) {
			if (player.isAlive()
					&& zone.contains(player.getX(), player.getY(), player.getZ())) {
				long gained = calculateExperience(player, setting);
				giveExperience(player, gained);
				rewardedPlayers++;
				MyFirstMod.LOGGER.info("Hunting kill in {} rewarded {} with {} EXP",
						zone.id, player.getName().getString(), gained);
			}
		}
		if (rewardedPlayers == 0) {
			MyFirstMod.LOGGER.debug("Hunting mob died in zone {} with no player inside",
					zone.id);
		}
	}

	private static void giveExperience(ServerPlayer player, long gained) {
		if (gained <= 0L) {
			return;
		}
		HuntingProgressData data = getData(((ServerLevel) player.level()).getServer());
		HuntingProgressData.PlayerProgress progress = data.getOrCreate(player.getUUID());

		int maxLevel = CONFIG.maxLevel;
		long experience = progress.experience() + gained;
		int level = progress.level();
		while (maxLevel == 0 || level < maxLevel) {
			long required = requiredExperience(level);
			if (experience < required) {
				break;
			}
			experience -= required;
			level++;
		}

		progress.set(level, experience);
		data.markChanged();
		player.sendSystemMessage(Component.literal("+" + gained + " hunting EXP"), false);
		showStatus(player);
	}

	private static long requiredExperience(int level) {
		return CONFIG.requiredExperienceBase
				+ (long) Math.max(0, level - 1) * CONFIG.requiredExperiencePerLevel;
	}

	private static long calculateExperience(ServerPlayer player, MobSetting setting) {
		HuntingProgressData data = getData(((ServerLevel) player.level()).getServer());
		int playerLevel = data.getOrCreate(player.getUUID()).level();
		int difference = setting.monsterLevel - playerLevel;
		double multiplier = 1.0D + difference * CONFIG.levelDifferenceExpPerLevel;
		multiplier = Math.max(CONFIG.levelDifferenceExpMinimum, multiplier);
		return Math.max(0L, Math.round(setting.baseExperience * multiplier));
	}

	private static void showStatus(ServerPlayer player) {
		HuntingProgressData data = getData(((ServerLevel) player.level()).getServer());
		HuntingProgressData.PlayerProgress progress = data.getOrCreate(player.getUUID());
		long required = requiredExperience(progress.level());
		double percent = required <= 0L ? 0.0D
				: Math.min(100.0D, progress.experience() * 100.0D / required);
		player.sendSystemMessage(Component.literal(
				String.format("Hunting Lv.%d  EXP %.1f%% (%d / %d)",
						progress.level(), percent, progress.experience(), required)), true);
	}

	private static void showHuntingBorders(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			String dimension = level.dimension().identifier().toString();
			for (Zone zone : CONFIG.zones) {
				if (!zone.dimension.equals(dimension) || zone.min == null || zone.max == null) {
					continue;
				}
				double centerX = (zone.min.x + zone.max.x) * 0.5D;
				double centerZ = (zone.min.z + zone.max.z) * 0.5D;
				if (player.distanceToSqr(centerX,
						(zone.min.y + zone.max.y) * 0.5D, centerZ) <= 256.0D * 256.0D) {
					drawZoneBorder(level,
							new BlockPos(zone.min.x, zone.min.y, zone.min.z),
							new BlockPos(zone.max.x, zone.max.y, zone.max.z));
				}
			}
			BlockPos first = SELECTIONS.get(player.getUUID());
			if (first != null) {
				HitResult hit = player.pick(128.0D, 1.0F, false);
				if (hit instanceof BlockHitResult blockHit) {
					BlockPos[] expanded = expandSelectedCorners(first, blockHit.getBlockPos());
					drawZoneBorder(level, expanded[0], expanded[1]);
				}
			}
		}
	}

	private static BlockPos[] expandSelectedCorners(BlockPos first, BlockPos second) {
		int minX = Math.min(first.getX(), second.getX());
		int minY = Math.min(first.getY(), second.getY());
		int minZ = Math.min(first.getZ(), second.getZ());
		int maxX = Math.max(first.getX(), second.getX());
		int maxY = Math.max(first.getY(), second.getY());
		int maxZ = Math.max(first.getZ(), second.getZ());
		if (minX == maxX) {
			maxX++;
		}
		if (minZ == maxZ) {
			maxZ++;
		}

		int widthX = maxX - minX + 1;
		int widthZ = maxZ - minZ + 1;
		double centerX = (minX + maxX) * 0.5D;
		double centerZ = (minZ + maxZ) * 0.5D;
		int expandedMinX = (int) Math.floor(centerX - widthX * 1.5D);
		int expandedMaxX = (int) Math.ceil(centerX + widthX * 1.5D);
		int expandedMinZ = (int) Math.floor(centerZ - widthZ * 1.5D);
		int expandedMaxZ = (int) Math.ceil(centerZ + widthZ * 1.5D);
		return new BlockPos[]{
				new BlockPos(expandedMinX, minY, expandedMinZ),
				new BlockPos(expandedMaxX, maxY, expandedMaxZ)
		};
	}

	private static void drawZoneBorder(ServerLevel level, BlockPos first, BlockPos second) {
		int x1 = Math.min(first.getX(), second.getX());
		int y1 = Math.min(first.getY(), second.getY());
		int z1 = Math.min(first.getZ(), second.getZ());
		int x2 = Math.max(first.getX(), second.getX());
		int y2 = Math.max(first.getY(), second.getY());
		int z2 = Math.max(first.getZ(), second.getZ());

		drawLine(level, x1, y1, z1, x2, y1, z1);
		drawLine(level, x1, y1, z2, x2, y1, z2);
		drawLine(level, x1, y1, z1, x1, y1, z2);
		drawLine(level, x2, y1, z1, x2, y1, z2);
		drawLine(level, x1, y2, z1, x2, y2, z1);
		drawLine(level, x1, y2, z2, x2, y2, z2);
		drawLine(level, x1, y2, z1, x1, y2, z2);
		drawLine(level, x2, y2, z1, x2, y2, z2);
		drawLine(level, x1, y1, z1, x1, y2, z1);
		drawLine(level, x2, y1, z1, x2, y2, z1);
		drawLine(level, x1, y1, z2, x1, y2, z2);
		drawLine(level, x2, y1, z2, x2, y2, z2);
	}

	private static void drawLine(ServerLevel level,
			double x1, double y1, double z1,
			double x2, double y2, double z2) {
		double distance = Math.max(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)), Math.abs(z2 - z1));
		int points = (int) Math.ceil(distance);
		for (int i = 0; i <= points; i++) {
			double ratio = points == 0 ? 0.0D : (double) i / points;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
					x1 + (x2 - x1) * ratio + 0.5D,
					y1 + (y2 - y1) * ratio + 0.5D,
					z1 + (z2 - z1) * ratio + 0.5D,
					1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
	}

	private static HuntingProgressData getData(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(HuntingProgressData.TYPE);
	}

	public static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("my-first-mod").resolve("hunting.json");
	}

	private static void loadConfig() {
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			if (Files.notExists(path)) {
				Files.writeString(path, GSON.toJson(defaultConfig()), StandardCharsets.UTF_8);
			}
			CONFIG = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Config.class);
			if (CONFIG == null) {
				CONFIG = defaultConfig();
			}
			if (CONFIG.maxZoneMobCount <= 0) {
				CONFIG.maxZoneMobCount = 20;
			}
			for (Zone zone : CONFIG.zones) {
				for (MobSetting setting : zone.mobSettings) {
					setting.maxCount = Math.min(20, Math.max(0, setting.maxCount));
				}
			}
			MyFirstMod.LOGGER.info("Loaded hunting configuration from {}", path);
		} catch (IOException exception) {
			MyFirstMod.LOGGER.error("Could not load hunting configuration", exception);
			CONFIG = defaultConfig();
		}
	}

	private static void saveConfig() {
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(CONFIG), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			MyFirstMod.LOGGER.error("Could not save hunting configuration", exception);
		}
	}

	private static String posToString(Vec3i pos) {
		return pos.x + "," + pos.y + "," + pos.z;
	}

	private static String posToString(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static Zone createZone(ServerPlayer player, BlockPos first, BlockPos second) {
		Vec3i selectedMin = new Vec3i(
				Math.min(first.getX(), second.getX()),
				Math.min(first.getY(), second.getY()),
				Math.min(first.getZ(), second.getZ()));
		Vec3i selectedMax = new Vec3i(
				Math.max(first.getX(), second.getX()),
				Math.max(first.getY(), second.getY()),
				Math.max(first.getZ(), second.getZ()));
		if (selectedMin.x == selectedMax.x) {
			selectedMax.x++;
		}
		if (selectedMin.z == selectedMax.z) {
			selectedMax.z++;
		}

		int selectedWidthX = selectedMax.x - selectedMin.x + 1;
		int selectedWidthZ = selectedMax.z - selectedMin.z + 1;
		double centerX = (selectedMin.x + selectedMax.x) * 0.5D;
		double centerZ = (selectedMin.z + selectedMax.z) * 0.5D;
		double halfWidthX = selectedWidthX * 1.5D;
		double halfWidthZ = selectedWidthZ * 1.5D;
		Vec3i min = new Vec3i(
				(int) Math.floor(centerX - halfWidthX),
				selectedMin.y,
				(int) Math.floor(centerZ - halfWidthZ));
		Vec3i max = new Vec3i(
				(int) Math.ceil(centerX + halfWidthX),
				selectedMax.y,
				(int) Math.ceil(centerZ + halfWidthZ));

		Zone zone = new Zone();
		zone.id = "zone_" + System.currentTimeMillis();
		zone.dimension = player.level().dimension().identifier().toString();
		zone.min = min;
		zone.max = max;

		MobSetting template = CONFIG.zones.stream()
				.flatMap(existing -> existing.mobSettings.stream())
				.findFirst().orElseGet(() -> {
					MobSetting setting = new MobSetting();
					setting.entity = "minecraft:zombie";
					setting.maxCount = 20;
					setting.respawnTicks = 200;
					setting.monsterLevel = 10;
					setting.baseExperience = 20L;
					return setting;
				});
		MobSetting setting = new MobSetting();
		setting.entity = template.entity;
		setting.maxCount = Math.min(20, template.maxCount);
		setting.respawnTicks = template.respawnTicks;
		setting.monsterLevel = template.monsterLevel;
		setting.baseExperience = template.baseExperience;

		int spawnY = (min.y + max.y) / 2;
		List<SpawnPoint> spawnPoints = new ArrayList<>();
		int columns = Math.max(1, Math.min(5, max.x - min.x + 1));
		int rows = Math.max(1, Math.min(4, max.z - min.z + 1));
		for (int i = 0; i < 20; i++) {
			int x = min.x + (columns == 1 ? 0 : (i % columns) * (max.x - min.x) / (columns - 1));
			int z = min.z + (rows == 1 ? 0 : (i / columns) % rows * (max.z - min.z) / (rows - 1));
			spawnPoints.add(new SpawnPoint(x, spawnY, z));
		}
		setting.spawnPoints = spawnPoints;
		zone.mobSettings = List.of(setting);
		return zone;
	}

	private static void removeZoneRuntimeEntries(MinecraftServer server, Zone zone) {
		RESPAWNS.removeIf(entry -> entry.zone.id.equals(zone.id));
		Iterator<Map.Entry<UUID, SpawnedMob>> iterator = SPAWNED_MOBS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, SpawnedMob> entry = iterator.next();
			if (!entry.getValue().zone.id.equals(zone.id)) {
				continue;
			}
			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntityInAnyDimension(entry.getKey()) instanceof LivingEntity living) {
					living.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
				}
			}
			iterator.remove();
		}
	}

	private static Zone findZoneFor(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		String dimension = level.dimension().identifier().toString();
		for (Zone zone : CONFIG.zones) {
			if (zone.dimension.equals(dimension)
					&& zone.contains(player.getX(), player.getY(), player.getZ())) {
				return zone;
			}
		}
		return null;
	}

	private static Config defaultConfig() {
		Config config = new Config();
		config.maxLevel = 0;
		config.maxZoneMobCount = 20;
		config.requiredExperienceBase = 100L;
		config.requiredExperiencePerLevel = 50L;
		config.levelDifferenceExpPerLevel = 0.0D;
		config.levelDifferenceExpMinimum = 0.0D;
		config.zones = List.of();
		return config;
	}

	private record SpawnedMob(Zone zone, MobSetting setting, UUID uuid) {
	}

	private record RespawnEntry(Zone zone, MobSetting setting, int respawnAtTick) {
	}

	private static final class Config {
		int maxLevel;
		int maxZoneMobCount = 20;
		long requiredExperienceBase;
		long requiredExperiencePerLevel;
		double levelDifferenceExpPerLevel;
		double levelDifferenceExpMinimum;
		List<Zone> zones = List.of();
	}

	private static final class Zone {
		String id;
		String dimension;
		Vec3i min;
		Vec3i max;
		List<MobSetting> mobSettings = List.of();

		boolean contains(double x, double y, double z) {
			return min != null && max != null
					&& x >= Math.min(min.x, max.x) && x <= Math.max(min.x, max.x)
					&& z >= Math.min(min.z, max.z) && z <= Math.max(min.z, max.z);
		}
	}

	private static final class MobSetting {
		String entity;
		int maxCount;
		int respawnTicks;
		int monsterLevel;
		long baseExperience;
		List<SpawnPoint> spawnPoints = List.of();
	}

	private static final class Vec3i {
		int x;
		int y;
		int z;

		Vec3i(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	private static final class SpawnPoint {
		int x;
		int y;
		int z;

		SpawnPoint(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}
}
