package app.mnemo.domain.text

/**
 * Turns the LaTeX found in cards (dollar, double-dollar, backslash-paren, backslash-bracket
 * and Anki [latex] style delimiters) into Unicode text the Markdown renderer can show:
 * Greek letters, operators, arrows, super/subscripts, fractions and roots. It approximates
 * card-sized formulas rather than typesetting them. Code spans and fences are left alone.
 */
object LatexText {
    private val codeChunk = Regex("```[\\s\\S]*?```|`[^`\\n]*`")
    private val display =
        Regex("""\$\$([\s\S]+?)\$\$|\\\[([\s\S]+?)\\\]|\[\$\$\]([\s\S]+?)\[/\$\$\]""")
    private val inline =
        Regex("""(?<![\\$\w])\$(?!\s)([^$\n]+?)(?<!\s)\$(?![\w$])|\\\(([\s\S]+?)\\\)|\[\$\]([\s\S]+?)\[/\$\]""")

    /** Converts every math span in a Markdown field. */
    fun render(markdown: String): String {
        if ('$' !in markdown && "\\(" !in markdown && "\\[" !in markdown) return markdown
        val out = StringBuilder()
        var last = 0
        codeChunk.findAll(markdown).forEach { m ->
            out.append(convertMath(markdown.substring(last, m.range.first)))
            out.append(m.value)
            last = m.range.last + 1
        }
        out.append(convertMath(markdown.substring(last)))
        return out.toString()
    }

    private fun convertMath(text: String): String {
        val withDisplay =
            display.replace(text) { m ->
                val body = m.groupValues.drop(1).first { it.isNotEmpty() }
                "\n\n**${escapeMd(toUnicode(body))}**\n\n"
            }
        return inline.replace(withDisplay) { m ->
            val body = m.groupValues.drop(1).first { it.isNotEmpty() }
            "*${escapeMd(toUnicode(body))}*"
        }
    }

    private fun escapeMd(s: String) = s.replace("*", "\\*").replace("_", "\\_")

    /** Converts one formula body to Unicode. */
    fun toUnicode(tex: String): String {
        var s = tex.trim()
        s = s.replace(Regex("""\\(left|right|big|Big|bigg|Bigg)(?![a-zA-Z])"""), "")
        s = s.replace(Regex("""\\(text|mathrm|mathbf|mathit|operatorname|textbf|textit)\{([^{}]*)\}"""), "$2")
        s = s.replace(Regex("""\\mathbb\{([A-Za-z])\}""")) { blackboard[it.groupValues[1]] ?: it.groupValues[1] }
        // Innermost-first so nested fractions and roots resolve.
        repeat(8) {
            s =
                s.replace(Regex("""\\[dt]?frac\{([^{}]*)\}\{([^{}]*)\}""")) { m ->
                    "${group(m.groupValues[1])}⁄${group(m.groupValues[2])}"
                }
            s =
                s.replace(Regex("""\\sqrt\[([^\]]*)\]\{([^{}]*)\}""")) { m ->
                    "${sup(m.groupValues[1])}√${group(m.groupValues[2])}"
                }
            s = s.replace(Regex("""\\sqrt\{([^{}]*)\}""")) { m -> "√${group(m.groupValues[1])}" }
            s =
                s.replace(Regex("""\\(hat|bar|vec|dot|tilde|overline)\{([^{}]*)\}""")) { m ->
                    m.groupValues[2] + (accents[m.groupValues[1]] ?: "")
                }
        }
        s = s.replace(Regex("""\\([A-Za-z]+)""")) { m -> symbols[m.groupValues[1]] ?: m.groupValues[1] }
        s = s.replace(Regex("""\^\{([^{}]*)\}|\^(\\?.)""")) { m -> sup(m.groupValues[1].ifEmpty { m.groupValues[2] }) }
        s = s.replace(Regex("""_\{([^{}]*)\}|_(\\?.)""")) { m -> sub(m.groupValues[1].ifEmpty { m.groupValues[2] }) }
        s =
            s
                .replace("\\,", " ")
                .replace("\\;", " ")
                .replace("\\ ", " ")
                .replace("\\!", "")
        s =
            s
                .replace("\\{", "\u0000")
                .replace("\\}", "\u0001")
                .replace("{", "")
                .replace("}", "")
        s =
            s
                .replace("\u0000", "{")
                .replace("\u0001", "}")
                .replace("&", "")
                .replace("\\\\", "; ")
        return s.replace(Regex(" {2,}"), " ").trim()
    }

    private fun group(s: String) = if (s.length <= 1 || s.all { it.isLetterOrDigit() }) s else "($s)"

    private fun sup(s: String): String = if (s.all { it in superscripts }) s.map { superscripts[it] }.joinToString("") else "^($s)"

    private fun sub(s: String): String = if (s.all { it in subscripts }) s.map { subscripts[it] }.joinToString("") else "_($s)"

