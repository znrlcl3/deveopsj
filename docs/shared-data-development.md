# 회사 데이터를 집·회사에서 함께 사용하는 개발 구성

현재 입력한 데이터의 원본은 회사 PC MySQL이다. 집 PC는 메인 개발 환경으로 두고,
회사 DB를 클라우드로 한 번 이전한 뒤 집·회사·서버 앱이 그 클라우드 DB를 함께 사용한다.

```text
집 PC 로컬 앱 ─── SSH DB 터널 ──┐
회사 PC 로컬 앱 ─ SSH DB 터널 ──┼── 클라우드 공용 MySQL
클라우드 서버 앱 ───────────────┘
```

로컬 앱을 실행하면 코드 수정 결과를 즉시 테스트한다. SSH 앱 터널(18080)은 배포된
서버 앱을 보는 방식이며, 이번 DB 터널(13306)과 용도가 다르다. 스키마 자동 변경은
막지만 로컬 앱에서 입력·수정·삭제하면 공유 데이터에 실제 반영된다.
API 키는 각 PC의 기존 로컬 설정/환경변수를 사용하며 데이터 공유와 별개다.

## 1. 회사 원본 내보내기

회사 앱이 사용하는 실제 DB명·MySQL 사용자·호스트·포트를 Workbench에서 확인한다.
비밀번호를 대화나 코드에 기록하지 않는다. 프로젝트 루트에서 다음을 실행한다.

```powershell
.\deploy\scripts\export-local-db.ps1 -DatabaseName "회사DB명" -DatabaseUser "회사DB사용자"
```

기본은 127.0.0.1:3306이며 다른 주소는 -DatabaseHost / -DatabasePort로 지정한다.
mysqldump를 PATH나 MySQL 8.0 설치 경로에서 찾고, 없으면 -MysqldumpPath로 지정한다.
MySQL 비밀번호는 도구가 터미널에서 직접 물어본다.

결과는 Git에서 제외된 .local-data 폴더에 SQL과 SHA256 파일로 저장한다.
디렉터리 접근 권한은 현재 Windows 사용자로 제한한다.
스키마/데이터/트리거/루틴/이벤트를 포함하며 전체 서버 계정 테이블은 복사하지 않는다.
Windows 인코딩 문제를 피하기 위해 PowerShell 출력 리디렉션 대신 --result-file을 사용한다.
[MySQL 공식 mysqldump 설명](https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html)

회사의 읽기·백업 권한이 부족하면 내보내기는 실패한다. .partial 파일은 이전에 사용하지 않는다.
single-transaction의 일관된 스냅샷은 InnoDB를 전제로 하므로 테이블 엔진과 DB 버전을 확인한다.
이전할 최종 백업부터 공용 DB 전환이 끝날 때까지 회사 앱에서 새 데이터를 입력하거나
스키마를 변경하지 않는다. 그렇지 않으면 내보낸 시점 이후 데이터는 서버로 옮겨지지 않는다.

## 2. 키 확보 후 서버로 이전

회사 PC에는 아직 SSH 개인키가 없어 이 단계는 원격 실행하지 않았다.
집의 키와 known-hosts를 저장소 밖으로 옮겨 서버에 접속한다.
기존 클라우드 DB도 먼저 백업하고 내용이 있는지 확인한다.

회사 SQL을 서버에 업로드하고 SHA256이 일치하는지 확인한다. 기존 서버 DB에 바로
덮어쓰거나 회사·서버 데이터를 자동 병합하지 않는다. 기존 DB를 보존한 채 새 스키마에
먼저 복원한다. 아래 명칭은 예시이며 아직 생성하지 않았다.

