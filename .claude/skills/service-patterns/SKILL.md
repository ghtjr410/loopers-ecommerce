---
name: service-patterns
description: Domain Service 구현 패턴. "Service 만들어줘", "비즈니스 로직 구현해줘", "트랜잭션 처리해줘" 요청 시 사용. 트랜잭션 관리, 중복 체크, CoreException, 외부 의존성 처리 패턴 제공.
---

# Service Patterns

Domain Service 구현 가이드입니다.

## 필수 규칙 참조

- `.claude/rules/core/layer-patterns.md` - Service 역할
- `.claude/rules/core/design-principles.md` - 중복 체크 전략
- `.claude/rules/core/exception-patterns.md` - CoreException 사용
- `.claude/rules/core/naming-conventions.md` - Service 네이밍

---

## 패키지 구조

```
com.loopers.domain.{domain}/
├── {Domain}.java           # Entity
├── {Domain}Repository.java # Repository 인터페이스
├── {Domain}Service.java    # Domain Service ← 여기
└── PasswordEncoder.java    # 외부 의존성 인터페이스 (필요시)
```

---

## 1. 기본 구조

### 템플릿

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 기본: 읽기 전용
public class {Domain}Service {

    private final {Domain}Repository {domain}Repository;
    // 필요시 외부 의존성 인터페이스 주입
    private final PasswordEncoder passwordEncoder;

    // 조회 메서드 (readOnly = true 적용됨)
    public {Domain} getById(Long id) { ... }

    // 쓰기 메서드 (개별 @Transactional 적용)
    @Transactional
    public {Domain} create(...) { ... }
}
```

### 어노테이션 규칙

| 어노테이션 | 위치 | 설명 |
|-----------|------|------|
| `@Service` | 클래스 | Spring Bean 등록 |
| `@RequiredArgsConstructor` | 클래스 | 생성자 주입 |
| `@Transactional(readOnly = true)` | 클래스 | 기본 읽기 전용 |
| `@Transactional` | 쓰기 메서드 | 쓰기 트랜잭션 |

---

## 2. 트랜잭션 관리

### 읽기 vs 쓰기 분리

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 클래스 레벨: 읽기 전용
public class UserService {

    // 조회 메서드 → 클래스 레벨 트랜잭션 사용
    public User getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다"));
    }

    public User getByLoginId(String loginId) {
        return userRepository.findByLoginIdAndDeletedAtIsNull(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다"));
    }

    // 쓰기 메서드 → 개별 @Transactional
    @Transactional
    public User signUp(...) { ... }

    @Transactional
    public User update(...) { ... }

    @Transactional
    public void delete(Long id) { ... }
}
```

### 왜 분리하는가?

| 구분 | readOnly = true | readOnly = false |
|------|-----------------|------------------|
| 플러시 | 비활성화 | 활성화 |
| 더티 체킹 | 비활성화 | 활성화 |
| DB 부하 | 낮음 (읽기 복제본 사용 가능) | 높음 |

---

## 3. 중복 체크

### 패턴: exists 쿼리로 명시적 수행

```java
@Transactional
public User signUp(String loginId, String password, String name,
                   String email, LocalDate birthDate) {
    // 1. 중복 체크 (exists 쿼리)
    if (userRepository.existsByLoginId(loginId)) {
        throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다");
    }
    if (userRepository.existsByEmail(email)) {
        throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 이메일입니다");
    }

    // 2. Entity 생성 및 저장 (외부 의존성은 파라미터로 전달)
    User user = User.create(loginId, password, name, birthDate, email, passwordEncoder);
    return userRepository.save(user);
}
```

### 금지 사항

```java
// ❌ 금지: DataIntegrityViolationException try-catch
try {
    return userRepository.save(user);
} catch (DataIntegrityViolationException e) {
    throw new CoreException(ErrorType.CONFLICT, "중복된 데이터입니다");
}

// ✅ 올바름: exists 쿼리로 사전 체크
if (userRepository.existsByLoginId(loginId)) {
    throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다");
}
```

### 왜 exists 쿼리인가?

