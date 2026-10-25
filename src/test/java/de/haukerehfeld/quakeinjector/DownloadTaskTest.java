package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.DownloadTask;
import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;
import de.haukerehfeld.quakeinjector.utils.Download;
import de.haukerehfeld.quakeinjector.utils.OnlineFileNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class DownloadTaskTest {
	@TempDir
	Path tmp;

	private static String url(Path file) throws Exception {
		return file.toUri().toURL().toString();
	}

	private static byte[] randomBytes(int size) {
		byte[] data = new byte[size];
		new Random(42).nextBytes(data);
		return data;
	}

	@Test
	public void copiesTheWholeFile() throws Exception {
		byte[] data = randomBytes(10_000);
		Path source = Files.write(tmp.resolve("source.zip"), data);
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		long size = new DownloadTask(Download.create(url(source)), out, new CancellationToken()).execute();

		assertEquals(data.length, size);
		assertArrayEquals(data, out.toByteArray());
	}

	@Test
	public void reportsProgressUpTo100Percent() throws Exception {
		Path source = Files.write(tmp.resolve("source.zip"), randomBytes(10_000));
		List<Long> progress = new ArrayList<>();

		new DownloadTask(Download.create(url(source)), new ByteArrayOutputStream(), new CancellationToken(),
			(current, total) -> progress.add(current)).execute();

		assertFalse(progress.isEmpty());
		assertEquals(100L, progress.get(progress.size() - 1));
	}

	@Test
	public void missingFileIsReportedAsNotFound() throws Exception {
		Path missing = tmp.resolve("missing.zip");

		assertThrows(OnlineFileNotFoundException.class,
			() -> new DownloadTask(Download.create(url(missing)), new ByteArrayOutputStream(),
				new CancellationToken()).execute());
	}

	@Test
	public void alreadyCancelledTokenDownloadsNothing() throws Exception {
		Path source = Files.write(tmp.resolve("source.zip"), randomBytes(10_000));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		CancellationToken token = new CancellationToken();
		token.cancel();

		assertThrows(CancelledException.class,
			() -> new DownloadTask(Download.create(url(source)), out, token).execute());

		assertEquals(0, out.size());
	}

	@Test
	public void cancelStopsARunningDownload() throws Exception {
		byte[] data = randomBytes(200_000); // many 1 KB reads
		Path source = Files.write(tmp.resolve("source.zip"), data);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		CancellationToken token = new CancellationToken();

		// cancel from inside the download, as the UI thread would while a download is running
		assertThrows(CancelledException.class,
			() -> new DownloadTask(Download.create(url(source)), out, token,
				(current, total) -> token.cancel()).execute());

		assertTrue(out.size() < data.length, "download should stop early, wrote " + out.size());
		// the source stream must be closed again: on Windows an open stream would keep the file locked
		Files.delete(source);
	}
}
