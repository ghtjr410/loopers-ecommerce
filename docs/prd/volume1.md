# PLAN: 회원 서비스

> 작성일: 2026-02-06
> 버전: v1.0

---

## 프로젝트 컨텍스트

> `.claude/rules/core/` 참조 결과

| 항목 | 프로젝트 표준 |
|------|--------------|
| HTTP 상태코드 | 성공: 200, 실패: 400/401/404/409 |
| 에러 응답 | `ApiResponse.fail(ErrorType.XXX)` |
| 검증 위치 | 형식→Request DTO (Bean Validation), 불변식→Domain Entity |
| 중복 체크 | Service에서 exists 쿼리로 명시적 수행 |
| 비밀번호 암호화 | BCrypt (도메인 인터페이스 PasswordEncoder) |
| 인증 헤더 | `X-Loopers-LoginId`, `X-Loopers-LoginPw` |
| Soft Delete | deletedAt 필드 사용, hard delete 금지 |

---

## Feature 1: 회원가입

### 개요
| 항목 | 내용 |
|------|------|
| 목적 | 신규 사용자가 서비스에 가입한다 |
| Actor | 비회원 |
| 우선순위 | P0 |
| 선행 조건 | 없음 |
| 후행 영향 | Feature 2(내 정보 조회), Feature 3(비밀번호 수정)에서 회원 정보 참조 |

### API 명세
| 항목 | 내용 |
|------|------|
| Method | POST |
| Endpoint | /api/v1/users |
| Auth | 불필요 |

#### Request Body
| 필드 | 타입 | 필수 | 검증 규칙 | 예시 |
|------|------|------|----------|------|
| loginId | String | Y | 영문과 숫자만 허용 (`^[a-zA-Z0-9]+$`) | "john123" |
| password | String | Y | 8~16자, 영문 대소문자/숫자/특수문자만 가능, 생년월일 포함 불가 | "Pass1234!" |
| name | String | Y | 한글 또는 영문 | "홍길동" |
| birthDate | LocalDate | Y | 과거 날짜만 허용 | "1995-03-15" |
| email | String | Y | 이메일 형식 | "john@test.com" |

#### Response Body (200 OK)
| 필드 | 타입 | 설명 |
|------|------|------|
| loginId | String | 로그인 ID |
| name | String | 이름 (마스킹: 홍길동→홍길*, 이→*) |
| birthDate | LocalDate | 생년월일 |
| email | String | 이메일 |

### Acceptance Criteria

#### 정상 케이스
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-1 | 모든 필수값 유효 | POST /api/v1/users | 200, 회원 생성, 비밀번호 BCrypt 암호화 저장 |
| AC-2 | 모든 필수값 유효 | POST /api/v1/users | 200, 이름 마스킹 처리된 응답 반환 |

#### 실패 케이스 - 입력값 검증 (Request DTO)
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-3 | loginId 누락 (null/blank) | POST /api/v1/users | 400 |
| AC-4 | password 누락 (null/blank) | POST /api/v1/users | 400 |
| AC-5 | name 누락 (null/blank) | POST /api/v1/users | 400 |
| AC-6 | birthDate 누락 (null) | POST /api/v1/users | 400 |
| AC-7 | email 누락 (null/blank) | POST /api/v1/users | 400 |

#### 실패 케이스 - 도메인 검증 (Entity)
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-8 | loginId에 특수문자 포함 | POST /api/v1/users | 400, "로그인 ID는 영문과 숫자만 허용합니다" |
| AC-9 | password 7자 (최소 미달) | POST /api/v1/users | 400, "비밀번호는 8~16자여야 합니다" |
| AC-10 | password 17자 (최대 초과) | POST /api/v1/users | 400, "비밀번호는 8~16자여야 합니다" |
| AC-11 | password에 허용되지 않는 문자 (한글 등) | POST /api/v1/users | 400, "비밀번호는 영문 대소문자, 숫자, 특수문자만 사용 가능합니다" |
| AC-12 | password에 생년월일 포함 (yyyyMMdd) | POST /api/v1/users | 400, "비밀번호에 생년월일을 포함할 수 없습니다" |
| AC-13 | password에 생년월일 포함 (yyMMdd) | POST /api/v1/users | 400, "비밀번호에 생년월일을 포함할 수 없습니다" |
| AC-14 | password에 생년월일 포함 (MMdd) | POST /api/v1/users | 400, "비밀번호에 생년월일을 포함할 수 없습니다" |
| AC-15 | birthDate 미래 날짜 | POST /api/v1/users | 400, "생년월일은 과거 날짜여야 합니다" |
| AC-16 | email 형식 오류 (@ 없음) | POST /api/v1/users | 400, "올바른 이메일 형식이 아닙니다" |

