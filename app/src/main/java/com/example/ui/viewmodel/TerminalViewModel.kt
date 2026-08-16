package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SavedScriptEntity
import com.example.data.local.TerminalDatabase
import com.example.data.model.CursorStyle
import com.example.data.model.InteractiveMode
import com.example.data.model.PackageItem
import com.example.data.model.TerminalLine
import com.example.data.model.TerminalTheme
import com.example.data.repository.TerminalRepository
import com.example.engine.AnsiParser
import com.example.engine.PythonMiniInterpreter
import com.example.engine.ShellEngine
import com.example.ui.theme.TerminalThemes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class SessionState(
    val id: String,
    val title: String,
    val lines: List<TerminalLine> = emptyList(),
    val inputValue: TextFieldValue = TextFieldValue(""),
    val historyIndex: Int = -1,
    val historyDraft: String = "",
    val isRunning: Boolean = false,
    val interactiveMode: InteractiveMode = InteractiveMode.SHELL,
    val interactivePayload: String? = null, // e.g. nano file path
    val currentWorkingDir: String = "~"
)

data class NanoState(
    val filePath: String = "",
    val fileName: String = "untitled.txt",
    val content: String = "",
    val isModified: Boolean = false,
    val cursorPosition: Int = 0,
    val statusMessage: String = ""
)

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val db = TerminalDatabase.getInstance(application)
    val repository = TerminalRepository(db.terminalDao())
    val shellEngine = ShellEngine(application, repository)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // Sessions
    private val _sessions = MutableStateFlow<List<SessionState>>(emptyList())
    val sessions: StateFlow<List<SessionState>> = _sessions.asStateFlow()

    private val _activeSessionIndex = MutableStateFlow(0)
    val activeSessionIndex: StateFlow<Int> = _activeSessionIndex.asStateFlow()

    // Modifiers
    private val _isCtrlActive = MutableStateFlow(false)
    val isCtrlActive: StateFlow<Boolean> = _isCtrlActive.asStateFlow()

    private val _isAltActive = MutableStateFlow(false)
    val isAltActive: StateFlow<Boolean> = _isAltActive.asStateFlow()

    // Settings
    private val _activeTheme = MutableStateFlow(TerminalThemes.TermuxClassic)
    val activeTheme: StateFlow<TerminalTheme> = _activeTheme.asStateFlow()

    private val _fontSizeSp = MutableStateFlow(13f)
    val fontSizeSp: StateFlow<Float> = _fontSizeSp.asStateFlow()

    private val _cursorStyle = MutableStateFlow(CursorStyle.BLOCK)
    val cursorStyle: StateFlow<CursorStyle> = _cursorStyle.asStateFlow()

    private val _cursorBlink = MutableStateFlow(true)
    val cursorBlink: StateFlow<Boolean> = _cursorBlink.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(true)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _soundEnabled = MutableStateFlow(false)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _extraKeysExpanded = MutableStateFlow(true)
    val extraKeysExpanded: StateFlow<Boolean> = _extraKeysExpanded.asStateFlow()

    // Nano Editor state
    private val _nanoState = MutableStateFlow(NanoState())
    val nanoState: StateFlow<NanoState> = _nanoState.asStateFlow()

    // Python REPL state
    private var pythonRepl: PythonMiniInterpreter? = null

    // DB flows
    val history = repository.history.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val savedScripts = repository.savedScripts.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val installedPackages = repository.installedPackages.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var runningJob: Job? = null

    init {
        createSession("1: bash")
        preloadDefaults()
    }

    private fun preloadDefaults() {
        viewModelScope.launch {
            repository.preloadDefaultPackages()
            repository.saveScript(
                name = "Neofetch & SysInfo",
                description = "Show system specs, memory, kernel, and ASCII banner",
                code = "neofetch",
                category = "System"
            )
            repository.saveScript(
                name = "Check Weather Forecast",
                description = "Fetch live ASCII weather for current location",
                code = "weather",
                category = "Utilities"
            )
            repository.saveScript(
                name = "Matrix Rain Screensaver",
                description = "Launch full-screen digital green falling code animation",
                code = "matrix",
                category = "Visual"
            )
            repository.saveScript(
                name = "Run Python Fibonacci",
                description = "Execute sample python Fibonacci generator script",
                code = "python ~/scripts/calc_fibonacci.py",
                category = "Python"
            )
            repository.saveScript(
                name = "List Android Storage",
                description = "Display disk free space and storage blocks",
                code = "df -h",
                category = "System"
            )
        }
    }

    fun createSession(title: String = "bash") {
        val id = "session_${System.currentTimeMillis()}"
        val initialLines = listOf(
            TerminalLine(
                text = "\u001B[1;32mWelcome to Termux Terminal!\u001B[0m\nType \u001B[1;36mhelp\u001B[0m for commands or \u001B[1;33mtpkg list\u001B[0m for packages.",
                isSystem = true
            )
        )
        val newSession = SessionState(
            id = id,
            title = if (title == "bash") "${_sessions.value.size + 1}: bash" else title,
            lines = initialLines,
            currentWorkingDir = "~"
        )
        _sessions.value = _sessions.value + newSession
        _activeSessionIndex.value = _sessions.value.size - 1
    }

    fun closeSession(index: Int) {
        val list = _sessions.value.toMutableList()
        if (list.size <= 1) {
            // Keep at least one session, just clear it
            val first = list[0]
            list[0] = first.copy(
                lines = listOf(TerminalLine(text = "\u001B[1;32mTerminal reset.\u001B[0m", isSystem = true)),
                inputValue = TextFieldValue(""),
                interactiveMode = InteractiveMode.SHELL
            )
            _sessions.value = list
            _activeSessionIndex.value = 0
            return
        }
        list.removeAt(index)
        _sessions.value = list
        if (_activeSessionIndex.value >= list.size) {
            _activeSessionIndex.value = list.size - 1
        }
    }

    fun switchSession(index: Int) {
        if (index in _sessions.value.indices) {
            _activeSessionIndex.value = index
        }
    }

    fun renameSession(index: Int, newTitle: String) {
        if (index in _sessions.value.indices && newTitle.isNotBlank()) {
            val list = _sessions.value.toMutableList()
            list[index] = list[index].copy(title = newTitle.trim())
            _sessions.value = list
        }
    }

    fun updateInput(value: TextFieldValue) {
        val activeIdx = _activeSessionIndex.value
        if (activeIdx !in _sessions.value.indices) return
        val current = _sessions.value[activeIdx]
        val list = _sessions.value.toMutableList()
        list[activeIdx] = current.copy(inputValue = value)
        _sessions.value = list
    }

    fun submitCommand() {
        val activeIdx = _activeSessionIndex.value
        if (activeIdx !in _sessions.value.indices) return
        val session = _sessions.value[activeIdx]
        val command = session.inputValue.text

        triggerHaptic()

        // Handle Interactive Modes
        when (session.interactiveMode) {
            InteractiveMode.PYTHON_REPL -> {
                handlePythonReplInput(command)
                return
            }
            InteractiveMode.NANO, InteractiveMode.MATRIX, InteractiveMode.SNAKE -> {
                // Ignore regular submit in visual interactive mode
                return
            }
            InteractiveMode.SHELL -> {
                // Regular shell command
            }
        }

        val promptStr = shellEngine.getPrompt()
        val inputLine = TerminalLine(
            text = command,
            isInput = true,
            prompt = promptStr
        )

        val updatedLines = session.lines + inputLine
        val list = _sessions.value.toMutableList()
        list[activeIdx] = session.copy(
            lines = updatedLines,
            inputValue = TextFieldValue(""),
            historyIndex = -1,
            historyDraft = "",
            isRunning = true
        )
        _sessions.value = list

        if (command.isBlank()) {
            list[activeIdx] = _sessions.value[activeIdx].copy(isRunning = false)
            _sessions.value = list
            return
        }

        runningJob?.cancel()
        runningJob = viewModelScope.launch {
            var exitCode = 0
            try {
                exitCode = shellEngine.executeCommand(
                    commandLine = command,
                    onOutput = { line ->
                        appendOutputLine(activeIdx, line)
                    },
                    onInteractiveModeChange = { mode, payload ->
                        setInteractiveMode(activeIdx, mode, payload)
                    }
                )
                repository.recordCommand(command, shellEngine.getRelativePath(shellEngine.currentDirectory), exitCode)
            } catch (e: Exception) {
                appendOutputLine(activeIdx, TerminalLine(text = "Error: ${e.localizedMessage}", isError = true))
                exitCode = 1
            } finally {
                val currentList = _sessions.value.toMutableList()
                if (activeIdx in currentList.indices) {
                    currentList[activeIdx] = currentList[activeIdx].copy(
                        isRunning = false,
                        currentWorkingDir = shellEngine.getRelativePath(shellEngine.currentDirectory)
                    )
                    _sessions.value = currentList
                }
            }
        }
    }

    private fun handlePythonReplInput(command: String) {
        val activeIdx = _activeSessionIndex.value
        val session = _sessions.value[activeIdx]

        if (command.trim() == "exit()" || command.trim() == "quit()" || command.trim() == "exit") {
            setInteractiveMode(activeIdx, InteractiveMode.SHELL, null)
            appendOutputLine(activeIdx, TerminalLine(text = "Exiting Python interactive REPL.", isSystem = true))
            clearInput(activeIdx)
            return
        }

        val inputLine = TerminalLine(text = command, isInput = true, prompt = ">>> ")
        appendOutputLine(activeIdx, inputLine)
        clearInput(activeIdx)

        if (pythonRepl == null) {
            pythonRepl = PythonMiniInterpreter()
        }

        val output = pythonRepl!!.execute(command)
        if (output.isNotBlank()) {
            appendOutputLine(activeIdx, TerminalLine(text = output))
        }
    }

    private fun appendOutputLine(sessionIdx: Int, line: TerminalLine) {
        val list = _sessions.value.toMutableList()
        if (sessionIdx !in list.indices) return
        val session = list[sessionIdx]

        // If clear sequence received
        if (line.text.contains("\u001B[2J") || line.text.contains("\u001B[H")) {
            list[sessionIdx] = session.copy(lines = emptyList())
        } else {
            list[sessionIdx] = session.copy(lines = session.lines + line)
        }
        _sessions.value = list
    }

    private fun clearInput(sessionIdx: Int) {
        val list = _sessions.value.toMutableList()
        if (sessionIdx in list.indices) {
            list[sessionIdx] = list[sessionIdx].copy(inputValue = TextFieldValue(""))
            _sessions.value = list
        }
    }

    fun setInteractiveMode(sessionIdx: Int, mode: InteractiveMode, payload: String?) {
        val list = _sessions.value.toMutableList()
        if (sessionIdx in list.indices) {
            list[sessionIdx] = list[sessionIdx].copy(
                interactiveMode = mode,
                interactivePayload = payload
            )
            _sessions.value = list

            if (mode == InteractiveMode.NANO && payload != null) {
                openNanoEditor(payload)
            } else if (mode == InteractiveMode.PYTHON_REPL) {
                pythonRepl = PythonMiniInterpreter()
                appendOutputLine(sessionIdx, TerminalLine(
                    text = "Python 3.11.4 (termux-light, default)\n[GCC 12.2.0] on linux\nType \"help\", \"copyright\", \"credits\" or \"license\" for more information. Type exit() to quit.",
                    isSystem = true
                ))
            }
        }
    }

    // Nano Editor operations
    private fun openNanoEditor(filePath: String) {
        val file = File(filePath)
        val content = if (file.exists()) file.readText() else ""
        _nanoState.value = NanoState(
            filePath = filePath,
            fileName = file.name,
            content = content,
            isModified = false,
            cursorPosition = 0,
            statusMessage = if (file.exists()) "Read ${content.lines().size} lines" else "[New File]"
        )
    }

    fun updateNanoContent(newContent: String) {
        _nanoState.value = _nanoState.value.copy(
            content = newContent,
            isModified = true,
            statusMessage = "Modified"
        )
    }

    fun saveNanoFile() {
        val state = _nanoState.value
        try {
            val file = File(state.filePath)
            file.writeText(state.content)
            _nanoState.value = state.copy(
                isModified = false,
                statusMessage = "Wrote ${state.content.lines().size} lines to ${file.name}"
            )
            triggerHaptic()
        } catch (e: Exception) {
            _nanoState.value = state.copy(
                statusMessage = "Error writing file: ${e.localizedMessage}"
            )
        }
    }

    fun exitNanoEditor() {
        val activeIdx = _activeSessionIndex.value
        setInteractiveMode(activeIdx, InteractiveMode.SHELL, null)
        appendOutputLine(activeIdx, TerminalLine(text = "[nano closed]", isSystem = true))
    }

    fun exitVisualMode() {
        val activeIdx = _activeSessionIndex.value
        setInteractiveMode(activeIdx, InteractiveMode.SHELL, null)
    }

    // Modifier and Extra Keys Handlers
    fun toggleCtrl() {
        _isCtrlActive.value = !_isCtrlActive.value
        triggerHaptic()
    }

    fun toggleAlt() {
        _isAltActive.value = !_isAltActive.value
        triggerHaptic()
    }

    fun handleKeyAction(actionKey: String) {
        triggerHaptic()
        val activeIdx = _activeSessionIndex.value
        if (activeIdx !in _sessions.value.indices) return
        val session = _sessions.value[activeIdx]
        val curText = session.inputValue.text
        val sel = session.inputValue.selection

        // Check if CTRL is active
        if (_isCtrlActive.value) {
            _isCtrlActive.value = false
            when (actionKey.uppercase()) {
                "C" -> {
                    // SIGINT
                    runningJob?.cancel()
                    if (session.interactiveMode != InteractiveMode.SHELL) {
                        setInteractiveMode(activeIdx, InteractiveMode.SHELL, null)
                    }
                    appendOutputLine(activeIdx, TerminalLine(text = "^C", isSystem = true))
                    val list = _sessions.value.toMutableList()
                    list[activeIdx] = session.copy(inputValue = TextFieldValue(""), isRunning = false)
                    _sessions.value = list
                    return
                }
                "L" -> {
                    // Clear screen
                    val list = _sessions.value.toMutableList()
                    list[activeIdx] = session.copy(lines = emptyList())
                    _sessions.value = list
                    return
                }
                "D" -> {
                    // EOF / Exit
                    if (curText.isEmpty()) {
                        closeSession(activeIdx)
                    } else {
                        // Delete char under cursor
                        if (sel.start < curText.length) {
                            val newText = curText.removeRange(sel.start, sel.start + 1)
                            updateInput(TextFieldValue(newText, TextRange(sel.start)))
                        }
                    }
                    return
                }
                "A" -> {
                    // Move cursor to start
                    updateInput(session.inputValue.copy(selection = TextRange(0)))
                    return
                }
                "E" -> {
                    // Move cursor to end
                    updateInput(session.inputValue.copy(selection = TextRange(curText.length)))
                    return
                }
                "U" -> {
                    // Cut line before cursor
                    val newText = curText.substring(sel.start)
                    updateInput(TextFieldValue(newText, TextRange(0)))
                    return
                }
                "K" -> {
                    // Cut line after cursor
                    val newText = curText.substring(0, sel.start)
                    updateInput(TextFieldValue(newText, TextRange(sel.start)))
                    return
                }
                "Z" -> {
                    // Suspend
                    appendOutputLine(activeIdx, TerminalLine(text = "[1]+ Stopped", isSystem = true))
                    return
                }
            }
        }

        when (actionKey) {
            "ESC" -> {
                _isCtrlActive.value = false
                _isAltActive.value = false
                if (session.interactiveMode != InteractiveMode.SHELL) {
                    setInteractiveMode(activeIdx, InteractiveMode.SHELL, null)
                }
            }
            "TAB" -> {
                handleTabAutoComplete(activeIdx, session)
            }
            "UP" -> {
                navigateHistory(activeIdx, session, up = true)
            }
            "DOWN" -> {
                navigateHistory(activeIdx, session, up = false)
            }
            "LEFT" -> {
                val newPos = (sel.start - 1).coerceAtLeast(0)
                updateInput(session.inputValue.copy(selection = TextRange(newPos)))
            }
            "RIGHT" -> {
                val newPos = (sel.start + 1).coerceAtMost(curText.length)
                updateInput(session.inputValue.copy(selection = TextRange(newPos)))
            }
            "HOME" -> {
                updateInput(session.inputValue.copy(selection = TextRange(0)))
            }
            "END" -> {
                updateInput(session.inputValue.copy(selection = TextRange(curText.length)))
            }
            "PASTE" -> {
                pasteFromClipboard(activeIdx, session)
            }
            "CLEAR" -> {
                val list = _sessions.value.toMutableList()
                list[activeIdx] = session.copy(lines = emptyList())
                _sessions.value = list
            }
            else -> {
                // Insert literal character (e.g. '|', '/', '~', '-', '_', '\')
                insertTextAtCursor(activeIdx, session, actionKey)
            }
        }
    }

    private fun handleTabAutoComplete(activeIdx: Int, session: SessionState) {
        val curText = session.inputValue.text
        val suggestions = shellEngine.getAutoCompleteSuggestions(curText)
        if (suggestions.isEmpty()) return

        if (suggestions.size == 1) {
            val tokens = curText.split(" ")
            val replacement = if (tokens.size <= 1) {
                "${suggestions[0]} "
            } else {
                val prefix = curText.substringBeforeLast(" ")
                val lastToken = tokens.last()
                val dirPrefix = if (lastToken.contains("/")) lastToken.substringBeforeLast("/") + "/" else ""
                "$prefix $dirPrefix${suggestions[0]}"
            }
            updateInput(TextFieldValue(replacement, TextRange(replacement.length)))
        } else {
            // Display suggestions
            appendOutputLine(activeIdx, TerminalLine(
                text = suggestions.joinToString("  ") { "\u001B[1;36m$it\u001B[0m" },
                isSystem = true
            ))
        }
    }

    private fun navigateHistory(activeIdx: Int, session: SessionState, up: Boolean) {
        val hist = history.value
        if (hist.isEmpty()) return

        val curIndex = session.historyIndex
        val draft = if (curIndex == -1) session.inputValue.text else session.historyDraft

        val newIndex = if (up) {
            (curIndex + 1).coerceAtMost(hist.size - 1)
        } else {
            (curIndex - 1).coerceAtLeast(-1)
        }

        val targetText = if (newIndex == -1) {
            draft
        } else {
            hist[newIndex].command
        }

        val list = _sessions.value.toMutableList()
        list[activeIdx] = session.copy(
            inputValue = TextFieldValue(targetText, TextRange(targetText.length)),
            historyIndex = newIndex,
            historyDraft = draft
        )
        _sessions.value = list
    }

    private fun insertTextAtCursor(activeIdx: Int, session: SessionState, char: String) {
        val curText = session.inputValue.text
        val sel = session.inputValue.selection
        val newText = curText.substring(0, sel.start) + char + curText.substring(sel.end)
        val newPos = sel.start + char.length
        updateInput(TextFieldValue(newText, TextRange(newPos)))
    }

    private fun pasteFromClipboard(activeIdx: Int, session: SessionState) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val pasteText = clip.getItemAt(0).text?.toString() ?: ""
            insertTextAtCursor(activeIdx, session, pasteText)
        }
    }

    fun copyOutputToClipboard(text: String) {
        val cleanText = AnsiParser.stripAnsi(text)
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Terminal Log", cleanText)
        clipboard.setPrimaryClip(clip)
        triggerHaptic()
    }

    // Settings actions
    fun setTheme(theme: TerminalTheme) {
        _activeTheme.value = theme
    }

    fun setFontSize(size: Float) {
        _fontSizeSp.value = size.coerceIn(9f, 24f)
    }

    fun setCursorStyle(style: CursorStyle) {
        _cursorStyle.value = style
    }

    fun toggleCursorBlink() {
        _cursorBlink.value = !_cursorBlink.value
    }

    fun toggleHaptic() {
        _hapticEnabled.value = !_hapticEnabled.value
    }

    fun toggleExtraKeys() {
        _extraKeysExpanded.value = !_extraKeysExpanded.value
    }

    fun runSavedScript(script: SavedScriptEntity) {
        val activeIdx = _activeSessionIndex.value
        val list = _sessions.value.toMutableList()
        if (activeIdx in list.indices) {
            list[activeIdx] = list[activeIdx].copy(inputValue = TextFieldValue(script.code))
            _sessions.value = list
            submitCommand()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    private fun triggerHaptic() {
        if (_hapticEnabled.value) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(15)
                }
            } catch (_: Exception) {}
        }
    }
}
