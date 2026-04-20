# 작업 요청: 검색 기능 Level 0 Phase 1 개선

## 컨텍스트

감사 리포트 `docs/0421/search-level0-audit-2026-04-21.md` 기반 Phase 1 작업.

**중요한 판단 프레임**:
검색 도구 선택은 사용자의 검색 행동 패턴에 따라 결정되어야 함.
현재 trigram 기반 검색이 실제 사용자 행동과 정합하는지 관찰하기 위해,
검색 로깅이 다른 모든 작업의 전제 조건임.

Phase 2 작업(word_similarity 전환, JPQL 개선 등)은 이번 턴 범위 아님.

## 작업 범위

다음 4개 커밋을 순차 진행:

### Commit 1: 검색 로깅 추가 (최우선)

**목적**: 사용자가 실제로 어떤 방식으로 검색하는지 관찰하여, 현재 trigram 방식이 사용자 행동과 정합하는지 판단하기 위한 데이터 확보.

**로깅 항목 (JSON)**:
- timestamp: ISO 8601 형식
- requestId: UUID
- keyword: 검색어
- keywordLength: 검색어 길이
- therapyArea: 치료 영역 필터
- postType: 게시글 타입 필터
- userId: 사용자 ID (없으면 null)
- cursor: {lastScore, lastId}
- resultCount: 결과 건수
- responseTimeMs: 응답 시간
- isZeroResult: 0건 여부

**구현 방식**: Spring AOP

SearchAccessLogger 클래스 생성:
- @Aspect, @Component
- PostController.search() 메서드를 @Around로 래핑
- StopWatch로 응답 시간 측정
- ProceedingJoinPoint로 파라미터 추출
- 결과 건수는 반환값에서 추출
- 예외 발생 시에도 로그 남기기 (resultCount: -1, error 포함)

**logback-spring.xml 설정**:
- 별도 appender 이름: SEARCH_ACCESS
- 파일: logs/search-access.log
- 일별 롤링, 30일 보관
- 비동기 로깅 (AsyncAppender) 적용
- logger 이름 SEARCH_ACCESS, additivity false

**JSON 직렬화**: Jackson ObjectMapper 사용 (기존 의존성)

**테스트**:
- AOP 정상 동작 단위 테스트
- 예외 발생 시 로깅 확인
- 검색 API 응답에 영향 없는지

### Commit 2: 검색어 길이 제한

PostController 검색 엔드포인트:
- keyword: @NotBlank, @Size(min=2, max=100)
- size: @Min(1), @Max(50)

GlobalExceptionHandler에서 MethodArgumentNotValidException 처리 확인.

**테스트**:
- 1글자 → 400
- 빈 문자열 → 400
- 공백만 → 400
- 101자 → 400
- size 51 → 400
- 정상 케이스 → 200

### Commit 3: 초성검색(titleChoseong) 제거

확정: 초성검색 기능은 MelloMe에 불필요. 완전 제거.

**1. Flyway 마이그레이션** V26__remove_title_choseong.sql:
- DROP INDEX IF EXISTS idx_therapy_posts_title_choseong
- ALTER TABLE therapy_posts DROP COLUMN IF EXISTS title_choseong

**2. Java 코드 제거**:
- TherapyPost.java에서 titleChoseong 필드, getter/setter, builder 관련 제거
- 관련 import 정리

**3. HangulUtils 처리**:
- 사용처 전수 검색 (grep)
- 초성 추출 용도만이면 파일 삭제
- 다른 용도 있으면 초성 관련 메서드만 제거
- 판단 어려우면 파일 삭제 후 컴파일 에러로 확인

**4. 테스트**:
- 기존 테스트에서 titleChoseong 참조 제거
- 게시글 생성/수정 테스트 정상 동작 확인

### Commit 4: 미사용 GIN 인덱스 제거

**Flyway 마이그레이션** V27__drop_unused_trigram_indexes.sql:
- DROP INDEX IF EXISTS idx_therapy_posts_title_trgm
- DROP INDEX IF EXISTS idx_therapy_posts_content_trgm

주석에 롤백 방법 기록.

**검증**:
- 제거 후 대표 검색 쿼리 EXPLAIN 실행
- idx_therapy_posts_search_text_trgm 사용 확인

## 진행 규칙

**커밋 단위**:
- 각 커밋 완료 후 멈춰서 대기. 다음 커밋 자동 진행 금지.
- 커밋 메시지 형식: feat(search): [설명] + 변경 사항 목록 + Ref: search-level0-audit Phase 1

**구현 전 계획 제시**:
- 각 커밋 시작 시 구현 계획 먼저 제시 (파일 목록, 주요 로직, 리스크)
- 내 승인 받기
- 그 후 실제 구현

**금지 사항**:
- Phase 2 작업 선행 금지
- 리포트에 없는 작업 추가 금지
- 급진적 리팩토링 금지

**테스트**:
- 각 커밋마다 관련 테스트 추가/수정
- 기존 테스트 깨지면 즉시 수정
- H2 환경 제약 인정 (trigram 쿼리 검증 불가)

## 시작

Commit 1 (검색 로깅)부터 시작.

구현 계획을 먼저 제시해줘:
1. AOP vs Interceptor vs Filter 선택 근거
2. JSON 포맷 방식 (수동 ObjectMapper vs logstash-encoder)
3. 비동기 로깅 구조
4. logback-spring.xml 변경 내역
5. 예상 파일 목록

내가 승인하면 구현 진행.
