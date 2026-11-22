package com.example.myfirstmod.feature.zeusleap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ZeusLeapFeature {
	private static final double LEAP_VERTICAL_SPEED = 3.2D;
	private static final double IMPACT_RADIUS = 5.0D;
	private static final float IMPACT_DAMAGE = 6.0F;
	private static final int LEAP_COOLDOWN_TICKS = 100;
	private static final int MAX_LEAP_TRACKING_TICKS = 200;

	private static final Map<UUID, LeapState> ACTIVE_LEAPS = new HashMap<>();
	private static final Map<UUID, Integer> LEAP_COOLDOWNS = new HashMap<>();

	private ZeusLeapFeature() {
	}

	public static void initialize() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.isEmpty()
					|| stack.getItem() != Items.BLAZE_ROD
					|| !player.isShiftKeyDown()
					|| level.isClientSide()) {
				return InteractionResult.PASS;
			}

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
		});

		ServerTickEvents.END_SERVER_TICK.register(ZeusLeapFeature::tickLeaps);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer)
						|| !isZeusDamage(source));
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

	private static final class LeapState {
		private boolean airborne;
		private int ticks;
	}
}
