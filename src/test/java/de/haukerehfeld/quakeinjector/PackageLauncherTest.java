package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.play.EngineStarter;
import de.haukerehfeld.quakeinjector.feature.play.PackageLauncher;
import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.RecentPackages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Playing a package with {@link PackageLauncher}: only a package whose engine was started becomes the
 * most recent one. The "engine" is the java executable running these tests; it gets Quake arguments, so
 * it exits right away with an error, which is fine here.
 */
public class PackageLauncherTest {
	@TempDir
	Path tmp;

	private final RecordingDialogs dialogs = new RecordingDialogs();
	private final RecentPackages recent = new RecentPackages();
	private Configuration config;

	@BeforeEach
	public void setUp() {
		config = new Configuration(tmp.resolve("config.properties").toFile());
	}

	private static File javaExecutable() {
		String name = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
		return new File(System.getProperty("java.home"), "bin" + File.separator + name);
	}

	private PackageLauncher launcher(File engine) {
		return new PackageLauncher(dialogs, new EngineStarter(tmp.toFile(), engine, config.EngineCommandLine), recent);
	}

	private static Package installedPackage(String id) {
		Package pkg = TestZip.testPackage(id, new ExtractMapping(), "file:///" + id + ".zip");
		pkg.setInstalled(true);
		return pkg;
	}

	@Test
	public void aPlayedPackageBecomesTheMostRecent() {
		recent.add("other");

		launcher(javaExecutable()).start(installedPackage("a"), "start");

		assertEquals(List.of("engine output"), dialogs.calls);
		assertEquals(List.of("a", "other"), recent.ids());
	}

	@Test
	public void playingAPackageOnTheListMovesItToTheTop() {
		recent.add("a");
		recent.add("b");

		launcher(javaExecutable()).start(installedPackage("a"), "start");

		assertEquals(List.of("a", "b"), recent.ids());
	}

	@Test
	public void aPackageThatIsntInstalledIsntStartedOrMadeRecent() {
		Package pkg = installedPackage("a");
		pkg.setInstalled(false);

		launcher(javaExecutable()).start(pkg, "start");

		assertEquals(List.of("error: Map not installed"), dialogs.calls);
		assertEquals(List.of(), recent.ids());
	}

	@Test
	public void withoutAWorkingEngineNothingBecomesRecent() {
		launcher(tmp.resolve("no-engine.exe").toFile()).start(installedPackage("a"), "start");

		assertEquals(List.of("error: Quake engine paths not configured"), dialogs.calls);
		assertEquals(List.of(), recent.ids());
	}

	@Test
	public void cancellingBecauseOfMissingPrerequisitesDoesntMakeItRecent() {
		Package pkg = installedPackage("a");
		pkg.setRequirements(List.of(TestZip.testPackage("base", new ExtractMapping(), "file:///base.zip")));
		dialogs.optionAnswer = 1; // "Cancel Start"

		launcher(javaExecutable()).start(pkg, "start");

		assertEquals(List.of("options: Prerequisites not installed"), dialogs.calls);
		assertEquals(List.of(), recent.ids());
	}

	@Test
	public void startingAnywayWithMissingPrerequisitesMakesItRecent() {
		Package pkg = installedPackage("a");
		pkg.setRequirements(List.of(TestZip.testPackage("base", new ExtractMapping(), "file:///base.zip")));
		dialogs.optionAnswer = 0; // "Start anyways"

		launcher(javaExecutable()).start(pkg, "start");

		assertEquals(List.of("options: Prerequisites not installed", "engine output"), dialogs.calls);
		assertEquals(List.of("a"), recent.ids());
	}

	@Test
	public void nothingToPlayDoesNothing() {
		launcher(javaExecutable()).start(null, "start");

		assertEquals(List.of(), dialogs.calls);
		assertEquals(List.of(), recent.ids());
	}
}