    private val superscripts =
        mapOf(
            '0' to '⁰',
            '1' to '¹',
            '2' to '²',
            '3' to '³',
            '4' to '⁴',
            '5' to '⁵',
            '6' to '⁶',
            '7' to '⁷',
            '8' to '⁸',
            '9' to '⁹',
            '+' to '⁺',
            '-' to '⁻',
            '=' to '⁼',
            '(' to '⁽',
            ')' to '⁾',
            'n' to 'ⁿ',
            'i' to 'ⁱ',
            'a' to 'ᵃ',
            'b' to 'ᵇ',
            'c' to 'ᶜ',
            'd' to 'ᵈ',
            'e' to 'ᵉ',
            'k' to 'ᵏ',
            'm' to 'ᵐ',
            'x' to 'ˣ',
            'y' to 'ʸ',
            'T' to 'ᵀ',
            '*' to '*',
            '′' to '′',
            ' ' to ' ',
        )
    private val subscripts =
        mapOf(
            '0' to '₀',
            '1' to '₁',
            '2' to '₂',
            '3' to '₃',
            '4' to '₄',
            '5' to '₅',
            '6' to '₆',
            '7' to '₇',
            '8' to '₈',
            '9' to '₉',
            '+' to '₊',
            '-' to '₋',
            '=' to '₌',
            '(' to '₍',
            ')' to '₎',
            'a' to 'ₐ',
            'e' to 'ₑ',
            'i' to 'ᵢ',
            'j' to 'ⱼ',
            'k' to 'ₖ',
            'n' to 'ₙ',
            'm' to 'ₘ',
            'o' to 'ₒ',
            'r' to 'ᵣ',
            'x' to 'ₓ',
            't' to 'ₜ',
            'p' to 'ₚ',
            's' to 'ₛ',
            ' ' to ' ',
        )
    private val accents =
        mapOf(
            "hat" to "\u0302",
            "bar" to "\u0304",
            "overline" to "\u0305",
            "vec" to "\u20D7",
            "dot" to "\u0307",
            "tilde" to "\u0303",
        )
    private val blackboard = mapOf("R" to "ℝ", "N" to "ℕ", "Z" to "ℤ", "Q" to "ℚ", "C" to "ℂ", "P" to "ℙ")
    private val symbols =
        mapOf(
            "alpha" to "α",
            "beta" to "β",
            "gamma" to "γ",
            "delta" to "δ",
            "epsilon" to "ε",
            "varepsilon" to "ε",
            "zeta" to "ζ",
            "eta" to "η",
            "theta" to "θ",
            "vartheta" to "ϑ",
            "iota" to "ι",
            "kappa" to "κ",
            "lambda" to "λ",
            "mu" to "μ",
            "nu" to "ν",
            "xi" to "ξ",
            "pi" to "π",
            "rho" to "ρ",
            "sigma" to "σ",
            "tau" to "τ",
            "upsilon" to "υ",
            "phi" to "φ",
            "varphi" to "φ",
            "chi" to "χ",
            "psi" to "ψ",
            "omega" to "ω",
            "Gamma" to "Γ",
            "Delta" to "Δ",
            "Theta" to "Θ",
            "Lambda" to "Λ",
            "Xi" to "Ξ",
            "Pi" to "Π",
            "Sigma" to "Σ",
            "Phi" to "Φ",
            "Psi" to "Ψ",
            "Omega" to "Ω",
            "times" to "×",
            "cdot" to "·",
            "div" to "÷",
            "pm" to "±",
            "mp" to "∓",
            "ast" to "∗",
            "leq" to "≤",
            "le" to "≤",
            "geq" to "≥",
            "ge" to "≥",
            "neq" to "≠",
            "ne" to "≠",
            "approx" to "≈",
            "equiv" to "≡",
            "sim" to "∼",
            "simeq" to "≃",
            "cong" to "≅",
            "propto" to "∝",
            "ll" to "≪",
            "gg" to "≫",
            "infty" to "∞",
            "partial" to "∂",
            "nabla" to "∇",
            "sum" to "∑",
            "prod" to "∏",
            "int" to "∫",
            "iint" to "∬",
            "oint" to "∮",
            "lim" to "lim",
            "log" to "log",
            "ln" to "ln",
            "exp" to "exp",
            "sin" to "sin",
            "cos" to "cos",
            "tan" to "tan",
            "max" to "max",
            "min" to "min",
            "to" to "→",
            "rightarrow" to "→",
            "leftarrow" to "←",
            "Rightarrow" to "⇒",
            "Leftarrow" to "⇐",
            "leftrightarrow" to "↔",
            "Leftrightarrow" to "⇔",
            "iff" to "⇔",
            "implies" to "⇒",
            "mapsto" to "↦",
            "rightleftharpoons" to "⇌",
            "uparrow" to "↑",
            "downarrow" to "↓",
            "in" to "∈",
            "notin" to "∉",
            "subset" to "⊂",
            "subseteq" to "⊆",
            "supset" to "⊃",
            "cup" to "∪",
            "cap" to "∩",
            "emptyset" to "∅",
            "varnothing" to "∅",
            "forall" to "∀",
            "exists" to "∃",
            "neg" to "¬",
            "land" to "∧",
            "wedge" to "∧",
            "lor" to "∨",
            "vee" to "∨",
            "oplus" to "⊕",
            "otimes" to "⊗",
            "circ" to "∘",
            "degree" to "°",
            "prime" to "′",
            "ldots" to "…",
            "cdots" to "⋯",
            "dots" to "…",
            "hbar" to "ℏ",
            "ell" to "ℓ",
            "angle" to "∠",
            "perp" to "⊥",
            "parallel" to "∥",
            "langle" to "⟨",
            "rangle" to "⟩",
            "quad" to "  ",
            "qquad" to "    ",
            "cdotp" to "·",
        )
}
