# Agent Workflow

본 문서는 Claude / Codex 등 AI 에이전트와 사람이 함께 이 저장소에서 작업할 때 따라야 하는 운영 룰의 단일 출처다. `harness/Workflow.md` 등 로컬 노트는 본 문서를 참조하는 형태로만 의미를 갖는다.

## 1. 워크트리 구조 (영구 보존)

| 워크트리 | 역할 | 고정 브랜치 |
|---|---|---|
| `backend-now` | 통합·검토·메인 세션의 홈 | `main` |
| `backend-claude` | Claude 작업 전용 | `claude/<task>` 회전 |
| `backend-codex` | Codex 작업 전용 | `codex/<task>` 회전 |
| `backend-deploy` | 배포 | `deploy` |
| `backend-hotfix` | 핫픽스 | 핫픽스 브랜치 |

워크트리는 영구 자산이며 디스크 정리 등 명백한 사유가 있을 때만 `git worktree remove`를 사용한다. 작업이 끝났다고 매 PR마다 워크트리를 지우지 않는다.

`backend-claude` / `backend-codex`가 부재할 때는 1회만 다음 명령으로 생성한다(반드시 한 줄로 실행).

패턴 (참고용 — placeholder 그대로 실행 X):

```text
git -C /Users/tom/dev/buildersMvp/backend-now worktree add /Users/tom/dev/buildersMvp/backend-<agent> -b <agent>/<task-name> origin/main
```

실행 예시 (TASK 변수 치환 후 그대로 복사 가능):

```bash
# Claude
TASK=setup-foo
git -C /Users/tom/dev/buildersMvp/backend-now worktree add /Users/tom/dev/buildersMvp/backend-claude -b "claude/$TASK" origin/main
```

```bash
# Codex
TASK=domain-policy
git -C /Users/tom/dev/buildersMvp/backend-now worktree add /Users/tom/dev/buildersMvp/backend-codex -b "codex/$TASK" origin/main
```

## 2. 세션 ↔ 워크트리 매핑

- **메인 세션**: `backend-now` cwd. 기획·설계·리뷰·통합 담당. 일반 기능 개발 코딩은 하지 않는다. 단, 다음은 예외로 허용한다.
  - 통합 충돌 해결
  - 운영 문서(`docs/ops/`) 수정
  - 자체 hotfix
- **작업 세션**: 사용자가 `/fork` 후 cwd를 `backend-claude`(또는 `backend-codex`)로 옮겨 코딩 진행.

## 3. 작업 시작 절차 (작업 세션)

> 본 절의 명령은 Claude/Codex 공통이다. `<agent>` 자리에 Claude 작업이면 `claude`, Codex 작업이면 `codex`를 넣는다.
>
> 변수 매핑:
> - `<agent>` ∈ { `claude`, `codex` }
> - `<work-worktree>` = `/Users/tom/dev/buildersMvp/backend-<agent>`
>
> ⚠️ `<...>` placeholder는 **그대로 복사·실행하지 않는다.** 셸이 `<` `>`를 리다이렉션 연산자로 해석한다. 실제 값으로 치환한 뒤 실행한다.

패턴 (참고용 — placeholder 그대로 실행 X):

```text
cd <work-worktree>
git status
git branch --show-current
git fetch origin --prune
git switch -c <agent>/<task-name> origin/main
```

dirty 상태면 멈추고 사용자에게 보고한다. 임의 stash / reset / clean 금지.

clean이면 아래 실행 예시(TASK 변수 치환 후 그대로 복사 가능):

```bash
# Claude 작업 시작
cd /Users/tom/dev/buildersMvp/backend-claude
git status
git branch --show-current

TASK=setup-foo
git fetch origin --prune
git switch -c "claude/$TASK" origin/main
```

```bash
# Codex 작업 시작
cd /Users/tom/dev/buildersMvp/backend-codex
git status
git branch --show-current

TASK=domain-policy
git fetch origin --prune
git switch -c "codex/$TASK" origin/main
```

