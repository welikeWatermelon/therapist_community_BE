#!/usr/bin/env bash
# Claude Code Hook — local-only checkpoint 자동 생성
#
# 사용 위치: .claude/settings.local.json의 PreCompact / PostCompact / SessionStart / Stop hooks
# 호출 형태: bash <this-script> <event>
#   <event>: pre-compact | post-compact | session-start | stop
#
# 동작:
#   - <worktree>/harness/checkpoints/checkpoint_<event>_<timestamp>.md 생성
#   - 현재 branch / git status / 최신 commit 정보를 frontmatter + 본문에 기록
#   - 차단 X — stdout/stderr만 사용. exit 0
#
# 정책: docs/ops/AGENT_WORKFLOW.md §11.11
#
# 활성화: 본 스크립트 위치를 settings.local.example.json의 command 경로와 맞춘다.
#   기본 경로: docs/ops/templates/claude-hooks/checkpoint-hook.sh (tracked 템플릿)
#   사용자 환경에 맞게 .git/hooks/ 또는 별 위치로 복사해 쓸 수 있다.

set -uo pipefail

EVENT="${1:-unknown}"
TS="$(date +%Y-%m-%dT%H-%M-%S%z | sed 's/\(..\)$/:\1/')"
HARNESS_DIR="harness"
CHECKPOINTS_DIR="${HARNESS_DIR}/checkpoints"

# Repo 루트로 이동 (workspace 어디서 호출되든)
# 우선순위: 현재 cwd 기준 git toplevel → $CLAUDE_PROJECT_DIR fallback
if git_root="$(git rev-parse --show-toplevel 2>/dev/null)"; then
  cd "$git_root" || exit 0
elif [[ -n "${CLAUDE_PROJECT_DIR:-}" && -d "$CLAUDE_PROJECT_DIR" ]]; then
  cd "$CLAUDE_PROJECT_DIR" || exit 0
fi

mkdir -p "$CHECKPOINTS_DIR" || { echo "[checkpoint-hook] mkdir failed: $CHECKPOINTS_DIR" >&2; exit 0; }

BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
SHORT_SHA="$(git rev-parse --short HEAD 2>/dev/null || echo none)"
COMMIT_MSG="$(git log -1 --pretty=%s 2>/dev/null || echo none)"
STATUS_LINES="$(git status --short 2>/dev/null | head -50)"

OUT="${CHECKPOINTS_DIR}/checkpoint_${EVENT}_${TS}.md"

# main-session.md 안내 (SessionStart 한정)
if [[ "$EVENT" == "session-start" ]]; then
  if [[ ! -f "${HARNESS_DIR}/main-session.md" ]]; then
    echo "[checkpoint-hook] harness/main-session.md 없음. docs/ops/templates/main-session-template.md 참조해 작성." >&2
  fi
  if [[ ! -f "${HARNESS_DIR}/session-handoff.md" ]]; then
    echo "[checkpoint-hook] harness/session-handoff.md 없음. docs/ops/templates/session-handoff-template.md 참조해 작성." >&2
  fi
fi

cat > "$OUT" <<EOF
---
last_writer: claude
created_at: ${TS}
event: ${EVENT}
branch: ${BRANCH}
short_sha: ${SHORT_SHA}
---

# Checkpoint: ${EVENT}

## Snapshot

- 브랜치: \`${BRANCH}\`
- 최신 commit: \`${SHORT_SHA}\` — ${COMMIT_MSG}
- working tree:

\`\`\`
${STATUS_LINES:-(clean)}
\`\`\`

## Notes

(자동 생성. 필요 시 사용자/AI가 보강)
EOF

echo "[checkpoint-hook] wrote ${OUT}" >&2
exit 0
