# Ops TODO

목표: MVP 기능 개발을 우선하면서, 운영 알림(CloudWatch) 작업을 누락 없이 추적한다.

## Now (코드 레벨)

- [ ] `TherapistVerificationService`에 이벤트 로그 키 추가
- [ ] 로그 키: `FILE_DELETE_FAILED`
- [ ] 로그 키: `THERAPIST_APPLY_FAILED_AFTER_UPLOAD`
- [ ] 로그 필드에 `userId`, `licenseCode`, `storedPath`, `reason` 포함
- [ ] `TherapistVerificationServiceTest`에서 실패 경로 회귀 테스트 통과 확인

## Before Prod Deploy (AWS/EC2)

- [ ] EC2 IAM Role에 CloudWatch Logs 권한 부여
- [ ] CloudWatch Agent 설치 및 `backend` journald 수집 설정
- [ ] Log Group 생성: `/ec2/backend`
- [ ] Metric Filter 생성: `FILE_DELETE_FAILED` -> `Backend/FileDeleteFailedCount`
- [ ] Metric Filter 생성: `THERAPIST_APPLY_FAILED_AFTER_UPLOAD` -> `Backend/TherapistApplyFailedAfterUploadCount`
- [ ] SNS Topic 생성: `backend-alerts`
- [ ] Email/Slack 구독 연결 및 수신 확인
- [ ] Alarm 생성: `FileDeleteFailedCount` `Sum >= 1` (5분)
- [ ] Alarm 생성: `TherapistApplyFailedAfterUploadCount` `Sum >= 1` (5분)
- [ ] Missing data 정책: `not breaching`

## Validation

- [ ] 의도적으로 실패 로그 1건 발생시켜 Metric 반영 확인
- [ ] Alarm 발화 및 알림 수신 확인
- [ ] 운영 Runbook(조치 절차) 문서 링크 추가

## Later (고도화)

- [ ] `AUTH_401` 급증 알림 추가
- [ ] 5xx 급증 알림 추가
- [ ] orphan 파일 주기 정리 배치(일 1회) 추가
- [ ] CloudWatch 대시보드(오류/인증/파일 처리) 구성
