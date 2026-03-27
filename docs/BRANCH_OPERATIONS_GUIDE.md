# Branch Operations Guide

현재 저장소 브랜치 규칙은 아래로 고정합니다.

- `main`: 개발 통합 브랜치
- `deploy`: 운영 배포 브랜치
- `feat/*`: 기능 개발 브랜치
- `hotfix/*`: 운영 긴급 수정 브랜치

핵심 원칙은 4개입니다.

- 개발은 `main`
- 운영 배포는 `deploy`
- hotfix는 `deploy`에서 시작
- hotfix 후 반드시 `main`에 다시 반영

## 1) 언제 어떤 브랜치를 쓰는가

### 기능 개발

- 시작 기준: `origin/main`
- 브랜치 이름: `feat/*`
- merge 대상: `main`

예시:

```bash
git fetch origin
git switch -c feat/comment origin/main
```

PR:

- `feat/comment -> main`

### 운영 배포

- 배포 기준 브랜치: `deploy`
- 개발 완료 후 `main -> deploy` PR 생성
- 운영 서버는 `deploy`만 배포

PR:

- `main -> deploy`

### 운영 긴급 수정

- 시작 기준: `origin/deploy`
- 브랜치 이름: `hotfix/*`
- 1차 merge 대상: `deploy`
- 배포 후 2차 반영 대상: `main`

예시:

```bash
git fetch origin
git switch -c hotfix/comment-500 origin/deploy
```

PR 순서:

1. `hotfix/comment-500 -> deploy`
2. 운영 배포
3. `deploy -> main` 또는 `hotfix/comment-500 -> main`

## 2) 팀원이 기억할 최소 규칙

- `main` 직접 push 금지
- `deploy` 직접 push 금지
- 기능 브랜치는 `main`에서만 분기
- hotfix 브랜치는 `deploy`에서만 분기
- 운영에서 고친 내용은 반드시 `main`에도 반영

## 3) 권장 GitHub 설정

- `main`, `deploy` 보호 브랜치 적용
- direct push 금지
- force push 금지
- PR merge만 허용
- 최소 1명 리뷰
- CI 통과 후 merge

권장 merge 방식:

- 기본: `Create a merge commit`

## 4) 자주 쓰는 명령

기능 브랜치 시작:

```bash
git fetch origin
git switch -c feat/post-search origin/main
```

운영 브랜치 확인:

```bash
git fetch origin
git log --oneline origin/deploy -n 5
```

배포 전 `main`과 `deploy` 차이 확인:

```bash
git fetch origin
git log --oneline --left-right origin/deploy...origin/main
git diff --stat origin/deploy..origin/main
```

hotfix 시작:

```bash
git fetch origin
git switch -c hotfix/prod-cors origin/deploy
```

## 5) 클라우드 팀 전달 문구

```text
[Backend Branch Rule]
- Development branch: main
- Production branch: deploy
- Feature branches start from origin/main
- Hotfix branches start from origin/deploy
- Please deploy only from deploy
```

## 6) 한 줄 정리

- 기능 개발: `feat/* -> main`
- 운영 배포: `main -> deploy`
- 운영 긴급 수정: `hotfix/* -> deploy -> main`
