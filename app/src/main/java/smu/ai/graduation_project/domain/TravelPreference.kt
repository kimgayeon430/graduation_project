package smu.ai.graduation_project.domain

/**
 * 여행 취향 카테고리. 값은 미션 문서의 `category` 문자열과 동일하게 맞춘다.
 * 순수 Kotlin (Firebase 비의존).
 */
object TravelPreference {

    const val TOUR = "투어"
    const val FOOD = "맛집"
    const val EXPERIENCE = "체험"
    const val SHOPPING = "쇼핑"

    /** 취향 선택 화면에 노출하는 순서. */
    val ALL: List<String> = listOf(TOUR, FOOD, EXPERIENCE, SHOPPING)

    /** 최소 1개 이상 선택해야 완료할 수 있다. */
    fun canComplete(selected: Collection<String>): Boolean = selected.isNotEmpty()

    /** 저장용 정규화: 알려진 카테고리만 남기고 [ALL] 순서로 정렬, 중복 제거. */
    fun normalize(selected: Collection<String>): List<String> =
        ALL.filter { it in selected }
}
