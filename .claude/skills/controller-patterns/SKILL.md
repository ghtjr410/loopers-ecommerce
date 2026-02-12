---
name: controller-patterns
description: Spring Controller + DTO 구현 패턴. "Controller 구현해줘", "API 만들어줘", "DTO 작성해줘" 요청 시 사용. ApiSpec 인터페이스, Controller 구현, Bean Validation 패턴 제공.
---

# Controller Patterns

Controller 계층 구현 가이드입니다.

## 필수 규칙 참조

- `.claude/rules/layer/api.md` - API Layer 상세 규칙 ⭐
- `.claude/rules/core/layer-patterns.md` - Controller 역할
- `.claude/rules/core/naming-conventions.md` - 네이밍 규칙
- `.claude/rules/core/dto-patterns.md` - DTO 패턴

---

## 핵심 규칙 요약

| 항목 | 규칙 |
|------|------|
| HTTP Method | PUT 사용 안 함, **PATCH로 통일** |
| HTTP Status | 성공은 **200 통일** (201, 204 사용 안 함) |
| Controller 역할 | HTTP 변환만, 비즈니스 로직 금지 |
| 인증 헤더 검증 | **존재 여부만** 검증 허용 |
| 예외 처리 | try-catch 금지, 글로벌 핸들러가 처리 |

> 상세 규칙은 `.claude/rules/layer/api.md` 참조

---

## 패키지 구조

```
com.loopers.interfaces.api.{domain}/
├── {Domain}ApiV{version}Spec.java    # API 스펙 인터페이스
├── {Domain}V{version}Controller.java # Controller 구현
└── {Domain}V{version}Dto.java        # DTO 컨테이너
```

---

## 1. ApiSpec 인터페이스

**목적:** API 명세를 인터페이스로 분리 (Swagger 문서화 + 구현 분리)

### 템플릿

```java
@Tag(name = "{Domain} API", description = "{도메인} 관련 API")
public interface {Domain}ApiV1Spec {

    @Operation(summary = "{요약}", description = "{상세 설명}")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공"),
        @ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
        @ApiResponse(responseCode = "404", description = "리소스 없음"),
        @ApiResponse(responseCode = "409", description = "중복 리소스")
    })
    @PostMapping("/api/v1/{domain}s")
    ApiResponse<{Domain}V1Dto.{Domain}Response> create(
        @Valid @RequestBody {Domain}V1Dto.CreateRequest request
    );
}
```

### 규칙

| 항목 | 규칙 |
|------|------|
| 네이밍 | `{Domain}ApiV{version}Spec` |
| 어노테이션 | `@Tag`, `@Operation`, `@ApiResponses` |
| 파라미터 | `@Valid`, `@RequestBody`, `@PathVariable` |
| 반환 타입 | `ApiResponse<T>` |

---

## 2. Controller 구현

**목적:** HTTP 요청/응답 변환만 담당 (비즈니스 로직 없음)

### 템플릿

```java
@RestController
@RequiredArgsConstructor
public class {Domain}V1Controller implements {Domain}ApiV1Spec {

    private final {Domain}Facade {domain}Facade;

    @Override
    public ApiResponse<{Domain}V1Dto.{Domain}Response> create({Domain}V1Dto.CreateRequest request) {
        {Domain}Info info = {domain}Facade.create(
            request.field1(),
            request.field2(),
            request.field3()
        );
        return ApiResponse.success({Domain}V1Dto.{Domain}Response.from(info));
    }
}
```

### 규칙

| 항목 | 규칙 |
|------|------|
| 네이밍 | `{Domain}V{version}Controller` |
| 어노테이션 | `@RestController`, `@RequiredArgsConstructor` |
| 의존성 | Facade만 주입 (Service 직접 호출 금지) |
| 데이터 전달 | Request에서 **원시값 추출**하여 Facade 전달 |
| 반환 | `ApiResponse.success(Response.from(Info))` |

### 금지 사항

```java
// ❌ 금지: request 객체를 그대로 전달
{domain}Facade.create(request);

// ❌ 금지: try-catch 사용
try {
    ...
} catch (Exception e) {
    return ApiResponse.fail(...);
}

// ❌ 금지: 비즈니스 로직 수행
if (request.amount() > 10000) {
    throw new CoreException(...);
}
```

### 허용 사항

```java
// ✅ 허용: 인증 헤더 존재 여부만 검증
private void validateAuthHeaders(String loginId, String password) {
    if (loginId == null || loginId.isBlank() || password == null || password.isBlank()) {
        throw new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 필요합니다");
    }
}
```

> 인증 헤더 **존재 여부**만 검증. 비밀번호 일치 등 비즈니스 검증은 Service에서.

### 올바른 패턴

```java
// ✅ 원시값 추출하여 전달
{Domain}Info info = {domain}Facade.create(
    request.loginId(),
    request.password(),
    request.name(),
    request.email(),
    request.birthDate()
);

// ✅ 단순 변환 후 반환
return ApiResponse.success({Domain}V1Dto.{Domain}Response.from(info));
```

