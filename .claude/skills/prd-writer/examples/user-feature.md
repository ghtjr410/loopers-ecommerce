# PLAN: 회원 서비스

> 작성일: 2024-01-15
> 버전: v1.0

---

## 프로젝트 컨텍스트

> `.claude/rules/core/` 참조 결과

| 항목 | 프로젝트 표준 |
|------|--------------|
| HTTP 상태코드 | 성공: 200, 실패: 400/401/404/409 |
| 에러 응답 | `ApiResponse.fail(ErrorType.XXX)` |
| 검증 위치 | 형식→Request DTO, 불변식→Domain Entity |
| 중복 체크 | Service에서 exists 쿼리로 명시적 수행 |

---

## Feature 1: 회원가입

### 개요
| 항목 | 내용 |
|------|------|
| 목적 | 신규 사용자가 서비스에 가입한다 |
| Actor | 비회원 |
| 우선순위 | P0 |
| 선행 조건 | 없음 |
| 후행 영향 | Feature 2(로그인), Feature 3(내 정보 조회)에서 회원 정보 참조 |

### API 명세
| 항목 | 내용 |
|------|------|
| Method | POST |
| Endpoint | /api/v1/users |
| Auth | 불필요 |

#### Request Body
| 필드 | 타입 | 필수 | 검증 규칙 | 예시 |
|------|------|------|----------|------|
| loginId | String | Y | 4-20자, 영문소문자+숫자, 영문으로 시작 | "john123" |
| password | String | Y | 8-20자, 영문+숫자+특수문자 각 1개 이상 | "Pass1234!" |
| name | String | Y | 2-20자, 한글 또는 영문 | "홍길동" |
| email | String | Y | 이메일 형식, 최대 100자 | "john@test.com" |
| birthDate | LocalDate | N | 과거 날짜만 허용 | "1995-03-15" |

#### Response Body (200 OK)
| 필드 | 타입 | 설명 |
|------|------|------|
| loginId | String | 로그인 ID |
| name | String | 이름 (마스킹: 홍*동) |
| email | String | 이메일 |
| birthDate | LocalDate | 생년월일 (nullable) |

### Acceptance Criteria

#### 정상 케이스
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-1 | 모든 필수값 유효 | POST /api/v1/users | 200, 회원 생성, 비밀번호 BCrypt 암호화 저장 |
| AC-2 | birthDate 미입력 | POST /api/v1/users | 200, birthDate null로 저장 |

#### 실패 케이스 - 입력값 검증
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-3 | loginId 3자 (최소 미달) | POST /api/v1/users | 400 |
| AC-4 | loginId 21자 (최대 초과) | POST /api/v1/users | 400 |
| AC-5 | loginId 숫자로 시작 | POST /api/v1/users | 400 |
| AC-6 | loginId 대문자 포함 | POST /api/v1/users | 400 |
| AC-7 | password 7자 (최소 미달) | POST /api/v1/users | 400 |
| AC-8 | password 영문만 | POST /api/v1/users | 400 |
| AC-9 | password 특수문자 없음 | POST /api/v1/users | 400 |
| AC-10 | name 1자 (최소 미달) | POST /api/v1/users | 400 |
| AC-11 | name 숫자 포함 | POST /api/v1/users | 400 |
| AC-12 | email 형식 오류 | POST /api/v1/users | 400 |
| AC-13 | birthDate 미래 날짜 | POST /api/v1/users | 400 |

#### 실패 케이스 - 비즈니스 규칙
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-14 | loginId 중복 | POST /api/v1/users | 409, "이미 사용 중인 로그인 ID입니다" |
| AC-15 | email 중복 | POST /api/v1/users | 409, "이미 사용 중인 이메일입니다" |
| AC-16 | 탈퇴한 회원의 loginId | POST /api/v1/users | 409, "이미 사용 중인 로그인 ID입니다" |

### 비즈니스 규칙
| 규칙# | 내용 | 구현 위치 |
|-------|------|----------|
| BR-1 | 비밀번호는 BCrypt로 암호화 저장 | Service |
| BR-2 | loginId는 생성 후 변경 불가 | 전체 (수정 API 미제공) |
| BR-3 | 탈퇴한 회원의 loginId도 재사용 불가 | Service (soft delete 조건 포함 조회) |
| BR-4 | 이름은 응답 시 마스킹 처리 (가운데 글자 *) | Info 변환 시 |

### 결정 사항
| 질문 | 결정 | 이유 |
|------|------|------|
| 이메일 인증 필요? | No | MVP 범위 외, Sprint 2에서 검토 |
| 비밀번호 정책 수준? | 중간 (8자+영문+숫자+특수문자) | 보안과 사용성 균형 |
| 중복 체크 시 탈퇴 회원 포함? | Yes | 재가입 시 혼란 방지 |
| 응답에 id(PK) 포함? | No | 보안상 내부 ID 노출 지양 |

### 미결 사항
| 항목 | 영향 범위 | 결정 기한 | 설계 대응 |
|------|----------|----------|----------|
| 소셜 로그인 | 회원가입 흐름 변경 | Sprint 2 전 | 현재는 일반 가입만, 추후 AuthProvider 분리 |
| 본인인증 | 회원가입 필수 조건 | Sprint 3 전 | 현재는 미적용, 추후 인터페이스 추가 |

---

## Feature 2: 내 정보 조회

### 개요
| 항목 | 내용 |
|------|------|
| 목적 | 회원이 자신의 정보를 조회한다 |
| Actor | 회원 |
| 우선순위 | P0 |
| 선행 조건 | Feature 1(회원가입) 완료 |
| 후행 영향 | 없음 |

