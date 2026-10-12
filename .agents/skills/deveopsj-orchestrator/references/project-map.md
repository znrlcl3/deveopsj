# 프로젝트 지도와 재설계 근거

2026-10-12 작업 트리 기준이다. 커밋되지 않은 카드 가져오기·환율·OIDC 변경도 포함한다. 이후 작업에서는 현재 코드를 다시 확인한다. 아래 경로는 저장소 루트 기준이다.

## 확인한 구조
- pom.xml: Java 17, Spring Boot 4.0.5, MVC/Thymeleaf/Security/OAuth2 Client, JPA/MySQL, Apache POI 5.4.1. Flyway/Liquibase와 내장 테스트 DB 의존성은 현재 POM에 없다.
- 금융: src/main/java/com/deveopsj/assetplan, income, spending, cashflow, dashboard, common. 매매·평가·반복 납입/지출과 월별 현금흐름을 포함한다.
- 파일: common/excel/FinancialExcelExportService, spending/service/SpendingExcelImportService, CardSpendingImportService, 카드사별 CardStatementParser 구현.
- AI: ai/service/AiService, SpendingAnalysisService와 spending/service/SpendingCategoryPredictionService. common/service/DataInputService와 카드 확정이 분류 결과를 소비한다.
- 시장: market/service의 KisAccessTokenService, KisMarketPriceService, KisExchangeRateService, KrxStockService, NasdaqDirectoryService, InvestmentAssetSyncService.
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

## 구현 사실과 검증 목표의 구분
- AiService는 호출 시 RestTemplate을 직접 생성하며 해당 메서드에는 명시적 timeout 설정이 보이지 않았다. timeout은 향후 관련 변경에서 확인할 목표이며 이번에 고친 기능이 아니다.
- PolicyAuthorizationService는 활성화 상태에서 true만 허용하고 HTTP 오류를 접근 거부로 처리한다. 비활성 상태의 우회 반환은 별도 프로필 계약이다.
- KisExchangeRateService는 정상/캐시/stale 상태를 반환하고 PortfolioValuationService는 실패 시 거래 환율을 대체값으로 쓰는 경로가 있다. UI와 합계 계약까지 검증해야 한다.
- CardSpendingImportService는 크기·형식·행 수·암호화 파일 검사, 미리보기/선택 확정, 사용자별 중복 확인을 갖는다. 존재 여부만으로 동시성·세션 안전성이 검증됐다고 단정하지 않는다.
- AiDashboardApplicationTests는 SpringBootTest이다. DB가 없는 환경의 실행 가능성을 확인해야 하며 여기서 테스트 통과를 주장하지 않는다.
- budget/tag/AIPrediction 등은 파일 존재만으로 완성된 기능이라고 가정하지 않는다.

## 관리 범위
재설계는 AGENTS.md와 .agents 아래 지침만 대상으로 한다. 애플리케이션, 기존 SQL, 사용자 미커밋 변경은 수정하지 않는다.
현재 .gitignore는 AGENTS.md와 역할·스킬 경로를 무시한다. 이 설정은 자동 변경하지 않는다. Git으로 공유하려면 사용자가 추적 여부를 결정해야 한다.
