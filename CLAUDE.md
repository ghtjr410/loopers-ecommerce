# CLAUDE.md

This file provides guidance to Claude Code when working with this codebase.

## Project Overview

**loopers-java-spring-template** is an enterprise-grade multi-module Spring Boot project template implementing Domain-Driven Design (DDD) with Hexagonal Architecture.

## Technology Stack

- **Java**: 21
- **Spring Boot**: 3.4.4
- **Build System**: Gradle (Kotlin DSL)
- **Database**: MySQL 8.0 with Spring Data JPA + QueryDSL
- **Cache**: Redis (Master-Replica)
- **Messaging**: Apache Kafka
- **Testing**: JUnit 5, TestContainers, Mockito

## Project Structure

```
├── apps/                    # Executable Spring Boot Applications
│   ├── commerce-api         # REST API (port 8080)
│   ├── commerce-batch       # Batch processing
│   └── commerce-streamer    # Kafka stream processing
├── modules/                 # Infrastructure Modules
│   ├── jpa                  # JPA/Hibernate configuration
│   ├── redis                # Redis configuration
│   └── kafka                # Kafka configuration
├── supports/                # Cross-cutting Support Modules
│   ├── jackson              # JSON serialization
│   ├── logging              # Logging configuration
│   └── monitoring           # Actuator/Prometheus metrics
```

## Build Commands

```bash
# Build all modules
./gradlew build

# Build without tests
./gradlew build -x test

# Run specific application
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun
./gradlew :apps:commerce-streamer:bootRun

# Run tests
./gradlew test

# Run tests with coverage
./gradlew test jacocoTestReport

# Clean build
./gradlew clean build
```

## Architecture

### Layered Package Structure (DDD)

```
com.loopers/
├── interfaces/          # REST controllers, DTOs, API specs
├── application/         # Facades, use cases, application DTOs
├── domain/              # Entities, domain services, repository interfaces
├── infrastructure/      # Repository implementations, external adapters
└── support/             # Cross-cutting concerns (errors, utils)
```

### Key Patterns

1. **Repository Pattern**: Interface in `domain/`, implementation in `infrastructure/`
2. **Facade Pattern**: Application layer orchestrates domain services
3. **Records for DTOs**: Immutable data transfer objects
4. **Soft Delete**: Entities use `deletedAt` field instead of hard delete

### Base Entity

All JPA entities extend `BaseEntity` providing:
- Auto-generated ID
- `createdAt`, `updatedAt` timestamps
- `deletedAt` for soft delete
- `delete()` and `restore()` methods

## Coding Conventions

### Creating New Features

1. **Controller** (`interfaces/api/`): Define `*ApiSpec` interface + `*Controller` implementation
2. **Facade** (`application/`): Create facade for orchestration + `*Info` records
3. **Service** (`domain/`): Domain service with business logic
4. **Repository** (`domain/`): Define repository interface
5. **Repository Impl** (`infrastructure/`): Implement repository with JPA

### API Response Format

```java
ApiResponse.success(data)    // Successful response
ApiResponse.fail(errorType)  // Error response
```

### Exception Handling

```java
throw new CoreException(ErrorType.NOT_FOUND);
throw new CoreException(ErrorType.BAD_REQUEST, "Custom message");
```

### Error Types

- `INTERNAL_ERROR` (500)
- `BAD_REQUEST` (400)
- `NOT_FOUND` (404)
- `CONFLICT` (409)

## Configuration

### Profiles

- `local`: Local development with Docker services
- `dev`, `qa`, `prd`: Environment-specific configurations

### Ports

- Application: 8080
- Actuator/Monitoring: 8081

### Docker Services (Local)

```bash
# Start local infrastructure
docker-compose -f docker/docker-compose.yml up -d
```

- MySQL: localhost:3306
- Redis Master: localhost:6379
- Redis Replica: localhost:6380
- Kafka: localhost:19092

## Testing

### Test Categories

- **Unit Tests**: Domain model validation
- **Integration Tests**: Service + Repository with TestContainers
- **E2E Tests**: Full API endpoint testing

### Test Utilities

- `DatabaseCleanUp`: Cleans tables after each test
- TestContainers: Provides real MySQL, Redis, Kafka instances
- Test fixtures in `supports/` modules

### Running Tests

```bash
# All tests
./gradlew test

# Specific module tests
./gradlew :apps:commerce-api:test

# With coverage report
./gradlew test jacocoTestReport
```
