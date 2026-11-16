package app.mnemo.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** One set of UI colors. Two instances exist: OLED dark (the default) and light. */
class Palette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val outline: Color,
    val outlineStrong: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val coral: Color,
    val onCoral: Color,
    val lime: Color,
    val onLime: Color,
    val lavender: Color,
    val onLavender: Color,
    val sky: Color,
    val onSky: Color,
    val stateNew: Color,
    val stateLearning: Color,
    val stateReview: Color,
    val stateSuspended: Color,
    val gradeAgain: Color,
    val gradeHard: Color,
    val gradeGood: Color,
    val gradeEasy: Color,
    val gradeAgainBg: Color,
    val gradeHardBg: Color,
    val gradeGoodBg: Color,
    val gradeEasyBg: Color,
    val genderDer: Color,
    val genderDie: Color,
    val genderDas: Color,
    val danger: Color,
    val success: Color,
)

/**
 * OLED dark: true-black ground, surfaces stepping up, accents from Design C
 * lightened to pass 4.5:1 on black.
 */
val OledPalette =
    Palette(
        isDark = true,
        background = Color(0xFF000000),
        surface = Color(0xFF0E0F14),
        surface2 = Color(0xFF171923),
        surface3 = Color(0xFF20232F),
        outline = Color(0xFF262935),
        outlineStrong = Color(0xFF3A3E4E),
        text = Color(0xFFF2F3F7),
        text2 = Color(0xFFA3A8B8),
        text3 = Color(0xFF6F7487),
        coral = Color(0xFFFF6A4D),
        onCoral = Color(0xFF17182B),
        lime = Color(0xFFD4F25A),
        onLime = Color(0xFF17182B),
        lavender = Color(0xFFC9B8FF),
        onLavender = Color(0xFF17182B),
        sky = Color(0xFF9DB7FF),
        onSky = Color(0xFF17182B),
        stateNew = Color(0xFF7A9CFF),
        stateLearning = Color(0xFFF2B63D),
        stateReview = Color(0xFF4FD1A1),
        stateSuspended = Color(0xFF6F7487),
        gradeAgain = Color(0xFFFF8A7A),
        gradeHard = Color(0xFFF2B63D),
        gradeGood = Color(0xFF4FD1A1),
        gradeEasy = Color(0xFF7AB8FF),
        gradeAgainBg = Color(0xFF3A1D1A),
        gradeHardBg = Color(0xFF3A2E12),
        gradeGoodBg = Color(0xFF123328),
        gradeEasyBg = Color(0xFF14263D),
        genderDer = Color(0xFF7A9CFF),
        genderDie = Color(0xFFFF7A80),
        genderDas = Color(0xFF5BD48E),
        danger = Color(0xFFFF8A7A),
        success = Color(0xFF4FD1A1),
    )

/** Light: Design C's paper ground with the same accents at full chroma. */
val LightPalette =
    Palette(
        isDark = false,
        background = Color(0xFFF3F4F8),
        surface = Color(0xFFFFFFFF),
        surface2 = Color(0xFFEBEDF3),
        surface3 = Color(0xFFDEE1EA),
        outline = Color(0xFFE2E4EC),
        outlineStrong = Color(0xFFC5C9D6),
        text = Color(0xFF17182B),
        text2 = Color(0xFF555A6E),
        text3 = Color(0xFF7D8296),
        coral = Color(0xFFFF6A4D),
        onCoral = Color(0xFF17182B),
        lime = Color(0xFFD4F25A),
        onLime = Color(0xFF17182B),
        lavender = Color(0xFFE6DEFF),
        onLavender = Color(0xFF17182B),
        sky = Color(0xFFDCE6FF),
        onSky = Color(0xFF17182B),
        stateNew = Color(0xFF2A5FE6),
        stateLearning = Color(0xFF9A5A00),
        stateReview = Color(0xFF1F7A4D),
        stateSuspended = Color(0xFF7D8296),
        gradeAgain = Color(0xFF9E2A14),
        gradeHard = Color(0xFF7A4A00),
        gradeGood = Color(0xFF145C33),
        gradeEasy = Color(0xFF1F4FC9),
        gradeAgainBg = Color(0xFFFFE1DA),
        gradeHardBg = Color(0xFFFFEFD1),
        gradeGoodBg = Color(0xFFDDF5E6),
        gradeEasyBg = Color(0xFFDCE6FF),
        genderDer = Color(0xFF2A5FE6),
        genderDie = Color(0xFFCC3338),
        genderDas = Color(0xFF1F7F47),
        danger = Color(0xFFA42A17),
        success = Color(0xFF17643E),
    )

/**
 * The colors every screen reads. Backed by Compose state, so switching the
 * palette recomposes everything that uses it.
 */
object MnemoColors {
    var palette: Palette by mutableStateOf(OledPalette)

    val isDark: Boolean get() = palette.isDark
    val background: Color get() = palette.background
    val surface: Color get() = palette.surface
    val surface2: Color get() = palette.surface2
    val surface3: Color get() = palette.surface3
    val outline: Color get() = palette.outline
    val outlineStrong: Color get() = palette.outlineStrong
    val text: Color get() = palette.text
    val text2: Color get() = palette.text2
    val text3: Color get() = palette.text3
    val coral: Color get() = palette.coral
    val onCoral: Color get() = palette.onCoral
    val lime: Color get() = palette.lime
    val onLime: Color get() = palette.onLime
    val lavender: Color get() = palette.lavender
    val onLavender: Color get() = palette.onLavender
    val sky: Color get() = palette.sky
    val onSky: Color get() = palette.onSky
    val stateNew: Color get() = palette.stateNew
    val stateLearning: Color get() = palette.stateLearning
    val stateReview: Color get() = palette.stateReview
    val stateSuspended: Color get() = palette.stateSuspended
    val gradeAgain: Color get() = palette.gradeAgain
    val gradeHard: Color get() = palette.gradeHard
    val gradeGood: Color get() = palette.gradeGood
    val gradeEasy: Color get() = palette.gradeEasy
    val gradeAgainBg: Color get() = palette.gradeAgainBg
    val gradeHardBg: Color get() = palette.gradeHardBg
    val gradeGoodBg: Color get() = palette.gradeGoodBg
    val gradeEasyBg: Color get() = palette.gradeEasyBg
    val genderDer: Color get() = palette.genderDer
    val genderDie: Color get() = palette.genderDie
    val genderDas: Color get() = palette.genderDas
    val danger: Color get() = palette.danger
    val success: Color get() = palette.success
}

/** Mastery ramp (0 → 100%) used by progress bars and the graph. */
fun masteryColor(mastery: Double): Color =
    if (MnemoColors.isDark) {
        when {
            mastery < 0.40 -> Color(0xFF244C40)
            mastery < 0.55 -> Color(0xFF2A6E58)
            mastery < 0.70 -> Color(0xFF3BA07F)
            mastery < 0.85 -> Color(0xFF4FD1A1)
            else -> Color(0xFF9AF0CD)
        }
    } else {
        when {
            mastery < 0.40 -> Color(0xFFB9E6D3)
            mastery < 0.55 -> Color(0xFF7FD3B2)
            mastery < 0.70 -> Color(0xFF3BA07F)
            mastery < 0.85 -> Color(0xFF1F7A4D)
            else -> Color(0xFF145C33)
        }
    }
