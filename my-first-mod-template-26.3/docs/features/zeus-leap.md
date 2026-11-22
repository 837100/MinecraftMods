# Zeus Leap

블레이즈 막대를 들고 웅크리기 상태에서 우클릭하면 위로 도약하고 착지 순간 충격파를 발생시킵니다.

## 구현 위치

- [`ZeusLeapFeature.java`](../../src/main/java/com/example/myfirstmod/feature/zeusleap/ZeusLeapFeature.java)
- 공통 모드 진입점: [`MyFirstMod.java`](../../src/main/java/com/example/myfirstmod/MyFirstMod.java)

## 동작

- 웅크리기와 서버 세계 확인 후 블레이즈 막대를 사용한 경우 도약합니다.
- 재사용 대기시간 없이 우클릭할 때마다 도약합니다.
- 공중 이동 추적 중 착지하면 번개와 구름 파티클을 생성합니다.
- 착지한 낙하 거리에 비례해 충격 범위와 피해가 증가합니다.
- 기본 충격 범위는 5블록이며 최대 128블록까지, 기본 피해는 6이며 최대 500까지 증가합니다.
- 번개, 낙하, 불 및 캠프파이어 계열 피해는 제우스 도약 관련 동작에서 예외 처리됩니다.
