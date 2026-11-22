# 사냥 시스템

바닐라 경험치와 별개로 레벨과 경험치를 관리하는 메이플스토리식 사냥 기능이다.

## 주요 기능

- 좌표 기반 사냥터 생성 및 취소
- 네더의 별 우클릭을 통한 사냥터 범위 지정
- 선택한 사냥터의 X/Z 범위를 약 3배로 확장
- Y 높이 제한 없이 사냥터 판정
- 전기 스파크 파티클로 사냥터 경계와 선택 중인 범위를 시각화
- 사냥터별 몬스터 종류, 최대 마릿수, 리젠 시간, 몬스터 레벨, 경험치 설정
- 사냥터 안에서 몹이 사망하면 구역 안의 모든 생존 플레이어에게 경험치 지급
- 근접, 원거리, 번개 등 처치 수단에 관계없는 구역 기반 보상
- 바닐라 경험치와 분리된 레벨 및 경험치 저장
- 서버 재시작 후에도 사냥 레벨과 경험치 유지

## 사냥터 지정

네더의 별을 들고 반대 모서리를 바라보며 우클릭한다.

1. 첫 우클릭: 첫 번째 모서리 지정
2. 두 번째 우클릭: 반대 모서리 지정
3. 전기 스파크 선으로 선택 중인 범위와 완성된 사냥터를 표시
4. 웅크린 채팅 우클릭: 진행 중인 선택 또는 서 있는 사냥터 취소

선택 좌표의 X/Z 폭은 약 3배로 확장된다. Y축은 사냥터 판정에 제한을 두지 않는다.

```text
예: 선택한 X/Z 폭이 10이면 완성된 사냥터 폭은 약 30
```

## 몬스터 제한

- 사냥터당 모드 스폰 몬스터는 최대 20마리
- 개별 몬스터 설정의 `maxCount`도 최대 20마리
- 몹 사망 후 `respawnTicks`가 지나면 사냥터 스폰 지점에서 다시 스폰
- `respawnTicks`는 20틱당 1초
- 사냥터 취소 시 해당 구역의 스폰 몹과 리젠 예약 제거

## 경험치 규칙

몬스터 사망 위치가 설정된 사냥터 안에 있으면 해당 구역 안의 모든 생존 플레이어에게 경험치를 지급한다.

- 킬러 엔티티나 번개 소유자에 의존하지 않음
- 근접, 원거리, 번개, 소환체 처치에 공통 적용
- 구역 밖에서 사망한 몹은 경험치 미지급
- 몬스터 레벨과 플레이어 레벨 차이로 경험치를 보정

최종 경험치 계산식은 다음과 같다.

```text
최종 경험치 =
round(몬스터 기본 경험치 × (1 + 몬스터레벨차 × 레벨차배율))
```

최종 경험치는 `levelDifferenceExpMinimum` 이상으로 제한된다.

레벨업 필요 경험치는 다음과 같다.

```text
필요 경험치 =
requiredExperienceBase
+ (현재 레벨 - 1) × requiredExperiencePerLevel
```

필요 경험치를 초과한 경험치는 다음 레벨로 이월된다.

## 설정 파일

서버 시작 시 다음 위치에 설정 파일을 생성한다.

```text
config/my-first-mod/hunting.json
```

예시 설정:

```json
{
  "maxLevel": 0,
  "maxZoneMobCount": 20,
  "requiredExperienceBase": 100,
  "requiredExperiencePerLevel": 50,
  "levelDifferenceExpPerLevel": 0.0,
  "levelDifferenceExpMinimum": 0.0,
  "zones": []
}
```

사냥터 예시:

```json
{
  "id": "hunting_1",
  "dimension": "minecraft:overworld",
  "min": {
    "x": 0,
    "y": 64,
    "z": 0
  },
  "max": {
    "x": 20,
    "y": 80,
    "z": 20
  },
  "mobSettings": [
    {
      "entity": "minecraft:zombie",
      "maxCount": 20,
      "respawnTicks": 200,
      "monsterLevel": 10,
      "baseExperience": 20,
      "spawnPoints": [
        {
          "x": 10,
          "y": 64,
          "z": 10
        }
      ]
    }
  ]
}
```

설정 항목:

- `maxLevel`: 최대 사냥 레벨, `0`은 무제한
- `requiredExperienceBase`: 레벨업 기준 경험치
- `requiredExperiencePerLevel`: 레벨별 추가 필요 경험치
- `levelDifferenceExpPerLevel`: 몬스터와 플레이어 레벨 차이 배율
- `levelDifferenceExpMinimum`: 경험치 배율 하한
- `maxZoneMobCount`: 사냥터당 최대 스폰 몹 수
- `zones`: 사냥터 목록
- `dimension`: 마인크래프트 차원 식별자
- `min`, `max`: 사냥터의 선택 및 저장 좌표
- `entity`: 몬스터 엔티티 식별자
- `maxCount`: 몬스터 최대 마릿수
- `respawnTicks`: 처치 후 리젠 틱
- `monsterLevel`: 몬스터 사냥 레벨
- `baseExperience`: 몬스터 기본 경험치
- `spawnPoints`: 스폰 좌표 목록

## 명령어

- `/hunting status`: 현재 사냥 레벨과 경험치 백분율
- `/hunting setlevel <level>`: 테스트용 레벨 설정 및 경험치 초기화
- `/hunting reload`: 설정 다시 불러오기
- `/hunting debug`: 레벨, 경험치, 추적 몹 수, 리젠 수, 사냥터 수 확인
- `/hunting list`: 설정된 사냥터 ID 목록
- `/hunting cancel`: 서 있는 사냥터 또는 진행 중 선택 취소
- `/hunting cancel <zoneId>`: 지정한 사냥터 삭제

## 저장

플레이어 사냥 레벨과 경험치는 세계 데이터에 저장된다.

```text
run/saves/<세계>/data/my-first-mod/hunting_progress.dat
```

실제 서버에서는 해당 세계의 `data/my-first-mod/hunting_progress.dat`에 저장된다.
사냥터 설정은 `config/my-first-mod/hunting.json`에 저장된다.

액션바는 1초마다 현재 레벨과 경험치 백분율을 갱신한다.

## 관련 코드

- `src/main/java/com/example/myfirstmod/MyFirstMod.java`
- `src/main/java/com/example/myfirstmod/feature/hunting/HuntingFeature.java`
- `src/main/java/com/example/myfirstmod/feature/hunting/HuntingProgressData.java`
- `src/main/java/com/example/myfirstmod/feature/hunting/KillerAttribution.java`
- `src/main/java/com/example/myfirstmod/feature/lightning/LightningStickFeature.java`

## 빌드 및 설치

Java 25, Fabric Loader 0.20.1 이상, Fabric API 0.166.2+26.3 이상이 필요하다.

```bash
cd /Users/macminim4/Documents/MinecraftMods/my-first-mod-template-26.3
./gradlew build
```

생성된 설치용 jar:

```text
build/libs/my-first-mod-1.0.0.jar
```

이 jar를 서버의 `mods/` 폴더에 넣고 서버를 재시작한다.
