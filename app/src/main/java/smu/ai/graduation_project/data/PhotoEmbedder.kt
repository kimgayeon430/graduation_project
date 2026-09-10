package smu.ai.graduation_project.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 촬영본을 이미지 임베딩 벡터로 바꾼다. 미션 대표 이미지 임베딩과의 코사인 유사도를
 * [smu.ai.graduation_project.domain.PhotoVerification] 이 보조 신호로 쓴다(보고서 6.7).
 *
 * 분류기([PhotoVerifier])와 분리한 이유: 분류 헤드로 파인튜닝된 pooled feature 는
 * "같은 카테고리 안 대상 구분" 분리도(AUC)가 ~0.60 에 그쳤고(`ml/embedding_separability.py`),
 * CLIP 이미지 인코더가 ~0.76 이라 별도 모델을 쓴다. CLIP 은 커서(int8 ≈ 89MB) 기본적으로
 * assets 번들 대신 런타임 다운로드([ModelSource.cachedDownload])로 받는다.
 */
interface PhotoEmbedder {

    /** 임베더 모델 식별자. `user_missions` 기록·재보정 추적용. */
    val embedderVersion: String

    /**
     * 촬영본을 L2 정규화된 임베딩으로 바꾼다.
     * **모델 로드/추론은 무거우므로 백그라운드 스레드에서 부르라.**
     * 모델을 아직 못 받았거나 실패하면 null (→ 유사도 결합을 건너뛰고 카테고리 규칙만 적용).
     */
    fun embed(photoBytes: ByteArray): FloatArray?

    /**
     * 모델을 미리 준비한다(다운로드 등). 첫 [embed] 호출이 네트워크에 막히지 않도록
     * 화면 진입 시점에 백그라운드로 부르면 좋다. 이미 준비됐으면 즉시 반환한다.
     */
    fun prefetch() {}
}

/** 테스트·프리뷰용. 고정 임베딩을 돌려준다. */
class FakePhotoEmbedder(
    private val fixed: FloatArray? = null,
    override val embedderVersion: String = "fake-embed-0",
) : PhotoEmbedder {
    override fun embed(photoBytes: ByteArray): FloatArray? = fixed
}

/**
 * ONNX 모델 바이트의 출처. assets 번들과 런타임 다운로드를 같은 인터페이스로 다룬다.
 */
interface ModelSource {

    val version: String

    /** 모델 파일 스트림. 아직 없으면(다운로드 미완/실패) null. */
    fun open(): InputStream?

    /** 다운로드형이면 여기서 받아 캐시한다. 번들형이면 아무것도 안 한다. */
    fun prefetch() {}

    companion object {
        private const val TAG = "ModelSource"

        /** `assets/<asset>` 를 그대로 읽는다 (APK 에 번들한 경우). */
        fun asset(context: Context, asset: String, modelVersion: String): ModelSource =
            object : ModelSource {
                override val version = modelVersion
                override fun open(): InputStream? =
                    runCatching { context.assets.open(asset) }.getOrNull()
            }

        /**
         * [url] 에서 받아 `filesDir/models/<fileName>` 에 캐시한다. 캐시된 버전이
         * [version] 과 다르면 다시 받는다. 네트워크·저장 실패는 삼키고 open() 이 null.
         */
        fun cachedDownload(
            context: Context,
            url: String,
            modelVersion: String,
            fileName: String,
        ): ModelSource = object : ModelSource {
            override val version = modelVersion
            private val dir = File(context.filesDir, "models").apply { mkdirs() }
            private val modelFile = File(dir, fileName)
            private val versionFile = File(dir, "$fileName.version")

            private fun isFresh(): Boolean =
                modelFile.exists() && modelFile.length() > 0 &&
                    runCatching { versionFile.readText().trim() }.getOrNull() == version

            override fun open(): InputStream? =
                if (isFresh()) modelFile.inputStream() else null

            @Synchronized
            override fun prefetch() {
                if (isFresh() || url.isBlank()) return
                runCatching {
                    val tmp = File(dir, "$fileName.tmp")
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 60_000
                    try {
                        if (conn.responseCode in 200..299) {
                            conn.inputStream.use { input ->
                                tmp.outputStream().use { input.copyTo(it) }
                            }
                            if (tmp.renameTo(modelFile) ||
                                tmp.copyRecursively(modelFile, overwrite = true)
                            ) {
                                versionFile.writeText(version)
                                tmp.delete()
                                Log.i(TAG, "임베더 캐시 완료 (${modelFile.length()} B, v$version)")
                            }
                        } else {
                            Log.i(TAG, "임베더 다운로드 실패 (${conn.responseCode}) $url")
                        }
                    } finally {
                        conn.disconnect()
                    }
                }.onFailure { Log.i(TAG, "임베더 준비 실패: ${it.message}") }
            }
        }
    }
}

/**
 * 프로덕션 임베더 소스. CLIP int8 모델(≈89MB)은 APK 에 번들하지 않고 HuggingFace Hub 에서
 * 받아 `filesDir` 에 캐시한다. (데이터셋과 같은 계정 `kimgayeon430`. Supabase 무료 플랜은
 * 파일당 50MB 상한이라 부적합.) 다운로드 실패 시 유사도 신호만 비활성, 앱은 그대로 동작.
 */
object PhotoEmbedderAssets {

    /** `ml/export_clip_image_encoder.py` 의 photo_embedder_version.txt 와 맞춘다. */
    const val VERSION = "clip-vit-base-patch32"
    private const val MODEL_FILE = "photo_embedder_int8.onnx"

    /** 전처리 상수는 작아서 APK 에 번들한다. */
    const val PREPROCESSOR_ASSET = "photo_embedder_preprocessor.json"

    /** HF Hub 의 공개 모델 repo. `resolve/main` 은 CDN 으로 302 리다이렉트되며 인증이 필요 없다. */
    private const val DOWNLOAD_URL =
        "https://huggingface.co/kimgayeon430/travel-mission-photo-embedder/resolve/main/photo_embedder_int8.onnx"

    /**
     * `assets/photo_embedder_int8.onnx` 가 번들돼 있으면 그걸 쓰고(옵션 1),
     * 없으면 HF Hub 에서 받아 캐시한다(옵션 3, 기본). APK 크기 vs 첫 사용 다운로드 트레이드오프.
     */
    fun source(context: Context): ModelSource {
        val bundled = runCatching { context.assets.open(MODEL_FILE).close(); true }.getOrDefault(false)
        return if (bundled) ModelSource.asset(context, MODEL_FILE, VERSION)
        else ModelSource.cachedDownload(context, DOWNLOAD_URL, VERSION, MODEL_FILE)
    }
}