#### 실패 케이스 - 비즈니스 규칙
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-17 | loginId 중복 (이미 존재하는 ID) | POST /api/v1/users | 409, "이미 사용 중인 로그인 ID입니다" |

### 비즈니스 규칙
| 규칙# | 내용 | 구현 위치 |
|-------|------|----------|
| BR-1 | 비밀번호는 BCrypt로 암호화 저장 | Service (PasswordEncoder.encode) |
| BR-2 | loginId는 생성 후 변경 불가 | 전체 (수정 API 미제공) |
| BR-3 | 이름은 응답 시 마지막 글자 마스킹 (홍길동→홍길*, 이→*) | UserInfo 변환 시 |
| BR-4 | 비밀번호에 생년월일 포함 불가 (yyyyMMdd, yyMMdd, MMdd 형식) | Domain (PasswordValidator) |
| BR-5 | 이메일 중복 체크 안함 (loginId만 unique) | Service |

### 결정 사항
| 질문 | 결정 | 이유 |
|------|------|------|
| 생년월일 필수 여부? | 필수 (Y) | 비밀번호 검증에 생년월일 필요, 사용자 확인 |
| 비밀번호 길이? | 8~16자 | 요구사항 원문 준수 |
| 이메일 중복 체크? | 안함 | 요구사항에 loginId 중복만 명시 |
| 1글자 이름 마스킹? | 전체 마스킹 (*) | 마지막 글자 마스킹 규칙의 자연스러운 적용 |
| 응답에 id(PK) 포함? | No | 보안상 내부 ID 노출 지양 |

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
| X-Loopers-LoginId | Y | 로그인 ID |
| X-Loopers-LoginPw | Y | 비밀번호 (평문) |

#### Response Body (200 OK)
| 필드 | 타입 | 설명 |
|------|------|------|
| loginId | String | 로그인 ID |
| name | String | 이름 (마스킹: 마지막 글자 *) |
| birthDate | LocalDate | 생년월일 |
| email | String | 이메일 |

### Acceptance Criteria

#### 정상 케이스
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-1 | 유효한 인증 정보 | GET /api/v1/users/me | 200, 본인 정보 반환 (이름 마스킹) |

#### 실패 케이스 - 인증
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-2 | X-Loopers-LoginId 헤더 누락 | GET /api/v1/users/me | 401, "인증 헤더가 필요합니다" |
| AC-3 | X-Loopers-LoginPw 헤더 누락 | GET /api/v1/users/me | 401, "인증 헤더가 필요합니다" |
| AC-4 | 존재하지 않는 loginId | GET /api/v1/users/me | 404, "회원을 찾을 수 없습니다" |
| AC-5 | 비밀번호 불일치 | GET /api/v1/users/me | 401, "비밀번호가 일치하지 않습니다" |

### 비즈니스 규칙
| 규칙# | 내용 | 구현 위치 |
|-------|------|----------|
| BR-1 | 로그인 ID는 영문과 숫자만 허용 | Domain Entity (회원가입 시 검증) |
| BR-2 | 본인 정보만 조회 가능 (헤더의 loginId로 조회) | Controller |
| BR-3 | 비밀번호 검증은 BCrypt.matches() 사용 | Service |
| BR-4 | 이름은 마지막 글자 마스킹 처리 | UserInfo 변환 시 |

### 결정 사항
| 질문 | 결정 | 이유 |
|------|------|------|
| 인증 방식? | 헤더 기반 (X-Loopers-LoginId, X-Loopers-LoginPw) | JWT 미도입 상태, MVP용 단순 인증 |
| 비밀번호 평문 전송? | Yes (HTTPS 전제) | MVP 단계, 추후 JWT로 전환 |

---

## Feature 3: 비밀번호 수정

### 개요
| 항목 | 내용 |
|------|------|
| 목적 | 회원이 자신의 비밀번호를 수정한다 |
| Actor | 회원 |
| 우선순위 | P1 |
| 선행 조건 | Feature 1(회원가입) 완료 |
| 후행 영향 | 이후 인증 시 새 비밀번호 사용해야 함 |

