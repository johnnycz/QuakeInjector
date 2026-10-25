package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.readme.ReadmeFiles;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which files of a package count as text files, and the order: most likely readme first.
 */
public class ReadmeFilesTest {
	@Test
	public void onlyTextFilesIgnoringCase() {
		List<String> files = List.of("jam9/maps/jam9.bsp", "jam9/pak0.pak", "jam9/Notes.TXT", "jam9/maps/");

		assertEquals(List.of("jam9/Notes.TXT"), ReadmeFiles.order("jam9", List.of(), files));
	}

	@Test
	public void noTextFiles() {
		assertEquals(List.of(), ReadmeFiles.order("jam9", List.of(), List.of("jam9/maps/jam9.bsp")));
	}

	@Test
	public void packageNameFirstThenMapNameThenReadmeThenOthersThenLicenses() {
		List<String> files = List.of(
			"jam9/license.txt",
			"jam9/notes.txt",
			"jam9/jam9_readme.txt",
			"jam9/maps/jam9_e1m1.bsp",
			"jam9/maps/jam9_e1m1.txt",
			"jam9/changelog.txt",
			"jam9/jam9.txt");

		assertEquals(List.of(
				"jam9/jam9.txt",
				"jam9/maps/jam9_e1m1.txt",
				"jam9/jam9_readme.txt",
				"jam9/notes.txt",
				"jam9/changelog.txt",
				"jam9/license.txt"),
			ReadmeFiles.order("jam9", List.of(), files));
	}

	@Test
	public void startmapsCountAsMapNames() {
		List<String> files = List.of("id1/notes.txt", "id1/start.txt");

		assertEquals(List.of("id1/start.txt", "id1/notes.txt"), ReadmeFiles.order("pkg", List.of("start"), files));
	}

	@Test
	public void higherUpInTheTreeFirstWithinTheSameRank() {
		List<String> files = List.of("jam9/maps/b_readme.txt", "jam9/readme.txt");

		assertEquals(List.of("jam9/readme.txt", "jam9/maps/b_readme.txt"), ReadmeFiles.order("jam9", List.of(), files));
	}

	@Test
	public void backslashSeparatedNames() {
		List<String> files = List.of("jam9\\notes.txt", "jam9\\jam9.txt");

		assertEquals(List.of("jam9\\jam9.txt", "jam9\\notes.txt"), ReadmeFiles.order("jam9", List.of(), files));
	}
}
