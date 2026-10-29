package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.ExtractTask;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.utils.AsyncProgressListener;
import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;
import de.haukerehfeld.quakeinjector.utils.UnsafePathException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.ZipException;

import static de.haukerehfeld.quakeinjector.TestZip.names;
import static de.haukerehfeld.quakeinjector.TestZip.read;
import static de.haukerehfeld.quakeinjector.TestZip.rel;
import static org.junit.jupiter.api.Assertions.*;

public class ExtractTaskTest {
	@TempDir
	Path tmp;

	private Path engineDir() throws Exception {
		return Files.createDirectories(tmp.resolve("quake"));
	}

	private PackageFileList extract(Path zip, ExtractMapping mapping, List<File> overwrites) throws Exception {
		return extractTask(zip, mapping, overwrites, new CancellationToken(), null).execute();
	}

	/** The package being extracted; ExtractTask takes the mapping as a separate argument. */
	private final Package pkg = TestZip.testPackage("testmap", new ExtractMapping(), "unused");

	/** The task reads from a stream that stays open until the test ends (closed by {@link #closeStreams}). */
	private ExtractTask extractTask(Path zip, ExtractMapping mapping, List<File> overwrites,
	                                CancellationToken token, AsyncProgressListener progress) throws Exception {
		InputStream in = new BufferedInputStream(Files.newInputStream(zip));
		openStreams.add(in);
		return new ExtractTask(in, Files.size(zip), pkg, engineDir().toFile(), mapping, overwrites, token, progress);
	}

	private final List<InputStream> openStreams = new ArrayList<>();

	@AfterEach
	public void closeStreams() throws Exception {
		for (InputStream in : openStreams) {
			in.close();
		}
	}

	@Test
	public void extractsAllEntriesWhenOverwritesIsNull() throws Exception {
		Path zip = new TestZip().file("maps/a.bsp", "A").file("maps/b.bsp", "B").writeTo(tmp.resolve("in.zip"));

		PackageFileList files = extract(zip, new ExtractMapping(), null);

		assertEquals("A", read(engineDir().resolve("maps/a.bsp")));
		assertEquals("B", read(engineDir().resolve("maps/b.bsp")));
		assertTrue(names(files).containsAll(List.of(rel("maps", "a.bsp"), rel("maps", "b.bsp"), rel("maps"))),
			"file list should contain both files and the created directory: " + names(files));
		assertFalse(pkg.isInstalled(), "extracting must not mark the package installed");
	}

	@Test
	public void appliesExtractMapping() throws Exception {
		ExtractMapping mapping = new ExtractMapping();
		mapping.addMapping("", "{base}/id1/");
		mapping.addMapping("readme.txt", null);
		Path zip = new TestZip().file("maps/a.bsp", "A").file("readme.txt", "R").writeTo(tmp.resolve("in.zip"));

		PackageFileList files = extract(zip, mapping, null);

		assertEquals("A", read(engineDir().resolve("id1/maps/a.bsp")));
		assertFalse(Files.exists(engineDir().resolve("readme.txt")));
		assertFalse(Files.exists(engineDir().resolve("id1/readme.txt")));
		assertFalse(names(files).contains(rel("readme.txt")));
	}

	@Test
	public void writesOnlyListedFilesWhenOverwritesIsGiven() throws Exception {
		Files.createDirectories(engineDir().resolve("maps"));
		Files.writeString(engineDir().resolve("maps/a.bsp"), "OLD");
		Path zip = new TestZip().file("maps/a.bsp", "A").file("maps/b.bsp", "B").writeTo(tmp.resolve("in.zip"));

		PackageFileList files = extract(zip, new ExtractMapping(),
			List.of(engineDir().resolve("maps/b.bsp").toFile()));

		assertEquals("OLD", read(engineDir().resolve("maps/a.bsp")));
		assertEquals("B", read(engineDir().resolve("maps/b.bsp")));
		assertFalse(names(files).contains(rel("maps", "a.bsp")));
	}

	@Test
	public void replacesExistingFileWithoutLeavingTempFiles() throws Exception {
		Files.createDirectories(engineDir().resolve("maps"));
		Files.writeString(engineDir().resolve("maps/a.bsp"), "OLD");
		Path zip = new TestZip().file("maps/a.bsp", "NEW").writeTo(tmp.resolve("in.zip"));

		extract(zip, new ExtractMapping(), null);

		assertEquals("NEW", read(engineDir().resolve("maps/a.bsp")));
		assertEquals(List.of(), tempFiles());
	}

