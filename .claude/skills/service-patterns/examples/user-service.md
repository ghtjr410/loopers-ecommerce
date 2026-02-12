# 회원 Service 예시

User 도메인의 Service 구현 예시입니다.

---

## 파일 위치

```
com.loopers.domain.user/
├── User.java              # Entity
├── UserRepository.java    # Repository 인터페이스
├── UserService.java       # Domain Service ← 여기
└── PasswordEncoder.java   # 외부 의존성 인터페이스
```

---

## UserService.java

```java
package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ========================================
    // 조회 메서드 (readOnly = true)
    // ========================================

    public User getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다"));
    }

    public User getByLoginId(String loginId) {
        return userRepository.findByLoginIdAndDeletedAtIsNull(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다"));
    }

    public List<User> getAll() {
        return userRepository.findAllByDeletedAtIsNull();
    }

    public boolean existsByLoginId(String loginId) {
        return userRepository.existsByLoginId(loginId);
    }

    // ========================================
    // 쓰기 메서드 (@Transactional)
    // ========================================

    @Transactional
    public User signUp(String loginId, String password, String name,
                       String email, LocalDate birthDate) {
        // 1. 중복 체크
        validateDuplicateLoginId(loginId);
        validateDuplicateEmail(email);

        // 2. Entity 생성 및 저장 (외부 의존성은 파라미터로 전달)
        User user = User.create(loginId, password, name, birthDate, email, passwordEncoder);
        return userRepository.save(user);
    }

    @Transactional
    public User updateProfile(Long id, String name, String email) {
        // 1. 조회
        User user = getById(id);

        // 2. 이메일 변경 시 중복 체크
        if (!user.getEmail().equals(email)) {
            validateDuplicateEmail(email);
        }

        // 3. 수정 (Entity 비즈니스 메서드 호출)
        user.updateProfile(name, email);

        return user;  // 더티 체킹으로 자동 저장
    }

    @Transactional
    public void changePassword(Long id, String currentPassword, String newPassword) {
        // 1. 조회
        User user = getById(id);

        // 2. 현재 비밀번호 검증
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다");
        }

        // 3. 비밀번호 변경 (Entity가 암호화 처리)
        user.changePassword(newPassword, passwordEncoder);
    }

    @Transactional
    public void delete(Long id) {
        User user = getById(id);
        user.delete();  // Soft Delete
    }

    @Transactional
    public void restore(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다"));
        user.restore();
    }

    // ========================================
    // 중복 체크 (private)
    // ========================================

    private void validateDuplicateLoginId(String loginId) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다");
        }
    }

    private void validateDuplicateEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 이메일입니다");
        }
    }
}
```

---

## 핵심 포인트

### 1. 트랜잭션 분리

```java
@Transactional(readOnly = true)  // 클래스 레벨: 기본 읽기 전용
public class UserService {

    // 조회 → readOnly = true (클래스 레벨 적용)
    public User getById(Long id) { ... }

    // 쓰기 → @Transactional (메서드 레벨 오버라이드)
    @Transactional
    public User signUp(...) { ... }
}
```

### 2. 중복 체크는 exists 쿼리로

```java
// ✅ exists 쿼리로 사전 체크
private void validateDuplicateLoginId(String loginId) {
    if (userRepository.existsByLoginId(loginId)) {
        throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다");
    }
}

// ❌ try-catch 금지
try {
    userRepository.save(user);
} catch (DataIntegrityViolationException e) {
    throw new CoreException(ErrorType.CONFLICT);
}
```

### 3. 외부 의존성은 Entity에 파라미터로 전달

```java
@Transactional
public User signUp(String loginId, String password, ...) {
    // Entity.create()에 rawPassword와 PasswordEncoder를 전달
    // Entity가 내부에서 암호화 수행
    User user = User.create(loginId, password, ..., passwordEncoder);
    return userRepository.save(user);
}
```

### 4. 수정은 더티 체킹 활용

```java
@Transactional
public User updateProfile(Long id, String name, String email) {
    User user = getById(id);
    user.updateProfile(name, email);
    return user;  // save() 호출 불필요, 더티 체킹으로 자동 저장
}
```

