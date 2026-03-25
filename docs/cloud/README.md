# Cloud Setup README

클라우드 팀이 이 리포를 받아 바로 서버 설정을 시작할 수 있도록, 현재 S3/프로필 구성을 빠르게 정리한 문서입니다.

## 1. 프로필 구조

- `local`
  - 개발자 로컬 실행용
  - 파일 저장소: 로컬 디스크
  - 설정 파일: `src/main/resources/application-local.yaml`
- `dev`
  - 개발 서버용
  - 파일 저장소: S3
  - 설정 파일: `src/main/resources/application-dev.yaml`
- `prod`
  - 운영 서버용
  - 파일 저장소: S3
  - 설정 파일: `src/main/resources/application-prod.yaml`

공통 설정은 `src/main/resources/application.yaml`에 있습니다.

## 2. 파일 저장 방식

- `local`에서는 `LocalFileStorageService` 사용
- `!local`에서는 `S3FileStorage` 사용
- `S3Config`도 `!local`에서만 로드되도록 구성

즉, 서버(`dev`, `prod`)에서는 S3 관련 환경변수가 반드시 필요합니다.

## 3. 서버 필수 환경변수

```bash
SPRING_PROFILES_ACTIVE=prod   # 개발 서버면 dev

SPRING_DATASOURCE_URL=jdbc:postgresql://<DB_HOST>:5432/<DB_NAME>
SPRING_DATASOURCE_USERNAME=<DB_USER>
SPRING_DATASOURCE_PASSWORD=<DB_PASSWORD>

JWT_ACCESS_SECRET=<LONG_RANDOM_SECRET>
JWT_ACCESS_TTL_SEC=1800
JWT_REFRESH_TTL_SEC=1209600

APP_CORS_ALLOWED_ORIGINS=https://app.example.com
APP_AWS_REGION=ap-northeast-2
APP_AWS_S3_BUCKET=<S3_BUCKET_NAME>
```

여러 프론트 도메인을 허용해야 하면 쉼표로 구분합니다.

```bash
APP_CORS_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com
```

## 4. AWS 인증 방식

코드에는 access key / secret key를 직접 넣지 않습니다.

- EC2면 Instance Profile / IAM Role
- ECS면 Task Role
- 그 외 환경이면 AWS SDK 기본 credential chain

즉, 이 리포에서는 버킷 이름과 리전만 설정하고 실제 AWS 자격증명은 서버 실행 환경이 제공합니다.

## 5. 바로 확인할 파일

- `src/main/resources/application.yaml`
- `src/main/resources/application-dev.yaml`
- `src/main/resources/application-prod.yaml`
- `src/main/java/com/therapyCommunity_Vol1/backend/global/config/S3Config.java`
- `src/main/java/com/therapyCommunity_Vol1/backend/file/service/S3FileStorage.java`
- `docs/SYSTEMD_DEPLOYMENT.md`
- `docs/CLOUD_HANDOFF_PACKAGE.md`

## 6. 실행 전 체크리스트

- 서버 프로필이 `dev` 또는 `prod`인지 확인
- DB 접속 정보가 들어갔는지 확인
- `APP_AWS_REGION`, `APP_AWS_S3_BUCKET`가 들어갔는지 확인
- AWS Role 권한이 대상 버킷에 접근 가능한지 확인
- `APP_CORS_ALLOWED_ORIGINS`가 실제 프론트 도메인과 맞는지 확인

## 7. 참고 문서

- `docs/SYSTEMD_DEPLOYMENT.md`
- `docs/CLOUD_HANDOFF_PACKAGE.md`
