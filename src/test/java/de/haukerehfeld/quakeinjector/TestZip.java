package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.FileInfo;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageFileList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds real zip files for tests.
 */
public class TestZip {
	private final Map<String, byte[]> entries = new LinkedHashMap<>();
	private final List<String> storedEntries = new ArrayList<>();

	public TestZip file(String name, String content) {
		return file(name, content.getBytes(StandardCharsets.UTF_8));
	}

	public TestZip file(String name, byte[] content) {
		entries.put(name, content);
		return this;
	}

	/** Add an uncompressed entry, so its bytes appear as-is in the zip. */
	public TestZip storedFile(String name, String content) {
		storedEntries.add(name);
		return file(name, content);
	}

	public TestZip directory(String name) {
		entries.put(name.endsWith("/") ? name : name + "/", null);
		return this;
	}

	public byte[] toBytes() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (Map.Entry<String, byte[]> e : entries.entrySet()) {
				ZipEntry entry = new ZipEntry(e.getKey());
				byte[] content = e.getValue();
				if (content != null && storedEntries.contains(e.getKey())) {
					CRC32 crc = new CRC32();
					crc.update(content);
					entry.setMethod(ZipEntry.STORED);
					entry.setSize(content.length);
					entry.setCompressedSize(content.length);
					entry.setCrc(crc.getValue());
				}
				zip.putNextEntry(entry);
				if (content != null) {
					zip.write(content);
				}
				zip.closeEntry();
			}
		}
		return bytes.toByteArray();
	}

	public Path writeTo(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Files.write(file, toBytes());
		return file;
	}

	/** Write the zip, replacing the first occurrence of {@code search} with {@code replacement} (same length). */
	public Path writeCorrupted(Path file, String search, String replacement) throws IOException {
		if (search.length() != replacement.length()) {
			throw new IllegalArgumentException("search and replacement must have the same length");
		}
		byte[] data = toBytes();
		byte[] needle = search.getBytes(StandardCharsets.UTF_8);
		int at = indexOf(data, needle);
		if (at < 0) {
			throw new IllegalArgumentException(search + " not found in zip");
		}
		System.arraycopy(replacement.getBytes(StandardCharsets.UTF_8), 0, data, at, needle.length);
		Files.createDirectories(file.getParent());
		Files.write(file, data);
		return file;
	}

	private static int indexOf(byte[] data, byte[] needle) {
		outer:
		for (int i = 0; i <= data.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (data[i + j] != needle[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}

	public static Package testPackage(String id, ExtractMapping mapping, String downloadUrl) {
		return new Package(id, "sha", id + ".zip", List.of(downloadUrl), "author", "title",
			0, null, false, 0f, "description", mapping, "", List.of(), List.of());
	}

	public static List<String> names(PackageFileList files) {
		List<String> names = new ArrayList<>();
		for (FileInfo f : files) {
			names.add(f.getName());
		}
		return names;
	}

	/** Relative path as stored in a PackageFileList ('/' separated on every OS). */
	public static String rel(String first, String... more) {
		return more.length == 0 ? first : first + "/" + String.join("/", more);
	}

	public static String read(Path file) throws IOException {
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
