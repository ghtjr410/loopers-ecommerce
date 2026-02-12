---
name: repository-patterns
description: Repository 구현 패턴. "Repository 만들어줘", "DB 접근 계층 구현해줘", "JPA 쿼리 작성해줘" 요청 시 사용. 인터페이스/구현체 분리, JpaRepository, Soft Delete 조회, 메서드 네이밍 패턴 제공.
---

# Repository Patterns

Repository 구현 가이드입니다.

## 필수 규칙 참조

- `.claude/rules/core/layer-patterns.md` - Repository 역할
- `.claude/rules/core/naming-conventions.md` - Repository 네이밍

---

## 패키지 구조

```
com.loopers/
├── domain/{domain}/
│   └── {Domain}Repository.java      # 인터페이스 (domain에 정의)
└── infrastructure/{domain}/
    ├── {Domain}RepositoryImpl.java  # 구현체
    └── {Domain}JpaRepository.java   # Spring Data JPA
```

---

## 1. Repository 인터페이스 (domain 패키지)

**목적:** 도메인이 필요로 하는 영속성 메서드 정의

### 템플릿

```java
package com.loopers.domain.{domain};

import java.util.List;
import java.util.Optional;

public interface {Domain}Repository {

    // 저장
    {Domain} save({Domain} {domain});

    // 단건 조회
    Optional<{Domain}> findById(Long id);
    Optional<{Domain}> findBy{Field}(String {field});

    // 목록 조회
    List<{Domain}> findAll();

    // 존재 여부
    boolean existsBy{Field}(String {field});
}
```

### 메서드 네이밍 규칙

| 메서드 | 반환 타입 | 용도 |
|--------|----------|------|
| `save(entity)` | `Entity` | 저장/수정 |
| `findById(id)` | `Optional<Entity>` | ID로 단건 조회 |
| `findBy{Field}(value)` | `Optional<Entity>` | 필드로 단건 조회 |
| `findAll()` | `List<Entity>` | 전체 조회 |
| `findAllBy{Condition}()` | `List<Entity>` | 조건부 목록 조회 |
| `existsBy{Field}(value)` | `boolean` | 존재 여부 |

### 주의사항

```java
// ❌ JPA 의존 노출 금지
Page<User> findAll(Pageable pageable);  // Spring Data 타입 노출

// ✅ 도메인 타입만 사용
List<User> findAll(int page, int size);
```

---

## 2. JpaRepository (infrastructure 패키지)

**목적:** Spring Data JPA 기능 활용

### 템플릿

```java
package com.loopers.infrastructure.{domain};

import com.loopers.domain.{domain}.{Domain};
import org.springframework.data.jpa.repository.JpaRepository;

public interface {Domain}JpaRepository extends JpaRepository<{Domain}, Long> {

    // Soft Delete 고려한 조회
    Optional<{Domain}> findByIdAndDeletedAtIsNull(Long id);
    Optional<{Domain}> findBy{Field}AndDeletedAtIsNull(String {field});

    // 존재 여부 (Soft Delete 고려)
    boolean existsBy{Field}AndDeletedAtIsNull(String {field});

    // 전체 조회 (삭제되지 않은 것만)
    List<{Domain}> findAllByDeletedAtIsNull();
}
```

### Soft Delete 조회 패턴

| 용도 | 메서드명 |
|------|---------|
| 삭제 안된 것만 | `findBy{Field}AndDeletedAtIsNull()` |
| 삭제된 것만 | `findBy{Field}AndDeletedAtIsNotNull()` |
| 전체 (삭제 포함) | `findBy{Field}()` |

---

## 3. RepositoryImpl (infrastructure 패키지)

**목적:** 도메인 인터페이스 구현

### 템플릿

