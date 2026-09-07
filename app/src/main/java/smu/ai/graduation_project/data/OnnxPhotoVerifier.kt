package smu.ai.graduation_project.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
 *  - `photo_verifier_preprocessor.json`  : HF `preprocessor_config.json` — 전처리 상수(size/crop/rescale/normalize/flip)
 *
 * **asset 이 하나라도 없거나 로드/추론이 실패하면 [classify] 는 null 을 돌려준다.**
 * 이때 미션 완료 흐름은 [PhotoVerification] + [PhotoVerificationConfig] 규칙에 맡긴다. (앱은 그대로 동작)
 *
 * 전처리는 하드코딩하지 않고 `photo_verifier_preprocessor.json` 에서 읽어 export 결과와 자동으로 맞춘다.
 * (누락된 키는 MobileViT 기본값으로 대체)
 */
class OnnxPhotoVerifier(
    context: Context,
    modelAsset: String = "photo_verifier.onnx",
    labelsAsset: String = "photo_verifier_labels.json",
    preprocessorAsset: String = "photo_verifier_preprocessor.json",
) : PhotoVerifier {

    private data class Preprocess(
        /** 짧은 변을 맞출 크기. shortest_edge 가 없으면 height/width. */
        val resizeShortest: Int,
        val cropHeight: Int,
        val cropWidth: Int,
        val rescale: Float,          // do_rescale 면 rescale_factor, 아니면 1f
        val mean: FloatArray?,       // do_normalize 면 [r,g,b], 아니면 null
        val std: FloatArray?,
        val flipChannelOrder: Boolean, // true 면 RGB → BGR (MobileViT 기본 true)
    )

    private val env: OrtEnvironment? by lazy { runCatching { OrtEnvironment.getEnvironment() }.getOrNull() }

    private val session: OrtSession? by lazy {
        val e = env ?: return@lazy null
        runCatching {
            val bytes = context.assets.open(modelAsset).use { it.readBytes() }
            e.createSession(bytes, OrtSession.SessionOptions())
        }.onFailure { Log.i(TAG, "모델 미탑재/로드 실패 ($modelAsset): ${it.message}") }.getOrNull()
    }

    private val labels: List<String>? by lazy {
        runCatching {
            val json = context.assets.open(labelsAsset).use { it.reader().readText() }
            val arr = JSONObject(json).getJSONArray("labels")
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrNull()
    }

    private val preprocess: Preprocess by lazy {
        runCatching {
            val json = context.assets.open(preprocessorAsset).use { it.reader().readText() }
            parsePreprocess(JSONObject(json))
        }.getOrElse { MOBILEVIT_DEFAULT }
    }

    private val modelVersionValue: String by lazy {
        runCatching {
            context.assets.open("photo_verifier_version.txt").use { it.reader().readText() }.trim()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "onnx-photo-verifier"
    }

    override val modelVersion: String get() = modelVersionValue

    override fun classify(photoBytes: ByteArray): PhotoVerification.Classification? {
        val e = env ?: return null
        val s = session ?: return null
        val labelNames = labels ?: return null

        return runCatching {
            val bitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
                ?: return@runCatching null
            val input = preprocessToNchw(bitmap)
            bitmap.recycle()

            val shape = longArrayOf(1, 3, preprocess.cropHeight.toLong(), preprocess.cropWidth.toLong())
            val inputName = s.inputNames.first()
            OnnxTensor.createTensor(e, FloatBuffer.wrap(input), shape).use { tensor ->
                s.run(mapOf(inputName to tensor)).use { result ->
                    val raw = result[0].value
                    val logits = (raw as Array<*>)[0] as FloatArray
                    val probs = softmax(logits)
                    val n = min(labelNames.size, probs.size)
                    PhotoVerification.Classification(
                        (0 until n).associate { labelNames[it] to probs[it].toDouble() }
                    )
                }
            }
        }.onFailure { Log.w(TAG, "추론 실패: ${it.message}", it) }.getOrNull()
    }

    // ---- 전처리 -----------------------------------------------------------

    private fun preprocessToNchw(src: Bitmap): FloatArray {
        val p = preprocess
        // 1) 짧은 변을 resizeShortest 로
        val scale = p.resizeShortest.toFloat() / min(src.width, src.height)
        val rw = Math.round(src.width * scale)
        val rh = Math.round(src.height * scale)
        val resized = Bitmap.createScaledBitmap(src, rw, rh, true)
        // 2) 가운데를 cropWidth x cropHeight 로
        val left = ((rw - p.cropWidth) / 2).coerceAtLeast(0)
        val top = ((rh - p.cropHeight) / 2).coerceAtLeast(0)
        val cropW = min(p.cropWidth, rw)
        val cropH = min(p.cropHeight, rh)
        val cropped = Bitmap.createBitmap(resized, left, top, cropW, cropH)
        if (resized != src && resized != cropped) resized.recycle()

        val h = p.cropHeight
        val w = p.cropWidth
        val pixels = IntArray(cropW * cropH)
        cropped.getPixels(pixels, 0, cropW, 0, 0, cropW, cropH)
        if (cropped != src) cropped.recycle()

        val out = FloatArray(3 * h * w)
        val plane = h * w
        for (y in 0 until h) {
            for (x in 0 until w) {
                val px = if (y < cropH && x < cropW) pixels[y * cropW + x] else 0
                var r = ((px shr 16) and 0xFF) * p.rescale
                var g = ((px shr 8) and 0xFF) * p.rescale
                var b = (px and 0xFF) * p.rescale
                if (p.mean != null && p.std != null) {
                    r = (r - p.mean[0]) / p.std[0]
                    g = (g - p.mean[1]) / p.std[1]
                    b = (b - p.mean[2]) / p.std[2]
                }
                val c0: Float; val c1: Float; val c2: Float
                if (p.flipChannelOrder) { c0 = b; c1 = g; c2 = r } else { c0 = r; c1 = g; c2 = b }
                val idx = y * w + x
                out[idx] = c0
                out[plane + idx] = c1
                out[2 * plane + idx] = c2
            }
        }
        return out
    }

    private fun parsePreprocess(o: JSONObject): Preprocess {
        val size = o.optJSONObject("size")
        val shortest = size?.optInt("shortest_edge", 0)?.takeIf { it > 0 }
            ?: size?.optInt("height", 0)?.takeIf { it > 0 }
            ?: MOBILEVIT_DEFAULT.resizeShortest
        val crop = o.optJSONObject("crop_size")
        val cropH = crop?.optInt("height", 0)?.takeIf { it > 0 }
            ?: o.optInt("crop_size", 0).takeIf { it > 0 }
            ?: MOBILEVIT_DEFAULT.cropHeight
        val cropW = crop?.optInt("width", 0)?.takeIf { it > 0 } ?: cropH
        val rescale = if (o.optBoolean("do_rescale", true)) {
            o.optDouble("rescale_factor", 1.0 / 255.0).toFloat()
        } else 1f
        val normalize = o.optBoolean("do_normalize", false)
        val mean = if (normalize) o.optJSONArray("image_mean")?.let { floatArrayOf(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat()) } else null
        val std = if (normalize) o.optJSONArray("image_std")?.let { floatArrayOf(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat()) } else null
        val flip = o.optBoolean("do_flip_channel_order", true)
        return Preprocess(shortest, cropH, cropW, rescale, mean, std, flip)
    }

    private fun softmax(v: FloatArray): FloatArray {
        val maxV = v.maxOrNull() ?: 0f
        val exps = FloatArray(v.size) { exp((v[it] - maxV).toDouble()).toFloat() }
        val sum = exps.sum().takeIf { it > 0f } ?: 1f
        return FloatArray(v.size) { exps[it] / sum }
    }

    companion object {
        private const val TAG = "OnnxPhotoVerifier"

        /** `apple/mobilevit-small` 의 preprocessor_config.json 기본값. */
        private val MOBILEVIT_DEFAULT = Preprocess(
            resizeShortest = 288,
            cropHeight = 256,
            cropWidth = 256,
            rescale = 1f / 255f,
            mean = null,
            std = null,
            flipChannelOrder = true,
        )
    }
}
