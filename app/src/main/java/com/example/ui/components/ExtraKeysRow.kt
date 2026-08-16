package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardTab
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TerminalTheme

@Composable
fun ExtraKeysToolbar(
    theme: TerminalTheme,
    isCtrlActive: Boolean,
    isAltActive: Boolean,
    isExpanded: Boolean,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onKeyAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollStateRow1 = rememberScrollState()
    val scrollStateRow2 = rememberScrollState()

    val surfaceBg = if (theme.isDark) Color(0xFF161B22) else Color(0xFFE2E8F0)
    val borderColor = if (theme.isDark) Color(0xFF30363D) else Color(0xFFCBD5E1)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(surfaceBg)
            .border(width = 0.5.dp, color = borderColor)
            .padding(vertical = 3.dp)
            .testTag("extra_keys_toolbar")
    ) {
        // Primary Row 1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollStateRow1)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ESC key
            KeyButton(
                label = "ESC",
                theme = theme,
                onClick = { onKeyAction("ESC") }
            )

            // TAB key
            KeyIconButton(
                icon = Icons.Default.KeyboardTab,
                contentDesc = "TAB",
                label = "TAB",
                theme = theme,
                onClick = { onKeyAction("TAB") }
            )

            // CTRL modifier key
            ModifierKeyButton(
                label = "CTRL",
                isActive = isCtrlActive,
                activeColor = theme.promptColor,
                theme = theme,
                onClick = onCtrlToggle
            )

            // ALT modifier key
            ModifierKeyButton(
                label = "ALT",
                isActive = isAltActive,
                activeColor = theme.secondaryColor,
                theme = theme,
                onClick = onAltToggle
            )

            // Quick symbols
            KeyButton(label = "-", theme = theme, onClick = { onKeyAction("-") })
            KeyButton(label = "/", theme = theme, onClick = { onKeyAction("/") })
            KeyButton(label = "|", theme = theme, onClick = { onKeyAction("|") })
            KeyButton(label = "~", theme = theme, onClick = { onKeyAction("~") })

            // Arrow keys
            KeyIconButton(
                icon = Icons.Default.ArrowUpward,
                contentDesc = "UP",
                theme = theme,
                onClick = { onKeyAction("UP") }
            )
            KeyIconButton(
                icon = Icons.Default.ArrowDownward,
                contentDesc = "DOWN",
                theme = theme,
                onClick = { onKeyAction("DOWN") }
            )
            KeyIconButton(
                icon = Icons.Default.ArrowBack,
                contentDesc = "LEFT",
                theme = theme,
                onClick = { onKeyAction("LEFT") }
            )
            KeyIconButton(
                icon = Icons.Default.ArrowForward,
                contentDesc = "RIGHT",
                theme = theme,
                onClick = { onKeyAction("RIGHT") }
            )

            // Paste from clipboard
            KeyIconButton(
                icon = Icons.Default.ContentPaste,
                contentDesc = "PASTE",
                theme = theme,
                onClick = { onKeyAction("PASTE") }
            )
        }

        // Secondary Row 2 (Optional expanded row)
        AnimatedVisibility(visible = isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollStateRow2)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KeyButton(label = "$", theme = theme, onClick = { onKeyAction("$") })
                KeyButton(label = "&", theme = theme, onClick = { onKeyAction("&") })
                KeyButton(label = ";", theme = theme, onClick = { onKeyAction(";") })
                KeyButton(label = ">", theme = theme, onClick = { onKeyAction(">") })
                KeyButton(label = "<", theme = theme, onClick = { onKeyAction("<") })
                KeyButton(label = "\\", theme = theme, onClick = { onKeyAction("\\") })
                KeyButton(label = "\"", theme = theme, onClick = { onKeyAction("\"") })
                KeyButton(label = "'", theme = theme, onClick = { onKeyAction("'") })
                KeyButton(label = ":", theme = theme, onClick = { onKeyAction(":") })
                KeyButton(label = "_", theme = theme, onClick = { onKeyAction("_") })
                KeyButton(label = "=", theme = theme, onClick = { onKeyAction("=") })
                KeyButton(label = "HOME", theme = theme, onClick = { onKeyAction("HOME") })
                KeyButton(label = "END", theme = theme, onClick = { onKeyAction("END") })

                KeyIconButton(
                    icon = Icons.Default.DeleteSweep,
                    contentDesc = "CLEAR",
                    label = "CLS",
                    theme = theme,
                    onClick = { onKeyAction("CLEAR") }
                )
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    theme: TerminalTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val btnBg = if (theme.isDark) Color(0xFF21262D) else Color(0xFFF1F5F9)
    val btnBorder = if (theme.isDark) Color(0xFF30363D) else Color(0xFFCBD5E1)
    val textCol = if (theme.isDark) Color(0xFFC9D1D9) else Color(0xFF1E293B)

    Box(
        modifier = modifier
            .height(34.dp)
            .widthIn(min = 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(btnBg)
            .border(1.dp, btnBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textCol,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ModifierKeyButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    theme: TerminalTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val btnBg = when {
        isActive -> activeColor.copy(alpha = 0.25f)
        theme.isDark -> Color(0xFF21262D)
        else -> Color(0xFFF1F5F9)
    }
    val btnBorder = if (isActive) activeColor else if (theme.isDark) Color(0xFF30363D) else Color(0xFFCBD5E1)
    val textCol = if (isActive) activeColor else if (theme.isDark) Color(0xFFC9D1D9) else Color(0xFF1E293B)

    Box(
        modifier = modifier
            .height(34.dp)
            .widthIn(min = 44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(btnBg)
            .border(1.dp, btnBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textCol,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun KeyIconButton(
    icon: ImageVector,
    contentDesc: String,
    theme: TerminalTheme,
    onClick: () -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    val btnBg = if (theme.isDark) Color(0xFF21262D) else Color(0xFFF1F5F9)
    val btnBorder = if (theme.isDark) Color(0xFF30363D) else Color(0xFFCBD5E1)
    val iconColor = if (theme.isDark) Color(0xFFC9D1D9) else Color(0xFF1E293B)

    Box(
        modifier = modifier
            .height(34.dp)
            .widthIn(min = 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(btnBg)
            .border(1.dp, btnBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (label != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDesc,
                    tint = iconColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = label,
                    color = iconColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
