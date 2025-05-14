package ios.silv.gemclient.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ios.silv.gemclient.settings.Theme

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

val DarkMochaColorScheme = darkColorScheme(
    primary = MochaMauve,
    onPrimary = MochaCrust,
    primaryContainer = MochaLavender,
    onPrimaryContainer = MochaCrust,

    secondary = MochaTeal,
    onSecondary = MochaCrust,
    secondaryContainer = MochaSurface2,
    onSecondaryContainer = MochaText,

    tertiary = MochaGreen,
    onTertiary = MochaCrust,
    tertiaryContainer = MochaSurface2,
    onTertiaryContainer = MochaText,

    background = MochaBase,
    onBackground = MochaText,

    surface = MochaSurface0,
    onSurface = MochaText,
    surfaceVariant = MochaSurface2,
    onSurfaceVariant = MochaOverlay1,

    inverseSurface = MochaText,
    inverseOnSurface = MochaBase,

    error = MochaRed,
    onError = MochaCrust,
    errorContainer = MochaMaroon,
    onErrorContainer = MochaCrust,

    outline = MochaOverlay1,
    outlineVariant = MochaSurface2,

    surfaceTint = MochaMauve
)
val LightLatteColorScheme = lightColorScheme(
    primary = LatteBlue,
    onPrimary = LatteCrust,
    primaryContainer = LatteLavender,
    onPrimaryContainer = LatteCrust,

    secondary = LatteTeal,
    onSecondary = LatteCrust,
    secondaryContainer = LatteSurface1,
    onSecondaryContainer = LatteText,

    tertiary = LatteGreen,
    onTertiary = LatteCrust,
    tertiaryContainer = LatteSurface1,
    onTertiaryContainer = LatteText,

    background = LatteBase,
    onBackground = LatteText,

    surface = LatteSurface0,
    onSurface = LatteText,
    surfaceVariant = LatteSurface2,
    onSurfaceVariant = LatteOverlay1,

    inverseSurface = LatteText,
    inverseOnSurface = LatteBase,

    error = LatteRed,
    onError = LatteCrust,
    errorContainer = LatteFlamingo,
    onErrorContainer = LatteCrust,

    outline = LatteOverlay1,
    outlineVariant = LatteSurface2,
    surfaceTint = LatteBlue
)

@Composable
fun GemClientTheme(
    theme: Theme,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when(theme) {
        Theme.Light -> false
        Theme.Dark -> true
        Theme.System -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkMochaColorScheme
        else -> LightLatteColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}