package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TerminalTheme
import com.example.ui.viewmodel.NanoState

@Composable
fun NanoEditorView(
    nanoState: NanoState,
    theme: TerminalTheme,
    fontSizeSp: Float,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lines = nanoState.content.lines()
    val lineCount = lines.size.coerceAtLeast(1)

    val headerBg = if (theme.isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val headerFg = if (theme.isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val gutterBg = if (theme.isDark) Color(0xFF161B22) else Color(0xFFF1F5F9)
    val gutterFg = if (theme.isDark) Color(0xFF6E7681) else Color(0xFF94A3B8)
    val footerBg = if (theme.isDark) Color(0xFF161B22) else Color(0xFFE2E8F0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .testTag("nano_editor_view")
    ) {
        // Nano Title Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "UW PICO 5.09",
                color = headerFg,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "File: ${nanoState.fileName}${if (nanoState.isModified) " [Modified]" else ""}",
                color = headerFg,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${lineCount}L",
                color = headerFg,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Editor Body with Line Numbers
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Line numbers gutter
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .width(42.dp)
                    .background(gutterBg)
                    .padding(vertical = 4.dp, horizontal = 4.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.End
            ) {
                for (i in 1..lineCount) {
                    Text(
                        text = "$i",
                        color = gutterFg,
                        fontSize = fontSizeSp.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = (fontSizeSp * 1.35f).sp
                    )
                }
            }

            // Editable text area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                BasicTextField(
                    value = nanoState.content,
                    onValueChange = onContentChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("nano_text_input"),
                    textStyle = TextStyle(
                        color = theme.foreground,
                        fontSize = fontSizeSp.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = (fontSizeSp * 1.35f).sp
                    ),
                    cursorBrush = SolidColor(theme.cursorColor)
                )
            }
        }

        // Status Message Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(footerBg)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = nanoState.statusMessage.ifEmpty { "Nano ready. Press ^O to Save, ^X to Exit." },
                color = theme.promptColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Shortcuts Toolbar (^O WriteOut, ^X Exit, etc.)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NanoShortcutButton(
                shortcut = "^O",
                label = "WriteOut",
                theme = theme,
                onClick = onSave
            )
            NanoShortcutButton(
                shortcut = "^X",
                label = "Exit",
                theme = theme,
                onClick = onExit
            )
            NanoShortcutButton(
                shortcut = "^R",
                label = "Read File",
                theme = theme,
                onClick = { /* Read action */ }
            )
            NanoShortcutButton(
                shortcut = "^W",
                label = "Where Is",
                theme = theme,
                onClick = { /* Search */ }
            )
            NanoShortcutButton(
                shortcut = "^K",
                label = "Cut",
                theme = theme,
                onClick = {
                    onContentChange("")
                }
            )
        }
    }
}

@Composable
private fun NanoShortcutButton(
    shortcut: String,
    label: String,
    theme: TerminalTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val btnBg = if (theme.isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val textCol = if (theme.isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(btnBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = shortcut,
            color = theme.promptColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = label,
            color = textCol,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
