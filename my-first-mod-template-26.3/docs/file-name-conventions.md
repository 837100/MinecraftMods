# 파일 이름 컨벤션

이 프로젝트의 문서와 소스 파일 이름은 협업 시 충돌과 혼선을 줄이기 위해 아래 규칙을 따릅니다.

## Markdown

- 소문자와 하이픈(`kebab-case`)만 사용합니다.
- 예: `lightning-stick.md`, `storm-sovereign.md`
- 프로젝트 개요 문서는 저장소 루트의 `README.md`에 둡니다.
- 기능별 설계 문서는 `docs/features/`에 둡니다.
- 문서 인덱스는 `docs/README.md`, 컨벤션 문서는 `docs/file-name-conventions.md`로 둡니다.
- 기존 기능을 설명하는 문서가 있으면 한 기능당 하나의 Markdown 파일로 관리합니다.
- 하나의 기능이 여러 구현 단계를 거치더라도 이름 뒤에 날짜나 숫자를 붙이지 않고 대표 기능명만 사용합니다.

## Java

- 패키지는 기능 단위로 분리합니다: `feature/<feature-name>`.
- 기능의 진입 클래스는 `<FeatureName>Feature` 형식을 사용합니다.
- 예: `LightningStickFeature`, `ZeusLeapFeature`, `StormSovereignFeature`.
- 기능별 패키지는 모두 소문자이며 단어 사이에 하이픈 대신 카멜 표기와 Java 패키지 규칙을 사용합니다.
- 공통 로직은 기능을 서로 의존하게 만들지 말고 별도의 공통 패키지에 둡니다.

## 협업 원칙

- 같은 기능을 수정할 때는 기능 패키지와 기능 설명 문서만 먼저 확인합니다.
- 기능 간 공유가 필요한 상수나 유틸리티는 기존 파일에 직접 추가하지 않고 별도 공통 클래스나 패키지로 분리합니다.
- 새 기능 문서는 이 컨벤션을 적용한 뒤 `docs/README.md`에 링크를 추가합니다.
- 소스 작성 규칙은 [코드 컨벤션](code-conventions.md)을 따릅니다.
