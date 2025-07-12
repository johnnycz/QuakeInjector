package de.haukerehfeld.quakeinjector;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class PackageDatabaseSolrJsonParserTest {

    private final PackageDatabaseParser parser = new PackageDatabaseSolrJsonParser(new Configuration(new File("nonexistent")));

    @Test
    public void importsAllEntries() {
        var result = parser.parse(testResource("/solr.json"));
        assertEquals(28, result.size());
    }

    @Test
    public void readsNonNullValues() {
        var result = parser.parse(testResource("/solr.json"));
        for (Requirement r : result) {
            assertNotNull(r);

            Package entry = (Package) r;

            assertNotNull(entry.getDate());
            assertNotNull(entry.getTitle());
            assertNotNull(entry.getAuthor());
            assertNotNull(entry.getDescription());
            assertNotNull(entry.getDownloadUrls().get(0));
            assertNotNull(entry.getSha256());
            assertNotEquals(0, entry.getSize());
            assertNotNull(entry.getStartmaps().get(0));

        }
    }

    private InputStream testResource(String path) {
        var is = getClass().getResourceAsStream(path);
        if (is == null) {
            throw new RuntimeException("Cannot load test resource '" + path + "'");
        }
        return is;
    }
}
