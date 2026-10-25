package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.utils.Download;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DownloadTest {

	@Test
	public void testUrlEncode() {
		String urlString = "http://example.com/some path/with spaces";
		var encodedUrl = Download.urlEncode(urlString);
		assertEquals("http://example.com/some%20path/with%20spaces", encodedUrl.toString());
	}

	@Test
	public void testQueryWithTags() {
		String urlString = "https://www.quaddicted.com/api/v1/?q=+tags:\"game_mode=singleplayer\" +tags:\"game=quake\"";
		var encodedUrl = Download.urlEncode(urlString);
		assertEquals("https://www.quaddicted.com/api/v1/?q=%2Btags:%22game_mode=singleplayer%22%20%2Btags:%22game=quake%22", encodedUrl.toString());
	}

	@Test
	public void testQueryWithNoTags() {
		String urlString = "https://www.quaddicted.com/api/v1/?q=*";
		var encodedUrl = Download.urlEncode(urlString);
		assertEquals("https://www.quaddicted.com/api/v1/?q=*", encodedUrl.toString());
	}
}
