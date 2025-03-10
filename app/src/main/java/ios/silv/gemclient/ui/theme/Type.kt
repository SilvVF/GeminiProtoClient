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

val Typography = Typography().copy(
    displayLarge = defaultTypography.displayLarge.copy(
        fontFamily = SamsungSansSharp,
        fontSize = 34.sp,
        lineHeight = 44.sp
    ),
    displayMedium = defaultTypography.displayMedium.copy(
        fontFamily = SamsungSansSharp,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    displaySmall = defaultTypography.displaySmall.copy(
        fontFamily = SamsungSansSharp,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineLarge = defaultTypography.headlineLarge.copy(
        fontFamily = SamsungSansSharp,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    headlineMedium = defaultTypography.headlineMedium.copy(
        fontFamily = SamsungSansSharp,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    headlineSmall = defaultTypography.headlineSmall.copy(
        fontFamily = SamsungSansSharp,
        fontSize = 18.sp,
        lineHeight = 26.sp
    ),
    titleLarge = defaultTypography.titleLarge.copy(
        fontFamily = SamsungSansSharp,
        fontSize = 18.sp,
        lineHeight = 26.sp
    ),
    titleMedium = defaultTypography.titleMedium.copy(
        fontFamily = SamsungSansSharp,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = defaultTypography.titleSmall.copy(
        fontFamily = SamsungSansSharp,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = defaultTypography.bodyLarge.copy(
        fontFamily = SamsungSansSharp,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = defaultTypography.bodyMedium.copy(
        fontFamily = SamsungSansSharp,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    bodySmall = defaultTypography.bodySmall.copy(
        fontFamily = SamsungSansSharp,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = defaultTypography.labelLarge.copy(
        fontFamily = SamsungSansSharp,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = defaultTypography.labelMedium.copy(
        fontFamily = SamsungSansSharp,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelSmall = defaultTypography.labelSmall.copy(
        fontFamily = SamsungSansSharp,
        fontSize = 10.sp,
        lineHeight = 16.sp
    ),
)
