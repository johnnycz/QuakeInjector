package de.haukerehfeld.quakeinjector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;
import de.haukerehfeld.quakeinjector.utils.QuakeEngineDetector.EngineType;
import de.haukerehfeld.quakeinjector.utils.QuakeEngineSearch;

public class QuakeEngineSearchTest {
	private static final String ENGINE = "gfx/palette.lmp gfx/colormap.lmp gfx.wad quake.rc maps/%s.bsp "
		+ "svc_serverinfo svc_stufftext svc_intermission Host_Error PR_ExecuteProgram ";
	private static final String BSP2 = "BSP2 2PSB ";
	private static final String MODERN = "csprogs.dat CSQC_DrawHud CSQC_DrawScores checkextension "
		+ "DP_QC_SPRINTF FTE_STRINGS FRIK_FILE traileffectnum ";

	@TempDir
	Path quakeDir;

	@Test
	public void findsExesInRootAndFirstSubfolderOnly() throws IOException {
		write("root.exe", "MZ");
		write("UPPER.EXE", "MZ");
		write("readme.txt", "MZ");
		write("sub/engine.exe", "MZ");
		write("sub/deeper/too-deep.exe", "MZ");
		Files.createDirectories(quakeDir.resolve("folder.exe"));

		List<String> found = QuakeEngineSearch.findExecutables(quakeDir.toFile()).stream()
			.map(f -> quakeDir.relativize(f.toPath()).toString().replace(File.separatorChar, '/'))
			.sorted()
			.collect(Collectors.toList());

		assertEquals(List.of("UPPER.EXE", "root.exe", "sub/engine.exe"), found);
	}

	@Test
	public void ordersModernEnhancedLegacyThenOthers() throws IOException {
		write("a-other.exe", "MZ nothing here");
		write("b-legacy.exe", "MZ" + ENGINE);
		write("c-enhanced/c.exe", "MZ" + ENGINE + BSP2);
		write("d-modern.exe", "MZ" + ENGINE + BSP2 + MODERN);
		write("e-modern/e.exe", "MZ" + ENGINE + MODERN);

		List<int[]> progress = new ArrayList<>();
		List<QuakeEngineSearch.Result> results = QuakeEngineSearch.search(
			quakeDir.toFile(), new CancellationToken(), (done, total) -> progress.add(new int[] { done, total }));

		assertEquals(List.of(EngineType.MODERN, EngineType.MODERN, EngineType.ENHANCED,
		                     EngineType.LEGACY, EngineType.NOT_ENGINE),
		             results.stream().map(QuakeEngineSearch.Result::getType).collect(Collectors.toList()));
		assertEquals(List.of("d-modern.exe", "e.exe", "c.exe", "b-legacy.exe", "a-other.exe"),
		             results.stream().map(r -> r.getFile().getName()).collect(Collectors.toList()));
		assertEquals(5, progress.size());
		assertEquals(5, progress.get(4)[0]);
		assertEquals(5, progress.get(4)[1]);
	}

	@Test
	public void stopsWhenCancelled() throws IOException {
		write("a.exe", "MZ");
		write("b.exe", "MZ");
		CancellationToken token = new CancellationToken();

		assertThrows(CancelledException.class,
			() -> QuakeEngineSearch.search(quakeDir.toFile(), token, (done, total) -> token.cancel()));
	}

	@Test
	public void missingDirectoryFindsNothing() {
		assertEquals(List.of(), QuakeEngineSearch.findExecutables(quakeDir.resolve("missing").toFile()));
	}

	private void write(String name, String content) throws IOException {
		Path p = quakeDir.resolve(name);
		Files.createDirectories(p.getParent());
		Files.write(p, content.getBytes(StandardCharsets.ISO_8859_1));
	}
}
