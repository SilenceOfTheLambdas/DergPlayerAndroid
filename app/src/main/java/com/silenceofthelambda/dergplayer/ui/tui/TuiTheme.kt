package com.silenceofthelambda.dergplayer.ui.tui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import android.app.Activity
import androidx.core.view.WindowCompat
import com.silenceofthelambda.dergplayer.R

data class TuiColors(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val scanlineColor: Color = Color.White.copy(alpha = 0.05f)
)

val MatrixColors = TuiColors(
    background = Color(0xFF0D0208),
    surface = Color(0xFF0D0208),
    primary = Color(0xFF00FF41),
    onBackground = Color(0xFF00FF41),
    onSurface = Color(0xFF00FF41)
)

val AmberColors = TuiColors(
    background = Color(0xFF1A1000),
    surface = Color(0xFF1A1000),
    primary = Color(0xFFFFB000),
    onBackground = Color(0xFFFFB000),
    onSurface = Color(0xFFFFB000)
)

val CyberpunkColors = TuiColors(
    background = Color(0xFF020205),
    surface = Color(0xFF020205),
    primary = Color(0xFFFDEE00),
    onBackground = Color(0xFFFDEE00),
    onSurface = Color(0xFFFDEE00),
    scanlineColor = Color(0xFF00FFFF).copy(alpha = 0.05f)
)

private val LocalTuiColors = staticCompositionLocalOf { MatrixColors }

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val fontName = GoogleFont("JetBrains Mono")

val JetBrainsMonoFontFamily = FontFamily(
    Font(googleFont = fontName, fontProvider = provider),
    Font(googleFont = fontName, fontProvider = provider, weight = FontWeight.Bold)
)

object TuiTheme {
    val colors: TuiColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTuiColors.current

    val typography = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "liga, clig"
    )
}

@Composable
fun TuiTheme(
    colors: TuiColors = MatrixColors,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(
        LocalTuiColors provides colors
    ) {
        content()
    }
}
