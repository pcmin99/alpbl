# AI 연결 규격

무료는 자체 모델, 유료는 외부 LLM API로 고정됩니다. 서버가 실제 구독의 유효 기간을 확인하여 recognition_jobs.plan_code/backend를 결정합니다. 클라이언트는 유료 플랜을 지정할 수 없습니다.

## 자체 온디바이스 모델

`frontend/src/ai/deviceModel.ts`의 classifyOnDevice(uri)를 구현하세요. 현재는 모델 미정 오류를 반환합니다. 반환 규격:

```ts
{modelVersion:'eatpick-mobilenet-v3-2026-10', candidates:[{foodCode:'kimchistew',confidence:0.87}]}
```

이미지 전처리 크기/채널/정규화, 라벨과 foods.code 매핑은 학습한 모델 규격을 따라야 합니다. 모델 가중치가 없는 상태에서 일반 MobileNet 분류 결과를 음식 DB에 임의 대응시키지 않았습니다. TFLite/ONNX 등 네이티브 모듈을 고른 뒤 개발 빌드에서 연결해야 합니다. 휴대폰 추론 결과는 사용자 입력으로 취급하며 모델이 실제 실행되었는지 서버만으로 입증할 수 없습니다. 수동 음식 기록도 지원합니다.

무료 인식은 서버의 회원 행 잠금 아래 5회 제한을 예약합니다. 같은 requestKey는 카운트를 다시 차감하지 않습니다. 실패한 요청도 예약 이후 발생했다면 차감됩니다. 서버 사전 검증에서 모델 설정이 없으면 차감하지 않습니다. 날짜 기준은 Asia/Seoul입니다. 온디바이스 추론이 서버 예약보다 먼저 실행되므로 로컬 모델의 실행 횟수 자체를 강제하지는 못합니다.

## 프리미엄 LLM 게이트웨이

특정 공급자가 아직 정해지지 않아 공급자 중립 HTTP 어댑터로 작성했습니다. 서버 환경변수:

```text
LLM_GATEWAY_URL=https://your-server.example/food-classify
LLM_GATEWAY_KEY=서버 전용 비밀 값
LLM_MODEL=실제 공급자/모델 버전
```

직접 OpenAI/Gemini/Claude 주소를 넣으면 이 사용자 정의 규격과 맞지 않을 수 있습니다. 선택한 공급자의 요청/응답 변환을 RecognitionService에 구현하거나 아래 규격의 내부 게이트웨이를 제공하세요.

요청: POST, Authorization: Bearer, Idempotency-Key: recognition job UUID.

```json
{"model":"configured-model","imageBase64":"...","mimeType":"image/jpeg","allowedFoodCodes":["kimchistew","salad"]}
```

응답:

```json
{"candidates":[{"foodCode":"kimchistew","confidence":null}],"inputTokens":120,"outputTokens":30}
```

후보는 1~5개, 중복 없는 foodCode, DB에 등록된 코드여야 합니다. 신뢰도가 제공되지 않으면 null입니다. LLM 응답의 임의 자기평가 수치를 실제 확률로 간주하지 마세요. 서버는 영양 추정값을 받아 저장하지 않고 검증한 음식 코드의 영양 DB를 사용합니다. API 연결 실패/미설정은 오류 처리하며 자체 모델로 폴백하지 않습니다.

각 요청의 실제 모델과 플랜, simulated, 처리 상태를 인식 작업에 보관하고 실제 LLM 호출은 llm_api_attempts에 사용량/실패를 기록합니다. 이 버전은 자동 재시도를 하지 않습니다. 타임아웃 때 공급자가 처리했을 수 있으므로 재시도 정책은 공급자의 멱등성 규격에 맞추어 확장해야 합니다. 외부 호출 중 프로세스가 종료된 running 작업은 운영 시 타임아웃 정리 작업을 추가하세요.

## 수동 기록과 보관

음식 검색 후 직접 기록은 인식 API를 호출하지 않으므로 인식 횟수를 차감하지 않습니다. 식사를 기록하는 시점에 인분을 적용한 영양 수치를 meals에 복사합니다. 사진 원문은 서버 DB/디스크에 보관하지 않으며, 유료 분석 시 해당 요청 동안 외부 API에 전달됩니다. 실제 운영 전 선택한 공급자의 이미지 보관 정책과 사용자 고지 내용을 정하세요.
