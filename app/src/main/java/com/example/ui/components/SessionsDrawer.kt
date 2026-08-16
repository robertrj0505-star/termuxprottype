package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ui.viewmodel.SessionState

@Composable
fun SessionsDrawer(
    sessions: List<SessionState>,
    activeSessionIndex: Int,
    onSelectSession: (Int) -> Unit,
    onCreateSession: () -> Unit,
    onCloseSession: (Int) -> Unit,
    onRenameSession: (Int, String) -> Unit,
    onOpenPackages: () -> Unit,
    onOpenScripts: () -> Unit,
    onOpenSettings: () -> Unit,
    onQuickCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var renameIndex by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(Color(0xFF161B22))
            .border(width = 1.dp, color = Color(0xFF30363D))
            .padding(16.dp)
            .testTag("sessions_drawer"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D1117))
                    .border(1.dp, Color(0xFF00FF66), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ">_",
                    color = Color(0xFF00FF66),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Column {
                Text(
                    text = "TERMINAL",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Linux Shell Emulator",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        HorizontalDivider(color = Color(0xFF30363D), thickness = 0.8.dp)

        // Sessions Header + New Session Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SESSIONS (${sessions.size})",
                color = Color(0xFF8B949E),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            IconButton(
                onClick = onCreateSession,
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFF238636), RoundedCornerShape(6.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Session", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // Sessions List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(sessions) { index, session ->
                val isActive = index == activeSessionIndex
                val itemBg = if (isActive) Color(0xFF21262D) else Color(0xFF0D1117)
                val itemBorder = if (isActive) Color(0xFF00FF66) else Color(0xFF30363D)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(itemBg)
                        .border(1.dp, itemBorder, RoundedCornerShape(8.dp))
                        .clickable { onSelectSession(index) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (session.isRunning) Color(0xFFE3B341) else if (isActive) Color(0xFF00FF66) else Color(0xFF8B949E))
                            )
                            Column {
                                Text(
                                    text = session.title,
                                    color = if (isActive) Color.White else Color(0xFFC9D1D9),
                                    fontSize = 13.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = session.currentWorkingDir,
                                    color = Color(0xFF58A6FF),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                onClick = {
                                    renameIndex = index
                                    renameText = session.title
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Color(0xFF8B949E), modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { onCloseSession(index) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFF85149), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF30363D), thickness = 0.8.dp)

        // Quick Navigation Buttons
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DrawerNavButton(
                icon = Icons.Default.Apps,
                label = "Package Manager (pkg)",
                color = Color(0xFF00FF66),
                onClick = onOpenPackages
            )
            DrawerNavButton(
                icon = Icons.Default.Bookmark,
                label = "Saved Scripts",
                color = Color(0xFF58A6FF),
                onClick = onOpenScripts
            )
            DrawerNavButton(
                icon = Icons.Default.Settings,
                label = "Settings & Themes",
                color = Color(0xFFD29922),
                onClick = onOpenSettings
            )
        }
    }

    // Rename Session Dialog
    if (renameIndex != null) {
        Dialog(onDismissRequest = { renameIndex = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E222B))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Rename Session",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { renameIndex = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30363D))
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (renameText.isNotBlank()) {
                                    onRenameSession(renameIndex!!, renameText)
                                }
                                renameIndex = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF21262D))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            color = Color(0xFFC9D1D9),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