`backend-now`가 이미 `main`을 점유하므로 작업 워크트리에서 로컬 `main`으로 직접 switch하지 않는다. 항상 `origin/main`에서 새 작업 브랜치를 분기한다.

이어서 작업할 기존 브랜치가 있다면 (TASK 변수에 task slug 넣고):

```bash
TASK=setup-foo
git switch "claude/$TASK"   # Codex면 codex/$TASK
git rebase origin/main      # 또는 merge
```

이미 머지된 동명 브랜치가 잔존할 때 (OLD에 옛 task slug 넣고):

```bash
OLD=old-task
git branch -d "claude/$OLD"   # Codex면 codex/$OLD
```

## 4. 작업 종료 → PR 흐름

테스트 통과를 게이트로 둔다.

```text
1. ./gradlew test (또는 영향 범위 한정 명령)
   ├─ 실패 → STOP. 사용자에게 실패 내용을 보고하고 종료한다.
   │         커밋 / push / PR 모두 진행하지 않는다.
   └─ 성공 → 다음 단계
2. 커밋 (Lore Commit Protocol, 5절). amend 금지.
3. git push -u origin <agent>/<task-name>     # 예: claude/<task> 또는 codex/<task>
4. gh pr create --base main
   본문에 Summary, Test plan, 외부 전달(필요 시 FE/Ops 안내) 포함
5. 종료 보고:
   ✅ PR #NNN 생성 완료. 수동 리뷰 결과 확인 후 머지 여부 결정 부탁드립니다.
```

AI는 다음 두 조건이 모두 충족되기 전까지 절대 머지하지 않는다.

1. 사용자의 명시적 머지 지시
2. 사용자 사인오프 (수동 리뷰 통과)

리뷰 운영 정책은 §7을 따른다.

## 5. Lore Commit Protocol

본 절(§5)이 Lore Commit Protocol의 단일 출처(Tier 1)다. 본문은 한국어 가능, 트레일러 키는 영문 고정. (글로벌 개인 문서 `~/.codex/AGENTS.md`에 같은 protocol이 있더라도 §11.9 위계에 따라 본 §5가 우선한다.)

```
<intent line: 왜 변경했는지 — 무엇을 변경했는지가 아님>

<optional body: 제약, 접근 근거>

Constraint: <외부 제약>
Rejected: <고려한 대안> | <기각 이유>
Confidence: <low|medium|high>
Scope-risk: <narrow|moderate|broad>
Directive: <후속 수정자에게 남기는 경고>
Tested: <검증한 항목>
Not-tested: <검증 못한 항목>
```

규칙:

- Intent 라인이 첫 줄, why 중심으로 작성한다.
- 트레일러는 결정 컨텍스트가 추가될 때만 사용한다.
- `Rejected:`는 후속 에이전트가 다시 탐색하지 않도록 명시한다.
- 도메인별 트레일러를 자유롭게 추가해도 호환성을 유지한다.

## 6. PR 머지 → main 동기화

머지는 메인 세션(`backend-now`)에서만 실행한다. 작업 워크트리에서 `gh pr merge`를 돌리면 워크트리가 머지된 브랜치를 따라 자동으로 `main`으로 fast-forward 전환되어 `backend-now`와 충돌한다(2026-05-06 사고 사례).

```bash
# backend-now에서 (PR 번호와 옛 task slug 치환)
cd /Users/tom/dev/buildersMvp/backend-now
PR=103
gh pr merge "$PR" --merge --delete-branch
git fetch origin --prune
git pull --ff-only origin main
./gradlew test                                   # 통합 회귀
```

작업 워크트리는 그대로 둔다. 다음 작업 시 새 브랜치로 회전하고, 머지된 로컬 브랜치는 다음과 같이 정리한다:

```bash
OLD=old-task
git branch -d "claude/$OLD"   # Codex면 codex/$OLD
```

## 7. PR 리뷰 운영

