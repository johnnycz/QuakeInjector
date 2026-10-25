package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.readme.ReadmeReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reading readme files: encodings, line ends, BOM, size limit.
 */
public class ReadmeReaderTest {
	@TempDir
	Path tmp;

	private String read(byte[] content) throws IOException {
		Path file = tmp.resolve("readme.txt");
		Files.write(file, content);
		return ReadmeReader.read(file.toFile());
	}

	@Test
	public void utf8() throws Exception {
		assertEquals("Gr\u00fc\u00dfe \u2013 ok", read("Gr\u00fc\u00dfe \u2013 ok".getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void notUtf8IsReadAsDosCodePage() throws Exception {
		// 0xC9 0xCD 0xBB is the top of a box in code page 437, and not valid UTF-8
		byte[] box = { (byte) 0xC9, (byte) 0xCD, (byte) 0xBB, 'x' };

		assertEquals("\u2554\u2550\u2557x", read(box));
	}

	@Test
	public void lineEndsBecomeNewlines() throws Exception {
		assertEquals("a\nb\nc\n", read("a\r\nb\rc\n".getBytes(StandardCharsets.US_ASCII)));
	}

	@Test
	public void byteOrderMarkIsDropped() throws Exception {
		assertEquals("text", read("\ufefftext".getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void longFileIsCutAtALineEnd() throws Exception {
		String line = "\u00e4".repeat(99) + "\n"; // 199 bytes in UTF-8
		String content = line.repeat(ReadmeReader.MAX_BYTES / 199 + 10);

		String text = read(content.getBytes(StandardCharsets.UTF_8));

		assertTrue(text.endsWith("[... file too long, the rest is not shown]"), text.substring(text.length() - 60));
		// the shown part ends with its last line's newline, then comes the note
		String shown = text.substring(0, text.lastIndexOf("\n\n["));
		assertTrue(shown.chars().allMatch(c -> c == '\u00e4' || c == '\n'), "still read as UTF-8, no line cut in half");
		assertEquals(0, shown.length() % 100, "whole lines only");
	}

	@Test
	public void missingFile() {
		assertThrows(NoSuchFileException.class, () -> ReadmeReader.read(tmp.resolve("missing.txt").toFile()));
	}
}
