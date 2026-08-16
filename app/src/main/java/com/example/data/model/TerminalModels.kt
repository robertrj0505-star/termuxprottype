package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class CursorStyle {
    BLOCK,
    UNDERLINE,
    BAR
}

data class TerminalTheme(
    val id: String,
    val name: String,
    val background: Color,
    val foreground: Color,
    val cursorColor: Color,
    val promptColor: Color,
    val secondaryColor: Color,
    val selectionColor: Color,
    val isDark: Boolean = true
)

data class TerminalLine(
    val id: Long = System.nanoTime(),
    val text: String,
    val isInput: Boolean = false,
    val isError: Boolean = false,
    val isSystem: Boolean = false,
    val prompt: String? = null,
    val customColor: Color? = null
)

data class PackageItem(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val category: String,
    val installed: Boolean,
    val size: String,
    val command: String
)

enum class InteractiveMode {
    SHELL,
    NANO,
    MATRIX,
    SNAKE,
    PYTHON_REPL
}
