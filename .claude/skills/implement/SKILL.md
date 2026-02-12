---
name: implement
description: PRD 기반 AI 최적화 구현. 기존 코드 패턴을 참조하여 일관된 코드 작성. "구현해줘", "개발해줘", "만들어줘", "코드 작성" 요청 시 사용.
---

# Implement Skill

PRD/AC 기반 **AI 최적화 구현** 가이드입니다.

## 핵심 철학

```
PRD (What) + 기존 패턴 (How) → 일관된 구현
```

- PRD/AC는 **약속된 인터페이스** (계약)
- 내부 구현은 **블랙박스** - 인터페이스만 지키면 됨
- 기존 코드 패턴을 참조하여 **일관성** 유지
- **AC ↔ 테스트 1:1 매핑**이 TDD의 본질

---

## 워크플로우

```
Phase 0: CONTEXT (컨텍스트)
    ↓
Phase 1: INTERFACE (인터페이스 정의)
    ↓
Phase 2: IMPLEMENT + TEST (구현과 테스트 동시)
    ↓
Phase 3: VERIFY (AC 매핑 검증)
    ↓
Phase 4: REFACTOR (리팩터링)
```

---

## Phase 0: CONTEXT (컨텍스트 파악)

**목적:** 기존 코드 패턴과 프로젝트 규칙 파악

### 필수 읽기 파일

1. `.claude/rules/core/layer-patterns.md` - 계층 구조
2. `.claude/rules/core/naming-conventions.md` - 네이밍 규칙
3. `.claude/rules/core/exception-patterns.md` - 예외 처리
4. `.claude/rules/core/dto-patterns.md` - DTO 패턴

### 기존 코드 참조 (유사 도메인 있는 경우)

- `domain/{유사도메인}/` - Entity, Service, Repository
- `interfaces/api/{유사도메인}/` - Controller, DTO
- `infrastructure/{유사도메인}/` - Repository 구현

### 산출물

- 참조할 기존 코드 패턴 목록
- 준수해야 할 규칙 체크리스트

---

## Phase 1: INTERFACE (인터페이스 정의)

**목적:** PRD/AC에서 공개 인터페이스 추출

### 입력

PRD 문서 (mission/*.md)

### 추출 항목

| 계층 | 추출 내용 |
|------|----------|
| Controller | API 스펙 (Method, Endpoint, Request/Response) |
| Facade | 공개 메서드 시그니처 |
| Service | 비즈니스 메서드 시그니처 |
| Repository | 필요한 쿼리 메서드 |
| Entity | 필드, 정적 팩토리, 비즈니스 메서드 |

### 산출물

- 각 계층의 인터페이스 정의 (구현 없이 시그니처만)

---

## Phase 2: IMPLEMENT + TEST (구현과 테스트 동시)

**목적:** 구현과 테스트를 동시에 작성

### 구현 순서 (상향식)

1. **Entity + 단위 테스트** (도메인 모델 + 불변식 검증)
   - BaseEntity 상속
   - 정적 팩토리 `create()`
   - 도메인 불변식 검증
   - 단위 테스트로 불변식 검증

2. **Repository** (인터페이스 + 구현)
   - 인터페이스: `domain/{도메인}/` 패키지
   - 구현체: `infrastructure/{도메인}/` 패키지

3. **Service + 통합 테스트** (비즈니스 로직 + DB 연동)
   - 중복 체크 (exists 쿼리)
   - 트랜잭션 관리
   - CoreException 사용
   - 통합 테스트 (TestContainers)

4. **Facade** (오케스트레이션)
   - Entity → Info 변환
   - Service 호출 조합

5. **Controller + DTO**
   - ApiSpec 인터페이스 + Controller 구현
   - DTO 컨테이너 (`{Domain}V{version}Dto`)
   - Bean Validation

### 테스트 패턴

- **3A 원칙**: Arrange - Act - Assert
- **AC와 1:1 매핑** (중요!)
- 도메인 테스트: 단위 테스트
- Service 테스트: 통합 테스트 (TestContainers)

### 참조

- `.claude/skills/test-patterns/SKILL.md` - 테스트 전략
- `.claude/skills/test-patterns/examples/` - 테스트 예시
- `references/layer-checklist.md` - 계층별 체크리스트

### 산출물

- 구현 코드 + 테스트 코드 (함께)
- 테스트 실행 결과 (모두 초록불)

---

## Phase 3: VERIFY (AC 매핑 검증)

**목적:** AC 커버리지 검증

### AC 매핑 테이블 작성

```markdown
| AC# | 조건 | 테스트 메서드 | 결과 |
|-----|------|-------------|------|
| AC-1 | 모든 필수값 유효 | 성공() | ✅ |
| AC-2 | loginId 누락 | 로그인_ID_누락이면_예외발생() | ✅ |
| AC-14 | loginId 중복 | 로그인_ID_중복이면_예외발생() | ✅ |
```

### 검증 체크리스트

- [ ] 모든 AC에 대응하는 테스트가 있는가?
- [ ] AC에 없는 테스트가 있는가? (과잉 구현 신호)
- [ ] 전체 테스트 실행 통과?

### 과잉/누락 판단

- **AC에 없는 테스트** → 과잉 구현이거나 PRD 부족
- **테스트 없는 AC** → 구현 누락

### 산출물

- AC 매핑 테이블
- 전체 테스트 실행 결과

---

## Phase 4: REFACTOR (리팩터링)

**목적:** 코드 품질 개선 (테스트 유지)

### 체크리스트

- [ ] 중복 코드 제거
- [ ] 불필요한 코드 제거
- [ ] unused import 제거
- [ ] 모든 테스트 통과 확인

### 산출물

- 리팩터링된 코드
- 테스트 실행 결과 (모두 초록불 유지)

---

## 진행 가이드

### 시작하기

```
PRD 파일 경로를 알려주세요.
예: mission/round1.md

또는 구현할 Feature를 지정해주세요.
예: Feature 1: 회원가입
```

### Phase 전환 시 보고

```
[Phase X 완료]
- 완료 항목: ...
- 생성 파일: ...

[Phase X+1 시작]
- 작업 대상: ...
```

### 마무리

```
[구현 완료]

생성 파일:
- src/main/.../User.java
- src/main/.../UserService.java
- ...

테스트 결과:
- 단위 테스트: X개 통과
- 통합 테스트: Y개 통과
- 전체: Z개 통과

다음 Feature로 진행할까요?
```

---

## 참조 문서

| 문서 | 설명 |
|------|------|
| [layer-checklist.md](./references/layer-checklist.md) | 계층별 구현 체크리스트 |
| [user-signup.md](./examples/user-signup.md) | 회원가입 구현 예시 |
