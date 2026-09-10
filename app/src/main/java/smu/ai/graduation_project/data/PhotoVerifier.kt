package smu.ai.graduation_project.data

import smu.ai.graduation_project.domain.PhotoVerification

/**
 * 촬영한 사진을 온디바이스 비전 모델로 분류한다.
 *
 * 실제 구현(`OnnxPhotoVerifier` 등)은 모델 파일과 전처리에 의존하므로 `data/` 에 두고,
 * 점수를 통과/재촬영/검수로 바꾸는 판정 규칙은 [PhotoVerification] (순수 Kotlin)에 둔다.
 */
interface PhotoVerifier {

    /** 모델 식별자. `user_missions.photoVerifyModelVersion` 에 기록한다. */
    val modelVersion: String

    /**
     * 촬영본을 라벨별 점수로 분류한다.
     * **모델 추론은 무거우므로 [SupabaseStorage.upload] 처럼 백그라운드 스레드에서 부르라.**
     *
     * @param photoBytes 촬영본 JPEG 바이트
     * @return 라벨별 점수. 모델을 불러오지 못했으면 null.
     */
    fun classify(photoBytes: ByteArray): PhotoVerification.Classification?

    /**
     * 무거운 리소스(예: 런타임 다운로드하는 임베더 모델)를 미리 준비한다.
     * 화면 진입 시 백그라운드로 부르면 첫 [classify] 가 네트워크에 막히지 않는다. 기본은 no-op.
     */
    fun prefetch() {}
}

/** 테스트·Compose 프리뷰용. 미리 지정한 분류 결과를 그대로 돌려준다. */
class FakePhotoVerifier(
    private val fixed: PhotoVerification.Classification? = null,
    override val modelVersion: String = "fake-0"
) : PhotoVerifier {
    override fun classify(photoBytes: ByteArray): PhotoVerification.Classification? = fixed
}
