# Stage 1: Build
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon || true
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

# JVM/system timezone을 KST로 통일.
# LocalDateTime.now()가 KST wall clock을 반환하도록 하여 timezone-naive 직렬화로 인한
# 9시간 차이 이슈(QA-011) 방지.
ENV TZ=Asia/Seoul

# t3.small(2GB)에서 호스트 OS/Docker daemon 여유를 두고 힙 상한을 명시.
# OOM 발생 시 즉시 종료 → docker restart 정책으로 자동 재기동 유도.
# user.timezone을 JVM에 직접 명시(TZ env와 이중 안전장치).
# 필요 시 `-e JAVA_TOOL_OPTIONS=...`로 런타임 오버라이드 가능.
ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx768m -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Seoul"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
