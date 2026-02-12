---
name: test-patterns
description: 테스트 작성 패턴. "테스트 작성해줘", "단위 테스트 만들어줘", "통합 테스트 구현해줘", "E2E 테스트 추가해줘" 요청 시 사용. 단위/통합/E2E 테스트 구조, Mock 원칙, 네이밍 컨벤션 제공.
---

# 테스트 패턴 및 컨벤션

## 테스트 유형

| 유형 | 대상 | 파일 네이밍 | 도구 |
|------|------|-----------|------|
| **단위 테스트** | Entity, VO, Domain Service | `{Domain}Test.java` | JUnit, AssertJ |
| **통합 테스트** | Service (DB 연동) | `{Domain}ServiceIntegrationTest.java` | @SpringBootTest, TestContainers |
| **E2E 테스트** | API 엔드포인트 | `{Domain}ApiE2ETest.java` | TestRestTemplate |

### 파일 네이밍 예시

| 도메인 | 단위 테스트 | 통합 테스트 | E2E 테스트 |
|--------|-----------|-----------|-----------|
| User | `UserTest.java` | `UserServiceIntegrationTest.java` | `UserApiE2ETest.java` |
| Order | `OrderTest.java` | `OrderServiceIntegrationTest.java` | `OrderApiE2ETest.java` |

### Mock 사용 원칙

> **우리가 상태를 제어할 수 없는 외부 시스템만 Mock으로 stubbing 한다**

#### "외부"의 정의

외부란 **JVM 밖에 있어서 우리가 상태를 보장할 수 없는 시스템**을 말한다.

#### "외부"의 판단 기준
```
"저쪽 상태를 우리가 관리할 수 있는가?"
  → Yes: 실제 객체 사용
  → No:  Mock/Fake 사용
```

#### 도메인 모델과 외부 라이브러리의 경계

JVM 내 라이브러리라도 **도메인 모델이 직접 의존하면 안 된다.**
도메인 모델 안으로 들어올 때는 반드시 **인터페이스로 감싸거나,
변환된 값으로 전달**한다.
```java
// ❌ 도메인 모델이 외부 라이브러리를 직접 참조
public class User {
    public void changePassword(String raw, PasswordEncoder encoder) {
        this.password = encoder.encode(raw);
    }
}

// ✅ 암호화된 값을 받는다 (도메인은 암호화 방식을 모른다)
public class User {
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}

// ✅ 또는 도메인 인터페이스로 감싼다
public interface PasswordEncryptor {  // 도메인 패키지에 위치
    String encrypt(String raw);
}

public class User {
    public void changePassword(String raw, PasswordEncryptor encryptor) {
        this.password = encryptor.encrypt(raw);
    }
}
```

**핵심:** 실제 객체를 쓰느냐 Mock을 쓰느냐는 **테스트 전략**이고,
인터페이스로 감싸느냐는 **설계 원칙**이다.
이 둘은 별개의 문제다.

| 관점 | 질문 | 결정 |
|------|------|------|
| 테스트 전략 | Mock 할 것인가? | 상태 제어 가능하면 실제 객체 |
| 설계 원칙 | 도메인이 직접 참조해도 되는가? | 외부 라이브러리는 인터페이스로 격리 |

---

## 공통 규칙

### 필수 구조: @Nested 사용

- **모든 테스트는 @Nested 클래스로 그룹화** (테스트가 1개여도 필수)
- 이유: 일관성 유지, 확장 용이

### 네이밍 컨벤션

| 항목 | 규칙 | 예시 |
|------|------|------|
| @Nested 클래스명 | 한글 (기능/유스케이스) | `class 생성`, `class 회원가입` |
| 테스트 메서드명 | `조건_결과` 한글 서술형 | `유효한_값이면_회원이_생성된다()` |
| @DisplayName | **사용 금지** | `ReplaceUnderscores` 설정 사용 |

### 클래스 설정

```java
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserTest {
    // ...
}
```

### 한글화 원칙

- 영어 메서드명 혼용 금지 (`confirm_호출시` ❌ → `확정` ✅)
- 영어 상태/값 직접 사용 금지 (`CONFIRMED` ❌ → `확정됨` ✅)

---

## 1. 단위 테스트

### 목적

- 도메인 불변식/비즈니스 규칙 검증
- 빠른 피드백 (ms 단위)
- Mock 없이 순수 객체 테스트

### 테스트 대상

| 구분 | 예시 |
|------|------|
| 생성 규칙 | `User.create()` 검증 |
| 불변식 검증 | 로그인ID 형식, 이메일 형식 |
| 비즈니스 메서드 | `getMaskedName()`, `changePassword()` |
| 예외 케이스 | null, 빈값, 형식 오류 |

### 기본 구조

