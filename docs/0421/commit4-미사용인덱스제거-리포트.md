# Commit 4: 미사용 GIN 인덱스 제거

## 무엇을 했나

V27 마이그레이션으로 2개의 미사용 GIN trigram 인덱스를 삭제했다:
- `idx_therapy_posts_title_trgm`
- `idx_therapy_posts_content_trgm`

## 왜 했나

**마이그레이션 히스토리:**
```
V16: title/content 각각에 GIN trigram 인덱스 생성
V25: search_text 컬럼 추가 + 검색 쿼리를 search_text로 전환
     → title/content 개별 인덱스는 더 이상 쿼리에서 참조되지 않음
```

**현재 검색 쿼리가 참조하는 인덱스:**
```sql
-- 이 쿼리는 idx_therapy_posts_search_text_trgm만 사용
SELECT ... FROM therapy_posts p
WHERE p.search_text % :keyword
   OR p.search_text ILIKE '%' || :escapedKeyword || '%'
```

**미사용 인덱스의 비용:**
- INSERT/UPDATE마다 인덱스 갱신 발생 (쓰기 성능 감소)
- 디스크 공간 점유
- VACUUM 시 인덱스도 처리 (유지보수 비용)
- 현재 데이터 30개로 무시할 수준이지만, 정리 차원

## 핵심 개념 학습

### 1. GIN 인덱스의 쓰기 비용

**GIN (Generalized Inverted Index)의 구조:**
```
"감각" → [row1, row5, row12]
"각통" → [row1, row5]
"통합" → [row1, row3, row12]
...
```

각 trigram이 어떤 행에 있는지를 역색인으로 저장.

**INSERT 시 발생하는 일:**
1. 새 행의 텍스트에서 모든 trigram 추출
2. 각 trigram에 대해 역색인 업데이트 (row ID 추가)
3. "감각통합 언어치료 3세" → trigram 약 15개 → 15개의 인덱스 엔트리 추가

**인덱스 3개 vs 1개:**
```
인덱스 3개 유지 시: title(~5 trigrams) + content(~30) + search_text(~40) = ~75 엔트리/행
인덱스 1개 유지 시: search_text(~40) = ~40 엔트리/행
```
→ 미사용 인덱스 2개 제거로 쓰기 비용 ~47% 감소 (이론적)

### 2. 인덱스 사용 여부 확인하는 방법

**PostgreSQL에서 확인:**
```sql
-- 인덱스 사용 통계 확인
SELECT indexrelname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
WHERE relname = 'therapy_posts';
```

`idx_scan = 0`이면 한 번도 사용되지 않은 인덱스.

**코드에서 확인:**
Repository의 모든 쿼리를 grep하여 `title`, `content` 컬럼을 직접 검색하는 쿼리가 있는지 확인:
- JPQL `searchByKeyword`: `LOWER(p.content) LIKE ...` → 이건 `LOWER()` 래핑으로 GIN 인덱스를 타지 않을 가능성 높음
- Native 검색: `search_text % :keyword` → title/content 인덱스 무관

### 3. DROP INDEX와 락(Lock)

```sql
DROP INDEX IF EXISTS idx_therapy_posts_title_trgm;
```

**일반 DROP INDEX:**
- `AccessExclusiveLock`을 테이블에 걸음
- 해당 테이블 읽기/쓰기 모두 대기
- 대규모 테이블에서는 수 초간 서비스 중단 가능

**DROP INDEX CONCURRENTLY:**
- 락 없이 삭제
- 하지만 Flyway 마이그레이션에서 사용 불가 (트랜잭션 내에서 CONCURRENTLY 불가)

**현재 선택:**
- 데이터 30개, 서비스 초기 단계 → 일반 DROP INDEX로 충분
- 프로덕션 대규모 데이터에서는 Flyway 바깥에서 수동으로 CONCURRENTLY 실행 고려

### 4. 인덱스 제거 후 검증 방법

**남아있는 유일한 검색 인덱스 확인:**
```sql
\di+ therapy_posts
-- 또는
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'therapy_posts';
```

**EXPLAIN으로 인덱스 사용 확인:**
```sql
SET pg_trgm.similarity_threshold = 0.03;
EXPLAIN ANALYZE
SELECT p.id, similarity(p.search_text, '감각통합') AS score
FROM therapy_posts p
WHERE p.deleted_at IS NULL
  AND p.search_text % '감각통합'
ORDER BY score DESC, p.id DESC
LIMIT 10;
```

기대 결과:
```
Bitmap Index Scan on idx_therapy_posts_search_text_trgm
```

### 5. 롤백 전략

마이그레이션 파일 주석에 롤백 SQL을 기록해둠:
```sql
-- 롤백 방법:
CREATE INDEX idx_therapy_posts_title_trgm ON therapy_posts USING GIN (title gin_trgm_ops);
CREATE INDEX idx_therapy_posts_content_trgm ON therapy_posts USING GIN (content gin_trgm_ops);
```

**언제 롤백이 필요할까:**
- JPQL `searchByKeyword`(`/api/v1/posts` 엔드포인트)의 `LOWER(content) LIKE` 쿼리가
  content 인덱스를 타고 있었다면 성능 저하 발생 가능
- 하지만 `LOWER()` 래핑은 GIN trigram 인덱스 활용을 방해하므로 가능성 낮음
- 모니터링 후 성능 문제 발견 시 롤백

## 마이그레이션 V26-V27 정리 후 남은 인덱스

| 인덱스 | 대상 | 용도 | 상태 |
|--------|------|------|------|
| `idx_therapy_posts_search_text_trgm` | search_text | relevance 검색 (%, ILIKE) | ✅ 유지 |
| `idx_therapy_posts_created_at_id` | (created_at DESC, id DESC) | 커서 피드 | ✅ 유지 |
| PK index | id | 기본 키 | ✅ 유지 |

제거된 인덱스:
| 인덱스 | 제거 버전 | 사유 |
|--------|----------|------|
| `idx_therapy_posts_title_choseong` | V26 | Dead Field (값 없음) |
| `idx_therapy_posts_title_trgm` | V27 | 쿼리 미참조 |
| `idx_therapy_posts_content_trgm` | V27 | 쿼리 미참조 |
