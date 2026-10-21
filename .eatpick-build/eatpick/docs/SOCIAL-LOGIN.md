# 소셜 로그인 연결

카카오·네이버는 OAuth2 사용자 정보 API, Google/Apple은 OIDC 기반 설정을 제공합니다. 실제 앱 키와 공급자 승인은 포함하지 않았으며 실제 계정 로그인은 미검증입니다.

1. 각 공급자 콘솔에 앱을 생성하고 Redirect URI를 `https://내-API-도메인/login/oauth2/code/google` 또는 kakao/naver/apple로 등록하세요.
2. backend/.env.example의 공급자 환경변수를 실제 서버 프로세스에 설정하세요. Apple client secret은 Apple 규격으로 서명한 만료 있는 JWT가 필요합니다.
3. `SPRING_PROFILES_ACTIVE=mysql,oauth`로 실행합니다. 사용하지 않는 공급자는 application-oauth.yml에서 registration/provider를 함께 삭제하세요. 해당 파일은 활성화한 모든 공급자의 키를 요구합니다.
4. frontend의 API_URL을 실제 서버 주소로 바꾸고 `EXPO_PUBLIC_DEMO_MODE=false`로 설정합니다.
5. 앱의 scheme `eatpick`가 등록된 개발 빌드를 만들고 설치합니다. Expo Go와 웹 시연의 콜백 주소는 이 native scheme과 일치하지 않습니다.

앱은 무작위 verifier의 SHA-256 challenge로 /auth/start/{provider}를 엽니다. Spring Security가 공급자 state와 OIDC 토큰을 검증합니다. 성공 시 서버는 세션을 종료하고 `eatpick://auth?code=...`로 60초 일회용 코드만 전달합니다. 앱은 원래 verifier로 /auth/exchange를 호출해 세션 토큰을 받습니다. 코드는 DB 잠금 아래 한 번만 사용할 수 있고 verifier가 다르면 거부됩니다. 앱 토큰을 URL에 직접 넣지 않습니다.

회원은 공급자와 subject로 찾습니다. 동일 이메일이라는 이유로 자동 병합하지 않습니다. 계정 연결/해제·탈퇴·세션 갱신 UI는 이 버전에 포함하지 않았습니다. 세션은 30일 후 만료되어 다시 로그인하며 로그아웃 시 서버에서 삭제됩니다.

Apple 설정은 최소 openid 범위의 코드 흐름입니다. name/email 범위를 요청하도록 확장하면 Apple의 form_post 응답과 HTTPS 쿠키 SameSite 설정을 함께 적용해야 합니다. Apple Services ID·도메인 검증·서명 JWT 생성은 공급자 문서대로 설정하세요. 외부 프록시를 사용하는 경우 외부 HTTPS base URL을 올바르게 전달하고 신뢰할 수 있는 프록시에서만 forwarded 헤더를 허용하세요.

참고 공식 문서:
- https://developers.kakao.com/docs/ko/kakaologin/rest-api
- https://developers.naver.com/docs/login/api/api.md
- https://developer.apple.com/documentation/signinwithapplerestapi
- https://docs.spring.io/spring-security/reference/servlet/oauth2/login/index.html
