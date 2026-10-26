package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.guimodel.ConfigViewModel;
import de.haukerehfeld.quakeinjector.model.CommandHandler;
import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.Package;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The settings dialog's state: Apply only when something differs from the saved settings and the paths
 * are valid. Reading the fields back as the dialog shows them is no change.
 */
public class ConfigViewModelTest {
	@TempDir
	Path tmp;

	private File quakeDir;
	private Configuration config;
	private int saves = 0;
	private int changes = 0;
	private ConfigViewModel vm;

	private final CommandHandler commands = new CommandHandler() {
		public void install(Package p) {}
		public void uninstall(Package p) {}
		public void play(Package p, String startmap) {}
		public void saveConfig() { saves++; }
		public boolean isInstallInProgress(Package p) { return false; }
		public void openPackageWebpage(Package p) {}
		public void openScreenshotInBrowser(Package p) {}
		public URI getScreenshotUri(Package p) { return null; }
	};

	@BeforeEach
	public void setUp() throws Exception {
		quakeDir = Files.createDirectories(tmp.resolve("quake")).toFile();
		config = new Configuration(tmp.resolve("config.properties").toFile());
		config.EnginePath.set(quakeDir);
		config.EngineExecutable.set(new File("quakespasm.exe"));
		config.RogueInstalled.set(true);
		vm = new ConfigViewModel(commands, config, code -> code == null ? "system" : code);
		vm.addChangeListener(e -> changes++);
	}

	/** What the dialog reports right after opening: full paths, as typed, all valid. */
	private void reportFieldsAsShown() {
		vm.setEngineCommandLine("");
		vm.setEnginePath(new File(quakeDir.getAbsolutePath()), true);
		vm.setEngineExecutable(new File(quakeDir.getAbsolutePath() + File.separator + "quakespasm.exe"), true);
		vm.setDownloadPath(new File(quakeDir, "downloads"));
		vm.setUiTheme("system");
	}

	@Test
	public void openingIsNoChange() {
		reportFieldsAsShown();

		assertFalse(vm.isDirty());
		assertFalse(vm.canApply());
		assertTrue(vm.canOkay());
	}

	@Test
	public void noThemeSavedReadsAsTheDefault() {
		assertEquals("system", vm.getUiTheme());
	}

	@Test
	public void changingAValueEnablesApply() {
		reportFieldsAsShown();

		vm.setHipnoticInstalled(true);

		assertTrue(vm.isDirty());
		assertTrue(vm.canApply());
	}

	@Test
	public void changingItBackDisablesApply() {
		reportFieldsAsShown();

		vm.setRogueInstalled(false);
		vm.setRogueInstalled(true);
		vm.setEngineCommandLine("-heapsize 64000");
		vm.setEngineCommandLine("");

		assertFalse(vm.isDirty());
		assertFalse(vm.canApply());
	}

	@Test
	public void theSameValueAgainNotifiesNoOne() {
		reportFieldsAsShown();
		changes = 0;

		vm.setRogueInstalled(true);
		vm.setEnginePath(quakeDir, true);

		assertEquals(0, changes);
	}

	@Test
	public void everySettingCountsAsAChange() {
		reportFieldsAsShown();
		Runnable[] edits = {
			() -> vm.setEngineCommandLine("-game foo"),
			() -> vm.setEnginePath(tmp.toFile(), true),
			() -> vm.setEngineExecutable(new File(quakeDir, "vkquake.exe"), true),
			() -> vm.setDownloadPath(tmp.toFile()),
			() -> vm.setRogueInstalled(false),
			() -> vm.setHipnoticInstalled(true),
			() -> vm.setWorkingDirAtExecutable(true),
			() -> vm.setUiTheme("darcula"),
		};
		for (Runnable edit : edits) {
			vm.updateFromConfig();
			reportFieldsAsShown();
			edit.run();
			assertTrue(vm.isDirty());
		}
	}

	@Test
	public void anInvalidPathBlocksSaving() {
		reportFieldsAsShown();
		vm.setRogueInstalled(false);

		vm.setEngineExecutable(new File(quakeDir, "missing.exe"), false);

		assertTrue(vm.isDirty());
		assertFalse(vm.canApply());
		assertFalse(vm.canOkay());
	}

	@Test
	public void anInvalidSavedPathStillLetsOkayClose() {
		reportFieldsAsShown();

		vm.setEnginePath(quakeDir, false);

		assertFalse(vm.isDirty());
		assertTrue(vm.canOkay());
	}

	@Test
	public void applySavesAndTheEditsBecomeTheSavedSettings() {
		reportFieldsAsShown();
		vm.setHipnoticInstalled(true);
		vm.setEngineExecutable(new File(quakeDir, "bin" + File.separator + "vkquake.exe"), true);
		vm.setUiTheme("darcula");

		vm.applyConfig();

		assertEquals(1, saves);
		assertFalse(vm.isDirty());
		assertFalse(vm.canApply());
		assertTrue(config.HipnoticInstalled.get());
		assertEquals(new File("bin", "vkquake.exe"), config.EngineExecutable.get());
		assertEquals("darcula", config.uiTheme.get());
	}

	@Test
	public void onlyANewThemeNeedsARestart() {
		reportFieldsAsShown();
		vm.setRogueInstalled(false);
		assertFalse(vm.isRestartNeededForTheme());

		vm.setUiTheme("darcula");
		assertTrue(vm.isRestartNeededForTheme());

		vm.setUiTheme("system");
		assertFalse(vm.isRestartNeededForTheme());
	}

	@Test
	public void aSavedNewThemeStillNeedsARestart() {
		reportFieldsAsShown();
		vm.setUiTheme("darcula");
		vm.applyConfig();
		vm.updateFromConfig();

		assertFalse(vm.isDirty());
		assertTrue(vm.isRestartNeededForTheme());
	}

	@Test
	public void reopeningDropsUnsavedEdits() {
		reportFieldsAsShown();
		vm.setRogueInstalled(false);

		vm.updateFromConfig();

		assertTrue(vm.isRogueInstalled());
		assertFalse(vm.isDirty());
	}
}