---

## 3. DTO 컨테이너

**목적:** 버전별 Request/Response를 하나의 클래스에서 관리

### 템플릿

```java
public class {Domain}V1Dto {

    // === Request DTOs ===

    public record CreateRequest(
        @NotBlank(message = "필드1은 필수입니다")
        @Size(min = 4, max = 20, message = "필드1은 4-20자여야 합니다")
        String field1,

        @NotBlank(message = "필드2는 필수입니다")
        String field2,

        @NotBlank(message = "이메일은 필수입니다")
        @Size(max = 100, message = "이메일은 100자를 초과할 수 없습니다")
        String email,  // 형식 검증은 Entity에서

        LocalDate birthDate  // 선택 필드, 날짜 규칙은 Entity에서
    ) {}

    public record UpdateRequest(
        @NotBlank(message = "필드1은 필수입니다")
        String field1
    ) {}

    // === Response DTOs ===

    public record {Domain}Response(
        Long id,
        String field1,
        String field2,
        LocalDateTime createdAt
    ) {
        public static {Domain}Response from({Domain}Info info) {
            return new {Domain}Response(
                info.id(),
                info.field1(),
                info.field2(),
                info.createdAt()
            );
        }
    }

    public record {Domain}ListResponse(
        List<{Domain}Response> items,
        int totalCount
    ) {
        public static {Domain}ListResponse from(List<{Domain}Info> infos) {
            return new {Domain}ListResponse(
                infos.stream()
                    .map({Domain}Response::from)
                    .toList(),
                infos.size()
            );
        }
    }
}
```

### 규칙

| 항목 | 규칙 |
|------|------|
| 컨테이너 네이밍 | `{Domain}V{version}Dto` |
| Request 네이밍 | `{Action}Request` (예: `CreateRequest`, `UpdateRequest`) |
| Response 네이밍 | `{Domain}Response`, `{Domain}ListResponse` |
| 타입 | Java `record` 사용 (불변성) |
| 변환 메서드 | `from(Info)` 정적 팩토리 |

---

## 4. Bean Validation

**목적:** 입력값 검증 (존재 여부 + 기본 범위)

> **중요:** 형식/규칙 검증(`@Pattern`, `@Email` 등)은 **도메인 불변식**으로, Entity에서 검증

### 허용 어노테이션 (존재 + 범위만)

| 어노테이션 | 용도 | 예시 |
|-----------|------|------|
| `@NotNull` | null 불가 | 필수 객체 |
| `@NotBlank` | null, 빈문자열, 공백만 불가 | 필수 문자열 |
| `@NotEmpty` | null, 빈 컬렉션 불가 | 필수 리스트 |
| `@Size` | 길이/크기 범위 | `@Size(min=4, max=20)` |
| `@Min`, `@Max` | 숫자 범위 | `@Min(0)`, `@Max(100)` |
| `@Positive` | 양수만 | 수량, 가격 |

### 사용 금지 (도메인 불변식으로 이동)

| 어노테이션 | 이유 | 대신 |
|-----------|------|------|
| `@Pattern` | 형식 규칙은 도메인 불변식 | Entity에서 정규식 검증 |
| `@Email` | 이메일 형식은 도메인 불변식 | Entity에서 검증 |
| `@Past`, `@Future` | 날짜 규칙은 도메인 불변식 | Entity에서 검증 |

### 검증 예시

```java
public record SignUpRequest(
    // 존재 + 범위만
    @NotBlank(message = "로그인 ID는 필수입니다")
    @Size(min = 4, max = 20, message = "로그인 ID는 4-20자여야 합니다")
    String loginId,

    // 존재 + 범위만
    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, max = 20, message = "비밀번호는 8-20자여야 합니다")
    String password,

    // 존재 + 범위만 (이메일 형식은 Entity에서 검증)
    @NotBlank(message = "이메일은 필수입니다")
    @Size(max = 100, message = "이메일은 100자를 초과할 수 없습니다")
    String email,

    // 선택 필드 (날짜 규칙은 Entity에서 검증)
    LocalDate birthDate
) {}
```

### 검증 책임 분리

| 계층 | 담당 | 예시 |
|------|------|------|
| **Request DTO** | 존재 여부 + 기본 범위 | `@NotBlank`, `@Size`, `@Min`, `@Max` |
| **Domain Entity** | 형식 + 비즈니스 규칙 | 정규식, 이메일 형식, 날짜 규칙, cross-field 검증 |
| **Service** | 비즈니스 검증 | 중복 체크, 권한 검증 |

### Entity에서 형식 검증 예시

```java
// Entity 내부
private static void validateLoginId(String loginId) {
    if (!loginId.matches("^[a-z][a-z0-9]*$")) {
        throw new IllegalArgumentException("로그인 ID는 영문 소문자로 시작하고, 영문 소문자와 숫자만 허용됩니다");
    }
}

private static void validateEmail(String email) {
    if (!email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
        throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다");
    }
}

private static void validateBirthDate(LocalDate birthDate) {
    if (birthDate != null && !birthDate.isBefore(LocalDate.now())) {
        throw new IllegalArgumentException("생년월일은 과거 날짜만 허용됩니다");
    }
}
```

