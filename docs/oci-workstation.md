# 회사 PC와 OCI 서버 연결

집·회사에서 로컬 코드를 실행하며 회사에서 입력한 데이터를 공유하려면
[회사 데이터 공유 개발](shared-data-development.md)을 먼저 따른다.
아래 앱 터널과 배포 절차는 클라우드에서 실행 중인 앱을 접속·업데이트하는 용도다.

이 문서는 서버 IP 129.225.200.188, SSH 사용자 ubuntu, SSH 포트 22,
서비스 deveopsj, JAR /opt/deveopsj/app.jar, 환경파일 /etc/deveopsj/deveopsj.env를
사용한다. 서버 정보는 전달받은 값이며 원격 접속으로 확인한 상태는 아니다.

## 지금 가능한 작업과 접속 준비

SSH 개인키가 회사 PC에 없으므로 여기서는 빌드·테스트·스크립트 준비가 가능하다.
집 PC에서 기존 deveopsj-server.pem과 oci-known-hosts를 안전한 방법으로 회사 PC의
저장소 밖 폴더에 옮긴다. 개인키를 대화나 Git에 넣지 않는다. 공개키만 복사해서는
기존 개인키의 역할을 대신할 수 없다.

기존 개인키가 집에도 없다면 새 키 생성만으로 접속할 수 없다. 서버에 새 공개키를
등록해야 하며, OCI 콘솔 접근 권한으로 복구 절차를 진행해야 한다.
[Oracle의 콘솔 연결·SSH 키 복구 안내](https://docs.oracle.com/en-us/iaas/Content/Compute/References/serialconsole.htm)를 따른다.
이 과정에서 기존 인스턴스나 금융 DB를 삭제하지 않는다.

개인키는 Windows OpenSSH가 읽을 수 있도록 현재 사용자에게만 읽기 권한을 준다.
집의 known-hosts 파일을 지정하면 스크립트는 등록된 서버 키와 일치할 때만 접속한다.
파일이 없을 때는 OpenSSH가 최초 서버 키 확인을 요청한다. 서버 지문은 집에서
이미 확인한 값이나 OCI 콘솔에서 확인한 값과 대조하며 검사를 끄지 않는다.

## 회사 PC 접속

프로젝트 루트에서 실행한다. 아래 키 경로는 사용자가 실제 저장한 위치로 바꾼다.

```powershell
$key = "C:\Users\KWIC\.ssh\deveopsj-server.pem"
$knownHosts = "C:\Users\KWIC\.ssh\oci-known-hosts"

# SSH 터널: 이 창을 열어 둔다.
.\deploy\scripts\connect-deveopsj.ps1 -IdentityFile $key -KnownHostsFile $knownHosts

# 별도 창에서 서버 셸 접속
.\deploy\scripts\connect-deveopsj.ps1 -IdentityFile $key -KnownHostsFile $knownHosts -Shell
```

터널은 회사 PC의 127.0.0.1:18080을 서버의 127.0.0.1:8080으로 전달한다.
http://localhost:18080/actuator/health로 연결을 확인한다.
이 주소의 응답은 서버 앱이며 회사 PC에서 실행한 앱이 아니다.

운영 프로필은 Secure 세션 쿠키를 사용한다. HTTP 터널에서 로그인/SSO가 동작한다고
가정하거나 운영 쿠키 보호를 끄지 않는다. 주소만 입력해 이용하는 최종 브라우저 접속은
아래 HTTPS 주소로 검증한다. SSO는 Keycloak의 issuer와 redirect URI도 별도로 맞춰야 한다.

## 로컬 빌드와 재배포

SSH 키 없이 먼저 빌드할 수 있다.

```powershell
.\deploy\scripts\build-deveopsj.ps1
```

스크립트는 test Spring 프로필로 전체 테스트를 실행한다. 테스트 전용 H2 DB를 쓰므로
회사·서버 MySQL이나 실제 API 키가 필요하지 않다. H2 검증이 운영 MySQL의 마이그레이션
검증을 대신하지는 않는다. 그 다음 clean 상태에서 cloud Maven 프로필로 JAR를 만들고,
JAR에 로컬 application.properties가 없고 application-prod.yml이 있는지 검사한다.
테스트가 실패하면 배포 단계로 진행하지 않는다.

SSH 키가 준비된 뒤 실제 재배포할 때만 실행한다.

```powershell
.\deploy\scripts\deploy-deveopsj.ps1 -IdentityFile $key -KnownHostsFile $knownHosts
```

다른 프로젝트 경로는 -SourcePath로 지정한다. 서버 주소·사용자·포트도 매개변수로 변경할 수 있다.
배포 스크립트는 전체 테스트와 빌드 후 JAR와 교체 스크립트를 서버 /tmp에 업로드한다.
서버에서 SHA256을 확인하고 이전 JAR를 app.jar.previous로 보존한 뒤 원자적으로 교체한다.
systemd 재시작과 /actuator/health 확인이 실패하면 이전 JAR로 복구를 시도하고 실패로 종료한다.
deveopsj 서비스 계정, 기존 JAR/환경파일, curl, flock과 ubuntu의 비대화형 sudo 권한이 필요하다.
DB 스키마와 환경파일은 자동 변경하지 않는다. 업로드 도중 실패하면 출력된 /tmp 경로를 확인한다.

## 서버 설정 확인과 API 키 입력

접속 후 먼저 현재 상태를 확인한다.

```bash
sudo systemctl status deveopsj --no-pager
sudo ss -lntp
curl --fail http://127.0.0.1:8080/actuator/health
sudoedit /etc/deveopsj/deveopsj.env
```

기존 DB_URL, DB_USERNAME, DB_PASSWORD와 기존 Keycloak/OPA 설정을 유지한다.
회사에서 보관한 API 키는 GEMINI_API_KEY, KRX_API_AUTH_KEY, KIS_API_APP_KEY,
KIS_API_APP_SECRET에 입력한다. 환경파일 전체를 출력하거나 Git에 복사하지 않는다.
PRIVACY_OPERATOR_NAME, PRIVACY_CONTACT_EMAIL도 실제 운영 정보로 설정한다.
앱의 내부 바인딩은 SERVER_ADDRESS=127.0.0.1로 두며 prod 프로필을 유지한다.

환경파일 변경 후 적용한다.

```bash
sudo systemctl restart deveopsj
curl --fail http://127.0.0.1:8080/actuator/health
```

운영 스키마는 validate이므로 deploy/sql의 반복 지출·반복 납입·OIDC·카드 가져오기 SQL이
서버 DB에 이미 적용됐는지 확인해야 한다. 적용되지 않은 파일은 기존 스키마와 백업을
확인한 뒤 필요한 SQL만 적용한다. 모든 SQL을 무조건 다시 실행하지 않는다.

## IP 기반 HTTPS 준비

최종 주소는 https://129.225.200.188/member/login이다.
OCI 보안 목록/NSG와 Ubuntu 방화벽에서 TCP 80(인증서 발급/갱신), 443(HTTPS)을
허용한다. SSH 22는 필요한 접속지로 제한하고 앱 8080과 MySQL은 외부에 공개하지 않는다.
현재 등록된 방화벽 규칙을 확인한 뒤 적용한다.

Nginx와 Certbot이 필요하다. IP webroot 지원은 Certbot 5.4 이상을 확인한다.
IP 인증서는 shortlived 프로필이며 약 6일 유효하므로 자동 갱신과 Nginx reload가 필요하다.
[Let's Encrypt 공식 IP 인증서 안내](https://letsencrypt.org/2026/03/11/shorter-certs-certbot)를 참고한다.

서버에 업로드할 준비 파일:

- deploy/nginx/deveopsj-ip-http.conf: 인증서 발급용 HTTP 설정, 금융 앱은 제공하지 않는다.
- deploy/nginx/deveopsj-ip-https.conf: 인증서가 발급된 후 사용하는 HTTPS 설정.
- deploy/scripts/reload-nginx.sh: 인증서 갱신 후 nginx -t를 통과하면 reload한다.

서버에서 /var/www/letsencrypt를 만들고 HTTP 설정을 Nginx에 설치한다.
sites-enabled의 기존 DeveopsJ 설정과 중복 listen/server_name이 생기지 않도록 기존 설정을
보존한 뒤 이 사이트의 설정만 교체한다. nginx -t가 통과한 후 reload한다.

```bash
sudo mkdir -p /var/www/letsencrypt
certbot --version
# 발급 경로 확인용 테스트 인증서
sudo certbot certonly --staging --webroot -w /var/www/letsencrypt \
  --preferred-profile shortlived --ip-address 129.225.200.188 \
  --cert-name deveopsj-ip-test

# 실제 인증서: 테스트 발급 성공 후 실행
sudo certbot certonly --webroot -w /var/www/letsencrypt \
  --preferred-profile shortlived --ip-address 129.225.200.188 \
  --cert-name deveopsj-ip
```

실제 인증서 발급 후 HTTPS 설정으로 교체하고 nginx -t / reload를 실행한다.
준비한 reload-nginx.sh를 /etc/letsencrypt/renewal-hooks/deploy/deveopsj-nginx로
설치하고 실행 권한을 부여한다. 설치 방식에 맞는 Certbot 자동 갱신 타이머가
실제로 활성화됐는지 확인한 뒤 갱신을 시험한다.

```bash
sudo certbot renew --dry-run
curl --fail https://129.225.200.188/health
```

로그인·로그아웃·파일 업로드까지 브라우저에서 확인한다. Keycloak을 사용하면
https://129.225.200.188/login/oauth2/code/keycloak, 로그아웃 redirect와 Web origin을
실제 주소에 맞춘다. KEYCLOAK_ISSUER_URI는 서버와 브라우저가 모두 접근할 수 있는
정확한 issuer를 유지해야 한다. Keycloak의 기존 issuer 변경은 기존 회원의 iss+sub 연결에
영향을 줄 수 있으므로 issuer를 임의로 바꾸지 않는다. 상세 설정은 keycloak-sso.md를 따른다.

SSH 키 확보, 실제 배포, 서버 DB/방화벽/HTTPS와 SSO 동작은 원격 접근 후 검증할 항목이다.

