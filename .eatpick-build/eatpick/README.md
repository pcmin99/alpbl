# 잇픽 — React Native + Spring Boot

첨부 `eatpick-prototype2.html`을 기준으로 만든 모바일 앱과 REST API입니다. 웹뷰로 HTML을 감싼 앱이 아니라 React Native의 View/Text/Pressable로 구현했습니다.

## 가장 빠른 시연 실행

필수: **Java 17**, **Node.js 22 LTS**. 백엔드 소스 빌드는 Maven Wrapper가 포함되어 있어 Maven 별도 설치가 필요 없습니다. 인터넷 연결은 최초 의존성 설치에 필요합니다.

### 1. 백엔드

Windows에서는 ZIP을 푼 `eatpick` 폴더에서 `start-demo-backend.cmd`를 실행합니다. 동봉한 실행 JAR를 사용합니다.

소스로 실행하려면:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```sh
cd backend
chmod +x mvnw
./mvnw spring-boot:run
```

기본 `demo` 프로필은 로컬 파일 H2 DB를 사용합니다. 주소: `http://localhost:8080/health`.

### 2. 설치 없이 웹 화면 먼저 보기

백엔드를 실행한 상태에서 `start-preview-web.cmd`를 실행하고 `http://localhost:8081`을 여세요. Node.js만 있으면 동봉된 웹 번들을 볼 수 있으며 npm 설치는 필요 없습니다. 코드를 수정할 때는 아래 개발 명령을 사용하세요.

### 3. 프런트엔드 개발 실행

다른 터미널에서:

```powershell
cd frontend
Copy-Item .env.example .env
npm ci
npm run web
```

웹에서 **로그인 없이 시연 시작**을 누릅니다. 홈 → 기록 → 샘플 음식 선택 → 인분 확인 → 기록 → 추천/장보기 → 요금제 변경 → 상세 분석 흐름을 사용할 수 있습니다.

휴대폰에서는 `.env`의 `EXPO_PUBLIC_API_URL`을 PC의 LAN IP로 바꾸고 `npm start`로 실행하세요. PC와 휴대폰은 같은 Wi-Fi를 사용해야 합니다. Android 에뮬레이터는 `http://10.0.2.2:8080`, iOS 시뮬레이터는 `http://localhost:8080`입니다. 이 프로젝트는 Expo SDK 54 기준이며 이에 맞는 Expo Go 또는 개발 빌드가 필요합니다. 설치된 Expo Go 버전이 SDK와 맞지 않으면 개발 빌드를 사용하세요.

소셜 로그인과 자체 모델의 네이티브 추론은 **개발 빌드**에서 확인하세요. `npm run android`는 Android SDK/JDK가 필요하며, `npm run ios`는 macOS/Xcode가 필요합니다. 이번 산출물에는 APK/IPA가 포함되지 않습니다.

## 구성

```text
frontend/              React Native + Expo + TypeScript
  App.tsx              로그인·탭·촬영/인식·목표 수정·프리미엄 모달
  src/screens.tsx      홈·분석·추천·내 정보
  src/ui.tsx           공통 UI와 영양정보 라벨
  src/ai/deviceModel.ts 자체 AI 연결 지점
backend/               Spring Boot 3.5.16, Java 17, Spring Security, JDBC
  src/main/java/       API·도메인·보안·AI 라우팅
  src/test/java/       권한·동시성·영양계산·로그인 코드 통합 테스트
  eatpick-api.jar      바로 실행하는 백엔드 JAR
  mvnw / mvnw.cmd      Maven Wrapper
database/              이 앱 버전에 맞춘 MySQL 초기 스키마
docs/                  API 명세·AI/소셜 연동 설명·검증 결과·화면 미리보기
design/                전달받은 원본 HTML
compose.yml            MySQL + API 컨테이너 설정
```

## 제공하는 기능

- 원본의 세이지 배경, 짙은 초록, 영양성분표, 하단 5개 메뉴와 카드 구성을 모바일 컴포넌트로 재현
- 식사 목록·인분·끼니·사진/앨범·수동 음식 검색·인식 후보 선택·추가/삭제
- 섭취 시점 영양 스냅샷 저장, 일별 합계, 최근 7일 단백질 그래프, 7일/28일 기록일 평균
- 목표 수정, 기본/맞춤 상품 목록, 직접 담기, 자동 장보기 삭제 상태 보존
- 무료/프리미엄 필드·API 권한 분리, 서버에서 무료 5회 한도와 중복 요청 제어
- 소셜 로그인 브라우저 인증 → 60초 일회용 코드 → 앱 전용 세션 토큰 교환. 회원은 이메일로 자동 병합하지 않음
- 네이티브 세션은 SecureStore에 저장하고 서버는 토큰의 SHA-256 해시만 저장. 웹 시연은 sessionStorage 사용

