# AGENTS.md

This file provides guidance to coding agents (Claude Code, Codex, etc.) when working with code in this repository.

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
- **SpotBugs analysis**: `gradlew.bat spotbugsMain` or `./gradlew spotbugsMain`; report in `build/reports/spotbugs/main.html` (and `main.xml`). The task fails while there are findings (currently 132, mostly `EI_EXPOSE_REP*` and `PA_PUBLIC_PRIMITIVE_ATTRIBUTE`), so `check`/`build` fail too; `assemble`, `run` and `winDist` don't run it
- **Code coverage (JaCoCo)**: `gradlew.bat test jacocoTestReport` or `./gradlew test jacocoTestReport`; report in `build/reports/jacoco/test/html/index.html` (and `jacocoTestReport.xml` / `.csv`). `test` alone records the data but doesn't write the report

### Build Artifacts
- JAR output: `build/libs/`
- Windows EXE distribution: `build/distributions/` (creates a zip file with bundled JRE)

## Project Architecture

QuakeInjector is a Swing-based desktop application that allows users to discover, download, and install Quake singleplayer maps from the Quaddicted archive. It follows an MVC/ViewModel structure: views in `gui`, view models in `guimodel`, the domain in `model` and the workflows in `feature.*`.

### Core Package Structure

- **`gui/`** — Swing UI components (windows, dialogs, panels, tables)
  - No business logic; solely responsible for rendering and user input
  - Text from the package database or a package (titles, ids, authors, start maps, file names, dialog messages) is never shown as HTML: Swing renders text starting with `<html>` in labels, buttons, menus, table cells and dialogs, and its HTML view loads images (a `file://server/share` image leaks the Windows password hash). Use `PlainText` (`label`, `menuItem`, `checkBox`, `message` for `JOptionPane`, `disableHtml` for renderers; the switch must be on before the text is set). The description pane gets HTML only through `PackageInteractionViewModel.getDescription`, cleaned by `utils.HtmlSanitizer`; HTML built in the views escapes database text with `HtmlSanitizer.escape`
  - `BrowserLauncher.openURL` opens only http and https URLs (`isWebAddress`); the OS would run anything else (`file:`, `ms-msdt:`, ...) with its program
  - Links in the description go to `PackageInteractionViewModel.openDescriptionLink` (the href as written) and from there to `guimodel.DescriptionLinkHandler`, set by `QuakeInjector`. `guimodel.DescriptionLink` sorts them: a package's SHA-256 (64 hex digits, the database's links to other packages; prerequisites link the same way) selects it (`PackageListViewModel.packageIdOfSha256`, `QuakeInjectorView.showPackage`); an absolute http or https address opens in the browser after the user agreed in a dialog; anything else (other schemes, other relative links) is logged as an error and not opened
  
- **`guimodel/`** — ViewModel classes that bridge UI and business logic
  - `PackageListViewModel`, `PackageInteractionViewModel`, `ConfigViewModel`, etc.
  - Encapsulates state and logic needed by the GUI views

