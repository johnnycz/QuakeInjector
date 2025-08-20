package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.Requirement;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class PackageDatabaseJsonParserTest {

    private final PackageDatabaseParser parser = new PackageDatabaseJsonParser(new Configuration(new File("nonexistent")));

    @Test
    public void importsAllEntries() {
        var result = parser.parse(testResourceFirst12());
        assertEquals(12, result.size());
        for (Requirement r : result) {
            assertNotNull(r);
        }
    }

    @Test
    public void reordersStartMaps() {
        var result = parser.parse(testResourceFirst12());
        for (Requirement r : result) {
            de.haukerehfeld.quakeinjector.model.Package p = (de.haukerehfeld.quakeinjector.model.Package) r;
            if (p.getSha256().equals("e2efb10efeb36af3d4b6b9e1ddeac537c8bb9b4773f701a1c1e8317785cad419")) {
                assertEquals("start", p.getStartmaps().get(0));
            }
        }
    }

    @Test
    public void readsNonNullValues() {
        var result = parser.parse(testResourceFirst12());
        for (Requirement r : result) {
            de.haukerehfeld.quakeinjector.model.Package entry = (Package) r;

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
    private InputStream testResourceFirst12() {
        var is = getClass().getResourceAsStream("/first-12.json");
        if (is == null) {
            throw new RuntimeException("Cannot load test resource first-12.json");
        }
        return is;
    }
}
