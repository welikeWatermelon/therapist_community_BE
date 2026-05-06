# Domain Map

## 읽는 순서

1. 범위 확인: [../PRD.md](../PRD.md) *(local-only, 없으면 [`docs/product/`](../../docs/product/) 또는 사용자에게 확인)*
2. 교차 흐름 확인: [../Architecture.md](../Architecture.md) *(local-only, 없으면 [`docs/architecture/ARCHITECTURE.md`](../../docs/architecture/ARCHITECTURE.md))*
3. 이유 확인: [../ARD.md](../ARD.md) *(local-only, 없으면 git log + PR description 참조)*
4. 도메인 세부 확인: 아래 문서

> `../PRD.md`, `../ARD.md`, `../Architecture.md`는 사용자 개인 기획 노트이며 `.gitignore` 대상(local-only). fresh clone 또는 다른 워크트리에서는 부재할 수 있다. 자세한 위치 정책은 [`docs/ops/AGENT_WORKFLOW.md §11.1`](../../docs/ops/AGENT_WORKFLOW.md) 참조.

## Identity / Trust

- [auth.md](auth.md): 회원가입, 로그인, refresh token, 약관 동의
- [user.md](user.md): 내 정보, 프로필, 탈퇴, 유저 캐시
- [therapist.md](therapist.md): 치료사 인증 신청과 상태
- [admin.md](admin.md): 관리자 심사와 운영성 엔드포인트

## Community Core

- [post.md](post.md): 게시글, 피드, 검색, 첨부파일, 이미지, 다운로드
- [comment.md](comment.md): 2-depth 댓글
- [reaction.md](reaction.md): 게시글/댓글 반응
- [scrap.md](scrap.md): 북마크
- [notification.md](notification.md): SSE 알림

## AI / Retrieval / Observability

- [knowledge.md](knowledge.md): 지식 문서 인입과 임베딩
- [autocomment.md](autocomment.md): AI 댓글 초안과 승인
- [analytics.md](analytics.md): 사용자 이벤트 적재와 집계

## Shared / Support

- [application-mypage.md](application-mypage.md): 마이페이지용 cross-domain facade
- [file.md](file.md): 파일 저장 추상화
- [meta.md](meta.md): 공개 메타/약관 엔드포인트
- [global.md](global.md): 보안, 예외, 캐시, 공통 설정
