# Naming Conventions

## 클래스 네이밍

### API 계층 (interfaces/api/)
- Controller: `{Domain}V{version}Controller` (예: `UserV1Controller`)
- API Spec: `{Domain}ApiV{version}Spec` (예: `UserApiV1Spec`)
- DTO Container: `{Domain}V{version}Dto` (예: `UserV1Dto`)
- Request DTO: `{Action}Request` (내부 record, 예: `SignUpRequest`)
- Response DTO: `{Domain}Response` (내부 record, 예: `UserResponse`)

### Application 계층 (application/)
- Facade: `{Domain}Facade` (예: `UserFacade`)
- Info: `{Domain}Info` (예: `UserInfo`)

### Domain 계층 (domain/)
- Entity: `{Domain}` (예: `User`)
- Service: `{Domain}Service` (예: `UserService`)
- Repository: `{Domain}Repository` (예: `UserRepository`)

### Infrastructure 계층 (infrastructure/)
- Repository 구현: `{Domain}RepositoryImpl` (예: `UserRepositoryImpl`)
- JPA Repository: `{Domain}JpaRepository` (예: `UserJpaRepository`)
- 인터페이스 구현: `{Prefix}{Concept}` (예: `BcryptPasswordEncoder`)

## 메서드 네이밍

### Repository 메서드
- 저장: `save(entity)`
- 단일 조회: `findBy{Field}(value)` → `Optional<T>` 반환
- 존재 여부: `existsBy{Field}(value)` → `boolean` 반환

### DTO 변환 메서드
- 팩토리 메서드: `from(source)` (예: `UserResponse.from(UserInfo)`)

### 도메인 엔티티 메서드
- 정적 팩토리: `create(...)` (생성자 대신 사용)
- 비즈니스 메서드: 동사로 시작 (예: `changePassword`, `delete`, `restore`)

### 검증 메서드
- private 검증: `validate{Field}(value)` (예: `validateLoginId`)
