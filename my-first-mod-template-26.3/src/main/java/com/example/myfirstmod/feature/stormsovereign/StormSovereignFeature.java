package com.example.myfirstmod.feature.stormsovereign;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StormSovereignFeature {
	private static final Map<UUID, StormBossState> ACTIVE_STORM_BOSSES = new HashMap<>();
	private static final int STORM_STRIKE_INTERVAL = 100;

	private StormSovereignFeature() {
	}

	public static void initialize() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.isEmpty()
					|| !stack.is(Items.NETHER_STAR)
					|| level.isClientSide()) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer)) {
				return InteractionResult.PASS;
			}

			ServerLevel serverLevel = (ServerLevel) level;
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
		});

		ServerTickEvents.END_SERVER_TICK.register(StormSovereignFeature::tickStormBosses);
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

	private record LevelTarget(Vec3 position) {
	}

	private static final class StormBossState {
		private int ticks;
	}
}
