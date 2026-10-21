/** 학습 모델 미정: 실제 모델을 연결할 단일 진입점입니다.
 * react-native-fast-tflite / ONNX Runtime 등의 개발 빌드에서 전처리·추론을 구현하세요.
 * 출력 foodCode는 서버 foods.code와 매핑하고 신뢰도는 0~1이어야 합니다.
 * Expo Go에는 사용자 네이티브 추론 모듈을 추가할 수 없습니다.
 * 샘플 결과를 실제 추론인 것처럼 반환하지 않습니다.
 */
export async function classifyOnDevice(_uri:string):Promise<{modelVersion:string;candidates:{foodCode:string;confidence:number|null}[]}>{
 throw new Error('자체 AI 모델이 아직 연결되지 않았어요. 데모 모드에서는 샘플 사진으로 체험할 수 있어요.');
}
