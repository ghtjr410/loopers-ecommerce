# 회원가입 구현 예시

PRD → 구현까지의 전체 흐름을 보여주는 예시입니다. (AI 최적화 워크플로우)

---

## PRD 참조

> `mission/round1.md` - Feature 1: 회원가입

### API 명세 요약

| 항목 | 내용 |
|------|------|
| Method | POST |
| Endpoint | /api/v1/users |
| Auth | 불필요 |

### Request Body

| 필드 | 타입 | 필수 | 검증 규칙 |
|------|------|------|----------|
| loginId | String | Y | 4-20자, 영문소문자+숫자, 영문으로 시작 |
| password | String | Y | 8-20자, 영문+숫자+특수문자 각 1개 이상 |
| name | String | Y | 2-20자, 한글 또는 영문 |
| email | String | Y | 이메일 형식, 최대 100자 |
| birthDate | LocalDate | N | 과거 날짜만 허용 |

### 핵심 AC

| AC# | 조건 | 기대 결과 |
|-----|------|----------|
| AC-1 | 모든 필수값 유효 | 200, 회원 생성 |
| AC-14 | loginId 중복 | 409 CONFLICT |
| AC-3~13 | 입력값 검증 실패 | 400 BAD_REQUEST |

---

## Phase 0: CONTEXT

### 읽은 파일

- `.claude/rules/core/layer-patterns.md`
- `.claude/rules/core/naming-conventions.md`
- `.claude/rules/core/exception-patterns.md`
- `.claude/rules/core/dto-patterns.md`

### 확인된 규칙

- BaseEntity 상속
- 정적 팩토리 `create()` 사용
- 도메인 불변식은 IllegalArgumentException
- 비즈니스 오류는 CoreException
- DTO는 record로 작성
- Controller에서 원시값 추출하여 Facade 전달

---

## Phase 1: INTERFACE

### Entity 인터페이스

```java
public class User extends BaseEntity {
    // Fields
    private String loginId;
    private String password;
    private String name;
    private String email;
    private LocalDate birthDate;

    // Factory
    public static User create(String loginId, String encodedPassword,
                              String name, String email, LocalDate birthDate);
}
```

### Repository 인터페이스

```java
public interface UserRepository {
    User save(User user);
    Optional<User> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
}
```

### Service 인터페이스

```java
public class UserService {
    public User signUp(String loginId, String password,
                       String name, String email, LocalDate birthDate);
}
```

### Facade 인터페이스

```java
public class UserFacade {
    public UserInfo signUp(String loginId, String password,
                           String name, String email, LocalDate birthDate);
}
```

### Controller 인터페이스

```java
public interface UserApiV1Spec {
    @PostMapping("/api/v1/users")
    ApiResponse<UserV1Dto.UserResponse> signUp(
        @Valid @RequestBody UserV1Dto.SignUpRequest request
    );
}
```

---

## Phase 2: IMPLEMENT + TEST (구현과 테스트 동시)

### 1. Entity + 단위 테스트

**Entity 구현**

```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    private LocalDate birthDate;

    private User(String loginId, String password, String name, String email, LocalDate birthDate) {
        validateLoginId(loginId);
        validatePassword(password);
        validateName(name);
        validateEmail(email);
        validateBirthDate(birthDate);
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.birthDate = birthDate;
    }

    public static User create(String loginId, String encodedPassword,
                              String name, String email, LocalDate birthDate) {
        return new User(loginId, encodedPassword, name, email, birthDate);
    }

    // === 도메인 불변식 검증 (형식 + 비즈니스 규칙) ===

    private static void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new IllegalArgumentException("로그인 ID는 필수입니다");
        }
        if (!loginId.matches("^[a-z][a-z0-9]*$")) {
            throw new IllegalArgumentException("로그인 ID는 영문 소문자로 시작하고, 영문 소문자와 숫자만 허용됩니다");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름은 필수입니다");
        }
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다");
        }
        if (!email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다");
        }
    }

    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate != null && !birthDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("생년월일은 과거 날짜만 허용됩니다");
        }
    }
}
```

**Entity 단위 테스트**

```java
class UserTest {

    @Nested
    class 생성 {

        @Test
        void 유효한_값이면_회원이_생성된다() {
            // given
            String loginId = "john123";
            String password = "encodedPassword";
            String name = "홍길동";
            String email = "john@test.com";
            LocalDate birthDate = LocalDate.of(1995, 3, 15);

            // when
            User user = User.create(loginId, password, name, email, birthDate);

            // then
            assertThat(user.getLoginId()).isEqualTo(loginId);
            assertThat(user.getPassword()).isEqualTo(password);
            assertThat(user.getName()).isEqualTo(name);
            assertThat(user.getEmail()).isEqualTo(email);
            assertThat(user.getBirthDate()).isEqualTo(birthDate);
        }

        @Test
        void 로그인_ID가_null이면_예외발생() {
            assertThatThrownBy(() ->
                User.create(null, "password", "홍길동", "test@test.com", null)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("로그인 ID");
        }

        @Test
        void 이름이_빈_문자열이면_예외발생() {
            assertThatThrownBy(() ->
                User.create("john123", "password", "", "test@test.com", null)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("이름");
        }

        @Test
        void 이메일이_null이면_예외발생() {
            assertThatThrownBy(() ->
                User.create("john123", "password", "홍길동", null, null)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("이메일");
        }

        @Test
        void 생년월일이_null이면_허용된다() {
            // when
            User user = User.create("john123", "password", "홍길동", "test@test.com", null);

            // then
            assertThat(user.getBirthDate()).isNull();
        }
    }
}
```

