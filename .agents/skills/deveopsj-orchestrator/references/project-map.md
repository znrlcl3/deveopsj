# 프로젝트 지도와 재설계 근거

2026-11-20 작업 트리 기준으로 갱신했다. 아래는 구조와 합의한 방향의 스냅샷이며 이후 작업에서는 현재 코드를 다시 확인한다.

경로 표기: pom.xml, src/, deploy/, docs/, .agents/로 시작하면 저장소 루트 기준이다. Java 패키지 경로(assetplan/, market/, common/ 등)는 src/main/java/com/deveopsj/ 아래이며 클래스명의 .java는 생략한다. templates/는 src/main/resources/ 아래다. 같은 문장에 클래스명만 나열하면 바로 앞 패키지를 따른다.

## 확인한 구조
- pom.xml: Java 17, Spring Boot 4.0.5, MVC/Thymeleaf/Security/OAuth2 Client, JPA/MySQL, Apache POI 5.4.1. H2 테스트 의존성과 src/test/resources/application-test.yml이 있다. Flyway/Liquibase 도입 여부는 스키마 변경 시 다시 확인한다.
- 금융: src/main/java/com/deveopsj/assetplan, income, spending, cashflow, dashboard, common. 매매·평가·반복 납입/지출과 월별 현금흐름을 포함한다.
- 파일: common/excel/FinancialExcelExportService, spending/service/SpendingExcelImportService, CardSpendingImportService, 카드사별 CardStatementParser 구현.
- AI: ai/service/AiService, SpendingAnalysisService와 spending/service/SpendingCategoryPredictionService. common/service/DataInputService와 카드 확정이 분류 결과를 소비한다.
- 시장: market/service의 KisAccessTokenService, KisMarketPriceService, KisExchangeRateService, KrxStockService, NasdaqDirectoryService, InvestmentAssetSyncService.
- 개인 증권 연결: market/namuh의 NamuhApiClient, NamuhService, NamuhConnectionRepository, NamuhSecretCipher. Market은 외부 계약, Web은 회원·계좌·자격증명 격리, Operations는 암호화키 설정, Domain은 가져오기 저장을 담당한다. 기능 재노출은 별도 요청 범위다.
- 포트폴리오: market/service/PortfolioValuationService와 market/dto, templates/portfolio. 평가 계산은 Domain, 외부 quote는 Market, 표시 계약은 Web 담당이다.
- 인증: member/security의 SecurityConfig, KeycloakOidcUserService, MemberPrincipal, ImpersonationAuditListener와 member/resolver.
- 인가: common/authorization/PolicyAuthorizationService, spending/service/SpendingService, deploy/opa/policies와 docs/opa-authorization.md.
- 운영: deploy/README.md, deploy/systemd, deploy/nginx, deploy/keycloak, deploy/sql, docs/keycloak-sso.md, docs/secret-setup.md.
- 검증: src/test/java/com/deveopsj의 도메인·Controller·Security·시장·AI·운영 설정 테스트. OPA 정책 테스트는 deploy/opa/policies/deveopsj/authz_test.rego.

## 기존 하네스에서 바꾼 결정
| 관찰 | 결정 |
| --- | --- |
| 기존 5개 역할은 Domain/Web/AI 중심이며 시장 데이터와 운영 담당이 명시되지 않음 | 기존 역할 유지, market-integration-expert와 operations-expert 추가 |
| income/cashflow/매매/포트폴리오와 카드 가져오기 안내가 부족 | Domain 범위 확대, 파일 처리 별도 에이전트 대신 기존 역할 간 계약 명시 |
| Web 지침이 로컬 로그인·기본 CSRF 중심 | Keycloak/OIDC, 관리자 전환, OPA와 세션 미리보기 반영 |
| 리드 문서의 무조건 병렬화와 오케스트레이터의 조건부 실행이 충돌 | 기본 순차, 명시적으로 요청/요구된 위임만 사용 |
| 모든 작업에서 전체 Maven 테스트를 먼저 실행 | 관련 테스트 우선, 코드/설정 전체 회귀, 문서 전용 검증 구분 |
| 역할 파일을 자동 실행 에이전트로 오인할 여지 | 역할은 읽는 지침임을 명시, 실제 위임 시 입출력·파일 소유권 정의 |

## 현재 서비스 방향과 주요 소비자
- 기본은 가계부의 수입·지출, 목표 → 자산플랜, 시작 원금과 실제 납입·인출 관리다. 증권 잔고·매매·평가 기능은 기존 기능으로 구분하고 요청 없이 기본 흐름으로 다시 확대하지 않는다.
- 내 자산: assetplan/service/HouseholdLedgerService와 assetplan/entity/AssetPlan의 openingPrincipal/openingDate. 시작 원금의 집계 기준일과 이후 납입을 구분한다. 기준일 이전·당일 내역과 월별 일괄 확정의 경계는 변경 시 함께 확인한다.
- 목표와 대시보드의 모은 금액은 원금 기준 흐름을 확인한다. 평가액·수익 재투자를 외부 순납입과 혼합하지 않고 기준 변경은 관련 소비자까지 추적한다.
- dashboard/service/DashboardService와 cashflow/service/MonthlyCashFlowService는 생활비 수입/투자수익, 실제/미래 지출을 구분한다. 같은 회원·기간·기준일을 사용해 합계를 대조한다. 월급 예상치는 실제 입금과 별도이며 추정 기준도 함께 확인한다.
- dashboard/service/DashboardChecklistService는 미확정 지출과 납입 확인 항목을 조회한다. 조회가 확정·입금 기록을 생성하지 않아야 한다.
- 화면 이동은 내 자산의 저축 현황·납입 내역·추가 납입·정기 설정 등 현재 연결된 메뉴와 공통 fragment를 기준으로 확인한다. 과거 증권 메뉴와 혼동하지 않는다.

## 구현 사실과 검증 목표의 구분
- AiService는 호출 시 RestTemplate을 직접 생성하며 해당 메서드에는 명시적 timeout 설정이 보이지 않았다. timeout은 향후 관련 변경에서 확인할 목표이며 이번에 고친 기능이 아니다.
- PolicyAuthorizationService는 활성화 상태에서 true만 허용하고 HTTP 오류를 접근 거부로 처리한다. 비활성 상태의 우회 반환은 별도 프로필 계약이다.
- KisExchangeRateService는 정상/캐시/stale 상태를 반환하고 PortfolioValuationService는 실패 시 거래 환율을 대체값으로 쓰는 경로가 있다. UI와 합계 계약까지 검증해야 한다.
- CardSpendingImportService는 크기·형식·행 수·암호화 파일 검사, 미리보기/선택 확정, 사용자별 중복 확인을 갖는다. 존재 여부만으로 동시성·세션 안전성이 검증됐다고 단정하지 않는다.
- AiDashboardApplicationTests는 SpringBootTest이다. 격리된 test 프로필의 H2 사용 여부를 확인하며 문서에 적힌 과거 테스트 결과를 현재 통과 근거로 쓰지 않는다.
- budget/tag/AIPrediction 등은 파일 존재만으로 완성된 기능이라고 가정하지 않는다.

## 관리 범위
재설계는 AGENTS.md와 .agents 아래 지침만 대상으로 한다. 애플리케이션, 기존 SQL, 사용자 미커밋 변경은 수정하지 않는다.
현재 AGENTS.md와 .agents는 Git 추적 대상이다. .gitignore의 /src/test/java/ 규칙에도 기존 추적 테스트는 남아 있고 신규 테스트는 제외될 수 있다. 추적 상태를 실제로 확인하며 사용자 요청 없이 제외 정책을 바꾸지 않는다.
