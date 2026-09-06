package smu.ai.graduation_project.domain

/** 위치 인증(1단계) 판정 규칙. 순수 Kotlin. */
object LocationVerification {

    const val DEFAULT_ALLOWED_RADIUS_METERS = 200.0

    data class Result(
        val distanceMeters: Double,
        val isWithinRadius: Boolean
    )

    /** 거리만 주어졌을 때 허용 반경 이내인지. 경계값(== 반경)은 성공으로 본다. */
    fun isWithinRadius(
        distanceMeters: Double,
        allowedRadiusMeters: Double = DEFAULT_ALLOWED_RADIUS_METERS
    ): Boolean = distanceMeters <= allowedRadiusMeters

    /** 현재 좌표가 목표 좌표의 허용 반경 이내인지 판정. */
    fun verify(
        currentLat: Double,
        currentLon: Double,
        targetLat: Double,
        targetLon: Double,
        allowedRadiusMeters: Double = DEFAULT_ALLOWED_RADIUS_METERS
    ): Result {
        val distance = GeoDistance.meters(currentLat, currentLon, targetLat, targetLon)
        return Result(distance, distance <= allowedRadiusMeters)
    }
}