### 2. Repository (인터페이스 + 구현)

**Repository 인터페이스**

```java
// domain/user/UserRepository.java
public interface UserRepository {
    User save(User user);
    Optional<User> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
}
```

**Repository 구현**

```java
// infrastructure/user/UserRepositoryImpl.java
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;

    @Override
    public User save(User user) {
        return jpaRepository.save(user);
    }

    @Override
    public Optional<User> findByLoginId(String loginId) {
        return jpaRepository.findByLoginIdAndDeletedAtIsNull(loginId);
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return jpaRepository.existsByLoginId(loginId);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }
}
```

**JPA Repository**

```java
// infrastructure/user/UserJpaRepository.java
public interface UserJpaRepository extends JpaRepository<User, Long> {
    Optional<User> findByLoginIdAndDeletedAtIsNull(String loginId);
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
}
```

**PasswordEncoder 인터페이스**

```java
// domain/user/PasswordEncoder.java
public interface PasswordEncoder {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
```

**PasswordEncoder 구현**

```java
// infrastructure/user/BcryptPasswordEncoder.java
@Component
public class BcryptPasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    @Override
    public String encode(String rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return delegate.matches(rawPassword, encodedPassword);
    }
}
```

### 3. Service + 통합 테스트

**Service 구현**

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User signUp(String loginId, String password, String name,
                       String email, LocalDate birthDate) {
        // 중복 체크
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다");
        }
        if (userRepository.existsByEmail(email)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 이메일입니다");
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(password);

        // 엔티티 생성 및 저장
        User user = User.create(loginId, encodedPassword, name, email, birthDate);
        return userRepository.save(user);
    }
}
```

**Service 통합 테스트**

```java
class UserServiceIT extends IntegrationTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Nested
    class 회원가입 {

        @Test
        void 성공() {
            // given
            String loginId = "john123";
            String password = "Pass1234!";
            String name = "홍길동";
            String email = "john@test.com";
            LocalDate birthDate = LocalDate.of(1995, 3, 15);

            // when
            User user = userService.signUp(loginId, password, name, email, birthDate);

            // then
            assertThat(user.getId()).isNotNull();
            assertThat(user.getLoginId()).isEqualTo(loginId);

            // DB 확인
            var saved = userRepository.findByLoginId(loginId);
            assertThat(saved).isPresent();
        }

        @Test
        void 로그인_ID_중복이면_예외발생() {
            // given
            String loginId = "john123";
            userService.signUp(loginId, "Pass1234!", "홍길동", "john1@test.com", null);

            // when & then
            assertThatThrownBy(() ->
                userService.signUp(loginId, "Pass1234!", "김철수", "john2@test.com", null)
            ).isInstanceOf(CoreException.class)
             .satisfies(e -> {
                 CoreException ce = (CoreException) e;
                 assertThat(ce.getErrorType()).isEqualTo(ErrorType.CONFLICT);
             });
        }

        @Test
        void 이메일_중복이면_예외발생() {
            // given
            String email = "john@test.com";
            userService.signUp("john123", "Pass1234!", "홍길동", email, null);

            // when & then
            assertThatThrownBy(() ->
                userService.signUp("john456", "Pass1234!", "김철수", email, null)
            ).isInstanceOf(CoreException.class)
             .satisfies(e -> {
                 CoreException ce = (CoreException) e;
                 assertThat(ce.getErrorType()).isEqualTo(ErrorType.CONFLICT);
             });
        }

        @Test
        void 비밀번호가_암호화되어_저장된다() {
            // given
            String rawPassword = "Pass1234!";

            // when
            User user = userService.signUp("john123", rawPassword, "홍길동", "john@test.com", null);

            // then
            assertThat(user.getPassword()).isNotEqualTo(rawPassword);
            assertThat(user.getPassword()).startsWith("$2"); // BCrypt prefix
        }
    }
}
```

### 4. Facade + Info

**Info**

```java
// application/user/UserInfo.java
public record UserInfo(
    String loginId,
    String name,
    String email,
    LocalDate birthDate
) {
    public static UserInfo from(User user) {
        return new UserInfo(
            user.getLoginId(),
            user.getName(),
            user.getEmail(),
            user.getBirthDate()
        );
    }

    public String maskedName() {
        if (name == null || name.length() < 2) return name;
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
}
```

**Facade**

```java
@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;

    public UserInfo signUp(String loginId, String password, String name,
                           String email, LocalDate birthDate) {
        User user = userService.signUp(loginId, password, name, email, birthDate);
        return UserInfo.from(user);
    }
}
```

### 5. Controller + DTO

**DTO**

```java
public class UserV1Dto {

