package smu.ai.graduation_project.data

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import smu.ai.graduation_project.domain.PhotoVerification
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.min

/**
 * [PhotoVerifier] 의 온디바이스 구현. `assets/` 의 ONNX 이미지 분류 모델로 촬영본을 라벨별 점수로 분류한다.
 *
 * 필요한 asset (모두 `ml/export_onnx.py` 산출물):
 *  - `photo_verifier.onnx`               : 이미지 분류 모델 (int8 양자화본을 이 이름으로 넣어도 됨)
 *  - `photo_verifier_labels.json`        : `{"labels": [...]}` — 모델 출력 인덱스 순서의 라벨
 *  - `photo_verifier_preprocessor.json`  : HF `preprocessor_config.json` — 전처리 상수
 *
 * **asset 이 하나라도 없거나 로드/추론이 실패하면 [classify] 는 null 을 돌려준다.**
 * 이때 미션 완료 흐름은 [PhotoVerification] + [PhotoVerificationConfig] 규칙에 맡긴다.
 *
 * [embedder] 가 주어지면 같은 촬영본의 이미지 임베딩도 함께 담아, 미션 대표 이미지와의
 * 코사인 유사도를 판정에 보조 신호로 쓸 수 있게 한다(보고서 6.7). 임베더가 없거나 실패하면
 * 임베딩만 null 이고 분류 점수는 그대로다.
 */
class OnnxPhotoVerifier(
    context: Context,
    modelAsset: String = "photo_verifier.onnx",
    labelsAsset: String = "photo_verifier_labels.json",
    preprocessorAsset: String = "photo_verifier_preprocessor.json",
    private val embedder: PhotoEmbedder? = null,
) : PhotoVerifier {

    private val appContext = context.applicationContext

    /** 프로덕션 배선: 분류기 + CLIP 임베더(런타임 다운로드). */
    constructor(context: Context) : this(
        context,
        embedder = OnnxClipPhotoEmbedder(
            context.applicationContext,
            PhotoEmbedderAssets.source(context.applicationContext),
        ),
    )

    private val env: OrtEnvironment? by lazy { runCatching { OrtEnvironment.getEnvironment() }.getOrNull() }

    private val session: OrtSession? by lazy {
        val e = env ?: return@lazy null
        runCatching {
            val bytes = appContext.assets.open(modelAsset).use { it.readBytes() }
            e.createSession(bytes, OrtSession.SessionOptions())
        }.onFailure { Log.i(TAG, "모델 미탑재/로드 실패 ($modelAsset): ${it.message}") }.getOrNull()
    }

    private val labels: List<String>? by lazy {
        runCatching {
            val json = appContext.assets.open(labelsAsset).use { it.reader().readText() }
            val arr = JSONObject(json).getJSONArray("labels")
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrNull()
    }

    private val preprocess: ImagePreprocess by lazy {
        runCatching {
            appContext.assets.open(preprocessorAsset).use { it.reader().readText() }
        }.map { ImagePreprocess.parse(it, ImagePreprocess.MOBILEVIT_DEFAULT) }
            .getOrDefault(ImagePreprocess.MOBILEVIT_DEFAULT)
    }

    private val modelVersionValue: String by lazy {
        runCatching {
            appContext.assets.open("photo_verifier_version.txt").use { it.reader().readText() }.trim()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "onnx-photo-verifier"
    }

    override val modelVersion: String get() = modelVersionValue

    /** 첫 [classify] 가 임베더 다운로드에 막히지 않도록 화면 진입 시 호출한다. */
    override fun prefetch() {
        runCatching { embedder?.prefetch() }
    }

    override fun classify(photoBytes: ByteArray): PhotoVerification.Classification? {
        val e = env ?: return null
        val s = session ?: return null
        val labelNames = labels ?: return null

        return runCatching {
            val bitmap = preprocess.decodeUpright(photoBytes) ?: return@runCatching null
            val input = preprocess.toNchw(bitmap)
            bitmap.recycle()

            val shape = longArrayOf(1, 3, preprocess.cropHeight.toLong(), preprocess.cropWidth.toLong())
            val inputName = s.inputNames.first()
            val scores = OnnxTensor.createTensor(e, FloatBuffer.wrap(input), shape).use { tensor ->
                s.run(mapOf(inputName to tensor)).use { result ->
                    val logits = (result[0].value as Array<*>)[0] as FloatArray
                    val probs = softmax(logits)
                    val n = min(labelNames.size, probs.size)
                    (0 until n).associate { labelNames[it] to probs[it].toDouble() }
                }
            }
            PhotoVerification.Classification(
                scores = scores,
                embedding = runCatching { embedder?.embed(photoBytes) }.getOrNull(),
            )
        }.onFailure { Log.w(TAG, "추론 실패: ${it.message}", it) }.getOrNull()
    }

    private fun softmax(v: FloatArray): FloatArray {
        val maxV = v.maxOrNull() ?: 0f
        val exps = FloatArray(v.size) { exp((v[it] - maxV).toDouble()).toFloat() }
        val sum = exps.sum().takeIf { it > 0f } ?: 1f
        return FloatArray(v.size) { exps[it] / sum }
    }

    companion object {
        private const val TAG = "OnnxPhotoVerifier"
    }
}
