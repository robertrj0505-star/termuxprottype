package com.example.engine

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import com.example.data.model.InteractiveMode
import com.example.data.model.PackageItem
import com.example.data.model.TerminalLine
import com.example.data.repository.TerminalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.*

class ShellEngine(
    private val context: Context,
    private val repository: TerminalRepository
) {

    val homeDir: File = File(context.filesDir, "home").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    private val binDir = File(homeDir, "bin").apply { if (!exists()) mkdirs() }
    private val scriptsDir = File(homeDir, "scripts").apply { if (!exists()) mkdirs() }
    private val tmpDir = File(homeDir, "tmp").apply { if (!exists()) mkdirs() }

    var currentDirectory: File = homeDir

    val envVars = mutableMapOf<String, String>()
    val aliases = mutableMapOf<String, String>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    init {
        setupEnvironment()
        setupSampleFiles()
    }

    private fun setupEnvironment() {
        envVars["HOME"] = homeDir.absolutePath
        envVars["PREFIX"] = context.filesDir.absolutePath
        envVars["PATH"] = "${binDir.absolutePath}:/system/bin:/system/xbin:/vendor/bin:/sbin"
        envVars["SHELL"] = "/system/bin/sh"
        envVars["USER"] = "u0_a" + (context.applicationInfo.uid % 1000)
        envVars["TERM"] = "xterm-256color"
        envVars["TMPDIR"] = tmpDir.absolutePath
        envVars["LANG"] = "en_US.UTF-8"
        envVars["PS1"] = "\\u@localhost:\\w$ "

        aliases["ll"] = "ls -la"
        aliases["la"] = "ls -a"
        aliases["cls"] = "clear"
        aliases["md"] = "mkdir -p"
        aliases["py"] = "python"
        aliases["edit"] = "nano"
    }

    private fun setupSampleFiles() {
        try {
            val bashrc = File(homeDir, ".bashrc")
            if (!bashrc.exists()) {
                bashrc.writeText(
                    """
                    # Termux-like Bash Environment
                    export PS1='\u@localhost:\w$ '
                    alias ll='ls -la'
                    alias cls='clear'
                    alias py='python'
                    echo -e "\e[1;32mWelcome to Terminal!\e[0m Type \e[1;36mhelp\e[0m or \e[1;33mpkg list\e[0m to get started."
                    """.trimIndent()
                )
            }

            val readme = File(homeDir, "README.md")
            if (!readme.exists()) {
                readme.writeText(
                    """
                    # Welcome to Terminal (Termux for Android)
                    
                    A versatile terminal emulator and Linux shell environment for Android.
                    
                    ## Key Features:
                    - **Full Android Shell**: Run native commands like `ls`, `ps`, `top`, `df`, `ping`, `logcat`, `pm`.
                    - **Built-in Packages**: `pkg install python, neofetch, nano, cmatrix, curl, snake, 2048, tree, weather`.
                    - **Interactive Nano Editor**: Run `nano script.sh` to edit files with line numbers.
                    - **Extra Keys Toolbar**: Fast access to ESC, TAB (autocomplete), CTRL, ALT, cursor keys.
                    - **Custom Themes**: Termux Classic, Hacker Matrix, Dracula, Monokai, Nord, Cyberpunk.
                    - **Saved Scripts & Bookmarks**: Save scripts and run with one tap from the drawer.
                    
                    Type `help` for command documentation!
                    """.trimIndent()
                )
            }

            val samplePy = File(scriptsDir, "calc_fibonacci.py")
            if (!samplePy.exists()) {
                samplePy.writeText(
                    """
                    # Python Sample Script
                    print("=== Fibonacci Series Generator ===")
                    a, b = 0, 1
                    for i in range(10):
                        print(f"Fib({i}): {a}")
                        a, b = b, a + b
                    print("Done!")
                    """.trimIndent()
                )
            }

            val welcomeScript = File(scriptsDir, "sysinfo.sh")
            if (!welcomeScript.exists()) {
                welcomeScript.writeText(
                    """
                    echo "=== System Information ==="
                    uname -a
                    uptime
                    date
                    echo "Storage usage:"
                    df -h /data
                    """.trimIndent()
                )
            }
        } catch (_: Exception) {}
    }

    fun getPrompt(): String {
        val user = envVars["USER"] ?: "u0_a100"
        val path = getRelativePath(currentDirectory)
        return "$user@localhost:$path$ "
    }

    fun getRelativePath(dir: File): String {
        val homePath = homeDir.absolutePath
        val curPath = dir.absolutePath
        return when {
            curPath == homePath -> "~"
            curPath.startsWith(homePath) -> "~" + curPath.removePrefix(homePath)
            else -> curPath
        }
    }

    suspend fun executeCommand(
        commandLine: String,
        onOutput: suspend (TerminalLine) -> Unit,
        onInteractiveModeChange: ((InteractiveMode, String?) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val rawCommand = commandLine.trim()
        if (rawCommand.isEmpty()) return@withContext 0

        // Handle alias substitution
        var resolvedCmd = rawCommand
        val firstWord = rawCommand.split(Regex("\\s+")).firstOrNull() ?: ""
        if (aliases.containsKey(firstWord)) {
            val aliasValue = aliases[firstWord]!!
            resolvedCmd = rawCommand.replaceFirst(firstWord, aliasValue)
        }

        // Handle multiple commands separated by ; or && or ||
        if (resolvedCmd.contains("&&") || resolvedCmd.contains("||") || resolvedCmd.contains(";")) {
            return@withContext executeChainedCommands(resolvedCmd, onOutput, onInteractiveModeChange)
        }

        // Handle redirection (>) or (>>)
        if (resolvedCmd.contains(">")) {
            return@withContext executeRedirection(resolvedCmd, onOutput)
        }

        // Handle pipes (|)
        if (resolvedCmd.contains("|")) {
            return@withContext executePipeline(resolvedCmd, onOutput)
        }

        // Variable expansion
        val expandedCmd = expandVariables(resolvedCmd)
        val tokens = tokenizeCommand(expandedCmd)
        if (tokens.isEmpty()) return@withContext 0

        val cmd = tokens[0]
        val args = tokens.drop(1)

        // Built-in commands dispatcher
        when (cmd) {
            "help", "man" -> {
                showHelp(args, onOutput)
                0
            }
            "clear", "cls" -> {
                // Signal clear
                onOutput(TerminalLine(text = "\u001B[2J\u001B[H", isSystem = true))
                0
            }
            "tpkg", "pkg", "apt" -> {
                handlePkgCommand(args, onOutput)
            }
            "htop", "top" -> {
                handleHtop(args, onOutput)
            }
            "jq" -> {
                handleJq(args, onOutput)
            }
            "speedtest" -> {
                handleSpeedtest(args, onOutput)
            }
            "nmap" -> {
                handleNmap(args, onOutput)
            }
            "todo" -> {
                handleTodo(args, onOutput)
            }
            "whois" -> {
                handleWhois(args, onOutput)
            }
            "bc" -> {
                handleBc(args, onOutput)
            }
            "cd" -> {
                handleCd(args, onOutput)
            }
            "pwd" -> {
                onOutput(TerminalLine(text = currentDirectory.absolutePath))
                0
            }
            "ls" -> {
                handleLs(args, onOutput)
            }
            "neofetch", "fastfetch" -> {
                showNeofetch(onOutput)
                0
            }
            "cat" -> {
                handleCat(args, onOutput)
            }
            "echo" -> {
                handleEcho(args, onOutput)
            }
            "mkdir" -> {
                handleMkdir(args, onOutput)
            }
            "rm" -> {
                handleRm(args, onOutput)
            }
            "touch" -> {
                handleTouch(args, onOutput)
            }
            "cp" -> {
                handleCp(args, onOutput)
            }
            "mv" -> {
                handleMv(args, onOutput)
            }
            "tree" -> {
                handleTree(args, onOutput)
            }
            "grep" -> {
                handleGrep(args, onOutput)
            }
            "find" -> {
                handleFind(args, onOutput)
            }
            "head" -> {
                handleHead(args, onOutput)
            }
            "tail" -> {
                handleTail(args, onOutput)
            }
            "wc" -> {
                handleWc(args, onOutput)
            }
            "curl", "wget" -> {
                handleCurl(args, onOutput)
            }
            "weather" -> {
                handleWeather(args, onOutput)
            }
            "cowsay" -> {
                handleCowsay(args, onOutput)
            }
            "figlet" -> {
                handleFiglet(args, onOutput)
            }
            "calc" -> {
                handleCalc(args, onOutput)
            }
            "base64" -> {
                handleBase64(args, onOutput)
            }
            "sha256sum" -> {
                handleSha256(args, onOutput)
            }
            "hexdump" -> {
                handleHexdump(args, onOutput)
            }
            "fortune" -> {
                showFortune(onOutput)
                0
            }
            "date" -> {
                val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US)
                onOutput(TerminalLine(text = sdf.format(Date())))
                0
            }
            "uptime" -> {
                val uptimeMs = SystemClock.elapsedRealtime()
                val hours = uptimeMs / (1000 * 60 * 60)
                val minutes = (uptimeMs / (1000 * 60)) % 60
                val seconds = (uptimeMs / 1000) % 60
                val curTime = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                onOutput(TerminalLine(text = "$curTime up $hours:$minutes,  load average: 0.18, 0.22, 0.25"))
                0
            }
            "uname" -> {
                val opt = args.firstOrNull() ?: ""
                if (opt == "-a") {
                    onOutput(TerminalLine(text = "Linux localhost ${System.getProperty("os.version")} ${Build.HARDWARE} ${Build.SUPPORTED_ABIS.firstOrNull() ?: "aarch64"} GNU/Linux (Android ${Build.VERSION.RELEASE})"))
                } else {
                    onOutput(TerminalLine(text = "Linux"))
                }
                0
            }
            "whoami" -> {
                onOutput(TerminalLine(text = envVars["USER"] ?: "u0_a100"))
                0
            }
            "env", "printenv" -> {
                envVars.forEach { (k, v) ->
                    onOutput(TerminalLine(text = "$k=$v"))
                }
                0
            }
            "export" -> {
                if (args.isEmpty()) {
                    envVars.forEach { (k, v) ->
                        onOutput(TerminalLine(text = "declare -x $k=\"$v\""))
                    }
                } else {
                    val pair = args[0].split("=", limit = 2)
                    if (pair.size == 2) {
                        envVars[pair[0]] = pair[1].trim('\"', '\'')
                    }
                }
                0
            }
            "alias" -> {
                if (args.isEmpty()) {
                    aliases.forEach { (k, v) ->
                        onOutput(TerminalLine(text = "alias $k='$v'"))
                    }
                } else {
                    val pair = args[0].split("=", limit = 2)
                    if (pair.size == 2) {
                        aliases[pair[0]] = pair[1].trim('\"', '\'')
                    }
                }
                0
            }
            "history" -> {
                val histList = repository.history.first()
                histList.take(50).reversed().forEachIndexed { index, item ->
                    onOutput(TerminalLine(text = String.format("%4d  %s", index + 1, item.command)))
                }
                0
            }
            "matrix", "cmatrix" -> {
                onInteractiveModeChange?.invoke(InteractiveMode.MATRIX, null)
                0
            }
            "snake" -> {
                onInteractiveModeChange?.invoke(InteractiveMode.SNAKE, null)
                0
            }
            "2048" -> {
                onInteractiveModeChange?.invoke(InteractiveMode.SNAKE, "2048")
                0
            }
            "pipes" -> {
                onInteractiveModeChange?.invoke(InteractiveMode.MATRIX, "pipes")
                0
            }
            "nano", "edit", "vi", "vim" -> {
                val targetFile = if (args.isNotEmpty()) resolveFile(args[0]) else File(currentDirectory, "untitled.txt")
                onInteractiveModeChange?.invoke(InteractiveMode.NANO, targetFile.absolutePath)
                0
            }
            "python", "python3", "py" -> {
                if (args.isEmpty()) {
                    onInteractiveModeChange?.invoke(InteractiveMode.PYTHON_REPL, null)
                    0
                } else {
                    handlePythonScript(args, onOutput)
                }
            }
            "sh", "bash" -> {
                if (args.isNotEmpty()) {
                    val scriptFile = resolveFile(args[0])
                    if (scriptFile.exists()) {
                        executeScriptFile(scriptFile, onOutput, onInteractiveModeChange)
                    } else {
                        onOutput(TerminalLine(text = "sh: cannot open ${args[0]}: No such file", isError = true))
                        1
                    }
                } else {
                    onOutput(TerminalLine(text = "Termux bash subshell initialized. Type 'exit' to return."))
                    0
                }
            }
            "exit" -> {
                onOutput(TerminalLine(text = "\u001B[1;33mSession logged out.\u001B[0m"))
                0
            }
            else -> {
                // If the command is an executable script in cwd or path
                val localExec = resolveFile(cmd)
                if (localExec.exists() && localExec.isFile) {
                    executeScriptFile(localExec, onOutput, onInteractiveModeChange)
                } else {
                    // Fallback to real native Android process
                    executeNativeProcess(expandedCmd, onOutput)
                }
            }
        }
    }

    private suspend fun executeNativeProcess(
        commandLine: String,
        onOutput: suspend (TerminalLine) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        try {
            val processBuilder = ProcessBuilder("/system/bin/sh", "-c", commandLine)
            processBuilder.directory(currentDirectory)

            val pEnv = processBuilder.environment()
            pEnv.putAll(envVars)

            val process = processBuilder.start()

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            var line: String?
            while (stdoutReader.readLine().also { line = it } != null) {
                onOutput(TerminalLine(text = line ?: ""))
            }

            var errLine: String?
            while (stderrReader.readLine().also { errLine = it } != null) {
                onOutput(TerminalLine(text = errLine ?: "", isError = true))
            }

            process.waitFor()
            val exitCode = process.exitValue()
            if (exitCode != 0 && exitCode != 127) {
                // Exit code logged
            }
            exitCode
        } catch (e: Exception) {
            onOutput(TerminalLine(text = "term: command not found: ${commandLine.split(" ").firstOrNull() ?: commandLine}. Type 'help' for built-in utilities.", isError = true))
            127
        }
    }

    private suspend fun executeChainedCommands(
        commandLine: String,
        onOutput: suspend (TerminalLine) -> Unit,
        onInteractiveModeChange: ((InteractiveMode, String?) -> Unit)?
    ): Int {
        // Parse chained commands
        val parts = mutableListOf<Pair<String, String>>() // (connector, command)
        var remaining = commandLine.trim()
        var lastConnector = ";"

        while (remaining.isNotEmpty()) {
            val andIdx = remaining.indexOf("&&")
            val orIdx = remaining.indexOf("||")
            val semiIdx = remaining.indexOf(";")

            val indices = listOf(
                if (andIdx != -1) andIdx to "&&" else null,
                if (orIdx != -1) orIdx to "||" else null,
                if (semiIdx != -1) semiIdx to ";" else null
            ).filterNotNull().sortedBy { it.first }

            if (indices.isEmpty()) {
                parts.add(lastConnector to remaining.trim())
                break
            } else {
                val (firstIdx, sep) = indices.first()
                val chunk = remaining.substring(0, firstIdx).trim()
                parts.add(lastConnector to chunk)
                lastConnector = sep
                remaining = remaining.substring(firstIdx + sep.length).trim()
            }
        }

        var lastExit = 0
        for ((connector, cmdStr) in parts) {
            if (cmdStr.isEmpty()) continue
            when (connector) {
                ";" -> {
                    lastExit = executeCommand(cmdStr, onOutput, onInteractiveModeChange)
                }
                "&&" -> {
                    if (lastExit == 0) {
                        lastExit = executeCommand(cmdStr, onOutput, onInteractiveModeChange)
                    }
                }
                "||" -> {
                    if (lastExit != 0) {
                        lastExit = executeCommand(cmdStr, onOutput, onInteractiveModeChange)
                    }
                }
            }
        }
        return lastExit
    }

    private suspend fun executePipeline(
        commandLine: String,
        onOutput: suspend (TerminalLine) -> Unit
    ): Int {
        val stages = commandLine.split("|").map { it.trim() }
        if (stages.size < 2) return executeCommand(commandLine, onOutput)

        // Capture output of stage 1 into string buffer
        val buffer = StringBuilder()
        executeCommand(stages[0], onOutput = { line ->
            buffer.append(line.text).append("\n")
        })

        val input = buffer.toString()
        val stage2Tokens = tokenizeCommand(stages[1])
        if (stage2Tokens.isEmpty()) return 0

        when (stage2Tokens[0]) {
            "grep" -> {
                val pattern = stage2Tokens.getOrNull(1) ?: ""
                val ignoreCase = stage2Tokens.contains("-i")
                input.lines().forEach { l ->
                    if (l.isNotEmpty() && l.contains(pattern, ignoreCase = ignoreCase)) {
                        val highlighted = l.replace(pattern, "\u001B[1;31m$pattern\u001B[0m", ignoreCase = ignoreCase)
                        onOutput(TerminalLine(text = highlighted))
                    }
                }
                return 0
            }
            "wc" -> {
                val linesCount = input.lines().filter { it.isNotEmpty() }.size
                val wordsCount = input.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                val bytesCount = input.toByteArray().size
                onOutput(TerminalLine(text = String.format("%8d %8d %8d", linesCount, wordsCount, bytesCount)))
                return 0
            }
            "head" -> {
                val n = (stage2Tokens.indexOf("-n").takeIf { it != -1 }?.let { stage2Tokens.getOrNull(it + 1)?.toIntOrNull() }) ?: 10
                input.lines().take(n).forEach { onOutput(TerminalLine(text = it)) }
                return 0
            }
            "tail" -> {
                val n = (stage2Tokens.indexOf("-n").takeIf { it != -1 }?.let { stage2Tokens.getOrNull(it + 1)?.toIntOrNull() }) ?: 10
                val allLines = input.lines()
                allLines.takeLast(n.coerceAtMost(allLines.size)).forEach { onOutput(TerminalLine(text = it)) }
                return 0
            }
            else -> {
                // Pipe to native process or command
                return executeNativeProcess(commandLine, onOutput)
            }
        }
    }

    private suspend fun executeRedirection(
        commandLine: String,
        onOutput: suspend (TerminalLine) -> Unit
    ): Int {
        val isAppend = commandLine.contains(">>")
        val sep = if (isAppend) ">>" else ">"
        val parts = commandLine.split(sep, limit = 2)
        val cmd = parts[0].trim()
        val targetPath = parts[1].trim()
        val targetFile = resolveFile(targetPath)

        val outputBuffer = StringBuilder()
        val exitCode = executeCommand(cmd, onOutput = { line ->
            outputBuffer.append(AnsiParser.stripAnsi(line.text)).append("\n")
        })

        try {
            if (isAppend) {
                targetFile.appendText(outputBuffer.toString())
            } else {
                targetFile.writeText(outputBuffer.toString())
            }
            onOutput(TerminalLine(text = "\u001B[32mOutput written to ${targetFile.name}\u001B[0m"))
        } catch (e: Exception) {
            onOutput(TerminalLine(text = "Cannot write to ${targetFile.name}: ${e.localizedMessage}", isError = true))
        }
        return exitCode
    }

    private suspend fun executeScriptFile(
        file: File,
        onOutput: suspend (TerminalLine) -> Unit,
        onInteractiveModeChange: ((InteractiveMode, String?) -> Unit)?
    ): Int {
        if (!file.exists()) {
            onOutput(TerminalLine(text = "Cannot execute ${file.name}: No such file", isError = true))
            return 1
        }
        val lines = file.readLines()
        var lastCode = 0
        for (l in lines) {
            val trimmed = l.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            lastCode = executeCommand(trimmed, onOutput, onInteractiveModeChange)
        }
        return lastCode
    }

    // Built-in command implementations

    private suspend fun showHelp(args: List<String>, onOutput: suspend (TerminalLine) -> Unit) {
        onOutput(TerminalLine(text = "\u001B[1;36m=== TERMINAL & LINUX SHELL ENVIRONMENT ===\u001B[0m"))
        onOutput(TerminalLine(text = "\u001B[1;32mPackage Management (tpkg / pkg / apt):\u001B[0m"))
        onOutput(TerminalLine(text = "  \u001B[1;33mtpkg list\u001B[0m               - List all available & installed packages"))
        onOutput(TerminalLine(text = "  \u001B[1;33mtpkg list-installed\u001B[0m     - List only installed packages"))
        onOutput(TerminalLine(text = "  \u001B[1;33mtpkg install <pkg...>\u001B[0m   - Install one or more utility packages"))
        onOutput(TerminalLine(text = "  \u001B[1;33mtpkg uninstall <pkg>\u001B[0m    - Remove / uninstall a package"))
        onOutput(TerminalLine(text = "  \u001B[1;33mtpkg show <pkg>\u001B[0m         - Display package metadata & information"))
        onOutput(TerminalLine(text = "  \u001B[1;33mtpkg search <keyword>\u001B[0m   - Search package repositories"))
        onOutput(TerminalLine(text = "  \u001B[1;33mtpkg update / upgrade\u001B[0m   - Update simulated repository indexes"))
        onOutput(TerminalLine(text = ""))
        onOutput(TerminalLine(text = "\u001B[1;32mEssential Tools & Utilities:\u001B[0m"))
        onOutput(TerminalLine(text = "  \u001B[1;33mneofetch\u001B[0m                       - Display Android specs & ASCII logo"))
        onOutput(TerminalLine(text = "  \u001B[1;33mnano\u001B[0m <file>                    - Interactive full-screen text editor"))
        onOutput(TerminalLine(text = "  \u001B[1;33mpython\u001B[0m [script.py]             - Python interactive REPL & runner"))
        onOutput(TerminalLine(text = "  \u001B[1;33mhtop\u001B[0m                           - Interactive system process & memory monitor"))
        onOutput(TerminalLine(text = "  \u001B[1;33mspeedtest\u001B[0m                      - Internet bandwidth benchmark tool"))
        onOutput(TerminalLine(text = "  \u001B[1;33mnmap\u001B[0m [host]                    - Network port scanner & service exploration"))
        onOutput(TerminalLine(text = "  \u001B[1;33mtodo\u001B[0m <add|list|done|rm|clear>  - CLI task manager"))
        onOutput(TerminalLine(text = "  \u001B[1;33mjq\u001B[0m <expr> [file.json]          - Command-line JSON processor & pretty-printer"))
        onOutput(TerminalLine(text = "  \u001B[1;33mwhois\u001B[0m <domain>                 - Domain & IP registry lookup"))
        onOutput(TerminalLine(text = "  \u001B[1;33mmatrix\u001B[0m                         - Falling digital green rain animation"))
        onOutput(TerminalLine(text = "  \u001B[1;33msnake\u001B[0m / \u001B[1;33m2048\u001B[0m                   - Classic terminal arcade games"))
        onOutput(TerminalLine(text = "  \u001B[1;33mcurl\u001B[0m <url>                     - Transfer data from web / REST API"))
        onOutput(TerminalLine(text = "  \u001B[1;33mweather\u001B[0m [city]                 - Live ASCII weather forecast"))
        onOutput(TerminalLine(text = "  \u001B[1;33mtree\u001B[0m [dir]                     - Directory visual hierarchy graph"))
        onOutput(TerminalLine(text = "  \u001B[1;33mcalc\u001B[0m / \u001B[1;33mbc\u001B[0m <expression>         - Scientific math calculator"))
        onOutput(TerminalLine(text = "  \u001B[1;33mcowsay\u001B[0m <text> / \u001B[1;33mfiglet\u001B[0m <text>   - ASCII art banners & speech bubbles"))
        onOutput(TerminalLine(text = ""))
        onOutput(TerminalLine(text = "\u001B[1;32mFile & Shell Navigation:\u001B[0m"))
        onOutput(TerminalLine(text = "  \u001B[37mls, cd, pwd, cat, mkdir, rm, cp, mv, touch, grep, find, head, tail, wc\u001B[0m"))
        onOutput(TerminalLine(text = "  \u001B[37mexport, env, alias, history, date, uptime, uname, whoami, clear, exit\u001B[0m"))
        onOutput(TerminalLine(text = ""))
        onOutput(TerminalLine(text = "\u001B[1;32mAndroid Native Commands:\u001B[0m"))
        onOutput(TerminalLine(text = "  \u001B[37mps, top, df, ping, logcat, getprop, ifconfig, ip, pm, dumpsys\u001B[0m"))
        onOutput(TerminalLine(text = "\u001B[90mTip: Use the Extra Keys toolbar for TAB auto-complete, CTRL+C, and arrows.\u001B[0m"))
    }

    private suspend fun showNeofetch(onOutput: suspend (TerminalLine) -> Unit) {
        val runtime = Runtime.getRuntime()
        val totalMemMb = runtime.totalMemory() / (1024 * 1024)
        val freeMemMb = runtime.freeMemory() / (1024 * 1024)
        val usedMemMb = totalMemMb - freeMemMb
        val uptimeHours = SystemClock.elapsedRealtime() / (1000 * 60 * 60)
        val uptimeMinutes = (SystemClock.elapsedRealtime() / (1000 * 60)) % 60
        val installedCount = repository.getInstalledPackageIds().size

        val banner = listOf(
            "\u001B[1;32m       .-''''-.        \u001B[1;37m${envVars["USER"]}@localhost\u001B[0m",
            "\u001B[1;32m      /  .---.  \\      \u001B[90m-------------------------\u001B[0m",
            "\u001B[1;32m     |  /     \\  |     \u001B[1;33mOS:\u001B[0m Android ${Build.VERSION.RELEASE} (${Build.ID})",
            "\u001B[1;32m     | | () () | |     \u001B[1;33mHost:\u001B[0m ${Build.MANUFACTURER} ${Build.MODEL}",
            "\u001B[1;32m     |  \\     /  |     \u001B[1;33mKernel:\u001B[0m Linux ${System.getProperty("os.version")}",
            "\u001B[1;32m      \\  '---'  /      \u001B[1;33mUptime:\u001B[0m ${uptimeHours}h ${uptimeMinutes}m",
            "\u001B[1;32m  .---'--.   .--'---.  \u001B[1;33mPackages:\u001B[0m $installedCount (tpkg)",
            "\u001B[1;32m /  |__|  \\ /  |__|  \\ \u001B[1;33mShell:\u001B[0m Termux-Bash 5.2",
            "\u001B[1;32m|   |  |   |   |  |   |\u001B[1;33mCPU:\u001B[0m ${Build.HARDWARE} (${Runtime.getRuntime().availableProcessors()} cores)",
            "\u001B[1;32m|   |  |   |   |  |   |\u001B[1;33mMemory:\u001B[0m ${usedMemMb}MB / ${totalMemMb}MB",
            "\u001B[1;32m \\__|  |__/ \\__|  |__/ \u001B[1;33mTerminal:\u001B[0m xterm-256color",
            "                       \u001B[40m   \u001B[41m   \u001B[42m   \u001B[43m   \u001B[44m   \u001B[45m   \u001B[46m   \u001B[47m   \u001B[0m",
            "                       \u001B[100m   \u001B[101m   \u001B[102m   \u001B[103m   \u001B[104m   \u001B[105m   \u001B[106m   \u001B[107m   \u001B[0m"
        )

        for (line in banner) {
            onOutput(TerminalLine(text = line))
        }
    }

    private suspend fun handlePkgCommand(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        if (args.isEmpty() || args[0] == "help" || args[0] == "--help" || args[0] == "-h") {
            onOutput(TerminalLine(text = "\u001B[1;36mTermux Package Manager (tpkg / pkg / apt)\u001B[0m"))
            onOutput(TerminalLine(text = "Usage: tpkg <command> [arguments...]"))
            onOutput(TerminalLine(text = ""))
            onOutput(TerminalLine(text = "\u001B[1;32mCommands:\u001B[0m"))
            onOutput(TerminalLine(text = "  \u001B[1;33mlist\u001B[0m                   - List all available packages with installation status"))
            onOutput(TerminalLine(text = "  \u001B[1;33mlist-installed\u001B[0m         - List only installed packages"))
            onOutput(TerminalLine(text = "  \u001B[1;33minstall <pkg...>\u001B[0m       - Install one or more utility packages"))
            onOutput(TerminalLine(text = "  \u001B[1;33muninstall <pkg...>\u001B[0m     - Uninstall / remove packages (alias: remove, purge)"))
            onOutput(TerminalLine(text = "  \u001B[1;33mshow <pkg>\u001B[0m             - Show detailed package info and description (alias: info)"))
            onOutput(TerminalLine(text = "  \u001B[1;33msearch <keyword>\u001B[0m       - Search available packages by name or description"))
            onOutput(TerminalLine(text = "  \u001B[1;33mupdate / upgrade\u001B[0m       - Update package index and verify latest versions"))
            onOutput(TerminalLine(text = "  \u001B[1;33mreinstall <pkg>\u001B[0m        - Reinstall an existing package"))
            onOutput(TerminalLine(text = "  \u001B[1;33mhelp\u001B[0m                   - Show this help summary"))
            return 0
        }

        val action = args[0].lowercase()
        val installedSet = repository.getInstalledPackageIds()

        when (action) {
            "list", "list-all" -> {
                onOutput(TerminalLine(text = "\u001B[1;32mListing all packages...\u001B[0m"))
                repository.availablePackages.forEach { pkg ->
                    val isInstalled = installedSet.contains(pkg.id.lowercase())
                    val status = if (isInstalled) {
                        "\u001B[1;32m[installed]\u001B[0m"
                    } else {
                        "\u001B[1;34m[available]\u001B[0m"
                    }
                    onOutput(TerminalLine(text = "\u001B[1;33m${pkg.id}\u001B[0m/${pkg.category.lowercase()} \u001B[90m${pkg.version}\u001B[0m aarch64 $status"))
                    onOutput(TerminalLine(text = "  ${pkg.description} (\u001B[36m${pkg.size}\u001B[0m)"))
                }
                0
            }
            "list-installed", "installed" -> {
                onOutput(TerminalLine(text = "\u001B[1;32mListing installed packages...\u001B[0m"))
                val installedPkgs = repository.availablePackages.filter { installedSet.contains(it.id.lowercase()) }
                if (installedPkgs.isEmpty()) {
                    onOutput(TerminalLine(text = "No packages currently installed. Run 'tpkg list' to see available packages."))
                } else {
                    installedPkgs.forEach { pkg ->
                        onOutput(TerminalLine(text = "\u001B[1;33m${pkg.id}\u001B[0m/${pkg.category.lowercase()} \u001B[90m${pkg.version}\u001B[0m aarch64 \u001B[1;32m[installed]\u001B[0m"))
                        onOutput(TerminalLine(text = "  ${pkg.description} (Run with: \u001B[36m${pkg.command}\u001B[0m)"))
                    }
                }
                0
            }
            "install", "add", "reinstall" -> {
                val pkgNames = args.drop(1)
                if (pkgNames.isEmpty()) {
                    onOutput(TerminalLine(text = "tpkg: missing package name. Example: tpkg install htop", isError = true))
                    return 1
                }
                for (pkgName in pkgNames) {
                    val pkg = repository.availablePackages.find { it.id.equals(pkgName, ignoreCase = true) }
                    if (pkg == null) {
                        onOutput(TerminalLine(text = "E: Unable to locate package '$pkgName'", isError = true))
                        onOutput(TerminalLine(text = "Try 'tpkg search $pkgName' or 'tpkg list' to find packages."))
                        continue
                    }

                    val isAlreadyInstalled = installedSet.contains(pkg.id.lowercase())
                    if (isAlreadyInstalled && action != "reinstall") {
                        onOutput(TerminalLine(text = "${pkg.id} is already the newest version (${pkg.version})."))
                        continue
                    }

                    onOutput(TerminalLine(text = "Reading package lists... Done"))
                    onOutput(TerminalLine(text = "Building dependency tree... Done"))
                    onOutput(TerminalLine(text = "Calculating upgrade... Done"))
                    onOutput(TerminalLine(text = "The following NEW package will be installed:"))
                    onOutput(TerminalLine(text = "  \u001B[1;33m${pkg.id}\u001B[0m (${pkg.version})"))
                    onOutput(TerminalLine(text = "0 upgraded, 1 newly installed, 0 to remove and 0 not upgraded."))
                    onOutput(TerminalLine(text = "Need to get ${pkg.size} of archives."))
                    onOutput(TerminalLine(text = "Get:1 https://packages.termux.dev/apt/termux-main stable aarch64 ${pkg.id} ${pkg.version} [${pkg.size}]"))
                    onOutput(TerminalLine(text = "Preparing to unpack .../${pkg.id}_${pkg.version}_aarch64.deb ..."))
                    onOutput(TerminalLine(text = "Unpacking ${pkg.id} (${pkg.version}) ..."))
                    onOutput(TerminalLine(text = "Setting up ${pkg.id} (${pkg.version}) ..."))
                    repository.installPackage(pkg.id)
                    onOutput(TerminalLine(text = "\u001B[1;32m✓ Package '${pkg.id}' (${pkg.name}) installed successfully!\u001B[0m"))
                    onOutput(TerminalLine(text = "Run command: '\u001B[1;33m${pkg.command}\u001B[0m'"))
                }
                0
            }
            "uninstall", "remove", "purge", "delete" -> {
                val pkgNames = args.drop(1)
                if (pkgNames.isEmpty()) {
                    onOutput(TerminalLine(text = "tpkg: missing package name. Example: tpkg uninstall htop", isError = true))
                    return 1
                }
                for (pkgName in pkgNames) {
                    val pkg = repository.availablePackages.find { it.id.equals(pkgName, ignoreCase = true) }
                    val id = pkg?.id ?: pkgName.lowercase()
                    val isInstalled = installedSet.contains(id)

                    if (!isInstalled) {
                        onOutput(TerminalLine(text = "Package '$id' is not installed, so not removed."))
                        continue
                    }

                    onOutput(TerminalLine(text = "Reading package lists... Done"))
                    onOutput(TerminalLine(text = "Building dependency tree... Done"))
                    onOutput(TerminalLine(text = "The following packages will be REMOVED:"))
                    onOutput(TerminalLine(text = "  \u001B[1;31m$id\u001B[0m"))
                    onOutput(TerminalLine(text = "0 upgraded, 0 newly installed, 1 to remove and 0 not upgraded."))
                    onOutput(TerminalLine(text = "Removing $id (${pkg?.version ?: "1.0"}) ..."))
                    onOutput(TerminalLine(text = "Purging configuration files for $id ..."))
                    repository.uninstallPackage(id)
                    onOutput(TerminalLine(text = "\u001B[1;33m✓ Package '$id' removed.\u001B[0m"))
                }
                0
            }
            "show", "info", "status" -> {
                val pkgName = args.getOrNull(1)
                if (pkgName == null) {
                    onOutput(TerminalLine(text = "tpkg: missing package name. Example: tpkg show python", isError = true))
                    return 1
                }
                val pkg = repository.availablePackages.find { it.id.equals(pkgName, ignoreCase = true) }
                if (pkg != null) {
                    val isInstalled = installedSet.contains(pkg.id.lowercase())
                    onOutput(TerminalLine(text = "\u001B[1;36mPackage:\u001B[0m ${pkg.id}"))
                    onOutput(TerminalLine(text = "\u001B[1;36mName:\u001B[0m ${pkg.name}"))
                    onOutput(TerminalLine(text = "\u001B[1;36mVersion:\u001B[0m ${pkg.version}"))
                    onOutput(TerminalLine(text = "\u001B[1;36mStatus:\u001B[0m ${if (isInstalled) "\u001B[1;32minstall ok installed\u001B[0m" else "\u001B[1;34mavailable for installation\u001B[0m"}"))
                    onOutput(TerminalLine(text = "\u001B[1;36mSection:\u001B[0m ${pkg.category}"))
                    onOutput(TerminalLine(text = "\u001B[1;36mInstalled-Size:\u001B[0m ${pkg.size}"))
                    onOutput(TerminalLine(text = "\u001B[1;36mArchitecture:\u001B[0m aarch64"))
                    onOutput(TerminalLine(text = "\u001B[1;36mBinary Command:\u001B[0m ${pkg.command}"))
                    onOutput(TerminalLine(text = "\u001B[1;36mMaintainer:\u001B[0m Termux Packaging Team <packages@termux.dev>"))
                    onOutput(TerminalLine(text = "\u001B[1;36mHomepage:\u001B[0m https://packages.termux.dev/apt/termux-main"))
                    onOutput(TerminalLine(text = "\u001B[1;36mDescription:\u001B[0m ${pkg.description}"))
                } else {
                    onOutput(TerminalLine(text = "E: Unable to locate package '$pkgName'", isError = true))
                }
                0
            }
            "update", "upgrade", "refresh" -> {
                onOutput(TerminalLine(text = "Get:1 https://packages.termux.dev/apt/termux-main stable InRelease [14.0 kB]"))
                onOutput(TerminalLine(text = "Get:2 https://packages.termux.dev/apt/termux-main stable/main aarch64 Packages [2,480 kB]"))
                onOutput(TerminalLine(text = "Fetched 2,494 kB in 1s (2,494 kB/s)"))
                onOutput(TerminalLine(text = "Reading package lists... Done"))
                onOutput(TerminalLine(text = "Building dependency tree... Done"))
                onOutput(TerminalLine(text = "Calculating upgrade... Done"))
                onOutput(TerminalLine(text = "\u001B[1;32mAll ${repository.availablePackages.size} packages are up to date.\u001B[0m"))
                0
            }
            "search", "find" -> {
                val query = args.getOrNull(1) ?: ""
                val matches = repository.availablePackages.filter {
                    it.id.contains(query, ignoreCase = true) ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true)
                }
                if (matches.isEmpty()) {
                    onOutput(TerminalLine(text = "No packages matching query '$query'"))
                } else {
                    onOutput(TerminalLine(text = "\u001B[1;32mFound ${matches.size} package(s) matching '$query':\u001B[0m"))
                    matches.forEach { pkg ->
                        val isInstalled = installedSet.contains(pkg.id.lowercase())
                        val status = if (isInstalled) "\u001B[1;32m[installed]\u001B[0m" else "\u001B[1;34m[available]\u001B[0m"
                        onOutput(TerminalLine(text = "  \u001B[1;33m${pkg.id}\u001B[0m/${pkg.category.lowercase()} - ${pkg.description} $status"))
                    }
                }
                0
            }
            else -> {
                onOutput(TerminalLine(text = "tpkg: unknown action '$action'. Type 'tpkg help' for usage.", isError = true))
                1
            }
        }
        return 0
    }

    // Mock utility commands

    private suspend fun handleHtop(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        if (!repository.isPackageInstalled("htop")) {
            onOutput(TerminalLine(text = "The command 'htop' is not installed. You can install it by typing:", isError = true))
            onOutput(TerminalLine(text = "  \u001B[1;33mtpkg install htop\u001B[0m"))
            return 1
        }

        val runtime = Runtime.getRuntime()
        val totalMem = runtime.totalMemory() / (1024 * 1024)
        val freeMem = runtime.freeMemory() / (1024 * 1024)
        val usedMem = totalMem - freeMem
        val cores = runtime.availableProcessors()

        onOutput(TerminalLine(text = "\u001B[1;37;44m  htop 3.2.2 - Termux Process Viewer                                           \u001B[0m"))
        onOutput(TerminalLine(text = "\u001B[1;32m 0\u001B[0m [\u001B[1;32m|||||||||||||||||||||||||||||||||\u001B[90m..................\u001B[0m 64.2%]     \u001B[1;33mTasks:\u001B[0m 32 total, 1 running"))
        if (cores > 1) {
            onOutput(TerminalLine(text = "\u001B[1;32m 1\u001B[0m [\u001B[1;32m||||||||||||||||||||||||||\u001B[90m........................\u001B[0m 52.0%]     \u001B[1;33mLoad average:\u001B[0m 0.28 0.35 0.31"))
        }
        onOutput(TerminalLine(text = "\u001B[1;36mMem\u001B[0m[\u001B[1;36m|||||||||||||||||||||||||\u001B[90m.......................\u001B[0m ${usedMem}M/${totalMem}M]   \u001B[1;33mUptime:\u001B[0m ${(SystemClock.elapsedRealtime() / 3600000)}h ${(SystemClock.elapsedRealtime() / 60000) % 60}m"))
        onOutput(TerminalLine(text = "\u001B[1;35mSwp\u001B[0m[\u001B[90m................................................\u001B[0m 0K/512M]"))
        onOutput(TerminalLine(text = ""))
        onOutput(TerminalLine(text = "\u001B[1;30;47m  PID USER      PRI  NI  VIRT   RES   SHR S CPU% MEM%   TIME+  Command             \u001B[0m"))
        onOutput(TerminalLine(text = " 1001 ${envVars["USER"] ?: "u0_a100"}   20   0  245M   52M   32M S  1.8  5.2  0:01.24 bash"))
        onOutput(TerminalLine(text = " 1042 ${envVars["USER"] ?: "u0_a100"}   20   0  580M  128M   74M S  3.4 12.8  0:03.88 python3"))
        onOutput(TerminalLine(text = " 1089 ${envVars["USER"] ?: "u0_a100"}   20   0  112M   28M   18M R  2.5  2.8  0:00.09 htop"))
        onOutput(TerminalLine(text = " 1120 root        20   0   84M   12M    8M S  0.1  1.2  0:00.32 surfaceflinger"))
        onOutput(TerminalLine(text = " 1155 system      20   0  1.2G  240M  110M S  0.8 24.0  0:15.60 system_server"))
        onOutput(TerminalLine(text = ""))
        onOutput(TerminalLine(text = "\u001B[30;46m F1\u001B[0mHelp \u001B[30;46m F2\u001B[0mSetup \u001B[30;46m F3\u001B[0mSearch \u001B[30;46m F4\u001B[0mFilter \u001B[30;46m F5\u001B[0mTree \u001B[30;46m F6\u001B[0mSortBy \u001B[30;46m F9\u001B[0mKill \u001B[30;46m F10\u001B[0mQuit"))
        return 0
    }

    private suspend fun handleJq(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        if (!repository.isPackageInstalled("jq")) {
            onOutput(TerminalLine(text = "The command 'jq' is not installed. You can install it with:", isError = true))
            onOutput(TerminalLine(text = "  \u001B[1;33mtpkg install jq\u001B[0m"))
            return 1
        }

        if (args.isEmpty()) {
            onOutput(TerminalLine(text = "jq - commandline JSON processor [version 1.6]"))
            onOutput(TerminalLine(text = "Usage: jq [options] <jq filter> [file...]"))
            onOutput(TerminalLine(text = "Example: echo '{\"name\":\"termux\",\"v\":1}' | jq '.'"))
            return 0
        }

        val firstArg = args[0]
        val targetFile = args.getOrNull(1)?.let { resolveFile(it) }

        val jsonText = if (targetFile != null && targetFile.exists()) {
            targetFile.readText()
        } else if (firstArg.trim().startsWith("{") || firstArg.trim().startsWith("[")) {
            firstArg
        } else {
            // Default sample demo json
            """{"app": "Termux", "version": "5.2.0", "status": "active", "features": ["bash", "python", "tpkg", "nano"]}"""
        }

        // Formatted pretty json output with syntax coloring
        val lines = jsonText.replace("{", "{\n  ")
            .replace("}", "\n}")
            .replace(",", ",\n  ")
            .split("\n")

        for (l in lines) {
            val formatted = l
                .replace(Regex("\"([a-zA-Z0-9_-]+)\":"), "\u001B[1;34m\"$1\"\u001B[0m:")
                .replace(Regex(": \"([^\"]+)\""), ": \u001B[32m\"$1\"\u001B[0m")
                .replace(Regex(": ([0-9.]+)"), ": \u001B[33m$1\u001B[0m")
                .replace(Regex(": (true|false)"), ": \u001B[35m$1\u001B[0m")
            onOutput(TerminalLine(text = formatted))
        }
        return 0
    }

    private suspend fun handleSpeedtest(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        if (!repository.isPackageInstalled("speedtest")) {
            onOutput(TerminalLine(text = "The command 'speedtest' is not installed. You can install it with:", isError = true))
            onOutput(TerminalLine(text = "  \u001B[1;33mtpkg install speedtest\u001B[0m"))
            return 1
        }

        onOutput(TerminalLine(text = "\u001B[1;36m   Speedtest CLI v2.1.3 (Simulated Network Benchmarking)\u001B[0m"))
        onOutput(TerminalLine(text = "========================================================"))
        onOutput(TerminalLine(text = "Testing from Google Cloud / Termux Network (104.28.19.42)..."))
        onOutput(TerminalLine(text = "Selecting best server based on ping..."))
        onOutput(TerminalLine(text = "Hosted by Cloudflare (Ashburn, VA) [14.2 km]: \u001B[1;32m12.4 ms\u001B[0m"))
        onOutput(TerminalLine(text = "Testing download speed ........................................ \u001B[1;32m284.50 Mbit/s\u001B[0m"))
        onOutput(TerminalLine(text = "Testing upload speed .......................................... \u001B[1;33m96.20 Mbit/s\u001B[0m"))
        onOutput(TerminalLine(text = ""))
        onOutput(TerminalLine(text = "\u001B[1;32mDownload: 284.50 Mbit/s   Upload: 96.20 Mbit/s   Latency: 12.4 ms\u001B[0m"))
        return 0
    }

    private suspend fun handleNmap(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        if (!repository.isPackageInstalled("nmap")) {
            onOutput(TerminalLine(text = "The command 'nmap' is not installed. You can install it with:", isError = true))
            onOutput(TerminalLine(text = "  \u001B[1;33mtpkg install nmap\u001B[0m"))
            return 1
        }

        val target = args.firstOrNull() ?: "127.0.0.1"
        onOutput(TerminalLine(text = "Starting Nmap 7.94 ( https://nmap.org ) at ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}"))
        onOutput(TerminalLine(text = "Nmap scan report for $target"))
        onOutput(TerminalLine(text = "Host is up (0.00038s latency)."))
        onOutput(TerminalLine(text = "Not shown: 996 closed tcp ports (reset)"))
        onOutput(TerminalLine(text = "\u001B[1;37mPORT     STATE SERVICE     VERSION\u001B[0m"))
        onOutput(TerminalLine(text = "\u001B[1;32m22/tcp   open  ssh         OpenSSH 9.3p1 (Termux sshd)\u001B[0m"))
        onOutput(TerminalLine(text = "\u001B[1;32m80/tcp   open  http        nginx/1.24.0\u001B[0m"))
        onOutput(TerminalLine(text = "\u001B[1;32m443/tcp  open  ssl/http    nginx/1.24.0\u001B[0m"))
        onOutput(TerminalLine(text = "\u001B[1;32m8080/tcp open  http-proxy  NodeJS / Python HTTP Server\u001B[0m"))
        onOutput(TerminalLine(text = ""))
        onOutput(TerminalLine(text = "Nmap done: 1 IP address (1 host up) scanned in 0.42 seconds"))
        return 0
    }

    private suspend fun handleTodo(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        if (!repository.isPackageInstalled("todo")) {
            onOutput(TerminalLine(text = "The command 'todo' is not installed. You can install it with:", isError = true))
            onOutput(TerminalLine(text = "  \u001B[1;33mtpkg install todo\u001B[0m"))
            return 1
        }

        val todoFile = File(homeDir, ".todo.txt")
        if (!todoFile.exists()) {
            todoFile.writeText("1|[ ]|Install Termux packages and explore tpkg\n2|[ ]|Write a Python script in nano editor\n")
        }

        if (args.isEmpty() || args[0] == "list") {
            onOutput(TerminalLine(text = "\u001B[1;36m=== TODO LIST (~/.todo.txt) ===\u001B[0m"))
            val lines = todoFile.readLines().filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                onOutput(TerminalLine(text = "No pending tasks. Add one with: todo add <task>"))
            } else {
                lines.forEach { line ->
                    val parts = line.split("|", limit = 3)
                    if (parts.size == 3) {
                        val id = parts[0]
                        val status = parts[1]
                        val task = parts[2]
                        val colorStatus = if (status.contains("x")) "\u001B[1;32m$status\u001B[0m" else "\u001B[1;33m$status\u001B[0m"
                        onOutput(TerminalLine(text = "  \u001B[90m$id.\u001B[0m $colorStatus $task"))
                    } else {
                        onOutput(TerminalLine(text = "  $line"))
                    }
                }
            }
            return 0
        }

        when (args[0]) {
            "add" -> {
                val task = args.drop(1).joinToString(" ")
                if (task.isBlank()) {
                    onOutput(TerminalLine(text = "todo: missing task description. Example: todo add 'Buy coffee'", isError = true))
                    return 1
                }
                val lines = todoFile.readLines().filter { it.isNotBlank() }
                val newId = (lines.size + 1).toString()
                todoFile.appendText("$newId|[ ]|$task\n")
                onOutput(TerminalLine(text = "\u001B[1;32m✓ Task #$newId added:\u001B[0m $task"))
                0
            }
            "done" -> {
                val id = args.getOrNull(1)
                if (id == null) {
                    onOutput(TerminalLine(text = "todo: missing task id. Example: todo done 1", isError = true))
                    return 1
                }
                val lines = todoFile.readLines().map { line ->
                    val parts = line.split("|", limit = 3)
                    if (parts.size == 3 && parts[0] == id) {
                        "${parts[0]}|[x]|${parts[2]}"
                    } else {
                        line
                    }
                }
                todoFile.writeText(lines.joinToString("\n") + "\n")
                onOutput(TerminalLine(text = "\u001B[1;32m✓ Task #$id marked as completed!\u001B[0m"))
                0
            }
            "rm", "remove" -> {
                val id = args.getOrNull(1)
                if (id == null) {
                    onOutput(TerminalLine(text = "todo: missing task id. Example: todo rm 1", isError = true))
                    return 1
                }
                val lines = todoFile.readLines().filter { !it.startsWith("$id|") }
                todoFile.writeText(lines.joinToString("\n") + if (lines.isNotEmpty()) "\n" else "")
                onOutput(TerminalLine(text = "\u001B[1;33mTask #$id removed.\u001B[0m"))
                0
            }
            "clear" -> {
                todoFile.writeText("")
                onOutput(TerminalLine(text = "\u001B[1;33mAll tasks cleared.\u001B[0m"))
                0
            }
            else -> {
                onOutput(TerminalLine(text = "Unknown todo command: ${args[0]}. Usage: todo <add|list|done|rm|clear>", isError = true))
                1
            }
        }
        return 0
    }

    private suspend fun handleWhois(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        if (!repository.isPackageInstalled("whois")) {
            onOutput(TerminalLine(text = "The command 'whois' is not installed. You can install it with:", isError = true))
            onOutput(TerminalLine(text = "  \u001B[1;33mtpkg install whois\u001B[0m"))
            return 1
        }

        val domain = args.firstOrNull() ?: "termux.dev"
        onOutput(TerminalLine(text = "   Domain Name: \u001B[1;33m${domain.uppercase()}\u001B[0m"))
        onOutput(TerminalLine(text = "   Registry Domain ID: 29481902_DOMAIN_DEV-VRSN"))
        onOutput(TerminalLine(text = "   Registrar WHOIS Server: whois.nic.dev"))
        onOutput(TerminalLine(text = "   Registrar URL: https://nic.dev"))
        onOutput(TerminalLine(text = "   Updated Date: 2023-08-14T08:12:00Z"))
        onOutput(TerminalLine(text = "   Creation Date: 2018-05-02T12:00:00Z"))
        onOutput(TerminalLine(text = "   Registry Expiry Date: 2028-05-02T12:00:00Z"))
        onOutput(TerminalLine(text = "   Registrar: Google Domains / MarkMonitor"))
        onOutput(TerminalLine(text = "   Name Server: NS1.CLOUDFLARE.COM"))
        onOutput(TerminalLine(text = "   Name Server: NS2.CLOUDFLARE.COM"))
        onOutput(TerminalLine(text = "   DNSSEC: signedDelegation"))
        return 0
    }

    private suspend fun handleBc(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        return handleCalc(args, onOutput)
    }

    private suspend fun handleCd(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val target = if (args.isEmpty() || args[0] == "~") {
            homeDir
        } else {
            resolveFile(args[0])
        }

        if (!target.exists()) {
            onOutput(TerminalLine(text = "cd: ${args.firstOrNull() ?: ""}: No such file or directory", isError = true))
            return 1
        }
        if (!target.isDirectory) {
            onOutput(TerminalLine(text = "cd: ${args.firstOrNull() ?: ""}: Not a directory", isError = true))
            return 1
        }
        currentDirectory = target.canonicalFile
        return 0
    }

    private suspend fun handleLs(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val showAll = args.contains("-a") || args.contains("-la") || args.contains("-al")
        val longFormat = args.contains("-l") || args.contains("-la") || args.contains("-al")
        val targetPath = args.filterNot { it.startsWith("-") }.firstOrNull()
        val dir = if (targetPath != null) resolveFile(targetPath) else currentDirectory

        if (!dir.exists()) {
            onOutput(TerminalLine(text = "ls: cannot access '${targetPath ?: ""}': No such file or directory", isError = true))
            return 1
        }

        if (dir.isFile) {
            onOutput(TerminalLine(text = dir.name))
            return 0
        }

        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
        val filtered = if (showAll) {
            listOf(File(dir, "."), File(dir, "..")) + files
        } else {
            files.filter { !it.name.startsWith(".") }
        }

        if (longFormat) {
            onOutput(TerminalLine(text = "total ${filtered.size}"))
            val sdf = SimpleDateFormat("MMM dd HH:mm", Locale.US)
            filtered.forEach { f ->
                val perm = if (f.isDirectory) "drwxr-xr-x" else if (f.canExecute()) "-rwxr-xr-x" else "-rw-r--r--"
                val size = if (f.isDirectory) "4096" else f.length().toString()
                val dateStr = sdf.format(Date(f.lastModified()))
                val colorName = when {
                    f.isDirectory -> "\u001B[1;34m${f.name}/\u001B[0m"
                    f.canExecute() -> "\u001B[1;32m${f.name}*\u001B[0m"
                    f.name.endsWith(".sh") || f.name.endsWith(".py") -> "\u001B[1;32m${f.name}\u001B[0m"
                    f.name.endsWith(".zip") || f.name.endsWith(".tar") || f.name.endsWith(".gz") -> "\u001B[1;31m${f.name}\u001B[0m"
                    f.name.endsWith(".md") || f.name.endsWith(".txt") -> "\u001B[0;37m${f.name}\u001B[0m"
                    else -> f.name
                }
                onOutput(TerminalLine(text = String.format("%-10s  1 %s %s %6s %s %s", perm, envVars["USER"] ?: "u0_a100", envVars["USER"] ?: "u0_a100", size, dateStr, colorName)))
            }
        } else {
            // Horizontal compact grid
            val formattedNames = filtered.map { f ->
                when {
                    f.isDirectory -> "\u001B[1;34m${f.name}/\u001B[0m"
                    f.canExecute() -> "\u001B[1;32m${f.name}*\u001B[0m"
                    f.name.endsWith(".sh") || f.name.endsWith(".py") -> "\u001B[1;32m${f.name}\u001B[0m"
                    f.name.endsWith(".zip") || f.name.endsWith(".tar") -> "\u001B[1;31m${f.name}\u001B[0m"
                    else -> f.name
                }
            }
            onOutput(TerminalLine(text = formattedNames.joinToString("  ")))
        }
        return 0
    }

    private suspend fun handleCat(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        if (args.isEmpty()) {
            onOutput(TerminalLine(text = "cat: missing file operand", isError = true))
            return 1
        }
        var hasError = false
        for (filePath in args) {
            val file = resolveFile(filePath)
            if (!file.exists()) {
                onOutput(TerminalLine(text = "cat: $filePath: No such file or directory", isError = true))
                hasError = true
                continue
            }
            if (file.isDirectory) {
                onOutput(TerminalLine(text = "cat: $filePath: Is a directory", isError = true))
                hasError = true
                continue
            }
            try {
                file.readLines().forEach { line ->
                    onOutput(TerminalLine(text = line))
                }
            } catch (e: Exception) {
                onOutput(TerminalLine(text = "cat: $filePath: ${e.localizedMessage}", isError = true))
                hasError = true
            }
        }
        return if (hasError) 1 else 0
    }

    private suspend fun handleEcho(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val interpretEscapes = args.firstOrNull() == "-e"
        val words = if (interpretEscapes) args.drop(1) else args
        var result = words.joinToString(" ")
        if (interpretEscapes) {
            result = result
                .replace("\\e", "\u001B")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
        }
        result.lines().forEach { onOutput(TerminalLine(text = it)) }
        return 0
    }

    private suspend fun handleMkdir(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        if (args.isEmpty()) {
            onOutput(TerminalLine(text = "mkdir: missing operand", isError = true))
            return 1
        }
        val pOption = args.contains("-p")
        val paths = args.filter { it != "-p" }
        for (p in paths) {
            val dir = resolveFile(p)
            val success = if (pOption) dir.mkdirs() else dir.mkdir()
            if (!success && !dir.exists()) {
                onOutput(TerminalLine(text = "mkdir: cannot create directory '$p'", isError = true))
            }
        }
        return 0
    }

    private suspend fun handleRm(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        if (args.isEmpty()) {
            onOutput(TerminalLine(text = "rm: missing operand", isError = true))
            return 1
        }
        val recursive = args.contains("-r") || args.contains("-rf") || args.contains("-fr")
        val paths = args.filterNot { it.startsWith("-") }
        for (p in paths) {
            val file = resolveFile(p)
            if (!file.exists()) {
                if (!args.contains("-f") && !args.contains("-rf")) {
                    onOutput(TerminalLine(text = "rm: cannot remove '$p': No such file or directory", isError = true))
                }
                continue
            }
            if (file.isDirectory && !recursive) {
                onOutput(TerminalLine(text = "rm: cannot remove '$p': Is a directory (use -r)", isError = true))
                continue
            }
            if (recursive) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        }
        return 0
    }

    private suspend fun handleTouch(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        if (args.isEmpty()) {
            onOutput(TerminalLine(text = "touch: missing file operand", isError = true))
            return 1
        }
        for (p in args) {
            val file = resolveFile(p)
            try {
                if (!file.exists()) {
                    file.createNewFile()
                } else {
                    file.setLastModified(System.currentTimeMillis())
                }
            } catch (e: Exception) {
                onOutput(TerminalLine(text = "touch: cannot touch '$p': ${e.localizedMessage}", isError = true))
            }
        }
        return 0
    }

    private suspend fun handleCp(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val cleanArgs = args.filterNot { it.startsWith("-") }
        if (cleanArgs.size < 2) {
            onOutput(TerminalLine(text = "cp: missing destination file operand after '${cleanArgs.firstOrNull() ?: ""}'", isError = true))
            return 1
        }
        val src = resolveFile(cleanArgs[0])
        val dest = resolveFile(cleanArgs[1])
        if (!src.exists()) {
            onOutput(TerminalLine(text = "cp: cannot stat '${cleanArgs[0]}': No such file or directory", isError = true))
            return 1
        }
        val target = if (dest.isDirectory) File(dest, src.name) else dest
        if (src.isDirectory) {
            src.copyRecursively(target, overwrite = true)
        } else {
            src.copyTo(target, overwrite = true)
        }
        return 0
    }

    private suspend fun handleMv(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val cleanArgs = args.filterNot { it.startsWith("-") }
        if (cleanArgs.size < 2) {
            onOutput(TerminalLine(text = "mv: missing destination file operand after '${cleanArgs.firstOrNull() ?: ""}'", isError = true))
            return 1
        }
        val src = resolveFile(cleanArgs[0])
        val dest = resolveFile(cleanArgs[1])
        if (!src.exists()) {
            onOutput(TerminalLine(text = "mv: cannot stat '${cleanArgs[0]}': No such file or directory", isError = true))
            return 1
        }
        val target = if (dest.isDirectory) File(dest, src.name) else dest
        src.renameTo(target)
        return 0
    }

    private suspend fun handleTree(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val targetDir = if (args.isNotEmpty() && !args[0].startsWith("-")) resolveFile(args[0]) else currentDirectory
        if (!targetDir.exists() || !targetDir.isDirectory) {
            onOutput(TerminalLine(text = "tree: [error opening dir]", isError = true))
            return 1
        }
        onOutput(TerminalLine(text = "\u001B[1;34m${targetDir.name}\u001B[0m"))
        var dirCount = 0
        var fileCount = 0

        suspend fun printSubtree(dir: File, prefix: String, depth: Int) {
            if (depth > 4) return
            val children = dir.listFiles()?.filter { !it.name.startsWith(".") }?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return
            for (i in children.indices) {
                val isLast = (i == children.size - 1)
                val connector = if (isLast) "└── " else "├── "
                val child = children[i]
                val formatted = if (child.isDirectory) "\u001B[1;34m${child.name}\u001B[0m" else child.name
                onOutput(TerminalLine(text = "$prefix$connector$formatted"))
                if (child.isDirectory) {
                    dirCount++
                    printSubtree(child, prefix + (if (isLast) "    " else "│   "), depth + 1)
                } else {
                    fileCount++
                }
            }
        }

        printSubtree(targetDir, "", 0)
        onOutput(TerminalLine(text = ""))
        onOutput(TerminalLine(text = "$dirCount directories, $fileCount files"))
        return 0
    }

    private suspend fun handleGrep(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val ignoreCase = args.contains("-i")
        val cleanArgs = args.filterNot { it.startsWith("-") }
        if (cleanArgs.isEmpty()) {
            onOutput(TerminalLine(text = "grep: missing pattern", isError = true))
            return 1
        }
        val pattern = cleanArgs[0]
        val files = cleanArgs.drop(1).map { resolveFile(it) }
        if (files.isEmpty()) {
            onOutput(TerminalLine(text = "grep: missing file operand", isError = true))
            return 1
        }
        for (f in files) {
            if (!f.exists()) {
                onOutput(TerminalLine(text = "grep: ${f.name}: No such file", isError = true))
                continue
            }
            f.readLines().forEachIndexed { index, line ->
                if (line.contains(pattern, ignoreCase = ignoreCase)) {
                    val prefix = if (files.size > 1) "\u001B[1;35m${f.name}\u001B[0m:\u001B[1;32m${index + 1}\u001B[0m:" else ""
                    val highlighted = line.replace(pattern, "\u001B[1;31m$pattern\u001B[0m", ignoreCase = ignoreCase)
                    onOutput(TerminalLine(text = "$prefix$highlighted"))
                }
            }
        }
        return 0
    }

    private suspend fun handleFind(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val targetDir = if (args.isNotEmpty() && !args[0].startsWith("-")) resolveFile(args[0]) else currentDirectory
        val nameIdx = args.indexOf("-name")
        val namePattern = if (nameIdx != -1 && nameIdx + 1 < args.size) args[nameIdx + 1].trim('\"', '\'') else null

        targetDir.walkTopDown().forEach { f ->
            if (namePattern == null || f.name.contains(namePattern.replace("*", ""))) {
                onOutput(TerminalLine(text = getRelativePath(f)))
            }
        }
        return 0
    }

    private suspend fun handleHead(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val nIdx = args.indexOf("-n")
        val count = if (nIdx != -1 && nIdx + 1 < args.size) args[nIdx + 1].toIntOrNull() ?: 10 else 10
        val files = args.filterNot { it.startsWith("-") || it.toIntOrNull() != null }
        if (files.isEmpty()) return 0
        val f = resolveFile(files[0])
        if (f.exists()) {
            f.readLines().take(count).forEach { onOutput(TerminalLine(text = it)) }
        }
        return 0
    }

    private suspend fun handleTail(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val nIdx = args.indexOf("-n")
        val count = if (nIdx != -1 && nIdx + 1 < args.size) args[nIdx + 1].toIntOrNull() ?: 10 else 10
        val files = args.filterNot { it.startsWith("-") || it.toIntOrNull() != null }
        if (files.isEmpty()) return 0
        val f = resolveFile(files[0])
        if (f.exists()) {
            val lines = f.readLines()
            lines.takeLast(count.coerceAtMost(lines.size)).forEach { onOutput(TerminalLine(text = it)) }
        }
        return 0
    }

    private suspend fun handleWc(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val files = args.filterNot { it.startsWith("-") }
        if (files.isEmpty()) return 0
        var totL = 0; var totW = 0; var totB = 0L
        for (fn in files) {
            val f = resolveFile(fn)
            if (!f.exists()) continue
            val lines = f.readLines()
            val lCount = lines.size
            val wCount = lines.sumOf { it.split(Regex("\\s+")).filter { w -> w.isNotBlank() }.size }
            val bCount = f.length()
            totL += lCount; totW += wCount; totB += bCount
            onOutput(TerminalLine(text = String.format("%6d %6d %6d %s", lCount, wCount, bCount, fn)))
        }
        if (files.size > 1) {
            onOutput(TerminalLine(text = String.format("%6d %6d %6d total", totL, totW, totB)))
        }
        return 0
    }

    private suspend fun handleCurl(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        if (args.isEmpty()) {
            onOutput(TerminalLine(text = "curl: try 'curl --help' or 'curl <url>'", isError = true))
            return 1
        }
        val isHead = args.contains("-I") || args.contains("--head")
        val outIdx = args.indexOf("-o").takeIf { it != -1 } ?: args.indexOf("-O").takeIf { it != -1 }
        val outFile = if (outIdx != null && outIdx + 1 < args.size) resolveFile(args[outIdx + 1]) else null
        var rawUrl = args.filterNot { it.startsWith("-") }.lastOrNull() ?: ""

        if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            rawUrl = "https://$rawUrl"
        }

        onOutput(TerminalLine(text = "\u001B[90mConnecting to $rawUrl ...\u001B[0m"))

        try {
            val request = Request.Builder().url(rawUrl).build()
            val response = httpClient.newCall(request).execute()

            if (isHead) {
                onOutput(TerminalLine(text = "HTTP/1.1 ${response.code} ${response.message}"))
                response.headers.forEach { pair ->
                    onOutput(TerminalLine(text = "\u001B[1;36m${pair.first}\u001B[0m: ${pair.second}"))
                }
            } else {
                val bodyStr = response.body?.string() ?: ""
                if (outFile != null) {
                    outFile.writeText(bodyStr)
                    onOutput(TerminalLine(text = "\u001B[1;32m100% Downloaded ${bodyStr.length} bytes -> ${outFile.name}\u001B[0m"))
                } else {
                    bodyStr.lines().take(60).forEach { onOutput(TerminalLine(text = it)) }
                    if (bodyStr.lines().size > 60) {
                        onOutput(TerminalLine(text = "\u001B[90m... [${bodyStr.lines().size - 60} more lines truncated. Use -o file.txt to save]\u001B[0m"))
                    }
                }
            }
            return 0
        } catch (e: Exception) {
            onOutput(TerminalLine(text = "curl: (6) Could not resolve host: ${e.localizedMessage}", isError = true))
            return 6
        }
    }

    private suspend fun handleWeather(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val city = args.firstOrNull() ?: ""
        val url = "https://wttr.in/$city?0TQ"
        try {
            val request = Request.Builder().url(url).header("User-Agent", "curl/8.4.0").build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (body.isNotBlank()) {
                body.lines().take(20).forEach { onOutput(TerminalLine(text = it)) }
                return 0
            }
        } catch (_: Exception) {}

        // Fallback ASCII weather
        onOutput(TerminalLine(text = "\u001B[1;36mWeather report for: ${city.ifEmpty { "Local Area" }}\u001B[0m"))
        onOutput(TerminalLine(text = "\u001B[1;33m   \\   /     \u001B[0m Clear Sky"))
        onOutput(TerminalLine(text = "\u001B[1;33m    .-.      \u001B[0m +24 °C"))
        onOutput(TerminalLine(text = "\u001B[1;33m --(   )--   \u001B[0m Wind: 8 km/h ↗"))
        onOutput(TerminalLine(text = "\u001B[1;33m    `-’      \u001B[0m Humidity: 45%"))
        onOutput(TerminalLine(text = "\u001B[1;33m   /   \\     \u001B[0m Visibility: 10 km"))
        return 0
    }

    private suspend fun handleCowsay(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val msg = if (args.isEmpty()) "Moo! Termux on Android is awesome." else args.joinToString(" ")
        val border = "-".repeat(msg.length + 2)
        onOutput(TerminalLine(text = " $border "))
        onOutput(TerminalLine(text = "< $msg >"))
        onOutput(TerminalLine(text = " $border "))
        onOutput(TerminalLine(text = "        \\   ^__^"))
        onOutput(TerminalLine(text = "         \\  (oo)\\_______"))
        onOutput(TerminalLine(text = "            (__)\\       )\\/\\"))
        onOutput(TerminalLine(text = "                ||----w |"))
        onOutput(TerminalLine(text = "                ||     ||"))
        return 0
    }

    private suspend fun handleFiglet(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val word = if (args.isEmpty()) "TERMUX" else args.joinToString(" ").uppercase()
        onOutput(TerminalLine(text = "\u001B[1;32m _                              \u001B[0m"))
        onOutput(TerminalLine(text = "\u001B[1;32m| |_ ___ _ __ _ __ ___  _   ___  __\u001B[0m"))
        onOutput(TerminalLine(text = "\u001B[1;32m| __/ _ \\ '__| '_ ` _ \\| | | \\ \\/ /\u001B[0m"))
        onOutput(TerminalLine(text = "\u001B[1;32m| ||  __/ |  | | | | | | |_| |>  < \u001B[0m"))
        onOutput(TerminalLine(text = "\u001B[1;32m \\__\\___|_|  |_| |_| |_|\\__,_/_/\\_\\\u001B[0m"))
        onOutput(TerminalLine(text = " [ $word ] "))
        return 0
    }

    private suspend fun handleCalc(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        if (args.isEmpty()) {
            onOutput(TerminalLine(text = "calc: missing math expression. Example: calc '2^8 + sqrt(144)'", isError = true))
            return 1
        }
        val expr = args.joinToString(" ")
        try {
            val result = evaluateMathExpression(expr)
            onOutput(TerminalLine(text = "\u001B[1;33m$result\u001B[0m"))
            return 0
        } catch (e: Exception) {
            onOutput(TerminalLine(text = "calc: evaluation error: ${e.localizedMessage}", isError = true))
            return 1
        }
    }

    private suspend fun handleBase64(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val isDecode = args.contains("-d") || args.contains("--decode")
        val text = args.filterNot { it.startsWith("-") }.joinToString(" ")
        if (text.isEmpty()) {
            onOutput(TerminalLine(text = "base64: missing text operand", isError = true))
            return 1
        }
        try {
            if (isDecode) {
                val decoded = String(android.util.Base64.decode(text, android.util.Base64.DEFAULT))
                onOutput(TerminalLine(text = decoded))
            } else {
                val encoded = android.util.Base64.encodeToString(text.toByteArray(), android.util.Base64.NO_WRAP)
                onOutput(TerminalLine(text = encoded))
            }
            return 0
        } catch (e: Exception) {
            onOutput(TerminalLine(text = "base64: error processing input", isError = true))
            return 1
        }
    }

    private suspend fun handleSha256(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val files = args.filterNot { it.startsWith("-") }
        if (files.isEmpty()) return 0
        for (fn in files) {
            val f = resolveFile(fn)
            if (!f.exists()) continue
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(f.readBytes())
            val hex = bytes.joinToString("") { "%02x".format(it) }
            onOutput(TerminalLine(text = "$hex  $fn"))
        }
        return 0
    }

    private suspend fun handleHexdump(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val files = args.filterNot { it.startsWith("-") }
        if (files.isEmpty()) return 0
        val f = resolveFile(files[0])
        if (!f.exists()) return 1
        val bytes = f.readBytes().take(256)
        var offset = 0
        for (chunk in bytes.chunked(16)) {
            val hexPart = chunk.joinToString(" ") { "%02x".format(it) }
            val asciiPart = chunk.map { if (it in 32..126) it.toInt().toChar() else '.' }.joinToString("")
            onOutput(TerminalLine(text = String.format("%08x  %-48s  |%s|", offset, hexPart, asciiPart)))
            offset += 16
        }
        return 0
    }

    private suspend fun showFortune(onOutput: suspend (TerminalLine) -> Unit) {
        val fortunes = listOf(
            "\"There is no place like ~\"",
            "\"Talk is cheap. Show me the code.\" - Linus Torvalds",
            "\"Unix is simple. It just takes a genius to understand its simplicity.\" - Dennis Ritchie",
            "\"Premature optimization is the root of all evil.\" - Donald Knuth",
            "\"Any fool can write code that a computer can understand. Good programmers write code that humans can understand.\" - Martin Fowler",
            "\"Deleted code is debugged code.\" - Jeff Sickel"
        )
        onOutput(TerminalLine(text = "\u001B[1;35m${fortunes.random()}\u001B[0m"))
    }

    private suspend fun handlePythonScript(args: List<String>, onOutput: suspend (TerminalLine) -> Unit): Int {
        val scriptFile = resolveFile(args[0])
        if (!scriptFile.exists()) {
            onOutput(TerminalLine(text = "python: can't open file '${args[0]}': [Errno 2] No such file or directory", isError = true))
            return 2
        }
        val lines = scriptFile.readLines()
        val engine = PythonMiniInterpreter()
        for (line in lines) {
            val res = engine.execute(line)
            if (res.isNotBlank()) {
                onOutput(TerminalLine(text = res))
            }
        }
        return 0
    }

    fun getAutoCompleteSuggestions(input: String): List<String> {
        val trimmed = input.trimEnd()
        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.size <= 1) {
            // Suggest commands
            val prefix = tokens.firstOrNull() ?: ""
            val allCommands = listOf(
                "tpkg", "pkg", "apt", "htop", "speedtest", "nmap", "todo", "jq", "whois", "bc",
                "neofetch", "nano", "python", "matrix", "snake", "2048", "curl", "weather",
                "tree", "calc", "cowsay", "figlet", "ls", "cd", "pwd", "cat", "echo", "mkdir",
                "rm", "touch", "cp", "mv", "grep", "find", "head", "tail", "wc", "clear", "exit",
                "uptime", "uname", "whoami", "history", "date", "env", "export", "alias", "ps", "top", "df"
            )
            return allCommands.filter { it.startsWith(prefix, ignoreCase = true) }
        } else if (tokens[0] in listOf("tpkg", "pkg", "apt") && tokens.size == 2) {
            val subActions = listOf("list", "list-installed", "install", "uninstall", "show", "search", "update", "upgrade", "reinstall", "help")
            val subPrefix = tokens[1]
            val subMatches = subActions.filter { it.startsWith(subPrefix, ignoreCase = true) }
            if (subMatches.isNotEmpty()) return subMatches

            // Also suggest package names if user typed e.g. `tpkg h`
            val pkgMatches = repository.availablePackages.map { it.id }.filter { it.startsWith(subPrefix, ignoreCase = true) }
            return pkgMatches
        } else if (tokens[0] in listOf("tpkg", "pkg", "apt") && tokens.size >= 3 && tokens[1] in listOf("install", "uninstall", "show", "info", "remove", "reinstall")) {
            val lastToken = tokens.last()
            return repository.availablePackages.map { it.id }.filter { it.startsWith(lastToken, ignoreCase = true) }
        } else {
            // Suggest files / directories
            val lastToken = tokens.last()
            val baseDir = if (lastToken.contains("/")) {
                val dirPart = lastToken.substringBeforeLast("/")
                resolveFile(dirPart)
            } else {
                currentDirectory
            }
            val filePrefix = lastToken.substringAfterLast("/")
            val files = baseDir.listFiles() ?: return emptyList()
            return files.filter { it.name.startsWith(filePrefix, ignoreCase = true) }
                .map { if (it.isDirectory) "${it.name}/" else it.name }
        }
    }

    fun resolveFile(path: String): File {
        val expanded = expandVariables(path)
        return when {
            expanded == "~" -> homeDir
            expanded.startsWith("~/") -> File(homeDir, expanded.removePrefix("~/"))
            expanded.startsWith("/") -> File(expanded)
            else -> File(currentDirectory, expanded)
        }
    }

    private fun expandVariables(cmd: String): String {
        var res = cmd
        envVars.forEach { (k, v) ->
            res = res.replace("$$k", v).replace("\${$k}", v)
        }
        return res
    }

    private fun tokenizeCommand(cmd: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var quoteChar = ' '

        for (c in cmd) {
            when {
                (c == '\"' || c == '\'') && !inQuotes -> {
                    inQuotes = true
                    quoteChar = c
                }
                c == quoteChar && inQuotes -> {
                    inQuotes = false
                }
                c.isWhitespace() && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> {
                    current.append(c)
                }
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }

    private fun evaluateMathExpression(expr: String): Double {
        val sanitized = expr.replace("sqrt", "√")
            .replace("pi", Math.PI.toString())
            .replace("e", Math.E.toString())
            .replace(" ", "")

        return parseExpression(sanitized)
    }

    private fun parseExpression(str: String): Double {
        var pos = -1
        var ch = 0

        fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression(str)
                eat(')'.code)
            } else if (eat('√'.code)) {
                x = sqrt(parseFactor())
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else {
                x = 0.0
            }

            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }

        fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> x /= parseFactor()
                    eat('%'.code) -> x %= parseFactor()
                    else -> return x
                }
            }
        }

        nextChar()
        var x = parseTerm()
        while (true) {
            when {
                eat('+'.code) -> x += parseTerm()
                eat('-'.code) -> x -= parseTerm()
                else -> return x
            }
        }
    }
}

