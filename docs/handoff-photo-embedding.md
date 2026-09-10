# 인수인계: 참조 이미지 임베딩 유사도 (Mac → Windows)

Mac 세션에서 보고서 6.7절(참조 이미지 임베딩 유사도)을 구현했다. **커밋은 안 했다.**
Windows 세션 Claude Code 에 아래를 그대로 붙여 이어서 진행한다.

---

## Windows Claude Code 에 붙일 프롬프트

```
Mac 세션에서 "참조 이미지 임베딩 유사도"(보고서 6.7절)를 구현하고 커밋하지 않은 상태로 넘겼어.
docs/handoff-photo-embedding.md 를 읽고, 아래 순서로 마무리해줘.

## Mac 에서 이미 한 것 (검증 완료)
- 0단계 검증: ml/embedding_separability.py 로 "같은 카테고리 안 대상 구분" 분리도 측정.
  MobileViT 분류기 pooled feature 재사용(0MB 안)은 ROC AUC 0.60 으로 기각,
  CLIP ViT-B/32 는 AUC 0.76(int8 양자화해도 0.762) → CLIP 채택.
- ML 스크립트 신규: ml/export_clip_image_encoder.py (CLIP→onnx int8 89MB),
  ml/embed_missions.py (대표 이미지→missions/{id}.photoEmbedding),
  ml/embedding_separability.py. thresholds.json 에 similarity 섹션.
- 앱 신규: data/ImagePreprocess.kt (분류·임베딩 공통 전처리 + EXIF 회전 반영),
  data/PhotoEmbedder.kt (interface + FakePhotoEmbedder + ModelSource: asset/cachedDownload
  + PhotoEmbedderAssets), data/OnnxClipPhotoEmbedder.kt (CLIP 임베더).
- 앱 수정: PhotoVerification.Classification 에 embedding, PhotoVerification.verify() 에
  referenceEmbedding 파라미터 + 유사도 ±1단계 결합(무효 단락 → 카테고리 1차 → 유사도 조정),
  PhotoEmbedding.cosineOrNull/l2Normalized, PhotoVerificationConfig 에
  similarityRescueThreshold=0.50 / similaritySuspectThreshold=0.68 (공개 프록시 잠정치),
  OnnxPhotoVerifier 가 임베더를 합성 + prefetch(), PhotoGate.decide(referenceEmbedding),
  Mission/MissionInfo/MissionPerformUiState 에 photoEmbedding,
  FirebaseMissionRepository 가 missions.photoEmbedding 읽어 넘기고 user_missions 에
  photoVerifySimilarity 기록, MissionPerformViewModel 이 화면 진입 시 photoVerifier.prefetch(),
  AdminPhotoReviewScreen 에 유사도 표시.
- build.gradle.kts + libs.versions.toml: androidx.exifinterface:1.3.7 추가.
- assets/ 에 photo_embedder_preprocessor.json, photo_embedder_version.txt 커밋.
- 보고서 docs/report-draft.md 6.7.4~6.7.6·7.x·8.2·10·트러블슈팅 갱신.
- Mac 에서 `gradlew :app:testDebugUnitTest :app:compileReleaseKotlin` BUILD SUCCESSFUL,
  PhotoVerificationTest 18 + PhotoGateTest 8 전부 통과. (instrumented 테스트는 미실행)

## Windows 에서 할 것
1. `gradlew :app:testDebugUnitTest` 재확인 후, 문제 없으면 이 변경을 **커밋**해줘.
   (커밋 메시지는 한국어로, 이 저장소 스타일에 맞춰서. attribution 라인 넣지 마.)
2. `gradlew :app:connectedDebugAndroidTest` 또는 Android Studio 로 OnnxPhotoVerifierTest 실행 —
   신규 `capturedEmbeddingIsNormalizedWhenEmbedderBundled` 는 임베더 모델이 없으면 Assume 으로 skip 됨.
3. CLIP 임베더 모델 준비:
   - `cd ml && .venv\Scripts\python export_clip_image_encoder.py --out C:\tmp\photo_embedder.onnx --quantize`
   - 나온 `photo_embedder_int8.onnx`(약 89MB)를 Supabase Storage 의 **공개 버킷 `app-models`** 에
     `photo_embedder_int8.onnx` 이름으로 업로드 (버킷 없으면 새로 만들고 Public 설정).
     ※ 앱은 SUPABASE_URL 로부터 `/storage/v1/object/public/app-models/photo_embedder_int8.onnx` 를 받음.
   - APK 에 번들하고 싶으면 대신 `app/src/main/assets/photo_embedder_int8.onnx` 로 넣으면 됨(APK +89MB).
4. 기존 미션 대표 이미지 임베딩 채우기:
   `.venv\Scripts\python embed_missions.py --model C:\tmp\photo_embedder.onnx --firebase-key serviceAccount.json`
   (서비스 계정 키 없으면 `--csv missions.csv --out embeddings.json` 로 뽑아 수동 반영)
5. 실기기에서 "졸업 프로젝트 개발하기" 미션으로 사진 인증 재시도.
   - photoEmbedding 이 있는 미션이면 로그/Firestore 의 photoVerifySimilarity 확인.
   - 유사도 임계값(PhotoVerificationConfig 0.50 / 0.68)은 공개 scene 프록시 기반 잠정치.
     실제 미션 사진 vs 대표 이미지로 재보정 필요 → 6.7.6.

## 주의
- PhotoVerification.verify() 시그니처가 바뀌었다: verify(missionCategory, classification, referenceEmbedding, config).
  위치 인자로 config 를 세 번째에 넘기던 코드는 깨진다(테스트는 이미 고쳐둠).
- MissionRepository.uploadPhotoAndComplete 에 referenceEmbedding: FloatArray? 파라미터 추가됨.
- 임베더 모델이 없으면 Classification.embedding == null → 유사도 결합 건너뛰고 기존 카테고리 규칙만 적용(하위호환).
```

---

## 참고: Mac 에서 SDK 없이 빌드했던 방법 (재현용)

```bash
brew install --cask android-commandlinetools
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties   # gitignored
# VS Code 번들 JRE 는 jlink 가 없어 javac 단계에서 실패 → 정식 JDK 지정
sh gradlew :app:testDebugUnitTest \
  -Dorg.gradle.java.home=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```
