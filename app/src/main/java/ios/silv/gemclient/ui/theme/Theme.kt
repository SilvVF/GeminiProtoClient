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

private val DarkMochaColorScheme = darkColorScheme(
    primary = MochaMauve,
    onPrimary = MochaCrust,
    secondary = MochaTeal,
    onSecondary = MochaCrust,
    background = MochaBase,
    onBackground = MochaText,
    surface = MochaSurface0,
    onSurface = MochaText,
    error = MochaRed,
    onError = MochaCrust
)

private val LightLatteColorScheme = lightColorScheme(
    primary = LatteBlue,
    onPrimary = LatteCrust,
    secondary = LatteTeal,
    onSecondary = LatteCrust,
    background = LatteBase,
    onBackground = LatteText,
    surface = LatteSurface0,
    onSurface = LatteText,
    error = LatteRed,
    onError = LatteCrust
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