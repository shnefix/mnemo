package app.mnemo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import mnemo.composeapp.generated.resources.Res
import mnemo.composeapp.generated.resources.bricolage_500
import mnemo.composeapp.generated.resources.bricolage_700
import mnemo.composeapp.generated.resources.bricolage_800
import mnemo.composeapp.generated.resources.dmsans_400
import mnemo.composeapp.generated.resources.dmsans_500
import mnemo.composeapp.generated.resources.dmsans_600
import mnemo.composeapp.generated.resources.dmsans_700
import org.jetbrains.compose.resources.Font

@Composable
fun displayFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.bricolage_500, FontWeight.Medium),
        Font(Res.font.bricolage_700, FontWeight.Bold),
        Font(Res.font.bricolage_800, FontWeight.ExtraBold),
    )

@Composable
fun bodyFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.dmsans_400, FontWeight.Normal),
        Font(Res.font.dmsans_500, FontWeight.Medium),
        Font(Res.font.dmsans_600, FontWeight.SemiBold),
        Font(Res.font.dmsans_700, FontWeight.Bold),
    )

@Composable
fun mnemoTypography(): Typography {
    val display = displayFontFamily()
    val body = bodyFontFamily()
    return Typography(
        displayLarge =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 64.sp,
                lineHeight = 64.sp,
                letterSpacing = (-2).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 44.sp,
                lineHeight = 48.sp,
                letterSpacing = (-1.5).sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                letterSpacing = (-1).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.5).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.3).sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 24.sp,
            ),
        titleLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
        titleMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium =
            TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.3.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.8.sp,
            ),
    )
}
