package ios.silv.gemclient.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ios.silv.gemclient.R

val SamsungSansSharp = FontFamily(
    Font(R.font.samsungsharpsans, weight = FontWeight.Normal),
    Font(R.font.samsungsharpsans, weight = FontWeight.Light),
    Font(R.font.samsungsharpsans, weight = FontWeight.Thin),
    Font(R.font.samsungsharpsans, weight = FontWeight.ExtraLight),
    Font(R.font.samsungsharpsans, weight = FontWeight.ExtraLight),
    Font(R.font.samsungsharpsans_bold, weight = FontWeight.Bold),
    Font(R.font.samsungsharpsans_bold, weight = FontWeight.ExtraBold),
    Font(R.font.samsungsharpsans_medium, weight = FontWeight.SemiBold),
    Font(R.font.samsungsharpsans_medium, weight = FontWeight.Medium)
)

private val defaultTypography = Typography()

val Typography =
    Typography().copy(
        displayLarge = defaultTypography.displayLarge.copy(fontFamily = SamsungSansSharp),
        displayMedium = defaultTypography.displayMedium.copy(fontFamily = SamsungSansSharp),
        displaySmall = defaultTypography.displaySmall.copy(fontFamily = SamsungSansSharp),
        headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = SamsungSansSharp),
        headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = SamsungSansSharp),
        headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = SamsungSansSharp),
        titleLarge = defaultTypography.titleLarge.copy(fontFamily = SamsungSansSharp),
        titleMedium = defaultTypography.titleMedium.copy(fontFamily = SamsungSansSharp),
        titleSmall = defaultTypography.titleSmall.copy(fontFamily = SamsungSansSharp),
        bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = SamsungSansSharp),
        bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = SamsungSansSharp),
        bodySmall = defaultTypography.bodySmall.copy(fontFamily = SamsungSansSharp),
        labelLarge = defaultTypography.labelLarge.copy(fontFamily = SamsungSansSharp),
        labelMedium = defaultTypography.labelMedium.copy(fontFamily = SamsungSansSharp),
        labelSmall = defaultTypography.labelSmall.copy(fontFamily = SamsungSansSharp),
    )
