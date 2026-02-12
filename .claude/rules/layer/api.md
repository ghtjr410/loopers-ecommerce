---
paths:
  - "**/interfaces/api/**/*.java"
---
# API Layer Rules

## URL 경로 규칙
- 리소스명은 **복수형** 사용: `/api/v1/users`, `/api/v1/orders`
- 케이스: **kebab-case** (단어 구분 시)
- 네스팅 깊이: **최대 4단계** (`/api/v1/users/me/password`)
- 버전 관리: 클래스명에 `V1`, `V2` 포함 (URL과 동일)
- 행위성 엔드포인트: 리소스로 표현 불가능한 경우만 허용

## HTTP 메서드 규칙
| 메서드 | 용도 | 멱등성 |
|--------|------|--------|
| GET | 조회 | O |
| POST | 생성 | X |
| PATCH | 부분 수정 | O |
| DELETE | 삭제 (soft delete 포함) | O |

- PUT은 사용하지 않음 (PATCH로 통일)
- soft delete도 DELETE 메서드 사용

## Controller 구조
```
{Domain}ApiV{version}Spec.java   - 인터페이스 (Swagger 문서화)
{Domain}V{version}Controller.java - 구현체
{Domain}V{version}Dto.java        - Request/Response DTO 그룹
```

## Request 규칙
- **record** 사용 (불변 보장)
- 네이밍: `{Domain}V{version}Dto.{Action}Request`
  ```java
  UserV1Dto.SignUpRequest
  UserV1Dto.ChangePasswordRequest
  ```
- **Bean Validation 허용**: 입력값 형식 검증 목적
  - 허용 어노테이션: `@NotNull`, `@NotBlank`, `@NotEmpty`, `@Min`, `@Max`, `@Positive`, `@Size` 등
  - Controller에서 `@Valid` 사용
  - 비즈니스 규칙 검증은 도메인에서 수행 (관심사 분리)

## Response 규칙
- **ApiResponse<T>** 래핑 필수
- 네이밍: `{Domain}V{version}Dto.{Domain}Response`
  ```java
  UserV1Dto.UserResponse
  ```
- 변환 메서드: `from(Info)` 정적 팩토리 메서드 사용
  ```java
  public static UserResponse from(UserInfo info) { ... }
  ```

## Controller → Service 전달
- **원시값 파라미터로 전달** (Request DTO 그대로 넘기지 않음)
  ```java
  // Good
  userFacade.signUp(request.loginId(), request.password(), request.name());

  // Bad
  userFacade.signUp(request);
  ```
- DTO는 Domain Entity를 직접 알지 않음

## Controller 책임 범위

### 허용
- HTTP 요청/응답 변환
- 인증 헤더 필수값 검증 (존재 여부만)
- Facade/Service 호출
- Response 변환

### 금지
- 비즈니스 로직
- Repository/DB 직접 접근
- 조건 분기로 서비스 다르게 호출
- @Transactional 사용
- try-catch로 예외 처리

## 예외 처리
- `@RestControllerAdvice`에서 글로벌 처리
- Controller에서 try-catch 금지
- 비즈니스 예외: `CoreException(ErrorType.XXX)` throw
- ErrorType: `BAD_REQUEST(400)`, `UNAUTHORIZED(401)`, `NOT_FOUND(404)`, `CONFLICT(409)`, `INTERNAL_ERROR(500)`

## HTTP Status Code
- 성공: **200** 통일 (201, 204 사용하지 않음)
- 실패: ErrorType의 status 사용