## AI 정책과 현재 상태

| 구분 | 실제 연동 모드 | 시연 모드 |
|---|---|---|
| 무료 | 자체 온디바이스 모델 결과를 서버에 동기화 | 자체 모델 경로로 표시한 샘플 결과 |
| 유료 | 서버가 외부 LLM 게이트웨이 API 호출 | LLM 경로로 표시한 샘플 결과 |

**자체 모델과 LLM 공급자가 미정이므로 실제 모델 파일/가중치·공급자 API 키는 포함하지 않았습니다.** 시연 결과에는 `simulated=true`가 기록되고 화면에도 시연임을 표시합니다. 업로드 사진도 시연 모드에서는 샘플 결과입니다.

실제 무료 모델은 `frontend/src/ai/deviceModel.ts`에서 연결합니다. 실제 프리미엄 API는 서버 환경변수와 `docs/AI-INTEGRATION.md`의 공급자 중립 게이트웨이 규격으로 연결합니다. 설정이 없으면 503을 반환하며 유료 요청을 무료 모델로 자동 전환하지 않습니다.

소셜 로그인은 클라이언트 ID/시크릿과 공급자 콘솔 Redirect URI 설정 후 사용할 수 있습니다. `docs/SOCIAL-LOGIN.md`를 참고하세요. 실제 외부 계정으로의 로그인 테스트는 수행하지 않았습니다.

프리미엄 전환은 **demo 전용 시연 기능**입니다. 실제 결제, 결제 웹훅, 외부 제휴 구매 링크, 생성형 AI 식단 코치는 별도 공급자 연결 전에는 동작하지 않습니다. 리포트 피드백과 맞춤 상품 순위는 명시적인 계산/정렬 규칙이며, LLM이 생성했다고 표시하지 않습니다. 상품·영양정보는 원본의 시연용 데이터입니다.

## MySQL로 실행

**이 ZIP의 `database/schema-mysql.sql`은 실행 코드와 일치하는 앱 전용 스키마입니다. 앞서 전달한 DB 초안에 덮어쓰는 마이그레이션이 아닙니다. 새 DB에서 시작하세요.** 단순화한 14개 테이블이며, 식사 영양 이력은 별도 food_versions 대신 meals의 영양 스냅샷으로 보존합니다.

1. MySQL 8.0.16+ 또는 8.4에서 `database/schema-mysql.sql`을 실행합니다.
2. backend/.env.example을 보고 **실제 프로세스 환경변수**를 설정합니다. Spring Boot는 `.env` 파일을 자동으로 읽지 않습니다.
3. `SPRING_PROFILES_ACTIVE=mysql`, DB_URL/DB_USER/DB_PASSWORD를 설정하고 실행합니다.
4. 프런트 `.env`에서 `EXPO_PUBLIC_DEMO_MODE=false`와 실제 API URL을 설정합니다. 환경변수 변경 후 Expo를 재시작하세요.

PowerShell 예시:

```powershell
$env:SPRING_PROFILES_ACTIVE='mysql'
$env:DB_URL='jdbc:mysql://localhost:3306/eatpick?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8'
$env:DB_USER='eatpick'
$env:DB_PASSWORD='내 비밀번호'
java -jar backend/eatpick-api.jar
```

Docker:

```powershell
Copy-Item .env.example .env
# .env의 비밀번호를 먼저 설정
# 기본 compose는 mysql 프로필이며 시연 로그인은 제공하지 않음
# 소셜을 사용하려면 docs/SOCIAL-LOGIN.md의 환경변수와 oauth 프로필을 추가
docker compose up --build
```

Docker DB 초기화는 새 볼륨에서만 실행됩니다. 기존 DB를 지우는 명령은 포함하지 않았습니다. MySQL 프로필은 스키마를 자동 변경하지 않습니다. 실제 서버는 HTTPS를 사용하고 프런트의 허용 Origin을 환경변수로 지정하세요.

## 검사

```powershell
cd backend
.\mvnw.cmd test
cd ../frontend
npm run typecheck
npm run export:web
```

검증 범위와 한계는 `docs/VERIFICATION.md`에 정리했습니다. 실행 JAR는 Java 17 이상에서 동작하며, 소스를 바꾼 뒤에는 다시 빌드해야 합니다.
