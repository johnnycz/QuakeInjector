package de.haukerehfeld.quakeinjector.feature.readme;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Reads a readme file for display.
 * <p>
 * Old readmes are rarely UTF-8: the text is read as UTF-8 when it is valid UTF-8, otherwise with the
 * DOS code page 437, which keeps the box drawing characters of DOS-era readmes. Line ends become
 * {@code \n}, a BOM is dropped, and only the first {@link #MAX_BYTES} are read.
 */
public final class ReadmeReader {
	public static final int MAX_BYTES = 1024 * 1024;
	static final String TRUNCATED = "\n\n[... file too long, the rest is not shown]";

	/** Not every Java runtime has the extended charsets; Latin-1 can decode any bytes. */
	private static final Charset FALLBACK = Charset.isSupported("IBM437")
		? Charset.forName("IBM437") : StandardCharsets.ISO_8859_1;

	private ReadmeReader() { }

	public static String read(File file) throws IOException {
		byte[] bytes;
		try (InputStream in = Files.newInputStream(file.toPath())) {
			bytes = in.readNBytes(MAX_BYTES + 1);
		}
		boolean truncated = bytes.length > MAX_BYTES;
		int length = truncated ? lastLineEnd(bytes, MAX_BYTES) : bytes.length;

		String text = decode(bytes, length);
		if (text.startsWith("\ufeff")) {
			text = text.substring(1);
		}
		text = text.replace("\r\n", "\n").replace('\r', '\n');
		return truncated ? text + TRUNCATED : text;
	}

	/** Cut at a line end, so a multi-byte character isn't cut in half. */
	private static int lastLineEnd(byte[] bytes, int limit) {
		for (int i = limit - 1; i > 0; i--) {
			if (bytes[i] == '\n') {
				return i + 1;
			}
		}
		return limit;
	}

	private static String decode(byte[] bytes, int length) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes, 0, length))
				.toString();
		}
		catch (CharacterCodingException e) {
			return new String(bytes, 0, length, FALLBACK);
		}
	}
}
