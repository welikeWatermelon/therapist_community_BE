FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app
COPY build/libs/*.jar app.jar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /app/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
