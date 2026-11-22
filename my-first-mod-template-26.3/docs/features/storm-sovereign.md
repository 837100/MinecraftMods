# Storm Sovereign

네더의 별을 들고 우클릭하면 바라보는 지점에 위더 기반의 Storm Sovereign 보스를 소환합니다.

## 구현 위치

- [`StormSovereignFeature.java`](../../src/main/java/com/example/myfirstmod/feature/stormsovereign/StormSovereignFeature.java)
- 공통 모드 진입점: [`MyFirstMod.java`](../../src/main/java/com/example/myfirstmod/MyFirstMod.java)

## 동작

보스는 UUID로 추적되며 체력 비율에 따라 두 단계로 공격합니다.

- 체력 50% 이상: 5초마다 번개 1회
- 체력 50% 이하: 2.5초마다 번개 3회
- 공격 대상은 보스와 가장 가까운 살아 있는 서버 플레이어입니다.
- 1단계 피해는 5, 2단계 피해는 8이며 보스 주변에서 마법 피해와 넉백을 줍니다.
- 보스가 사라지면 추적 상태도 서버 틱 처리 과정에서 정리됩니다.
