package de.haukerehfeld.quakeinjector.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Only web pages are opened; the operating system would run anything else with its program. */
public class BrowserLauncherTest {
	@Test
	public void webAddressesAreOpened() {
		assertTrue(BrowserLauncher.isWebAddress("https://www.quaddicted.com/db/v2/maps/a.html"));
		assertTrue(BrowserLauncher.isWebAddress("http://example.com/"));
		assertTrue(BrowserLauncher.isWebAddress("HTTPS://example.com/a b.html"));
	}

	@Test
	public void anythingElseIsNot() {
		for (String url : new String[] {
			"file:///C:/Quake/id1/evil.bat", "file://attacker/share/x", "ms-msdt:/id PCWDiagnostic",
			"search-ms:query=x", "javascript:alert(1)", "mailto:a@b.c", "jar:file:///x!/y", "ftp://example.com/x",
			"\\\\attacker\\share\\x", "https://", "https:///path", "not a url", "", "https://example.com/\nx",
		}) {
			assertFalse(BrowserLauncher.isWebAddress(url), url);
		}
		assertFalse(BrowserLauncher.isWebAddress(null));
	}
}
