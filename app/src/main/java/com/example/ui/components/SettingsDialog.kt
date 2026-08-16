package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.CursorStyle
import com.example.data.model.TerminalTheme
import com.example.ui.theme.TerminalThemes

@Composable
fun SettingsDialog(
    currentTheme: TerminalTheme,
    fontSizeSp: Float,
    cursorStyle: CursorStyle,
    cursorBlink: Boolean,
    hapticEnabled: Boolean,
    extraKeysExpanded: Boolean,
    onThemeChange: (TerminalTheme) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onCursorStyleChange: (CursorStyle) -> Unit,
    onCursorBlinkToggle: () -> Unit,
    onHapticToggle: () -> Unit,
    onExtraKeysToggle: () -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("settings_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222B))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF00FF66))
                        Text(
                            text = "Terminal Settings",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                // Section: Themes
                Text(
                    text = "THEMES & COLORS",
                    color = Color(0xFF00FF66),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerminalThemes.allThemes.chunked(3).forEach { rowThemes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowThemes.forEach { theme ->
                                val isSelected = theme.id == currentTheme.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme.background)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF00FF66) else Color(0xFF3E4451),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onThemeChange(theme) }
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = theme.name.split(" ").first(),
                                            color = theme.foreground,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier.padding(top = 3.dp)
                                        ) {
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(theme.promptColor))
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(theme.secondaryColor))
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(theme.cursorColor))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section: Font Size
                Text(
                    text = "FONT SIZE (${fontSizeSp.toInt()} sp)",
                    color = Color(0xFF00FF66),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = fontSizeSp,
                    onValueChange = onFontSizeChange,
                    valueRange = 9f..22f,
                    steps = 12,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00FF66),
                        activeTrackColor = Color(0xFF00FF66)
                    )
                )

                // Section: Cursor Style
                Text(
                    text = "CURSOR STYLE",
                    color = Color(0xFF00FF66),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CursorStyle.values().forEach { style ->
                        val isSelected = style == cursorStyle
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCursorStyleChange(style) },
                            label = { Text(style.name, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00FF66),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF282C34),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                // Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cursor Blinking", color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    Switch(
                        checked = cursorBlink,
                        onCheckedChange = { onCursorBlinkToggle() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00FF66))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptic Feedback", color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    Switch(
                        checked = hapticEnabled,
                        onCheckedChange = { onHapticToggle() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00FF66))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Expanded Extra Keys", color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    Switch(
                        checked = extraKeysExpanded,
                        onCheckedChange = { onExtraKeysToggle() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00FF66))
                    )
                }

                // Clear History Button
                Button(
                    onClick = {
                        onClearHistory()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E2723), contentColor = Color(0xFFFF8A80)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Command History", fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