/**
 * Lightweight in-terminal Python interpreter for interactive REPL and quick scripts
 */
class PythonMiniInterpreter {
    val vars = mutableMapOf<String, Any>()

    init {
        vars["pi"] = Math.PI
        vars["e"] = Math.E
    }

    fun execute(line: String): String {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return ""

        if (trimmed == "help()" || trimmed == "help") {
            return "Type python expressions like: 2 + 2, x = 10, print(x * 5), sqrt(100), len([1,2,3])"
        }

        // Print statement
        if (trimmed.startsWith("print(") && trimmed.endsWith(")")) {
            val content = trimmed.substring(6, trimmed.length - 1).trim()
            if (content.startsWith("\"") && content.endsWith("\"")) {
                return content.trim('\"')
            }
            if (content.startsWith("f\"") && content.endsWith("\"")) {
                var formatStr = content.substring(2, content.length - 1)
                vars.forEach { (k, v) ->
                    formatStr = formatStr.replace("{$k}", v.toString())
                }
                return formatStr
            }
            val evaluated = evalExpression(content)
            return evaluated.toString()
        }

        // Variable assignment: x = 10
        if (trimmed.contains("=") && !trimmed.contains("==")) {
            val parts = trimmed.split("=", limit = 2)
            val varName = parts[0].trim()
            val expr = parts[1].trim()
            val value = evalExpression(expr)
            vars[varName] = value
            return ""
        }

        // Direct expression eval
        val res = evalExpression(trimmed)
        return res.toString()
    }

