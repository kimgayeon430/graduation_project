package smu.ai.graduation_project.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import smu.ai.graduation_project.domain.PhotoVerification
import smu.ai.graduation_project.domain.PhotoVerificationConfig
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * `assets/photo_verifier.onnx` 를 **실제로 추론에 태워** 보는 계측 테스트.
 *
 * 유닛 테스트([smu.ai.graduation_project.domain.PhotoVerificationTest] 등)는 판정 규칙만 다루고
 * 모델은 [FakePhotoVerifier] 로 대체하므로, "모델이 기기에서 실제로 뜨는가" 는 여기서만 검증된다.
 *
 * 모델 로딩이 실패하면 [OnnxPhotoVerifier.classify] 가 조용히 null 을 돌려주고
 * ([PhotoVerificationConfig.passWhenModelUnavailable] = false 이므로) 모든 사진이 관리자 검수로
 * 넘어가 버리는데, 앱은 정상으로 보인다. 그 무증상 고장을 잡는 것이 이 테스트의 목적이다.
 *
 * 사진의 *내용* 이 맞는지(정확도)는 `ml/thresholds.json` 의 오프라인 평가가 담당한다.
 * 여기서는 파이프라인이 끝까지 도는지와 출력 계약만 본다.
 */
@RunWith(AndroidJUnit4::class)
class OnnxPhotoVerifierTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val verifier by lazy { OnnxPhotoVerifier(context) }

    /** 라벨 파일과 판정 규칙이 기대하는 라벨 집합. */
    private val expectedLabels = setOf("투어", "맛집", "체험", "쇼핑", PhotoVerification.INVALID_LABEL)

    /** 단색·도형이 섞인 JPEG 을 만든다. 내용은 무의미하고 디코드·전처리 경로만 태운다. */
    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(120, 160, 90))
        canvas.drawCircle(
            width / 2f, height / 2f, minOf(width, height) / 4f,
            Paint().apply { color = Color.rgb(200, 80, 40) },
        )
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    @Test
    fun modelVersionComesFromAsset() {
        // photo_verifier_version.txt 를 못 읽으면 "onnx-photo-verifier" 로 폴백한다.
        assertEquals("mobilevit-small-fullft-1", verifier.modelVersion)
    }

    @Test
    fun classifyReturnsProbabilityOverEveryLabel() {
        val result = verifier.classify(jpeg(640, 480))

        assertNotNull("모델을 못 불러왔다 (session/labels null 또는 추론 실패)", result)
        val scores = result!!.scores

        assertEquals("라벨 집합이 photo_verifier_labels.json 과 달라졌다", expectedLabels, scores.keys)
        scores.forEach { (label, p) ->
            assertTrue("$label 점수가 0..1 밖: $p", p in 0.0..1.0)
        }
        val sum = scores.values.sum()
        assertTrue("softmax 합이 1 이 아님: $sum", abs(sum - 1.0) < 1e-3)
    }

    @Test
    fun handlesPortraitAndLandscapeAlike() {
        // 짧은 변 288 리사이즈 → 256 center-crop 경로를, 세로/가로/정사각 모두에서 태운다.
        listOf(480 to 640, 640 to 480, 300 to 300).forEach { (w, h) ->
            val result = verifier.classify(jpeg(w, h))
            assertNotNull("${w}x$h 에서 추론 실패", result)
            assertEquals("${w}x$h 라벨 수", expectedLabels.size, result!!.scores.size)
        }
    }

    @Test
    fun undecodableBytesYieldNullInsteadOfCrash() {
        assertNull(verifier.classify(ByteArray(64) { it.toByte() }))
    }

    @Test
    fun capturedEmbeddingIsNormalizedWhenEmbedderBundled() {
        // photo_embedder_int8.onnx 가 assets 에 번들된 경우만 검증한다(옵션 1). 런타임 다운로드
        // 환경에서는 스킵. 임베딩이 나오면 CLIP 512차원·L2 정규화 계약을 지켜야 한다.
        val embedder = OnnxClipPhotoEmbedder(
            context, ModelSource.asset(context, "photo_embedder_int8.onnx", "test"),
        )
        val e = embedder.embed(jpeg(640, 480))
        org.junit.Assume.assumeNotNull(e)
        assertEquals("CLIP ViT-B/32 임베딩 차원", 512, e!!.size)
        val norm = kotlin.math.sqrt(e.fold(0.0) { acc, x -> acc + x.toDouble() * x })
        assertTrue("임베딩이 L2 정규화되지 않음: $norm", abs(norm - 1.0) < 1e-3)
    }

    @Test
    fun verdictIsReachedWithoutTheModelUnavailableFallback() {
        val result = verifier.classify(jpeg(640, 480))
        assertNotNull(result)

        val verdict = PhotoVerification.verify("맛집", result, config = PhotoVerificationConfig.DEFAULT)

        // 모델이 null 이었다면 이 사유 문구로 검수 큐에 실린다. 그 경로를 타지 않아야 한다.
        assertTrue(
            "모델 미탑재 폴백 경로를 탔다: ${verdict.reason}",
            !verdict.reason.contains("자동 인증을 할 수 없어"),
        )
    }
}
