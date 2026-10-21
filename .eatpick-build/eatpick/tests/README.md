# 선택적 UI 시연 테스트

별도 터미널에서 demo 백엔드와 웹 미리보기를 먼저 실행합니다.

```sh
npm install
npx playwright install chromium
npm test
```

CHROME_PATH 환경변수로 이미 설치한 Chrome 실행 파일을 지정할 수도 있습니다. WEB_URL 기본값은 http://localhost:8081 입니다. 테스트는 새 시연 계정을 만들고 docs의 미리보기 이미지를 갱신합니다. 실제 계정이나 실제 결제는 사용하지 않습니다.
