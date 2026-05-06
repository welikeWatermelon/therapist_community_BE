# Agent Navigation

## 이럴 때는 여기부터 본다

- 제품 목표나 범위를 바꾸기 전: [harness/PRD.md](harness/PRD.md)
- 여러 도메인을 함께 건드리기 전: [harness/Architecture.md](harness/Architecture.md)
- 왜 이렇게 만들었는지 확인할 때: [harness/ARD.md](harness/ARD.md)
- 특정 도메인만 수정할 때: [harness/domains/README.md](harness/domains/README.md)
- 인증, 권한, 파일, AI 안전성 관련 변경 전: [harness/Secure-Coding.md](harness/Secure-Coding.md)
- 기능 추가나 버그 수정 전 테스트 전략 정리: [harness/TDD.md](harness/TDD.md)
- Codex/Claude 병렬 작업이나 worktree 통합 전: [docs/ops/WORKTREE_OPERATIONS.md](docs/ops/WORKTREE_OPERATIONS.md)

## 작업 원칙
1. 코딩 전에 생각한다. 가정, 대안, 막히는 점을 먼저 드러낸다.
2. 단순함을 우선한다. 추측성 추상화와 미래용 옵션을 넣지 않는다.
3. 수술적으로 바꾼다. 요청과 직접 연결된 파일과 줄만 만진다.
4. 목표 중심으로 끝낸다. 성공 기준과 검증 방법을 먼저 정하고 닫는다.
5. 작업 생명주기 관리: 새로운 작업을 시작할 때나, 사용자가 코딩 결과를 승인("좋아", "완료" 등)했을 때, 무조건 [harness/Workflow.md](harness/Workflow.md)를 먼저 읽고 해당 절차를 따른다.

## 깊게 들어갈 때

- 알림 상세 흐름: [docs/architecture/NOTIFICATION.md](docs/architecture/NOTIFICATION.md)
- 분석 적재/집계: [docs/architecture/ANALYTICS.md](docs/architecture/ANALYTICS.md)
- 기존 상세 아키텍처 문서: [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md)
- API 계약: [docs/api/API_SPEC.md](docs/api/API_SPEC.md)
- 성능 자료: [docs/perf/README.md](docs/perf/README.md)
- 브랜치/worktree 운영: [docs/ops/BRANCH_OPERATIONS_GUIDE.md](docs/ops/BRANCH_OPERATIONS_GUIDE.md), [docs/ops/WORKTREE_OPERATIONS.md](docs/ops/WORKTREE_OPERATIONS.md)
