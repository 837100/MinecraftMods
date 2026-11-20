package com.example.myfirstmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MyFirstMod implements ModInitializer {
	public static final String MOD_ID = "my-first-mod";
	private static final double LEAP_VERTICAL_SPEED = 3.2D;
	private static final double IMPACT_RADIUS = 5.0D;
	private static final float IMPACT_DAMAGE = 6.0F;
	private static final int LEAP_COOLDOWN_TICKS = 100;
	private static final int MAX_LEAP_TRACKING_TICKS = 200;

	private static final Map<UUID, LeapState> ACTIVE_LEAPS = new HashMap<>();
	private static final Map<UUID, Integer> LEAP_COOLDOWNS = new HashMap<>();
	private static final Map<UUID, StormBossState> ACTIVE_STORM_BOSSES = new HashMap<>();
	private static final int STORM_STRIKE_INTERVAL = 100;

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
			if (!stack.isEmpty() && stack.is(Items.NETHER_STAR) && !level.isClientSide()) {
				ServerLevel serverLevel = (ServerLevel) level;
				if (!(player instanceof ServerPlayer serverPlayer)) {
					return InteractionResult.PASS;
				}

				HitResult hitResult = player.pick(32.0D, 1.0F, false);
				Vec3 position = hitResult.getLocation();
				EntityType<? extends WitherBoss> witherType =
						(EntityType<? extends WitherBoss>) BuiltInRegistries.ENTITY_TYPE
								.getValue(Identifier.withDefaultNamespace("wither"));
				WitherBoss boss = new WitherBoss(witherType, serverLevel);
				boss.setPos(position.x, position.y, position.z);
				boss.setCustomName(Component.literal("Storm Sovereign"));
				boss.setCustomNameVisible(true);
				serverLevel.addFreshEntity(boss);
				ACTIVE_STORM_BOSSES.put(boss.getUUID(), new StormBossState());

				serverLevel.playSound(null, position.x, position.y, position.z,
						SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 0.8F);
				return InteractionResult.SUCCESS_SERVER;
			}

			if (stack.isEmpty() || stack.getItem() != Items.BLAZE_ROD || level.isClientSide()) {
				return InteractionResult.PASS;
			}

			ServerLevel serverLevel = (ServerLevel) level;
			if (player.isShiftKeyDown()) {
				if (!(player instanceof ServerPlayer serverPlayer)
						|| LEAP_COOLDOWNS.containsKey(serverPlayer.getUUID())) {
					return InteractionResult.SUCCESS_SERVER;
				}

				Vec3 velocity = serverPlayer.getDeltaMovement();
				serverPlayer.push(0.0D, LEAP_VERTICAL_SPEED - velocity.y, 0.0D);
				serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
				serverPlayer.resetFallDistance();
				ACTIVE_LEAPS.put(serverPlayer.getUUID(), new LeapState());
				LEAP_COOLDOWNS.put(serverPlayer.getUUID(), LEAP_COOLDOWN_TICKS);
				serverPlayer.playSound(SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.9F, 0.8F);
				return InteractionResult.SUCCESS_SERVER;
			}

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

		ServerTickEvents.END_SERVER_TICK.register(MyFirstMod::tickLeaps);
		ServerTickEvents.END_SERVER_TICK.register(MyFirstMod::tickStormBosses);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer)
						|| !isZeusDamage(source));

		LOGGER.info("My First Mod has been loaded successfully!");
		LOGGER.info("Welcome to the server! This is a message from your first mod (Log version).");
	}

	private static boolean isZeusDamage(DamageSource source) {
		return source.is(DamageTypes.LIGHTNING_BOLT)
				|| source.is(DamageTypes.FALL)
				|| source.is(DamageTypes.IN_FIRE)
				|| source.is(DamageTypes.ON_FIRE)
				|| source.is(DamageTypes.CAMPFIRE)
				|| source.is(DamageTypes.HOT_FLOOR);
	}

	private static void tickLeaps(MinecraftServer server) {
		LEAP_COOLDOWNS.replaceAll((uuid, ticks) -> ticks - 1);
		LEAP_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= 0);

		Set<UUID> onlinePlayers = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID playerId = player.getUUID();
			onlinePlayers.add(playerId);

			LeapState leap = ACTIVE_LEAPS.get(playerId);
			if (leap == null) {
				continue;
			}

			leap.ticks++;
			if (!leap.airborne) {
				leap.airborne = !player.onGround();
			} else if (player.onGround()) {
				createImpact(player);
				ACTIVE_LEAPS.remove(playerId);
			}

			if (leap.ticks > MAX_LEAP_TRACKING_TICKS) {
				ACTIVE_LEAPS.remove(playerId);
			}
		}

		ACTIVE_LEAPS.keySet().retainAll(onlinePlayers);
	}

	private static void createImpact(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 position = player.position();
		double radiusSquared = IMPACT_RADIUS * IMPACT_RADIUS;

		level.sendParticles(ParticleTypes.CLOUD, position.x, position.y + 0.1D, position.z,
				36, 1.8D, 0.08D, 1.8D, 0.08D);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, position.x, position.y + 0.2D, position.z,
				24, 1.5D, 0.4D, 1.5D, 0.12D);
		level.playSound(null, position.x, position.y, position.z,
				SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0F, 0.85F);

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(IMPACT_RADIUS, 2.0D, IMPACT_RADIUS),
				entity -> entity != player && entity.isAlive())) {
			if (target.distanceToSqr(position) > radiusSquared) {
				continue;
			}

			target.hurt(level.damageSources().playerAttack(player), IMPACT_DAMAGE);
			Vec3 pushDirection = target.position().subtract(position).multiply(1.0D, 0.0D, 1.0D).normalize();
			if (pushDirection.lengthSqr() < 1.0E-6D) {
				pushDirection = new Vec3(0.0D, 0.0D, 1.0D);
			}
			target.push(pushDirection.x * 0.85D, 0.4D, pushDirection.z * 0.85D);
		}
	}

	private static void tickStormBosses(MinecraftServer server) {
		Set<UUID> aliveBosses = new HashSet<>();

		for (ServerLevel level : server.getAllLevels()) {
			for (Map.Entry<UUID, StormBossState> entry : ACTIVE_STORM_BOSSES.entrySet()) {
				UUID bossId = entry.getKey();
				WitherBoss boss = findWither(level, bossId);
				if (boss == null || !boss.isAlive()) {
					continue;
				}

				aliveBosses.add(bossId);
				StormBossState state = entry.getValue();
				state.ticks++;

				float healthRatio = boss.getHealth() / boss.getMaxHealth();
				boolean phaseTwo = healthRatio <= 0.5F;
				int strikeInterval = phaseTwo ? 50 : STORM_STRIKE_INTERVAL;

				if (state.ticks >= strikeInterval) {
					state.ticks = 0;
					createStormAttack(level, boss, phaseTwo);
				}
			}
		}

		ACTIVE_STORM_BOSSES.keySet().retainAll(aliveBosses);
	}

	private static WitherBoss findWither(ServerLevel level, UUID bossId) {
		return level.getEntitiesOfClass(WitherBoss.class,
				new AABB(-30_000_000.0D, -64.0D, -30_000_000.0D,
						30_000_000.0D, 320.0D, 30_000_000.0D),
				entity -> entity.getUUID().equals(bossId) && entity.isAlive())
				.stream().findFirst().orElse(null);
	}

	private static void createStormAttack(ServerLevel level, WitherBoss boss, boolean phaseTwo) {
		LevelTarget target = findStormTarget(level, boss);
		if (target == null) {
			return;
		}

		int strikes = phaseTwo ? 3 : 1;
		float damage = phaseTwo ? 8.0F : 5.0F;
		double spread = phaseTwo ? 4.0D : 2.5D;

		for (int i = 0; i < strikes; i++) {
			double angle = (Math.PI * 2.0D * i) / strikes;
			double offsetX = Math.cos(angle) * spread;
			double offsetZ = Math.sin(angle) * spread;
			Vec3 strikePosition = target.position().add(offsetX, 0.0D, offsetZ);

			EntityType<? extends LightningBolt> lightningType =
					(EntityType<? extends LightningBolt>) BuiltInRegistries.ENTITY_TYPE
							.getValue(Identifier.withDefaultNamespace("lightning_bolt"));
			LightningBolt lightning = new LightningBolt(lightningType, level);
			lightning.setPos(strikePosition);
			level.addFreshEntity(lightning);

			level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
					strikePosition.x, strikePosition.y + 0.25D, strikePosition.z,
					16, 1.0D, 0.5D, 1.0D, 0.08D);
		}

		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
				boss.getBoundingBox().inflate(phaseTwo ? 18.0D : 14.0D),
				entity -> entity != boss && entity.isAlive())) {
			double distance = victim.distanceTo(boss);
			double radius = phaseTwo ? 18.0D : 14.0D;
			if (distance > radius) {
				continue;
			}

			victim.hurt(level.damageSources().magic(), damage);
			Vec3 push = victim.position().subtract(boss.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
			if (push.lengthSqr() < 1.0E-6D) {
				push = new Vec3(0.0D, 0.0D, 1.0D);
			}
			victim.push(push.x * (phaseTwo ? 1.2D : 0.8D), 0.55D, push.z * (phaseTwo ? 1.2D : 0.8D));
		}

		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
				SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0F, 0.7F);
	}

	private static LevelTarget findStormTarget(ServerLevel level, WitherBoss boss) {
		ServerPlayer nearest = null;
		double nearestDistance = Double.MAX_VALUE;

		for (ServerPlayer player : level.players()) {
			double distance = player.distanceToSqr(boss);
			if (player.isAlive() && distance < nearestDistance) {
				nearest = player;
				nearestDistance = distance;
			}
		}

		return nearest == null ? null : new LevelTarget(nearest.position());
	}

	private record LevelTarget(Vec3 position) {}

	private static final class StormBossState {
		private int ticks;
	}

	private static final class LeapState {
		private boolean airborne;
		private int ticks;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
