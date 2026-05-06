# Secure Coding Guide

## 기본 원칙

- 비밀값은 환경변수와 profile 설정으로만 주입한다. 토큰, API 키, 비밀번호 원문을 코드나 로그에 남기지 않는다.
- coarse-grained 권한은 `SecurityConfig`, ownership/visibility 검증은 service 레벨에서 같이 막는다.
- 새 예외는 `CustomException` + `ErrorCode` 로 추가하고, 500으로 숨길 일을 200으로 넘기지 않는다.

## 이 코드베이스에서 특히 지킬 것

- 인증: refresh token은 쿠키 정책과 family rotation을 유지한다. raw refresh token을 DB나 응답 body에 저장하지 않는다.
- 파일: `FileStorageService` 만 사용한다. 업로드 타입, 확장자, 크기, 헤더 검증과 실패 시 orphan cleanup을 같이 본다.
- 게시글/댓글: soft delete와 private visibility 필터를 우회하는 조회를 추가하지 않는다.
- Redis: 로그인 잠금, 조회수, 캐시는 장애 시 폴백 동작이 다르다. authz 자체를 Redis에 의존하지 않는다.
- AI: private post를 AI 초안 처리 대상으로 보내지 않는다. 관리자 승인 없는 자동 공개를 추가하지 않는다.
- 로그: 이메일, license code, 원문 문서, AI 응답 전문은 필요 최소한만 남긴다.

## 변경 전 체크리스트

- 새 endpoint가 `SecurityConfig` 에 정확히 반영되었는가?
- 작성자/관리자/치료사 제한이 service에서 다시 검증되는가?
- soft delete, visibility, ownership 조건이 조회 쿼리에 반영되는가?
- 파일/문서 저장 실패 시 DB와 스토리지 orphan이 남지 않는가?
- AI/외부 API 오류가 본작업 트랜잭션을 깨지 않는가?
