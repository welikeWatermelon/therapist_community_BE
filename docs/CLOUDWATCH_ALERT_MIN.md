# CloudWatch Alert (Minimal)

목표: `FILE_DELETE_FAILED` 로그가 발생하면 이메일 알림을 받는다.

## 1) 애플리케이션 로그 확인
- 서비스 로그에서 아래 패턴이 출력되어야 함
- 패턴: `FILE_DELETE_FAILED`

## 2) CloudWatch Logs로 EC2 로그 수집
- CloudWatch Agent 설치/설정
- `journalctl -u backend` 또는 앱 로그 파일을 Log Group으로 전송
- 예시 Log Group: `/ec2/backend`

## 3) Metric Filter 생성
- CloudWatch -> Logs -> Log groups -> `/ec2/backend`
- `Create metric filter`
- Filter pattern: `FILE_DELETE_FAILED`
- Metric namespace: `Backend`
- Metric name: `FileDeleteFailedCount`
- Metric value: `1`

## 4) Alarm 생성
- CloudWatch -> Alarms -> `Create alarm`
- Metric: `Backend / FileDeleteFailedCount`
- 조건: `Sum >= 1` (1분~5분)
- Missing data: `not breaching`

## 5) 알림 채널(SNS)
- 새 SNS Topic 생성 (예: `backend-alerts`)
- Email 구독 추가
- 메일 수신 후 `Confirm subscription` 클릭

## 6) 테스트
- 의도적으로 파일 삭제 실패를 1회 유도
- CloudWatch Metric 증가 확인
- 이메일 알림 수신 확인

## 7) 운영 권장
- 동일 방식으로 `AUTH_401` 급증, 5xx 급증 알림을 추가
