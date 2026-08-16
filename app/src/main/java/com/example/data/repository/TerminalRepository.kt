package com.example.data.repository

import com.example.data.local.CommandHistoryEntity
import com.example.data.local.InstalledPackageEntity
import com.example.data.local.SavedScriptEntity
import com.example.data.local.TerminalDao
import com.example.data.model.PackageItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

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
            id = "htop",
            name = "htop Process Viewer",
            version = "3.2.2",
            description = "Interactive process monitor with CPU/RAM usage meters",
            category = "System",
            installed = false,
            size = "1.6 MB",
            command = "htop"
        ),
        PackageItem(
            id = "jq",
            name = "jq JSON Processor",
            version = "1.6",
            description = "Command-line JSON parser, slicer, filter and pretty printer",
            category = "Utilities",
            installed = false,
            size = "1.1 MB",
            command = "jq"
        ),
        PackageItem(
            id = "speedtest",
            name = "Speedtest CLI",
            version = "2.1.3",
            description = "Simulated internet bandwidth and latency benchmarking tool",
            category = "Networking",
            installed = false,
            size = "2.3 MB",
            command = "speedtest"
        ),
        PackageItem(
            id = "nmap",
            name = "nmap Network Scanner",
            version = "7.94",
            description = "Network exploration tool and security / port scanner simulation",
            category = "Networking",
            installed = false,
            size = "5.8 MB",
            command = "nmap"
        ),
        PackageItem(
            id = "todo",
            name = "Todo CLI Manager",
            version = "1.4.0",
            description = "Command-line productivity task manager with tags and status",
            category = "Utilities",
            installed = false,
            size = "480 KB",
            command = "todo"
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
            id = "fortune",
            name = "Fortune Cookie",
            version = "1.9.1",
            description = "Displays inspirational, humorous quotes and Unix epigrams",
            category = "Fun",
            installed = true,
            size = "350 KB",
            command = "fortune"
        ),
        PackageItem(
            id = "calc",
            name = "Scientific Math Calc (bc)",
            version = "2.15",
            description = "Arbitrary precision arithmetic expression calculator",
            category = "Math",
            installed = true,
            size = "1.2 MB",
            command = "calc"
        ),
        PackageItem(
            id = "whois",
            name = "whois Domain Lookup",
            version = "5.5.17",
            description = "Simulated internet domain name and IP address directory lookup",
            category = "Networking",
            installed = false,
            size = "760 KB",
            command = "whois"
        )
    )

    private val defaultInstalledPackages = listOf("python", "neofetch", "nano", "cmatrix", "snake", "2048", "curl", "tree", "weather", "pipes", "cowsay", "fortune", "calc")

    suspend fun preloadDefaultPackages() {
        val existing = dao.getAllInstalledPackages().first()
        if (existing.isEmpty()) {
            defaultInstalledPackages.forEach { pkgId ->
                dao.installPackage(InstalledPackageEntity(packageId = pkgId))
            }
        }
    }

    suspend fun getInstalledPackageIds(): Set<String> {
        val list = dao.getAllInstalledPackages().first()
        return list.map { it.packageId }.toSet()
    }

    suspend fun isPackageInstalled(pkgId: String): Boolean {
        val installed = getInstalledPackageIds()
        return installed.contains(pkgId.lowercase())
    }

    suspend fun getAllPackagesWithStatus(): List<PackageItem> {
        val installed = getInstalledPackageIds()
        return availablePackages.map { pkg ->
            pkg.copy(installed = installed.contains(pkg.id.lowercase()))
        }
    }

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
        dao.installPackage(InstalledPackageEntity(packageId = pkgId.lowercase()))
    }

    suspend fun uninstallPackage(pkgId: String) {
        dao.uninstallPackage(pkgId.lowercase())
    }

    suspend fun preloadDefaultScriptsIfEmpty() {
        // Preloaded via ViewModel
    }
}
