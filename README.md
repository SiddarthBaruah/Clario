# Core – Spring Boot 3 Application

Production-ready Spring Boot 3 project (Java 21) with JWT auth, JDBC, Flyway, and MCP/LLM integration placeholders.

## Requirements

- Java 21
- Maven 3.8+
- MySQL 8 (or compatible)

## Configuration

Set environment variables or override in `application.properties`:


| Variable                 | Description                          | Default                                  |
| ------------------------ | ------------------------------------ | ---------------------------------------- |
| `DB_HOST`                | MySQL host                           | localhost                                |
| `DB_PORT`                | MySQL port                           | 3306                                     |
| `DB_NAME`                | Database name                        | assistant_db                             |
| `DB_USERNAME`            | DB user                              | root                                     |
| `DB_PASSWORD`            | DB password                          | (empty)                                  |
| `JWT_SECRET`             | Secret for JWT signing (min 256-bit) | (dev default – **change in production**) |
| `JWT_ISSUER`             | JWT issuer                           | com.assistant.core                       |
| `JWT_EXPIRATION_SECONDS` | Token TTL                            | 86400                                    |
| `SERVER_PORT`            | HTTP port                            | 8080                                     |


## Build & Run

```bash
# Create DB first, e.g.:
# mysql -e "CREATE DATABASE clario_db;"

mvn clean package -DskipTests
java -jar target/core-1.0.0-SNAPSHOT.jar
```

Or with Maven:

```bash
mvn spring-boot:run
```

## API

- **POST /api/auth/login** – Login (JSON: `username`, `password`); returns JWT.
- **GET /api/health** – Health check (no auth).
- **GET /api/** (other) – Require `Authorization: Bearer <token>`.

Controllers use DTOs only; entities are not exposed. Business logic is in `service/`, data access in `repository/` with `NamedParameterJdbcTemplate` and RowMappers.

## Project layout (`src/main/java/com/assistant/core/`)

- **config/** – SecurityConfig, AppConfig, RestClientConfig  
- **controller/** – REST and webhook endpoints  
- **dto/** – Request/response DTOs  
- **mcp/** – LLMService (RestClient), ToolRouter, Tool interface, tools (create_task, list_tasks, add_person, retrieve_people)  
- **model/** – Domain models mapping to DB  
- **repository/** – JDBC repositories  
- **scheduler/** – Cron/scheduled tasks  
- **security/** – JwtFilter, JwtService, CustomUserDetailsService  
- **service/** – Business logic

Scheduling is enabled via `@EnableScheduling`. Database migrations: Flyway in `src/main/resources/db/migration/`.