이번 달은 비용 합의 전까지 GitHub Actions 기반 Claude 자동 리뷰를 활성화하지 않는다. **PR 리뷰는 수동으로 수행한다.**

### 수동 리뷰 흐름

1. 작업 브랜치에서 테스트 통과
2. PR 생성 — 본문에 Summary / Test plan / Risk / 외부 전달(FE/Ops 안내) 작성
3. 사람이 리뷰
4. 필요 시 Claude / Codex에게 수동으로 PR diff 리뷰 요청 (사용자가 직접 호출, 통제 가능)
5. 사용자 사인오프 후 backend-now에서 머지

수동 Claude/Codex 호출은 자동 과금이 아니라 사용자가 필요할 때만 호출하므로 비용 통제 가능하다.

### 향후 자동 리뷰 도입 (별 PR)

팀 비용 합의가 완료되면 별 PR에서 다음을 도입한다.

- `.github/workflows/claude-review.yml` (`anthropics/claude-code-action@v1`)
- `ANTHROPIC_API_KEY` GitHub Secret 등록
- 작은 테스트 PR을 통한 최초 실행 검증
- 실패 시 디버깅 PR로 분리

자동 리뷰 도입 시 본 §7은 "수동 + 자동 병행"으로 갱신한다. claude-review prompt에 §11(컨텍스트 보존) 위반 검사 항목을 포함한다.

### 외부 전달 문서 명명 정책

**외부 전달 문서명에는 `handoff` 단어를 쓰지 않는다** (AI 세션 인수인계 — `harness/session-handoff.md` — 와 의미 충돌). 외부 전달은 "delivery", "전달 노트" 같은 표현을 쓴다.

| 영역 | 상태 (2026-05-07 기준) |
|---|---|
| FE 영역 | ✅ 적용됨: `docs/handoff/` → `docs/frontend-notes/`, `FRONTEND_HANDOFF.md` → `FRONTEND_DELIVERY_GUIDE.md` |
| Ops cloud 문서 | ✅ 적용됨: `CLOUD_HANDOFF_PACKAGE.md` → `CLOUD_DELIVERY_PACKAGE.md`, 본문 `[Backend → Cloud Delivery]`로 통일 |
| 새로 추가되는 외부 전달 문서 | ❌ `handoff` 단어 사용 금지 |

`harness/session-handoff.md`는 외부 전달이 아니라 AI 세션 인수인계 정책 파일이므로 본 정책 대상 아님(파일명 그대로 유지).

## 8. 문서 위치 정책

| 문서 | 위치 | tracked |
|---|---|---|
| `docs/ops/AGENT_WORKFLOW.md` (본 문서) | `docs/ops/` | ✅ |
| `docs/ops/WORKTREE_OPERATIONS.md` | `docs/ops/` | ✅ |
| `docs/ops/BRANCH_OPERATIONS_GUIDE.md` | `docs/ops/` | ✅ |
| `harness/TDD.md` | `harness/` | ✅ |
| `harness/Secure-Coding.md` | `harness/` | ✅ |
| `harness/domains/*` | `harness/domains/` | ✅ |
| `AGENTS.md`, `CLAUDE.md` | repo root | ✅ |
| `harness/PRD.md` | `harness/` | ❌ 로컬 (개인 기획) |
| `harness/ARD.md` | `harness/` | ❌ 로컬 |
| `harness/Architecture.md` | `harness/` | ❌ 로컬 |
| `harness/Workflow.md` | `harness/` | ❌ 로컬 (본 문서로 마이그레이션. 사용자가 본인 워크트리에서 한 줄 redirect로 수동 정리) |

`.gitignore`는 개인 노트만 선택적으로 ignore한다. `harness/` 전체를 ignore하지 않는다(그러면 tracked 문서까지 막힌다).

## 9. 금지 사항

