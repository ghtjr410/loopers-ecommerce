# 회원 Entity 예시

User 도메인의 Entity 구현 예시입니다.

---

## 파일 위치

```
com.loopers.domain.user/
├── User.java              # Entity
├── UserRepository.java    # Repository 인터페이스
├── UserService.java       # Domain Service
└── PasswordEncoder.java   # 외부 라이브러리 인터페이스
```

---

## User.java

```java
package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.util.regex.Pattern;

@Entity
@Table(name = "users")
@Getter  // Getter만 사용, @NoArgsConstructor/@AllArgsConstructor 금지
public class User extends BaseEntity {

    // ========================================
    // 검증용 정규식 상수
    // ========================================

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    // ========================================
    // 필드
    // ========================================

    @Column(nullable = false, unique = true)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private String email;

    // ========================================
    // JPA용 기본 생성자 (직접 작성)
    // ========================================

    protected User() {}

    // ========================================
    // private 생성자 (직접 작성)
    // ========================================

    private User(String loginId, String password, String name, LocalDate birthDate, String email) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    // ========================================
    // 정적 팩토리 메서드
    // ========================================

    /**
     * 회원 생성
     * @param loginId 로그인 ID (영문/숫자)
     * @param rawPassword 원문 비밀번호 (Entity 내에서 암호화)
     * @param name 이름
     * @param birthDate 생년월일
     * @param email 이메일
     * @param encoder 비밀번호 인코더 (외부 의존성, 파라미터로 전달)
     */
    public static User create(String loginId, String rawPassword, String name,
                              LocalDate birthDate, String email, PasswordEncoder encoder) {
        // 검증
        validateLoginId(loginId);
        validateBirthDate(birthDate);
        PasswordValidator.validate(rawPassword, birthDate);  // 비밀번호 복잡도 검증
        validateName(name);
        validateEmail(email);

        // 암호화 (Entity 내에서 수행)
        String encodedPassword = encoder.encode(rawPassword);
        return new User(loginId, encodedPassword, name, birthDate, email);
    }

    // ========================================
    // 도메인 불변식 검증
    // ========================================

    private static void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new IllegalArgumentException("로그인 ID는 필수입니다");
        }
        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new IllegalArgumentException("로그인 ID는 영문/숫자만 가능합니다");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름은 필수입니다");
        }
    }

    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new IllegalArgumentException("생년월일은 필수입니다");
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("생년월일은 미래일 수 없습니다");
        }
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다");
        }
    }

    // ========================================
    // 비즈니스 메서드
    // ========================================

    /**
     * 비밀번호 변경 (외부 의존성 파라미터로 전달)
     */
    public void changePassword(String newRawPassword, PasswordEncoder encoder) {
        // 현재 비밀번호와 동일한지 검증
        if (encoder.matches(newRawPassword, password)) {
            throw new IllegalArgumentException("현재 비밀번호와 동일한 비밀번호는 사용할 수 없습니다");
        }
        // 비밀번호 복잡도 검증
        PasswordValidator.validate(newRawPassword, birthDate);
        // 암호화 및 저장
        this.password = encoder.encode(newRawPassword);
    }

    /**
     * 마스킹된 이름 반환 (예: 홍*동)
     */
    public String getMaskedName() {
        if (name == null || name.length() < 2) return name;
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    /**
     * 삭제 여부 확인
     */
    public boolean isDeleted() {
        return getDeletedAt() != null;
    }
}
```

---

## 핵심 포인트

### 1. 정규식은 Pattern 상수로

```java
// ✅ 컴파일 비용 절약
private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9]*$");

// ❌ 매번 컴파일
if (!loginId.matches("^[a-z][a-z0-9]*$")) { ... }
```

### 2. 생성자는 private, 팩토리는 public

```java
// ✅ 생성은 반드시 팩토리를 통해
private User(...) { ... }
public static User create(...) { return new User(...); }

// ❌ public 생성자 금지
public User(...) { ... }
```

### 3. 검증은 static 메서드로

```java
// ✅ static → 생성자에서 호출 가능, 재사용 가능
private static void validateLoginId(String loginId) { ... }

// ❌ instance 메서드 → this 참조 전에 호출 불가
private void validateLoginId() { ... }
```

### 4. 외부 의존성은 파라미터로

```java
// ✅ 올바름: create() 파라미터로 전달받아 Entity 내에서 처리
public static User create(String loginId, String rawPassword, ..., PasswordEncoder encoder) {
    // 검증
    validateLoginId(loginId);
    PasswordValidator.validate(rawPassword, birthDate);
    // 암호화 (Entity 내에서 수행)
    String encodedPassword = encoder.encode(rawPassword);
    return new User(loginId, encodedPassword, ...);
}

// ❌ 금지: 필드 주입
@Autowired
private PasswordEncoder passwordEncoder;
```

### 5. 상태 변경 시에도 검증

```java
public void changePassword(String newEncodedPassword) {
    validatePassword(newEncodedPassword);  // 검증 후 변경
    this.password = newEncodedPassword;
}
```

---

## 관련 테스트

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
            assertThat(user.getName()).isEqualTo(name);
        }

        @Test
        void 로그인_ID가_null이면_예외발생() {
            assertThatThrownBy(() ->
                User.create(null, "password", "홍길동", "test@test.com", null)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("로그인 ID");
        }

        @Test
        void 로그인_ID가_숫자로_시작하면_예외발생() {
            assertThatThrownBy(() ->
                User.create("1john", "password", "홍길동", "test@test.com", null)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("영문 소문자로 시작");
        }

        @Test
        void 이메일_형식이_잘못되면_예외발생() {
            assertThatThrownBy(() ->
                User.create("john123", "password", "홍길동", "invalid-email", null)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("이메일 형식");
        }

        @Test
        void 생년월일이_미래면_예외발생() {
            LocalDate future = LocalDate.now().plusDays(1);

            assertThatThrownBy(() ->
                User.create("john123", "password", "홍길동", "test@test.com", future)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("과거 날짜");
        }

        @Test
        void 생년월일이_null이면_허용된다() {
            // when
            User user = User.create("john123", "password", "홍길동", "test@test.com", null);

            // then
            assertThat(user.getBirthDate()).isNull();
        }
    }

    @Nested
    class 비즈니스_메서드 {

        @Test
        void 마스킹된_이름_반환() {
            User user = User.create("john123", "password", "홍길동", "test@test.com", null);

            assertThat(user.getMaskedName()).isEqualTo("홍*동");
        }

        @Test
        void 비밀번호_변경() {
            User user = User.create("john123", "password", "홍길동", "test@test.com", null);

            user.changePassword("newPassword");

            assertThat(user.getPassword()).isEqualTo("newPassword");
        }
    }
}
```

---

## 연관관계 Entity 예시

```java
@Entity
@Table(name = "orders")
@Getter  // Getter만 사용, @NoArgsConstructor/@AllArgsConstructor 금지
public class Order extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    // JPA용 기본 생성자 (직접 작성)
    protected Order() {}

    // private 생성자 (직접 작성)
    private Order(User user, Integer amount) {
        validateUser(user);
        validateAmount(amount);
        this.user = user;
        this.amount = amount;
        this.status = OrderStatus.PENDING;
    }

    public static Order create(User user, Integer amount) {
        return new Order(user, amount);
    }

    private static void validateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("주문자는 필수입니다");
        }
    }

    private static void validateAmount(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("주문 금액은 0보다 커야 합니다");
        }
    }

    public void complete() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("대기 중인 주문만 완료할 수 있습니다");
        }
        this.status = OrderStatus.COMPLETED;
    }
}
```
