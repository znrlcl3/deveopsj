# 1. Project Context
이 프로젝트는 사용자의 자산 계획과 지출 내역을 관리하고, AI(Gemini)가 깐깐한 자산 관리사 콘셉트로 피드백을 해주는 대시보드 애플리케이션입니다.

# 2. Tech Stack
- Backend: Java 17, Spring Boot, Spring Data JPA
- Database: MySQL
- Frontend: Thymeleaf, HTML, JavaScript
- Build Tool: Maven (pom.xml)

# 3. AI Assistant Rules (명령어 및 토큰 절약 규칙)
- [응답 형식] 불필요한 서론/결론은 빼고, 핵심 내용과 코드 위주로만 간결하게 답변해.
- [코드 작성] 코드를 제안할 때는 import 문을 포함한 전체 코드를 주지 말고, 수정이 필요한 '메서드'나 '클래스 내부'만 보여줘.
- [언어] 모든 설명과 주석은 한국어로 작성해.
- [디자인 패턴] Lombok 어노테이션(@Getter, @RequiredArgsConstructor 등)을 적극적으로 활용해서 코드를 줄여.