	@Test
	public void dropsFileWithWrongCrc() throws Exception {
		Path zip = new TestZip()
			.storedFile("maps/bad.bsp", "GOODDATA")
			.file("maps/ok.bsp", "OK")
			.writeCorrupted(tmp.resolve("in.zip"), "GOODDATA", "BADDATA!");

		PackageFileList files = extract(zip, new ExtractMapping(), null);

		assertFalse(Files.exists(engineDir().resolve("maps/bad.bsp")));
		assertFalse(names(files).contains(rel("maps", "bad.bsp")));
		assertEquals("OK", read(engineDir().resolve("maps/ok.bsp")));
	}

	@Test
	public void recordsDirCreatedForAFileWithWrongCrc() throws Exception {
		Path zip = new TestZip()
			.storedFile("bad/bad.bsp", "GOODDATA")
			.file("maps/ok.bsp", "OK")
			.writeCorrupted(tmp.resolve("in.zip"), "GOODDATA", "BADDATA!");

		PackageFileList files = extract(zip, new ExtractMapping(), null);

		assertTrue(names(files).contains(rel("bad")),
			"the dir created for the dropped file must be recorded so uninstall removes it: " + names(files));
	}

	@Test
	public void failedWriteRemovesPartialFileAndReportsWhatWasWritten() throws Exception {
		byte[] big = new byte[3 * 1024 * 1024];
		new Random(3).nextBytes(big);
		Path zip = new TestZip().file("maps/a.bsp", "A").file("pak/big.pak", big).writeTo(tmp.resolve("in.zip"));
		// cut the zip in the middle of big.pak, so reading it fails half way
		Files.write(zip, Arrays.copyOf(Files.readAllBytes(zip), (int) (Files.size(zip) / 2)));
		ExtractTask task = extractTask(zip, new ExtractMapping(), null, new CancellationToken(), null);

		assertThrows(IOException.class, task::execute);

		assertFalse(Files.exists(engineDir().resolve("pak/big.pak")), "partial file must be deleted");
		assertEquals(List.of(), tempFiles());
		assertTrue(names(task.getExtractedFiles()).containsAll(List.of(rel("maps", "a.bsp"), rel("maps"), rel("pak"))),
			"cleanup needs the files and dirs written before the failure: " + names(task.getExtractedFiles()));
	}

	@Test
	public void failsWhenNothingWasExtracted() throws Exception {
		Path zip = new TestZip().directory("maps").writeTo(tmp.resolve("in.zip"));

		assertThrows(ZipException.class, () -> extract(zip, new ExtractMapping(), null));
	}

	@Test
	public void anEntryLeadingOutOfTheDirectoryStopsTheExtraction() throws Exception {
		Path zip = new TestZip().file("../outside.txt", "EVIL").writeTo(tmp.resolve("in.zip"));

		assertThrows(UnsafePathException.class, () -> extract(zip, new ExtractMapping(), null));

		assertFalse(Files.exists(tmp.resolve("outside.txt")));
	}

	@Test
	public void anExtractMappingLeadingOutOfTheDirectoryStopsTheExtraction() throws Exception {
		Path zip = new TestZip().file("maps/a.bsp", "A").writeTo(tmp.resolve("in.zip"));
		ExtractMapping mapping = new ExtractMapping();
		mapping.addMapping("maps", "../elsewhere");

		assertThrows(UnsafePathException.class, () -> extract(zip, mapping, null));

		assertFalse(Files.exists(tmp.resolve("elsewhere")));
	}

	@Test
	public void alreadyCancelledTokenWritesNothing() throws Exception {
		Path zip = new TestZip().file("maps/a.bsp", "A").writeTo(tmp.resolve("in.zip"));
		CancellationToken token = new CancellationToken();
		token.cancel();

		assertThrows(CancelledException.class,
			() -> extractTask(zip, new ExtractMapping(), null, token, null).execute());

		assertFalse(Files.exists(engineDir().resolve("maps")));
	}

	@FunctionalInterface
	private interface Condition {
		boolean holds() throws Exception;
	}

	/**
	 * Progress is reported as the archive gets read, so it is a good moment to check the disk
	 * and press cancel at a well-defined point of the extraction.
	 */
	private static AsyncProgressListener cancelWhen(CancellationToken token, Condition condition) {
		return (current, total) -> {
			try {
				if (condition.holds()) {
					token.cancel();
				}
			}
			catch (Exception e) {
				throw new IllegalStateException(e);
			}
		};
	}

