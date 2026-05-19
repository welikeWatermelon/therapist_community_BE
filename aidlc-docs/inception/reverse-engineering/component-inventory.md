# Component Inventory

## Application Packages (17 domains)
- auth - 인증/인가 (signup, login, JWT)
- user - 사용자 프로필, 역할 관리
- post - 게시글 CRUD, 검색, 피드
- comment - 스레드형 댓글
- reaction - 게시글/댓글 리액션
- scrap - 게시글 북마킹
- therapist - 치료사 자격 인증
- admin - 관리자 기능
- notification - SSE 실시간 알림
- file - 파일 저장소 추상화
- knowledge - 지식 베이스 (pgvector)
- autocomment - AI 자동 댓글
- analytics - 사용자 이벤트 분석
- application - MyPage 퍼사드
- meta - 홈/약관 엔드포인트
- global - 공통 인프라

## Test Packages
- 62 test files, 9,008 lines
- Unit tests (~55), Controller tests (~8), Integration tests (2)

## Total Count
- **Total Packages**: 17
- **Application**: 15
- **Infrastructure**: 1 (global)
- **Facade**: 1 (application)
- **Test Files**: 62
