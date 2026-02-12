# 회원 Repository 예시

User 도메인의 Repository 구현 예시입니다.

---

## 파일 구조

```
com.loopers/
├── domain/user/
│   └── UserRepository.java          # 인터페이스
└── infrastructure/user/
    ├── UserRepositoryImpl.java      # 구현체
    └── UserJpaRepository.java       # Spring Data JPA
```

---

## 1. UserRepository.java (domain 패키지)

```java
package com.loopers.domain.user;

import java.util.List;
import java.util.Optional;

/**
 * 회원 Repository 인터페이스
 * - domain 패키지에 위치
 * - 도메인이 필요로 하는 영속성 메서드만 정의
 */
public interface UserRepository {

    // ========================================
    // 저장
    // ========================================

    User save(User user);

    // ========================================
    // 단건 조회
    // ========================================

    Optional<User> findById(Long id);

    Optional<User> findByLoginId(String loginId);

    Optional<User> findByEmail(String email);

    // ========================================
    // 목록 조회
    // ========================================

    List<User> findAll();

    List<User> findAll(int page, int size);

    // ========================================
    // 존재 여부
    // ========================================

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    // ========================================
    // 카운트
    // ========================================

    long count();
}
```

---

## 2. UserJpaRepository.java (infrastructure 패키지)

```java
package com.loopers.infrastructure.user;

import com.loopers.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository
 * - infrastructure 패키지에 위치
 * - Soft Delete 조건 (DeletedAtIsNull) 포함
 */
public interface UserJpaRepository extends JpaRepository<User, Long> {

    // ========================================
    // 단건 조회 (Soft Delete 고려)
    // ========================================

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    Optional<User> findByLoginIdAndDeletedAtIsNull(String loginId);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    // ========================================
    // 목록 조회 (Soft Delete 고려)
    // ========================================

    List<User> findAllByDeletedAtIsNull();

    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    // ========================================
    // 존재 여부 (Soft Delete 고려)
    // ========================================

    boolean existsByLoginIdAndDeletedAtIsNull(String loginId);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    // ========================================
    // 카운트 (Soft Delete 고려)
    // ========================================

    long countByDeletedAtIsNull();
}
```

---

## 3. UserRepositoryImpl.java (infrastructure 패키지)

```java
package com.loopers.infrastructure.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository 구현체
 * - infrastructure 패키지에 위치
 * - 도메인 인터페이스 구현
 * - JpaRepository에 위임
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;

    // ========================================
    // 저장
    // ========================================

    @Override
    public User save(User user) {
        return jpaRepository.save(user);
    }

    // ========================================
    // 단건 조회
    // ========================================

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<User> findByLoginId(String loginId) {
        return jpaRepository.findByLoginIdAndDeletedAtIsNull(loginId);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmailAndDeletedAtIsNull(email);
    }

    // ========================================
    // 목록 조회
    // ========================================

    @Override
    public List<User> findAll() {
        return jpaRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public List<User> findAll(int page, int size) {
        return jpaRepository.findAllByDeletedAtIsNull(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();
    }

    // ========================================
    // 존재 여부
    // ========================================

    @Override
    public boolean existsByLoginId(String loginId) {
        return jpaRepository.existsByLoginIdAndDeletedAtIsNull(loginId);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmailAndDeletedAtIsNull(email);
    }

    // ========================================
    // 카운트
    // ========================================

    @Override
    public long count() {
        return jpaRepository.countByDeletedAtIsNull();
    }
}
```

---

## 핵심 포인트

### 1. 패키지 분리

```
domain/user/UserRepository.java       ← 인터페이스 (도메인)
infrastructure/user/UserJpaRepository ← JPA (인프라)
infrastructure/user/UserRepositoryImpl ← 구현체 (인프라)
```

**왜 분리하는가?**
- 도메인 레이어가 인프라(JPA)에 의존하지 않음
- 테스트 시 Mock Repository 주입 용이
- JPA 외 다른 구현으로 교체 가능

### 2. Soft Delete 일괄 처리

