package smu.ai.graduation_project.domain

import java.util.Calendar

/**
 * "이번 주" 경계를 기기 로컬 시각 기준 월요일 0시로 정의한다. ISO 주차 대신 이 방식을 쓰는 이유는
 * 연도 경계의 주차 계산 미묘함을 피하기 위해서다 — [smu.ai.graduation_project.data.MissionRewardCounters]
 * 의 주간 배지 카운터, 사진 인증 완료 후 "이번 주 N/5" 표시, 홈 화면 주간 진행률이 모두 같은 경계를 공유한다.
 */
object WeekBoundary {
    fun startOfThisWeekMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
