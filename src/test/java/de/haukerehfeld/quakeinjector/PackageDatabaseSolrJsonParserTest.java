package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.feature.list.PackageDatabaseParser;
import de.haukerehfeld.quakeinjector.feature.list.PackageDatabaseSolrJsonParser;
import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.OfficialExpansionRequirement;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.model.UnavailableRequirement;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PackageDatabaseSolrJsonParserTest {

    private final PackageDatabaseParser parser = new PackageDatabaseSolrJsonParser(new Configuration(new File("nonexistent")));

    @Test
    public void importsAllEntries() {
        var result = parser.parse(testResource("/solr2pp.json"));
        assertEquals(28, result.size());
    }

    @Test
    public void readsNonNullValues() {
        var result = parser.parse(testResource("/solr2pp.json"));
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
			assertNotNull(entry.getSupposedFileList());
	        assertFalse(entry.getSupposedFileList().isEmpty());
	        assertTrue(entry.getSupposedFileList().iterator().next().getSize() > 0);
        }
    }

	@Test
	public void reordersStartMaps() {
		var result = parser.parse(testResource("/solr2pp.json"));
		for (Requirement r : result) {
			de.haukerehfeld.quakeinjector.model.Package p = (Package) r;
			if (p.getSha256().equals("7ad993da6c760c446ca31fad71e9f5b6c9eee99b6354f161aa746b572fe70a7d")) {
				assertEquals("start", p.getStartmaps().get(0));
			}
		}
	}

	@Test
	public void prerequisitesMissingFromTheDatabaseAreOfficialExpansionsIfKnown() {
		String json = "[{\"bytes\":100,\"description\":\"A map.\","
			+ "\"files\":{\"limbo2.bsp\":{\"bytes\":10,\"sha256\":\"ab\",\"timestamp\":\"2001-01-01T00:00:00.000Z\"}},"
			+ "\"install\":{\"extract\":\"{base}/hipnotic/maps/\"},"
			+ "\"sha256\":\"cd\","
			+ "\"tags\":[\"author=someone\",\"filename=limbo2.zip\",\"release_date=2001-01-01\",\"title=Limbo\","
			+ "\"type=map\",\"dependency=hipnotic\",\"dependency=drake\"],"
			+ "\"urls\":[\"https://example.com/limbo2.zip\"]}]";

		var result = parser.parse(new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

		assertEquals(1, result.size());
		List<Requirement> requirements = ((Package) result.get(0)).getRequirements();
		assertInstanceOf(OfficialExpansionRequirement.class, requirements.get(0));
		assertEquals("Scourge of Armagon (Mission Pack 1)", requirements.get(0).toString());
		assertInstanceOf(UnavailableRequirement.class, requirements.get(1));
		assertEquals("drake", requirements.get(1).getId());
	}

    private InputStream testResource(String path) {
        var is = getClass().getResourceAsStream(path);
        if (is == null) {
            throw new RuntimeException("Cannot load test resource '" + path + "'");
        }
        return is;
    }
}
