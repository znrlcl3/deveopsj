---
name: web-security-expert
description: DeveopsJ Controller·Thymeleaf·JavaScript, 업로드 세션, 로그인·Keycloak OIDC·관리자 전환, Spring Security·CSRF 및 OPA/Rego 인가 변경과 분석에 사용한다.
---

# 웹·인증·인가
1. endpoint method, DTO/검증, 응답/view/model, redirect/flash와 JS 소비자를 함께 추적한다. 화면이 소비하는 누락·오류·대체 데이터 상태도 계약에 포함한다.
2. MemberArgumentResolver와 MemberPrincipal에서 서비스 소유권 조건까지 확인한다. 조회·다운로드·수정·삭제·연관 ID 모두 사용자 격리를 검증한다.
3. 로컬 로그인과 Keycloak 로그인을 구분한다. OIDC issuer/subject와 기존 계정 연결, 비활성 회원, 역할 매핑·갱신, 로그인/로그아웃을 확인한다. 이메일 등 사용자 속성만으로 계정을 연결하는 변경은 계정 탈취 가능성을 검증한다.
4. 관리자 대리 로그인은 진입·종료 권한, 중첩 전환 차단, 원 관리자 복원, 감사 이벤트와 세션 저장을 함께 확인한다.
5. OPA 관련 변경은 docs/opa-authorization.md, common/authorization, 호출 서비스와 deploy/opa/policies를 함께 읽는다. 활성화 시 명시적 true만 허용하고 timeout·누락·오류는 거부한다. OPA가 Repository 소유권 검사를 대체하지 않는다. 비활성 프로필과 장애를 구분한다.
6. 상태 변경은 적절한 HTTP method와 CSRF를 유지한다. DTO 바인딩, Thymeleaf escaping, 오류 메시지와 파일명 노출을 확인한다.
7. 업로드 미리보기는 인증 세션의 저장·만료·소비, 재전송, 선택 인덱스/카테고리 변조를 검증한다. 파일 비밀번호를 세션·로그·오류에 잔류시키지 않는다.
8. MockMvc로 변경 경로의 정상·익명·타인·관리자 권한·CSRF 사례를 골라 검증한다. 템플릿 표현식과 JS 계약은 필요하면 렌더링/브라우저로 확인하며 Controller 테스트만으로 화면 확인 완료라고 하지 않는다.

운영 인증을 테스트 편의로 완화하지 않는다. 프로필 변경은 operations-expert와, 데이터 조건은 domain-data-expert와 계약을 맞춘다.
