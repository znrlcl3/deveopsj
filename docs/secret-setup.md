# 로컬 비밀값 설정

`src/main/resources/application.properties`에는 실제 비밀값을 저장하지 않는다.
애플리케이션을 실행하기 전에 아래 환경변수를 IDE 실행 설정 또는 운영 환경에 등록한다.

| 환경변수 | 용도 |
|---|---|
| `DB_URL` | MySQL JDBC URL |
| `DB_USERNAME` | MySQL 사용자 |
| `DB_PASSWORD` | MySQL 비밀번호 |
| `GEMINI_API_KEY` | Gemini API 키 |
| `KRX_API_AUTH_KEY` | KRX API 인증키 |
| `KIS_API_APP_KEY` | 한국투자증권 앱 키 |
| `KIS_API_APP_SECRET` | 한국투자증권 앱 시크릿 |

## 공개 배포 전 교체 대상

기존 DB 비밀번호와 Gemini, KRX, KIS 키는 폐기하고 새 값으로 교체한다.
새 키는 Git 커밋, 문서, 채팅, 로그에 붙여 넣지 않는다.

EC2에서는 우선 환경변수 또는 AWS Systems Manager Parameter Store/Secrets Manager로
주입하고, RDS 보안 그룹은 애플리케이션 EC2에서만 접근 가능하게 제한한다. 

## 운영 프로필 실행

운영 환경에서는 `prod` 프로필을 명시해서 실행한다.

```text
java -jar ai-dashboard.jar --spring.profiles.active=prod
```

`prod` 프로필은 DB 스키마를 자동 변경하지 않고 검증만 수행하며, SQL과 바인딩 값
상세 로그를 출력하지 않는다. HTTPS 프록시 뒤에서만 사용하며 세션 쿠키에
`Secure`, `HttpOnly`, `SameSite=Lax`를 적용한다.
