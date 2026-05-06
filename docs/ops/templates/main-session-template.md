---
last_writer: user
last_updated: <YYYY-MM-DDTHH-MM-SS+09-00>      # 파일명 안전 형식, hook 출력과 통일
---

# Main Session Context

> 새 세션이라면 §10 Recovery → §1 Current Goal → §5 Open Questions → §9 Evidence 순으로 읽고 시작.
>
> 이 파일은 `backend-now/harness/main-session.md`이며 local-only(.gitignore). docs/ops/AGENT_WORKFLOW.md §11에 따른다.

## 1. Current Goal

- (한두 줄: 지금 세션이 무엇을 향하는지)

## 2. Context

- (배경, 제약, 외부 의존성)

## 3. Decisions

- D-001: <결정 본문> (Evidence: E-001)
- D-002: ...

## 4. PRs

### Active

- #NNN (status): <제목>
  - branch: `<branch>`

### Merged

- #NNN: <제목> (<YYYY-MM-DD>)
  - branch: `<branch>`
  - archive: [[archive_<YYYY-MM-DD>_pr<NNN>_<task-slug>]]

### Abandoned/Paused

- (없음)

## 5. Open Questions

- Q-001: ...
- Q-002: ...

## 6. TODO

- [ ] (우선순위 1) ...
- [ ] (우선순위 2) ...

## 7. Non-Goals

- 이 세션에서 하지 않는 것:
  - (예) auth 리팩터링은 별 PR로
  - (예) Codex 워크트리 작업은 본 세션 범위 밖

## 8. Anti-Patterns

현재 작업에 직접 영향을 주는 항목(요약, 1~3개):

- AP-001: 작업 워크트리에서 `gh pr merge` 금지 — backend-now에서만 머지
- AP-XXX: ...

전체 목록(source of truth): `harness/anti-patterns.md`

## 9. Evidence

- E-001: <파일 또는 PR 또는 commit hash> — <근거 본문>
- E-002: ...

## 10. Recovery

- Last session ended at: commit `<hash>` on branch `<branch>`
- Next action: (예) backend-claude에서 PR #NNN 추가 커밋 진행
- 읽을 파일:
  1. `backend-now/harness/main-session.md` (이 파일)
  2. `<work-worktree>/harness/session-handoff.md`
  3. (필요 시) `docs/ops/AGENT_WORKFLOW.md`
