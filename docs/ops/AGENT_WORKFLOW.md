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

`backend-claude` / `backend-codex`가 부재할 때는 1회만 다음 명령으로 생성한다(반드시 한 줄로 실행):

```bash
git -C /Users/tom/dev/buildersMvp/backend-now worktree add /Users/tom/dev/buildersMvp/backend-claude -b claude/<task-name> origin/main
```

## 2. 세션 ↔ 워크트리 매핑

- **메인 세션**: `backend-now` cwd. 기획·설계·리뷰·통합 담당. 일반 기능 개발 코딩은 하지 않는다. 단, 다음은 예외로 허용한다.
  - 통합 충돌 해결
  - 운영 문서(`docs/ops/`) 수정
  - 자체 hotfix
- **작업 세션**: 사용자가 `/fork` 후 cwd를 `backend-claude`(또는 `backend-codex`)로 옮겨 코딩 진행.

## 3. 작업 시작 절차 (작업 세션)

```bash
cd /Users/tom/dev/buildersMvp/backend-claude

git status                                      # working tree clean 확인
git branch --show-current                       # 현재 브랜치 파악
```

dirty 상태면 멈추고 사용자에게 보고한다. 임의 stash / reset / clean 금지.

clean이면 다음을 실행한다.

```bash
git fetch origin --prune
git switch -c claude/<task-name> origin/main    # 항상 origin/main 기준
```

`backend-now`가 이미 `main`을 점유하므로 작업 워크트리에서 로컬 `main`으로 직접 switch하지 않는다. 항상 `origin/main`에서 새 작업 브랜치를 분기한다.

이어서 작업할 기존 브랜치가 있다면:

```bash
git switch claude/<task-name>
git rebase origin/main                           # 또는 merge
```

이미 머지된 동명 브랜치가 잔존할 때:

```bash
git branch -d claude/<old-task>
```

## 4. 작업 종료 → PR 흐름

테스트 통과를 게이트로 둔다.

```text
1. ./gradlew test (또는 영향 범위 한정 명령)
   ├─ 실패 → STOP. 사용자에게 실패 내용을 보고하고 종료한다.
   │         커밋 / push / PR 모두 진행하지 않는다.
   └─ 성공 → 다음 단계
2. 커밋 (Lore Commit Protocol, 5절). amend 금지.
3. git push -u origin claude/<task-name>
4. gh pr create --base main
   본문에 Summary, Test plan, 핸드오프(필요 시 FE/Ops 안내) 포함
5. 종료 보고:
   ✅ PR #NNN 생성 완료. GitHub Actions claude-review 자동 리뷰가 코멘트를 등록할 때까지
      대기해 주세요. 리뷰 결과 확인 후 머지 여부 결정 부탁드립니다.
```

AI는 다음 세 조건이 모두 충족되기 전까지 절대 머지하지 않는다.

1. 사용자의 명시적 머지 지시
2. GitHub Actions claude-review의 자동 리뷰 코멘트 등록 완료
3. 사용자 사인오프

## 5. Lore Commit Protocol

`~/.codex/AGENTS.md`의 Lore protocol을 따른다. 본문은 한국어 가능, 트레일러 키는 영문 고정.

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
# backend-now에서
gh pr merge <NNN> --merge --delete-branch
git fetch origin --prune
git pull --ff-only origin main
./gradlew test                                   # 통합 회귀
```

작업 워크트리는 그대로 둔다. 다음 작업 시 새 브랜치로 회전하고, 머지된 로컬 브랜치는 `git branch -d claude/<old>`로 정리한다.

## 7. 자동 리뷰 (GitHub Actions / claude-review)

- 워크플로우: `.github/workflows/claude-review.yml`
- 액션: `anthropics/claude-code-action@v1`
- 트리거: `pull_request: [opened, synchronize, reopened]` (private repo이므로 secret 노출 위험 낮음)
- 모델: 최신 Sonnet 계열(셋업 직전 공식 사양 확인). 본 문서는 모델 ID를 박지 않는다. 필요 시 워크플로우의 `claude_args`에 `--model <id>` 형태로 지정.
- 시스템 프롬프트(요지):
  - 한국어로 응답
  - Java 21 / Spring Boot / JPA 컨벤션
  - 보안·회귀·N+1·트랜잭션 위험 우선
  - nitpick 자제
  - `docs/ops/AGENT_WORKFLOW.md`, `docs/ops/WORKTREE_OPERATIONS.md`, `harness/TDD.md`, `harness/Secure-Coding.md`, `harness/domains/`를 컨벤션 소스로 사용
- Secret: `ANTHROPIC_API_KEY` (사용자가 GitHub Settings → Secrets and variables → Actions에서 직접 등록)
- 운영 1~2주 후 false positive 패턴을 수집해 시스템 프롬프트를 보강한다.
- 깊은 리뷰가 필요하면 사용자가 PR에서 `/ultrareview <PR번호>`를 별도로 트리거한다.

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

- AI 자동 머지 (사용자 명시적 지시 + 자동 리뷰 + 사인오프 후에만)
- 테스트 실패 상태에서 커밋 / push / PR
- `git commit --amend` (항상 새 커밋)
- 작업 워크트리에서 `gh pr merge`
- `git push --force` (특히 main으로)
- 워크트리 임의 `remove`
- `--no-verify`, `--no-gpg-sign` 등 훅 우회

## 10. 본 문서 갱신

본 문서 수정도 동일한 PR 흐름을 따른다. 메인 세션이 운영 문서를 직접 수정하는 예외(2절)에 해당하지만, 그 경우에도 PR을 거쳐 자동 리뷰를 받는다.