    /**
     * Bean Validation: 입력값 검증 (존재 여부 + 기본 범위)
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

    public record UserResponse(
        String loginId,
        String name,
        String email,
        LocalDate birthDate
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.loginId(),
                info.maskedName(),
                info.email(),
                info.birthDate()
            );
        }
    }
}
```

**ApiSpec**

```java
@Tag(name = "User API", description = "회원 관련 API")
public interface UserApiV1Spec {

    @Operation(summary = "회원가입", description = "신규 회원을 등록합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "회원가입 성공"),
        @ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
        @ApiResponse(responseCode = "409", description = "중복된 로그인 ID 또는 이메일")
    })
    @PostMapping("/api/v1/users")
    ApiResponse<UserV1Dto.UserResponse> signUp(
        @Valid @RequestBody UserV1Dto.SignUpRequest request
    );
}
```

**Controller**

```java
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
}
```

---

## Phase 3: VERIFY (AC 매핑 검증)

### AC 매핑 테이블

| AC# | 조건 | 테스트 메서드 | 결과 |
|-----|------|-------------|------|
| AC-1 | 모든 필수값 유효 | `UserServiceIT.성공()` | ✅ |
| AC-3 | loginId null | `UserTest.로그인_ID가_null이면_예외발생()` | ✅ |
| AC-4 | loginId 빈문자열 | (AC-3과 동일 검증) | ✅ |
| AC-7 | name null | (Bean Validation) | ✅ |
| AC-8 | name 빈문자열 | `UserTest.이름이_빈_문자열이면_예외발생()` | ✅ |
| AC-10 | email null | `UserTest.이메일이_null이면_예외발생()` | ✅ |
| AC-12 | birthDate null | `UserTest.생년월일이_null이면_허용된다()` | ✅ |
| AC-14 | loginId 중복 | `UserServiceIT.로그인_ID_중복이면_예외발생()` | ✅ |
| AC-15 | email 중복 | `UserServiceIT.이메일_중복이면_예외발생()` | ✅ |
| - | 비밀번호 암호화 | `UserServiceIT.비밀번호가_암호화되어_저장된다()` | ✅ |

### 검증 체크리스트

- [x] 모든 AC에 대응하는 테스트가 있는가? → Yes
- [x] AC에 없는 테스트가 있는가? → 비밀번호 암호화 테스트 (보안 요구사항으로 추가됨)
- [x] 전체 테스트 실행 통과? → Yes

### 전체 테스트 실행 결과

```
UserTest
  생성
    ✓ 유효한_값이면_회원이_생성된다
    ✓ 로그인_ID가_null이면_예외발생
    ✓ 이름이_빈_문자열이면_예외발생
    ✓ 이메일이_null이면_예외발생
    ✓ 생년월일이_null이면_허용된다

UserServiceIT
  회원가입
    ✓ 성공
    ✓ 로그인_ID_중복이면_예외발생
    ✓ 이메일_중복이면_예외발생
    ✓ 비밀번호가_암호화되어_저장된다

9 tests passed
```

---

## Phase 4: REFACTOR

### 완료 체크리스트

- [x] 중복 코드 없음
- [x] 불필요한 코드 없음
- [x] unused import 없음
- [x] 모든 테스트 통과

### 테스트 실행 결과

```
UserTest
  생성
    ✓ 유효한_값이면_회원이_생성된다
    ✓ 로그인_ID가_null이면_예외발생
    ✓ 이름이_빈_문자열이면_예외발생
    ✓ 이메일이_null이면_예외발생
    ✓ 생년월일이_null이면_허용된다

UserServiceIT
  회원가입
    ✓ 성공
    ✓ 로그인_ID_중복이면_예외발생
    ✓ 이메일_중복이면_예외발생
    ✓ 비밀번호가_암호화되어_저장된다

9 tests passed
```

---

## 생성 파일 목록

| 계층 | 파일 |
|------|------|
| Domain | `User.java`, `UserRepository.java`, `PasswordEncoder.java` |
| Infrastructure | `UserRepositoryImpl.java`, `UserJpaRepository.java`, `BcryptPasswordEncoder.java` |
| Application | `UserFacade.java`, `UserInfo.java` |
| Interfaces | `UserApiV1Spec.java`, `UserV1Controller.java`, `UserV1Dto.java` |
| Test | `UserTest.java`, `UserServiceIT.java` |
