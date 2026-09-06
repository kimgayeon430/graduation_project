package smu.ai.graduation_project.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Firebase / Android 프레임워크에 의존하지 않는 순수 거리 계산.
 * (프로덕션에서 `android.location.Location.distanceBetween` 대신 사용해 단위 테스트가 가능하도록 분리)
 */
object GeoDistance {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** 두 위경도 좌표 사이의 거리(미터). Haversine 공식. */
    fun meters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
