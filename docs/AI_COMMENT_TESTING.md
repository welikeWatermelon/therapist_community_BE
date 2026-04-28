# AI 자동 댓글 로컬 테스트 가이드

## 사전 준비

### 1. Google AI API Key 발급
- https://aistudio.google.com/apikey 에서 생성
- 무료 티어로 테스트 가능 (결제 불필요)

### 2. Docker (pgvector)
```bash
# 기존 DB 초기화 + pgvector 이미지로 시작
docker compose down -v
docker compose up -d

# healthy 확인
docker compose ps
```
> `docker-compose.yml`의 이미지가 `pgvector/pgvector:pg16`인지 확인

### 3. application-local.yaml 설정
```yaml
app:
  knowledge:
    enabled: true
    api-key: <YOUR_GOOGLE_AI_API_KEY>
  ai-comment:
    enabled: true
    api-key: <YOUR_GOOGLE_AI_API_KEY>
```

### 4. 앱 실행
```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

---

## 테스트 흐름

### Step 1: 로그인

```bash
# admin 로그인
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@local.com","password":"test1234!"}' | jq -r .data.tokens.accessToken)

echo $ADMIN_TOKEN

# therapist 로그인
THERAPIST_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"therapist@local.com","password":"test1234!"}' | jq -r .data.tokens.accessToken)

echo $THERAPIST_TOKEN
```

### Step 2: 지식 문서 업로드 (RAG 자료)

```bash
# 테스트 문서 생성
cat > /tmp/speech-therapy.txt << 'EOF'
언어치료 초기 평가에서는 표준화된 검사 도구를 활용하여 아동의 수용언어와 표현언어 수준을 파악합니다.
대표적인 검사 도구로는 REVT(수용·표현 어휘력 검사), PRES(취학전 아동의 수용언어 및 표현언어 발달 척도),
SELSI(영유아 언어발달 검사) 등이 있습니다.
자발화 분석을 통해 평균발화길이(MLU)를 측정하고, 조음 정확도, 유창성, 음성 특성도 함께 평가합니다.
초기 평가 시 부모 면담을 통해 발달력, 가족력, 현재 의사소통 환경도 파악해야 합니다.
EOF

# 업로드
curl -s -X POST http://localhost:8080/api/v1/admin/knowledge/documents \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -F "file=@/tmp/speech-therapy.txt;type=text/plain" \
  -F "title=언어치료 초기 평가 가이드" \
  -F "therapyArea=SPEECH" | jq .
```

### Step 3: 인덱싱 완료 확인

```bash
# 문서 상태 확인 (READY가 될 때까지 반복)
curl -s http://localhost:8080/api/v1/admin/knowledge/documents \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data.items[0].status'

# "READY" → 인덱싱 완료
# "QUEUED" 또는 "PROCESSING" → 대기 중 (몇 초 후 재확인)
# "FAILED" → 에러 확인:
curl -s http://localhost:8080/api/v1/admin/knowledge/documents \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data.items[0] | {status, lastErrorCode, lastErrorMessage}'
```

### Step 4: 게시글 작성 (자동 댓글 요청)

```bash
# requestAutoComment: true로 게시글 작성
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/posts \
  -H "Authorization: Bearer $THERAPIST_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "content":"<p>언어치료 초기 평가에서 주의할 점이 뭘까요? REVT 외에 다른 검사 도구도 추천해주세요.</p>",
    "therapyArea":"SPEECH",
    "visibility":"PUBLIC",
    "requestAutoComment":true
  }')

echo $RESPONSE | jq .

# postId 추출
POST_ID=$(echo $RESPONSE | jq -r .data.id)
echo "Post ID: $POST_ID"

# autoCommentStatus 확인 → "QUEUED"
echo $RESPONSE | jq .data.autoCommentStatus
```

### Step 5: 초안 확인

```bash
# 몇 초 대기 후 초안 조회 (비동기 처리 시간 필요)
sleep 5

curl -s http://localhost:8080/api/v1/admin/posts/$POST_ID/ai-comment-draft \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .

