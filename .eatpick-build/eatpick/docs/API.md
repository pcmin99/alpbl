# API 명세

기본 주소 http://localhost:8080. JSON. /api/**는 `Authorization: Bearer <session token>` 필요.

| 메서드 | 경로 | 동작 |
|---|---|---|
| GET | /health | 서버 상태 |
| POST | /auth/demo | 시연 계정 생성·샘플 식단·토큰 발급(demo만) |
| GET | /auth/start/{provider}?challenge=... | 공급자 로그인 시작 |
| POST | /auth/exchange | code/verifier로 앱 토큰 교환 |
| POST | /api/logout | 현재 세션 삭제 |
| GET | /api/me | 회원·현재 구독·목표·남은 인식 횟수 |
| PUT | /api/me/goal | 오늘부터 적용할 영양 목표 저장 |
| GET | /api/foods?q=김치 | 음식 검색 |
| GET | /api/home?date=2026-10-20 | 날짜별 식사·합계·목표 |
| POST | /api/meals | 음식·인분·끼니 기록 |
| DELETE | /api/meals/{id} | 본인 식사 삭제 |
| GET | /api/protein-trend | 최근 7일 기록된 날짜의 단백질 합계 |
| GET | /api/reports?days=7 | 프리미엄 7일/28일 상세 평균 |
| GET | /api/products?mode=basic | 기본 또는 ai(프리미엄) 순위 |
| GET | /api/shopping | 자동/직접 장보기 목록 |
| POST | /api/shopping | productId 담기(중복 방지) |
| DELETE | /api/shopping/{id} | 장보기 숨김·삭제 상태 보존 |
| POST | /api/recognitions | 인식 요청·중복 요청 조회 |
| GET | /api/recognitions/{id} | 본인 인식 상태·후보 |
| PUT | /api/demo/plan | premium:true/false 전환(demo만) |

식사 저장:

```json
{"foodCode":"kimchistew","slot":"lunch","servings":1.5,"recognitionJobId":null,"eatenAt":"2026-10-20T03:00:00Z"}
```

slot: breakfast/lunch/dinner/snack. 인분 범위 0.1~10. eatenAt 생략 시 현재 시각. recognitionJobId는 성공한 본인 작업만 허용하며 목록에서 직접 고른 음식은 null. 사용자 후보 수정은 최종 foodCode로 반영합니다.

인식 시연:

```json
{"requestKey":"클라이언트가 생성한 요청별 UUID","sampleFoodCode":"kimchistew"}
```

실제 AI 입력은 AI-INTEGRATION.md 참고. 중복 key는 기존 작업 반환. 인식 상태는 running/succeeded/failed이며 실제 API가 실패하면 같은 키로 자동 재호출하지 않습니다.

목표:

```json
{"goalType":"muscle_gain","kcal":2300,"carb":320,"protein":100,"fat":65,"sugar":50,"sodium":2000,"fiber":25}
```

날짜 기준: 식사와 한도 모두 한국 시간(Asia/Seoul). DB/JDBC 세션은 UTC. 열량 kcal, 나트륨 mg, 나머지 g. 무료 API 응답에서는 sugar/sodium/fiber를 제거합니다. 기록일 평균은 미기록일을 0으로 간주하지 않습니다. 같은 날짜의 목표를 다시 저장하면 그 날짜 목표는 교체되고 과거 날짜의 목표는 유지됩니다.

주요 오류: 400 입력 형식, 401 세션 만료/없음, 403 프리미엄 권한, 404 데이터/소유권, 409 아직 분석 중, 429 일일 한도, 502 외부 AI 오류, 503 실제 모델/API/소셜 설정 없음. 응답은 `{ "message": "사용자 안내" }` 형식입니다.
