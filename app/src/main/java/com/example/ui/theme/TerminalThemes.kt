package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.data.model.TerminalTheme

object TerminalThemes {

    val TermuxClassic = TerminalTheme(
        id = "termux_classic",
        name = "Termux Classic",
        background = Color(0xFF000000),
        foreground = Color(0xFFE5E5E5),
        cursorColor = Color(0xFF00FF66),
        promptColor = Color(0xFF00FF66),
        secondaryColor = Color(0xFF00BFFF),
        selectionColor = Color(0x6600FF66),
        isDark = true
    )

    val HackerMatrix = TerminalTheme(
        id = "matrix",
        name = "Matrix Green",
        background = Color(0xFF0D1117),
        foreground = Color(0xFF23D18B),
        cursorColor = Color(0xFF00FF66),
        promptColor = Color(0xFF00FF88),
        secondaryColor = Color(0xFF0DBC79),
        selectionColor = Color(0x6600FF88),
        isDark = true
    )

    val Dracula = TerminalTheme(
        id = "dracula",
        name = "Dracula",
        background = Color(0xFF282A36),
        foreground = Color(0xFFF8F8F2),
        cursorColor = Color(0xFFFF79C6),
        promptColor = Color(0xFF50FA7B),
        secondaryColor = Color(0xFF8BE9FD),
        selectionColor = Color(0x66BD93F9),
        isDark = true
    )

    val Monokai = TerminalTheme(
        id = "monokai",
        name = "Monokai Pro",
        background = Color(0xFF2D2A2E),
        foreground = Color(0xFFFCFCFA),
        cursorColor = Color(0xFFFFD866),
        promptColor = Color(0xFFA9DC76),
        secondaryColor = Color(0xFF78DCE8),
        selectionColor = Color(0x66FF6188),
        isDark = true
    )

    val Nord = TerminalTheme(
        id = "nord",
        name = "Nord Frost",
        background = Color(0xFF2E3440),
        foreground = Color(0xFFD8DEE9),
        cursorColor = Color(0xFF88C0D0),
        promptColor = Color(0xFFA3BE8C),
        secondaryColor = Color(0xFF81A1C1),
        selectionColor = Color(0x664C566A),
        isDark = true
    )

    val SolarizedDark = TerminalTheme(
        id = "solarized_dark",
        name = "Solarized Dark",
        background = Color(0xFF002B36),
        foreground = Color(0xFF839496),
        cursorColor = Color(0xFF2AA198),
        promptColor = Color(0xFF859900),
        secondaryColor = Color(0xFF268BD2),
        selectionColor = Color(0x66073642),
        isDark = true
    )

    val Cyberpunk = TerminalTheme(
        id = "cyberpunk",
        name = "Cyberpunk 2077",
        background = Color(0xFF120E24),
        foreground = Color(0xFFFCEE09),
        cursorColor = Color(0xFFFF007F),
        promptColor = Color(0xFF00FFFF),
        secondaryColor = Color(0xFFFF007F),
        selectionColor = Color(0x6600FFFF),
        isDark = true
    )

    val AmberCRT = TerminalTheme(
        id = "amber_crt",
        name = "Amber CRT Retro",
        background = Color(0xFF140D00),
        foreground = Color(0xFFFFB000),
        cursorColor = Color(0xFFFFCC00),
        promptColor = Color(0xFFFF9900),
        secondaryColor = Color(0xFFFFD166),
        selectionColor = Color(0x66FF9900),
        isDark = true
    )

    val OneDark = TerminalTheme(
        id = "one_dark",
        name = "One Dark Pro",
        background = Color(0xFF1E222B),
        foreground = Color(0xFFABB2BF),
        cursorColor = Color(0xFF528BFF),
        promptColor = Color(0xFF98C379),
        secondaryColor = Color(0xFF61AFEF),
        selectionColor = Color(0x663E4451),
        isDark = true
    )

    val allThemes = listOf(
        TermuxClassic,
        HackerMatrix,
        Dracula,
        Monokai,
        Nord,
        SolarizedDark,
        Cyberpunk,
        AmberCRT,
        OneDark
    )

    fun getThemeById(id: String): TerminalTheme {
        return allThemes.find { it.id == id } ?: TermuxClassic
    }
}
