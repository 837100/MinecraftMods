package com.example.myfirstmod.feature.staticaura;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StaticAuraFeature {
	private static final double AURA_RADIUS = 3.0D;
	private static final float AURA_DAMAGE = 1.0F;
	private static final int DAMAGE_INTERVAL_TICKS = 10;
	private static final int PARTICLE_POINTS = 12;

	private static final double AURA_RADIUS_SQUARED = AURA_RADIUS * AURA_RADIUS;
	private static final Map<UUID, StaticAuraState> ACTIVE_AURAS = new HashMap<>();

	private StaticAuraFeature() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(StaticAuraFeature::tickAuras);
	}

	private static void tickAuras(MinecraftServer server) {
		Set<UUID> onlinePlayers = new HashSet<>();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID playerId = player.getUUID();
			onlinePlayers.add(playerId);

			if (!player.isAlive()) {
				continue;
			}

			StaticAuraState state = ACTIVE_AURAS.computeIfAbsent(
					playerId,
					uuid -> new StaticAuraState());
			state.ticks++;

			spawnAuraParticles(player, state.ticks);
			if (state.ticks >= DAMAGE_INTERVAL_TICKS) {
				state.ticks = 0;
				damageNearbyEntities(player);
			}
		}

		ACTIVE_AURAS.keySet().retainAll(onlinePlayers);
	}

	private static void spawnAuraParticles(ServerPlayer player, int ticks) {
		ServerLevel level = player.level();
		Vec3 position = player.position();
		double particleY = position.y + 0.15D;
		double angleOffset = ticks * Math.PI / PARTICLE_POINTS;

		for (int i = 0; i < PARTICLE_POINTS; i++) {
			double angle = (Math.PI * 2.0D * i) / PARTICLE_POINTS + angleOffset;
			double offsetX = Math.cos(angle) * AURA_RADIUS;
			double offsetZ = Math.sin(angle) * AURA_RADIUS;

			level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
					position.x + offsetX, particleY, position.z + offsetZ,
					1, 0.02D, 0.05D, 0.02D, 0.01D);
		}
	}

	private static void damageNearbyEntities(ServerPlayer player) {
		ServerLevel level = player.level();
		AABB searchArea = player.getBoundingBox().inflate(AURA_RADIUS);

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
				searchArea,
				entity -> entity != player && entity.isAlive())) {
			if (target.distanceToSqr(player) > AURA_RADIUS_SQUARED) {
				continue;
			}

			target.hurt(level.damageSources().magic(), AURA_DAMAGE);
		}
	}

	private static final class StaticAuraState {
		private int ticks;
	}
}
