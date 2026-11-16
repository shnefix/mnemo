package app.mnemo.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Mnemo's OLED dark palette. The ground is true black so OLED pixels switch
 * off; surfaces step up in two levels; the accents come from Design C (coral,
 * lime, lavender) lightened enough to pass 4.5:1 on black.
 */
object MnemoColors {
    val background = Color(0xFF000000)
    val surface = Color(0xFF0E0F14)
    val surface2 = Color(0xFF171923)
    val surface3 = Color(0xFF20232F)
    val outline = Color(0xFF262935)
    val outlineStrong = Color(0xFF3A3E4E)

    val text = Color(0xFFF2F3F7)
    val text2 = Color(0xFFA3A8B8)
    val text3 = Color(0xFF6F7487)

    val coral = Color(0xFFFF6A4D)
    val onCoral = Color(0xFF17182B)
    val lime = Color(0xFFD4F25A)
    val onLime = Color(0xFF17182B)
    val lavender = Color(0xFFC9B8FF)
    val onLavender = Color(0xFF17182B)
    val sky = Color(0xFF9DB7FF)
    val onSky = Color(0xFF17182B)

    // Card states
    val stateNew = Color(0xFF7A9CFF)
    val stateLearning = Color(0xFFF2B63D)
    val stateReview = Color(0xFF4FD1A1)
    val stateSuspended = Color(0xFF6F7487)

    // Grades
    val gradeAgain = Color(0xFFFF8A7A)
    val gradeHard = Color(0xFFF2B63D)
    val gradeGood = Color(0xFF4FD1A1)
    val gradeEasy = Color(0xFF7AB8FF)

    // Tinted backgrounds for grade buttons (on black)
    val gradeAgainBg = Color(0xFF3A1D1A)
    val gradeHardBg = Color(0xFF3A2E12)
    val gradeGoodBg = Color(0xFF123328)
    val gradeEasyBg = Color(0xFF14263D)

    // German genders (Design C, lightened for black)
    val genderDer = Color(0xFF7A9CFF)
    val genderDie = Color(0xFFFF7A80)
    val genderDas = Color(0xFF5BD48E)

    val danger = Color(0xFFFF8A7A)
    val success = Color(0xFF4FD1A1)
}

/** Mastery ramp (0 → 100%) used by progress bars and the graph. */
fun masteryColor(mastery: Double): Color =
    when {
        mastery < 0.40 -> Color(0xFF244C40)
        mastery < 0.55 -> Color(0xFF2A6E58)
        mastery < 0.70 -> Color(0xFF3BA07F)
        mastery < 0.85 -> Color(0xFF4FD1A1)
        else -> Color(0xFF9AF0CD)
    }
