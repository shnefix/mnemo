package app.mnemo

import app.mnemo.domain.text.LatexText
import kotlin.test.Test
import kotlin.test.assertEquals

class LatexTextTest {
    @Test
    fun convertsCommonFormulas() {
        assertEquals("E = mc²", LatexText.toUnicode("E = mc^2"))
        assertEquals("H₂O", LatexText.toUnicode("H_2O"))
        assertEquals("α + β → γ", LatexText.toUnicode("\\alpha + \\beta \\to \\gamma"))
        assertEquals("1⁄2", LatexText.toUnicode("\\frac{1}{2}"))
        assertEquals("√(x+1)", LatexText.toUnicode("\\sqrt{x+1}"))
        assertEquals("x ∈ ℝ", LatexText.toUnicode("x \\in \\mathbb{R}"))
        assertEquals("e^(iπ)", LatexText.toUnicode("e^{i\\pi}"))
    }

    @Test
    fun rendersMathSpansButNotCodeOrPrices() {
        assertEquals("Mass: *E = mc²*", LatexText.render("Mass: \$E = mc^2\$"))
        assertEquals("Anki: *α*", LatexText.render("Anki: \\(\\alpha\\)"))
        assertEquals("costs \$5 and \$6", LatexText.render("costs \$5 and \$6"))
        assertEquals("`\$x^2\$`", LatexText.render("`\$x^2\$`"))
        assertEquals("a\n\n**∑ xᵢ**\n\nb", LatexText.render("a\$\$\\sum x_i\$\$b"))
    }
}
