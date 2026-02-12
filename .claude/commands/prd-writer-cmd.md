# PRD + AC 작성

$ARGUMENTS 경로의 요구사항 파일을 분석하여 PRD + Acceptance Criteria를 작성합니다.

## 사용법
```
/prd-writer mission/round2.md
```

## 실행 절차

### 1단계: 스킬 로드
```
Skill tool 호출: skill="prd-writer"
```
- PRD 작성 가이드라인과 템플릿을 로드합니다.

### 2단계: 요구사항 파일 읽기
- 지정된 파일($ARGUMENTS)을 읽습니다.
- 파일이 없으면 사용자에게 확인합니다.

### 3단계: 브레인스토밍 진행
스킬의 워크플로우를 따릅니다:

1. **CAPTURE**: Feature 목록 추출
2. **CLARIFY**: 모호한 부분 AskUserQuestion으로 확인
3. **STRUCTURE**: PRD + AC 형식으로 구조화
4. **VALIDATE**: 완전성 체크

### 4단계: 결과물 저장
- `.claude/PLAN.md`에 저장
- 사용자에게 요약 제시

## 출력물
- `.claude/PLAN.md`: 완성된 PRD + AC 문서

## 참조
- 템플릿: `.claude/skills/prd-writer/templates/prd-template.md`
- 예시: `.claude/skills/prd-writer/examples/user-feature.md`
