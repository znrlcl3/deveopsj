# Keycloak SSO 설정

DeveopsJ는 기본 로컬 로그인을 유지하며 `keycloak` 프로필을 활성화했을 때만
OpenID Connect 로그인을 제공한다. Keycloak 사용자는 ID Token의 `iss`와 `sub`로
식별하고 애플리케이션 권한은 `members.role`의 `USER` 또는 `ADMIN`을 사용한다.

## Keycloak 클라이언트

Realm에 OpenID Connect confidential client를 생성한다.

- Client ID: `deveopsj`
- Standard flow: 활성화
- Valid redirect URI: `https://서비스주소/login/oauth2/code/keycloak`
- Valid post logout redirect URI: `https://서비스주소/member/login*`
- Web origin: 실제 서비스 origin만 등록
- Scope: `openid`, `profile`, `email`

`preferred_username`, `name`, `sub`, `iss`가 ID Token 또는 UserInfo 응답에 포함되어야
한다. 운영 환경에서 `*` redirect URI나 web origin은 사용하지 않는다.

## 로컬 Keycloak 실행

Docker Desktop을 설치하고 Docker Engine을 실행한 다음 예제 환경파일을 복사한다.

```text
cd deploy/keycloak
copy .env.example .env
```

`.env`의 관리자 비밀번호, client secret, 테스트 사용자 비밀번호를 임의의 강한 값으로
변경한다. `.env`는 Git에서 제외된다. 이후 Keycloak을 시작한다.

```text
docker compose up -d
docker compose logs -f keycloak
```

Keycloak은 `http://localhost:8180`에서 실행된다. 시작 시
`deveopsj-realm.json`을 import하여 다음 항목을 만든다.

- Realm: `deveopsj`
- OIDC confidential client: `deveopsj`
- 테스트 사용자: `sso-user`
- Redirect URI: `http://localhost:8080/login/oauth2/code/keycloak`

Realm import는 기존 Realm이 있으면 건너뛴다. JSON을 변경한 뒤 다시 검증하려면 기존
Realm을 Admin Console에서 삭제하고 컨테이너를 다시 시작한다. 볼륨 삭제는 모든 로컬
Keycloak 데이터가 사라지는 작업이므로 필요한 데이터가 없는지 확인한 뒤 수행한다.

## 애플리케이션 설정

먼저 `deploy/sql/20260818_add_member_oidc_identity.sql`을 운영 DB에 적용한다. 이후
환경변수를 설정한다.

```text
SPRING_PROFILES_ACTIVE=prod,keycloak
KEYCLOAK_ISSUER_URI=https://sso.example.com/realms/deveopsj
KEYCLOAK_CLIENT_ID=deveopsj
KEYCLOAK_CLIENT_SECRET=실제_클라이언트_시크릿
```

로컬 DeveopsJ 실행 값은 다음과 같다. `KEYCLOAK_CLIENT_SECRET`은
`deploy/keycloak/.env`에 설정한 값과 반드시 같아야 한다.

```text
SPRING_PROFILES_ACTIVE=keycloak
KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/deveopsj
KEYCLOAK_CLIENT_ID=deveopsj
KEYCLOAK_CLIENT_SECRET=로컬_compose와_같은_값
```

로컬 개발에서는 `keycloak` 프로필과 동일한 세 환경변수를 사용한다. 시크릿은
저장소의 설정 파일에 기록하지 않는다.

## 계정 연결 정책

- 처음 로그인한 Keycloak 사용자는 로컬 `USER` 회원으로 생성된다.
- 동일한 `iss + sub`로 다시 로그인하면 기존 회원을 재사용한다.
- 같은 `login_id`의 로컬 회원이 이미 있으면 자동으로 연결하지 않고 로그인을
  거부한다. 이는 동일 아이디를 이용한 계정 탈취를 방지하기 위한 정책이다.
- 관리자 권한은 Keycloak role에서 자동 승격하지 않는다. 운영자가 로컬 회원을
  확인한 뒤 `members.role`을 변경한다.
- SSO 회원의 비밀번호는 Keycloak에서 관리하며 로컬 비밀번호 변경과 직접 탈퇴는
  허용하지 않는다.

## 확인 항목

1. `/member/login`에서 Keycloak SSO 버튼이 표시되는지 확인한다.
2. 로그인 후 `members.oidc_issuer`, `members.oidc_subject`가 저장되는지 확인한다.
3. 로그아웃 시 애플리케이션과 Keycloak 세션이 함께 종료되는지 확인한다.
4. 비활성화한 로컬 회원이 같은 Keycloak 계정으로 로그인할 수 없는지 확인한다.
