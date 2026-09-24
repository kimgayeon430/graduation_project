package smu.ai.graduation_project.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.DocumentSnapshot

/**
 * 앱 표시 언어. 미션 콘텐츠(제목/설명)는 Firestore에 `title`/`desc`(한국어)와
 * `titleEn`/`descEn`(영어, 선택)로 저장되고, [localizedString] 이 이 값에 따라 고른다.
 * 앱 UI 문구(버튼·라벨 등)는 대상이 아니다 — 별도 리소스화 작업이 필요하다.
 */
enum class AppLanguage(val code: String, val label: String) {
    KOREAN("ko", "한국어"),
    ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: KOREAN
    }
}

private const val PREFS_NAME = "app_settings"
private const val KEY_LANGUAGE = "language"

/** 앱 전역 언어 설정. [init] 을 앱 시작 시 한 번 호출해 저장된 값을 불러온다. */
object LanguagePreference {
    var current by mutableStateOf(AppLanguage.KOREAN)
        private set

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        current = AppLanguage.fromCode(prefs.getString(KEY_LANGUAGE, null))
    }

    fun set(context: Context, language: AppLanguage) {
        current = language
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.code)
            .apply()
    }
}

/**
 * `field`(한국어) 와 `${field}En`(영어) 중 현재 언어에 맞는 값을 읽는다.
 * 영어 필드가 비어 있으면 한국어로 안전하게 폴백한다(번역 누락 미션이 빈 텍스트로 보이지 않도록).
 */
fun DocumentSnapshot.localizedString(field: String, language: AppLanguage, fallback: String = ""): String {
    if (language == AppLanguage.ENGLISH) {
        getString("${field}En")?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return getString(field) ?: fallback
}