### 5. 조회 메서드 재사용

```java
// getById를 내부에서 재사용
@Transactional
public User updateProfile(Long id, String name, String email) {
    User user = getById(id);  // 조회 로직 재사용
    ...
}

@Transactional
public void delete(Long id) {
    User user = getById(id);  // 조회 로직 재사용
    user.delete();
}
```

---

## 관련 테스트

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

            // when
            User user = userService.signUp(loginId, password, name, email, null);

            // then
            assertThat(user.getId()).isNotNull();
            assertThat(user.getLoginId()).isEqualTo(loginId);

            // DB 저장 확인
            assertThat(userRepository.findByLoginIdAndDeletedAtIsNull(loginId))
                .isPresent();
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
        void 비밀번호가_암호화되어_저장된다() {
            // given
            String rawPassword = "Pass1234!";

            // when
            User user = userService.signUp("john123", rawPassword, "홍길동", "john@test.com", null);

            // then
            assertThat(user.getPassword()).isNotEqualTo(rawPassword);
            assertThat(user.getPassword()).startsWith("$2");  // BCrypt prefix
        }
    }

    @Nested
    class 조회 {

        @Test
        void ID로_조회_성공() {
            // given
            User saved = userService.signUp("john123", "Pass1234!", "홍길동", "john@test.com", null);

            // when
            User found = userService.getById(saved.getId());

            // then
            assertThat(found.getLoginId()).isEqualTo("john123");
        }

        @Test
        void 존재하지_않는_ID면_예외발생() {
            assertThatThrownBy(() -> userService.getById(999L))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> {
                    CoreException ce = (CoreException) e;
                    assertThat(ce.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                });
        }
    }

    @Nested
    class 비밀번호_변경 {

        @Test
        void 성공() {
            // given
            String oldPassword = "Pass1234!";
            User user = userService.signUp("john123", oldPassword, "홍길동", "john@test.com", null);

            // when
            userService.changePassword(user.getId(), oldPassword, "NewPass5678!");

            // then
            User updated = userService.getById(user.getId());
            assertThat(updated.getPassword()).isNotEqualTo(user.getPassword());
        }

        @Test
        void 현재_비밀번호_불일치면_예외발생() {
            // given
            User user = userService.signUp("john123", "Pass1234!", "홍길동", "john@test.com", null);

            // when & then
            assertThatThrownBy(() ->
                userService.changePassword(user.getId(), "WrongPassword!", "NewPass5678!")
            ).isInstanceOf(CoreException.class)
             .satisfies(e -> {
                 CoreException ce = (CoreException) e;
                 assertThat(ce.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
             });
        }
    }

    @Nested
    class 삭제 {

        @Test
        void Soft_Delete_성공() {
            // given
            User user = userService.signUp("john123", "Pass1234!", "홍길동", "john@test.com", null);

            // when
            userService.delete(user.getId());

            // then
            assertThat(userRepository.findByLoginIdAndDeletedAtIsNull("john123"))
                .isEmpty();  // 삭제된 데이터는 조회 안됨
            assertThat(userRepository.findById(user.getId()))
                .isPresent();  // 실제 데이터는 존재
        }
    }
}
```

---

## 복잡한 비즈니스 로직 예시

```java
@Transactional
public void transferPoints(Long fromUserId, Long toUserId, Integer amount) {
    // 1. 조회
    User fromUser = getById(fromUserId);
    User toUser = getById(toUserId);

    // 2. 비즈니스 규칙 검증
    if (fromUser.getPoints() < amount) {
        throw new CoreException(ErrorType.BAD_REQUEST, "포인트가 부족합니다");
    }
    if (fromUserId.equals(toUserId)) {
        throw new CoreException(ErrorType.BAD_REQUEST, "본인에게 전송할 수 없습니다");
    }

    // 3. 상태 변경 (Entity 메서드 호출)
    fromUser.deductPoints(amount);
    toUser.addPoints(amount);

    // 더티 체킹으로 자동 저장
}
```
