---
name: entity-patterns
description: Domain Entity 구현 패턴. "Entity 만들어줘", "도메인 모델 작성해줘", "JPA Entity 구현해줘" 요청 시 사용. BaseEntity 상속, 정적 팩토리, 도메인 불변식 검증, Soft Delete 패턴 제공.
---

# Entity Patterns

Domain Entity 구현 가이드입니다.

## 필수 규칙 참조

- `.claude/rules/layer/domain.md` - Domain Layer 상세 규칙 ⭐
- `.claude/rules/core/design-principles.md` - 2단계 검증 전략
- `.claude/rules/core/exception-patterns.md` - IllegalArgumentException 사용
- `.claude/rules/core/naming-conventions.md` - Entity 네이밍

---

## 핵심 규칙 요약

| 항목 | 규칙 |
|------|------|
| 상속 | `extends BaseEntity` 필수 |
| 생성자 | **Lombok 금지**, 직접 작성 |
| 기본 생성자 | `protected {Domain}() {}` JPA용 |
| private 생성자 | 모든 필드 초기화 + 검증 호출 |
| 팩토리 메서드 | `public static create()` |
| 정규식 | `private static final Pattern` 상수 |
| 예외 | `IllegalArgumentException` |

### Lombok 사용 규칙

| 허용 | 금지 |
|------|------|
| `@Getter` | `@NoArgsConstructor` |
| | `@AllArgsConstructor` |
| | `@Builder` |
| | `@Setter` |

> 생성자는 **반드시 직접 작성**. Lombok 생성자 어노테이션 사용 금지.

> 상세 규칙은 `.claude/rules/layer/domain.md` 참조

---

## 패키지 구조

```
com.loopers.domain.{domain}/
├── {Domain}.java           # Entity
├── {Domain}Repository.java # Repository 인터페이스
└── {Domain}Service.java    # Domain Service
```

---

## 1. BaseEntity 상속

모든 Entity는 `BaseEntity`를 상속받습니다.

### BaseEntity 제공 기능

| 필드/메서드 | 설명 |
|------------|------|
| `id` | Auto-generated Long ID |
| `createdAt` | 생성 시각 (자동) |
| `updatedAt` | 수정 시각 (자동) |
| `deletedAt` | 삭제 시각 (Soft Delete) |
| `delete()` | Soft Delete 수행 |
| `restore()` | 삭제 복원 |
| `guard()` | PrePersist/PreUpdate 시 호출되는 검증 훅 |

### 기본 구조

```java
@Entity
@Table(name = "{domain}s")
@Getter  // Getter만 허용
public class {Domain} extends BaseEntity {

    // 정규식 상수
    private static final Pattern XXX_PATTERN = Pattern.compile("...");

    // 필드 정의
    @Column(nullable = false)
    private String field1;

    // JPA용 기본 생성자 (직접 작성)
    protected {Domain}() {}

    // private 생성자 (직접 작성)
    private {Domain}(String field1, ...) {
        this.field1 = field1;
        ...
    }

    // 정적 팩토리 메서드
    public static {Domain} create(...) { ... }

    // 도메인 불변식 검증 메서드
    private static void validateXxx(...) { ... }

    // 비즈니스 메서드
    public void changeXxx(...) { ... }
}
```

> **주의:** `@NoArgsConstructor`, `@AllArgsConstructor` 사용 금지. 생성자 직접 작성.

---

## 2. 생성자 패턴

**목적:** 생성 시점에 모든 불변식 검증 수행

### JPA용 기본 생성자

```java
// 반드시 직접 작성 (@NoArgsConstructor 금지)
protected {Domain}() {}
```

### private 생성자

```java
// 반드시 직접 작성 (@AllArgsConstructor 금지)
private {Domain}(String field1, String field2, LocalDate field3) {
    this.field1 = field1;
    this.field2 = field2;
    this.field3 = field3;
}
```

### 정적 팩토리 메서드

```java
public static {Domain} create(String field1, String field2, LocalDate field3) {
    validateField1(field1);
    validateField2(field2);
    validateField3(field3);
    return new {Domain}(field1, field2, field3);
}
```

### 외부 의존성이 필요한 경우