    private fun evalExpression(expr: String): Any {
        val sanitized = expr.trim()
        // String literal
        if (sanitized.startsWith("\"") && sanitized.endsWith("\"")) {
            return sanitized.trim('\"')
        }
        if (sanitized.startsWith("'") && sanitized.endsWith("'")) {
            return sanitized.trim('\'')
        }
        if (sanitized == "True") return true
        if (sanitized == "False") return false

        // Check if expression is variable
        if (vars.containsKey(sanitized)) {
            return vars[sanitized]!!
        }

        // Math evaluator
        try {
            var subExpr = sanitized
            vars.forEach { (k, v) ->
                if (v is Number) {
                    subExpr = subExpr.replace(Regex("\\b$k\\b"), v.toString())
                }
            }
            // Basic arithmetic
            return evalSimpleMath(subExpr)
        } catch (_: Exception) {
            return sanitized
        }
    }

    private fun evalSimpleMath(expr: String): Double {
        val s = expr.replace(" ", "")
        if (s.toDoubleOrNull() != null) return s.toDouble()
        if (s.contains("+")) {
            val p = s.split("+")
            return p.sumOf { evalSimpleMath(it) }
        }
        if (s.contains("*")) {
            val p = s.split("*")
            return p.fold(1.0) { acc, num -> acc * evalSimpleMath(num) }
        }
        if (s.contains("-") && !s.startsWith("-")) {
            val p = s.split("-")
            return evalSimpleMath(p[0]) - evalSimpleMath(p[1])
        }
        if (s.contains("/")) {
            val p = s.split("/")
            return evalSimpleMath(p[0]) / evalSimpleMath(p[1])
        }
        return s.toDoubleOrNull() ?: 0.0
    }
}
