package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.model.RequirementList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Replacing the package list, as a reloaded package database does.
 */
public class RequirementListTest {
	private static Package pkg(String id) {
		return TestZip.testPackage(id, new ExtractMapping(), "file:///unused.zip");
	}

	private static List<Requirement> list(Requirement... requirements) {
		return new ArrayList<>(List.of(requirements));
	}

	@Test
	public void replacingKeepsTheInstalledState() {
		RequirementList packages = new RequirementList(list(pkg("a"), pkg("b")));
		PackageFileList files = new PackageFileList("a");
		packages.setInstalled(List.of(files));
		Package newA = pkg("a");
		Package newB = pkg("b");

		packages.setRequirements(list(newA, newB));

		assertSame(newA, packages.get("a"));
		assertTrue(newA.isInstalled());
		assertSame(files, newA.getFileList());
		assertFalse(newB.isInstalled());
	}

	@Test
	public void packagesThatAreGoneAreDropped() {
		RequirementList packages = new RequirementList(list(pkg("a")));
		packages.setInstalled(List.of(new PackageFileList("a")));

		packages.setRequirements(list(pkg("b")));

		assertFalse(packages.get("b").isInstalled());
		assertEquals(1, packages.size());
	}
}
