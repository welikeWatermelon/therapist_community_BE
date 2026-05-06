---
last_writer: claude | codex | user
last_updated: <YYYY-MM-DDTHH-MM-SS+09-00>      # 파일명 안전 형식, hook 출력과 통일
---

# Anti-Patterns

> 이 파일은 `<worktree>/harness/anti-patterns.md`이며 local-only.
> AP-NNN 식별자로 누적. 여러 작업에서 반복 발견되거나 사용자가 공식화하면 `docs/ops/AGENT_WORKFLOW.md` §9로 승격하고 여기서는 `Promoted to docs/ops/AGENT_WORKFLOW.md §N`로 redirect 한 줄 또는 제거.

## AP-001: <한 줄 제목>

- **Status**: confirmed | hypothesis | promoted | obsolete
- **Evidence**: <왜 이게 안티패턴인지 — 사고/근거>
- **Do not**: <명령/행동 형태로 금지 사항>
- **Instead**: <대신 해야 할 것>
- **Source**: <발견 PR/commit/문서 또는 사용자 지적>
- **Discovered**: <YYYY-MM-DD>

## AP-002: <한 줄 제목>

- **Status**: ...
- **Evidence**: ...
- **Do not**: ...
- **Instead**: ...
- **Source**: ...
- **Discovered**: ...

---

## 예시

### AP-001 (예시): 작업 워크트리에서 PR 머지 금지

- **Status**: promoted (→ docs/ops/AGENT_WORKFLOW.md §9)
- **Evidence**: 2026-05-06 PR #102 머지 시 backend-claude 워크트리에서 `gh pr merge`를 실행 → 워크트리가 main으로 자동 fast-forward 전환되어 backend-now와 충돌. main 점유 상태 발생.
- **Do not**: `gh pr merge`를 backend-claude / backend-codex 워크트리에서 실행하지 말 것.
- **Instead**: backend-now (메인 세션 cwd)에서만 `gh pr merge`.
- **Source**: PR #102 사후 분석, docs/ops/AGENT_WORKFLOW.md §6
- **Discovered**: 2026-05-06
