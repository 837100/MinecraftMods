# Storm Sovereign 구현 설명

아래 파일은 **`MyFirstMod.java`**와 **`STICK_LIGHTNING.md`**에서 찾은 Storm Sovereign 보스 구현을 정리한 문서입니다. 프로젝트에 바로 넣어 사용하세요.

## 1. 개요
Storm Sovereign는 네더 스타를 우클릭하면 보스가 소환되는 Wither 기반 보스입니다. 보스는 2단계(체력 50% 이상/50% 이하) 로 전개되며, 각 단계마다 번개 공격 빈도와 피해가 달라집니다.

## 2. 주요 파일 및 라인
| 파일 | 기능 | 주요 라인 |
|------|------|-----------|
| `MyFirstMod.java` | 보스 소환 및 트릭 구현 | 63‑81, 122‑225, 235‑282 |
| `STICK_LIGHTNING.md` | 보스 설명 | 31‑38 |

## 3. 보스 소환
```java
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
        ...
    }
    ...
});
```
* 보스는 `WitherBoss` 클래스를 사용해 생성됩니다.
* 소환 위치는 플레이어가 바라보는 지점(`player.pick(32.0D,…)`).
* `ACTIVE_STORM_BOSSES` 맵에 UUID를 키로 저장해 추적합니다.

## 4. 보스 상태 관리
### 4‑1. 상태 구조
```java
private static final class StormBossState {
    private int ticks; // 타이머
}
```
### 4‑2. 서버 타이클 처리
```java
ServerTickEvents.END_SERVER_TICK.register(MyFirstMod::tickStormBosses);
```
```java
private static void tickStormBosses(MinecraftServer server) {
    Set<UUID> aliveBosses = new HashSet<>();
    for (ServerLevel level : server.getAllLevels()) {
        for (Map.Entry<UUID, StormBossState> entry : ACTIVE_STORM_BOSSES.entrySet()) {
            UUID bossId = entry.getKey();
            WitherBoss boss = findWither(level, bossId);
            if (boss == null || !boss.isAlive()) continue;
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
```
* **`ACTIVE_STORM_BOSSES`** 로 보스를 추적하고, 각 tick마다 타이머를 증가시킵니다.
* 체력 비율에 따라 **phaseTwo** 를 결정하고, `strikeInterval` 을 설정합니다.
* 타이머가 interval 도달하면 `createStormAttack()` 를 호출합니다.

## 5. 번개 공격 구현
```java
private static void createStormAttack(ServerLevel level, WitherBoss boss, boolean phaseTwo) {
    LevelTarget target = findStormTarget(level, boss);
    if (target == null) return;

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
        if (distance > radius) continue;

        victim.hurt(level.damageSources().magic(), damage);
        Vec3 push = victim.position().subtract(boss.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
        if (push.lengthSqr() < 1.0E-6D) push = new Vec3(0.0D, 0.0D, 1.0D);
        victim.push(push.x * (phaseTwo ? 1.2D : 0.8D), 0.55D, push.z * (phaseTwo ? 1.2D : 0.8D));
    }

    level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0F, 0.7F);
}
```
* **target**: 가장 가까운 플레이어 위치를 사용합니다.
* `strikes`: phase I → 1, phase II → 3.
* **damage**: phase I → 5, phase II → 8.
* **spread**: 공격 포인트가 얼마나 퍼질지 결정.
* 번개(엔티티)와 전기 스파크 파티클을 생성합니다.
* 주변 플레이어에게 마법 피해와 넉백을 줍니다.

## 6. 타깃 선택
```java
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
```
* 보스와 가장 가까운 살아있는 플레이어를 찾아서 공격 대상 좌표를 반환합니다.

## 7. 추가 참고
* **공격 간격**은 `STORM_STRIKE_INTERVAL`(100 tick = 5 s)과 `50 tick = 2.5 s` 로 조정됩니다.
* **거리에 따라** 1회 또는 3회의 번개가 동시에 발생합니다.
* **플레이어가 50% 이하 체력에 도달하면** 피해와 공격 빈도가 상승합니다.

