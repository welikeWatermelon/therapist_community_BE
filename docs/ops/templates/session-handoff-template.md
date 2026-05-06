---
last_writer: claude | codex | user
last_updated: <YYYY-MM-DDTHH:MM:SS+09:00>
branch: <현재 작업 브랜치>
pr: <PR 번호 또는 null>
status: in-progress | paused | done
---

# Session Handoff

> 이 파일은 `<work-worktree>/harness/session-handoff.md`이며 local-only(.gitignore).
> 작업 진행 중 갱신, 작업 종료 시 §Promotion Candidate를 메인 세션으로 승격하고 본 파일은 `harness/checkpoints/archive_<date>_<pr>_<task-slug>.md`로 이동 후 빈 템플릿으로 초기화.

## Current State

- 작업 브랜치: `<branch>`
- 진행 단계: <단계 설명>
- 마지막 commit: `<hash>` — <짧은 메시지>

## Changes So Far

- 변경 파일 목록 (요약):
  - `<path>`: <한 줄>

## Tests

- 마지막 테스트: `<명령>` — <결과>
- 미실행/미작성:
  - <항목>

## Blockers / Open Questions (작업 단위)

- <블로커/질문>

## Notes

- (작업 진행 중 짧은 메모)

---

## Promotion Candidate

> 작업 종료 시 AI가 5개 질문에 답하여 채운다. 사용자가 검토 후 메인 세션으로 승격.

### Decisions

- [keep/drop] D-001: <결정>
- [keep/drop] D-002: ...

### Open Questions

- [keep/drop] Q-001: <질문>
- [keep/drop] Q-002: ...

### Next TODO

- [keep/drop] T-001: <행동>
- [keep/drop] T-002: ...

### Evidence

- [keep/drop] E-001: <파일/PR/commit hash> — <근거>
- [keep/drop] E-002: ...

### Anti-Patterns

- [keep/drop] AP-001: <Status/Evidence/Do not/Instead/Source 형식, 자세한 건 anti-patterns.md>

### Not Promoted

- 중간 명령 로그
- 해결된 에러의 상세
- 오래된 가정
