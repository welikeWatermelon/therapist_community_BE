---
last_writer: claude | codex | user
created_at: <YYYY-MM-DDTHH:MM:SS+09:00>
event: post-commit | pre-pr | pre-compact | post-compact | stop | pre-push
branch: <현재 브랜치>
short_sha: <commit short hash, post-commit/pre-push 시>
---

# Checkpoint: <event>

> 이 파일은 `<worktree>/harness/checkpoints/checkpoint_<event>_<timestamp>.md`. 자동 hook이 생성. 임시 안전망. 작업 종료 시 마지막 유효 정보는 archive에 흡수 후 본 파일은 삭제.

## Snapshot

### Branch / Working Tree

- 브랜치: `<branch>`
- working tree: clean / dirty (변경 파일: <list>)
- 최신 commit: `<short_sha>` — `<message-first-line>`

### Recent Decisions (이번 세션 누적)

- (있으면 짧게)

### Open Questions (이번 세션)

- (있으면 짧게)

### Last Test Result

- `<command>` — <결과>

### Notes

- (event 종류에 따라 짧은 컨텍스트)

---

## Recovery Hint

새 세션이 이 checkpoint로부터 복구할 때 가장 먼저 확인할 것:

1. 위 Branch / 최신 commit이 현재 git 상태와 일치하는가
2. main-session.md가 위 Decisions를 반영했는가
3. 미해결 Open Question 중 사용자 결정이 필요한 것은?