- **`model/`** — Core data models and business logic
  - `Package`, `Requirement`, `Configuration`, `InstalledPackages`
  - Immutable where possible; change-event notification via listeners
  - `CommandHandler` — central router for async operations
  - `OfficialExpansion` — the hardcoded list of official expansions (hipnotic, rogue, mg1, mg3), which the package database can't install. A prerequisite on the list becomes an `OfficialExpansionRequirement` (made by the parsers), which is installed when `<Quake dir>/<id>/pak0.pak` exists (any case), checked on every `isInstalled()`; the installed packages list has no say. `MissingPrerequisites` names them in full in the Install/Play dialogs
  - `JobTracker` — one row in the install queue panel, which shows all background work (installs, uninstalls, loading the database, "Check for installed maps"); there are no progress popups: `setProgress` (0..100 or `UNKNOWN_PROGRESS`, shown as a busy bar), `finish`; call it on the EDT
  - `PackageUrls` — a package's web page and screenshot address on Quaddicted, by its SHA-256 under the addresses in the settings (null without a SHA-256); `QuakeInjector`'s `CommandRouter` opens the page and shows an error if the setting makes no valid address
  - `RecentPackages` — the ids of the packages installed or played most recently, newest first, at most `MAX_SIZE` (10). A successful install of the package the user picked (not a prerequisite installed along with it) and a started engine (`PackageLauncher`) move it to the top; an uninstall takes it off. Kept in the settings (`recentPackages`, comma-separated) through `RecentPackages.boundTo`, which are written when the app closes. Shown in the Recent menu (`gui.RecentMenu`, next to File) through `guimodel.RecentPackagesViewModel`, which leaves out ids not in the loaded package list and numbers the items 1-9, 0; clicking one selects it in the table (`QuakeInjectorView.showPackage`, clearing the filter if it hides the package)
  - Back / Forward history (in `guimodel.PackageHistoryViewModel`, this session only, at most `MAX_SIZE` (100) entries): works like a web browser. A selected package is recorded once it stayed selected for a second (`gui.HistoryNavigation`'s timer calls `settle`), so rows passed with the arrow keys are not; Back/Forward record a pending package first, and a new package drops the Forward entries. Ids not in the loaded list are skipped. The buttons left of the filter (Refresh next to them reloads the database), Alt+Left/Right and the mouse side buttons (4/5) go through `QuakeInjectorView.showPackage`; the buttons are disabled when there is nothing to go to

- **`feature/`** — Feature-specific implementations organized by user-facing workflows
  - **`feature.list/`** — Package database fetching, parsing, and search
    - `PackageDatabaseService` — main entry point, showing a row in the install queue panel (no popup): `loadCachedThenDownload` (startup) shows the cached database right away, then downloads and replaces the list; `download` (the Refresh button left of the filter) only downloads. A failed download keeps the list already shown; there is no fallback to the cache after a download. An error while showing the cached list (a bug in a package list listener) is logged and the download still runs; one while showing the downloaded list fails the row with its own error ("Showing the package list failed"), not as a download failure. The row is finished only after the list is shown
    - `PackageDatabaseJsonParser` / `PackageDatabaseSolrJsonParser` — API parsers
    - `PackageDatabaseCache` — reads the cache file (`database.json`); missing or broken reads as empty
    - `PackageDatabaseDownloadTask` — downloads and parses the database, and keeps the download as the new cache file
  - **`feature.install/`** — Download, unzip, dependency resolution, and finding out what is installed
    - `PackageInstallUninstallService` — main entry point (install, uninstall, "Check for installed maps", reading the installed packages file at startup: `loadInstalledStatus`)
    - `InstallPipeline` — the install process as a `CompletableFuture` chain: download (network thread) → inspect (disk thread) → ask about overwrites (UI thread) → extract (disk thread) → report to `InstallListener` (UI thread)
    - `Installer` — owns the network/disk threads and the install queue; the UI executor (`SwingUtilities::invokeLater`) is injected
    - `DownloadTask` / `InspectZipTask` / `ExtractTask` — the install stages; "extract" is the last stage, "install" always means the whole process
    - `UninstallTask` — removes a package's files
    - `CheckInstalled` — "Check for installed maps": looks for each package's files (`Package.getSupposedFileList`, copied from the loaded package list) in the Quake directory
  - **`feature.readme/`** — "Readme": shows the text files of an installed package
    - `ReadmeService` — main entry point, implementing `model.ReadmeSource` (guimodel may not use feature packages); `ReadmeViewModel` and `gui.ReadmeWindow` show the files
    - The files come from the package's installed file list, which holds the names in the Quake directory *after* the extract mapping (e.g. `readme.txt` in the zip is `mapname/mapname_readme.txt`); without one, from the database's file list, which the parser maps the same way. Never use the archive's own paths
    - `ReadmeFiles` — picks the `.txt` files and puts the likely readme first (`<package id>.txt`, `<map name>.txt`, `*readme*`, others, licenses/change logs last)
    - `ReadmeReader` — UTF-8 if valid, otherwise code page 437 (DOS readmes); normalizes line ends, drops a BOM, reads at most 1 MB
  - **`feature.play/`** — Quake engine launching
    - `PackageLauncher` — main entry point; prepares game state and launches engine
    - `EngineStarter` — invokes the Quake engine process with configured arguments; set up from the settings with `fromConfig`, updated with `applyConfig` when they change

- **`utils/`** — Shared utilities
  - `Download`, `ProgressListener*` — HTTP fetching with progress reporting
  - `Urls.escape` — escapes a web address put together from settings and database values (a space becomes `%20`, not `+`); used by `Download` and `model.PackageUrls`
  - `ChangeListener` / `ChangeListenerList` — custom observable pattern (predates Java Beans)
  - `Utils` — file operations, path handling
  - `HtmlSanitizer` — cleans the database's description HTML: keeps simple formatting tags without attributes and links with http, https or relative `href` (entities decoded before the check); drops everything else (images, styles, objects, forms, scripts), and escapes all text
  - `SafePath` — turns a relative path from a package or the database (zip entry, extract mapping, file list, package id) into a file inside a base directory, throwing `UnsafePathException` for anything outside (`..`, absolute, drive or UNC paths). Use it wherever such a path meets the disk: extract and inspect (an unsafe entry fails the whole install as `InstallFailure.Kind.UNSAFE_PATH` before anything is written), the download file, "Check for installed maps" (counts as missing), uninstall (skipped) and readme (not listed, not read). Tested in `SafePathTest` plus cases in the install, uninstall, check and readme tests

### Architectural Constraints

**Separation of Concerns**
- `gui/` must not import from `model/` directly; communication flows through `guimodel/`
- `feature/*` packages should not depend on `gui/` or `guimodel/`
- `feature/*`, `model/` and `utils/` must not use Swing or AWT; only `gui/` and the main class `QuakeInjector` may
- This is validated by `ArchitectureTest` using ArchUnit; it passes, keep it green

**Threading**
- All UI updates happen on the Event Dispatch Thread (EDT). `guimodel` classes are only called on the EDT and notify their views on it, synchronously (see `guimodel/package-info.java`).
- Views call `gui.Edt.check(...)` in `stateChanged`; off the EDT it logs an `[EDT violation]` stack trace once per view. It only logs: dialog methods such as `DialogViewModel.showOptions` return the user's answer, so the caller has to be on the EDT already.
- Long-running work runs off the EDT. `feature/*` must not use Swing, so it gets a UI `Executor` injected (`SwingUtilities::invokeLater`, passed in by `QuakeInjector`) and reports back through it.
- Installs and uninstalls (`feature.install`):
  - `Installer` owns two single-thread daemon executors: **network** (downloads, one at a time) and **disk** (inspect, extract, uninstall, cleanup and saving the installed list, one at a time, so they never touch the same files concurrently)
  - `InstallPipeline` chains the stages with `CompletableFuture`; no thread blocks waiting for another. The overwrite question and every `InstallListener` / `UninstallErrorHandler` callback (including progress) run on the UI executor.
  - Cancelling: one `utils.CancellationToken` per install, checked by every stage (before each zip entry and each written chunk; a download's stream is closed on cancel). Never cancel the future itself. After a failure or cancel, the pipeline deletes the partially written files on the disk thread *before* it reports, and the package stays in the install queue until then.
  - `Installer` sends queue change events on the UI executor; `PackageInteractionViewModel` listens to refresh the Install button.
  - Progress reaches the UI only when the whole percent changes (`UiProgress`).
  - The installed-packages file is snapshotted on the EDT (`SaveInstalled.snapshot`) and written on the disk thread.
- Outside installs:
  - Package database loading: `PackageDatabaseService` reads the cache and downloads on its own daemon thread (not the installer's network thread, so it doesn't wait behind a map download) and reports on the injected UI executor. It doesn't block the UI; a second load while one runs is ignored. Cancel goes through a `CancellationToken` that closes the download stream.
  - Replacing the package list (`RequirementList.setRequirements`) brings new `Package` objects, so anything that outlives a reload goes by package id: the installed state carries over to the new objects, the install queue (`Installer`) is keyed by id, and the selection is restored by id (`PackageListViewModel`, `PackageTable.tableChanged`). The package selected at close is saved in the settings (`selectedPackage`) and selected again, and scrolled to, on the first list with packages after startup (`PackageListViewModel.restoreSelection`); later lists never re-select it. The installed packages file is read only once, after the first list arrived.
  - Reading the installed packages file (`model.InstalledPackages`) and "Check for installed maps" (`PackageInstallUninstallService.checkForInstalledMaps`, running `CheckInstalled`) run on the installer's disk thread (`Installer.diskExecutor()`), because the disk thread also writes those files; results come back on the UI executor via `whenCompleteAsync`. Don't block the EDT on these futures: they may queue behind an install.
  - Quake engine search (Settings → Select next to Quake Engine, Windows only, needs a valid Quake Directory; otherwise the file chooser): `gui.EngineSelectionDialog` runs `utils.QuakeEngineSearch` (exe files in the Quake Directory and its direct subfolders, classified by `QuakeEngineDetector`, modern engines first) on its own daemon thread; closing the dialog cancels it.
  - Readme files are read on `ReadmeService`'s own daemon thread (not the disk thread, so they don't wait behind an install); the future completes on the UI executor. A file that finishes reading after the window moved on is ignored.
  - "Check for installed maps" doesn't block the UI: one check at a time (a second click while one runs is ignored), and its result is applied by package id, so installs, uninstalls or a database reload during the check are fine.

**Immutability**
- `Package`, `Requirement`, and other model objects are immutable (or should be treated as such)
- Listeners are used for change notification rather than in-place mutations

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
- Install tests: `ExtractTaskTest`, `DownloadTaskTest`, `UninstallTaskTest` per stage; `InstallProcessTest` (whole `InstallPipeline`) and `InstallerTest` (queue, cancel, uninstall) end to end; `PackageInstallUninstallServiceTest` (the service: install directory, prerequisites, overwrite question, failure messages, installed state saved)
- Check for installed maps: `CheckInstalledTest` (the file check), `CheckForInstalledMapsTest` (the whole service flow)
- Database loading: `PackageDatabaseDownloadTaskTest` (download, cache file, cancel), `PackageDatabaseServiceTest` (cache first then download, failures, cancel, installed state kept, one load at a time)
- Readme: `ReadmeFilesTest` (which files, order), `ReadmeReaderTest` (encodings, line ends, size limit), `ReadmeServiceTest` (end to end: a zip whose extract mapping renames the readme), `ReadmeViewModelTest` (switching files, late or failed reads), `PackageInteractionPanelViewTest` (the Readme button)
- Quake engine search: `QuakeEngineSearchTest` (search depth, order, cancel)
- HTML from the database: `HtmlSanitizerTest` (kept formatting, dropped tags and links), `DatabaseHtmlTest` (a malicious entry in the real description pane and table), `gui.PlainTextTest` (no HTML rendering, also under Darklaf), `gui.BrowserLauncherTest` (only http and https), `DescriptionLinkTest` (the three kinds of description links, the handler, a click in the real description pane, prerequisite links)
- Package web addresses: `PackageUrlsTest` (web page and screenshot by SHA-256, escaping, invalid settings); `DownloadTest` (escaping for downloads)
- Settings dialog: `ConfigViewModelTest` (Apply only when something differs from the saved settings and the paths are valid; reading the fields back as shown is no change); `ConfigurationWriteTest` (saving the settings file, failures reported)
- Engine setup: `EngineStarterConfigTest` (executable relative to the Quake directory or absolute, working directory, command line)
- Installed packages file at startup: `LoadInstalledStatusTest` (marked installed, missing file, broken file)
- Reloading the list: `RequirementListTest` (installed state carries over), `PackageTableSelectionTest` (selection survives, selection of the last session restored), `InstallerTest` (queue by package id)
- Official expansions: `OfficialExpansionTest` (the list, finding the files), `OfficialExpansionRequirementTest` (installed state, messages), plus cases in `PackageDatabaseSolrJsonParserTest` and `PackageInstallUninstallServiceTest`
- Back / Forward history: `PackageHistoryViewModelTest` (browser-like moves, settling, Forward dropped, missing packages, size limit)
- Recent packages: `RecentPackagesTest` (order, size limit, settings value), `PackageLauncherTest` (playing; the engine is the test's own java executable), `RecentPackagesViewModelTest` (the menu's items, updates, `PackageListViewModel.rowIndexOf`), plus cases in `PackageInstallUninstallServiceTest`
- Prefer focused, single-concern tests over integration suites

### Test helpers
- `TestZip` builds real zip files (also stored or corrupted entries); downloads use `file://` URLs, so no server or mocking is needed
- `TestThreads` provides named single-thread executors standing in for the network, disk and UI threads; install tests assert that every listener call runs on the "UI" one
- `RecordingInstallListener` records install callbacks, their order and threads
- `RecordingDialogs` is a `DialogProvider` that shows nothing and records the dialogs asked for, and their threads; dialogs that return an answer (options, overwrite, engine config) fail unless the test sets one
- GUI tests (`InstallQueuePanelTest`) build real Swing components on the EDT with `SwingUtilities.invokeAndWait`; they need no window, so they run headless (modal dialogs can't be tested this way)
- Files and Windows: close every stream (also on failure), otherwise the `@TempDir` can't be deleted and the test fails

### No Mocking
- Tests use real files, real objects; integration testing is preferred
- This keeps tests close to real behavior but means they can be slower

## Dependencies

**Key Libraries:**
- **Swing** — GUI framework (bundled with Java)
- **Jackson** (v2.13.3) — JSON parsing for package database API responses
- **Apache Commons Compress** (v1.27.1) — ZIP extraction during installation
- **Darklaf** (v2.7.3) — Modern dark/light look-and-feel support
- **svgSalamander** (v1.1.2.4, also used by Darklaf) — draws the SVG icons (`gui.SvgIcon`: a single-color SVG from the resources in the button's text color, e.g. the Uninstall trash can `delete_icon.svg`)
- **BrowserLauncher2** (v1.3) — Cross-platform web browser launching
- **JUnit 5** (v5.10.2) — Testing framework
- **JaCoCo** (v0.8.13) — Code coverage
- **ArchUnit** (v1.4.1) — Architecture testing

**Build Plugins:**
- **Launch4j** (v2.5.0) — Builds Windows EXE from JAR
- **SpotBugs** (Gradle plugin v6.5.13, SpotBugs v4.10.5 pinned via `toolVersion`) — Static bug analysis

## Commit Messages

- Keep them short: a brief one-line summary, lowercase like the existing history
- At most one more paragraph, and only when something about the change isn't obvious
- Never add a `Co-Authored-By` line (or any other attribution trailer)

## Version Management

- Version is determined from Git tags via `git describe --tags` in build.gradle
- To release: `git tag -a "alphaXX" -m "alphaXX"` → will produce `QuakeInjector-alphaXX.jar`
- No manual version strings in code; always derived from tags

## Distribution

- **JAR** — Self-contained, requires Java 17+
- **Windows EXE** — Includes bundled OpenJDK 17 (32-bit via Launch4j); distributed as a zip archive
- Build process downloads the JRE during `winDist` task (one-time cost, cached in `build/jre`)
- See `setupWinDist` task in build.gradle for distribution assembly details
- Licenses: `COPYING` and everything in `licenses/` (`THIRD_PARTY_NOTICES.txt`, `LICENSE-APACHE-2.0.txt`, `LICENSE-LGPL-2.1.txt`) go into the JAR (`META-INF/`), the `distZip` zip and the Windows zip. When adding third-party material (icons, images, libraries, also new transitive ones) or changing a library version, update `THIRD_PARTY_NOTICES.txt` (it names the versions and copies the NOTICE files of Apache libraries), plus the license text if it is a new one

## Known Issues and TODOs

- **Prerequisites** — `PackageInstallUninstallService.installRequirements` queues prerequisites as separate installs that don't wait for each other; the main package doesn't wait for them, and a failed prerequisite doesn't stop it
- **SpotBugs** — `AT_STALE_THREAD_WRITE_OF_PRIMITIVE` on `InstallQueueViewModel.JobVm` is a false positive: the fields are only used on the EDT, which SpotBugs can't see
- **Case-sensitive filesystems** — Linux/macOS cases aren't fully handled; many Quake maps are packed with mixed-case filenames
- **Legacy engine limits** — Some modern maps exceed original Quake engine constraints; users may need a modern engine like QuakeSpasm or vkQuake
