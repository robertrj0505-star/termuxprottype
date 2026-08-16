package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InteractiveMode
import com.example.data.model.TerminalLine
import com.example.engine.AnsiParser
import com.example.ui.viewmodel.TerminalViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val sessions by viewModel.sessions.collectAsState()
    val activeSessionIdx by viewModel.activeSessionIndex.collectAsState()
    val activeTheme by viewModel.activeTheme.collectAsState()
    val fontSizeSp by viewModel.fontSizeSp.collectAsState()
    val cursorStyle by viewModel.cursorStyle.collectAsState()
    val cursorBlink by viewModel.cursorBlink.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val extraKeysExpanded by viewModel.extraKeysExpanded.collectAsState()
    val isCtrlActive by viewModel.isCtrlActive.collectAsState()
    val isAltActive by viewModel.isAltActive.collectAsState()

    val currentSession = sessions.getOrNull(activeSessionIdx) ?: return
    val nanoState by viewModel.nanoState.collectAsState()
    val savedScripts by viewModel.savedScripts.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPackagesDialog by remember { mutableStateOf(false) }
    var showScriptsDialog by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // Auto-scroll when new output lines arrive
    LaunchedEffect(currentSession.lines.size) {
        if (currentSession.lines.isNotEmpty()) {
            listState.animateScrollToItem(currentSession.lines.size)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF161B22),
                modifier = Modifier.width(310.dp)
            ) {
                SessionsDrawer(
                    sessions = sessions,
                    activeSessionIndex = activeSessionIdx,
                    onSelectSession = { idx ->
                        viewModel.switchSession(idx)
                        scope.launch { drawerState.close() }
                    },
                    onCreateSession = {
                        viewModel.createSession()
                        scope.launch { drawerState.close() }
                    },
                    onCloseSession = { idx ->
                        viewModel.closeSession(idx)
                    },
                    onRenameSession = { idx, name ->
                        viewModel.renameSession(idx, name)
                    },
                    onOpenPackages = {
                        showPackagesDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onOpenScripts = {
                        showScriptsDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onOpenSettings = {
                        showSettingsDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onQuickCommand = { cmd ->
                        viewModel.updateInput(androidx.compose.ui.text.input.TextFieldValue(cmd))
                        viewModel.submitCommand()
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = currentSession.title,
                                color = activeTheme.foreground,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            if (currentSession.isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = activeTheme.promptColor
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("menu_drawer_button")
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu Drawer",
                                tint = activeTheme.foreground
                            )
                        }
                    },
                    actions = {
                        // Copy all buffer
                        IconButton(
                            onClick = {
                                val allText = currentSession.lines.joinToString("\n") { it.text }
                                viewModel.copyOutputToClipboard(allText)
                            }
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy Output",
                                tint = activeTheme.foreground,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Search buffer
                        IconButton(onClick = { showSearchOverlay = !showSearchOverlay }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = activeTheme.foreground,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Packages
                        IconButton(onClick = { showPackagesDialog = true }) {
                            Icon(
                                Icons.Default.Apps,
                                contentDescription = "Packages",
                                tint = activeTheme.promptColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Settings
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = activeTheme.foreground,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = activeTheme.background.copy(alpha = 0.95f)
                    )
                )
            },
            modifier = modifier
                .fillMaxSize()
                .background(activeTheme.background)
                .imePadding()
                .testTag("terminal_main_screen")
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(activeTheme.background)
            ) {
                // Interactive Mode Switcher
                when (currentSession.interactiveMode) {
                    InteractiveMode.NANO -> {
                        NanoEditorView(
                            nanoState = nanoState,
                            theme = activeTheme,
                            fontSizeSp = fontSizeSp,
                            onContentChange = { viewModel.updateNanoContent(it) },
                            onSave = { viewModel.saveNanoFile() },
                            onExit = { viewModel.exitNanoEditor() }
                        )
                    }
                    InteractiveMode.MATRIX -> {
                        MatrixVisualizer(
                            onExit = { viewModel.exitVisualMode() }
                        )
                    }
                    InteractiveMode.SNAKE -> {
                        SnakeGameView(
                            onExit = { viewModel.exitVisualMode() }
                        )
                    }
                    InteractiveMode.SHELL, InteractiveMode.PYTHON_REPL -> {
                        // Standard Terminal Buffer + Prompt Input
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Search Overlay if active
                            AnimatedVisibility(visible = showSearchOverlay) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF21262D))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Search buffer...", color = Color.Gray, fontSize = 12.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        showSearchOverlay = false
                                        searchQuery = ""
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close Search", tint = Color.LightGray)
                                    }
                                }
                            }

                            // Output Buffer Lines
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .clickable { focusRequester.requestFocus() }
                            ) {
                                items(currentSession.lines, key = { it.id }) { line ->
                                    TerminalLineItem(
                                        line = line,
                                        theme = activeTheme,
                                        fontSizeSp = fontSizeSp,
                                        searchHighlight = searchQuery.takeIf { it.isNotBlank() }
                                    )
                                }

                                // Interactive Active Input Line
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val prompt = if (currentSession.interactiveMode == InteractiveMode.PYTHON_REPL) {
                                            ">>> "
                                        } else {
                                            viewModel.shellEngine.getPrompt()
                                        }

                                        Text(
                                            text = prompt,
                                            color = if (currentSession.interactiveMode == InteractiveMode.PYTHON_REPL) activeTheme.secondaryColor else activeTheme.promptColor,
                                            fontSize = fontSizeSp.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = (fontSizeSp * 1.35f).sp
                                        )

                                        BasicTextField(
                                            value = currentSession.inputValue,
                                            onValueChange = { viewModel.updateInput(it) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(focusRequester)
                                                .testTag("terminal_command_input"),
                                            textStyle = TextStyle(
                                                color = activeTheme.foreground,
                                                fontSize = fontSizeSp.sp,
                                                fontFamily = FontFamily.Monospace,
                                                lineHeight = (fontSizeSp * 1.35f).sp
                                            ),
                                            cursorBrush = SolidColor(activeTheme.cursorColor),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                            keyboardActions = KeyboardActions(onGo = { viewModel.submitCommand() }),
                                            singleLine = true
                                        )
                                    }
                                }
                            }

                            // Extra Keys Toolbar (Termux accessory row)
                            ExtraKeysToolbar(
                                theme = activeTheme,
                                isCtrlActive = isCtrlActive,
                                isAltActive = isAltActive,
                                isExpanded = extraKeysExpanded,
                                onCtrlToggle = { viewModel.toggleCtrl() },
                                onAltToggle = { viewModel.toggleAlt() },
                                onKeyAction = { actionKey -> viewModel.handleKeyAction(actionKey) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showSettingsDialog) {
        SettingsDialog(
            currentTheme = activeTheme,
            fontSizeSp = fontSizeSp,
            cursorStyle = cursorStyle,
            cursorBlink = cursorBlink,
            hapticEnabled = hapticEnabled,
            extraKeysExpanded = extraKeysExpanded,
            onThemeChange = { viewModel.setTheme(it) },
            onFontSizeChange = { viewModel.setFontSize(it) },
            onCursorStyleChange = { viewModel.setCursorStyle(it) },
            onCursorBlinkToggle = { viewModel.toggleCursorBlink() },
            onHapticToggle = { viewModel.toggleHaptic() },
            onExtraKeysToggle = { viewModel.toggleExtraKeys() },
            onClearHistory = { viewModel.clearAllHistory() },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showPackagesDialog) {
        PackagesDialog(
            packages = viewModel.repository.availablePackages,
            onRunPackage = { cmd ->
                viewModel.updateInput(androidx.compose.ui.text.input.TextFieldValue(cmd))
                viewModel.submitCommand()
            },
            onInstallPackage = { pkgId ->
                viewModel.updateInput(androidx.compose.ui.text.input.TextFieldValue("pkg install $pkgId"))
                viewModel.submitCommand()
            },
            onDismiss = { showPackagesDialog = false }
        )
    }

    if (showScriptsDialog) {
        ScriptsDialog(
            scripts = savedScripts,
            onRunScript = { script ->
                viewModel.runSavedScript(script)
            },
            onSaveScript = { name, desc, code, cat ->
                scope.launch {
                    viewModel.repository.saveScript(name, desc, code, cat)
                }
            },
            onDeleteScript = { script ->
                scope.launch {
                    viewModel.repository.deleteScript(script)
                }
            },
            onDismiss = { showScriptsDialog = false }
        )
    }
}

@Composable
private fun TerminalLineItem(
    line: TerminalLine,
    theme: com.example.data.model.TerminalTheme,
    fontSizeSp: Float,
    searchHighlight: String?
) {
    val annotated = remember(line.text, theme, searchHighlight) {
        val parsed = AnsiParser.parseAnsi(line.text, line.customColor ?: theme.foreground)
        if (searchHighlight != null && parsed.text.contains(searchHighlight, ignoreCase = true)) {
            buildAnnotatedString {
                append(parsed)
                val raw = parsed.text
                var start = raw.indexOf(searchHighlight, ignoreCase = true)
                while (start != -1) {
                    addStyle(
                        SpanStyle(background = Color(0xFFFFD600), color = Color.Black),
                        start,
                        start + searchHighlight.length
                    )
                    start = raw.indexOf(searchHighlight, start + searchHighlight.length, ignoreCase = true)
                }
            }
        } else {
            parsed
        }
    }

    if (line.isInput) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (line.prompt != null) {
                Text(
                    text = line.prompt,
                    color = theme.promptColor,
                    fontSize = fontSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = (fontSizeSp * 1.35f).sp
                )
            }
            Text(
                text = line.text,
                color = theme.foreground,
                fontSize = fontSizeSp.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = (fontSizeSp * 1.35f).sp
            )
        }
    } else {
        Text(
            text = annotated,
            fontSize = fontSizeSp.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = (fontSizeSp * 1.35f).sp,
            color = if (line.isError) Color(0xFFFF5252) else theme.foreground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 0.5.dp)
        )
    }
}
