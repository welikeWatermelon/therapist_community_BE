# Business Overview

## Business Description
- **Business Description**: 치료사 전용 커뮤니티 플랫폼. 치료사(THERAPIST)가 게시글/댓글을 작성하고 리소스를 공유하며, 리액션/스크랩/알림으로 상호작용하는 REST API 백엔드
- **Business Transactions**:
  1. 회원가입/로그인/JWT 토큰 갱신 (auth)
  2. 치료사 자격 인증 신청 및 관리자 승인/반려 (therapist + admin)
  3. 게시글 CRUD + 검색 + 피드 (post)
  4. 댓글 작성/수정/삭제 (max depth 2) (comment)
  5. 게시글/댓글 리액션 토글 (reaction)
  6. 게시글 스크랩 (scrap)
  7. 실시간 SSE 알림 (notification)
  8. AI 자동 댓글 생성 (autocomment + knowledge)
  9. 사용자 이벤트 분석 집계 (analytics)
- **Business Dictionary**:
  - TherapyArea: 치료 분야 (감각통합, 언어치료, 작업치료 등)
  - PopularityScore: `(reaction_count*30) + (scrap_count*20) + (epoch/8640)`
  - Visibility: PUBLIC/PRIVATE (PRIVATE는 THERAPIST+ 전용)

## Component Level Business Descriptions

### auth
- **Purpose**: 사용자 인증/인가
- **Responsibilities**: 회원가입, 로그인, 로그아웃, JWT 토큰 발급/갱신, 리프레시 토큰 가족 기반 회전

### user
- **Purpose**: 사용자 프로필 관리
- **Responsibilities**: 역할 관리 (USER/THERAPIST/ADMIN), 프로필 조회/수정

### post
- **Purpose**: 치료 게시글 관리
- **Responsibilities**: CRUD, 피드(LATEST/POPULAR), 검색(GIN trigram + pgvector), 조회수, 인기도 점수, 첨부파일/이미지/동영상

### comment
- **Purpose**: 댓글 시스템
- **Responsibilities**: 스레드형 댓글 (max depth 2), 소프트 삭제

### reaction
- **Purpose**: 리액션 시스템
- **Responsibilities**: 게시글 리액션 (LIKE/CURIOUS/USEFUL), 댓글 리액션 (LIKE/CURIOUS/USEFUL), 토글 방식

### scrap
- **Purpose**: 게시글 북마킹
- **Responsibilities**: 스크랩 토글, 인기도 점수 재계산 트리거

### notification
- **Purpose**: 실시간 알림
- **Responsibilities**: SSE 기반 푸시 알림, 미읽은 알림 수, 알림 히스토리

### therapist / admin
- **Purpose**: 치료사 자격 인증 워크플로우
- **Responsibilities**: 자격증 제출, 관리자 승인/반려

### knowledge / autocomment
- **Purpose**: AI 기반 지식 베이스 및 자동 댓글
- **Responsibilities**: PDF 추출, 청크 임베딩, RAG 파이프라인, Gemini 기반 댓글 생성

### analytics
- **Purpose**: 사용자 행동 분석
- **Responsibilities**: 비동기 이벤트 수집, 파티션 집계, 치료사 전문성 일간 요약

### global
- **Purpose**: 공통 인프라
- **Responsibilities**: SecurityConfig, JWT 필터, 예외 처리, Redis/S3 설정, BaseEntity, ApiResponse