### API 명세
| 항목 | 내용 |
|------|------|
| Method | GET |
| Endpoint | /api/v1/users/me |
| Auth | 회원 (헤더 인증) |

#### Request Header
| 헤더 | 필수 | 설명 |
|------|------|------|
| X-Login-Id | Y | 로그인 ID |
| X-Login-Pw | Y | 비밀번호 (평문) |

#### Response Body (200 OK)
| 필드 | 타입 | 설명 |
|------|------|------|
| loginId | String | 로그인 ID |
| name | String | 이름 (마스킹) |
| email | String | 이메일 |
| birthDate | LocalDate | 생년월일 (nullable) |

### Acceptance Criteria

#### 정상 케이스
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-1 | 유효한 인증 정보 | GET /api/v1/users/me | 200, 본인 정보 반환 |

#### 실패 케이스 - 인증
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-2 | X-Login-Id 헤더 누락 | GET /api/v1/users/me | 401, "인증 헤더가 필요합니다" |
| AC-3 | X-Login-Pw 헤더 누락 | GET /api/v1/users/me | 401, "인증 헤더가 필요합니다" |
| AC-4 | 존재하지 않는 loginId | GET /api/v1/users/me | 404, "회원을 찾을 수 없습니다" |
| AC-5 | 비밀번호 불일치 | GET /api/v1/users/me | 401, "비밀번호가 일치하지 않습니다" |
| AC-6 | 탈퇴한 회원 | GET /api/v1/users/me | 404, "회원을 찾을 수 없습니다" |

### 비즈니스 규칙
| 규칙# | 내용 | 구현 위치 |
|-------|------|----------|
| BR-1 | 본인 정보만 조회 가능 | Controller (헤더의 loginId로 조회) |
| BR-2 | 탈퇴 회원은 조회 불가 | Repository (deletedAt IS NULL 조건) |
| BR-3 | 비밀번호 검증은 BCrypt.matches() 사용 | Service |

### 결정 사항
| 질문 | 결정 | 이유 |
|------|------|------|
| 인증 방식? | 헤더 기반 (X-Login-Id, X-Login-Pw) | JWT 미도입 상태, MVP용 단순 인증 |
| 비밀번호 평문 전송? | Yes (HTTPS 전제) | MVP 단계, 추후 JWT로 전환 |

---

## Feature 3: 비밀번호 변경

### 개요
| 항목 | 내용 |
|------|------|
| 목적 | 회원이 자신의 비밀번호를 변경한다 |
| Actor | 회원 |
| 우선순위 | P1 |
| 선행 조건 | Feature 1(회원가입) 완료 |
| 후행 영향 | 이후 인증 시 새 비밀번호 사용 |

### API 명세
| 항목 | 내용 |
|------|------|
| Method | PATCH |
| Endpoint | /api/v1/users/me/password |
| Auth | 회원 (헤더 인증) |

#### Request Header
| 헤더 | 필수 | 설명 |
|------|------|------|
| X-Login-Id | Y | 로그인 ID |
| X-Login-Pw | Y | 현재 비밀번호 |

#### Request Body
| 필드 | 타입 | 필수 | 검증 규칙 | 예시 |
|------|------|------|----------|------|
| newPassword | String | Y | 8-20자, 영문+숫자+특수문자 | "NewPass1234!" |

#### Response Body (200 OK)
빈 응답 (성공 여부만 상태코드로 판단)

### Acceptance Criteria

#### 정상 케이스
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-1 | 유효한 인증 + 유효한 새 비밀번호 | PATCH /api/v1/users/me/password | 200, 비밀번호 변경됨 |

#### 실패 케이스 - 입력값 검증
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-2 | newPassword 7자 | PATCH /api/v1/users/me/password | 400 |
| AC-3 | newPassword 특수문자 없음 | PATCH /api/v1/users/me/password | 400 |

#### 실패 케이스 - 인증
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-4 | 인증 헤더 누락 | PATCH /api/v1/users/me/password | 401 |
| AC-5 | 현재 비밀번호 불일치 | PATCH /api/v1/users/me/password | 401 |

#### 실패 케이스 - 비즈니스 규칙
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-6 | 새 비밀번호 = 현재 비밀번호 | PATCH /api/v1/users/me/password | 400, "현재 비밀번호와 다른 비밀번호를 입력해주세요" |

### 비즈니스 규칙
| 규칙# | 내용 | 구현 위치 |
|-------|------|----------|
| BR-1 | 새 비밀번호는 현재 비밀번호와 달라야 함 | Service |
| BR-2 | 새 비밀번호도 BCrypt로 암호화 | Service |

### 결정 사항
| 질문 | 결정 | 이유 |
|------|------|------|
| 이전 비밀번호 재사용 금지? | No | MVP 범위 외, 히스토리 관리 필요 |
| 비밀번호 변경 후 재로그인 강제? | No | 현재 세션/토큰 없음 |

---

## 용어 정의 (Glossary)

| 용어 | 정의 |
|------|------|
| 회원 | 서비스에 가입한 사용자 |
| 비회원 | 가입하지 않은 방문자 |
| Soft Delete | deletedAt 필드로 논리 삭제, 실제 데이터는 유지 |
| 마스킹 | 개인정보 보호를 위해 일부 문자를 *로 대체 |

---

## 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v1.0 | 2024-01-15 | AI | 최초 작성 |