- AI 자동 머지 (사용자 명시적 지시 + 리뷰 통과 + 사인오프 후에만)
- 테스트 실패 상태에서 커밋 / push / PR
- `git commit --amend` (항상 새 커밋)
- 작업 워크트리에서 `gh pr merge`
- `git push --force` (특히 main으로)
- 워크트리 임의 `remove`
- `--no-verify`, `--no-gpg-sign` 등 훅 우회
- AI가 자율적으로 tracked 문서(`docs/ops/`, `AGENTS.md`, `CLAUDE.md`) 수정 (사용자 명시 지시 또는 PR 범위에 포함될 때만)

## 10. 본 문서 갱신

본 문서 수정도 동일한 PR 흐름을 따른다. 메인 세션이 운영 문서를 직접 수정하는 예외(2절)에 해당하지만, 그 경우에도 PR을 거쳐 수동 리뷰(이번 달) 또는 자동 리뷰(향후 도입 시)를 받는다.

## 11. 컨텍스트 보존 / 세션 인수인계

본 절은 Claude/Codex 등 AI 에이전트와 사람이 함께 작업할 때 컨텍스트가 손실되지 않도록 기록 위치·시점·신뢰도·라이프사이클을 정한다. AI auto memory는 보조 캐시로만 보고, 진짜 source of truth는 아래 파일들이다.

### 11.1 위치 구조 (3계층)

| 종류 | 위치 | tracked |
|---|---|---|
| 공식 운영 규칙 | `docs/ops/AGENT_WORKFLOW.md` (본 절) | ✅ |
| 공식 템플릿 | `docs/ops/templates/main-session-template.md`, `session-handoff-template.md`, `checkpoint-template.md`, `anti-patterns-template.md` | ✅ |
| 메인 세션 기록 | `backend-now/harness/main-session.md` | ❌ |
| 작업 세션 기록 | `<work-worktree>/harness/session-handoff.md` | ❌ |
| 작업별 계획 | `<work-worktree>/harness/plan.md` | ❌ |
| 작업별 안티패턴 | `<worktree>/harness/anti-patterns.md` | ❌ |
| 체크포인트/아카이브 | `<worktree>/harness/checkpoints/` | ❌ |

각 워크트리는 자기 `harness/`만 갖는다(다른 워크트리와 섞이지 않음). 실제 기록은 세션/워크트리별로 따로, 형식·규칙은 tracked 문서로 공유한다.

### 11.2 핵심 권한 룰

local-only harness 기록은 자동 갱신 대상이다. 사용자 승인 없이 생성/수정 가능하다.

tracked 문서(`docs/ops/`, `AGENTS.md`, `CLAUDE.md`) 변경은 자동 갱신 대상이 아니며, 사용자 명시 지시 또는 PR 범위에 포함될 때만 수정한다. 변경은 항상 별 PR로 리뷰를 받는다.

### 11.3 갱신 시점 (출력)

AI는 다음 시점에 local-only harness 기록을 자율 갱신한다.

- PR 생성 직전
- 커밋 직후 (commit hash 포함)
- 긴 작업을 중단하기 전
- 사용자가 "기록해줘" / "정리해줘" / "인수인계 남겨줘" / "handoff 남겨줘"(영문 자연어) 같은 의도를 표현한 때 — 트리거 문구는 예시이며, 사용자 발화의 의도가 갱신·승격이면 모두 포함. (외부 전달 문서명 정책(§7)은 자연어 트리거를 제한하지 않는다.)
- `/compact`가 예상되거나 컨텍스트가 길어졌다고 판단한 때

#### compact 운영 원칙

`/compact`는 파일이 아니라 대화 컨텍스트를 압축한다. 파일 기록(`harness/*`, `docs/*`, git commit)은 영향받지 않는다. 따라서:

- **메인 세션은 가능한 `/compact`를 피한다.** 대신 작업 세션을 짧게 쓰고 자주 닫아 메인 세션의 컨텍스트 압박을 줄인다.
- compact 전 이벤트(상태 전이) 기반 checkpoint(§11.11)가 컨텍스트 손실 방어선이다.
- 진짜 위험은 "오래됨"이 아니라 **기록되지 않은 결정의 손실**이다. 시간 기준으로 경고하지 않는다.