```java
package com.loopers.infrastructure.{domain};

import com.loopers.domain.{domain}.{Domain};
import com.loopers.domain.{domain}.{Domain}Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class {Domain}RepositoryImpl implements {Domain}Repository {

    private final {Domain}JpaRepository jpaRepository;

    @Override
    public {Domain} save({Domain} {domain}) {
        return jpaRepository.save({domain});
    }

    @Override
    public Optional<{Domain}> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<{Domain}> findBy{Field}(String {field}) {
        return jpaRepository.findBy{Field}AndDeletedAtIsNull({field});
    }

    @Override
    public List<{Domain}> findAll() {
        return jpaRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public boolean existsBy{Field}(String {field}) {
        return jpaRepository.existsBy{Field}AndDeletedAtIsNull({field});
    }
}
```

### 역할 분리

| 클래스 | 역할 |
|--------|------|
| `{Domain}Repository` | 도메인이 필요한 메서드 정의 (인터페이스) |
| `{Domain}JpaRepository` | Spring Data JPA 기능 활용 |
| `{Domain}RepositoryImpl` | 도메인 인터페이스 구현, JPA 호출 위임 |

---

## 4. QueryDSL (복잡한 쿼리)

### 언제 사용하는가?

| 상황 | 도구 |
|------|------|
| 단순 CRUD | Spring Data JPA |
| 동적 조건 쿼리 | QueryDSL |
| 복잡한 조인 | QueryDSL |
| 집계/통계 | QueryDSL or Native Query |

### 구조

```java
// QueryDSL 전용 인터페이스
public interface {Domain}RepositoryCustom {
    List<{Domain}> searchByCondition({Domain}SearchCondition condition);
}

// QueryDSL 구현
@RequiredArgsConstructor
public class {Domain}RepositoryCustomImpl implements {Domain}RepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<{Domain}> searchByCondition({Domain}SearchCondition condition) {
        return queryFactory
            .selectFrom({domain})
            .where(
                loginIdContains(condition.loginId()),
                nameContains(condition.name()),
                deletedAtIsNull()
            )
            .fetch();
    }

    private BooleanExpression loginIdContains(String loginId) {
        return StringUtils.hasText(loginId)
            ? {domain}.loginId.contains(loginId)
            : null;
    }

    private BooleanExpression nameContains(String name) {
        return StringUtils.hasText(name)
            ? {domain}.name.contains(name)
            : null;
    }

    private BooleanExpression deletedAtIsNull() {
        return {domain}.deletedAt.isNull();
    }
}

// JpaRepository에 Custom 상속
public interface {Domain}JpaRepository
    extends JpaRepository<{Domain}, Long>, {Domain}RepositoryCustom {
}
```

---

## 5. Soft Delete 처리 전략

### 조회 시 기본 원칙

```java
// ❌ 삭제된 데이터도 조회됨
Optional<User> findByLoginId(String loginId);

// ✅ 삭제되지 않은 데이터만 조회
Optional<User> findByLoginIdAndDeletedAtIsNull(String loginId);
```

### RepositoryImpl에서 일괄 처리

```java
@Override
public Optional<User> findById(Long id) {
    // Impl에서 항상 DeletedAtIsNull 적용
    return jpaRepository.findByIdAndDeletedAtIsNull(id);
}

@Override
public Optional<User> findByLoginId(String loginId) {
    return jpaRepository.findByLoginIdAndDeletedAtIsNull(loginId);
}
```

### 삭제된 데이터 조회가 필요한 경우

```java
// 도메인 인터페이스에 별도 메서드 추가
public interface UserRepository {
    Optional<User> findById(Long id);                    // 삭제 안된 것
    Optional<User> findByIdIncludeDeleted(Long id);      // 삭제 포함
}

// 구현
@Override
public Optional<User> findByIdIncludeDeleted(Long id) {
    return jpaRepository.findById(id);  // DeletedAt 조건 없이
}
```

---

## 6. 페이징 처리

### 도메인 인터페이스

```java
public interface UserRepository {
    // 도메인 타입으로 반환
    List<User> findAll(int page, int size);
    long count();
}
```