| 방식 | 장점 | 단점 |
|------|------|------|
| exists 쿼리 | 명확한 에러 메시지, 빠른 실패 | 쿼리 1회 추가 |
| DB 제약 의존 | 쿼리 절약 | 에러 메시지 불명확, 롤백 비용 |

> DB unique constraint는 **최종 방어선**으로 반드시 설정. 글로벌 핸들러가 처리.

---

## 4. CoreException 사용

### ErrorType별 사용 상황

| ErrorType | HTTP | 사용 상황 | 예시 |
|-----------|------|----------|------|
| `NOT_FOUND` | 404 | 리소스 없음 | 회원 조회 실패 |
| `CONFLICT` | 409 | 중복 리소스 | 로그인 ID 중복 |
| `UNAUTHORIZED` | 401 | 인증 실패 | 비밀번호 불일치 |
| `BAD_REQUEST` | 400 | 비즈니스 규칙 위반 | 본인만 수정 가능 |

### 패턴

```java
// 조회 실패
User user = userRepository.findById(id)
    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다"));

// 중복 체크
if (userRepository.existsByEmail(email)) {
    throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 이메일입니다");
}

// 인증 실패
if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
    throw new CoreException(ErrorType.UNAUTHORIZED, "비밀번호가 일치하지 않습니다");
}

// 권한 검증
if (!order.getUserId().equals(currentUserId)) {
    throw new CoreException(ErrorType.BAD_REQUEST, "본인의 주문만 수정할 수 있습니다");
}
```

### 금지 사항

```java
// ❌ 금지: try-catch 사용
try {
    userService.signUp(...);
} catch (CoreException e) {
    // 처리
}

// ✅ 올바름: 글로벌 핸들러가 처리
userService.signUp(...);  // 예외는 그대로 전파
```

---

## 5. 외부 의존성 처리

### 원칙

- 외부 의존성은 **도메인 패키지에 인터페이스** 정의
- 구현체는 **infrastructure 패키지**에 배치
- **Entity.create() 파라미터로 전달**, Entity가 내부에서 처리

### 예시: PasswordEncoder

**인터페이스 (domain 패키지)**
```java
// domain/user/PasswordEncoder.java
public interface PasswordEncoder {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
```

**구현체 (infrastructure 패키지)**
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

**Service → Entity 전달**
```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;  // 인터페이스로 주입

    @Transactional
    public User signUp(String loginId, String rawPassword, ...) {
        // Entity.create()에 파라미터로 전달, Entity 내부에서 암호화 수행
        User user = User.create(loginId, rawPassword, ..., passwordEncoder);
        return userRepository.save(user);
    }

    public void login(String loginId, String rawPassword) {
        User user = getByLoginId(loginId);
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "비밀번호가 일치하지 않습니다");
        }
    }
}
```

---

## 6. 파라미터 규칙

### 6개 이하: 원시값 파라미터

```java
@Transactional
public User signUp(String loginId, String password, String name,
                   String email, LocalDate birthDate) {
    // 5개 파라미터 → 원시값으로 전달
}
```

### 7개 이상: Command 객체

```java
// Command 정의
public record CreateOrderCommand(
    Long userId,
    String productId,
    Integer quantity,
    String shippingAddress,
    String recipientName,
    String recipientPhone,
    String memo
) {}

// Service 메서드
@Transactional
public Order createOrder(CreateOrderCommand command) {
    // 7개 이상 → Command 객체로 그루핑
}
```

---

## 7. 메서드 패턴

### 생성 (Create)

```java
@Transactional
public User signUp(String loginId, String password, String name,
                   String email, LocalDate birthDate) {
    // 1. 중복 체크
    if (userRepository.existsByLoginId(loginId)) {
        throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다");
    }

    // 2. Entity 생성 및 저장 (외부 의존성은 파라미터로 전달)
    User user = User.create(loginId, password, name, birthDate, email, passwordEncoder);
    return userRepository.save(user);
}
```

### 조회 (Read)

```java
public User getById(Long id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다"));
}

public List<User> getAll() {
    return userRepository.findAllByDeletedAtIsNull();
}
```

### 수정 (Update)

