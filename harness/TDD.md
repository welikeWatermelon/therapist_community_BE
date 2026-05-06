# TDD Guide

## 기본 루프

1. 먼저 실패하는 테스트를 만든다. 버그면 재현 테스트, 기능이면 기대 동작 테스트를 먼저 둔다.
2. 가장 가까운 레벨에서 고친다. 도메인 규칙은 domain/service 테스트, 직렬화나 검증은 controller 테스트부터 간다.
3. 필요한 만큼만 구현한다. 테스트를 통과시킨 뒤 중복만 정리한다.

## 이 저장소의 테스트 레벨

- service/domain: JUnit 5 + Mockito 기반 단위 테스트가 기본이다.
- controller: `MockMvcBuilders.standaloneSetup(...)` + `MockitoExtension` 패턴을 따른다.
- repository/query: 커서, 검색, 정렬, native SQL은 `@DataJpaTest` 로 검증한다.
- full context: wiring 문제가 아니면 `@SpringBootTest` 는 기본 선택지가 아니다.

## 작성 규칙

- 테스트 이름은 한국어 시나리오 문장으로 쓴다.
- `given / when / then` 구조를 유지한다.
- 비동기 로직은 `Thread.sleep` 대신 publisher, listener, 상태 전이를 직접 검증한다.
- 검색, 커서, 정렬은 tie-break와 다음 페이지 중복 여부까지 같이 검증한다.

## 완료 기준

- 새 분기마다 실패 테스트가 먼저 존재했는가?
- 버그 수정이면 재현 테스트가 이전 코드에서 실패했는가?
- 보안 규칙 변경이면 권한 실패 케이스도 같이 테스트했는가?
- repository 쿼리를 건드렸다면 정렬/필터/soft delete 조건 테스트가 있는가?