	@Test
	public void cancelStopsBeforeTheNextEntry() throws Exception {
		byte[] a = new byte[200_000];
		new Random(4).nextBytes(a);
		Path zip = new TestZip().file("maps/a.bsp", a).file("maps/b.bsp", a).writeTo(tmp.resolve("in.zip"));
		CancellationToken token = new CancellationToken();
		Path aFile = engineDir().resolve("maps/a.bsp");
		// press cancel once a.bsp is completely written
		ExtractTask task = extractTask(zip, new ExtractMapping(), null, token,
			cancelWhen(token, () -> Files.exists(aFile) && Files.size(aFile) == a.length));

		assertThrows(CancelledException.class, task::execute);

		assertEquals(a.length, Files.size(aFile));
		assertFalse(Files.exists(engineDir().resolve("maps/b.bsp")));
		assertTrue(names(task.getExtractedFiles()).containsAll(List.of(rel("maps", "a.bsp"), rel("maps"))),
			"files written before the cancel must be reported for cleanup: " + names(task.getExtractedFiles()));
	}

	@Test
	public void cancelDuringALargeFileRemovesThePartialFile() throws Exception {
		byte[] big = new byte[3 * 1024 * 1024]; // several 1 MB write chunks
		new Random(1).nextBytes(big);
		Path zip = new TestZip().file("maps/big.pak", big).writeTo(tmp.resolve("in.zip"));
		CancellationToken token = new CancellationToken();
		// press cancel as soon as big.pak has been created, i.e. while it is being written
		ExtractTask task = extractTask(zip, new ExtractMapping(), null, token,
			cancelWhen(token, () -> Files.exists(engineDir().resolve("maps/big.pak"))));

		assertThrows(CancelledException.class, task::execute);

		assertFalse(Files.exists(engineDir().resolve("maps/big.pak")), "partial file must be deleted");
		assertEquals(List.of(), tempFiles());
		assertEquals(List.of(rel("maps")), names(task.getExtractedFiles()),
			"the created directory must be reported for cleanup");
	}

	@Test
	public void cancelWhileReplacingAFileKeepsTheOriginal() throws Exception {
		Files.createDirectories(engineDir().resolve("maps"));
		Files.writeString(engineDir().resolve("maps/big.pak"), "OLD");
		byte[] big = new byte[3 * 1024 * 1024];
		new Random(2).nextBytes(big);
		Path zip = new TestZip().file("maps/big.pak", big).writeTo(tmp.resolve("in.zip"));
		CancellationToken token = new CancellationToken();
		// press cancel while the new content is being written to a temp file
		ExtractTask task = extractTask(zip, new ExtractMapping(), null, token,
			cancelWhen(token, () -> !tempFiles().isEmpty()));

		assertThrows(CancelledException.class, task::execute);

		assertEquals("OLD", read(engineDir().resolve("maps/big.pak")));
		assertEquals(List.of(), tempFiles());
	}

	@Test
	public void progressReachesExactly100ForWellCompressedFiles() throws Exception {
		// 2 MB of zeros compress to a few KB; progress used to be counted on the unpacked bytes
		Path zip = new TestZip().file("maps/zeros.bsp", new byte[2 * 1024 * 1024])
			.file("maps/a.bsp", "A").writeTo(tmp.resolve("in.zip"));
		List<Long> progress = new ArrayList<>();

		extractTask(zip, new ExtractMapping(), null, new CancellationToken(),
			(current, total) -> progress.add(current)).execute();

		assertFalse(progress.isEmpty(), "progress must be reported");
		assertTrue(progress.stream().allMatch(p -> p >= 0 && p <= 100), "progress out of range: " + progress);
		assertEquals(100L, progress.get(progress.size() - 1));
	}

	@Test
	public void progressGrowsWhileExtractingALargeArchive() throws Exception {
		byte[] big = new byte[1024 * 1024];
		new Random(5).nextBytes(big);
		Path zip = new TestZip().file("maps/big.pak", big).writeTo(tmp.resolve("in.zip"));
		List<Long> progress = new ArrayList<>();

		extractTask(zip, new ExtractMapping(), null, new CancellationToken(),
			(current, total) -> progress.add(current)).execute();

		assertTrue(progress.stream().distinct().count() > 10, "expected gradual progress: " + progress);
		for (int i = 1; i < progress.size(); i++) {
			assertTrue(progress.get(i) >= progress.get(i - 1), "progress must not go back: " + progress);
		}
		assertEquals(100L, progress.get(progress.size() - 1));
	}

	private List<Path> tempFiles() throws Exception {
		try (Stream<Path> walk = Files.walk(engineDir())) {
			return walk.filter(p -> p.getFileName().toString().endsWith(".tmp")).toList();
		}
	}
}
