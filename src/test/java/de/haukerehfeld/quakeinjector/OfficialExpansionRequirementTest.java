package de.haukerehfeld.quakeinjector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.MissingPrerequisites;
import de.haukerehfeld.quakeinjector.model.OfficialExpansion;
import de.haukerehfeld.quakeinjector.model.OfficialExpansionRequirement;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.model.UnavailableRequirement;

/**
 * A map's official expansion prerequisite: installed when it is in the Quake directory, whatever the
 * installed packages list says, and named in full in messages.
 */
public class OfficialExpansionRequirementTest {
	@TempDir
	Path quakeDir;

	private Requirement hipnotic() {
		return OfficialExpansionRequirement.forMissingPackage("hipnotic", quakeDir::toFile);
	}

	private void installHipnotic() throws Exception {
		Files.createFile(Files.createDirectories(quakeDir.resolve("hipnotic")).resolve("pak0.pak"));
	}

	private Package mapNeeding(Requirement... requirements) {
		Package map = TestZip.testPackage("limbo2", new ExtractMapping(), "file:///limbo2.zip");
		map.setRequirements(List.of(requirements));
		return map;
	}

	@Test
	public void anOfficialExpansionMissingFromTheDatabaseIsOne() {
		Requirement r = hipnotic();

		assertInstanceOf(OfficialExpansionRequirement.class, r);
		assertEquals("hipnotic", r.getId());
		assertEquals("Scourge of Armagon (Mission Pack 1)", r.toString());
	}

	@Test
	public void anyOtherMissingPackageStaysUnavailable() {
		assertInstanceOf(UnavailableRequirement.class,
			OfficialExpansionRequirement.forMissingPackage("drake", quakeDir::toFile));
	}

	@Test
	public void installedOnceItsFilesAreInTheQuakeDirectory() throws Exception {
		Requirement r = hipnotic();
		assertFalse(r.isInstalled());

		installHipnotic();

		assertTrue(r.isInstalled(), "looked up again, not remembered");
	}

	@Test
	public void theInstalledPackagesListHasNoSay() throws Exception {
		Requirement r = hipnotic();
		r.setInstalled(true);
		assertFalse(r.isInstalled());

		installHipnotic();
		r.setInstalled(false);
		assertTrue(r.isInstalled());
	}

	@Test
	public void theQuakeDirectoryAsCurrentlySetIsUsed() throws Exception {
		installHipnotic();
		File[] current = { null };
		Requirement r = OfficialExpansionRequirement.forMissingPackage("hipnotic", () -> current[0]);
		assertFalse(r.isInstalled(), "no Quake directory set");

		current[0] = quakeDir.toFile();

		assertTrue(r.isInstalled());
	}

	@Test
	public void aMapNeedingAnInstalledExpansionMissesNothing() throws Exception {
		installHipnotic();
		Package map = mapNeeding(hipnotic());

		// what Install and Play check
		assertEquals(List.of(), map.getUnavailableRequirements());
		assertEquals(List.of(), map.getUnmetRequirements());
	}

	@Test
	public void aMapNeedingAMissingExpansionMissesIt() {
		Requirement hipnotic = hipnotic();
		Package map = mapNeeding(hipnotic);

		assertEquals(List.of(hipnotic), map.getUnavailableRequirements());
		assertEquals(List.of(hipnotic), map.getUnmetRequirements());
	}

	@Test
	public void messagesNameOfficialExpansionsInFull() {
		String text = MissingPrerequisites.describe(List.of(hipnotic(), new UnavailableRequirement("drake")));

		assertEquals("- Scourge of Armagon (Mission Pack 1): official expansion, not found in your Quake directory\n"
			+ "- drake", text);
	}

	@Test
	public void theExpansionIsTheOneFromTheList() {
		assertEquals(OfficialExpansion.forId("hipnotic").orElseThrow(),
			((OfficialExpansionRequirement) hipnotic()).getExpansion());
	}
}