### API 명세
| 항목 | 내용 |
|------|------|
| Method | PATCH |
| Endpoint | /api/v1/users/me/password |
| Auth | 회원 (헤더 인증) |

#### Request Header
| 헤더 | 필수 | 설명 |
|------|------|------|
| X-Loopers-LoginId | Y | 로그인 ID |
| X-Loopers-LoginPw | Y | 현재 비밀번호 |

#### Request Body
| 필드 | 타입 | 필수 | 검증 규칙 | 예시 |
|------|------|------|----------|------|
| newPassword | String | Y | 8~16자, 영문 대소문자/숫자/특수문자만 가능, 생년월일 포함 불가 | "NewPass1234!" |

#### Response Body (200 OK)
빈 응답 (성공 여부만 상태코드로 판단, data: null)

### Acceptance Criteria

#### 정상 케이스
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-1 | 유효한 인증 + 유효한 새 비밀번호 | PATCH /api/v1/users/me/password | 200, 비밀번호 변경됨 (BCrypt 암호화 저장) |
| AC-2 | 변경 후 새 비밀번호로 인증 | GET /api/v1/users/me (새 비밀번호 헤더) | 200, 정상 조회 |

#### 실패 케이스 - 입력값 검증 (Request DTO)
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-3 | newPassword 누락 (null/blank) | PATCH /api/v1/users/me/password | 400 |

#### 실패 케이스 - 도메인 검증 (Entity)
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-4 | newPassword 7자 (최소 미달) | PATCH /api/v1/users/me/password | 400, "비밀번호는 8~16자여야 합니다" |
| AC-5 | newPassword 17자 (최대 초과) | PATCH /api/v1/users/me/password | 400, "비밀번호는 8~16자여야 합니다" |
| AC-6 | newPassword 특수문자만 없음 | PATCH /api/v1/users/me/password | 400 |
| AC-7 | newPassword에 생년월일 포함 | PATCH /api/v1/users/me/password | 400, "비밀번호에 생년월일을 포함할 수 없습니다" |

#### 실패 케이스 - 인증
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-8 | X-Loopers-LoginId 헤더 누락 | PATCH /api/v1/users/me/password | 401, "인증 헤더가 필요합니다" |
| AC-9 | X-Loopers-LoginPw 헤더 누락 | PATCH /api/v1/users/me/password | 401, "인증 헤더가 필요합니다" |
| AC-10 | 현재 비밀번호 불일치 | PATCH /api/v1/users/me/password | 401, "비밀번호가 일치하지 않습니다" |

#### 실패 케이스 - 비즈니스 규칙
| AC# | 조건 | 행위 | 기대 결과 |
|-----|------|------|----------|
| AC-11 | 새 비밀번호 = 현재 비밀번호 | PATCH /api/v1/users/me/password | 400, "현재 비밀번호와 다른 비밀번호를 입력해주세요" |

### 비즈니스 규칙
| 규칙# | 내용 | 구현 위치 |
|-------|------|----------|
| BR-1 | 새 비밀번호는 현재 비밀번호와 달라야 함 | Service (encoder.matches 비교) |
| BR-2 | 새 비밀번호도 BCrypt로 암호화 저장 | Service |
| BR-3 | 새 비밀번호도 동일한 비밀번호 RULE 적용 (8~16자, 영문/숫자/특수문자, 생년월일 불포함) | Domain (PasswordValidator) |

### 결정 사항
| 질문 | 결정 | 이유 |
|------|------|------|
| 이전 비밀번호 재사용 금지? | No | 요구사항에 "현재 비밀번호는 사용할 수 없습니다"만 명시, 히스토리 관리 불필요 |
| 비밀번호 변경 후 재로그인 강제? | No | 현재 세션/토큰 없음 |

---

## 용어 정의 (Glossary)

| 용어 | 정의 |
|------|------|
| 회원 | 서비스에 가입한 사용자 |
| 비회원 | 가입하지 않은 방문자 |
| Soft Delete | deletedAt 필드로 논리 삭제, 실제 데이터는 유지 |
| 마스킹 | 개인정보 보호를 위해 마지막 글자를 `*`로 대체 |
| 인증 헤더 | X-Loopers-LoginId, X-Loopers-LoginPw를 통한 요청별 인증 방식 |

---

## 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v1.0 | 2026-02-06 | AI | 최초 작성 |
