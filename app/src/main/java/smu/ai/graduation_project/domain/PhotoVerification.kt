package smu.ai.graduation_project.domain

import kotlin.math.sqrt

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
 *
 * ### 참조 이미지 유사도 (선택)
 * 카테고리 분류만으로는 "미션 *유형* 에 맞는 사진인가"만 볼 수 있고 "*이* 미션의 대상을
 * 찍었는가"는 못 본다(보고서 6.7). 미션 대표 이미지의 임베딩([referenceEmbeddings])이
 * 주어지면, 촬영본 임베딩([Classification.embedding])과의 코사인 유사도를 **보조 신호**로
 * 결합한다. 유사도만으로 통과/거절을 뒤집지 않고, 한 단계씩만 조정한다(6.7.5).
 *
 * 참조 이미지가 여러 장이면(각도·조명·계절이 다른 사진) [PhotoEmbedding.maxCosineOrNull] 로
 * **최대 유사도**를 취한다 — 한 장만 닮아도 같은 대상으로 인정한다(6.7.6/6.7.8, 오프라인
 * 프로토타입에서 Youden J 0.33 → 0.47 로 분리력 개선 확인).
 */
object PhotoVerification {

    /** 무효(셀카·스크린샷·무관 실내 등) 클래스 라벨. 모델 학습 라벨과 일치해야 한다. */
    const val INVALID_LABEL = "무효"

    enum class Verdict { PASS, NEEDS_REVIEW, REJECT }

    /**
     * [Verdict.REJECT] 일 때 사용자에게 보여줄 안내 문구를 고르기 위한 원인 분류.
     * 화면 표시 문구(다국어)는 UI 레이어(`strings.xml`)에서 맡고, 여기서는 원인만 분류한다.
     */
    enum class RejectReasonCode { INVALID_SUBJECT, CATEGORY_MISMATCH, LOW_CONFIDENCE }

    /** 모델이 낸 라벨별 점수. 합이 1일 필요는 없다. */
    data class Classification(
        val scores: Map<String, Double>,
        /**
         * 촬영본의 이미지 임베딩(L2 정규화 권장). 임베더 모델이 없으면 null.
         * [referenceEmbedding] 과 코사인 유사도를 계산하는 데만 쓴다.
         */
        val embedding: FloatArray? = null,
    ) {
        fun scoreOf(label: String): Double = scores[label] ?: 0.0
        val topLabel: String? get() = scores.maxByOrNull { it.value }?.key

        // data class + FloatArray: equals/hashCode 를 배열 내용 기준으로 직접 구현한다.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Classification) return false
            return scores == other.scores && embedding.contentEqualsNullable(other.embedding)
        }

