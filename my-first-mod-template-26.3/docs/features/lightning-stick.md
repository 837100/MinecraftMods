# Lightning Stick

블레이즈 막대를 들고 조작하지 않은 채 우클릭하면 플레이어 시선이 닿는 위치에 번개를 생성합니다.

## 구현 위치

- [`LightningStickFeature.java`](../../src/main/java/com/example/myfirstmod/feature/lightning/LightningStickFeature.java)
- 공통 모드 진입점: [`MyFirstMod.java`](../../src/main/java/com/example/myfirstmod/MyFirstMod.java)

## 동작

1. 우클릭 아이템이 블레이즈 막대인지 확인합니다.
2. 웅크리기 상태가 아니고 서버 세계에서 실행 중인지 확인합니다.
3. `player.pick(128.0D, 1.0F, false)`로 조준 위치를 계산합니다.
4. `lightning_bolt` 엔티티를 생성해 월드에 추가합니다.

조준 대상이 없으면 조준 사거리의 끝 지점에 번개를 생성할 수 있습니다. 아이템 소모는 없습니다.
