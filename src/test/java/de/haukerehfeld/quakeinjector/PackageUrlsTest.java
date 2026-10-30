package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageUrls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The web page and screenshot addresses of a package, made from the settings and its SHA-256. */
public class PackageUrlsTest {
	@TempDir
	Path tmp;

	private static final String SHA = "a25043c2b6c1c73b24e4a00c63d95a8a9e08943d8b8e39d7c4571ae3a1899d99";

	private Configuration config;
	private PackageUrls urls;

	@BeforeEach
	public void setUp() {
		config = new Configuration(tmp.resolve("config.properties").toFile());
		urls = new PackageUrls(config);
	}

	private static Package pkg(String id, String sha256) {
		return new Package(id, sha256, id + ".zip", List.of("file:///" + id + ".zip"), "author", "title",
			0, null, false, 0f, "description", new ExtractMapping(), "", List.of(), List.of());
	}

	@Test
	public void webpageIsFoundBySha256() throws Exception {
		assertEquals("https://www.quaddicted.com/db/v1/maps/" + SHA,
			urls.webpage(pkg("colony3", SHA)).toASCIIString());
	}

	@Test
	public void screenshotIsFoundBySha256AndId() throws Exception {
		assertEquals("https://www.quaddicted.com/files/quaddicted-images/by-sha256/a2/" + SHA + "/colony3.jpg",
			urls.screenshot(pkg("colony3", SHA)).toASCIIString());
	}

	@Test
	public void spacesAndOtherCharactersAreEscaped() throws Exception {
		assertEquals("https://www.quaddicted.com/files/quaddicted-images/by-sha256/a2/" + SHA
				+ "/my%20map%C3%A9.jpg",
			urls.screenshot(pkg("my map\u00e9", SHA)).toASCIIString());
	}

	@Test
	public void withoutSha256ThereAreNoAddresses() throws Exception {
		assertNull(urls.webpage(pkg("a", null)));
		assertNull(urls.screenshot(pkg("a", null)));
		assertNull(urls.screenshot(pkg("a", "")));
	}

	@Test
	public void anInvalidSettingIsAnError() {
		config.mapWebpageBaseUrl.set("not a url/");
		config.ScreenshotRepositoryPath.set("not a url/");

		assertThrows(URISyntaxException.class, () -> urls.webpage(pkg("a", SHA)));
		assertThrows(URISyntaxException.class, () -> urls.screenshot(pkg("a", SHA)));
	}

	@Test
	public void changedSettingsAreUsed() throws Exception {
		config.mapWebpageBaseUrl.set("https://example.com/maps/");

		assertEquals("https://example.com/maps/" + SHA, urls.webpage(pkg("a", SHA)).toASCIIString());
	}
}
