# Commit 3: 초성검색(titleChoseong) 완전 제거

## 무엇을 했나

1. Flyway 마이그레이션 V26: `title_choseong` 컬럼 + 인덱스 DROP
2. TherapyPost 엔티티에서 `titleChoseong` 필드 제거
3. `HangulUtils.java` 유틸 클래스 삭제
4. `HangulUtilsTest.java` 테스트 삭제

## 왜 했나

**Dead Field 문제:**
- V16 마이그레이션에서 `title_choseong` 컬럼 + 인덱스를 만들었지만
- Java 코드에서 **한 번도 값을 넣지 않음** (populate 코드 없음)
- 결과: 모든 행에 `title_choseong = NULL`
- 인덱스는 존재하지만 **쓸모 없는 공간 + 쓰기 비용**만 발생

**HangulUtils는 왜 존재했나:**
- 초성 검색 기능을 위해 만들어둔 유틸
- `extractChoseong("언어치료")` → `"ㅇㅇㅊㄹ"`
- 하지만 이걸 호출해서 DB에 저장하는 코드가 구현되지 않은 상태로 방치됨

## 핵심 개념 학습

### 1. Dead Field / Dead Code 정리의 원칙

**언제 삭제해야 하나:**
- 코드가 실행되지 않음이 **확실**할 때
- grep으로 전수 검색하여 참조가 없음을 확인

**확인 방법:**
```bash
grep -r "titleChoseong\|title_choseong\|HangulUtils" src/
```
→ TherapyPost.java 필드 정의 + HangulUtils 자체만 나옴 → 호출하는 곳 없음

**삭제 후 확인:**
```bash
./gradlew compileJava compileTestJava  # 컴파일 에러 없으면 안전
```

### 2. Flyway 마이그레이션의 원칙

**왜 Java 코드만 지우면 안 되나:**
- 엔티티에서 필드를 지워도 DB 테이블에 컬럼은 그대로 남아있음
- JPA는 unmapped 컬럼을 무시하므로 "동작은 하지만"
- 불필요한 컬럼/인덱스가 프로덕션 DB에 영원히 남음

**마이그레이션 작성 패턴:**
```sql
-- 반드시 IF EXISTS 사용 (멱등성 보장)
DROP INDEX IF EXISTS idx_therapy_posts_title_choseong;
ALTER TABLE therapy_posts DROP COLUMN IF EXISTS title_choseong;
```

**IF EXISTS의 중요성:**
- 개발 환경에서 이미 수동으로 지운 경우에도 에러 없이 진행
- 마이그레이션 재실행 시에도 안전

### 3. 인덱스 제거 시 고려사항

**이 인덱스(`idx_therapy_posts_title_choseong`)의 특성:**
```sql
CREATE INDEX idx_therapy_posts_title_choseong 
    ON therapy_posts (title_choseong varchar_pattern_ops);
```

- `varchar_pattern_ops`: LIKE 'prefix%' 검색을 위한 연산자 클래스
- 모든 값이 NULL → 인덱스에 아무것도 안 들어있음
- 하지만 INSERT/UPDATE마다 "이 행 인덱스에 넣을까?" 체크는 발생 → 미세한 비용

### 4. 컬럼 제거 시 PostgreSQL 동작

```sql
ALTER TABLE therapy_posts DROP COLUMN IF EXISTS title_choseong;
```

**PostgreSQL의 DROP COLUMN 동작:**
- 실제로 디스크에서 데이터를 지우지 않음 (!)
- 시스템 카탈로그에서 컬럼을 "숨김" 처리
- 이후 VACUUM FULL 또는 테이블 rewrite 시 물리적으로 제거됨
- → 대용량 테이블에서도 **즉시 완료** (락 시간 최소)

### 5. 초성 검색이 "불필요"한 이유 (이 프로젝트에서)

**초성 검색이 유용한 경우:**
- 모바일 키보드에서 빠르게 검색 (카카오톡 연락처 검색)
- 고정된 짧은 텍스트 (이름, 상품명)

**이 프로젝트에서 불필요한 이유:**
- 검색 대상이 `search_text` = title + content(100자) + therapyArea + ageGroup
- 이미 pg_trgm ILIKE로 부분 문자열 검색 가능
- 초성 검색의 UX 가치 < 유지 비용 (코드 복잡도)
- 향후 pgvector 의미 검색으로 가면 초성 검색은 더더욱 무의미

## 삭제 전/후 비교

### 삭제된 파일
| 파일 | 역할 |
|------|------|
| `HangulUtils.java` | 초성 추출 유틸 (extractChoseong, isChoseongOnly) |
| `HangulUtilsTest.java` | 초성 유틸 단위 테스트 5개 |

### 수정된 파일
| 파일 | 변경 |
|------|------|
| `TherapyPost.java` | `titleChoseong` 필드 2줄 제거 |

### 추가된 파일
| 파일 | 내용 |
|------|------|
| `V26__remove_title_choseong.sql` | DROP INDEX + DROP COLUMN |

## 롤백 방법

만약 초성 검색이 필요해지면:
```sql
-- V27 (가상)
ALTER TABLE therapy_posts ADD COLUMN title_choseong VARCHAR(200);
CREATE INDEX idx_therapy_posts_title_choseong 
    ON therapy_posts (title_choseong varchar_pattern_ops);
-- + Java 코드에서 populate 로직 구현 필요
```
