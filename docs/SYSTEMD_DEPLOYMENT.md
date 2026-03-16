# Systemd Deployment Manual (EC2)

Spring Boot JAR를 EC2에 배포하고 `systemctl`로 운영하는 기준입니다.

## 1) 1회 초기 세팅 (EC2)

### 1-1. 디렉터리 생성

```bash
sudo mkdir -p /opt/backend/current
sudo mkdir -p /opt/backend/releases
sudo mkdir -p /etc/backend
sudo chown -R ubuntu:ubuntu /opt/backend
```

### 1-2. 환경변수 파일 생성

`/etc/backend/backend.env`:

```bash
sudo tee /etc/backend/backend.env > /dev/null <<'EOF'
SPRING_PROFILES_ACTIVE=prod

SPRING_DATASOURCE_URL=jdbc:postgresql://<RDS_ENDPOINT>:5432/postgres
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<DB_PASSWORD>

JWT_ACCESS_SECRET=<LONG_RANDOM_SECRET>
JWT_ACCESS_TTL_SEC=1800

APP_AWS_REGION=ap-northeast-2
APP_AWS_S3_BUCKET=melonne-stg-images
EOF
```

주의:
- 현재 코드에서 `S3Config`가 `app.aws.region`을 필수로 읽습니다.
- `S3FileStorage`가 `app.aws.s3.bucket`을 필수로 읽습니다.
- 누락되면 앱 시작 시 `UnsatisfiedDependencyException`으로 실패합니다.

### 1-3. systemd 서비스 등록

`/etc/systemd/system/backend.service`:

```ini
[Unit]
Description=Builders MVP Backend
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/backend/current
EnvironmentFile=/etc/backend/backend.env
ExecStart=/usr/bin/java -jar /opt/backend/current/backend.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

서비스 반영:

```bash
sudo systemctl daemon-reload
sudo systemctl enable backend
```

## 2) 배포 절차 (매 릴리스)

### 2-1. 로컬에서 JAR 빌드

```bash
./gradlew clean bootJar
```

생성물:
- `build/libs/backend-0.0.1-SNAPSHOT.jar`

### 2-2. EC2로 업로드

```bash
scp -i /Users/tom/dev/buildersMvp/melonne-key.pem \
  build/libs/backend-0.0.1-SNAPSHOT.jar \
  ubuntu@<EC2_PUBLIC_IP>:/tmp/backend.jar
```

### 2-3. 서비스 반영 및 재시작

```bash
ssh -i /Users/tom/dev/buildersMvp/melonne-key.pem ubuntu@<EC2_PUBLIC_IP> \
  "mv /tmp/backend.jar /opt/backend/current/backend.jar && sudo systemctl restart backend"
```

## 3) 상태 확인

```bash
ssh -i /Users/tom/dev/buildersMvp/melonne-key.pem ubuntu@<EC2_PUBLIC_IP>
sudo systemctl status backend
journalctl -u backend -n 200 --no-pager
```

헬스체크:

```bash
curl http://localhost:8080/api/v1/home
```

외부 확인:

- `http://<EC2_PUBLIC_IP>:8080/swagger-ui/index.html`
- `http://<EC2_PUBLIC_IP>:8080/v3/api-docs`

## 4) 자주 나는 에러와 해결

### 4-1. `s3Client`/`s3FileStorage` 빈 생성 실패

증상:
- `UnsatisfiedDependencyException`
- `Could not resolve placeholder 'app.aws.region'`

해결:
- `/etc/backend/backend.env`에 아래 2개가 있는지 확인
  - `APP_AWS_REGION`
  - `APP_AWS_S3_BUCKET`
- 반영 후 재시작

```bash
sudo systemctl restart backend
```

### 4-2. DB 연결 실패

확인:
- `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` 값
- RDS SG 인바운드(5432)에서 EC2 SG 허용 여부

### 4-3. 재시작했는데 옛 버전 동작

확인:
- `/opt/backend/current/backend.jar` 교체됐는지
- `sudo systemctl restart backend` 실행했는지
- `journalctl -u backend -n 50`에서 기동 시각 확인

## 5) 운영 팁

1. 배포 전 로컬 검증:
```bash
./gradlew build
```
2. 시크릿은 git에 커밋하지 말고 `backend.env`로만 관리
3. 서비스 로그는 `journalctl -u backend -f`로 실시간 확인
