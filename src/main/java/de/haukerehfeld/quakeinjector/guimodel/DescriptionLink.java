package de.haukerehfeld.quakeinjector.guimodel;

import java.net.URL;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Where a link in a package description leads. Descriptions come from the package database, so a link
 * can be anything; only two kinds are followed.
 */
public sealed interface DescriptionLink {
	/** Another package, linked by its SHA-256 (64 hex digits, as the database's descriptions do). */
	record ToPackage(String sha256) implements DescriptionLink {}

	/** An absolute http or https address: opened in the browser after the user agrees. */
	record ToWebPage(String url) implements DescriptionLink {}

	/** Anything else (file:, javascript:, ms-msdt:, other relative links, ...): not followed. */
	record Refused(String href) implements DescriptionLink {}

	Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

	static DescriptionLink of(String href) {
		String link = href == null ? "" : href.strip();
		if (SHA256.matcher(link).matches()) {
			return new ToPackage(link.toLowerCase(Locale.ROOT));
		}
		if (isWebAddress(link)) {
			return new ToWebPage(link);
		}
		return new Refused(href);
	}

	/** An absolute http or https URL with a host, without control characters. */
	static boolean isWebAddress(String url) {
		if (url == null || url.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
			return false;
		}
		try {
			// URL, not URI: addresses with spaces are fine, the browser launcher copes with them
			URL parsed = new URL(url);
			String scheme = parsed.getProtocol().toLowerCase(Locale.ROOT);
			return (scheme.equals("http") || scheme.equals("https"))
				&& parsed.getHost() != null && !parsed.getHost().isEmpty();
		}
		catch (Exception e) {
			return false;
		}
	}
}
