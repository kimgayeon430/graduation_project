package smu.ai.graduation_project.domain

/**
 * 사진 인증(2단계) 판정 규칙. 순수 Kotlin (모델·Android·Firebase 비의존).
 *
 * 온디바이스 비전 모델이 촬영본을 카테고리별 점수로 분류하면([Classification]),
 * 그 점수와 미션이 기대하는 카테고리를 비교해 세 갈래로 판정한다.
 *
 *  - [Verdict.PASS]         : 자동 통과. 바로 완료 처리.
 *  - [Verdict.NEEDS_REVIEW] : 애매함. 미션은 완료하되 관리자 검수 큐에 올린다.
 *  - [Verdict.REJECT]       : 부적합. 업로드하지 않고 재촬영을 요청한다.
 *
 * 임계값은 [PhotoVerificationConfig] 에 모여 있어 오프라인 평가 후 조정할 수 있다.
 */
object PhotoVerification {

    /** 무효(셀카·스크린샷·무관 실내 등) 클래스 라벨. 모델 학습 라벨과 일치해야 한다. */
    const val INVALID_LABEL = "무효"

    enum class Verdict { PASS, NEEDS_REVIEW, REJECT }

    /** 모델이 낸 라벨별 점수. 합이 1일 필요는 없다. */
    data class Classification(val scores: Map<String, Double>) {
        fun scoreOf(label: String): Double = scores[label] ?: 0.0
        val topLabel: String? get() = scores.maxByOrNull { it.value }?.key
    }

    data class Result(
        val verdict: Verdict,
        /** 미션 카테고리에 대한 모델 점수. */
        val matchScore: Double,
        /** 무효 라벨 점수. */
        val invalidScore: Double,
        /** 사용자/관리자에게 보여줄 사유. */
        val reason: String
    )

    /**
     * @param missionCategory 미션이 기대하는 카테고리 (예: `"맛집"`)
     * @param classification  모델 분류 결과. 모델을 못 불러왔으면 null.
     */
    fun verify(
        missionCategory: String,
        classification: Classification?,
        config: PhotoVerificationConfig = PhotoVerificationConfig.DEFAULT
    ): Result {
        if (classification == null) {
            return if (config.passWhenModelUnavailable) {
                Result(Verdict.PASS, 0.0, 0.0, "자동 인증을 건너뛰고 통과했습니다.")
            } else {
                Result(Verdict.NEEDS_REVIEW, 0.0, 0.0, "자동 인증을 할 수 없어 관리자 확인 후 포인트가 지급돼요.")
            }
        }

        val match = classification.scoreOf(missionCategory)
        val invalid = classification.scoreOf(INVALID_LABEL)

        return when {
            invalid >= config.invalidRejectThreshold ->
                Result(Verdict.REJECT, match, invalid,
                    "사진이 미션과 무관해 보여요. 미션 장소·대상을 촬영해 주세요.")

            match < config.hardRejectThreshold ->
                Result(Verdict.REJECT, match, invalid,
                    "사진에서 '$missionCategory' 미션 요소를 찾지 못했어요. 다시 촬영해 주세요.")

            match >= config.autoPassThreshold ->
                Result(Verdict.PASS, match, invalid, "사진 인증 완료")

            else ->
                Result(Verdict.NEEDS_REVIEW, match, invalid,
                    "자동 인증이 애매해 관리자 확인 후 포인트가 지급돼요.")
        }
    }
}
