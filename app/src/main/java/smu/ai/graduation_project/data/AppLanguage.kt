package smu.ai.graduation_project.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Locale

/**
 * 앱 표시 언어. 두 가지에 쓰인다.
 *  1) 미션 콘텐츠(제목/설명): Firestore에 `title`/`desc`(한국어)와 `titleEn`/`descEn`(영어)로 저장되고,
 *     [localizedString] 이 이 값에 따라 고른다.
 *  2) 앱 UI 문구(핵심 사용자 화면): `strings.xml`(기본, 한국어) / `values-en/strings.xml`(영어) 리소스가,
 *     [MainActivity.attachBaseContext] 가 [wrapWithStoredLocale] 로 감싼 Configuration 에 따라 전환된다.
 *     `AppCompatDelegate.setApplicationLocales` 는 이 앱처럼 `ComponentActivity`(AppCompatActivity 아님)에서는
 *     리소스가 즉시 갱신되지 않아(자동 재생성 훅이 없음) 쓰지 않는다 — 수동 Locale/Configuration 방식이 더 확실하다.
 *     관리자 화면은 대상 밖 — 한국어 문자열이 그대로 남는다.
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

/** 앱 전역 언어 설정. SharedPreferences 에 저장, [MainActivity.attachBaseContext] 가 읽어 반영한다. */
object LanguagePreference {
    var current by mutableStateOf(AppLanguage.KOREAN)
        private set

    fun storedLanguage(context: Context): AppLanguage {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppLanguage.fromCode(prefs.getString(KEY_LANGUAGE, null))
    }

    /** 액티비티 생성 시 한 번 호출해 UI(Compose) 쪽 상태를 저장된 값과 맞춘다. */
    fun syncCurrent(context: Context) {
        current = storedLanguage(context)
    }

    /** 저장 후 액티비티를 재생성해 [wrapWithStoredLocale] 이 새 로케일로 다시 감싸지게 한다. */
    fun set(context: Context, language: AppLanguage) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.code)
            .apply()
        current = language
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
            ?.recreate()
    }
}

/** [MainActivity.attachBaseContext] 에서 저장된 언어로 Configuration 을 감싼 Context 를 만든다. */
fun Context.wrapWithStoredLocale(): Context {
    val locale = Locale(LanguagePreference.storedLanguage(this).code)
    Locale.setDefault(locale)
    val config = resources.configuration
    config.setLocale(locale)
    return createConfigurationContext(config)
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
