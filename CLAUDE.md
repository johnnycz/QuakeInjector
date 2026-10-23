# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick Start Commands

### Running the Application
- **Development run**: `gradlew.bat run` (Windows) or `./gradlew run` (Unix)
- **Build JAR**: `gradlew.bat assemble` (Windows) or `./gradlew assemble` (Unix)
- **Build Windows EXE with bundled JRE**: `gradlew.bat winDist` (Windows) or `./gradlew winDist` (Unix)

### Testing
- **Run all tests**: `gradlew.bat test` or `./gradlew test`
- **Run single test**: `gradlew.bat test --tests ClassName` or `./gradlew test --tests ClassName`
- **Run tests with parallel forks disabled** (default, required for this project): Tests already run serially (maxParallelForks = 1)

### Code Quality
- **SpotBugs analysis**: `gradlew.bat spotbugsMain` or `./gradlew spotbugsMain`

### Build Artifacts
- JAR output: `build/libs/`
- Windows EXE distribution: `build/distributions/` (creates a zip file with bundled JRE)

## Project Architecture

QuakeInjector is a Swing-based desktop application that allows users to discover, download, and install Quake singleplayer maps from the Quaddicted archive. The codebase is undergoing a major refactoring from a monolithic architecture towards a cleaner MVC/ViewModel pattern.

### Core Package Structure

- **`gui/`** — Swing UI components (windows, dialogs, panels, tables)
  - No business logic; solely responsible for rendering and user input
  - Currently being refactored to reduce direct model references
  
- **`guimodel/`** — ViewModel classes that bridge UI and business logic
  - `PackageListViewModel`, `PackageInteractionViewModel`, `ConfigViewModel`, etc.
  - Encapsulates state and logic needed by the GUI views
  - Being expanded as part of current refactoring to improve GUI decoupling

- **`model/`** — Core data models and business logic
  - `Package`, `Requirement`, `Configuration`, `InstalledPackages`
  - Immutable where possible; change-event notification via listeners
  - `CommandHandler` — central router for async operations
  - `JobTracker` — tracks pending operations to prevent concurrent conflicts

- **`feature/`** — Feature-specific implementations organized by user-facing workflows
  - **`feature.list/`** — Package database fetching, parsing, and search
    - `PackageDatabaseService` — main entry point; manages fetching and parsing
    - `PackageDatabaseJsonParser` / `PackageDatabaseSolrJsonParser` — API parsers
    - `PackageDatabaseParserWorker` — SwingWorker for async parsing
  - **`feature.install/`** — Download, unzip, and dependency resolution
    - `PackageInstallUninstallService` — main entry point
    - `PackageInstallOrchestrationTask` — the install process: download → inspect → ask about overwrites → extract
    - `DownloadTask` / `InspectZipTask` / `ExtractTask` — the install stages; "extract" is the last stage, "install" always means the whole process
    - `UninstallTask` — removes a package's files
    - `Installer` — core installation logic (package extraction, dependency traversal)
  - **`feature.play/`** — Quake engine launching
    - `PackageLauncher` — main entry point; prepares game state and launches engine
    - `EngineStarter` — invokes the Quake engine process with configured arguments

- **`utils/`** — Shared utilities
  - `Download`, `ProgressListener*` — HTTP fetching with progress reporting
  - `ChangeListener` / `ChangeListenerList` — custom observable pattern (predates Java Beans)
  - `Utils` — file operations, path handling

### Architectural Constraints

**Separation of Concerns (Current Goal)**
- `gui/` must not import from `model/` directly; communication flows through `guimodel/`
- `feature/*` packages should not depend on `gui/` or `guimodel/`
- This is validated by `ArchitectureTest` using ArchUnit

**Threading**
- All UI updates must occur on the Event Dispatch Thread (EDT)
- Long-running operations (downloads, parsing, extraction) must run off-EDT
- Currently using SwingWorker; refactoring may change this pattern, but EDT constraint remains
- `JobTracker` prevents concurrent operations on the same resource to avoid corruption

**Immutability**
- `Package`, `Requirement`, and other model objects are immutable (or should be treated as such)
- Listeners are used for change notification rather than in-place mutations

## Recent Refactoring Context

The branch `refactor_mvc_interaction_viewmodel` is actively working to:
1. Decouple the GUI from model classes by introducing/expanding ViewModels
2. Improve test isolation via architecture tests
3. Potentially reduce or eliminate SwingWorker dependencies in favor of cleaner async patterns
4. Ensure all cross-layer communication flows through ViewModel objects

When touching GUI-related code, prefer adding/expanding ViewModel classes over letting the GUI reference models directly.

## Testing Patterns

### JUnit 5
- All tests inherit from or use JUnit 5 (`org.junit.jupiter`)
- Run with `useJUnitPlatform()` in build.gradle

### ArchUnit
- Architecture tests ensure layer separation: GUI must not directly reference models
- Located in `ArchitectureTest`; run as a normal JUnit test
- Update these tests if you intentionally change architectural constraints

### Unit Tests
- Parser tests (`PackageDatabaseJsonParserTest`, `PackageDatabaseSolrJsonParserTest`) validate data transformation
- Path tests (`RelativePathTest`) verify file operations on different OS
- Prefer focused, single-concern tests over integration suites

### No Mocking
- Tests use real files, real objects; integration testing is preferred
- This keeps tests close to real behavior but means they can be slower

## Dependencies

**Key Libraries:**
- **Swing** — GUI framework (bundled with Java)
- **Jackson** (v2.13.3) — JSON parsing for package database API responses
- **Apache Commons Compress** (v1.27.1) — ZIP extraction during installation
- **Darklaf** (v2.7.3) — Modern dark/light look-and-feel support
- **BrowserLauncher2** (v1.3) — Cross-platform web browser launching
- **JUnit 5** (v5.10.2) — Testing framework
- **ArchUnit** (v1.4.1) — Architecture testing

**Build Plugins:**
- **Launch4j** (v2.5.0) — Builds Windows EXE from JAR
- **SpotBugs** (v4.7.1) — Static bug analysis

## Version Management

- Version is determined from Git tags via `git describe --tags` in build.gradle
- To release: `git tag -a "alphaXX" -m "alphaXX"` → will produce `QuakeInjector-alphaXX.jar`
- No manual version strings in code; always derived from tags

## Distribution

- **JAR** — Self-contained, requires Java 17+
- **Windows EXE** — Includes bundled OpenJDK 17 (32-bit via Launch4j); distributed as a zip archive
- Build process downloads the JRE during `winDist` task (one-time cost, cached in `build/jre`)
- See `setupWinDist` task in build.gradle for distribution assembly details

## Known Issues and TODOs

- **SwingWorker usage** — Current refactoring is moving away from direct SwingWorker instantiation toward a more testable pattern; some cleanup remains (see recent commits "simplify arch unit test, tbd SwingWorker dependency")
- **Case-sensitive filesystems** — Linux/macOS cases aren't fully handled; many Quake maps are packed with mixed-case filenames
- **Legacy engine limits** — Some modern maps exceed original Quake engine constraints; users may need a modern engine like QuakeSpasm or vkQuake
