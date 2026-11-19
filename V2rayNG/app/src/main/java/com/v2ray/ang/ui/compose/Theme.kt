package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Freedom VPN: palette of the web cabinet (cabinet.topfreedomhub.com): champagne background,
// navy accent in light mode; slate background, light-blue accent in dark mode.
private val LightColor = lightColorScheme(
    primary = Color(0xFF073C92), // accent navy
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9EFFA), // accent-100
    onPrimaryContainer = Color(0xFF073C92),
    secondary = Color(0xFF746343), // champagne-600
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFCF4E6), // champagne-100
    onSecondaryContainer = Color(0xFF1F1A12),
    tertiary = Color(0xFF15803D), // success
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE7F7ED),
    onTertiaryContainer = Color(0xFF0A3D1D),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFCF3F3),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF7E7CE), // champagne-200
    onBackground = Color(0xFF1F1A12), // champagne-900
    surface = Color(0xFFF7E7CE),
    onSurface = Color(0xFF1F1A12),
    surfaceVariant = Color(0xFFFCF4E6),
    onSurfaceVariant = Color(0xFF746343),
    outline = Color(0xFFC6B598), // champagne-400
    outlineVariant = Color(0xFFDFCEB3), // champagne-300
    inverseSurface = Color(0xFF1F1A12),
    inverseOnSurface = Color(0xFFFEF9F0),
    inversePrimary = Color(0xFF72A5F8),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF073C92),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF4E6),
    surfaceContainer = Color(0xFFFEF9F0), // champagne-50: cards
    surfaceContainerHigh = Color(0xFFFEF9F0),
    surfaceContainerHighest = Color(0xFFFFFFFF),
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFF72A5F8), // accent-400
    onPrimary = Color(0xFF0A1A3A),
    primaryContainer = Color(0xFF0950C3), // accent-700
    onPrimaryContainer = Color(0xFFE9EFFA),
    secondary = Color(0xFFDFCEB3), // champagne-300
    onSecondary = Color(0xFF3F3624),
    secondaryContainer = Color(0xFF2A3346),
    onSecondaryContainer = Color(0xFFF1F5F9),
    tertiary = Color(0xFF4ADE80),
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFDCFCE7),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF7F1D1D),
    onError = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF0C111F), // dark-900
    onBackground = Color(0xFFF1F5F9), // dark-50
    surface = Color(0xFF0C111F),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1A2236),
    onSurfaceVariant = Color(0xFF94A3B8), // dark-400
    outline = Color(0xFF7C8797), // dark-500
    outlineVariant = Color(0xFF2A3346), // dark-700
    inverseSurface = Color(0xFFF1F5F9),
    inverseOnSurface = Color(0xFF0F172A),
    inversePrimary = Color(0xFF073C92),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF72A5F8),
    surfaceContainerLowest = Color(0xFF0A0F1A), // dark-950
    surfaceContainerLow = Color(0xFF0D1322), // dark-850
    surfaceContainer = Color(0xFF0F172A), // dark-800: cards
    surfaceContainerHigh = Color(0xFF151E33),
    surfaceContainerHighest = Color(0xFF1D2740),
)

/** Freedom VPN: Manrope, the web cabinet's font (OFL, res/font). */
private val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

private fun Typography.withFont(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
    headlineMedium = headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold),
    headlineSmall = headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.Bold),
    titleLarge = titleLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    titleMedium = titleMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

private val FreedomTypography = Typography().withFont(Manrope)

/** Rounded corners like the cabinet's cards (16 / 20 dp). */
private val FreedomShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Semantic Colors
val colorPing = Color(0xFF009966) // Green
val colorPingRed = Color(0xFFFF0099) // Pink Red
val colorConfigType = Color(0xFF0B64F4) // Freedom VPN: cabinet accent
val colorFabActive = Color(0xFF0B64F4) // Freedom VPN: cabinet accent
val colorFabInactiveLight = Color(0xFF9C9C9C) // Gray
val colorFabInactiveDark = Color(0xFF646464) // Dark Gray
val dividerColorLight = Color(0xFFE0E0E0) // Light Gray
val dividerColorDark = Color(0xFF424242) // Dark Gray

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E) // Dark Gray
val toastNormalBgDark = Color(0xD94A4F54) // Darker Gray
val toastSuccessBg = Color(0xD9388E3C) // Green
val toastErrorBg = Color(0xD9D50000) // Red
val toastInfoBg = Color(0xD93F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FreedomTypography,
            shapes = FreedomShapes,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
