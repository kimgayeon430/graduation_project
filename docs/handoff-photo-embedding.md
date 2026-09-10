# 참조 이미지 임베딩 유사도 (보고서 6.7절) — 상태

## 완료

| 커밋 | 내용 |
| --- | --- |
| `7d52c8e` | 판정 로직·앱 배선·테스트 (CLIP 인코더 채택, MobileViT pooled 재사용은 AUC 0.60 으로 기각) |
| `935e1ad` | 임베더 호스팅 Supabase → HF Hub (Supabase 무료 플랜 50MB 상한) |
| `8f1a8fd` | `embed_missions.py` ADC 지원 + 서비스 계정 키 gitignore |

- **임베더 모델**: [`kimgayeon430/travel-mission-photo-embedder`](https://huggingface.co/kimgayeon430/travel-mission-photo-embedder) (Public).
  `photo_embedder_int8.onnx` 88.6MB. 앱은 `resolve/main` 에서 받아 `filesDir` 캐시.
- **미션 임베딩 백필**: `missions` 16건 중 15건에 `photoEmbedding`(512d) + `photoEmbeddingModelVersion` 저장 완료.
  나머지 1건(`숙대입구`)은 `imageUrl` 이 없어 skip — 관리자 화면에서 대표 이미지 넣고 재실행하면 됨:
  `python ml/embed_missions.py --model <int8.onnx> --firebase-key serviceAccount.json`
- **빌드**: `gradlew :app:testDebugUnitTest :app:compileReleaseKotlin` BUILD SUCCESSFUL (Mac). 단위 테스트 전건 통과.

## 남은 것

1. **실기기 확인** — 임베더가 있는 미션(예: `용산_체험(졸프)`)으로 사진 인증:
   - 첫 인증 시 HF 에서 89MB 다운로드 → `filesDir/models/` 캐시 (미션 화면 진입 시 `prefetch()` 로 미리 받음)
   - Firestore `user_missions.photoVerifySimilarity` 기록 확인
   - 관리자 검수 화면에 "대표사진 유사도 0.xx" 표시 확인
2. **유사도 임계값 보정** — `PhotoVerificationConfig` rescue 0.50 / suspect 0.68 은 공개 scene 프록시 잠정치.
   실사용 로그(`photoVerifySimilarity`)가 쌓이면 실제 분포로 재조정 (6.7.6).
3. `숙대입구` 미션 대표 이미지 등록 후 임베딩 백필 (선택).
