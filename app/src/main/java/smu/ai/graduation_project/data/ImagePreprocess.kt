package smu.ai.graduation_project.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.math.min

/**
 * HF `preprocessor_config.json` 을 읽어 이미지를 모델 입력 NCHW float 텐서로 바꾼다.
 *
 * `photo_verifier.onnx`(MobileViT, 256·채널반전·정규화 없음)와
 * `photo_embedder.onnx`(CLIP, 224·정규화 있음·RGB 유지)가 전처리만 다르고 경로는 같아,
 * 상수를 json 에서 읽어 두 모델이 이 코드를 공유한다.
 *
 * 학습·export 전처리와 어긋나면 정확도가 조용히 무너지므로 하드코딩하지 않는다.
 */
class ImagePreprocess private constructor(
    /** 짧은 변을 맞출 크기. shortest_edge 가 없으면 height/width. */
    val resizeShortest: Int,
    val cropHeight: Int,
    val cropWidth: Int,
    private val rescale: Float,        // do_rescale 면 rescale_factor, 아니면 1f
    private val mean: FloatArray?,     // do_normalize 면 [r,g,b], 아니면 null
    private val std: FloatArray?,
    private val flipChannelOrder: Boolean, // true 면 RGB → BGR (MobileViT 기본 true)
) {

    /** 촬영본 JPEG 을 EXIF 회전까지 반영해 디코드한다. 실패하면 null. */
    fun decodeUpright(photoBytes: ByteArray): Bitmap? {
        val bitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size) ?: return null
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(photoBytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        }.getOrDefault(bitmap).also { if (it != bitmap) bitmap.recycle() }
    }

    /** 짧은 변 리사이즈 → center-crop → rescale/normalize → (선택) 채널 반전 → NCHW. */
    fun toNchw(src: Bitmap): FloatArray {
        val scale = resizeShortest.toFloat() / min(src.width, src.height)
        val rw = Math.round(src.width * scale)
        val rh = Math.round(src.height * scale)
        val resized = Bitmap.createScaledBitmap(src, rw, rh, true)

        val left = ((rw - cropWidth) / 2).coerceAtLeast(0)
        val top = ((rh - cropHeight) / 2).coerceAtLeast(0)
        val cropW = min(cropWidth, rw)
        val cropH = min(cropHeight, rh)
        val cropped = Bitmap.createBitmap(resized, left, top, cropW, cropH)
        if (resized != src && resized != cropped) resized.recycle()

        val h = cropHeight
        val w = cropWidth
        val pixels = IntArray(cropW * cropH)
        cropped.getPixels(pixels, 0, cropW, 0, 0, cropW, cropH)
        if (cropped != src) cropped.recycle()

        val out = FloatArray(3 * h * w)
        val plane = h * w
        for (y in 0 until h) {
            for (x in 0 until w) {
                val px = if (y < cropH && x < cropW) pixels[y * cropW + x] else 0
                var r = ((px shr 16) and 0xFF) * rescale
                var g = ((px shr 8) and 0xFF) * rescale
                var b = (px and 0xFF) * rescale
                if (mean != null && std != null) {
                    r = (r - mean[0]) / std[0]
                    g = (g - mean[1]) / std[1]
                    b = (b - mean[2]) / std[2]
                }
                val c0: Float; val c1: Float; val c2: Float
                if (flipChannelOrder) { c0 = b; c1 = g; c2 = r } else { c0 = r; c1 = g; c2 = b }
                val idx = y * w + x
                out[idx] = c0
                out[plane + idx] = c1
                out[2 * plane + idx] = c2
            }
        }
        return out
    }

    companion object {

        /** `apple/mobilevit-small` 의 preprocessor_config.json 기본값. */
        val MOBILEVIT_DEFAULT = ImagePreprocess(
            resizeShortest = 288, cropHeight = 256, cropWidth = 256,
            rescale = 1f / 255f, mean = null, std = null, flipChannelOrder = true,
        )

        /** `openai/clip-vit-base-patch32` 의 기본값 (전처리 json 이 없을 때의 안전망). */
        val CLIP_DEFAULT = ImagePreprocess(
            resizeShortest = 224, cropHeight = 224, cropWidth = 224,
            rescale = 1f / 255f,
            mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f),
            std = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f),
            flipChannelOrder = false,
        )

        /** json 문자열에서 파싱한다. 실패/누락 키는 [fallback] 값으로 채운다. */
        fun parse(json: String, fallback: ImagePreprocess = MOBILEVIT_DEFAULT): ImagePreprocess =
            runCatching { parse(JSONObject(json), fallback) }.getOrDefault(fallback)

        fun parse(o: JSONObject, fallback: ImagePreprocess): ImagePreprocess {
            val size = o.optJSONObject("size")
            val shortest = size?.optInt("shortest_edge", 0)?.takeIf { it > 0 }
                ?: size?.optInt("height", 0)?.takeIf { it > 0 }
                ?: fallback.resizeShortest
            val crop = o.optJSONObject("crop_size")
            val cropH = crop?.optInt("height", 0)?.takeIf { it > 0 }
                ?: o.optInt("crop_size", 0).takeIf { it > 0 }
                ?: fallback.cropHeight
            val cropW = crop?.optInt("width", 0)?.takeIf { it > 0 } ?: cropH
            val rescale = if (o.optBoolean("do_rescale", true)) {
                o.optDouble("rescale_factor", 1.0 / 255.0).toFloat()
            } else 1f
            val normalize = o.optBoolean("do_normalize", false)
            val mean = if (normalize) o.optJSONArray("image_mean")?.toFloat3() else null
            val std = if (normalize) o.optJSONArray("image_std")?.toFloat3() else null
            val flip = o.optBoolean("do_flip_channel_order", fallback.flipChannelOrder)
            return ImagePreprocess(shortest, cropH, cropW, rescale, mean, std, flip)
        }

        private fun JSONArray.toFloat3(): FloatArray =
            floatArrayOf(getDouble(0).toFloat(), getDouble(1).toFloat(), getDouble(2).toFloat())
    }
}