```sql
CREATE DATABASE deveopsj_shared CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

같은 이름이 이미 있으면 멈추고 다른 이름을 사용한다. 비어 있는 새 스키마에만 복원한다.
서버의 MySQL 관리 계정으로 실행하는 예시:

```bash
mysql --user=관리계정 --password --database=deveopsj_shared < /업로드한/회사백업.sql
```

서버 버전의 MySQL 호환성, collation, 루틴/이벤트 DEFINER 계정과 필요한 권한을 확인한다.
행 수, 회원 계정, 지출·수입·계획·납입·매매와 합계를 회사 원본과 비교한다.
일부 복원 실패 상태에서 앱을 연결하거나 --force로 실패를 숨기지 않는다.
기존 서버 DB에도 따로 입력한 내역이 있다면 전환 전에 병합 여부를 확인한다.
운영 계정은 새 스키마에 필요한 권한을 부여하고 DDL 변경은 관리 계정으로 처리한다.

검증 후 서버 환경파일의 DB_URL만 공용 스키마에 맞춰 변경한다.
DB_USERNAME/DB_PASSWORD는 실제 권한과 맞추되 API/Keycloak 설정은 유지한다.
운영 prod 프로필의 validate를 유지한다. 재시작 후 health와 로그인·데이터를 확인한다.
기존 서버 DB와 회사 백업은 검증이 끝날 때까지 보존한다.

## 3. 집·회사 로컬 앱을 같은 DB에 연결

서버 MySQL 주소/포트는 서버 환경파일의 DB_URL에 있는 값으로 확인한다.
같은 서버의 MySQL이 127.0.0.1:3306에서 실행되는 경우의 터널 예시:

```powershell
$key = "개인키의 실제 경로"
$knownHosts = "oci-known-hosts의 실제 경로"
.\deploy\scripts\connect-deveopsj.ps1 -IdentityFile $key -KnownHostsFile $knownHosts -DatabaseTunnel
```

MySQL이 다른 호스트/포트에 있으면 -RemoteDatabaseHost / -RemoteDatabasePort를
정확히 지정한다. 회사 PC의 13306이 이미 사용 중이면 -LocalDatabasePort를 바꾼다.
SSH가 서버에서 DB로 접속하므로 DB의 공인 포트를 열지 않는다.

터널 창을 열어 둔 상태에서 다른 터미널로 앱을 실행한다.

```powershell
.\deploy\scripts\start-cloud-dev.ps1 -DatabaseName "deveopsj_shared" -DatabaseUser "서버DB사용자"
```

DB 비밀번호를 화면에 표시하지 않고 입력받아 해당 실행의 환경변수로만 전달한다.
앱은 http://localhost:8080/member/login으로 접속한다. 다른 앱이 8080을 쓰면 -AppPort를 바꾼다.
DB 포트를 바꿨다면 -DatabasePort도 같은 값으로 지정한다. 서버 MySQL 계정의 접속 허용
호스트와 권한이 SSH 터널 경로에 맞아야 한다. 터널이 살아 있다고 DB 인증까지 검증된 것은 아니다.

STS에서는 활성 프로필 cloud-db와 환경변수 CLOUD_DB_NAME, CLOUD_DB_USERNAME,
CLOUD_DB_PASSWORD, 필요 시 CLOUD_DB_LOCAL_PORT를 Run Configuration에 설정해도 된다.
비밀번호가 포함된 실행 설정은 Git에 내보내지 않는다. 같은 실행에서 prod/test 프로필이나
SPRING_JPA_HIBERNATE_DDL_AUTO=update/create/create-drop 설정을 추가하지 않는다.
Test 프로필과 자동화 테스트는 공유 DB에 연결하지 않고 기존 H2/mock 환경을 사용한다.

cloud-db 프로필은 회사 application.properties의 DB 주소/ddl-auto=update를 덮어쓰며
validate와 SQL 초기화 금지를 사용한다. 스키마가 맞지 않으면 시작 실패로 알려준다.
기능 변경에 DDL이 필요하면 별도 SQL과 적용 순서를 먼저 검토한다.
API 키는 기존 로컬 설정을 유지하고 SSO 테스트는 Keycloak issuer/redirect 조건을 따로 맞춘다.

## 집을 메인으로 하는 코드 흐름

집에서 수정·검증·커밋·푸시한 뒤 회사에서 pull한다. 회사에서 수정했다면 푸시해 집에서
이어받는다. DB는 클라우드 한 곳으로 공유하므로 코드 동기화와 데이터 이동을 혼동하지 않는다.
양쪽 코드의 스키마 요구사항이 다르면 validate가 실패할 수 있으므로 버전을 맞춘다.

2026-10-12 회사 MySQL의 my_wealth_db를 확인하고 원본을 변경하지 않은 SQL 백업을
.local-data에 생성했다. SHA256과 주요 테이블 행 수 기록을 함께 보관한다.
추가 입력이 생기면 이전 직전에 최종 백업을 다시 만든다.
서버 복원, 실제 공용 DB 연결과 원본/복원 데이터 대조는 아직 실행하지 않았다.