```java
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserTest {

    private static final PasswordEncoder PASSWORD_ENCODER = new FakePasswordEncoder();

    // 외부 의존성은 Fake 구현체로
    static class FakePasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(String rawPassword) {
            return "encoded_" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return encodedPassword.equals("encoded_" + rawPassword);
        }
    }

    @Nested
    class 생성 {

        @Test
        void 유효한_값이면_회원이_생성된다() {
            // given
            String loginId = "testuser123";
            String rawPassword = "Test1234!";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(2000, 1, 15);
            String email = "test@example.com";

            // when
            User user = User.create(loginId, rawPassword, name, birthDate, email, PASSWORD_ENCODER);

            // then
            assertThat(user.getLoginId()).isEqualTo(loginId);
            assertThat(user.getPassword()).isNotEqualTo(rawPassword);
            assertThat(user.getName()).isEqualTo(name);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void 로그인ID가_null_또는_빈값이면_예외(String loginId) {
            assertThatThrownBy(() -> User.create(loginId, "Test1234!", "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com", PASSWORD_ENCODER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("로그인 ID");
        }
    }

    @Nested
    class 비밀번호_변경 {

        @Test
        void 유효한_새_비밀번호면_변경된다() {
            // given
            User user = User.create("testuser", "Test1234!", "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com", PASSWORD_ENCODER);
            String oldPassword = user.getPassword();

            // when
            user.changePassword("NewPass123!", PASSWORD_ENCODER);

            // then
            assertThat(user.getPassword()).isNotEqualTo(oldPassword);
        }

        @Test
        void 현재_비밀번호와_동일하면_예외() {
            // given
            User user = User.create("testuser", "Test1234!", "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com", PASSWORD_ENCODER);

            // when & then
            assertThatThrownBy(() -> user.changePassword("Test1234!", PASSWORD_ENCODER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 비밀번호와 동일");
        }
    }
}
```

---

## 2. 통합 테스트

### 목적

- Service → Repository → DB 전체 흐름 검증
- 트랜잭션, 중복 체크 등 검증
- 리팩터링 내성 확보

### 테스트 대상

| 구분 | 예시 |
|------|------|
| 핵심 유스케이스 | 회원가입, 인증, 비밀번호 변경 |
| 중복 체크 | 로그인ID 중복, 이메일 중복 |
| 예외 시나리오 | 존재하지 않는 회원, 인증 실패 |

### 기본 구조

```java
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 회원가입 {

        @Test
        void 유효한_정보로_회원가입하면_회원이_생성된다() {
            // given
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(2000, 1, 15);
            String email = "test@example.com";

            // when
            User result = userService.signUp(loginId, rawPassword, name, birthDate, email);

            // then
            assertThat(result.getLoginId()).isEqualTo(loginId);
            assertThat(result.getName()).isEqualTo(name);
        }

        @Test
        void 이미_존재하는_로그인ID로_가입하면_예외() {
            // given
            String loginId = "testuser";
            userService.signUp(loginId, "Test1234!", "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com");

            // when & then
            assertThatThrownBy(() -> userService.signUp(loginId, "Test5678!", "김철수",
                    LocalDate.of(1995, 5, 20), "other@example.com"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.CONFLICT));
        }
    }

    @Nested
    class 인증 {

        @Test
        void 유효한_인증정보로_인증하면_회원을_반환한다() {
            // given
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            userService.signUp(loginId, rawPassword, "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com");

            // when
            User user = userService.authenticate(loginId, rawPassword);

            // then
            assertThat(user.getLoginId()).isEqualTo(loginId);
        }

        @Test
        void 비밀번호가_일치하지_않으면_예외() {
            // given
            String loginId = "testuser";
            userService.signUp(loginId, "Test1234!", "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com");

            // when & then
            assertThatThrownBy(() -> userService.authenticate(loginId, "WrongPass1!"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.UNAUTHORIZED));
        }
    }
}
```

---

## 3. E2E 테스트

### 목적

- HTTP 요청/응답 전체 파이프라인 검증
- API 스펙 검증 (상태 코드, 응답 형식)
- 실제 사용자 시나리오 검증

### 테스트 대상

| 구분 | 예시 |
|------|------|
| 성공 시나리오 | 회원가입 성공, 조회 성공 |
| HTTP 상태 코드 | 200, 400, 401, 404, 409 |
| 응답 데이터 검증 | 마스킹된 이름, 필드 확인 |

