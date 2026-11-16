package app.mnemo.platform

import java.text.Normalizer

private val combiningMarks = Regex("""\p{Mn}+""")

actual fun foldDiacritics(s: String): String =
    Normalizer
        .normalize(s, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .replace("ß", "ss")
        .replace("ẞ", "SS")
        .replace("æ", "ae")
        .replace("Æ", "AE")
        .replace("œ", "oe")
        .replace("Œ", "OE")
        .replace("ø", "o")
        .replace("Ø", "O")
        .replace("đ", "d")
        .replace("Đ", "D")
        .replace("ł", "l")
        .replace("Ł", "L")