### RepositoryImpl

```java
@Override
public List<User> findAll(int page, int size) {
    return jpaRepository.findAllByDeletedAtIsNull(
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
    ).getContent();
}

@Override
public long count() {
    return jpaRepository.countByDeletedAtIsNull();
}
```

### JpaRepository

```java
public interface UserJpaRepository extends JpaRepository<User, Long> {
    Page<User> findAllByDeletedAtIsNull(Pageable pageable);
    long countByDeletedAtIsNull();
}
```

---

## 체크리스트

### 구조
- [ ] 인터페이스는 `domain/{domain}/` 패키지
- [ ] JpaRepository, Impl은 `infrastructure/{domain}/` 패키지
- [ ] `@Repository`는 Impl에만 적용

### 네이밍
- [ ] 인터페이스: `{Domain}Repository`
- [ ] JPA: `{Domain}JpaRepository`
- [ ] 구현체: `{Domain}RepositoryImpl`

### Soft Delete
- [ ] 조회 메서드에 `AndDeletedAtIsNull` 적용
- [ ] RepositoryImpl에서 일괄 처리

### 메서드
- [ ] `save()` → Entity 반환
- [ ] `findBy*()` → `Optional<Entity>` 반환
- [ ] `existsBy*()` → `boolean` 반환

---

## 트러블슈팅

### 1. 삭제된 데이터가 조회됨

**원인:** `AndDeletedAtIsNull` 조건 누락
```java
// ❌ 삭제된 데이터도 조회됨
Optional<User> findByLoginId(String loginId);

// ✅ JpaRepository에서 조건 추가
Optional<User> findByLoginIdAndDeletedAtIsNull(String loginId);
```

### 2. Repository 인터페이스에 JPA 타입 노출

**문제:** 도메인이 infrastructure에 의존하게 됨
```java
// ❌ domain 패키지에서 Spring Data 타입 사용
public interface UserRepository {
    Page<User> findAll(Pageable pageable);
}

// ✅ 도메인 타입만 사용
public interface UserRepository {
    List<User> findAll(int page, int size);
}
```

### 3. `@Repository`를 인터페이스에 붙임

**문제:** `@Repository`는 구현체에만 적용
```java
// ❌ 인터페이스에 @Repository
@Repository
public interface UserRepository { ... }

// ✅ 구현체에만 @Repository
public interface UserRepository { ... }

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository { ... }
```

### 4. RepositoryImpl 없이 JpaRepository만 사용

**문제:** 도메인이 infrastructure에 직접 의존
```java
// ❌ Service에서 JpaRepository 직접 사용
@Service
public class UserService {
    private final UserJpaRepository jpaRepository;  // infrastructure 의존
}

// ✅ 도메인 인터페이스 사용
@Service
public class UserService {
    private final UserRepository userRepository;  // domain 의존
}
```

### 5. exists 쿼리에서 Soft Delete 미고려

**원인:** exists도 `AndDeletedAtIsNull` 필요
```java
// ❌ 삭제된 데이터도 존재로 판단
boolean existsByLoginId(String loginId);

// ✅ 삭제되지 않은 데이터만 체크
boolean existsByLoginIdAndDeletedAtIsNull(String loginId);
```

### 6. QueryDSL에서 deletedAt 조건 누락

**원인:** 동적 쿼리에서도 Soft Delete 필수
```java
// ❌ deletedAt 조건 없음
return queryFactory.selectFrom(user)
    .where(nameContains(name))
    .fetch();

// ✅ deletedAt.isNull() 항상 포함
return queryFactory.selectFrom(user)
    .where(
        nameContains(name),
        user.deletedAt.isNull()  // 필수
    )
    .fetch();
```

---

## 참조 문서

| 문서 | 설명 |
|------|------|
| [user-repository.md](./examples/user-repository.md) | 회원 Repository 예시 |
