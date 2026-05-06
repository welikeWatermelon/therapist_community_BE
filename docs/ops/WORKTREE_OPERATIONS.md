# Worktree Operations

Codex와 Claude가 동시에 작업할 때는 작업 위치와 통합 위치를 분리한다.

> 본 문서는 worktree 구조와 브랜치 규칙만 다룬다. PR / 머지 / 컨텍스트 보존 / hook / 자동 리뷰 등 전체 운영 룰은 [AGENT_WORKFLOW.md](AGENT_WORKFLOW.md)를 단일 출처로 따른다.

## 기본 구조 (영구 보존)

| Worktree | 역할 | 고정 브랜치 |
|---|---|---|
| `/Users/tom/dev/buildersMvp/backend-now` | 통합·검토·메인 세션의 홈 | `main` |
| `/Users/tom/dev/buildersMvp/backend-claude` | Claude 작업 전용 | `claude/<task>` 회전 |
| `/Users/tom/dev/buildersMvp/backend-codex` | Codex 작업 전용 | `codex/<task>` 회전 |
| `/Users/tom/dev/buildersMvp/backend-deploy` | 배포 | `deploy` |
| `/Users/tom/dev/buildersMvp/backend-hotfix` | 핫픽스 | 핫픽스 브랜치 |

워크트리는 **영구 자산**이다. 작업이 끝났다고 매번 지우지 않는다. `git worktree remove`는 디스크 정리 등 명백한 사유에만 사용한다.

`backend-now`는 통합 / 운영 문서 수정 / 충돌 해결 외에는 일반 기능 개발 코딩을 하지 않는다 (예외는 `AGENT_WORKFLOW.md` §2 참조).

## 브랜치 규칙

- `main`: 원격 기준 개발 통합 브랜치
- `claude/<task-name>`: Claude 작업 브랜치
- `codex/<task-name>`: Codex 작업 브랜치
- `deploy`: 배포 브랜치
- `integrate/*`: 필요할 때만 만드는 임시 통합 브랜치

기능 브랜치는 항상 `origin/main`에서 시작한다.

## 새 작업 시작 절차

**영구 보존된 워크트리에서 브랜치만 회전한다.** 매번 새 worktree를 추가하지 않는다.

```bash
cd /Users/tom/dev/buildersMvp/backend-claude

# 상태 점검 (clean이어야 함)
git status

# origin/main 동기화
git fetch origin --prune

# 작업 브랜치 시작 (origin/main 기준 — 작업 워크트리는 로컬 main을 직접 체크아웃하지 않음)
git switch -c claude/<task-name> origin/main
```

이어서 작업할 기존 브랜치가 있다면:

```bash
git switch claude/<task-name>
git rebase origin/main          # 또는 git merge origin/main
```

머지된 동명 브랜치 잔존:

```bash
git branch -d claude/<old-task>
```

## 워크트리 최초 1회 생성 (부재 시)

`backend-claude` / `backend-codex`가 아예 없는 경우 1회만 생성한다(반드시 한 줄로 실행).

```bash
git -C /Users/tom/dev/buildersMvp/backend-now worktree add /Users/tom/dev/buildersMvp/backend-claude -b claude/<task-name> origin/main
```

```bash
git -C /Users/tom/dev/buildersMvp/backend-now worktree add /Users/tom/dev/buildersMvp/backend-codex -b codex/<task-name> origin/main
```

이후 작업은 위 "새 작업 시작 절차"를 따르고 워크트리는 영구 보존한다.

## 에이전트 작업 규칙

- 각 에이전트는 자기 worktree와 자기 브랜치에서만 파일을 수정한다.
- 같은 파일을 동시에 수정해야 하면 먼저 소유권을 정한다.
- DB migration은 충돌 위험이 높으므로 작업 브랜치에서 임시 번호를 쓰고, 통합 시 최종 번호를 정렬한다.
- 작업 단위가 끝나면 관련 테스트를 통과시킨 뒤 커밋한다 (`AGENT_WORKFLOW.md` §4).

## 문서 위치 정책 (tracked vs local-only)

| 종류 | 위치 | tracked |
|---|---|---|
| 공식 운영 규칙 | `docs/ops/AGENT_WORKFLOW.md`, `docs/ops/WORKTREE_OPERATIONS.md` (본 문서) 등 | ✅ |
| 운영 룰 인덱스 | `AGENTS.md`, `CLAUDE.md` (repo root) | ✅ |
| 컨벤션 | `harness/TDD.md`, `harness/Secure-Coding.md`, `harness/domains/*` | ✅ |
| 사용자 개인 기획 | `harness/PRD.md`, `harness/ARD.md`, `harness/Architecture.md`, `harness/Workflow.md` | ❌ |
| 메인 세션 컨텍스트 | `backend-now/harness/main-session.md` | ❌ |
| 작업 세션 인수인계 | `<work-worktree>/harness/session-handoff.md`, `plan.md`, `anti-patterns.md`, `checkpoints/*` | ❌ |

운영 룰·인덱스·컨벤션은 tracked, 사용자 개인 노트와 세션 기록만 local-only. (이전 본 문서가 `AGENTS.md` / `CLAUDE.md` / `harness/`를 일괄 로컬로 분류했던 표현은 폐기됨.)

자세한 위치 정책과 충돌 해결 우선순위는 `AGENT_WORKFLOW.md` §11.1, §11.9 참조.

## 통합·머지 절차

PR 흐름을 단일 출처로 따른다 (`AGENT_WORKFLOW.md` §4·§6).

요지:
- 작업 워크트리에서 PR 생성 → 수동 리뷰(이번 달, §7) → 사용자 사인오프
- 머지는 **`backend-now`에서만** `gh pr merge --merge --delete-branch`
- 머지 후 `backend-now`에서 `git pull --ff-only origin main`
- 작업 워크트리는 영구 보존, 다음 작업은 새 브랜치로 회전

작업 워크트리에서 `gh pr merge`를 실행하면 워크트리가 머지된 브랜치를 따라 자동으로 main으로 fast-forward 전환되어 `backend-now`와 충돌한다(2026-05-06 사고 사례). 본 사항은 `AGENT_WORKFLOW.md` §9 금지 사항에 박혀 있다.

## 통합 전 체크리스트

- `git status --short`가 예상한 변경만 보여준다.
- migration 번호가 중복되지 않는다.
- 인증/권한 변경은 `SecurityConfig`, `CustomUserDetails`, 도메인 서비스 guard를 함께 확인했다.
- API 응답 필드 추가는 관련 DTO 테스트 또는 컨트롤러 테스트로 확인했다.
- 작업 브랜치의 테스트 명령을 커밋 메시지(Lore Commit Protocol, `AGENT_WORKFLOW.md` §5) 또는 PR 본문에 남겼다.
