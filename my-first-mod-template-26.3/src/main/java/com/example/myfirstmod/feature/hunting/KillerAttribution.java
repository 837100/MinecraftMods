package com.example.myfirstmod.feature.hunting;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class KillerAttribution {
	private static final Map<UUID, ServerPlayer> ENTITY_KILLERS = new HashMap<>();

	private KillerAttribution() {
	}

	public static void mark(Entity entity, ServerPlayer killer) {
		if (entity != null && killer != null) {
			ENTITY_KILLERS.put(entity.getUUID(), killer);
		}
	}

	public static void clear() {
		ENTITY_KILLERS.clear();
	}

	public static void cleanupRemovedEntities() {
		Iterator<Map.Entry<UUID, ServerPlayer>> iterator = ENTITY_KILLERS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, ServerPlayer> entry = iterator.next();
			if (entry.getValue() == null || !entry.getValue().isAlive()) {
				iterator.remove();
			}
		}
	}

	public static ServerPlayer resolve(LivingEntity victim, DamageSource source) {
		if (source != null) {
			if (source.getEntity() instanceof ServerPlayer player) {
				return player;
			}
			if (source.getDirectEntity() instanceof ServerPlayer player) {
				return player;
			}

			Entity directEntity = source.getDirectEntity();
			if (directEntity != null) {
				ServerPlayer marked = ENTITY_KILLERS.get(directEntity.getUUID());
				if (marked != null) {
					return marked;
				}
				if (directEntity instanceof net.minecraft.world.entity.LightningBolt lightning
						&& lightning.getCause() != null) {
					return lightning.getCause();
				}
			}

			Entity sourceEntity = source.getEntity();
			if (sourceEntity != null) {
				ServerPlayer marked = ENTITY_KILLERS.get(sourceEntity.getUUID());
				if (marked != null) {
					return marked;
				}
				if (sourceEntity instanceof net.minecraft.world.entity.LightningBolt lightning
						&& lightning.getCause() != null) {
					return lightning.getCause();
				}
			}
		}

		if (victim.getKillCredit() instanceof ServerPlayer player) {
			return player;
		}
		if (victim.getLastHurtByPlayer() instanceof ServerPlayer player) {
			return player;
		}
		return null;
	}
}
