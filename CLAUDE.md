# Agent Navigation

## 이럴 때는 여기부터 본다

- 제품 목표나 범위를 바꾸기 전: [harness/PRD.md](harness/PRD.md) *(local-only, 없으면 [docs/product/](docs/product/) 또는 사용자에게 확인)*
- 여러 도메인을 함께 건드리기 전: [harness/Architecture.md](harness/Architecture.md) *(local-only, 없으면 [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md))*
- 왜 이렇게 만들었는지 확인할 때: [harness/ARD.md](harness/ARD.md) *(local-only, 없으면 git log + PR description 참조)*
- 특정 도메인만 수정할 때: [harness/domains/README.md](harness/domains/README.md)
- 인증, 권한, 파일, AI 안전성 관련 변경 전: [harness/Secure-Coding.md](harness/Secure-Coding.md)
- 기능 추가나 버그 수정 전 테스트 전략 정리: [harness/TDD.md](harness/TDD.md)
- Codex/Claude 병렬 작업이나 worktree 통합 전: [docs/ops/WORKTREE_OPERATIONS.md](docs/ops/WORKTREE_OPERATIONS.md)
- AI 에이전트 운영 / PR / 머지 / 컨텍스트 보존 룰 전반: [docs/ops/AGENT_WORKFLOW.md](docs/ops/AGENT_WORKFLOW.md)

> `harness/PRD.md`, `harness/ARD.md`, `harness/Architecture.md`, `harness/Workflow.md`는 사용자 개인 기획 노트이며 `.gitignore` 대상(local-only). fresh clone 또는 다른 워크트리에서는 부재할 수 있다. 부재 시 위 fallback을 따르거나 사용자에게 확인한다. 자세한 위치 정책은 [docs/ops/AGENT_WORKFLOW.md §11.1](docs/ops/AGENT_WORKFLOW.md) 참조.

## 컨텍스트 보존 / 세션 인수인계

- 운영 규칙: [docs/ops/AGENT_WORKFLOW.md](docs/ops/AGENT_WORKFLOW.md) §11
- 템플릿: [docs/ops/templates/](docs/ops/templates/)
- 메인 세션 상태: `harness/main-session.md` (local-only)
- 작업 세션 인수인계: `harness/session-handoff.md` (local-only)
- 작업별 안티패턴: `harness/anti-patterns.md` (local-only)
- 자동 체크포인트: `harness/checkpoints/` (local-only)

`harness/`의 위 파일들은 local-only이며 git에 올리지 않는다.
PR 생성/커밋/`/compact` 등 상태 전이 이벤트마다 hook이 자동 checkpoint를 남긴다 (§11.11).

Claude Code는 본 `CLAUDE.md`를 우선 참조한다. 개인 전역 문서(`~/.claude/CLAUDE.md`)는 보조 규칙이며 repo 문서와 충돌하면 repo 문서를 우선한다.

## 작업 원칙
1. 코딩 전에 생각한다. 가정, 대안, 막히는 점을 먼저 드러낸다.
2. 단순함을 우선한다. 추측성 추상화와 미래용 옵션을 넣지 않는다.
3. 수술적으로 바꾼다. 요청과 직접 연결된 파일과 줄만 만진다.
4. 목표 중심으로 끝낸다. 성공 기준과 검증 방법을 먼저 정하고 닫는다.
5. 작업 생명주기 관리: 새로운 작업을 시작할 때나, 사용자가 코딩 결과를 승인("좋아", "완료" 등)했을 때, [docs/ops/AGENT_WORKFLOW.md](docs/ops/AGENT_WORKFLOW.md) §3·§4 절차를 따른다. 기존 `harness/Workflow.md` *(local-only)*는 본 문서로 마이그레이션됨 (사용자 워크트리에서 한 줄 redirect로 정리 또는 삭제).
6. local-only `harness/` 기록은 자율 갱신, tracked 문서는 사용자 명시 지시/PR 범위에서만 변경한다.

## 깊게 들어갈 때

- 알림 상세 흐름: [docs/architecture/NOTIFICATION.md](docs/architecture/NOTIFICATION.md)
- 분석 적재/집계: [docs/architecture/ANALYTICS.md](docs/architecture/ANALYTICS.md)
- 기존 상세 아키텍처 문서: [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md)
- API 계약: [docs/api/API_SPEC.md](docs/api/API_SPEC.md)
- 성능 자료: [docs/perf/README.md](docs/perf/README.md)
- 브랜치/worktree 운영: [docs/ops/BRANCH_OPERATIONS_GUIDE.md](docs/ops/BRANCH_OPERATIONS_GUIDE.md), [docs/ops/WORKTREE_OPERATIONS.md](docs/ops/WORKTREE_OPERATIONS.md)
