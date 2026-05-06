# Worktree Operations

Codex와 Claude가 동시에 작업할 때는 작업 위치와 통합 위치를 분리한다.

## 기본 구조

```text
/Users/tom/dev/buildersMvp/backend-now     통합 전용 worktree
/Users/tom/dev/buildersMvp/backend-codex   Codex 작업 전용 worktree
/Users/tom/dev/buildersMvp/backend-claude  Claude 작업 전용 worktree
```

`backend-now`에서는 직접 기능 개발을 하지 않는다. 통합, 충돌 해결, 최종 테스트만 수행한다.

## 브랜치 규칙

- `main`: 원격 기준 개발 통합 브랜치
- `codex/*` 또는 `codex-*`: Codex 작업 브랜치
- `claude/*` 또는 `claude-*`: Claude 작업 브랜치
- `integrate/*`: 필요할 때만 만드는 임시 통합 브랜치

기능 브랜치는 가능하면 `origin/main`에서 시작한다. 이미 진행 중인 변경이 있으면 먼저 해당 에이전트 브랜치에 커밋한 뒤 통합한다.

## 작업 시작 절차

```bash
git fetch origin
git worktree add ../backend-codex -b codex/<task-name> origin/main
git worktree add ../backend-claude -b claude/<task-name> origin/main
```

이미 브랜치가 있으면 `-b`를 빼고 기존 브랜치를 체크아웃한다.

```bash
git worktree add ../backend-codex codex/<task-name>
```

## 에이전트 작업 규칙

- 각 에이전트는 자기 worktree와 자기 브랜치에서만 파일을 수정한다.
- 같은 파일을 동시에 수정해야 하면 먼저 소유권을 정한다.
- DB migration은 충돌 위험이 높으므로 작업 브랜치에서 임시 번호를 쓰고, `backend-now` 통합 시 최종 번호를 정렬한다.
- `AGENTS.md`, `CLAUDE.md`, `harness/`는 로컬 하네스 문서다. 공유가 필요한 운영 규칙은 `docs/ops/`에 둔다.
- 작업 단위가 끝나면 관련 테스트를 통과시킨 뒤 커밋한다.

## 통합 절차

`backend-now`에서만 통합한다.

```bash
cd /Users/tom/dev/buildersMvp/backend-now
git fetch origin
git switch main
git pull --ff-only origin main
git merge --no-ff codex/<task-name>
git merge --no-ff claude/<task-name>
./gradlew test
```

충돌이 나면 통합 worktree에서 해결한다. 해결 후에는 충돌 파일과 migration 번호를 다시 확인한다.

## 통합 전 체크리스트

- `git status --short`가 예상한 변경만 보여준다.
- migration 번호가 중복되지 않는다.
- 인증/권한 변경은 `SecurityConfig`, `CustomUserDetails`, 도메인 서비스 guard를 함께 확인했다.
- API 응답 필드 추가는 관련 DTO 테스트 또는 컨트롤러 테스트로 확인했다.
- 작업 브랜치의 테스트 명령을 커밋 메시지나 PR 본문에 남겼다.

## 현재 운영 결정

- `backend-now`는 통합 지점으로 유지한다.
- Codex는 `backend-codex`에서 작업한다.
- Claude는 `backend-claude`에서 작업한다.
- 지금 진행 중인 Codex 변경은 `codex-domain-policy-foundation` 브랜치로 분리한다.