### 11.4 읽기 시점 (입력)

AI는 다음 시점에 자동으로 컨텍스트 파일을 읽고 현재 상태를 사용자에게 짧게 요약 보고한다.

- 새 세션 시작
- cwd 변경 (워크트리 이동)
- 사용자가 새 작업 지시

읽기 대상: `harness/main-session.md` + 자기 워크트리의 `harness/session-handoff.md`.

AI는 새 세션 시작 시 `main-session.md`의 `Recovery`, `Current Goal`, `Open Questions`, `Evidence` 섹션을 우선 읽고, 나머지는 필요할 때 참조한다.

### 11.5 기록 신뢰도

- harness 기록은 실행 로그가 아니라 작업 기억 초안이다.
- 확정 사실 / 추론 / 미확인 가정을 구분한다.
- 미확인 내용은 `[추정]` 또는 `[확인 필요]`로 표시한다.
- 사실 기록에는 파일명·PR 번호·commit hash·테스트 명령을 함께 적는다.
- 틀린 기록은 다음 갱신에서 수정한다(local-only이므로 부담 없음).
- 각 기록 파일 frontmatter에 `last_writer: claude|codex|user`로 작성자 표시(필요 시).

### 11.6 안티패턴 운영

작업 단위로 발견한 "하지 말 것"은 `<worktree>/harness/anti-patterns.md`에 누적하고, 형식은 `docs/ops/templates/anti-patterns-template.md`를 따른다(AP-NNN 식별자, Status/Evidence/Do not/Instead/Source).

여러 작업에서 반복 발견되거나 사용자가 공식화 결정한 항목은 `docs/ops/AGENT_WORKFLOW.md` §9(금지 사항) 또는 별 절로 승격하고, harness 쪽 항목은 `Promoted to docs/ops/AGENT_WORKFLOW.md §N`로 redirect 라인만 남기거나 제거한다. 같은 룰이 두 곳에서 살아 있지 않게 한다.

### 11.7 라이프사이클

#### 승격 흐름

- 작업 종료(머지·중단·폐기) 시 `<work-worktree>/harness/session-handoff.md`의 핵심을 `backend-now/harness/main-session.md`로 승격한다.
- 승격된 `session-handoff.md`는 `<worktree>/harness/checkpoints/archive_<YYYY-MM-DD>_<pr-or-no-pr>_<task-slug>.md`로 이동하고, 본 파일은 빈 템플릿으로 초기화한다.

#### checkpoint 운영

- 자동 스냅샷은 §11.11 목록의 상태 전이 이벤트마다 `<worktree>/harness/checkpoints/checkpoint_<event>_<timestamp>.md`로 저장한다. 이벤트 목록·트리거·hook 셋업은 §11.11이 단일 출처. `<timestamp>` 형식은 본 §11.7 파일명 예시 참조 (`+09-00`).
- `checkpoint_*`는 작업 진행 중 안전망이며 영구 기록이 아니다.
- 작업 종료(머지·중단·폐기) 시 유효한 마지막 checkpoint 내용은 `archive_*`에 흡수하고, 나머지 `checkpoint_*`는 삭제한다.
- 영구 보존 대상은 `archive_*`와 `main-session.md`에 승격된 요약뿐이다.

#### archive 인덱스

- 별도 `INDEX.md`는 만들지 않는다.
- 메인 세션의 `main-session.md`에서 관련 PR/브랜치 항목 아래 archive 파일 링크를 남긴다.
- archive 링크가 많아져 `main-session.md`가 과밀해지면 그때 `harness/checkpoints/INDEX.md`로 분리한다.

#### task-slug 명명 규칙

- `task-slug`는 브랜치명에서 `claude/`, `codex/`, `fix/`, `feat/` 같은 접두어를 제거한 뒤 파일명 안전 문자만 남긴다.
- `/`는 `-`로 바꾸고, 영문 소문자·숫자·하이픈만 사용한다.
- PR이 있으면 파일명에 PR 번호를 `pr<NNN>` 형태로 붙인다. PR이 없으면 `no-pr`를 쓴다.

