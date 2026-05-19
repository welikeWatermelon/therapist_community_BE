# Technology Stack

## Programming Languages
- Java 17

## Frameworks
- Spring Boot 3.5.10
- Spring Security (JWT)
- Spring Data JPA (Hibernate)
- Spring Data Redis
- Spring AOP
- Spring Actuator

## Database
- PostgreSQL 16 (prod/dev) + pgvector extension
- H2 (test, in-memory)
- Flyway 1.8.0 (43 migrations, V1-V43)

## Caching
- Redis (login throttling, view count, profile cache)
- Caffeine (embedding query LRU cache)

## File Storage
- AWS SDK 2.32.22 (S3 + Presigner)
- LocalFileStorage (dev fallback)

## AI/ML
- Spring AI BOM 1.0.0 (OpenAI embedding client)
- Google Gemini (embedding + chat, custom REST client)
- pgvector 0.1.6 (HNSW index, cosine similarity)
- Apache PDFBox 3.0.4 (PDF extraction)

## Security
- JJWT 0.11.5 (HS256)
- BCrypt password encoder

## API Documentation
- SpringDoc OpenAPI 2.8.5

## Testing
- JUnit 5
- Spring Boot Test
- Testcontainers (PostgreSQL)
- MockMvc

## Build Tools
- Gradle 8.x
- io.spring.dependency-management 1.1.7
