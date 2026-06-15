# [Cloud Handoff] Capacitor 모바일 CORS origin Parameter Store 등록 (MEL-56)

- 작성일: 2026-06-15
- 대상: 클라우드/배포 담당
- 관련 PR: backend #126 (`claude/mel-56-capacitor-cors`)
- 관련 Jira: MEL-56

## 1) 요약 (한 줄)

Capacitor 모바일 앱(WebView) CORS origin 2개를 web origin과 **동일하게 SSM Parameter Store에서 관리**하도록 통일합니다. 클라우드 팀은 **Parameter Store 항목 2개만 생성**하면 됩니다. (코드/배포 파이프라인은 backend에서 이미 반영)

대상 origin:
- `capacitor://localhost` — iOS Capacitor WebView
- `http://localhost` — Android Capacitor WebView

## 2) 배경 (왜 하는가)

기존 CORS web origin은 Parameter Store(`cors-origins`)에서 관리하는데, 모바일 origin은
backend 코드(`application.yaml`) 기본값으로만 존재해 **관리 위치가 갈려 있었습니다.**
운영팀이 `cors-origins`만 봐서는 모바일 origin의 존재를 알 수 없는 비대칭을 해소하기 위해,
모바일 origin도 Parameter Store로 올려 **모든 CORS origin을 한 곳에서** 보도록 통일합니다.

> 안전망: backend `application.yaml`에 기본값(`capacitor://localhost,http://localhost`)이
> 남아 있어, **Parameter Store 항목이 없어도 모바일 CORS는 깨지지 않습니다.**
> Parameter Store 등록은 "관리 통일/가시화"가 목적이며, 누락이 장애로 이어지지 않습니다.

## 3) 클라우드 팀 작업 (이것만 하면 됨)

기존 `cors-origins`와 동일한 네이밍 규칙(`/community/melonne/config/{env}/...`)으로
`cors-mobile-origins` 항목을 staging/prod 각각 1개씩 생성합니다.

```bash
# staging
aws ssm put-parameter \
  --name /community/melonne/config/staging/cors-mobile-origins \
  --type String \
  --value "capacitor://localhost,http://localhost" \
  --region ap-northeast-2

# prod
aws ssm put-parameter \
  --name /community/melonne/config/prod/cors-mobile-origins \
  --type String \
  --value "capacitor://localhost,http://localhost" \
  --region ap-northeast-2
```

- `--type String` 사용 (web `cors-origins`와 동일, 민감정보 아님 → Secrets Manager 아님)
- 값은 콤마 구분, 공백 없이. 향후 origin 추가 시 이 값에 콤마로 append
- IAM: 기존 배포 롤(`GitHubActionsMelonneCommunityDeployRole`)이 `ssm:GetParameter`로
  `/community/melonne/config/*`를 읽으므로 **추가 권한 변경 불필요** (기존 `cors-origins`와 동일 경로)

### 확인

```bash
aws ssm get-parameter --name /community/melonne/config/prod/cors-mobile-origins \
  --region ap-northeast-2 --query Parameter.Value --output text
# 기대 출력: capacitor://localhost,http://localhost
```

## 4) backend에서 이미 반영한 것 (참고)

클라우드 팀이 건드릴 필요 없음. PR #126에 포함.

- `.github/workflows/deploy.yml`: staging/prod 둘 다 `cors-mobile-origins`를 읽어
  `APP_CORS_MOBILE_ORIGINS`로 주입. **파라미터가 없으면 주입을 건너뛰고 yaml 기본값 사용**
  (배포 실패하지 않음 → Parameter Store 생성 순서와 무관):
  ```bash
  MOBILE=$(aws ssm get-parameter --name /community/melonne/config/{env}/cors-mobile-origins ... 2>/dev/null || true)
  [ x$MOBILE != x ] && echo APP_CORS_MOBILE_ORIGINS=$MOBILE >> /tmp/{env}.env || echo cors-mobile-origins absent, using yaml default
  ```
- `application.yaml`: `app.cors.mobile-origins: ${APP_CORS_MOBILE_ORIGINS:capacitor://localhost,http://localhost}` (안전망 기본값)
- `SecurityConfig`: web origins + mobile origins 병합(distinct), `allowCredentials=true` 유지, 와일드카드 미사용

## 5) 적용 순서 (순서 의존성 없음)

deploy.yml이 파라미터 부재에 관대하므로 **어느 것을 먼저 해도 안전**합니다.

1. (권장) 클라우드 팀이 Parameter Store 항목 2개 생성
2. backend PR #126 머지 → main/deploy 배포 시 자동 주입

항목 생성 전에 배포돼도 yaml 기본값으로 동작하고, 이후 항목 생성 + 재배포 시 Parameter Store 값이 적용됩니다.

## 6) 검증 (배포 후)

운영/스테이징 도메인에 모바일 origin으로 preflight 확인:

```bash
curl -i -X OPTIONS https://api.melonnetherapists.com/api/v1/home \
  -H "Origin: capacitor://localhost" \
  -H "Access-Control-Request-Method: GET"
# 기대: Access-Control-Allow-Origin: capacitor://localhost
#       Access-Control-Allow-Credentials: true
```

`http://localhost`(Android)도 동일하게 확인.

## 7) 보안 메모

- `allowCredentials=true` + `http://localhost` 조합은 본질적으로 로컬 프로세스 origin 위장 여지가 있습니다.
  현재 prod 쿠키가 `SameSite=Lax` + `Secure`라 노출이 제한되지만,
  **prod 쿠키가 `SameSite=None`으로 전환되면 이 origin은 재검토 대상**입니다.
- 와일드카드(`*`)는 credentialed CORS에서 사용 불가하며 사용하지 않습니다.
