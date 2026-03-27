# Docs Index

`docs/`는 아래 기준 문서만 업데이트합니다. 같은 주제를 여러 파일에 다시 적지 않습니다.

## 기준 문서

- [API_SPEC.md](./API_SPEC.md): 수기 API 설명서입니다. 실제 최신 계약 확인은 실행 중 Swagger/OpenAPI를 우선 기준으로 봅니다.
- [FRONTEND_HANDOFF.md](./FRONTEND_HANDOFF.md): 프론트 전달 기준 문서입니다. 전달 메시지 템플릿까지 이 문서에서 관리합니다.
- [CLOUD_HANDOFF_PACKAGE.md](./CLOUD_HANDOFF_PACKAGE.md): 클라우드 배포, systemd, Nginx, 환경변수, CloudWatch 기준 문서입니다.
- [SERVER_CHECK_RUNBOOK.md](./SERVER_CHECK_RUNBOOK.md): 운영 서버 점검과 장애 구분 절차 문서입니다.
- [BRANCH_OPERATIONS_GUIDE.md](./BRANCH_OPERATIONS_GUIDE.md): 브랜치 운영 규칙 문서입니다.

## 보조 문서

- [ops-todo.md](./ops-todo.md): 아직 끝나지 않은 운영 작업 체크리스트입니다. (운영 고도화의 예시로 남겨놓음.)
- [product/therapist_community_policy_decisions.md](./product/therapist_community_policy_decisions.md): 정책 결정 배경 문서입니다.
- [product/therapist_community_feature_spec.csv](./product/therapist_community_feature_spec.csv): 기능명세 CSV입니다.
- [product/therapist_community_requirements_spec.csv](./product/therapist_community_requirements_spec.csv): 요구사항명세 CSV입니다.

## 편집 원칙

- 한 주제에는 기준 문서 하나만 둡니다.
- 전달용 템플릿은 실제 값 대신 플레이스홀더를 사용합니다.
- 시크릿, 실계정 비밀번호, 개인 키 경로는 저장소 문서에 적지 않습니다.