```java
// RepositoryImpl에서 항상 DeletedAtIsNull 적용
@Override
public Optional<User> findById(Long id) {
    return jpaRepository.findByIdAndDeletedAtIsNull(id);  // ✅
}

// Service에서는 신경 쓸 필요 없음
User user = userRepository.findById(id)
    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND));
```

### 3. 도메인 타입만 노출

```java
// ❌ JPA 타입 노출
Page<User> findAll(Pageable pageable);

// ✅ 도메인 타입만 노출
List<User> findAll(int page, int size);
```

### 4. @Repository는 Impl에만

```java
// ❌ 인터페이스에 @Repository
@Repository
public interface UserRepository { }

// ✅ 구현체에만 @Repository
@Repository
public class UserRepositoryImpl implements UserRepository { }
```

---

## 관련 테스트

```java
class UserRepositoryIT extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Nested
    class 저장 {

        @Test
        void 성공() {
            // given
            User user = User.create("john123", "password", "홍길동", "john@test.com", null);

            // when
            User saved = userRepository.save(user);

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    class 조회 {

        @Test
        void ID로_조회_성공() {
            // given
            User user = User.create("john123", "password", "홍길동", "john@test.com", null);
            User saved = userRepository.save(user);

            // when
            Optional<User> found = userRepository.findById(saved.getId());

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getLoginId()).isEqualTo("john123");
        }

        @Test
        void 삭제된_회원은_조회되지_않는다() {
            // given
            User user = User.create("john123", "password", "홍길동", "john@test.com", null);
            User saved = userRepository.save(user);
            saved.delete();  // Soft Delete

            // when
            Optional<User> found = userRepository.findById(saved.getId());

            // then
            assertThat(found).isEmpty();
        }

        @Test
        void 로그인_ID로_조회_성공() {
            // given
            User user = User.create("john123", "password", "홍길동", "john@test.com", null);
            userRepository.save(user);

            // when
            Optional<User> found = userRepository.findByLoginId("john123");

            // then
            assertThat(found).isPresent();
        }
    }

    @Nested
    class 존재_여부 {

        @Test
        void 존재하면_true() {
            // given
            User user = User.create("john123", "password", "홍길동", "john@test.com", null);
            userRepository.save(user);

            // when & then
            assertThat(userRepository.existsByLoginId("john123")).isTrue();
        }

        @Test
        void 존재하지_않으면_false() {
            assertThat(userRepository.existsByLoginId("notexist")).isFalse();
        }

        @Test
        void 삭제된_회원은_존재하지_않음() {
            // given
            User user = User.create("john123", "password", "홍길동", "john@test.com", null);
            User saved = userRepository.save(user);
            saved.delete();

            // when & then
            assertThat(userRepository.existsByLoginId("john123")).isFalse();
        }
    }
}
```

---

## QueryDSL 확장 예시

```java
// 검색 조건
public record UserSearchCondition(
    String loginId,
    String name,
    String email
) {}

// Custom 인터페이스
public interface UserRepositoryCustom {
    List<User> search(UserSearchCondition condition);
}

// Custom 구현
@RequiredArgsConstructor
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<User> search(UserSearchCondition condition) {
        return queryFactory
            .selectFrom(user)
            .where(
                loginIdContains(condition.loginId()),
                nameContains(condition.name()),
                emailContains(condition.email()),
                user.deletedAt.isNull()
            )
            .orderBy(user.createdAt.desc())
            .fetch();
    }

    private BooleanExpression loginIdContains(String loginId) {
        return StringUtils.hasText(loginId)
            ? user.loginId.contains(loginId)
            : null;
    }

    private BooleanExpression nameContains(String name) {
        return StringUtils.hasText(name)
            ? user.name.contains(name)
            : null;
    }

    private BooleanExpression emailContains(String email) {
        return StringUtils.hasText(email)
            ? user.email.contains(email)
            : null;
    }
}

// JpaRepository에 상속
public interface UserJpaRepository
    extends JpaRepository<User, Long>, UserRepositoryCustom {
    // ...
}
```
