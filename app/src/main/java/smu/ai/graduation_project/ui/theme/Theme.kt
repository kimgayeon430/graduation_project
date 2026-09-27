package smu.ai.graduation_project.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = MainPurple,
    onPrimary = Color.White,
    primaryContainer = LightPurple,
    onPrimaryContainer = MainPurple,
    secondary = Orange,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = CardGray,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSoft,
    error = DangerRed
)

@Composable
fun Graduation_projectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 브랜드 보라색을 항상 유지하기 위해 기본은 false — true면 Android 12+ 기기에서 배경화면 색상에
    // 따라 primary 등이 바뀌어(Material You) 브랜드 아이덴티티가 흔들린다.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}