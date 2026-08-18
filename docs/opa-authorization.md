# OPA/Rego 정책 기반 인가

## 목적

DeveopsJ의 금융 데이터 소유권 규칙을 애플리케이션 코드에만 두지 않고 OPA가
평가할 수 있는 정책으로 분리한다. 첫 적용 대상은 지출 수정과 삭제이다.

## 구성 요소

- PEP(Policy Enforcement Point): `SpendingService`
- PDP(Policy Decision Point): OPA Data API
- Policy: `deploy/opa/policies/deveopsj/authz.rego`
- Java 정책 클라이언트: `PolicyAuthorizationService`

애플리케이션은 다음 입력을 OPA에 전달한다.

```json
{
  "input": {
    "subject": {
      "member_id": 7,
      "role": "USER"
    },
    "action": "update",
    "resource": {
      "type": "spending",
      "id": 10,
      "owner_id": 7
    }
  }
}
```

OPA는 `POST /v1/data/deveopsj/authz/allow` 요청에 다음과 같이 응답한다.

```json
{
  "result": true,
  "decision_id": "optional-correlation-id"
}
```

`result`가 명시적으로 `true`인 경우에만 허용한다. false, 누락, HTTP 오류, timeout은
모두 거부한다.

## 이중 방어

OPA 도입 후에도 Repository의 `findByIdAndMemberMemberId` 소유권 조건을 유지한다.

1. Repository가 타 사용자의 금융 데이터를 조회하지 않는다.
2. 조회된 자원에 대해 OPA가 행동을 허용해야 한다.
3. 두 검사를 모두 통과해야 수정하거나 삭제한다.

OPA 장애 시 금융정보 변경을 허용하면 정책 서버 장애가 권한 우회로 이어지므로,
OPA 기능을 활성화한 환경에서는 fail-closed로 동작한다. 반면 `opa` 프로필이 꺼진
기본 환경은 기존 소유권 검사만 사용해 로컬 개발과 단계적 도입을 지원한다.

## 설정

OPA 연동을 활성화할 때 `opa` 프로필을 추가한다.

```text
SPRING_PROFILES_ACTIVE=prod,keycloak,opa
OPA_BASE_URL=http://127.0.0.1:8181
OPA_TIMEOUT=500ms
```

OPA는 애플리케이션과 같은 호스트 또는 sidecar처럼 가까운 위치에 두는 것을
전제로 짧은 timeout을 사용한다. 운영 환경에서는 OPA API를 외부 인터넷에 공개하지
않는다.

## 정책 테스트

OPA 실행 환경에서 다음 명령으로 Rego 정책을 검증한다.

```text
opa test deploy/opa/policies -v
```

현재 정책 테스트는 다음 사례를 정의한다.

- 본인 지출 수정 허용
- 타인 지출 삭제 거부
- 정의되지 않은 행동 거부

## 면접 설명 포인트

> Keycloak이 인증한 사용자 정보를 subject로 사용하고, 자원의 소유자와 요청 행동을
> 구조화된 input으로 만들어 OPA Data API에 전달했습니다. SpendingService를 PEP로,
> OPA를 PDP로 분리했으며, 기존 Repository 소유권 조건도 유지해 심층 방어를
> 적용했습니다. OPA가 활성화된 환경에서는 timeout이나 비정상 응답도 거부하는
> fail-closed 전략을 사용했습니다.