### 기본 구조

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserApiE2ETest {

    private static final String SIGNUP_ENDPOINT = "/api/v1/users";
    private static final String MY_INFO_ENDPOINT = "/api/v1/users/me";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 회원가입 {

        @Test
        void 유효한_정보로_회원가입하면_회원정보가_반환된다() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "testuser", "Test1234!", "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com"
            );

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = postSignUp(request);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser"),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*")
            );
        }

        @Test
        void 이미_존재하는_로그인ID로_가입하면_409_응답() {
            // arrange
            signUp("testuser", "Test1234!", "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com");

            UserV1Dto.SignUpRequest duplicateRequest = new UserV1Dto.SignUpRequest(
                    "testuser", "Test5678!", "김철수",
                    LocalDate.of(1995, 5, 20), "other@example.com"
            );

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(duplicateRequest),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 유효하지_않은_입력이면_400_응답() {
            // arrange - 개별 검증 규칙은 단위 테스트에서 검증
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "test-user!", "Test1234!", "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com"
            );

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class 내_정보_조회 {

        @Test
        void 유효한_인증정보로_조회하면_정보가_반환된다() {
            // arrange
            signUp("testuser", "Test1234!", "홍길동",
                    LocalDate.of(2000, 1, 15), "test@example.com");

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    getMyInfo("testuser", "Test1234!");

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().loginId()).isEqualTo("testuser");
        }

        @Test
        void 인증헤더가_누락되면_401_응답() {
            // arrange & act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    MY_INFO_ENDPOINT, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // --- 헬퍼 메서드 ---

    private void signUp(String loginId, String password, String name,
                        LocalDate birthDate, String email) {
        UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                loginId, password, name, birthDate, email);
        postSignUp(request);
    }

    private ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> postSignUp(
            UserV1Dto.SignUpRequest request) {
        return testRestTemplate.exchange(
                SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> getMyInfo(
            String loginId, String password) {
        return testRestTemplate.exchange(
                MY_INFO_ENDPOINT, HttpMethod.GET,
                new HttpEntity<>(authHeaders(loginId, password)),
                new ParameterizedTypeReference<>() {}
        );
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }
}
```

---

## 체크리스트

### 공통
- [ ] `@DisplayNameGeneration(ReplaceUnderscores.class)` 적용
- [ ] `@Nested` 클래스로 그룹화
- [ ] 한글 메서드명 사용
- [ ] 3A 패턴: Arrange - Act - Assert (또는 given - when - then)

### 단위 테스트
- [ ] Mock 사용 안 함
- [ ] 외부 의존성은 Fake 구현체
- [ ] 도메인 불변식 검증

### 통합 테스트
- [ ] `@SpringBootTest` 적용
- [ ] `DatabaseCleanUp` 사용
- [ ] Service 메서드 호출

### E2E 테스트
- [ ] `RANDOM_PORT` 설정
- [ ] `TestRestTemplate` 사용
- [ ] HTTP 상태 코드 검증

---

## 트러블슈팅

### 1. `@Nested` 없이 테스트 작성

**문제:** 일관성과 확장성 저하
```java
// ❌ 플랫한 테스트
class UserTest {
    @Test
    void 유효한_값이면_회원이_생성된다() { ... }
    @Test
    void 로그인ID가_null이면_예외() { ... }
}

// ✅ @Nested로 그룹화
class UserTest {
    @Nested
    class 생성 {
        @Test
        void 유효한_값이면_회원이_생성된다() { ... }
        @Test
        void 로그인ID가_null이면_예외() { ... }
    }
}
```

### 2. `@DisplayName` 사용

**문제:** `ReplaceUnderscores` 설정과 중복
```java
// ❌ @DisplayName 사용
@Test
@DisplayName("유효한 값이면 회원이 생성된다")
void createUser() { ... }

// ✅ 한글 메서드명 + ReplaceUnderscores
@Test
void 유효한_값이면_회원이_생성된다() { ... }
```

### 3. 단위 테스트에서 @SpringBootTest 사용

**문제:** 불필요한 컨텍스트 로딩, 느린 실행
```java
// ❌ 단위 테스트에 Spring 컨텍스트
@SpringBootTest
class UserTest { ... }

// ✅ 순수 Java 테스트
class UserTest {
    @Nested
    class 생성 { ... }
}
```

### 4. DB 관련 테스트에서 Mock Repository 사용

**문제:** 실제 동작을 검증하지 못함
```java
// ❌ Repository Mock
@Mock
private UserRepository userRepository;

when(userRepository.existsByLoginId("test")).thenReturn(false);

// ✅ 실제 DB 사용 (통합 테스트)
@SpringBootTest
class UserServiceIntegrationTest {
    @Autowired
    private UserService userService;
}
```

### 5. 테스트 간 데이터 오염

**문제:** 이전 테스트 데이터가 영향
```java
// ❌ 데이터 정리 안 함
class UserServiceIntegrationTest { ... }

// ✅ @AfterEach로 정리
@AfterEach
void tearDown() {
    databaseCleanUp.truncateAllTables();
}
```

### 6. 영어 메서드명과 한글 혼용

**문제:** 일관성 저하
```java
// ❌ 혼용
@Test
void confirm_호출시_상태가_CONFIRMED로_변경된다() { ... }

// ✅ 전체 한글
@Test
void 확정하면_상태가_확정됨으로_변경된다() { ... }
```

### 7. 외부 라이브러리를 단위 테스트에서 직접 사용

**문제:** 의존성 격리 안 됨
```java
// ❌ 실제 PasswordEncoder 사용
@Test
void 비밀번호가_암호화된다() {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    User user = User.create(..., encoder);
}

// ✅ Fake 구현체 사용
private static final PasswordEncoder ENCODER = new FakePasswordEncoder();

@Test
void 비밀번호가_암호화된다() {
    User user = User.create(..., ENCODER);
}
```