```java
// ❌ 금지: 필드 주입
@Autowired
private PasswordEncoder passwordEncoder;

// ✅ 올바름: create() 파라미터로 전달
public static User create(String loginId, String rawPassword,
                          String name, LocalDate birthDate, String email,
                          PasswordEncoder encoder) {
    validateLoginId(loginId);
    validateBirthDate(birthDate);
    validateName(name);
    validateEmail(email);
    PasswordValidator.validate(rawPassword, birthDate);

    String encodedPassword = encoder.encode(rawPassword);
    return new User(loginId, encodedPassword, name, birthDate, email);
}
```

> 외부 의존성(PasswordEncoder 등)은 **create() 파라미터**로 전달받아 Entity 내에서 처리

---

## 3. 도메인 불변식 검증

**목적:** Entity가 항상 유효한 상태를 보장

### 검증 순서

1. **null/blank 체크** - 필수값 존재 여부
2. **형식 검증** - 정규식 패턴 (이메일, 아이디 등)
3. **비즈니스 규칙** - 논리적 제약 (미래 날짜 불가 등)
4. **cross-field 검증** - 필드 간 교차 검증

### 검증 메서드 패턴

```java
// 정규식은 상수로 선언
private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9]*$");
private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

private static void validateLoginId(String loginId) {
    // 1. null/blank 체크
    if (loginId == null || loginId.isBlank()) {
        throw new IllegalArgumentException("로그인 ID는 필수입니다");
    }
    // 2. 형식 검증
    if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
        throw new IllegalArgumentException("로그인 ID는 영문 소문자로 시작하고, 영문 소문자와 숫자만 허용됩니다");
    }
}

private static void validateEmail(String email) {
    if (email == null || email.isBlank()) {
        throw new IllegalArgumentException("이메일은 필수입니다");
    }
    if (!EMAIL_PATTERN.matcher(email).matches()) {
        throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다");
    }
}

private static void validateBirthDate(LocalDate birthDate) {
    // 선택 필드는 null 허용
    if (birthDate != null && !birthDate.isBefore(LocalDate.now())) {
        throw new IllegalArgumentException("생년월일은 과거 날짜만 허용됩니다");
    }
}
```

### 예외 타입

| 상황 | 예외 | 처리 |
|------|------|------|
| 도메인 불변식 위반 | `IllegalArgumentException` | 글로벌 핸들러 → 400 BAD_REQUEST |
| 비즈니스 로직 오류 | `CoreException` | Service에서 throw |

---

## 4. Soft Delete

**목적:** 데이터 삭제 시 실제 삭제 대신 `deletedAt` 필드 사용

### BaseEntity 제공 메서드

```java
// Soft Delete (멱등)
public void delete() {
    if (this.deletedAt == null) {
        this.deletedAt = ZonedDateTime.now();
    }
}

// 복원 (멱등)
public void restore() {
    if (this.deletedAt != null) {
        this.deletedAt = null;
    }
}
```

### Repository 조회 시 주의

```java
// ❌ 삭제된 데이터도 조회됨
Optional<User> findByLoginId(String loginId);

// ✅ 삭제되지 않은 데이터만 조회
Optional<User> findByLoginIdAndDeletedAtIsNull(String loginId);
```

---

## 5. 비즈니스 메서드

**목적:** Entity 상태 변경 로직 캡슐화

### 패턴

```java
// 상태 변경 메서드
public void changePassword(String newEncodedPassword) {
    validatePassword(newEncodedPassword);
    this.password = newEncodedPassword;
}

public void updateProfile(String name, String email) {
    validateName(name);
    validateEmail(email);
    this.name = name;
    this.email = email;
}

// 조회용 메서드
public boolean isDeleted() {
    return this.deletedAt != null;
}

public String getMaskedName() {
    if (name == null || name.length() < 2) return name;
    if (name.length() == 2) return name.charAt(0) + "*";
    return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
}
```

---

## 6. JPA 어노테이션

### 필수 어노테이션

```java
@Entity                                              // JPA Entity
@Table(name = "users")                               // 테이블명
@Getter                                              // Lombok Getter만 사용
public class User extends BaseEntity {
    // JPA용 기본 생성자 (직접 작성)
    protected User() {}
```

### 필드 어노테이션

```java
@Column(nullable = false, unique = true, length = 20)
private String loginId;

@Column(nullable = false)
private String password;

@Column(nullable = false, length = 20)
private String name;

@Column(nullable = false, unique = true, length = 100)
private String email;

private LocalDate birthDate;  // nullable = true (기본값)
```

