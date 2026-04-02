# Docs Index

`docs/`는 아래 기준 문서만 업데이트합니다. 같은 주제를 여러 파일에 다시 적지 않습니다.

## architecture/ — 코드 구조 & 설계

- [ARCHITECTURE.md](./architecture/ARCHITECTURE.md): 레이어 구조, 요청 흐름, 주요 컴포넌트, 도메인 목록
- [FILE_STRUCTURE.md](./architecture/FILE_STRUCTURE.md): 전체 패키지 트리, 폴더 역할, 네이밍 패턴
- [DECISIONS.md](./architecture/DECISIONS.md): 도메인별 설계 결정 이유
- [CONVENTIONS.md](./architecture/CONVENTIONS.md): 패키지 규칙, 소프트 삭제, 응답/예외 형식, 트랜잭션 규칙

## api/ — API 관련

- [API_RULES.md](./api/API_RULES.md): URL 패턴, 인증, 응답/에러 형식, 페이지네이션 규칙
- [API_SPEC.md](./api/API_SPEC.md): 수기 API 설명서 (실제 최신 계약은 Swagger/OpenAPI 우선)
- [openapi.json](./api/openapi.json): springdoc 자동 생성 OpenAPI 3.1 스펙 (한국어 description 포함)

## ops/ — 운영 & 인프라

- [CLOUD_HANDOFF_PACKAGE.md](./ops/CLOUD_HANDOFF_PACKAGE.md): 클라우드 배포, systemd, Nginx, 환경변수, CloudWatch
- [SERVER_CHECK_RUNBOOK.md](./ops/SERVER_CHECK_RUNBOOK.md): 운영 서버 점검과 장애 구분 절차
- [BRANCH_OPERATIONS_GUIDE.md](./ops/BRANCH_OPERATIONS_GUIDE.md): 브랜치 운영 규칙
- [ops-todo.md](./ops/ops-todo.md): 미완료 운영 작업 체크리스트

## handoff/ — 인수인계

- [FRONTEND_HANDOFF.md](./handoff/FRONTEND_HANDOFF.md): 프론트 전달 기준 문서 (템플릿 포함)

## product/ — 기획 & 정책

- [therapist_community_policy_decisions.md](./product/therapist_community_policy_decisions.md): 정책 결정 배경
- [therapist_community_feature_spec.csv](./product/therapist_community_feature_spec.csv): 기능명세 CSV
- [therapist_community_requirements_spec.csv](./product/therapist_community_requirements_spec.csv): 요구사항명세 CSV

## 편집 원칙

- 한 주제에는 기준 문서 하나만 둡니다.
- 전달용 템플릿은 실제 값 대신 플레이스홀더를 사용합니다.
- 시크릿, 실계정 비밀번호, 개인 키 경로는 저장소 문서에 적지 않습니다.
