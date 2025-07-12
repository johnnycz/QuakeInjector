package de.haukerehfeld.quakeinjector.repackage;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public class ExtractMapping {

    private final Map<String, String> map = new TreeMap<>(
            (a, b) -> a.length() != b.length()
                    ? Integer.compare(b.length(), a.length())
                    : a.compareTo(b)
    );

    private String makeRelative(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("{base}")) {
            path = path.substring("{base}".length());
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    public void addMapping(String from, String to) {
        map.put(makeRelative(from), makeRelative(to));
    }

    public String remap(String originalPathString) {
        originalPathString = makeRelative(originalPathString);
        var originalPath = Path.of(originalPathString);
        for (Map.Entry<String, String> entry: map.entrySet()) {
            if (originalPath.startsWith(entry.getKey()) || entry.getKey().isEmpty()) {
                if (entry.getValue() == null) {
                    return null;
                }
                Path relativePath = Path.of(entry.getKey()).relativize(originalPath);

                if (relativePath.toString().isEmpty()) {
                    return Path.of(entry.getValue()).toString();
                } else {
                    return Path.of(entry.getValue(), relativePath.toString()).toString();
                }
            }
        }
        return Path.of(originalPathString).toString();
    }
}
