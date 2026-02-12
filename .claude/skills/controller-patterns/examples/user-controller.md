# 회원 Controller 예시

User 도메인의 Controller 계층 구현 예시입니다.

---

## 파일 구조

```
com.loopers.interfaces.api.user/
├── UserApiV1Spec.java      # API 스펙 인터페이스
├── UserV1Controller.java   # Controller 구현
└── UserV1Dto.java          # DTO 컨테이너
```

---

## 1. UserApiV1Spec.java

```java
package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User API", description = "회원 관련 API")
public interface UserApiV1Spec {

    @Operation(summary = "회원가입", description = "신규 회원을 등록합니다")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "중복된 로그인 ID 또는 이메일")
    })
    @PostMapping("/api/v1/users")
    ApiResponse<UserV1Dto.UserResponse> signUp(
        @Valid @RequestBody UserV1Dto.SignUpRequest request
    );

    @Operation(summary = "회원 조회", description = "회원 정보를 조회합니다")
    @GetMapping("/api/v1/users/{id}")
    ApiResponse<UserV1Dto.UserResponse> getById(@PathVariable Long id);

    @Operation(summary = "회원 정보 수정", description = "회원 정보를 수정합니다 (PATCH 사용)")
    @PatchMapping("/api/v1/users/{id}")
    ApiResponse<UserV1Dto.UserResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UserV1Dto.UpdateRequest request
    );

    @Operation(summary = "회원 탈퇴", description = "회원을 탈퇴 처리합니다 (Soft Delete)")
    @DeleteMapping("/api/v1/users/{id}")
    ApiResponse<Void> delete(@PathVariable Long id);
}
```

---

## 2. UserV1Controller.java

```java
package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.user.dto.UserV1Dto;
import com.loopers.support.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserV1Controller implements UserApiV1Spec {

    private final UserFacade userFacade;

    @Override
    public ApiResponse<UserV1Dto.UserResponse> signUp(UserV1Dto.SignUpRequest request) {
        UserInfo info = userFacade.signUp(
            request.loginId(),
            request.password(),
            request.name(),
            request.email(),
            request.birthDate()
        );
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @Override
    public ApiResponse<UserV1Dto.UserResponse> getById(Long id) {
        UserInfo info = userFacade.getById(id);
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @Override
    public ApiResponse<UserV1Dto.UserResponse> update(Long id, UserV1Dto.UpdateRequest request) {
        UserInfo info = userFacade.update(
            id,
            request.name(),
            request.email()
        );
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @Override
    public ApiResponse<Void> delete(Long id) {
        userFacade.delete(id);
        return ApiResponse.success(null);
    }
}
```

---

## 3. UserV1Dto.java

```java
package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class UserV1Dto {

    // ========================================
    // Request DTOs
    // ========================================

    /**
     * Bean Validation: 존재 여부 + 기본 범위만 검증
     * 형식 검증(@Pattern, @Email, @Past)은 Entity 도메인 불변식에서 처리
     */
    public record SignUpRequest(
        @NotBlank(message = "로그인 ID는 필수입니다")
        @Size(min = 4, max = 20, message = "로그인 ID는 4-20자여야 합니다")
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다")
        @Size(min = 8, max = 20, message = "비밀번호는 8-20자여야 합니다")
        String password,

        @NotBlank(message = "이름은 필수입니다")
        @Size(min = 2, max = 20, message = "이름은 2-20자여야 합니다")
        String name,

        @NotBlank(message = "이메일은 필수입니다")
        @Size(max = 100, message = "이메일은 100자를 초과할 수 없습니다")
        String email,

        LocalDate birthDate  // 선택 필드, 날짜 규칙은 Entity에서 검증
    ) {}

    public record UpdateRequest(
        @NotBlank(message = "이름은 필수입니다")
        @Size(min = 2, max = 20, message = "이름은 2-20자여야 합니다")
        String name,

        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "이메일 형식이 올바르지 않습니다")
        @Size(max = 100, message = "이메일은 100자를 초과할 수 없습니다")
        String email
    ) {}

    // ========================================
    // Response DTOs
    // ========================================

    public record UserResponse(
        Long id,
        String loginId,
        String name,
        String email,
        LocalDate birthDate,
        LocalDateTime createdAt
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.id(),
                info.loginId(),
                info.maskedName(),  // 마스킹 처리된 이름
                info.email(),
                info.birthDate(),
                info.createdAt()
            );
        }
    }

    public record UserListResponse(
        List<UserResponse> users,
        int totalCount
    ) {
        public static UserListResponse from(List<UserInfo> infos) {
            return new UserListResponse(
                infos.stream()
                    .map(UserResponse::from)
                    .toList(),
                infos.size()
            );
        }
    }
}
```

---

## 핵심 포인트

### 1. Controller는 변환만 담당

```java
// Request → 원시값 추출 → Facade 호출
UserInfo info = userFacade.signUp(
    request.loginId(),    // 원시값 추출
    request.password(),
    request.name(),
    request.email(),
    request.birthDate()
);

// Info → Response 변환
return ApiResponse.success(UserV1Dto.UserResponse.from(info));
```

### 2. 비즈니스 로직 없음

```java
// ❌ 잘못된 예시 - Controller에서 비즈니스 로직
if (userFacade.existsByLoginId(request.loginId())) {
    throw new CoreException(ErrorType.CONFLICT);
}

// ✅ 올바른 예시 - 단순히 Facade 호출만
UserInfo info = userFacade.signUp(...);
```

### 3. try-catch 사용 안 함

```java
// ❌ 잘못된 예시
try {
    UserInfo info = userFacade.signUp(...);
    return ApiResponse.success(...);
} catch (CoreException e) {
    return ApiResponse.fail(e.getErrorType());
}

// ✅ 올바른 예시 - 글로벌 핸들러가 처리
UserInfo info = userFacade.signUp(...);
return ApiResponse.success(...);
```

### 4. Response는 Info에서 변환

```java
// Response가 Info를 알고 있음 (의존 방향: interfaces → application)
public static UserResponse from(UserInfo info) {
    return new UserResponse(
        info.id(),
        info.loginId(),
        info.maskedName(),  // Info의 가공 메서드 활용
        ...
    );
}
```

---

## 데이터 흐름

```
[HTTP Request]
     ↓
[Controller] - @Valid로 형식 검증
     ↓ request.field() (원시값 추출)
[Facade] - 오케스트레이션
     ↓
[Service] - 비즈니스 로직
     ↓
[Entity] - 도메인 불변식
     ↓
[Repository] - 영속화
     ↑
[Entity] - 저장된 엔티티
     ↓
[Info] - Entity → Info 변환 (Facade에서)
     ↓
[Response] - Info → Response 변환
     ↓
[HTTP Response]
```
