package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.Requirement;
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

            de.haukerehfeld.quakeinjector.model.Package entry = (de.haukerehfeld.quakeinjector.model.Package) r;

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

	@Test
	public void reordersStartMaps() {
		var result = parser.parse(testResource("/solr.json"));
		for (Requirement r : result) {
			de.haukerehfeld.quakeinjector.model.Package p = (Package) r;
			if (p.getSha256().equals("7ad993da6c760c446ca31fad71e9f5b6c9eee99b6354f161aa746b572fe70a7d")) {
				assertEquals("start", p.getStartmaps().get(0));
			}
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
