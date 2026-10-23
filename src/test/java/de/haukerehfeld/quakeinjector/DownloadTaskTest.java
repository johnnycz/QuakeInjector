package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.install.DownloadTask;
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

		long size = new DownloadTask(Download.create(url(source)), out).execute();

		assertEquals(data.length, size);
		assertArrayEquals(data, out.toByteArray());
	}

	@Test
	public void reportsProgressUpTo100Percent() throws Exception {
		Path source = Files.write(tmp.resolve("source.zip"), randomBytes(10_000));
		List<Long> progress = new ArrayList<>();

		new DownloadTask(Download.create(url(source)), new ByteArrayOutputStream(),
			(current, total) -> progress.add(current)).execute();

		assertFalse(progress.isEmpty());
		assertEquals(100L, progress.get(progress.size() - 1));
	}

	@Test
	public void missingFileIsReportedAsNotFound() throws Exception {
		Path missing = tmp.resolve("missing.zip");

		assertThrows(OnlineFileNotFoundException.class,
			() -> new DownloadTask(Download.create(url(missing)), new ByteArrayOutputStream()).execute());
	}
}