```java
@Transactional
public User updateProfile(Long id, String name, String email) {
    // 1. 조회
    User user = getById(id);

    // 2. 중복 체크 (변경된 경우만)
    if (!user.getEmail().equals(email) && userRepository.existsByEmail(email)) {
        throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 이메일입니다");
    }

    // 3. 수정
    user.updateProfile(name, email);

    return user;  // 더티 체킹으로 자동 저장
}
```

### 삭제 (Delete)

```java
@Transactional
public void delete(Long id) {
    User user = getById(id);
    user.delete();  // Soft Delete
}
```

---

## 체크리스트

### 기본 구조
- [ ] `@Service`, `@RequiredArgsConstructor` 적용
- [ ] 클래스 레벨 `@Transactional(readOnly = true)`
- [ ] 쓰기 메서드에만 `@Transactional`

### 중복 체크
- [ ] exists 쿼리로 사전 체크
- [ ] CoreException(CONFLICT) 사용
- [ ] DataIntegrityViolationException try-catch 금지

### 예외 처리
- [ ] 조회 실패 → `NOT_FOUND`
- [ ] 중복 → `CONFLICT`
- [ ] 인증 실패 → `UNAUTHORIZED`
- [ ] try-catch 사용 안 함

### 외부 의존성
- [ ] 인터페이스 domain 패키지에 정의
- [ ] 구현체 infrastructure 패키지에 배치
- [ ] Entity.create() 파라미터로 전달

### 파라미터
- [ ] 6개 이하 → 원시값
- [ ] 7개 이상 → Command 객체

---

## 트러블슈팅

### 1. 조회 메서드에 `@Transactional` 붙임

**문제:** 불필요한 쓰기 트랜잭션 오버헤드
```java
// ❌ 조회인데 쓰기 트랜잭션
@Transactional
public User getById(Long id) { ... }

// ✅ 클래스 레벨 readOnly=true 상속
public User getById(Long id) { ... }
```

### 2. Service에서 암호화 후 Entity 생성

**문제:** 외부 의존성은 Entity.create() 파라미터로 전달
```java
// ❌ Service에서 암호화
String encoded = passwordEncoder.encode(rawPassword);
User user = User.create(loginId, encoded, ...);

// ✅ Entity.create()에 PasswordEncoder 전달
User user = User.create(loginId, rawPassword, ..., passwordEncoder);
```

### 3. DataIntegrityViolationException try-catch

**문제:** 중복 체크는 exists 쿼리로 사전 수행
```java
// ❌ DB 예외 catch
try {
    return userRepository.save(user);
} catch (DataIntegrityViolationException e) {
    throw new CoreException(ErrorType.CONFLICT, "중복");
}

// ✅ exists로 사전 체크
if (userRepository.existsByLoginId(loginId)) {
    throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다");
}
```

### 4. Entity 검증에 CoreException 사용

**문제:** Entity는 `IllegalArgumentException`, Service는 `CoreException`
```java
// ❌ Entity에서 CoreException
User.create() {
    throw new CoreException(ErrorType.BAD_REQUEST, "...");
}

// ✅ Entity → IllegalArgumentException, Service → CoreException
// Entity
throw new IllegalArgumentException("로그인 ID는 필수입니다");
// Service
throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다");
```

### 5. 수정 후 명시적 save() 호출

**문제:** 더티 체킹으로 자동 저장됨
```java
// ❌ 불필요한 save()
user.updateProfile(name, email);
userRepository.save(user);

// ✅ 더티 체킹으로 자동 저장
user.updateProfile(name, email);
return user;
```

### 6. Service 간 직접 호출

**문제:** 순환 참조 발생 가능
```java
// ❌ Service → Service 직접 호출
@Service
public class OrderService {
    private final UserService userService;  // 순환 참조 위험
}

// ✅ Repository 직접 사용 또는 Facade로 조합
@Service
public class OrderService {
    private final UserRepository userRepository;  // Repository 사용
}
```

---

## 참조 문서

| 문서 | 설명 |
|------|------|
| [user-service.md](./examples/user-service.md) | 회원 Service 예시 |