### 연관관계

```java
// ManyToOne
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", nullable = false)
private User user;

// OneToMany
@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Order> orders = new ArrayList<>();
```

---

## 7. guard() 메서드 (선택)

**목적:** PrePersist/PreUpdate 시점에 추가 검증

```java
@Override
protected void guard() {
    // 저장/수정 직전에 호출됨
    if (this.amount != null && this.amount < 0) {
        throw new IllegalStateException("금액은 0 이상이어야 합니다");
    }
}
```

> 주의: `guard()`는 영속화 시점 검증용. 생성 시점 검증은 `create()`에서 수행.

---

## 체크리스트

### 기본 구조
- [ ] `extends BaseEntity` 상속
- [ ] `@Entity`, `@Table` 적용
- [ ] `@Getter`만 사용
- [ ] `@NoArgsConstructor`, `@AllArgsConstructor` **사용 안 함**
- [ ] `protected {Domain}() {}` 직접 작성
- [ ] `private {Domain}(...)` 직접 작성
- [ ] `public static create()` 정적 팩토리 존재

### 불변식 검증
- [ ] 모든 필수 필드 null/blank 체크
- [ ] 형식 검증 (정규식 → `Pattern.compile()` 상수)
- [ ] 비즈니스 규칙 검증
- [ ] `IllegalArgumentException` 사용

### 외부 의존성
- [ ] 필드 주입 금지
- [ ] `create()` 파라미터로 전달
- [ ] Entity 내에서 처리 (예: 암호화)

### 기타
- [ ] Soft Delete 사용 (`delete()`, `restore()`)
- [ ] 상태 변경 메서드에서도 검증 수행

---

## 트러블슈팅

### 1. `@NoArgsConstructor` 사용함

**문제:** Lombok 생성자 어노테이션 사용 금지
```java
// ❌ 잘못됨
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity { ... }

// ✅ 올바름 - 직접 작성
public class User extends BaseEntity {
    protected User() {}
}
```

### 2. `@Builder` 사용함

**문제:** Builder는 불변식 검증을 우회함
```java
// ❌ 검증 우회 가능
@Builder
public class User { ... }

User.builder().loginId(null).build();  // 검증 안 됨

// ✅ 정적 팩토리에서 검증 강제
public static User create(...) {
    validateLoginId(loginId);  // 검증 수행
    return new User(...);
}
```

### 3. 검증에서 `CoreException` 사용

**문제:** Entity 검증은 `IllegalArgumentException` 사용
```java
// ❌ Entity에서 CoreException 사용
private static void validateLoginId(String loginId) {
    if (loginId == null) {
        throw new CoreException(ErrorType.BAD_REQUEST, "...");
    }
}

// ✅ IllegalArgumentException 사용
private static void validateLoginId(String loginId) {
    if (loginId == null) {
        throw new IllegalArgumentException("로그인 ID는 필수입니다");
    }
}
```

### 4. 정규식을 매번 컴파일

**문제:** 성능 저하
```java
// ❌ 매번 컴파일
if (!loginId.matches("^[a-z][a-z0-9]*$")) { ... }

// ✅ 상수로 한 번만 컴파일
private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9]*$");
if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) { ... }
```

### 5. Service에서 암호화 후 Entity 생성

**문제:** 외부 의존성은 Entity.create() 파라미터로 전달
```java
// ❌ Service에서 암호화
String encoded = passwordEncoder.encode(rawPassword);
User user = User.create(loginId, encoded, ...);

// ✅ Entity.create()에 PasswordEncoder 전달
User user = User.create(loginId, rawPassword, ..., passwordEncoder);
// Entity 내부에서 암호화 수행
```

### 6. Soft Delete 조회 시 삭제된 데이터 포함

**문제:** `deletedAt` 조건 누락
```java
// ❌ 삭제된 데이터도 조회됨
Optional<User> findByLoginId(String loginId);

// ✅ 삭제되지 않은 데이터만 조회
Optional<User> findByLoginIdAndDeletedAtIsNull(String loginId);
```

---

## 참조 문서

| 문서 | 설명 |
|------|------|
| [user-entity.md](./examples/user-entity.md) | 회원 Entity 예시 |