#### 폴더 구조

- `<worktree>/harness/checkpoints/` 단일 폴더, 파일명 prefix로 종류 구분:
  - `checkpoint_<event>_<timestamp>.md` — 임시 안전망
  - `archive_<YYYY-MM-DD>_<pr-or-no-pr>_<task-slug>.md` — 영구 보존

#### 파일명 예시

```
archive_2026-05-06_pr103_setup-agent-workflow-and-claude-review.md
archive_2026-05-06_no-pr_spike-s3-policy.md
checkpoint_post-commit_2026-05-06T11-30-00+09-00.md
checkpoint_pre-pr_2026-05-06T11-45-00+09-00.md
```

#### Frontmatter 표준

archive 파일:

```yaml
---
last_writer: claude | codex | user
archived_at: <YYYY-MM-DDTHH-MM-SS+09-00>      # 파일명 안전 timestamp, hook 출력과 통일
branch: <원본 브랜치명>
pr: <PR 번호 또는 null>
task_slug: <접두어 제거된 슬러그>
status: merged | abandoned | paused
---
```

checkpoint 파일:

```yaml
---
last_writer: claude | codex | user
created_at: <YYYY-MM-DDTHH-MM-SS+09-00>      # 파일명 안전 timestamp, hook 출력과 통일
event: post-commit | pre-pr | pre-compact | post-compact | session-start | stop | pre-push
branch: <원본 브랜치명>
---
```

### 11.8 Codex/Claude 공통 적용

본 절은 Claude/Codex 모두 동일하게 적용한다. 각 에이전트는 자기 워크트리의 `session-handoff.md`만 사용해 메모가 섞이지 않게 한다.

공통 운영 규칙은 repo root의 `AGENTS.md`와 `CLAUDE.md`가 인덱싱한다. Codex는 repo `AGENTS.md`를 우선 참조하고, Claude는 repo `CLAUDE.md`를 우선 참조한다. 개인 전역 문서(`~/.codex/AGENTS.md`, `~/.claude/CLAUDE.md`)는 보조 규칙이며, repo 문서와 충돌하면 repo 문서를 우선한다.

### 11.9 Source of Truth 우선순위

```
Tier 1: 공식 운영 규칙 (tracked, repo)
  docs/ops/*, AGENTS.md, CLAUDE.md
   ↓
Tier 2: 메인 세션 기록 (untracked, repo)
  backend-now/harness/main-session.md, anti-patterns.md
   ↓
Tier 3: 작업 세션 기록 (untracked, repo)
  <work-worktree>/harness/session-handoff.md, plan.md, anti-patterns.md, checkpoints/*
   ↓
Tier 4: 글로벌 개인 운영 문서 (untracked, 사용자 환경)
  ~/.codex/AGENTS.md, ~/.claude/CLAUDE.md
  → 보조 규칙. 현재 프로젝트의 repo-local 기록(Tier 1~3)이 모두 우선.
   ↓
Tier 5: AI auto memory (보조 캐시, 단독 신뢰 X)
  Claude auto memory, Codex auto memory
```

#### 충돌 해결

- 상위 Tier가 하위 Tier와 충돌하면 상위를 신뢰. 하위는 갱신 또는 폐기한다.
- 같은 Tier 내 충돌은 frontmatter `last_updated` 기준.
- Tier 5(auto memory)는 단독으로 신뢰하지 않는다. Tier 1~4 중 어느 것과도 어긋나면 memory를 정정한다.

#### 승격 흐름

작업 세션 발견(Tier 3) → 메인 세션 흡수(Tier 2) → 공식화(Tier 1). 승격 시 하위에서 제거 또는 redirect 한 줄만 남긴다(중복 방지). 자세한 승격 룰은 §11.12 참조.

#### 강제 장치가 아니라 운영 정책

