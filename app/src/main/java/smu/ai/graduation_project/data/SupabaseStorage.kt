package smu.ai.graduation_project.data

import smu.ai.graduation_project.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Supabase Storage 의 public 버킷에 이미지를 올린다. (Firebase Storage 대체)
 *
 * anon(publishable) 키로 REST 업로드하며, 성공하면 파일의 공개 URL 을 돌려준다.
 * 버킷은 Public 이고 `storage.objects` 에 anon INSERT 정책이 설정돼 있어야 한다.
 */
object SupabaseStorage {

    private const val BUCKET = "mission-photos"

    /** local.properties 에 Supabase 설정이 채워져 있는지. */
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    /**
     * 버킷에 바이트를 업로드한다. **네트워크 호출이므로 백그라운드 스레드에서 부르라.**
     *
     * @param objectPath 버킷 내 경로 (예: `"missionId/uid_1699999999.jpg"`)
     * @param bytes 업로드할 파일 내용
     * @return 업로드된 파일의 공개 URL
     * @throws IOException 설정이 없거나 업로드가 실패한 경우
     */
    fun upload(objectPath: String, bytes: ByteArray, contentType: String = "image/jpeg"): String {
        if (!isConfigured) throw IOException("Supabase 설정이 없습니다. local.properties 를 확인하세요.")

        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val conn = (URL("$base/storage/v1/object/$BUCKET/$objectPath").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("x-upsert", "true")
        }
        try {
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Supabase 업로드 실패 ($code): $detail")
            }
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
        return "$base/storage/v1/object/public/$BUCKET/$objectPath"
    }
}
