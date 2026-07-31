# DeveopsJ 서버 실행 준비

OCI 계정 생성 전에도 저장소에서 배포 JAR를 만들 수 있다. 실제 서버 생성, DB 연결,
도메인 및 HTTPS 설정은 OCI 계정 준비 후 진행한다.

## 1. JAR 빌드

Windows:

```text
mvnw.cmd clean package
```

Linux:

```text
./mvnw clean package
```

생성된 `target/ai-dashboard-0.0.1-SNAPSHOT.jar`를 서버의
`/opt/deveopsj/app.jar`로 배치한다.

## 2. 서버 파일 배치

- `deploy/systemd/deveopsj.service` → `/etc/systemd/system/deveopsj.service`
- `deploy/systemd/deveopsj.env.example` → `/etc/deveopsj/deveopsj.env`
- `deploy/nginx/deveopsj.conf` → `/etc/nginx/sites-available/deveopsj`

환경변수 파일은 실제 값으로 수정하고 소유자를 `root:deveopsj`, 권한을 `640`으로
제한한다. 실제 비밀값이 들어간 파일은 Git에 커밋하지 않는다.

현재 템플릿은 최초 사용자 준비를 위해 `REGISTRATION_ENABLED=true`로 되어 있다.
필요한 사용자의 가입이 끝나면 `false`로 변경한다.

로그인은 같은 ID와 접속 IP 조합에서 기본 5회 실패하면 10분 동안 차단된다.
`LOGIN_MAX_ATTEMPTS`와 `LOGIN_BLOCK_DURATION`으로 조정할 수 있다. 차단 기록은 서버
메모리에만 저장되므로 애플리케이션을 재시작하면 초기화된다.

로그인 세션은 마지막 요청 후 기본 30분이 지나면 만료된다. 운영 환경에서는
`SESSION_TIMEOUT`으로 변경할 수 있으며 `30m`, `1h` 같은 형식을 사용한다.

## 최초 관리자 지정

회원가입으로 만든 계정은 모두 `USER` 권한이다. DB 연결 후 관리자 ID를 정확히
확인하고 다음 명령을 한 번만 실행한다. 실제 배포 전에는 실행하지 않는다.

```sql
START TRANSACTION;
UPDATE members SET role = 'ADMIN' WHERE login_id = '관리자_로그인_ID';
SELECT member_id, login_id, role FROM members WHERE login_id = '관리자_로그인_ID';
COMMIT;
```

조회 결과가 정확히 한 명인지 확인한다. 관리자는 종목 동기화 등 `/krx/**` 관리
기능에 접근할 수 있으므로 일반 사용자에게 부여하지 않는다.

## 3. systemd 실행

```text
sudo systemctl daemon-reload
sudo systemctl enable --now deveopsj
sudo systemctl status deveopsj
sudo journalctl -u deveopsj -f
```

애플리케이션은 `prod` 프로필로 실행되며 실패하면 자동 재시작한다.

## 4. Nginx 연결

```text
sudo ln -s /etc/nginx/sites-available/deveopsj /etc/nginx/sites-enabled/deveopsj
sudo nginx -t
sudo systemctl reload nginx
```

`http://SERVER_IP/health`가 `{"status":"UP"}`을 반환하면 애플리케이션과 DB 연결이
정상이다. 현재 Nginx 파일은 최초 연결 확인용 HTTP 설정이다. `prod` 세션 쿠키는
HTTPS 전용이므로 로그인 기능을 외부에 공개하기 전에 도메인과 인증서를 반드시
설정한다.
