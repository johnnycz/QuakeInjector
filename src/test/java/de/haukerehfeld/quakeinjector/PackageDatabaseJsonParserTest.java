package de.haukerehfeld.quakeinjector;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PackageDatabaseJsonParserTest {

    private PackageDatabaseParser parser = new PackageDatabaseJsonParser(new Configuration(new File("nonexistent")));

    @Test
    public void importsAllEntries() {
        var result = parser.parse(testResourceFirst12());
        assertEquals(12, result.size());
    }

    private InputStream testResourceFirst12() {
        var is = getClass().getResourceAsStream("/first-12.json");
        if (is == null) {
            throw new RuntimeException("Cannot load test resource first-12.json");
        }
        return is;
    }
}