# 확인할 것:
# - status: "SUCCEEDED"
# - sourceMode: "RAG" (지식 문서와 매칭됨) 또는 "FALLBACK" (매칭 안 됨)
# - draftComment: AI가 생성한 초안
# - confidenceScore: 검색 품질 점수
# - retrievalContextJson: 참고한 문서 목록 (RAG일 때)
```

### Step 6: 승인 또는 거절

```bash
# 승인 → AI 댓글 생성
curl -s -X POST http://localhost:8080/api/v1/admin/posts/$POST_ID/ai-comment-draft/approve \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .

# 또는 거절 → 댓글 미생성
# curl -s -X POST http://localhost:8080/api/v1/admin/posts/$POST_ID/ai-comment-draft/reject \
#   -H "Authorization: Bearer $ADMIN_TOKEN" | jq .
```

### Step 7: 댓글 확인

```bash
curl -s http://localhost:8080/api/v1/posts/$POST_ID/comments \
  -H "Authorization: Bearer $THERAPIST_TOKEN" | jq .

# authorIsAi: true인 댓글이 보여야 함
# authorNickname: "Melonne AI"
```

---

## 시나리오별 테스트

### FALLBACK 테스트 (지식 문서 없이)

```bash
# 지식 문서 없이 다른 therapyArea로 게시글 작성
curl -s -X POST http://localhost:8080/api/v1/posts \
  -H "Authorization: Bearer $THERAPIST_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "content":"<p>미술치료에서 색채 선택이 아동 심리에 미치는 영향은?</p>",
    "therapyArea":"ART",
    "visibility":"PUBLIC",
    "requestAutoComment":true
  }' | jq .

# → sourceMode: "FALLBACK" (ART 관련 지식 문서가 없으므로)
```

### PRIVATE 글 거절 테스트

```bash
curl -s -X POST http://localhost:8080/api/v1/posts \
  -H "Authorization: Bearer $THERAPIST_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "content":"<p>비공개 질문</p>",
    "therapyArea":"SPEECH",
    "visibility":"PRIVATE",
    "requestAutoComment":true
  }' | jq .

# → 400 에러 (PRIVATE + requestAutoComment 조합 불가)
```

### 자동 댓글 미요청 테스트

```bash
curl -s -X POST http://localhost:8080/api/v1/posts \
  -H "Authorization: Bearer $THERAPIST_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "content":"<p>일반 게시글</p>",
    "therapyArea":"SPEECH",
    "visibility":"PUBLIC"
  }' | jq .data.autoCommentStatus

# → "NOT_REQUESTED"
```

### 기능 비활성화 테스트

`application-local.yaml`에서:
```yaml
app:
  ai-comment:
    enabled: false
```

앱 재시작 후 게시글 작성 → `autoCommentStatus: "FAILED"` (게시글 자체는 성공)

---

## 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| 문서 상태 FAILED + "No extractor" | 지원하지 않는 파일 형식 | pdf, txt, md, html만 허용 |
| 문서 상태 FAILED + API 에러 | Google API key 오류 | key 확인, 무료 할당량 확인 |
| 초안 상태 FAILED + "FEATURE_DISABLED" | ai-comment.enabled=false | application-local.yaml 확인 |
| 초안 상태 FAILED + "RATE_LIMITED" | Google API 호출 제한 | 1분 후 스케줄러가 자동 재시도 |
| sourceMode가 항상 FALLBACK | 지식 문서 미업로드 또는 therapyArea 불일치 | Step 2~3 확인 |
| pgvector 에러 | postgres:16 이미지 사용 | pgvector/pgvector:pg16으로 변경 |
| approve 후 댓글 안 보임 | AI 계정 미생성 | V27 마이그레이션 적용 확인 |

## 관련 로그 확인

```bash
# 앱 로그에서 AI 관련 확인
# "ai-comment-" 스레드: 초안 생성
# "knowledge-" 스레드: 문서 인덱싱
# 에러 시 상세 로그가 남음
```