본 위계는 운영 정책이며, AI가 항상 완벽히 지킨다는 보장은 없다. 준수율은 다음 장치로 높인다.

- repo root `AGENTS.md` / `CLAUDE.md` 인덱싱
- Claude Code hook / Git hook checkpoint (§11.11)
- 수동 리뷰 시 §11 위반 검사 (이번 달 공식). 향후 자동 리뷰 도입 시 `claude-review` prompt에 §11 위반 검사 항목 포함하여 자동화 (§7 참조)
- 사용자 교정 시 `harness/anti-patterns.md` 누적
- 반복 위반은 `docs/ops/AGENT_WORKFLOW.md` §9로 승격

### 11.10 main-session.md 구조

`backend-now/harness/main-session.md`는 단일 파일로 시작한다. 아래 10개 섹션을 두고, 한 섹션이 비대해지거나(예: 200줄 이상) 전체가 500줄을 초과하면 그 섹션을 별 파일로 분리한다. 분리 시 main-session.md에는 한 줄 link만 남긴다.

1. **Current Goal** — 지금 세션이 무엇을 향하는지 한두 줄
2. **Context** — 배경, 제약, 외부 의존성
3. **Decisions** — 확정된 결정 (ID + 본문, Evidence 링크)
4. **PRs** — 진행 중/대기 PR 상태
5. **Open Questions** — 아직 결정되지 않은 사항
6. **TODO** — 우선순위 있는 다음 행동 목록
7. **Non-Goals** — 명시적으로 하지 않는 것 (scope creep 방지)
8. **Anti-Patterns** — 현재 작업에 직접 영향을 주는 1~3개 요약 + 링크. 단일 출처는 `harness/anti-patterns.md`
9. **Evidence** — Decisions의 근거 (E-NNN ↔ D-NNN 매칭, 파일명·PR·commit hash·테스트 명령)
10. **Recovery** — 새 세션 시작 시 컨텍스트 복구 절차 + 마지막 작업 지점 ("어디서 다시 시작할지"가 핵심)

자세한 형식과 예시는 `docs/ops/templates/main-session-template.md` 참조.

### 11.11 Claude Hook Checkpoint Policy

Claude를 메인 작업 도구로 사용할 때는 Claude Code hook과 Git hook을 사용해 local-only checkpoint를 자동 생성한다.

#### 사용 hook (모두 이벤트 기반, 시간 임계값 없음)

- **`post-commit`** (Git hook): 커밋 직후 `harness/checkpoints/checkpoint_post-commit_<timestamp>_<short-sha>.md` 생성
- **`PreCompact`** (Claude Code): `/compact` 또는 auto-compact 직전 `checkpoint_pre-compact_<timestamp>.md` 생성. AI에게 "마지막 기록 이후 결정/변경 검토" 신호 출력
- **`PostCompact`** (Claude Code): compact 이후 git snapshot(branch / status / 최신 commit)을 `checkpoint_post-compact_<timestamp>.md`에 저장. 향후 hook 입력 JSON에서 compact summary를 추출하도록 보강 가능 (별 PR)
- **`SessionStart`** (Claude Code): 새 세션 시작 시 `harness/main-session.md`와 `harness/session-handoff.md` 위치 안내, 없으면 템플릿 생성 유도
- **`Stop`** (Claude Code): 세션 종료/중단 시 현재 branch, git status, 변경 파일을 `checkpoint_stop_<timestamp>.md`로 저장
- **`pre-push`** (Git hook) 또는 PR 생성 직전: `checkpoint_pre-push_<timestamp>.md` 생성

#### 핵심 원칙

> 커밋, compact, stop, push 같은 **상태 전이 이벤트가 발생하면 조건 없이 checkpoint를 남긴다.** 시간 임계값은 사용하지 않는다. checkpoint는 경고 시스템이 아니라 복구용 원자료다.

#### 운영 원칙

