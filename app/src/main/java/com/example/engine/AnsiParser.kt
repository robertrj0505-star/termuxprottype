package com.example.engine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.example.data.model.TerminalTheme

object AnsiParser {

    private val ANSI_ESCAPE_REGEX = Regex("\u001B\\[[0-9;]*[a-zA-Z]")

    fun stripAnsi(text: String): String {
        return text.replace(ANSI_ESCAPE_REGEX, "")
    }

    private val STANDARD_COLORS = arrayOf(
        Color(0xFF000000), // 0 Black
        Color(0xFFCD3131), // 1 Red
        Color(0xFF0DBC79), // 2 Green
        Color(0xFFE5E510), // 3 Yellow
        Color(0xFF2472C8), // 4 Blue
        Color(0xFFBC3FBC), // 5 Magenta
        Color(0xFF11A8CD), // 6 Cyan
        Color(0xFFE5E5E5), // 7 White
        Color(0xFF666666), // 8 Bright Black (Gray)
        Color(0xFFF14C4C), // 9 Bright Red
        Color(0xFF23D18B), // 10 Bright Green
        Color(0xFFF5F543), // 11 Bright Yellow
        Color(0xFF3B8EEA), // 12 Bright Blue
        Color(0xFFD670D6), // 13 Bright Magenta
        Color(0xFF29B8DB), // 14 Bright Cyan
        Color(0xFFFFFFFF)  // 15 Bright White
    )

    fun parseAnsi(text: String, defaultColor: Color): AnnotatedString {
        if (!text.contains("\u001B[")) {
            return buildAnnotatedString {
                append(text)
            }
        }

        return buildAnnotatedString {
            var currentIndex = 0
            var fgColor: Color? = null
            var bgColor: Color? = null
            var isBold = false
            var isItalic = false
            var isUnderline = false
            var isDim = false

            val matches = ANSI_ESCAPE_REGEX.findAll(text).toList()

            for (match in matches) {
                if (match.range.first > currentIndex) {
                    val plainChunk = text.substring(currentIndex, match.range.first)
                    val start = length
                    append(plainChunk)

                    val style = SpanStyle(
                        color = fgColor ?: defaultColor,
                        background = bgColor ?: Color.Transparent,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = if (isUnderline) TextDecoration.Underline else TextDecoration.None
                    )
                    addStyle(style, start, length)
                }

                // Process escape code
                val code = match.value
                if (code.endsWith("m")) {
                    val paramsStr = code.substring(2, code.length - 1)
                    val params = if (paramsStr.isEmpty()) listOf(0) else paramsStr.split(";").mapNotNull { it.toIntOrNull() }

                    var i = 0
                    while (i < params.size) {
                        when (val p = params[i]) {
                            0 -> { // Reset
                                fgColor = null
                                bgColor = null
                                isBold = false
                                isItalic = false
                                isUnderline = false
                                isDim = false
                            }
                            1 -> isBold = true
                            2 -> isDim = true
                            3 -> isItalic = true
                            4 -> isUnderline = true
                            22 -> {
                                isBold = false
                                isDim = false
                            }
                            23 -> isItalic = false
                            24 -> isUnderline = false
                            in 30..37 -> fgColor = STANDARD_COLORS[p - 30]
                            39 -> fgColor = null // Default fg
                            in 40..47 -> bgColor = STANDARD_COLORS[p - 40]
                            49 -> bgColor = null // Default bg
                            in 90..97 -> fgColor = STANDARD_COLORS[p - 90 + 8]
                            in 100..107 -> bgColor = STANDARD_COLORS[p - 100 + 8]
                            38 -> { // Extended foreground
                                if (i + 2 < params.size && params[i + 1] == 5) {
                                    val colorIndex = params[i + 2]
                                    fgColor = get256Color(colorIndex)
                                    i += 2
                                } else if (i + 4 < params.size && params[i + 1] == 2) {
                                    val r = params[i + 2].coerceIn(0, 255)
                                    val g = params[i + 3].coerceIn(0, 255)
                                    val b = params[i + 4].coerceIn(0, 255)
                                    fgColor = Color(r, g, b)
                                    i += 4
                                }
                            }
                            48 -> { // Extended background
                                if (i + 2 < params.size && params[i + 1] == 5) {
                                    val colorIndex = params[i + 2]
                                    bgColor = get256Color(colorIndex)
                                    i += 2
                                } else if (i + 4 < params.size && params[i + 1] == 2) {
                                    val r = params[i + 2].coerceIn(0, 255)
                                    val g = params[i + 3].coerceIn(0, 255)
                                    val b = params[i + 4].coerceIn(0, 255)
                                    bgColor = Color(r, g, b)
                                    i += 4
                                }
                            }
                        }
                        i++
                    }
                }

                currentIndex = match.range.last + 1
            }

            if (currentIndex < text.length) {
                val remaining = text.substring(currentIndex)
                val start = length
                append(remaining)
                val style = SpanStyle(
                    color = fgColor ?: defaultColor,
                    background = bgColor ?: Color.Transparent,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (isUnderline) TextDecoration.Underline else TextDecoration.None
                )
                addStyle(style, start, length)
            }
        }
    }

    private fun get256Color(index: Int): Color {
        return when {
            index in 0..15 -> STANDARD_COLORS[index]
            index in 16..231 -> {
                // 6x6x6 color cube
                val idx = index - 16
                val r = (idx / 36) * 51
                val g = ((idx % 36) / 6) * 51
                val b = (idx % 6) * 51
                Color(r, g, b)
            }
            index in 232..255 -> {
                // Grayscale ramp
                val gray = (index - 232) * 10 + 8
                Color(gray, gray, gray)
            }
            else -> Color.White
        }
    }
}
