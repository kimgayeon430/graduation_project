package smu.ai.graduation_project.data

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import smu.ai.graduation_project.domain.PhotoEmbedding
import java.nio.FloatBuffer

/**
 * [PhotoEmbedder] 의 온디바이스 구현. CLIP 이미지 인코더 ONNX([ModelSource])로 임베딩을 낸다.
 *
 * 모델은 크므로([PhotoEmbedderAssets], int8 ≈ 89MB) [ModelSource.cachedDownload] 로 받아
 * `filesDir` 에 캐시한다. 아직 못 받았으면 [embed] 가 null 을 돌려주고, 그 미션은
 * 유사도 결합 없이 카테고리 규칙만으로 판정된다.
 *
 * 전처리는 `assets/photo_embedder_preprocessor.json`(CLIP: 224 center-crop·mean/std 정규화·
 * 채널 반전 없음)을 [ImagePreprocess] 로 읽는다. MobileViT 분류기와 상수만 다르고 코드는 공유.
 */
class OnnxClipPhotoEmbedder(
    context: Context,
    private val source: ModelSource,
    preprocessorAsset: String = PhotoEmbedderAssets.PREPROCESSOR_ASSET,
) : PhotoEmbedder {

    private val appContext = context.applicationContext

    override val embedderVersion: String get() = source.version

    private val env: OrtEnvironment? by lazy {
        runCatching { OrtEnvironment.getEnvironment() }.getOrNull()
    }

    @Volatile private var sessionLoaded = false
    @Volatile private var sessionValue: OrtSession? = null

    private fun session(): OrtSession? {
        if (sessionLoaded) return sessionValue
        synchronized(this) {
            if (sessionLoaded) return sessionValue
            val e = env
            sessionValue = if (e == null) null else runCatching {
                source.open()?.use { it.readBytes() }?.let { bytes ->
                    e.createSession(bytes, OrtSession.SessionOptions())
                }
            }.onFailure { Log.i(TAG, "임베더 세션 생성 실패: ${it.message}") }.getOrNull()
            sessionLoaded = sessionValue != null // 실패 시 다음 prefetch 후 재시도 여지를 남긴다
            return sessionValue
        }
    }

    private val preprocess: ImagePreprocess by lazy {
        runCatching {
            appContext.assets.open(preprocessorAsset).use { it.reader().readText() }
        }.map { ImagePreprocess.parse(it, ImagePreprocess.CLIP_DEFAULT) }
            .getOrDefault(ImagePreprocess.CLIP_DEFAULT)
    }

    override fun prefetch() {
        runCatching { source.prefetch() }
    }

    override fun embed(photoBytes: ByteArray): FloatArray? {
        val e = env ?: return null
        val s = session() ?: return null
        return runCatching {
            val bitmap = preprocess.decodeUpright(photoBytes) ?: return@runCatching null
            val input = preprocess.toNchw(bitmap)
            bitmap.recycle()

            val shape = longArrayOf(
                1, 3, preprocess.cropHeight.toLong(), preprocess.cropWidth.toLong()
            )
            val inputName = s.inputNames.first()
            OnnxTensor.createTensor(e, FloatBuffer.wrap(input), shape).use { tensor ->
                s.run(mapOf(inputName to tensor)).use { result ->
                    val vec = (result[0].value as Array<*>)[0] as FloatArray
                    PhotoEmbedding.l2Normalized(vec)
                }
            }
        }.onFailure { Log.w(TAG, "임베딩 추론 실패: ${it.message}", it) }.getOrNull()
    }

    companion object {
        private const val TAG = "OnnxClipPhotoEmbedder"
    }
}