- hook은 **차단형이 아니라 기록형**이다. 사용자 흐름을 막지 않는다.
- hook은 **tracked 문서를 수정하지 않는다**. 쓰는 대상은 local-only `harness/` 파일뿐이다.
- 진짜 위험은 "오래됨"이 아니라 **기록되지 않은 결정의 손실**이다. 이벤트 기반 checkpoint가 이를 방어한다.

#### 설치 위치

- **활성 설정 (Claude Code)**: `.claude/settings.local.json` (local-only)
- **활성 설정 (Git)**: `.git/hooks/post-commit`, `.git/hooks/pre-push` (각 워크트리별 local-only)
- **템플릿 (tracked)**: `docs/ops/templates/claude-hooks/settings.local.example.json`, `checkpoint-hook.sh`, `git-hooks/post-commit.sample`, `git-hooks/pre-push.sample`
- Claude Code hook의 프로젝트 스크립트 참조는 `$CLAUDE_PROJECT_DIR` 환경 변수로 절대 경로를 만든다 (cwd가 repo root라는 보장 없음, [공식 안내](https://code.claude.com/docs/en/hooks)).

`.claude/settings.local.json`은 Claude Code 공식 local project settings 위치이며 git에 올리지 않는다. 사용자/CI/다른 에이전트에 강제되지 않는다.

#### Codex 대응

Codex는 동일 운영 룰(§11.1~§11.10)만 적용한다. Codex의 자체 hook 시스템이 있으면 동등 패턴으로 별도 셋업한다. Codex 일관성 때문에 Claude 고유 기능을 포기하지 않는다.

### 11.12 Promotion Rule

작업 세션 종료 시 `session-handoff.md` 전체를 메인 세션에 복사하지 않는다. **다음 세션의 행동을 바꾸는 정보만** `main-session.md`로 승격한다.

#### 승격 대상

- **결정** — 앞으로도 따라야 하는 확정 사항
- **미해결 질문** — 사용자가 결정해야 하는 사항
- **다음 TODO** — 다음 세션이 바로 실행할 작업
- **검증 근거** — PR 번호 / commit hash / 테스트 결과 / 파일 경로 기준
- **반복 방지 안티패턴** — 같은 실수 반복 시 사고 위험

#### 비승격 대상

- 중간 명령 로그
- 해결된 에러의 상세 로그
- 오래된 추정·가정
- 파일별 변경 세부 (PR description / commit log이 더 적합)
- 대화 흐름·감상

#### 자동화 — 작업 종료 시 AI에게 답할 5개 질문

1. 이번 작업 후 다음 세션이 반드시 알아야 할 결정은?
2. 아직 사용자가 결정해야 하는 질문은?
3. 다음에 바로 실행할 TODO는?
4. 믿어도 되는 근거는? PR / commit / test / file 기준으로.
5. 반복하면 안 되는 실수는?

이 5개 답만 `main-session.md`에 반영하고, 나머지는 `archive_*`에 둔다.

#### 운영 흐름

```
작업 세션 종료
   ↓
AI가 5개 질문 답을 session-handoff.md의 "Promotion Candidate" 섹션에 작성
   ↓
사용자 검토/수정 후 main-session.md로 옮김 (수동 또는 사용자 명령 시 AI 자동)
   ↓
원본 session-handoff.md → archive_<date>_<pr>_<task-slug>.md로 이동
   ↓
중간 checkpoint_*는 마지막 유효 정보를 archive에 흡수 후 삭제
```

#### 판단 예시

| 후보 | 판단 |
|---|---|
| "Java 17이 실제 버전이다" | 다음 자동 리뷰/문서 수정에 영향 → **승격** (Decisions + Evidence) |
| "rg 명령으로 Java 21을 검색했다" | 증거 가치만 있고 행동 불변 → **Evidence에 짧게만** |
| "gh pr diff 옵션이 한 번 실패했다" | 반복 방지 가치 작음 → **승격 안 함** |
| "작업 워크트리에서 gh pr merge 금지" | 반복 시 사고 위험 → **Anti-Patterns 또는 docs/ops로 승격** |
