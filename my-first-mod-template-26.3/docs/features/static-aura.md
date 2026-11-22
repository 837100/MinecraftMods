# Static Aura

접속 중인 모든 플레이어 주변에 지속적인 정전기 오라를 적용합니다.

## 구현 위치

- [`StaticAuraFeature.java`](../../src/main/java/com/example/myfirstmod/feature/staticaura/StaticAuraFeature.java)
- 공통 모드 진입점: [`MyFirstMod.java`](../../src/main/java/com/example/myfirstmod/MyFirstMod.java)

## 동작

- 서버 틱마다 접속 중인 플레이어를 순회합니다.
- 플레이어 중심 반경 3블록의 모든 살아 있는 LivingEntity를 피해 대상으로 감지합니다.
- 플레이어 본인은 제외됩니다.
- 10틱마다 대상당 마법 피해 1점을 적용합니다.
- 플레이어 주변을 회전하는 원형 전기 스파크 입자를 매 틱 생성합니다.
- 반경, 피해량, 피해 주기는 StaticAuraFeature 내부 상수로 조절할 수 있습니다.