---

## 5. HTTP Method별 패턴

### POST (생성)

```java
// ApiSpec
@PostMapping("/api/v1/users")
ApiResponse<UserResponse> create(@Valid @RequestBody CreateRequest request);

// Controller
@Override
public ApiResponse<UserResponse> create(CreateRequest request) {
    UserInfo info = userFacade.create(
        request.loginId(),
        request.password(),
        request.name()
    );
    return ApiResponse.success(UserResponse.from(info));
}
```

### GET (단건 조회)

```java
// ApiSpec
@GetMapping("/api/v1/users/{id}")
ApiResponse<UserResponse> getById(@PathVariable Long id);

// Controller
@Override
public ApiResponse<UserResponse> getById(Long id) {
    UserInfo info = userFacade.getById(id);
    return ApiResponse.success(UserResponse.from(info));
}
```

### GET (목록 조회)

```java
// ApiSpec
@GetMapping("/api/v1/users")
ApiResponse<UserListResponse> getList(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
);

// Controller
@Override
public ApiResponse<UserListResponse> getList(int page, int size) {
    List<UserInfo> infos = userFacade.getList(page, size);
    return ApiResponse.success(UserListResponse.from(infos));
}
```

### PATCH (수정)

> PUT 사용 안 함, **PATCH로 통일**

```java
// ApiSpec
@PatchMapping("/api/v1/users/{id}")
ApiResponse<UserResponse> update(
    @PathVariable Long id,
    @Valid @RequestBody UpdateRequest request
);

// Controller
@Override
public ApiResponse<UserResponse> update(Long id, UpdateRequest request) {
    UserInfo info = userFacade.update(id, request.name(), request.email());
    return ApiResponse.success(UserResponse.from(info));
}
```

### DELETE (삭제)

```java
// ApiSpec
@DeleteMapping("/api/v1/users/{id}")
ApiResponse<Void> delete(@PathVariable Long id);

// Controller
@Override
public ApiResponse<Void> delete(Long id) {
    userFacade.delete(id);
    return ApiResponse.success(null);
}
```

---

## 체크리스트

### ApiSpec
- [ ] `@Tag`로 API 그룹 정의
- [ ] `@Operation`으로 요약/설명 작성
- [ ] `@ApiResponses`로 응답 코드 문서화
- [ ] `@Valid` 적용

### Controller
- [ ] `@RestController`, `@RequiredArgsConstructor` 적용
- [ ] Facade만 의존 (Service 직접 호출 금지)
- [ ] Request에서 원시값 추출하여 전달
- [ ] try-catch 사용 안 함
- [ ] 비즈니스 로직 없음

### DTO
- [ ] record로 작성 (불변성)
- [ ] 버전별 컨테이너 클래스 사용
- [ ] Request: Bean Validation 적용
- [ ] Response: `from(Info)` 정적 팩토리
- [ ] Domain Entity import 안 함

---

## 트러블슈팅

### 1. @Valid가 동작하지 않음

**원인:** `@RequestBody` 없이 `@Valid`만 사용
```java
// ❌ 잘못됨
ApiResponse<UserResponse> create(@Valid CreateRequest request);

// ✅ 올바름
ApiResponse<UserResponse> create(@Valid @RequestBody CreateRequest request);
```

### 2. Validation 에러 메시지가 출력되지 않음

**원인:** `message` 속성 누락
```java
// ❌ 메시지 없음
@NotBlank
String loginId;

// ✅ 메시지 있음
@NotBlank(message = "로그인 ID는 필수입니다")
String loginId;
```

### 3. Controller에서 비즈니스 검증 로직 작성

**문제:** Controller는 HTTP 변환만 담당
```java
// ❌ Controller에서 비즈니스 검증
if (request.amount() > 10000) {
    throw new CoreException(ErrorType.BAD_REQUEST, "금액 초과");
}

// ✅ Service에서 비즈니스 검증 (Controller는 전달만)
userFacade.create(request.amount());  // 검증은 Service에서
```

### 4. @Pattern, @Email 사용하면 안 됨

**이유:** 형식 검증은 도메인 불변식으로, Entity에서 검증
```java
// ❌ DTO에서 형식 검증
@Email
String email;

// ✅ DTO는 존재+범위만, Entity에서 형식 검증
@NotBlank
@Size(max = 100)
String email;
```

### 5. Response에서 Entity 직접 사용

**문제:** Response는 Info를 통해 변환
```java
// ❌ Entity 직접 import
public static UserResponse from(User user) { ... }

// ✅ Info를 통해 변환
public static UserResponse from(UserInfo info) { ... }
```

---

## 참조 문서

| 문서 | 설명 |
|------|------|
| [user-controller.md](./examples/user-controller.md) | 회원 Controller 예시 |
