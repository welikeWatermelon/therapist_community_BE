# PRIVATE UX 개편 — 프론트 핸드오프 (2026-04-29)

## 목적

일반회원(USER role)이 피드/검색에서 **치료사 전용(PRIVATE) 게시글의 존재를 인지**하고 **메타데이터를 볼 수 있도록** 변경. 본문/이미지는 가려진 상태로 노출되어 **인증 유도 hook** 역할.

배경: 기존엔 USER에게 PRIVATE 게시글이 아예 안 보여서 "치료사 인증을 받으면 더 많은 콘텐츠가 있다"는 사실을 알 수 없었음 → 인증/가입 전환 hook 부재.

## 백엔드 변경 요약

### 응답 DTO에 신규 필드: `accessLocked: boolean`

`TherapyPostSummaryResponse`에 추가:
```json
{
  "id": 123,
  "postType": "RESOURCE",
  "contentPreview": "비공개 글입니다",   // 마스킹된 텍스트
  "authorNickname": "강박사",            // 메타데이터 그대로 노출
  "therapyArea": "SPEECH",
  "visibility": "PRIVATE",
  "viewCount": 152,
  "likeCount": 12,
  "commentCount": 3,
  "createdAt": "2026-04-22T10:00:00",
  "isScrapped": false,
  "accessLocked": true                   // ⬅ 신규
}
```

### 동작 매트릭스

| 사용자 role | PUBLIC 게시글 | PRIVATE 게시글 |
|-------------|--------------|---------------|
| USER (일반회원) | accessLocked=false, 본문 정상 | **accessLocked=true, contentPreview="비공개 글입니다"** |
| THERAPIST/ADMIN | accessLocked=false, 본문 정상 | accessLocked=false, 본문 정상 |
| 본인 글 (`/me/posts`) | 자신 글이므로 항상 unlocked | 자신 글이므로 항상 unlocked |

### 변경된 엔드포인트

이 필드가 응답에 포함되는 모든 게시글 목록·검색·피드:

- `GET /api/v1/posts` — 게시글 목록 (페이징)
- `GET /api/v1/posts/feed?sort=LATEST|POPULAR` — 무한스크롤 피드
- `GET /api/v1/posts/search?keyword=...` — 검색 (RELEVANCE)
- `GET /api/v1/me/posts` — 내가 쓴 글 (본인 글이라 항상 unlocked)
- `GET /api/v1/me/scraps` — 스크랩한 글 (별도 처리, 추후 핸드오프 추가 시)

### 변경 안 된 동작

- **상세 페이지 진입은 여전히 차단**: `GET /api/v1/posts/{id}`는 USER가 PRIVATE 글에 접근 시 `403 THERAPIST_VERIFICATION_REQUIRED` 반환. 클릭 시 인증 유도 모달 노출 권장.
- **PRIVATE 글 작성 권한**: USER는 여전히 PRIVATE 게시글 작성 불가 (`400 INVALID_INPUT`).
- **이미지/첨부 파일 접근**: USER가 PRIVATE 게시글의 이미지·첨부 다운로드 시도 시 차단.

## 프론트 처리 가이드

### 1. 카드 렌더링

```jsx
function PostCard({ post }) {
  return (
    <article className={post.accessLocked ? 'card--locked' : 'card'}>
      <div className="meta">
        <span>{post.authorNickname}</span>
        <span>{areaLabel(post.therapyArea)}</span>
        {post.visibility === 'PRIVATE' && <LockIcon />}
      </div>

      {post.accessLocked ? (
        <LockedPreview onClick={openAuthModal} />
      ) : (
        <p>{post.contentPreview}</p>
      )}

      <div className="counts">
        ❤️ {post.likeCount} · 💬 {post.commentCount} · 👁 {post.viewCount}
      </div>
    </article>
  );
}
```

### 2. 카드 클릭 처리

```jsx
function handleCardClick(post) {
  if (post.accessLocked) {
    // 인증 유도 모달
    showAuthModal({
      title: '치료사 전용 콘텐츠입니다',
      message: '치료사 인증을 완료하면 본문을 볼 수 있어요',
      ctaPrimary: '치료사 인증하기',
      ctaSecondary: '닫기',
      onPrimary: () => router.push('/verify-therapist')
    });
    return;
  }
  // 정상 진입
  router.push(`/posts/${post.id}`);
}
```

### 3. 잠금 표시 디자인 권장

- 카드 자체에 `lock` 아이콘 + 미세한 그라디언트/블러 오버레이
- contentPreview 영역만 회색 placeholder ("비공개 글입니다") 또는 모자이크 효과
- 메타데이터(좋아요/댓글 수, 작성자, 영역, 시간)는 **풀로 노출**해야 인증 유도 hook 효과 발휘
- "더 읽기" 같은 버튼 → 인증 유도 모달

### 4. 검색 결과에서도 동일

`/posts/search` 응답의 각 item도 동일한 `accessLocked` 필드를 가짐. 동일하게 마스킹 처리.

## 엣지 케이스

- **로그인 안 한 게스트**: 현재 백엔드는 `/api/v1/posts/**`를 인증 필요로 막음. 게스트가 피드를 볼 수 없음. 이 핸드오프는 USER role 이상에 한해 적용.
- **이미 인증된 치료사가 보는 자기 자신의 PRIVATE 글**: `accessLocked=false`, 본문 노출.
- **본인이 작성한 PRIVATE 글을 USER가 본인 마이페이지(`/me/posts`)에서 보는 경우**: USER는 PRIVATE 작성 권한 없음 → 발생 불가.

## 백엔드 PR

- 브랜치: `feat/private-ux-mask`
- 변경 파일:
  - `TherapyPostSummaryResponse` — accessLocked 필드 + canViewPrivate 인자
  - `PostService` — Repository publicOnly 분기 제거, role 기반 마스킹
  - `GinTrigramSearchStrategy`, `PgVectorSearchStrategy` — publicOnly 분기 제거
  - `SearchResultAssembler` — canViewPrivate 인자 추가
  - 테스트 보강

## 변경 전후 비교 (USER가 받는 응답 차이)

### Before (PR 이전)

USER 피드 응답:
```json
{ "items": [ /* PUBLIC 글만 */ ] }
```

PRIVATE 게시글이 아예 결과에 없어서 사용자는 "치료사 전용 글이 있다"는 사실 모름.

### After (이 PR)

USER 피드 응답:
```json
{
  "items": [
    { "visibility": "PUBLIC", "accessLocked": false, "contentPreview": "본문..." },
    { "visibility": "PRIVATE", "accessLocked": true, "contentPreview": "비공개 글입니다" },
    ...
  ]
}
```

PRIVATE 게시글이 메타데이터와 함께 노출되어 인증 유도 hook 작동.

## 질문/이슈

질문은 본인(@ttttom1) 또는 backend channel에. 추후 변경 시 이 문서 갱신.
