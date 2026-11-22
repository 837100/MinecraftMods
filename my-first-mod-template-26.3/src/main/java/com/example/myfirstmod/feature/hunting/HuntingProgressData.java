package com.example.myfirstmod.feature.hunting;

import com.example.myfirstmod.MyFirstMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HuntingProgressData extends SavedData {
	public static final class PlayerProgress {
		public static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.optionalFieldOf("level", 1).forGetter(progress -> progress.level),
				Codec.LONG.optionalFieldOf("experience", 0L).forGetter(progress -> progress.experience)
		).apply(instance, PlayerProgress::new));

		private int level;
		private long experience;

		public PlayerProgress() {
			this(1, 0L);
		}

		public PlayerProgress(int level, long experience) {
			this.level = Math.max(1, level);
			this.experience = Math.max(0L, experience);
		}

		public int level() {
			return level;
		}

		public long experience() {
			return experience;
		}

		public void set(int level, long experience) {
			this.level = level;
			this.experience = experience;
		}
	}

	public static final Codec<HuntingProgressData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerProgress.CODEC)
					.optionalFieldOf("players", Map.of())
					.forGetter(data -> data.players)
	).apply(instance, HuntingProgressData::new));

	public static final SavedDataType<HuntingProgressData> TYPE = new SavedDataType<>(
			MyFirstMod.id("hunting_progress"),
			HuntingProgressData::new,
			CODEC,
			DataFixTypes.LEVEL
	);

	private final Map<UUID, PlayerProgress> players;

	public HuntingProgressData() {
		this(Map.of());
	}

	public HuntingProgressData(Map<UUID, PlayerProgress> players) {
		this.players = new HashMap<>(players);
	}

	public PlayerProgress getOrCreate(UUID playerUuid) {
		return players.computeIfAbsent(playerUuid, ignored -> new PlayerProgress());
	}

	public void markChanged() {
		setDirty();
	}

	public Map<UUID, PlayerProgress> players() {
		return players;
	}
}