        override fun hashCode(): Int =
            31 * scores.hashCode() + (embedding?.contentHashCode() ?: 0)
    }

    data class Result(
        val verdict: Verdict,
        /** 미션 카테고리에 대한 모델 점수. */
        val matchScore: Double,
        /** 무효 라벨 점수. */
        val invalidScore: Double,
        /** 참조 이미지와의 코사인 유사도. 계산하지 못했으면 null. */
        val similarity: Double?,
        /** 사용자/관리자에게 보여줄 사유. */
        val reason: String,
        /** [Verdict.REJECT] 일 때만 값이 있다. */
        val rejectReasonCode: RejectReasonCode? = null
    )

    /**
     * @param missionCategory     미션이 기대하는 카테고리 (예: `"맛집"`)
     * @param classification      모델 분류 결과. 모델을 못 불러왔으면 null.
     * @param referenceEmbeddings 미션 대표 이미지(들)의 임베딩. 비어 있으면(대부분의 기존 미션) 유사도 결합을 건너뛴다.
     *                            여러 장이면 최대 유사도를 쓴다.
     */
    fun verify(
        missionCategory: String,
        classification: Classification?,
        referenceEmbeddings: List<FloatArray> = emptyList(),
        config: PhotoVerificationConfig = PhotoVerificationConfig.DEFAULT
    ): Result {
        if (classification == null) {
            return if (config.passWhenModelUnavailable) {
                Result(Verdict.PASS, 0.0, 0.0, null, "자동 인증을 건너뛰고 통과했습니다.")
            } else {
                Result(Verdict.NEEDS_REVIEW, 0.0, 0.0, null,
                    "자동 인증을 할 수 없어 관리자 확인 후 포인트가 지급돼요.")
            }
        }

        val match = classification.scoreOf(missionCategory)
        val invalid = classification.scoreOf(INVALID_LABEL)

        // (1) 무효는 유사도보다 먼저 무조건 차단한다. 참조 이미지를 화면에 띄워 재촬영하는
        //     스푸핑은 유사도가 최대에 가깝게 나오므로, 유사도로는 막을 수 없다(6.7.5-가).
        if (invalid >= config.invalidRejectThreshold) {
            return Result(Verdict.REJECT, match, invalid, null,
                "사진이 미션과 무관해 보여요. 미션 장소·대상을 촬영해 주세요.",
                RejectReasonCode.INVALID_SUBJECT)
        }

        // (2) 카테고리 점수만으로 1차 판정.
        val base = when {
            match < config.hardRejectThreshold -> Verdict.REJECT
            match >= config.autoPassThreshold -> Verdict.PASS
            else -> Verdict.NEEDS_REVIEW
        }
        val baseReasonCode = if (base == Verdict.REJECT) {
            rejectReasonCode(classification, missionCategory, match)
        } else null

        val similarity = PhotoEmbedding.maxCosineOrNull(classification.embedding, referenceEmbeddings)
        if (similarity == null) {
            return Result(base, match, invalid, null, reasonFor(base, missionCategory), baseReasonCode)
        }

        // (3) 유사도를 보조 신호로 결합한다. 한 단계씩만 조정한다(6.7.5).
        val combined = when {
            // 카테고리 점수가 낮아도 대표 이미지와 충분히 닮았으면 즉시 거절하지 않고 검수로 구제.
            base == Verdict.REJECT && similarity >= config.similarityRescueThreshold ->
                Verdict.NEEDS_REVIEW
            // 애매한데 대표 이미지와 매우 닮았으면 통과.
            base == Verdict.NEEDS_REVIEW && similarity >= config.similaritySuspectThreshold ->
                Verdict.PASS
            // 유형은 맞는데 대표 이미지와 안 닮음 → 같은 유형의 다른 대상 촬영 의심, 검수로.
            base == Verdict.PASS && similarity < config.similaritySuspectThreshold ->
                Verdict.NEEDS_REVIEW
            else -> base
        }
        // combined 가 REJECT 로 남는 경우는 base 가 이미 REJECT 였고 유사도 구제도 안 된 경우뿐이다.
        val combinedReasonCode = if (combined == Verdict.REJECT) baseReasonCode else null

        return Result(combined, match, invalid, similarity,
            reasonForCombined(base, combined, missionCategory), combinedReasonCode)
    }

    /**
     * REJECT 원인을 분류한다. 무효 판정([INVALID_LABEL])은 [verify] 에서 먼저 걸러지므로 여기선
     * "다른 카테고리로 더 강하게 분류됐는가"(유형 불일치) vs "전반적으로 애매함"(저신뢰) 만 가른다.
     */
    private fun rejectReasonCode(
        classification: Classification,
        missionCategory: String,
        matchScore: Double
    ): RejectReasonCode {
        val topLabel = classification.topLabel
        return if (topLabel != null && topLabel != missionCategory && topLabel != INVALID_LABEL &&
            classification.scoreOf(topLabel) > matchScore
        ) {
            RejectReasonCode.CATEGORY_MISMATCH
        } else {
            RejectReasonCode.LOW_CONFIDENCE
        }
    }

    private fun reasonFor(verdict: Verdict, category: String): String = when (verdict) {
        Verdict.REJECT ->
            "사진에서 '$category' 미션 요소를 찾지 못했어요. 다시 촬영해 주세요."
        Verdict.PASS -> "사진 인증 완료"
        Verdict.NEEDS_REVIEW ->
            "자동 인증이 애매해 관리자 확인 후 포인트가 지급돼요."
    }

    private fun reasonForCombined(
        base: Verdict,
        combined: Verdict,
        category: String,
    ): String = when {
        base == Verdict.REJECT && combined == Verdict.NEEDS_REVIEW ->
            "'$category' 요소는 약하지만 대표 사진과 닮아 관리자 확인 후 지급돼요."
        base == Verdict.NEEDS_REVIEW && combined == Verdict.PASS ->
            "사진 인증 완료"
        base == Verdict.PASS && combined == Verdict.NEEDS_REVIEW ->
            "미션 대표 장소·대상과 달라 보여요. 관리자 확인 후 포인트가 지급돼요."
        else -> reasonFor(combined, category)
    }
}

/** 이미지 임베딩 유사도 계산. 순수 Kotlin. */
object PhotoEmbedding {

    /**
     * 두 벡터의 코사인 유사도(-1..1). 정규화 여부와 무관하게 각 노름으로 나눈다.
     * 한쪽이 null 이거나 길이가 다르거나 노름이 0이면 null.
     */
    fun cosineOrNull(a: FloatArray?, b: FloatArray?): Double? {
        if (a == null || b == null || a.size != b.size || a.isEmpty()) return null
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        if (na <= 0.0 || nb <= 0.0) return null
        return dot / (sqrt(na) * sqrt(nb))
    }

    /**
     * 참조 이미지가 여러 장일 때 **최대 유사도**를 취한다(6.7.8). 각도·조명·계절이 다른 사진 중
     * 한 장만 닮아도 같은 대상으로 인정한다. [refs] 가 비어 있거나 [captured] 가 null 이면 null.
     */
    fun maxCosineOrNull(captured: FloatArray?, refs: List<FloatArray>): Double? {
        if (captured == null || refs.isEmpty()) return null
        return refs.mapNotNull { cosineOrNull(captured, it) }.maxOrNull()
    }

    /** L2 정규화한 복사본. 노름이 0이면 원본 복사본. */
    fun l2Normalized(v: FloatArray): FloatArray {
        var n = 0.0
        for (x in v) n += x.toDouble() * x
        if (n <= 0.0) return v.copyOf()
        val inv = (1.0 / sqrt(n)).toFloat()
        return FloatArray(v.size) { v[it] * inv }
    }
}

private fun FloatArray?.contentEqualsNullable(other: FloatArray?): Boolean = when {
    this == null && other == null -> true
    this == null || other == null -> false
    else -> this.contentEquals(other)
}
