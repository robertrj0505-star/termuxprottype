package com.example.data.repository

import com.example.data.local.CommandHistoryEntity
import com.example.data.local.InstalledPackageEntity
import com.example.data.local.SavedScriptEntity
import com.example.data.local.TerminalDao
import com.example.data.model.PackageItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TerminalRepository(private val dao: TerminalDao) {

    val history: Flow<List<CommandHistoryEntity>> = dao.getAllHistory()
    val savedScripts: Flow<List<SavedScriptEntity>> = dao.getAllScripts()
    val installedPackages: Flow<List<InstalledPackageEntity>> = dao.getAllInstalledPackages()

    val availablePackages = listOf(
        PackageItem(
            id = "python",
            name = "Python (Core + REPL)",
            version = "3.11.4",
            description = "Interactive Python calculator, math evaluator & script interpreter",
            category = "Development",
            installed = true,
            size = "14.2 MB",
            command = "python"
        ),
        PackageItem(
            id = "neofetch",
            name = "Neofetch & Fastfetch",
            version = "7.1.0",
            description = "CLI system information tool with colorful Termux ASCII art",
            category = "Utilities",
            installed = true,
            size = "1.8 MB",
            command = "neofetch"
        ),
        PackageItem(
            id = "nano",
            name = "GNU nano Editor",
            version = "7.2",
            description = "Full-screen interactive terminal text editor with syntax highlighting",
            category = "Editors",
            installed = true,
            size = "3.4 MB",
            command = "nano"
        ),
        PackageItem(
            id = "cmatrix",
            name = "CMatrix Digital Rain",
            version = "2.0",
            description = "Simulates the falling green code screen from The Matrix",
            category = "Customization",
            installed = true,
            size = "850 KB",
            command = "matrix"
        ),
        PackageItem(
            id = "snake",
            name = "Terminal Snake",
            version = "1.0",
            description = "Classic arcade snake game playable in your terminal",
            category = "Games",
            installed = true,
            size = "620 KB",
            command = "snake"
        ),
        PackageItem(
            id = "2048",
            name = "Terminal 2048",
            version = "1.2",
            description = "The famous 2048 puzzle game directly on terminal",
            category = "Games",
            installed = true,
            size = "580 KB",
            command = "2048"
        ),
        PackageItem(
            id = "curl",
            name = "curl / wget HTTP Client",
            version = "8.4.0",
            description = "Command-line tool for transferring data with URLs & REST APIs",
            category = "Networking",
            installed = true,
            size = "4.1 MB",
            command = "curl"
        ),
        PackageItem(
            id = "tree",
            name = "Tree Directory Grapher",
            version = "2.1.1",
            description = "Recursive directory listing command that produces a depth-indented file tree",
            category = "Utilities",
            installed = true,
            size = "450 KB",
            command = "tree"
        ),
        PackageItem(
            id = "weather",
            name = "ANSI Weather Forecaster",
            version = "1.5.0",
            description = "Fetches formatted ASCII / ANSI weather forecasts",
            category = "Utilities",
            installed = true,
            size = "320 KB",
            command = "weather"
        ),
        PackageItem(
            id = "pipes",
            name = "Pipes.sh Screensaver",
            version = "1.3",
            description = "Animated retro pipe screensaver running in terminal",
            category = "Customization",
            installed = true,
            size = "290 KB",
            command = "pipes"
        ),
        PackageItem(
            id = "cowsay",
            name = "Cowsay & Figlet",
            version = "3.7.0",
            description = "Generates ASCII pictures of a cow with message bubbles & ASCII banners",
            category = "Fun",
            installed = true,
            size = "510 KB",
            command = "cowsay"
        ),
        PackageItem(
            id = "calc",
            name = "Scientific Math Calc",
            version = "2.15",
            description = "Arbitrary precision arithmetic expression calculator",
            category = "Math",
            installed = true,
            size = "1.2 MB",
            command = "calc"
        )
    )

    suspend fun recordCommand(command: String, workingDir: String, exitCode: Int) {
        if (command.isNotBlank()) {
            dao.insertHistory(
                CommandHistoryEntity(
                    command = command.trim(),
                    workingDir = workingDir,
                    exitCode = exitCode
                )
            )
        }
    }

    suspend fun clearHistory() {
        dao.clearHistory()
    }

    suspend fun saveScript(name: String, description: String, code: String, category: String) {
        dao.insertScript(
            SavedScriptEntity(
                name = name,
                description = description,
                code = code,
                category = category
            )
        )
    }

    suspend fun updateScript(script: SavedScriptEntity) {
        dao.updateScript(script)
    }

    suspend fun deleteScript(script: SavedScriptEntity) {
        dao.deleteScript(script)
    }

    suspend fun installPackage(pkgId: String) {
        dao.installPackage(InstalledPackageEntity(packageId = pkgId))
    }

    suspend fun uninstallPackage(pkgId: String) {
        dao.uninstallPackage(pkgId)
    }

    suspend fun preloadDefaultScriptsIfEmpty() {
        // Will be called on startup
    }
}
