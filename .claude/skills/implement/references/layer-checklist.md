# 계층별 구현 체크리스트

각 계층 구현 시 확인해야 할 필수 항목들입니다.

---

## Entity 체크리스트

### 필수 항목

- [ ] `BaseEntity` 상속
- [ ] 정적 팩토리 메서드 `create(...)` 사용
- [ ] private 생성자 (JPA용 `protected` no-args 생성자 별도)
- [ ] 도메인 불변식 검증 (`validate{Field}()` private 메서드)
- [ ] `IllegalArgumentException` 사용 (도메인 검증 실패 시)

### 비즈니스 메서드

- [ ] 상태 변경 메서드 (동사로 시작: `changePassword`, `delete`, `restore`)
- [ ] 조회 메서드 필요 시 추가

### 예시

```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    private User(String loginId, String password, String name) {
        validateLoginId(loginId);
        validatePassword(password);
        validateName(name);
        this.loginId = loginId;
        this.password = password;
        this.name = name;
    }

    public static User create(String loginId, String password, String name) {
        return new User(loginId, password, name);
    }

    private static void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new IllegalArgumentException("로그인 ID는 필수입니다");
        }
    }

    // ... 다른 검증 메서드
}
```

---

## Repository 체크리스트

### 인터페이스 (domain 패키지)

- [ ] `domain/{도메인}/` 패키지에 위치
- [ ] 필요한 쿼리 메서드만 정의
- [ ] 반환 타입 규칙 준수

### 반환 타입 규칙

| 메서드 패턴 | 반환 타입 |
|-------------|----------|
| `findBy{Field}(value)` | `Optional<T>` |
| `existsBy{Field}(value)` | `boolean` |
| `findAllBy{Field}(value)` | `List<T>` |
| `save(entity)` | `T` |

### 예시 (인터페이스)

```java
// domain/user/UserRepository.java
public interface UserRepository {
    User save(User user);
    Optional<User> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
}
```

### 구현체 (infrastructure 패키지)

- [ ] `infrastructure/{도메인}/` 패키지에 위치
- [ ] `@Repository` 어노테이션
- [ ] JPA Repository 위임
- [ ] Soft Delete 조건 포함 (`deletedAt IS NULL`)

### 예시 (구현체)

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
}
```

### JPA Repository

```java
// infrastructure/user/UserJpaRepository.java
public interface UserJpaRepository extends JpaRepository<User, Long> {
    Optional<User> findByLoginIdAndDeletedAtIsNull(String loginId);
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
}
```

---

## Service 체크리스트

### 트랜잭션 관리

- [ ] 클래스 레벨: `@Transactional(readOnly = true)` (기본)
- [ ] 쓰기 메서드만: `@Transactional` (개별 지정)

### 중복 체크

- [ ] `existsBy{Field}()` 메서드로 명시적 수행
- [ ] 중복 시 `CoreException(ErrorType.CONFLICT, "메시지")` throw
- [ ] **try-catch로 DataIntegrityViolationException 잡지 않음**

### 조회 실패

- [ ] `findBy{Field}().orElseThrow()` 패턴
- [ ] `CoreException(ErrorType.NOT_FOUND, "메시지")` throw

### 예시

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User signUp(String loginId, String password, String name, String email) {
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
        User user = User.create(loginId, encodedPassword, name, email);
        return userRepository.save(user);
    }

    public User getUser(String loginId) {
        return userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다"));
    }
}
```

---

## Facade 체크리스트

### 역할

- [ ] Service 호출 오케스트레이션
- [ ] Entity → Info 변환
- [ ] **트랜잭션 경계 아님** (Service에서 관리)

### 예시

```java
@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;

    public UserInfo signUp(String loginId, String password, String name, String email) {
        User user = userService.signUp(loginId, password, name, email);
        return UserInfo.from(user);
    }

    public UserInfo getUser(String loginId) {
        User user = userService.getUser(loginId);
        return UserInfo.from(user);
    }
}
```

---

## Controller 체크리스트

### 구조

- [ ] ApiSpec 인터페이스 + Controller 구현 분리
- [ ] `@Valid`로 Request DTO 검증
- [ ] `ApiResponse.success(data)` 반환
- [ ] **try-catch 금지** (글로벌 핸들러가 처리)

### ApiSpec 인터페이스

```java
@Tag(name = "User API")
public interface UserApiV1Spec {

    @Operation(summary = "회원가입")
    @PostMapping("/api/v1/users")
    ApiResponse<UserV1Dto.UserResponse> signUp(
        @Valid @RequestBody UserV1Dto.SignUpRequest request
    );
}
```

### Controller 구현

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
            request.email()
        );
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }
}
```

---

## DTO 체크리스트

### 구조

- [ ] Java record 사용 (불변성)
- [ ] DTO 컨테이너 클래스 내부에 정의 (`{Domain}V{version}Dto`)
- [ ] Request: Bean Validation 어노테이션
- [ ] Response: `from(Info)` 정적 팩토리 메서드

### Request DTO 검증

| 어노테이션 | 용도 |
|-----------|------|
| `@NotNull` | null 불가 |
| `@NotBlank` | null, 빈 문자열, 공백만 불가 |
| `@NotEmpty` | null, 빈 컬렉션 불가 |
| `@Size(min, max)` | 길이 제한 |
| `@Pattern(regexp)` | 정규식 패턴 |
| `@Email` | 이메일 형식 |
| `@Positive` | 양수만 |
| `@Past` | 과거 날짜만 |

### 예시

```java
public class UserV1Dto {

    public record SignUpRequest(
        @NotBlank @Size(min = 4, max = 20)
        @Pattern(regexp = "^[a-z][a-z0-9]{3,19}$")
        String loginId,

        @NotBlank @Size(min = 8, max = 20)
        String password,

        @NotBlank @Size(min = 2, max = 20)
        String name,

        @NotBlank @Email @Size(max = 100)
        String email,

        @Past
        LocalDate birthDate
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

---

## Info 체크리스트

### 위치

- [ ] `application/{도메인}/` 패키지

### 역할

- [ ] Entity → 외부 전달용 데이터
- [ ] 필요 시 변환 로직 포함 (마스킹 등)

### 예시

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
