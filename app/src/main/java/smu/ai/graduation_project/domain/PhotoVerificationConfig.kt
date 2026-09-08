package smu.ai.graduation_project.domain

/**
 * 사진 인증(2단계) 판정 임계값.
 *
 * 온디바이스 비전 모델의 오프라인 평가(test 셋 임계값 스윕)로 정한 값을 한곳에 모아,
 * 판정 결과를 설명하고 튜닝할 수 있게 한다. ([RecommendationWeights] 와 같은 역할)
 *
 * 현재 기본값: `mobilevit-small-fullft-1` (test macro-F1 0.82) 로 `ml/thresholds.json` 에서 선정.
 * invalidReject 0.55 → 무효 사진 차단율 ≈ 0.92 / 정상 사진 오탐 ≈ 0.005.
 */
data class PhotoVerificationConfig(
    /** 미션 카테고리 점수가 이 값 이상이면 자동 통과. */
    val autoPassThreshold: Double = 0.65,
    /** 미션 카테고리 점수가 이 값 미만이면 재촬영을 요청한다. */
    val hardRejectThreshold: Double = 0.22,
    /** 무효(셀카·스크린샷·무관 실내 등) 점수가 이 값 이상이면 재촬영을 요청한다. */
    val invalidRejectThreshold: Double = 0.55,
    /**
     * 모델을 불러오지 못했을 때(추론 불가) 미션을 어떻게 처리할지.
     * true 면 종전처럼 통과, false 면 관리자 검수로 보낸다.
     */
    val passWhenModelUnavailable: Boolean = false
) {
    init {
        require(hardRejectThreshold in 0.0..1.0) { "hardRejectThreshold 는 0..1 범위여야 합니다." }
        require(autoPassThreshold in 0.0..1.0) { "autoPassThreshold 는 0..1 범위여야 합니다." }
        require(invalidRejectThreshold in 0.0..1.0) { "invalidRejectThreshold 는 0..1 범위여야 합니다." }
        require(hardRejectThreshold <= autoPassThreshold) {
            "hardRejectThreshold 는 autoPassThreshold 보다 클 수 없습니다."
        }
    }

    companion object {
        val DEFAULT = PhotoVerificationConfig()
    }
}
