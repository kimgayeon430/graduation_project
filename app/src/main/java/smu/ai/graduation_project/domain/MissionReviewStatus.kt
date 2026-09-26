package smu.ai.graduation_project.domain

/**
 * 사용자 제안 미션의 검수 상태. 순수 Kotlin (Firebase 비의존).
 *
 * 기존 관리자 생성 미션에는 `reviewStatus` 필드 자체가 없다 — 그 문서들은 이 값이 null/빈 문자열로
 * 읽히므로, 여기서는 그 경우를 [APPROVED]와 동일하게 취급해 하위 호환을 유지한다.
 */
object MissionReviewStatus {
    const val PENDING = "pending"
    const val APPROVED = "approved"
    const val CHANGES_REQUESTED = "changes_requested"
    const val REJECTED = "rejected"

    /** 미션 목록/지도/추천 등 공개 노출 대상인지. 기존 문서(필드 없음)도 공개 대상이다. */
    fun isPubliclyVisible(status: String?): Boolean =
        status.isNullOrEmpty() || status == APPROVED

    /** 제안자가 수정 후 재제출할 수 있는 상태인지. 반려(rejected)는 재제출 대상이 아니다. */
    fun canResubmit(status: String?): Boolean = status == CHANGES_REQUESTED
}
