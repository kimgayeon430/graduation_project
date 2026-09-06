package smu.ai.graduation_project.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 위치 인증(1단계) 판정 규칙 (필수 테스트 1, 2). */
class LocationVerificationTest {

    private val targetLat = 37.60000
    private val targetLon = 126.90000

    // 1. 목표 위치에서 200m 이내이면 위치 인증 성공 (위도 +0.001° ≈ 111m)
    @Test
    fun within200mIsVerified() {
        val result = LocationVerification.verify(
            currentLat = targetLat + 0.001,
            currentLon = targetLon,
            targetLat = targetLat,
            targetLon = targetLon
        )
        assertTrue("거리는 200m 미만이어야 한다: ${result.distanceMeters}", result.distanceMeters < 200.0)
        assertTrue(result.isWithinRadius)
    }

    @Test
    fun exactTargetIsVerified() {
        val result = LocationVerification.verify(targetLat, targetLon, targetLat, targetLon)
        assertTrue(result.isWithinRadius)
    }

    // 2. 200m를 초과하면 인증 실패 (위도 +0.003° ≈ 334m)
    @Test
    fun beyond200mIsRejected() {
        val result = LocationVerification.verify(
            currentLat = targetLat + 0.003,
            currentLon = targetLon,
            targetLat = targetLat,
            targetLon = targetLon
        )
        assertTrue("거리는 200m 초과여야 한다: ${result.distanceMeters}", result.distanceMeters > 200.0)
        assertFalse(result.isWithinRadius)
    }

    @Test
    fun boundaryDistanceEqualToRadiusPasses() {
        assertTrue(LocationVerification.isWithinRadius(200.0, 200.0))
        assertFalse(LocationVerification.isWithinRadius(200.001, 200.0))
    }
}
